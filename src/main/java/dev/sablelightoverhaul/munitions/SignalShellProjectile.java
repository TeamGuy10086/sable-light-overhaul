package dev.sablelightoverhaul.munitions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
/** Same CBC fuze/flight/persistence path, releasing the colored point-light payload. */
public final class SignalShellProjectile extends IlluminationShellProjectile {
 public SignalShellProjectile(EntityType<? extends SignalShellProjectile> type,Level level){super(type,level);}
 @Override protected boolean illumination(){return false;}
}
