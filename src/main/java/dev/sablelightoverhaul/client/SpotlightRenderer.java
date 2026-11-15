package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sablelightoverhaul.SableLightOverhaul;
import dev.sablelightoverhaul.SpotlightPose;
import dev.sablelightoverhaul.lighting.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** One rigid transform per mechanical part. The mounting plate remains in the chunk mesh. */
public final class SpotlightRenderer implements BlockEntityRenderer<SpotlightBlockEntity> {
    private static final ModelResourceLocation YOKE=model("yoke"),HEAD=model("head"),LENS=model("lens");
    // Reserved low bits in both lightmap coordinates, decoded only for block-entity vertices.
    // Vanilla clamps these to the last (full-bright) lightmap texel.
    private static final int LENS_LIGHT=LightTexture.FULL_BRIGHT | (9<<16) | 7;
    private static final RenderType SOLID=RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS);
    public SpotlightRenderer(BlockEntityRendererProvider.Context context) {}
    private static ModelResourceLocation model(String part){
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID,"block/spotlight_"+part));
    }
    public static void registerModels(ModelEvent.RegisterAdditional event){
        event.register(YOKE);event.register(HEAD);event.register(LENS);
    }
    @Override public void render(SpotlightBlockEntity lamp,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!lamp.getBlockState().getValue(SpotlightBlock.VISIBLE))return;
        var effective=lamp.getLevel() instanceof ClientLevel level?ExtraLights.spotlightModel(level,lamp.getBlockPos()):null;
        double tilt=effective!=null?effective.tilt():(lamp.control()==SpotlightBlockEntity.FULL?lamp.sweep(0):lamp.angle());
        double azimuth=effective!=null?effective.azimuth():lamp.azimuth();
        boolean lit=effective!=null&&effective.intensity()>0;
        pose.pushPose();
        pose.translate(0.5,SpotlightPose.PIVOT_Y,0.5);
        pose.mulPose(Axis.YP.rotationDegrees(SpotlightPose.yaw(azimuth)));
        pose.translate(-0.5,-SpotlightPose.PIVOT_Y,-0.5);
        part(YOKE,pose,buffers,light,1,1,1);
        pose.translate(0.5,SpotlightPose.PIVOT_Y,0.5);
        pose.mulPose(Axis.XP.rotationDegrees(SpotlightPose.pitch(tilt)));
        pose.translate(-0.5,-SpotlightPose.PIVOT_Y,-0.5);
        part(HEAD,pose,buffers,light,1,1,1);
        // Photon decodes this lens-only marker as emissive; ordinary housing and off lenses are unmarked.
        // The off lens remains ordinarily lit. No translucent overlay, extra pass, or shader option.
        float[] rgb=lit?SourceColor.rgb(Math.max(0,effective.colorSignal()),0,new float[3],1,1,1):new float[]{0.3f,0.34f,0.38f};
        part(LENS,pose,buffers,lit?LENS_LIGHT:light,rgb[0],rgb[1],rgb[2]);
        pose.popPose();
    }
    private static void part(ModelResourceLocation id,PoseStack pose,MultiBufferSource buffers,int light,float r,float g,float b){
        var mc=Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(SOLID),null,
            mc.getModelManager().getModel(id),r,g,b,light,OverlayTexture.NO_OVERLAY);
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(SpotlightBlockEntity lamp){
        return new net.minecraft.world.phys.AABB(lamp.getBlockPos()).inflate(0.6);
    }
}
