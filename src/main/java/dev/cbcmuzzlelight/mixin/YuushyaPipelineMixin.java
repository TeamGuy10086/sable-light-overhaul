package dev.cbcmuzzlelight.mixin;
import java.util.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cbcmuzzlelight.client.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.*;
@Pseudo @Mixin(targets="com.yuushya.modelling.client.anvilcraft.rendering.CacheableBERenderingPipeline",remap=false)
public abstract class YuushyaPipelineMixin implements YuushyaLighting.Pipeline {
 @Shadow @Final private Map<ChunkPos,com.yuushya.modelling.client.anvilcraft.rendering.CachedRegion> regions;
 public void muzzle$invalidate(Set<Long> sections){for(var region:regions.values())((YuushyaLighting.Region)(Object)region).muzzle$invalidate(sections);}
 @WrapMethod(method="runTasks")
 private void muzzle$run(Operation<Void> original){
  try(var scope=LightReadScope.enter()){ModelBlockRenderer.clearCache();original.call();}
  finally {ModelBlockRenderer.clearCache();for(var region:regions.values())((YuushyaLighting.Region)(Object)region).muzzle$finished();}
 }
}
