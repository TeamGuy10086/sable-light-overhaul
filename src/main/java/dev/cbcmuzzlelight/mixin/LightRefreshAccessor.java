package dev.cbcmuzzlelight.mixin;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(LevelRenderer.class)
public interface LightRefreshAccessor {
 @Invoker("setSectionDirty") void muzzle$dirty(int x,int y,int z,boolean important);
}
