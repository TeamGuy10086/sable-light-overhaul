package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.sablelightoverhaul.mixin.ParticlePositionAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import static org.lwjgl.opengl.GL33.*;

/** Batched thermal smoke downsampling for the main target and patched Photon. */
public final class SmokeResolution {
    private static final List<Particle> queue=new ArrayList<>();
    private static boolean drawing,failed;
    private static int width,height,lowWidth,lowHeight,fbo,color,depth,reduce,compose,vao;
    private record Target(int width,int height,int framebuffer,int color,int depth) {}
    private static final Target[] targets = new Target[17];
    private static int divisor=1, automaticDivisor=1, previousBias=-1;
    private static int photonCompositeFbo;
    private static boolean photonPending, photonReduced, photonFailureLogged;
    public static int override;

    static boolean photon() {
        return ClientFlashes.inPackLighting() && IrisCompat.premultipliedParticles();
    }

    static int configuredBias() {
        return photon() ? BigSmokeConfig.PHOTON_RESOLUTION_BIAS.get() : BigSmokeConfig.RESOLUTION_BIAS.get();
    }

    public static boolean photonDrawPending() { return photonPending; }
    private SmokeResolution(){}

    public static int screenWidth(int full){return drawing&&divisor>1?lowWidth:full;}
    public static int screenHeight(int full){return drawing&&divisor>1?lowHeight:full;}
    public static void begin(){queue.clear();}
    static boolean capture(Particle p){
        if(drawing||failed||(IrisCompat.shaderPackInUse()&&!photon())||MuzzleParticleLayer.mode()!=MuzzleParticleLayer.Mode.FIXED)return false;
        if(!SmokeBatteryBenchmark.active() && (override == 1 || (override == 0 && configuredBias() == 0)))return false;
        queue.add(p);return true;
    }
    public static void flush(Camera camera,float partial){
        SmokeBatteryBenchmark.collect(queue,camera);
        if(queue.isEmpty()){
            automaticDivisor=1;
            return;
        }
        boolean pack = IrisCompat.shaderPackInUse();
        int bias = SmokeBatteryBenchmark.resolutionBias();
        double area = Double.NaN;
        if (bias != previousBias) automaticDivisor=1;
        previousBias=bias;
        boolean photon = pack && photon();
        if (pack && !photon) divisor=1;
        else if (override>0) divisor=override;
        else {
            if (bias>0 && bias<100) area = projectedArea(camera,partial);
            divisor=automaticDivisor=photon
                ? SmokeResolutionPolicy.choosePhoton(bias,area,automaticDivisor)
                : SmokeResolutionPolicy.choose(bias,area,automaticDivisor);
        }
        var eye = camera.getPosition();
        var look = camera.getLookVector();
        queue.sort(Comparator.comparingDouble((Particle particle) -> {
            var p = (ParticlePositionAccessor)particle;
            float interpolation = SmokeBatteryBenchmark.fixture(particle) ? 0 : partial;
            return (Mth.lerp(interpolation,p.slo$previousX(),p.slo$x())-eye.x)*look.x+
                (Mth.lerp(interpolation,p.slo$previousY(),p.slo$y())-eye.y)*look.y+
                (Mth.lerp(interpolation,p.slo$previousZ(),p.slo$z())-eye.z)*look.z;
        }).reversed());
        var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();

        int oldFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldRead=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int[] vp=new int[4];glGetIntegerv(GL_VIEWPORT,vp);
        var oldShader=RenderSystem.getShader();
        int program=glGetInteger(GL_CURRENT_PROGRAM),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING),active=glGetInteger(GL_ACTIVE_TEXTURE);
        int[] bindings=new int[3];for(int i=0;i<3;i++){RenderSystem.activeTexture(GL_TEXTURE0+i);bindings[i]=glGetInteger(GL_TEXTURE_BINDING_2D);}
        RenderSystem.activeTexture(active);
        boolean cull=glIsEnabled(GL_CULL_FACE),blend=glIsEnabled(GL_BLEND),test=glIsEnabled(GL_DEPTH_TEST),mask=glGetBoolean(GL_DEPTH_WRITEMASK);
        int func=glGetInteger(GL_DEPTH_FUNC),sr=glGetInteger(GL_BLEND_SRC_RGB),dr=glGetInteger(GL_BLEND_DST_RGB),sa=glGetInteger(GL_BLEND_SRC_ALPHA),da=glGetInteger(GL_BLEND_DST_ALPHA);
        float[] clear=new float[4];glGetFloatv(GL_COLOR_CLEAR_VALUE,clear);
        try{
            // Only the native main target has a known attachment/depth convention. Fabulous/custom
            // targets fall back rather than sampling an unrelated main-depth texture.
            if(!pack && (oldFbo!=target.frameBufferId||target.getDepthTextureId()<=0))divisor=1;
            if(!pack && divisor>1){
                try {ensure(target.width,target.height,divisor);}catch(RuntimeException ex){
                    failed=true;divisor=1;
                    com.mojang.logging.LogUtils.getLogger().error("SLO low-resolution smoke disabled; native fallback",ex);
                }
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldFbo);glBindFramebuffer(GL_READ_FRAMEBUFFER,oldRead);
            restoreTextures(bindings,active);
            SmokeBatteryBenchmark.gpuStart(pack?1:divisor,divisor,queue.size(),area);
            RenderSystem.disableCull();RenderSystem.enableDepthTest();
            if(!pack && divisor>1){
                glBindFramebuffer(GL_FRAMEBUFFER,fbo);RenderSystem.viewport(0,0,lowWidth,lowHeight);
                RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.depthFunc(GL_ALWAYS);
                glUseProgram(reduce);glBindVertexArray(vao);bind(0,target.getDepthTextureId());
                glUniform1i(glGetUniformLocation(reduce,"sourceDepth"),0);
                glUniform1i(glGetUniformLocation(reduce,"factor"),divisor);
                glDrawArrays(GL_TRIANGLES,0,3);
                glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
                restoreTextures(bindings,active);
            }
            SmokeBatteryBenchmark.gpuStamp(1);
            BufferUploader.invalidate();
            drawing=true;
            if (pack) RenderSystem.setShader(GameRenderer::getParticleShader);
            var out=MuzzleParticleLayer.TYPE.begin(Tesselator.getInstance(),mc.getTextureManager());
            RenderSystem.depthMask(false);RenderSystem.depthFunc(GL_LEQUAL);
            if(!pack && divisor>1)RenderSystem.blendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            for(var p:queue)p.render(out,camera,SmokeBatteryBenchmark.fixture(p)?0:partial);
            MeshData mesh=out.build();
            photonPending=pack && divisor>1;
            photonReduced=false;
            if(mesh!=null)BufferUploader.drawWithShader(mesh);
            photonPending=false;
            drawing=false;
            if (!photonReduced) SmokeBatteryBenchmark.gpuStamp(2);
            if(!pack && divisor>1){
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldFbo);RenderSystem.viewport(vp[0],vp[1],vp[2],vp[3]);
                RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
                glUseProgram(compose);glBindVertexArray(vao);bind(0,color);bind(1,depth);bind(2,target.getDepthTextureId());
                glUniform1i(glGetUniformLocation(compose,"smokeTex"),0);glUniform1i(glGetUniformLocation(compose,"lowDepth"),1);
                glUniform1i(glGetUniformLocation(compose,"sceneDepth"),2);glDrawArrays(GL_TRIANGLES,0,3);
            }
            SmokeBatteryBenchmark.gpuEnd();
        }finally{
            photonPending=false;drawing=false;queue.clear();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldFbo);glBindFramebuffer(GL_READ_FRAMEBUFFER,oldRead);
            RenderSystem.viewport(vp[0],vp[1],vp[2],vp[3]);restoreTextures(bindings,active);
            RenderSystem.setShader(()->oldShader);glUseProgram(program);glBindVertexArray(oldVao);BufferUploader.invalidate();glClearColor(clear[0],clear[1],clear[2],clear[3]);
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(test)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.depthMask(mask);RenderSystem.depthFunc(func);RenderSystem.blendFuncSeparate(sr,dr,sa,da);
        }
    }
    /** Runs after Iris applies the particle program and binds its real draw target. */
    public static boolean drawPhoton(Runnable draw) {
        int program = glGetInteger(GL_CURRENT_PROGRAM);
        if (program == 0) return false;
        int scale = glGetUniformLocation(program, "sloSmokePixelScale");
        int colorBuffer = glGetInteger(GL_DRAW_BUFFER0);
        if (scale < 0 || colorBuffer < GL_COLOR_ATTACHMENT0 || colorBuffer > GL_COLOR_ATTACHMENT31
                || glGetInteger(GL_DRAW_BUFFER1) != GL_NONE) {
            return photonFallback("particle program lacks the single HDR smoke output");
        }
        int sourceColor = attachmentTexture(colorBuffer);
        int sourceDepth = attachmentTexture(GL_DEPTH_ATTACHMENT);
        if (sourceColor == 0 || sourceDepth == 0) return photonFallback("Iris attachments are not sampleable textures");

        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        int fullWidth = viewport[2], fullHeight = viewport[3];
        if (viewport[0] != 0 || viewport[1] != 0 || fullWidth <= 0 || fullHeight <= 0) {
            return photonFallback("unsupported Iris viewport");
        }
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        int[] textures = new int[3];
        for (int i=0;i<textures.length;i++) {
            RenderSystem.activeTexture(GL_TEXTURE0+i);
            textures[i]=glGetInteger(GL_TEXTURE_BINDING_2D);
        }
        int drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        boolean blend = glIsEnabledi(GL_BLEND,0), depthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        int depthFunc = glGetInteger(GL_DEPTH_FUNC);
        int srcRgb = glGetIntegeri(GL_BLEND_SRC_RGB,0), dstRgb = glGetIntegeri(GL_BLEND_DST_RGB,0);
        int srcAlpha = glGetIntegeri(GL_BLEND_SRC_ALPHA,0), dstAlpha = glGetIntegeri(GL_BLEND_DST_ALPHA,0);
        try {
            if (!matchesSize(sourceDepth, fullWidth, fullHeight) || !matchesSize(sourceColor, fullWidth, fullHeight)) {
                return photonFallback("Iris viewport and attachment sizes differ");
            }
            try {
                ensure(fullWidth, fullHeight, divisor);
            } catch (RuntimeException ex) {
                return photonFallback(ex.toString());
            }

            // Use a color-only view of the real HDR attachment during upsampling. The sampled
            // scene depth is deliberately absent from this FBO to avoid a texture feedback loop.
            if (photonCompositeFbo == 0) photonCompositeFbo = glGenFramebuffers();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, photonCompositeFbo);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sourceColor, 0);
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                return photonFallback("Iris color-only framebuffer is incomplete");
            }

            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
            glViewport(0,0,lowWidth,lowHeight);
            glDisablei(GL_BLEND,0);
            glEnable(GL_DEPTH_TEST);
            glDepthMask(true);
            glDepthFunc(GL_ALWAYS);
            glUseProgram(reduce);
            glBindVertexArray(vao);
            bind(0,sourceDepth);
            glUniform1i(glGetUniformLocation(reduce,"sourceDepth"),0);
            glUniform1i(glGetUniformLocation(reduce,"factor"),divisor);
            glDrawArrays(GL_TRIANGLES,0,3);
            glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,0});
            restoreTextures(textures,active);

            SmokeBatteryBenchmark.gpuDivisor(divisor);
            SmokeBatteryBenchmark.gpuStamp(1);
            glUseProgram(program);
            glUniform2f(scale,(float)fullWidth/lowWidth,(float)fullHeight/lowHeight);
            glBindVertexArray(vertexArray);
            glDepthMask(false);
            glDepthFunc(GL_LEQUAL);
            glEnablei(GL_BLEND,0);
            org.lwjgl.opengl.GL40.glBlendFuncSeparatei(0,GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            draw.run();

            SmokeBatteryBenchmark.gpuStamp(2);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,photonCompositeFbo);
            glViewport(0,0,fullWidth,fullHeight);
            glDisable(GL_DEPTH_TEST);
            glUseProgram(compose);
            glBindVertexArray(vao);
            bind(0,color);bind(1,depth);bind(2,sourceDepth);
            glUniform1i(glGetUniformLocation(compose,"smokeTex"),0);
            glUniform1i(glGetUniformLocation(compose,"lowDepth"),1);
            glUniform1i(glGetUniformLocation(compose,"sceneDepth"),2);
            glDrawArrays(GL_TRIANGLES,0,3);
            photonReduced=true;
            return true;
        } finally {
            if (photonCompositeFbo != 0) {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,photonCompositeFbo);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,0,0);
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFbo);
            glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo);
            glViewport(viewport[0],viewport[1],fullWidth,fullHeight);
            glUseProgram(program);
            glUniform2f(scale,1,1);
            glBindVertexArray(vertexArray);
            restoreTextures(textures,active);
            glDepthFunc(depthFunc);
            glDepthMask(depthMask);
            org.lwjgl.opengl.GL40.glBlendFuncSeparatei(0,srcRgb,dstRgb,srcAlpha,dstAlpha);
            if(blend)glEnablei(GL_BLEND,0);else glDisablei(GL_BLEND,0);
            if(depthTest)glEnable(GL_DEPTH_TEST);else glDisable(GL_DEPTH_TEST);
        }
    }

    private static int attachmentTexture(int attachment) {
        return glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,attachment,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL_TEXTURE
            ? glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER,attachment,GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) : 0;
    }

    private static boolean matchesSize(int texture,int w,int h) {
        bind(0,texture);
        return glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)==w
            && glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT)==h;
    }

    private static boolean photonFallback(String reason) {
        if (!photonFailureLogged) {
            photonFailureLogged=true;
            com.mojang.logging.LogUtils.getLogger().warn("SLO Photon smoke used full resolution: {}",reason);
        }
        return false;
    }

    private static double projectedArea(Camera camera,float partial) {
        var eye = camera.getPosition();
        var rotation = new Quaternionf(camera.rotation()).conjugate();
        var center = new Vector3f();
        var projection = RenderSystem.getProjectionMatrix();
        double xScale = Math.abs(projection.m00()), yScale = Math.abs(projection.m11());
        boolean single = BigSmokeParticle.singleQuad();
        double total = 0;
        for (Particle particle : queue) {
            float interpolation = SmokeBatteryBenchmark.fixture(particle) ? 0 : partial;
            double size, weight = 1;
            if (particle instanceof BigSmokeParticle puff) {
                if (!puff.hasVisibleCoverage(interpolation)) continue;
                size = puff.getQuadSize(interpolation) * (single ? .8 : .68);
                weight = single ? 1 : 1.85;
            } else if (particle instanceof PlumeSmokeParticle puff) {
                size = puff.getQuadSize(interpolation) * .8;
            } else {
                var bounds = particle.getBoundingBox();
                size = Math.max(bounds.getXsize(),Math.max(bounds.getYsize(),bounds.getZsize())) * .5;
            }
            var p = (ParticlePositionAccessor)particle;
            center.set((float)(Mth.lerp(interpolation,p.slo$previousX(),p.slo$x())-eye.x),
                       (float)(Mth.lerp(interpolation,p.slo$previousY(),p.slo$y())-eye.y),
                       (float)(Mth.lerp(interpolation,p.slo$previousZ(),p.slo$z())-eye.z)).rotate(rotation);
            double depth = -center.z;
            if (depth <= 0) continue;
            // Screen-aligned square approximates the rolled quads, including overlap.
            double x = center.x * xScale / depth - projection.m20();
            double y = center.y * yScale / depth - projection.m21();
            total += SmokeResolutionPolicy.clippedArea(x,y,size*xScale/depth,size*yScale/depth)
                * weight;
        }
        return total;
    }
    private static void bind(int unit,int texture){RenderSystem.activeTexture(GL_TEXTURE0+unit);RenderSystem.bindTexture(texture);}
    private static void restoreTextures(int[] bindings,int active){for(int i=0;i<bindings.length;i++)bind(i,bindings[i]);RenderSystem.activeTexture(active);}
    private static void ensure(int w,int h,int factor){
        if(reduce==0){reduce=program("smoke_reduce.fsh");compose=program("smoke_upsample.fsh");vao=glGenVertexArrays();}
        int lw=(w+factor-1)/factor,lh=(h+factor-1)/factor;
        if (width!=w || height!=h) {
            for (int i=0;i<targets.length;i++) {
                Target old=targets[i];
                if (old==null) continue;
                glDeleteFramebuffers(old.framebuffer());
                RenderSystem.deleteTexture(old.color());
                RenderSystem.deleteTexture(old.depth());
                targets[i]=null;
            }
        }
        Target cached=targets[factor];
        if (cached!=null) {
            lowWidth=cached.width(); lowHeight=cached.height();
            fbo=cached.framebuffer(); color=cached.color(); depth=cached.depth();
            return;
        }
        width=w;height=h;lowWidth=lw;lowHeight=lh;
        RenderSystem.activeTexture(GL_TEXTURE0);
        color=texture(GL_RGBA16F,GL_RGBA,GL_FLOAT);depth=texture(GL_DEPTH_COMPONENT32F,GL_DEPTH_COMPONENT,GL_FLOAT);
        fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) {
            glDeleteFramebuffers(fbo);
            RenderSystem.deleteTexture(color);
            RenderSystem.deleteTexture(depth);
            throw new IllegalStateException("Incomplete smoke framebuffer");
        }
        targets[factor]=new Target(lw,lh,fbo,color,depth);
    }
    private static int texture(int internal,int format,int type){
        int id=glGenTextures();RenderSystem.bindTexture(id);
        glTexImage2D(GL_TEXTURE_2D,0,internal,lowWidth,lowHeight,0,format,type,(java.nio.ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);return id;
    }
    private static int program(String file){
        int v=shader(GL_VERTEX_SHADER,read("smoke_fullscreen.vsh")),f=shader(GL_FRAGMENT_SHADER,read(file));
        int p=glCreateProgram();glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p);glDeleteShader(v);glDeleteShader(f);
        if(glGetProgrami(p,GL_LINK_STATUS)==0){String log=glGetProgramInfoLog(p);glDeleteProgram(p);throw new IllegalStateException(log);}return p;
    }
    private static int shader(int type,String source){int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);
        if(glGetShaderi(s,GL_COMPILE_STATUS)==0){String log=glGetShaderInfoLog(s);glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    private static String read(String file){try(var in=SmokeResolution.class.getResourceAsStream("/assets/sablelightoverhaul/shaders/experimental/"+file)){
        if(in==null)throw new IllegalStateException(file);return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}}
}
