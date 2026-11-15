uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform vec2 taaJitter;
uniform float dayStrength;

#ifdef VOXY
    #define DISTANT_HORIZONS
    uniform sampler2D vxDepthTexTrans;
    uniform sampler2D vxDepthTexOpaque;
    uniform mat4 vxProjInv;
    #define dhDepthTex0 vxDepthTexTrans
    #define dhDepthTex1 vxDepthTexOpaque
    #define dhProjectionInverse vxProjInv
#elif defined DISTANT_HORIZONS
    uniform sampler2D dhDepthTex0;
    uniform sampler2D dhDepthTex1;
    uniform mat4 dhProjectionInverse;
#endif

// This pass replaces Mellow's disabled composite1 and runs before its bloom chain.
noperspective in vec2 texcoord;
/* DRAWBUFFERS:0 */
layout(location = 0) out vec4 Color;

void main() {
    Color = texture(colortex0, texcoord);
}
