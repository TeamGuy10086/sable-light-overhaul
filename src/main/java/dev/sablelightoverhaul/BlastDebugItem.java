package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.SourceAnchor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = SableLightOverhaul.ID)
public final class BlastDebugItem extends Item {
    private static final String TEXT = "sablelightoverhaul.blast_debug.";
    private static final int DEFAULT_POWER = 24;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final ThreadLocal<Detonation> PENDING = new ThreadLocal<>();
    private static final WeakHashMap<ServerLevel, ArrayList<Approaching>> APPROACHES = new WeakHashMap<>();
    private static final WeakHashMap<ServerLevel, List<BuriedImpact>> BURIED_IMPACTS = new WeakHashMap<>();

    private record BuriedImpact(Vec3 center, float power, List<Outlet> outlets, int destroyed) {
        void send(ServerLevel level) {
            Outlet opening = firstOpenOutlet(outlets, center, (from, to) -> clearPath(level, from, to));
            Outlet outlet = opening == null ? outlets.getFirst() : opening;
            Vec3 axis = opening == null ? Vec3.ZERO : ventAxis(center, outlet);
            int color = opening == null ? outlet.color
                : BlastPreviewPayload.ventDustColor(outlet.color, center.distanceTo(outlet.point));
            BlastPreviewPayload.Impact.broadcast(level, BlastPreviewPayload.Kind.BURIED,
                center, outlet.point, axis, power, BlastPreviewPayload.withDustHeight(color, power, destroyed));
        }
    }

    private enum Mode {
        GROUND("ground", 0), BURIED_3("buried_3", -3), BURIED_5("buried_5", -5),
        BURIED_10("buried_10", -10), AIR_10("air_10", 10), AIR_30("air_30", 30), AIR_50("air_50", 50),
        AIR_100("air_100", 100),
        INTERCEPT_10("interception_10", 10, true), INTERCEPT_30("interception_30", 30, true),
        INTERCEPT_50("interception_50", 50, true), INTERCEPT_100("interception_100", 100, true),
        BURIED_1("buried_1", -1), BURIED_2("buried_2", -2);

        // Keep saved ordinals stable while placing the shallow modes next to ground mode.
        private static final List<Mode> SCROLL_ORDER = List.of(GROUND, BURIED_1, BURIED_2,
            BURIED_3, BURIED_5, BURIED_10, AIR_10, AIR_30, AIR_50, AIR_100,
            INTERCEPT_10, INTERCEPT_30, INTERCEPT_50, INTERCEPT_100);

        final String key;
        final int offset;
        final boolean interception;

        Mode(String key, int offset) { this(key, offset, false); }

        Mode(String key, int offset, boolean interception) {
            this.key = key;
            this.offset = offset;
            this.interception = interception;
        }

        Component label() { return Component.translatable(TEXT + key); }
    }

    private record Settings(Mode mode, int power) {
        static Settings get(ItemStack stack) {
            var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            int mode = Math.clamp(data.getInt("blast_mode"), 0, Mode.values().length - 1);
            int power = data.contains("blast_power") ? Math.clamp(data.getInt("blast_power"), 1, 48) : DEFAULT_POWER;
            return new Settings(Mode.values()[mode], power);
        }
    }

    private record Pending(ServerPlayer player, Vec3 center, Vec3 surface, Vec3 normal, float power,
                           BlastPreviewPayload.Kind kind) {}

    private record Approaching(Pending shot, long detonateAt) {}

    private static final class Detonation {
        final Pending shot;
        List<Outlet> outlets;
        int destroyed = -1;

        Detonation(Pending shot) { this.shot = shot; }
    }

    public BlastDebugItem(Properties properties) { super(properties); }

    static void adjust(ServerPlayer player, BlastPreviewPayload.Adjust adjustment) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlastDebugItem)) return;
        if (!allowed(player)) return;
        Settings current = Settings.get(stack);
        int step = adjustment.next() ? 1 : -1;
        int index = current.mode.ordinal();
        int power = current.power;
        if (adjustment.power()) power = Math.clamp(power + step, 1, 48);
        else index = Mode.SCROLL_ORDER.get(Math.floorMod(Mode.SCROLL_ORDER.indexOf(current.mode) + step, Mode.SCROLL_ORDER.size())).ordinal();
        final int modeIndex = index;
        final int storedPower = power;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.putInt("blast_mode", modeIndex);
            data.putInt("blast_power", storedPower);
        });
        player.displayClientMessage(Component.translatable(TEXT + "settings", Mode.values()[index].label(), power), true);
    }

    private static boolean allowed(ServerPlayer player) {
        if (player.isCreative() && player.hasPermissions(2)) return true;
        player.displayClientMessage(Component.translatable(TEXT + "permission"), true);
        return false;
    }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer player) fire(player, context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer serverPlayer) fire(serverPlayer, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void fire(ServerPlayer player, ItemStack stack) {
        if (!allowed(player) || player.getCooldowns().isOnCooldown(this) || PENDING.get() != null) return;
        ServerLevel level = player.serverLevel();
        Settings settings = Settings.get(stack);
        int offset = settings.mode.offset;
        Vec3 eye = player.getEyePosition();
        Vec3 center;
        Vec3 surface = Vec3.ZERO;
        Vec3 normal = UP;
        var kind = BlastPreviewPayload.Kind.AIR;
        if (offset > 0) {
            Vec3 look = player.getLookAngle();
            center = eye.add(look.scale(offset));
            if (settings.mode.interception) {
                kind = BlastPreviewPayload.Kind.INTERCEPTION;
                normal = tangentMotion(look, level.random.nextDouble(), level.random.nextDouble());
            } else {
                Surface hit = aimedSurface(player, offset, true);
                if (hit != null) {
                    kind = BlastPreviewPayload.Kind.HULL;
                    surface = hit.point;
                    normal = hit.normal;
                    center = surface.add(normal.scale(.04));
                }
            }
        } else {
            Surface hit = aimedSurface(player, 128, false);
            if (hit == null) {
                player.displayClientMessage(Component.translatable(TEXT + "aim"), true);
                return;
            }
            surface = hit.point;
            normal = hit.normal;
            Vec3 blockCenter = hit.blockCenter;
            if (offset < 0) {
                center = blockCenter.add(0, offset, 0);
                kind = BlastPreviewPayload.Kind.BURIED;
            } else {
                center = surface.add(normal.scale(.04));
                kind = hit.hull ? BlastPreviewPayload.Kind.HULL : BlastPreviewPayload.Kind.GROUND;
            }
        }
        BlockPos at = BlockPos.containing(center);
        if (level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(at) || !level.hasChunkAt(at)) {
            player.displayClientMessage(Component.translatable(TEXT + "unavailable"), true);
            return;
        }
        var shot = new Pending(player, center, surface, normal, settings.power, kind);
        if (kind == BlastPreviewPayload.Kind.INTERCEPTION) {
            int ticks = BlastPreviewPayload.Approach.TICKS;
            player.getCooldowns().addCooldown(this, ticks + 4);
            APPROACHES.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new Approaching(shot, level.getGameTime() + ticks));
            BlastPreviewPayload.Approach.broadcast(level, center, normal);
        } else {
            player.getCooldowns().addCooldown(this, 8);
            detonate(level, shot);
        }
    }

    private record Surface(Vec3 point, Vec3 normal, Vec3 blockCenter, boolean hull) {}

    private static Surface aimedSurface(ServerPlayer player, double range, boolean hullOnly) {
        if (!(player.pick(range, 1, false) instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) return null;
        ServerLevel level = player.serverLevel();
        BlockPos block = hit.getBlockPos();
        Vec3 point = hit.getLocation();
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 center = Vec3.atCenterOf(block);
        SourceAnchor body = SourceAnchor.capture(level, block);
        if (body.ship() == null) return hullOnly ? null : new Surface(point, normal, center, false);
        Vec3 world = body.world(level, block, point);
        Vec3 tip = body.world(level, block, point.add(normal));
        Vec3 worldCenter = body.world(level, block, center);
        if (world == null || tip == null || worldCenter == null) return null;
        return new Surface(world, tip.subtract(world).normalize(), worldCenter, true);
    }

    static Vec3 tangentMotion(Vec3 look, double turn, double speedFraction) {
        Vec3 side = look.cross(Math.abs(look.y) > .9 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 across = look.cross(side).normalize();
        double angle = turn * Math.PI * 2;
        return side.scale(Math.cos(angle)).add(across.scale(Math.sin(angle))).scale(.65 + 1.75 * speedFraction);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var buriedImpacts = BURIED_IMPACTS.remove(level);
        if (buriedImpacts != null) buriedImpacts.forEach(impact -> impact.send(level));
        var pending = APPROACHES.get(level);
        if (pending == null) return;
        for (var it = pending.iterator(); it.hasNext();) {
            var entry = it.next();
            if (level.getGameTime() < entry.detonateAt) continue;
            it.remove();
            var shot = entry.shot;
            if (!shot.player.isRemoved() && shot.player.serverLevel() == level
                && shot.player.isCreative() && shot.player.hasPermissions(2)
                && level.hasChunkAt(BlockPos.containing(shot.center))) detonate(level, shot);
        }
        if (pending.isEmpty()) APPROACHES.remove(level);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            APPROACHES.remove(level);
            BURIED_IMPACTS.remove(level);
        }
    }

    private static void detonate(ServerLevel level, Pending shot) {
        var detonation = new Detonation(shot);
        PENDING.set(detonation);
        try {
            level.explode(shot.player, shot.center.x, shot.center.y, shot.center.z, shot.power, Level.ExplosionInteraction.TNT);
        } finally {
            PENDING.remove();
        }
        if (detonation.outlets != null) sendBuriedPreview(level, shot, detonation.outlets, detonation.destroyed);
    }

    static boolean onDetonate(ExplosionEvent.Detonate event) {
        var detonation = PENDING.get();
        if (detonation == null) return false;
        Pending shot = detonation.shot;
        if (event.getLevel() != shot.player.serverLevel()
            || event.getExplosion().getDirectSourceEntity() != shot.player
            || !event.getExplosion().center().equals(shot.center)) return false;
        PENDING.remove();

        ServerLevel level = shot.player.serverLevel();
        if (shot.kind == BlastPreviewPayload.Kind.BURIED) {
            // Capture the old surface and its material before any blocks are removed.
            detonation.outlets = captureOutlets(level, shot);
            if (event.getExplosion() instanceof BlastDamageTracking tracking)
                tracking.slo$afterBlockDestruction(destroyed -> detonation.destroyed = destroyed);
            return true;
        }
        Vec3 axis = shot.kind == BlastPreviewPayload.Kind.GROUND
            ? surfaceOpening(level, shot.center, shot.normal) : shot.normal;
        if (shot.kind == BlastPreviewPayload.Kind.GROUND && event.getExplosion() instanceof BlastDamageTracking tracking) {
            tracking.slo$afterBlockDestruction(destroyed -> {
                BlastPreviewPayload.broadcast(level, shot.kind, shot.center, axis, shot.power,
                    BlastPreviewPayload.withDustHeight(0, shot.power, destroyed));
                reportFired(shot);
            });
        } else {
            BlastPreviewPayload.broadcast(level, shot.kind, shot.center, axis, shot.power, 0);
            reportFired(shot);
        }
        return true;
    }

    static void sendImpactVisual(dev.sablelightoverhaul.api.EffectApi.ImpactEvent impact, net.minecraft.world.level.Explosion explosion) {
        float power = explosion.radius();
        if (!Float.isFinite(power) || power < BlastPreviewPayload.Impact.MIN_POWER
            || !(impact.level() instanceof ServerLevel level)) return;
        Vec3 center = impact.center();
        var kind = switch (impact.hitClass()) {
            case GROUND -> BlastPreviewPayload.Kind.GROUND;
            case BURIED -> BlastPreviewPayload.Kind.BURIED;
            case BLOCK, ENTITY -> BlastPreviewPayload.Kind.HULL;
            default -> BlastPreviewPayload.Kind.AIR;
        };
        if (kind == BlastPreviewPayload.Kind.BURIED) {
            // Capture surface colors before block removal, then check connectivity after the blast.
            List<Outlet> outlets = captureGroundOutlets(level, center);
            java.util.function.IntConsumer queue = destroyed -> BURIED_IMPACTS.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new BuriedImpact(center, power, outlets, destroyed));
            if (explosion instanceof BlastDamageTracking tracking) tracking.slo$afterBlockDestruction(queue);
            else queue.accept(-1);
            return;
        }
        Vec3 direction = kind == BlastPreviewPayload.Kind.GROUND ? surfaceOpening(level, center, UP) : Vec3.ZERO;
        if (kind == BlastPreviewPayload.Kind.GROUND && explosion instanceof BlastDamageTracking tracking) {
            tracking.slo$afterBlockDestruction(destroyed -> BlastPreviewPayload.Impact.broadcast(level, kind,
                center, center, direction, power, BlastPreviewPayload.withDustHeight(0, power, destroyed)));
        } else {
            BlastPreviewPayload.Impact.broadcast(level, kind, center, center, direction, power, 0);
        }
    }

    private static void sendBuriedPreview(ServerLevel level, Pending shot, List<Outlet> outlets, int destroyed) {
        // The ray list estimates damage. The actual post-blast geometry decides which outlet opened.
        var outlet = firstOpenOutlet(outlets, shot.center,
            (from, to) -> clearPath(level, from, to));
        if (outlet != null) {
            Vec3 axis = ventAxis(shot.center, outlet);
            int color = BlastPreviewPayload.ventDustColor(outlet.color, shot.center.distanceTo(outlet.point));
            BlastPreviewPayload.broadcast(level, shot.kind, outlet.point, axis, shot.power,
                BlastPreviewPayload.withDustHeight(color, shot.power, destroyed));
            reportFired(shot);
        } else {
            var primary = outlets.getFirst();
            BlastPreviewPayload.broadcast(level, shot.kind, primary.point, Vec3.ZERO, shot.power, primary.color);
            shot.player.displayClientMessage(Component.translatable(TEXT + "sealed"), true);
        }
    }

    private static void reportFired(Pending shot) {
        shot.player.displayClientMessage(Component.translatable(TEXT + "fired",
            Component.translatable(TEXT + "kind." + shot.kind.name().toLowerCase(java.util.Locale.ROOT)), (int)shot.power), true);
    }

    private static Vec3 surfaceOpening(ServerLevel level, Vec3 center, Vec3 normal) {
        Vec3 seed = new Vec3(normal.x, 0, normal.z).normalize();
        Vec3 start = center.add(0, .5, 0);
        Vec3 clearance = Vec3.ZERO;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 end = start.add(direction.scale(6)).add(0, .65, 0);
            double reach = clearance(level, start, end);
            clearance = clearance.add(direction.scale(reach * reach));
        }
        Vec3 opening = clearance.scale(.6).add(seed);
        return opening.lengthSqr() < .25 ? Vec3.ZERO : opening.normalize();
    }

    private static double clearance(ServerLevel level, Vec3 start, Vec3 end) {
        if (!level.hasChunkAt(BlockPos.containing(end))) return 0;
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? 1
            : Math.min(1, start.distanceTo(hit.getLocation()) / start.distanceTo(end));
    }

    private static boolean clearPath(ServerLevel level, Vec3 start, Vec3 end) {
        return clearance(level, start, end) >= .999;
    }

    private static int dustColor(ServerLevel level, BlockPos block) {
        if (!level.hasChunkAt(block)) return 0x8D7258;
        int color = level.getBlockState(block).getMapColor(level, block).col;
        return color != 0 ? color : 0x8D7258;
    }

    record Outlet(Vec3 point, Vec3 outward, int color) {}

    private static List<Outlet> captureOutlets(ServerLevel level, Pending shot) {
        // Sample the aimed face, including a vertical crater wall, in its own plane.
        Vec3 side = shot.normal.cross(Math.abs(shot.normal.y) > .9 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 across = shot.normal.cross(side).normalize();
        var outlets = new ArrayList<Outlet>(9);
        outlets.add(outletAt(level, shot.surface, shot.normal));
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                if (x != 0 || y != 0) outlets.add(outletAt(level, shot.surface.add(side.scale(x)).add(across.scale(y)), shot.normal));
            }
        }
        return outlets;
    }

    private static Vec3 ventAxis(Vec3 center, Outlet outlet) {
        return outlet.point.subtract(center).normalize().add(outlet.outward).normalize();
    }

    private static List<Outlet> captureGroundOutlets(ServerLevel level, Vec3 center) {
        var outlets = new ArrayList<Outlet>(9);
        outlets.add(groundOutlet(level, center));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if ((x != 0 || z != 0) && level.hasChunkAt(BlockPos.containing(center.add(x, 0, z)))) {
                    outlets.add(groundOutlet(level, center.add(x, 0, z)));
                }
            }
        }
        return outlets;
    }

    private static Outlet groundOutlet(ServerLevel level, Vec3 column) {
        BlockPos block = BlockPos.containing(column);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
            block.getX(), block.getZ());
        return outletAt(level, new Vec3(column.x, y, column.z), UP);
    }

    private static Outlet outletAt(ServerLevel level, Vec3 face, Vec3 normal) {
        var material = BlockPos.containing(face.subtract(normal.scale(.025)));
        return new Outlet(face.add(normal.scale(.40)), normal, dustColor(level, material));
    }

    static Outlet firstOpenOutlet(List<Outlet> candidates, Vec3 center, java.util.function.BiPredicate<Vec3, Vec3> clear) {
        var exposed = candidates.stream()
            .filter(outlet -> center.distanceToSqr(outlet.point) <= 64 * 64)
            .filter(outlet -> clear.test(outlet.point, outlet.point.add(outlet.outward.scale(1.5))))
            .sorted(java.util.Comparator.comparingDouble(outlet -> center.distanceToSqr(outlet.point)))
            .toList();
        for (Outlet outlet : exposed) {
            if (clear.test(center, outlet.point)) return outlet;
        }
        if (exposed.isEmpty()) return null;

        // A broken crater wall can leave a ledge in the straight ray while the opening is connected around it.
        var area = new AABB(center, center);
        for (Outlet outlet : exposed) area = area.minmax(new AABB(outlet.point, outlet.point));
        area = area.inflate(2);
        var visited = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        var root = BlockPos.containing(center);
        if (center.distanceToSqr(Vec3.atCenterOf(root)) > 1e-8 && !clear.test(center, Vec3.atCenterOf(root))) return null;
        pending.add(root);
        visited.add(root);
        while (!pending.isEmpty()) {
            BlockPos next = pending.removeFirst();
            Vec3 point = Vec3.atCenterOf(next);
            for (Outlet outlet : exposed) {
                if (point.distanceToSqr(outlet.point) < 2.26 && clear.test(point, outlet.point)) return outlet;
            }
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = next.relative(direction);
                Vec3 neighbor = Vec3.atCenterOf(adjacent);
                if (area.contains(neighbor) && !visited.contains(adjacent) && clear.test(point, neighbor)) {
                    pending.addLast(adjacent);
                    visited.add(adjacent);
                }
            }
        }
        return null;
    }
}
