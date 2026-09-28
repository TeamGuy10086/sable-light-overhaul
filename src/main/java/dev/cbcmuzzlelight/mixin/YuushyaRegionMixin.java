package dev.cbcmuzzlelight.mixin;
import java.util.Set;
import dev.cbcmuzzlelight.client.YuushyaLighting;
import com.yuushya.modelling.blockentity.itemblock.ItemBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.*;
import org.spongepowered.asm.mixin.*;
@Pseudo @Mixin(targets="com.yuushya.modelling.client.anvilcraft.rendering.CachedRegion",remap=false)
public abstract class YuushyaRegionMixin implements YuushyaLighting.Region {
 @Shadow @Final private Set<BlockEntity> blockEntities;
 @Shadow public abstract void forcedUpdate();
 @Unique private boolean muzzle$pending;
 public void muzzle$invalidate(Set<Long> sections){
  if(muzzle$pending)return;
  for(var entity:blockEntities){
   if(entity.isRemoved())continue;
   if(sections.contains(SectionPos.asLong(entity.getBlockPos()))){muzzle$pending=true;forcedUpdate();return;}
   if(entity instanceof ItemBlockEntity item)for(var transform:item.getTransformData()){
    var pos=entity.getBlockPos().offset((int)(transform.pos.x/16),(int)(transform.pos.y/16),(int)(transform.pos.z/16));
    if(sections.contains(SectionPos.asLong(pos))){muzzle$pending=true;forcedUpdate();return;}
   }
  }
 }
 public void muzzle$finished(){muzzle$pending=false;}
}
