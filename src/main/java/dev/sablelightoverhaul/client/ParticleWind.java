package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.AfterSmokeModel.Point;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** One horizontal, world-space displacement shared by the particle systems each client tick. */
public final class ParticleWind {
    public enum Mode { FIXED, VARYING }

    private static final Point CALM = new Point(0, 0, 0);
    private static ModConfigSpec.EnumValue<Mode> mode;
    private static ModConfigSpec.DoubleValue direction, speed;
    private static ModConfigSpec.IntValue period;
    private static Point step = CALM;

    private ParticleWind() {}

    static void define(ModConfigSpec.Builder b) {
        mode = b.comment("Fixed wind or smooth variation around the configured direction and speed.")
            .defineEnum("wind.mode", Mode.FIXED);
        direction = b.comment("Direction the wind blows towards. 0 north (-Z), 90 east (+X), 180 south, 270 west.")
            .defineInRange("wind.direction", 0.0, 0.0, 360.0);
        speed = b.comment("Wind speed in blocks per second. Zero disables particle drift.")
            .defineInRange("wind.speed", 0.0, 0.0, 32.0);
        period = b.comment("Variation period in seconds, used only in varying mode.")
            .defineInRange("wind.period", 120, 15, 1800);
    }

    static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        step = level == null || mc.isPaused() ? CALM
            : sample(mode.get(), direction.get(), speed.get(), period.get(), level.getGameTime());
    }

    static Point step() { return step; }

    static Point sample(Mode mode, double direction, double speed, int period, long gameTick) {
        if (speed <= 0) return CALM;
        if (mode == Mode.VARYING) {
            long cycleTicks = period * 20L;
            double phase = 2 * Math.PI * Math.floorMod(gameTick, cycleTicks) / cycleTicks;
            direction += 25 * Math.sin(phase);
            speed *= 1 + .25 * Math.sin(phase + Math.PI / 3);
        }
        double angle = Math.toRadians(direction);
        double distance = speed / 20;
        return new Point(Math.sin(angle) * distance, 0, -Math.cos(angle) * distance);
    }
}
