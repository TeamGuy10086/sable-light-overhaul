package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.SableLightOverhaul;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The A/B switch for the tank-smoke rim, so one build can answer "which of the three candidates is
 * it" without three rebuilds.
 *
 * <p>{@code /slosmoke fixed} is the shipped contract. The other three each break one part of it:
 * {@code blend} pieces the old (double-premultiplied) edge, {@code depth} brings back the rim as an
 * occluder, {@code single} draws one lobe instead of three. Fire once per mode and compare the same
 * frame; if the rim is gone in {@code fixed} but back in {@code blend}, the blend is the cause, and
 * so on. {@code /slosmoke} with no argument only reports, which is what a bug report should quote.
 *
 * <p>{@code /slosmoke spawn [count] [power]} creates exactly {@code count} big-cannon thermal
 * puffs (default 1, charge reference 4) six blocks along the view direction, in a fresh smoke
 * group with no flash attached. One isolated puff is the repro for per-particle flicker reports:
 * with nothing else drawn, any change between two frames belongs to that one particle.
 *
 * <p>{@code /slosmoke trace} toggles the per-frame render trace (writes logs/slo-smoke-trace.csv
 * on toggle-off): the answer to "was the puff even drawn on the frame it vanished from".
 */
@EventBusSubscriber(modid = SableLightOverhaul.ID, value = Dist.CLIENT)
public final class SmokeDebugCommand {
    private SmokeDebugCommand() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        var root = Commands.literal("slosmoke").executes(context -> {
            report(context.getSource());
            return 1;
        });
        for (var mode : MuzzleParticleLayer.Mode.values()) {
            root.then(Commands.literal(mode.name().toLowerCase(Locale.ROOT)).executes(context -> {
                MuzzleParticleLayer.mode(mode);
                report(context.getSource());
                return 1;
            }));
        }
        root.then(Commands.literal("reset").executes(context -> {
            MuzzleParticleLayer.mode(MuzzleParticleLayer.Mode.FIXED);
            report(context.getSource());
            return 1;
        }));
        root.then(Commands.literal("trace").executes(context -> {
            String line = SmokeTrace.toggle();
            context.getSource().sendSuccess(() -> Component.literal(line), false);
            com.mojang.logging.LogUtils.getLogger().info(line);
            return 1;
        }));
        root.then(Commands.literal("spawn")
            .executes(context -> spawn(context.getSource(), 1, 4))
            .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                .executes(context -> spawn(context.getSource(),
                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count"), 4))
                .then(Commands.argument("power", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 128))
                    .executes(context -> spawn(context.getSource(),
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count"),
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "power"))))));
        root.then(Commands.literal("bench")
            .executes(context -> SmokeBenchmark.start(32))
            .then(Commands.literal("stop").executes(context -> SmokeBenchmark.stop()))
            .then(Commands.literal("shot")
                .executes(context -> SmokeBenchmark.startShot(SmokeBenchmark.SHOT_PUFFS))
                .then(Commands.argument("maximum", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, SmokeBenchmark.SHOT_PUFFS))
                    .executes(context -> SmokeBenchmark.startShot(
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "maximum")))))
            .then(Commands.literal("layers")
                .then(Commands.argument("maximum", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                    .executes(context -> SmokeBenchmark.startLayers(
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "maximum")))))
            .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 128))
                .executes(context -> SmokeBenchmark.start(
                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count")))));
        root.then(Commands.literal("battery").executes(c -> SmokeBatteryBenchmark.start())
            .then(Commands.literal("sweep").executes(c -> SmokeBatteryBenchmark.startSweep()))
            .then(Commands.literal("tiers").executes(c -> SmokeBatteryBenchmark.startTiers()))
            .then(Commands.literal("stop").executes(c -> SmokeBatteryBenchmark.stop())));
        root.then(Commands.literal("resolution").then(Commands.argument("divisor",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,16))
            .executes(c -> {int d=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"divisor");
                if(Integer.bitCount(d)!=1){c.getSource().sendFailure(Component.literal("Use 1, 2, 4, 8 or 16"));return 0;}
                SmokeResolution.override=d;
                c.getSource().sendSuccess(()->Component.literal("Smoke resolution divisor="+d+"; applies without shaders or with patched Photon Advanced."),false);return 1;})));
        root.then(Commands.literal("resolution").then(Commands.literal("auto").executes(c -> {
            SmokeResolution.override=0;
            c.getSource().sendSuccess(()->Component.literal("Smoke resolution follows the F9 slider."),false);
            return 1;
        })));
        event.getDispatcher().register(root);
    }

    /** Exactly {@code count} puffs in one burst, six blocks ahead at eye height; no shot, no flash. */
    private static int spawn(CommandSourceStack source, int count, int power) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            source.sendFailure(Component.literal("SLO smoke spawn: no client level"));
            return 0;
        }
        net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
        net.minecraft.world.phys.Vec3 origin = mc.player.getEyePosition().add(look.scale(6));
        var group = SmokeTracker.create(mc.level.getGameTime());
        BigSmokeParticle.emit(mc.level, origin, look, group, 0, count, 1, power);
        String line = "SLO smoke spawn: " + count + " puff(s) at "
            + String.format(Locale.ROOT, "%.1f %.1f %.1f", origin.x, origin.y, origin.z)
            + " power=" + power;
        source.sendSuccess(() -> Component.literal(line), false);
        com.mojang.logging.LogUtils.getLogger().info(line);
        return 1;
    }

    private static void report(CommandSourceStack source) {
        var mode = MuzzleParticleLayer.mode();
        String line = "SLO smoke A/B: " + mode.name() + " (" + mode.detail() + ")"
            + " | " + MuzzleParticleLayer.describe()
            + " | pack=" + (IrisCompat.shaderPackInUse() ? "on" : "off")
            + " | modes: fixed, blend, depth, single";
        source.sendSuccess(() -> Component.literal(line), false);
        com.mojang.logging.LogUtils.getLogger().info(line);
    }
}
