package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.lighting.ForcedParticles;
import dev.sablelightoverhaul.lighting.LightingCommon;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends the explosion particles a whitelist names to every player within the configured distance, instead of only to the ones
 * inside vanilla's thirty-two blocks.
 *
 * <p>{@code ServerLevel.sendParticles} tests the distance itself, so a shell's blast wave, smoke and debris vanish for anyone
 * watching from further away -- which is the whole point of a big cannon. This mod already sends its own particles through
 * {@link ForcedParticles}, which builds the packet and hands it to the players it chose; the same road is opened here for the
 * particles another mod spawns.
 *
 * <p>The decision costs one map lookup per spawn call: the whitelist answer is cached by particle type, so the steady state is a
 * pointer comparison and the extra work is a distance test per player -- which vanilla was doing anyway.
 */
@Mixin(ServerLevel.class)
public abstract class ServerParticleRangeMixin {
    /** Particles worth seeing from far away: the big, slow, unmistakable ones. */
    private static final Set<String> WHITELIST = Set.of(
            "createbigcannons:cannon_blast_wave",
            "createbigcannons:shell_blast_wave",
            "createbigcannons:cannon_smoke",
            "createbigcannons:shell_explosion_cloud",
            "createbigcannons:shell_explosion_smoke",
            "createbigcannons:debris_matter",
            "createbigcannons:debris_smoke",
            "createbigcannons:debris_smoke_burst",
            "createbigcannons:flak_cloud",
            "createbigcannons:shrapnel_cloud",
            "createbigcannons:fluid_cloud",
            "createbigcannons:gas_cloud");

    /** Answers per particle type, so a spawn does not pay for a registry lookup every time. */
    private static final Map<ParticleType<?>, Boolean> DECIDED = new ConcurrentHashMap<>();

    private static boolean covers(ParticleOptions options) {
        if (options == null) return false;
        ParticleType<?> type = options.getType();
        Boolean known = DECIDED.get(type);
        if (known != null) return known;
        var key = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
        boolean answer = key != null && WHITELIST.contains(key.toString());
        DECIDED.put(type, answer);
        return answer;
    }

    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"), cancellable = true)
    private void slo$sendFurther(ParticleOptions options, double x, double y, double z, int count,
                                 double dx, double dy, double dz, double speed,
                                 CallbackInfoReturnable<Integer> cir) {
        if (!LightingCommon.PARTICLE_FORCE.get()) return;
        if (!covers(options)) return;
        ServerLevel level = (ServerLevel) (Object) this;
        double limit = LightingCommon.EXPLOSION_CULL.get();
        double limitSq = limit * limit;
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= limitSq) viewers.add(player);
        }
        if (viewers.isEmpty()) {
            cir.setReturnValue(0);
            return;
        }
        ForcedParticles.send(level, viewers, options, x, y, z, count, dx, dy, dz, speed);
        cir.setReturnValue(viewers.size());
    }
}
