package dev.cbcmuzzlelight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cbcmuzzlelight.lighting.LightSourceBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the block a stand-in replaced, at full brightness.
 *
 * <p>The stand-in emits nothing, so the position it occupies is dark and the model would render black.
 * Drawing it with the brightness the block had while it still lit the place is what keeps the swap
 * invisible: the lamp goes on looking like a lamp, while this mod supplies the light it used to.
 */
public final class LightSourceRenderer implements BlockEntityRenderer<LightSourceBlockEntity> {
 private final BlockRenderDispatcher dispatcher;
 public LightSourceRenderer(BlockEntityRendererProvider.Context context){this.dispatcher=context.getBlockRenderDispatcher();}
 @Override public void render(LightSourceBlockEntity standIn,float partialTick,PoseStack pose,MultiBufferSource buffer,int packedLight,int packedOverlay){
  BlockState original=standIn.originalState();
  if(original==null||original.isAir())return;
  this.dispatcher.renderSingleBlock(original,pose,buffer,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY);
 }
}
