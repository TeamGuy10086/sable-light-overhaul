package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.NavalSoundPayload;
import java.util.Comparator;
import java.util.PriorityQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import rbasamoyai.createbigcannons.index.CBCSoundEvents;

public final class NavalAudio {
    private static final int MAX_ARRIVALS = 2048;
    private record Arrival(long tick, NavalSoundPayload sound, double distance) {}

    private static final PriorityQueue<Arrival> arrivals = new PriorityQueue<>(Comparator.comparingLong(Arrival::tick));
    private static ClientLevel world;
    private static long tick;
    private static float duck;

    private NavalAudio() {}

    static void receive(NavalSoundPayload sound) {
        var mc = Minecraft.getInstance();
        updateWorld(mc.level);
        if (world == null || !world.dimension().location().equals(sound.dimension())
                || !NavalSoundPayload.validPosition(sound.position()) || !enabled(sound)) return;
        double distance = mc.gameRenderer.getMainCamera().getPosition().distanceTo(sound.position());
        if (!Double.isFinite(distance) || distance >= range(sound)) return;
        long delay = delay(distance, NavalAudioConfig.SPEED.get());
        var arrival = new Arrival(tick + delay, sound, distance);
        if (delay == 0 && !mc.isPaused()) play(mc, arrival);
        else if (arrivals.size() < MAX_ARRIVALS) arrivals.add(arrival);
    }

    static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        updateWorld(mc.level);
        if (world == null || mc.isPaused()) return;
        tick++;
        duck = Math.max(0, duck - .035f);
        while (!arrivals.isEmpty() && arrivals.peek().tick() <= tick) play(mc, arrivals.remove());
    }

    private static void updateWorld(ClientLevel current) {
        if (world == current) return;
        world = current;
        arrivals.clear();
        tick = 0;
        duck = 0;
    }

    public static float vanillaGain() { return 1 - duck; }

    static long delay(double distance, double speed) {
        // Bound tiny positive configured speeds to a minute of outstanding sound.
        return speed == 0 ? 0 : Math.min(1200, Math.round(distance * 20 / speed));
    }

    static boolean far(double distance, int nearRange) { return distance > nearRange; }

    static float attenuation(double distance, int range, int falloff) {
        double remaining = Math.clamp(1 - distance / range, 0, 1);
        return (float) (remaining * remaining / (1 + distance / falloff));
    }

    private static boolean enabled(NavalSoundPayload sound) {
        return report(sound) ? NavalAudioConfig.REPORTS.get() : NavalAudioConfig.IMPACTS.get();
    }

    private static boolean report(NavalSoundPayload sound) { return sound.kind() == NavalSoundPayload.Kind.REPORT; }

    private static int range(NavalSoundPayload sound) {
        return report(sound) ? NavalAudioConfig.RANGE.get() : NavalSoundPayload.IMPACT_RANGE;
    }

    private static void play(Minecraft mc, Arrival arrival) {
        var sound = arrival.sound();
        double distance = arrival.distance();
        if (!enabled(sound) || distance >= range(sound)) return;
        var manager = mc.getSoundManager();
        boolean report = report(sound);
        boolean far = report && far(distance, NavalAudioConfig.NEAR.get());
        var id = report ? sound.profile().reportEvent(far) : sound.profile().impactEvent(sound.kind());
        var event = manager.getSoundEvent(id);
        if (event == null && report && !far) {
            id = (switch (sound.profile()) {
                case MM20 -> CBCSoundEvents.FIRE_MACHINE_GUN;
                case MM37, MM40 -> CBCSoundEvents.FIRE_AUTOCANNON;
                default -> CBCSoundEvents.FIRE_BIG_CANNON;
            }).getMainEvent().getLocation();
            event = manager.getSoundEvent(id);
        }
        if (event == null || event.getWeight() == 0) return;

        double gain = report ? NavalAudioConfig.REPORT_GAIN.get() : NavalAudioConfig.IMPACT_GAIN.get();
        float volume = (float) (attenuation(distance, range(sound), report ? 160 : 48) * gain);
        if (volume <= 0 || mc.options.getSoundSourceVolume(SoundSource.BLOCKS) <= 0) return;
        int duckRange = NavalAudioConfig.DUCK_RANGE.get();
        if (report && distance < duckRange) {
            float depth = (float) (sound.profile().duckDepth() * NavalAudioConfig.DUCK_GAIN.get());
            duck = Math.max(duck, depth * (1 - (float) distance / duckRange));
        }
        manager.play(new NavalSound(id, sound.position(), volume));
    }

    private static final class NavalSound extends AbstractSoundInstance {
        NavalSound(ResourceLocation id, Vec3 position, float gain) {
            super(id, SoundSource.BLOCKS, RandomSource.create());
            x = position.x;
            y = position.y;
            z = position.z;
            volume = gain;
            attenuation = SoundInstance.Attenuation.NONE;
        }
    }
}
