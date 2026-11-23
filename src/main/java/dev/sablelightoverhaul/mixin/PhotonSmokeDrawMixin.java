package dev.sablelightoverhaul.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.sablelightoverhaul.client.SmokeResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VertexBuffer.class)
abstract class PhotonSmokeDrawMixin {
    @WrapOperation(method="_drawWithShader", at=@At(value="INVOKE", target="Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"))
    private void slo$drawSmoke(VertexBuffer buffer, Operation<Void> draw) {
        if (SmokeResolution.photonDrawPending() && SmokeResolution.drawPhoton(() -> draw.call(buffer))) return;
        draw.call(buffer);
    }
}
