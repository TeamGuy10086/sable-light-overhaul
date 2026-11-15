package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.CannonDustPayload;
import dev.sablelightoverhaul.FlashStore;
import dev.sablelightoverhaul.HostileSmokePayload;
import dev.sablelightoverhaul.HostileSmokeWeapon;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.effects.particles.plumes.BigCannonPlumeParticleData;

final class HostileSmokeClient {
    private static final LinkedHashMap<Integer, Long> LAST_HAZE = new LinkedHashMap<>(16, .75f, true);
    private static java.lang.ref.WeakReference<ClientLevel> world = new java.lang.ref.WeakReference<>(null);

    private HostileSmokeClient() {}

    static void receive(HostileSmokePayload payload) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || !level.dimension().location().equals(payload.dimension())
                || !payload.weapon().getNamespace().equals("sablehostile")) return;
        var weapon = HostileSmokeWeapon.resolve(payload.weapon().getPath(), payload.haze() != null);
        var profile = HostileSmokeConfig.profile(weapon);
        if (!profile.enabled()) return;
        var muzzle = payload.muzzle();
        var axis = payload.axis().normalize();
        if (!FlashStore.valid(muzzle.x, muzzle.y, muzzle.z, axis.x, axis.y, axis.z, 0) || axis.lengthSqr() < .5) return;
        if (weapon.thin()) {
            if (payload.haze() != null) thin(level, payload.haze(), profile);
            return;
        }
        int lobes = profile.integer("lobesPerShot");
        if (lobes == 0 || profile.decimal("opacity") == 0) return;
        if (!ClientFlashes.prepareEffects(level) || !BigSmokeParticle.available()) {
            mc.particleEngine.createParticle(new BigCannonPlumeParticleData(2, 2, 10),
                muzzle.x, muzzle.y, muzzle.z, axis.x, axis.y, axis.z);
            return;
        }
        mc.particleEngine.add(new Burst(level, muzzle, axis, BigSmokeParticle.qualityCount(lobes),
            profile.integer("releaseTicks"), profile.thermal()));
    }

    private static void thin(ClientLevel level, CannonDustPayload shot, HostileSmokeConfig.Profile profile) {
        if (world.get() != level) {
            LAST_HAZE.clear();
            world = new java.lang.ref.WeakReference<>(level);
        }
        int count = profile.integer("hazeCount");
        long now = level.getGameTime();
        int emitter = shot.emitterId();
        Long last = LAST_HAZE.get(emitter);
        if (last != null && now >= last && now - last < profile.integer("hazeInterval")) count = 0;
        if (profile.decimal("hazeAlpha") == 0) count = 0;
        if (count > 0) {
            LAST_HAZE.put(emitter, now);
            if (LAST_HAZE.size() > 1024) LAST_HAZE.pollFirstEntry();
        }
        CannonDustEffects.receiveAutocannon(new CannonDustPayload(shot.dimension(), shot.muzzle(), shot.axis(),
            shot.seed(), List.of(), 0, count, 0, shot.burst(), profile.haze(), profile.number("hazeSpread"),
            profile.number("hazeDrift"), profile.number("hazeSink"), profile.number("hazeClearance"),
            profile.number("hazePush"), profile.number("hazePushRadius"), profile.number("hazeLightGain"), emitter));
    }

    private static final class Burst extends NoRenderParticle {
        private final Vec3 muzzle, axis;
        private final BigSmokeParticle.Appearance appearance;
        private final int count;
        private final int epoch = ClientFlashes.visualEpoch();

        private Burst(ClientLevel level, Vec3 muzzle, Vec3 axis, int count, int release, BigSmokeParticle.Appearance appearance) {
            super(level, muzzle.x, muzzle.y, muzzle.z);
            this.muzzle = muzzle;
            this.axis = axis;
            this.appearance = appearance;
            this.count = count;
            lifetime = release;
        }

        @Override public void tick() {
            if (epoch != ClientFlashes.visualEpoch() || !BigSmokeParticle.available()) { remove(); return; }
            BigSmokeParticle.emit(level, muzzle, axis, null, age, count, lifetime, appearance);
            if (++age >= lifetime) remove();
        }
    }
}
