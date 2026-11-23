package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.ProjectileStreaks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1500)
abstract class FlywheelTracerFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void slo$prepareTracers(DeltaTracker delta, boolean outline, Camera camera,
                                   GameRenderer renderer, LightTexture light, Matrix4f modelView,
                                   Matrix4f projection, CallbackInfo ci) {
        double focal = Math.abs(projection.m11()) * Minecraft.getInstance().getWindow().getHeight() * .5;
        ProjectileStreaks.prepareFlywheelFrame(camera, delta.getGameTimeDeltaPartialTick(false), focal);
    }
}
