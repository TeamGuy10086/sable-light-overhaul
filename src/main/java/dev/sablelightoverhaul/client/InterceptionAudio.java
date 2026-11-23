package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.InterceptionPayload;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.effects.sounds.HasAirAbsorptionSound;
import rbasamoyai.createbigcannons.index.CBCSoundEvents;

/** Audio-only equivalent of CBC's shell blast wave. No screen shake or world explosion. */
public final class InterceptionAudio {
    private static final int MAX_WAVES = 128;
    private static final int CBC_LIFETIME = 100;
    private static final ArrayDeque<Wave> waves = new ArrayDeque<>();
    private static ClientLevel world;

    private InterceptionAudio() {}

    static void offer(ClientLevel level, InterceptionPayload payload) {
        setWorld(level);
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(payload.center()) >=
                (double)payload.cbcRange() * payload.cbcRange()) return;
        if (waves.size() >= MAX_WAVES) waves.removeFirst();
        waves.addLast(new Wave(payload.center(), InterceptionPayload.cbcVolume(payload.power()),
            InterceptionPayload.audibleRange(payload.power(), payload.cbcRange()),
            .8f + level.random.nextFloat() * .4f));
    }

    static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        setWorld(mc.level);
        if (waves.isEmpty() || mc.player == null || mc.isPaused()) return;
        var config = CBCConfigs.client();
        boolean instant = config.isInstantaneousBlastEffect();
        double step = config.blastEffectDelaySpeed.getF() / 20.0;
        for (var it = waves.iterator(); it.hasNext();) {
            var wave = it.next();
            double first = instant ? 0 : wave.radius;
            wave.radius = instant ? wave.range : Math.min(wave.range, first + step);
            double distance = Math.sqrt(mc.player.distanceToSqr(wave.center));
            if (distance >= first && distance <= wave.radius && distance < wave.range) {
                mc.getSoundManager().play(new InterceptionSound(world, wave));
                it.remove();
            } else if (instant || wave.radius >= wave.range || ++wave.age >= CBC_LIFETIME) {
                it.remove();
            }
        }
    }

    private static void setWorld(ClientLevel level) {
        if (world == level) return;
        waves.clear();
        world = level;
    }

    /** Apply after SoundEngine establishes the attenuation distance and clamps category gain. */
    public static float mixGain(SoundInstance sound, float normal) {
        return sound instanceof InterceptionSound ? normal * .85f : normal;
    }

    private static final class Wave {
        final Vec3 center;
        final float volume, pitch;
        final double range;
        double radius;
        int age;
        Wave(Vec3 center, float volume, double range, float pitch) {
            this.center = center;
            this.volume = volume;
            this.range = range;
            this.pitch = pitch;
        }
    }

    private static final class InterceptionSound extends SimpleSoundInstance implements HasAirAbsorptionSound {
        InterceptionSound(ClientLevel level, Wave wave) {
            super(CBCSoundEvents.SHELL_EXPLOSION.getMainEvent(), SoundSource.BLOCKS, wave.volume, wave.pitch,
                net.minecraft.util.RandomSource.create(level.random.nextLong()), wave.center.x, wave.center.y, wave.center.z);
        }

        @Override public float getAirAbsorption() {
            return CBCConfigs.client().blastSoundAirAbsorption.get() ? 2 : 0;
        }
    }
}
