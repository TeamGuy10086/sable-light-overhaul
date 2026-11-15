package dev.sablelightoverhaul.munitions;
import dev.sablelightoverhaul.lighting.DebugFlareServer;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.big_cannon.he_shell.HEShellProjectile;
/** Flight and all fuze callbacks are CBC HE behaviour; detonation releases a burning payload instead of HE. */
public class IlluminationShellProjectile extends HEShellProjectile {
 private boolean released;
 private FlareSettings settings = FlareSettings.DEFAULT;
 public FlareSettings settings(){return settings;}
 public void setSettings(FlareSettings settings){this.settings=settings;}
 public IlluminationShellProjectile(EntityType<? extends IlluminationShellProjectile> type,Level level){super(type,level);}
 protected boolean illumination(){return true;}
 @Override protected void detonate(Position position){
  if(released||!(level() instanceof ServerLevel level))return;
  released=true;
  DebugFlareServer.release(level,new Vec3(position.x(),position.y(),position.z()),
    settings.burnSeconds(),settings.brightness(),illumination(),settings.rgb(),settings.angleDegrees());
  // A clip, impact and tick may all report the same detonation. Release once and remove the ballistic carrier.
  discard();
 }
 @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("SloFlareReleased",released);settings.write(tag);}
 @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);released=tag.getBoolean("SloFlareReleased");settings=FlareSettings.read(tag);}
}
