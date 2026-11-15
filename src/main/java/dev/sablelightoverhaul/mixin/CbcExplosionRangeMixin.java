package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.lighting.LightingCommon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import rbasamoyai.createbigcannons.munitions.ImpactExplosion;
import rbasamoyai.createbigcannons.munitions.ShellExplosion;
import rbasamoyai.createbigcannons.munitions.autocannon.flak.FlakExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.fluid_shell.FluidExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.mortar_stone.MortarStoneExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.shrapnel.ShrapnelExplosion;
import rbasamoyai.createbigcannons.munitions.big_cannon.smoke_shell.SmokeExplosion;

/**
 * How far away a player still sees an explosion.
 *
 * <p>CBC does not spawn its explosion particles through the vanilla particle call: it sends {@code ClientboundCBCExplodePacket}
 * and the client rebuilds the explosion there, which then spawns its own particles locally. So the range is not decided where
 * particles are sent -- it is decided here, in each explosion's own send method, by one comparison against a squared distance
 * of 263000 blocks (about 513). Past that the player is simply never told, and no amount of particle-range work can help.
 *
 * <p>The constant is replaced with {@code Particle.explosionCull}, the same key that already governs the particles this mod
 * forwards, so one setting decides how far an explosion is seen however it is drawn. Nothing else about the packet changes.
 */
@Mixin({ShellExplosion.class, ImpactExplosion.class, FlakExplosion.class, FluidExplosion.class,
        MortarStoneExplosion.class, ShrapnelExplosion.class, SmokeExplosion.class})
public abstract class CbcExplosionRangeMixin {
    private static long LAST_LOG;
    @ModifyConstant(method = "sendExplosionToClient", remap = false, require = 0,
            constant = @Constant(doubleValue = 263000.0))
    private double slo$explosionRange(double original) {
        double range = LightingCommon.EXPLOSION_CULL.get();
        if (range * range > original && System.nanoTime() - LAST_LOG > 5_000_000_000L) {
            LAST_LOG = System.nanoTime();
            org.slf4j.LoggerFactory.getLogger("sablelightoverhaul").info("SLO cbc range: {} -> {}", original, range * range);
        }
        return range * range;
    }
}
