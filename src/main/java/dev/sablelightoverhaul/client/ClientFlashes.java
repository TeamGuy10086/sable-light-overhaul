package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.FlashPayload;
import dev.sablelightoverhaul.FlashStore;
import dev.sablelightoverhaul.SableLightOverhaul;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = SableLightOverhaul.ID, value = Dist.CLIENT)
public final class ClientFlashes {
    public static final FlashStore STORE = new FlashStore(64);
    private static volatile ClientLevel owner;
    private static dev.sablelightoverhaul.LightingMode effective=dev.sablelightoverhaul.LightingMode.Off;
    private static dev.sablelightoverhaul.LightingMode lastRequested;
    private static int visualEpoch;
    public static int visualEpoch() { return visualEpoch; }
    public static boolean effectsEnabled() { return effective!=dev.sablelightoverhaul.LightingMode.Off && MuzzleClientConfig.MODE.get()!=dev.sablelightoverhaul.LightingMode.Off; }
    /** True when the active tier expects the shader pack patch, so the screen-space overlay stands down. */
    public static boolean inPackLighting() { return effective==dev.sablelightoverhaul.LightingMode.Advanced; }
    public static boolean sloOnly() {
        return effective == dev.sablelightoverhaul.LightingMode.SloOnly && !IrisCompat.shaderPackInUse();
    }

    /** The tier actually in use after fallbacks, for the config screen to hide rows that cannot read. */
    public static dev.sablelightoverhaul.LightingMode effectiveMode() { return effective; }
    private static void selectMode(ClientLevel level) {
        var requested=MuzzleClientConfig.MODE.get();
        boolean pack = IrisCompat.shaderPackInUse();
        var next = requested.resolve(GpuLightRenderer.ready(), pack, pack && IrisCompat.sloPackInUse());
        boolean changed=effective!=next;
        if(changed) {
            AdvancedLights.clear(); GpuLightRenderer.clear(); STORE.clear(); SmokeTracker.clear(); GroundDustParticle.clearHaze(); visualEpoch++;
            effective=next;
        }
        if(changed || requested!=lastRequested) {
            String message="Sable Light Overhaul: requested="+requested+", active="+next;
            com.mojang.logging.LogUtils.getLogger().info(message);
            if(Minecraft.getInstance().player!=null) Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sablelightoverhaul.mode.active", modeLabel(requested), modeLabel(next)),false);
        }
        lastRequested=requested;
    }
    private static net.minecraft.network.chat.Component modeLabel(dev.sablelightoverhaul.LightingMode mode) {
        return mode == dev.sablelightoverhaul.LightingMode.DynamicOnly
                ? net.minecraft.network.chat.Component.translatable("sablelightoverhaul.mode.screenSpaceApproximation")
                : net.minecraft.network.chat.Component.literal(mode.name());
    }
    /** Positions whose own vanilla light a registered source has given up (in-pack path only). */
    private static Set<Long> takenOver = Set.of();
    /** Where the sources were last seen, so a move can be turned into a pair of single-block updates. */
    private static List<Long> lastTaken = List.of();
    private static long lastTakeOverLog = Long.MIN_VALUE;
    private ClientFlashes() {}
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        // The light field's texture has to be visible to Iris before the shader pack is parsed, not when the
        // pipeline is built. Iris resolves every customTexture in shaders.properties while it loads the pack, and
        // that happens before a world exists; registering the dynamic name from addLevelSamplers was therefore
        // always too late, and the pack found nothing to bind. This runs from the first client tick, which is
        // still before any world can be joined.
        if (MuzzleClientConfig.MODE.get() != dev.sablelightoverhaul.LightingMode.SloOnly
                || IrisCompat.shaderPackInUse()) LightFields.ensureTexture();
        ClientLevel level = Minecraft.getInstance().level;
        if (owner != level) reset(level);
        if (level == null || Minecraft.getInstance().isPaused()) return;
        selectMode(level);
        // Released as well as taken, so turning the option off hands the lamp its own light back.
        takeOverSourceLight(level);
        MuzzleVisualProbe.tick(level.getGameTime());
        if(!effectsEnabled()) return;
        GroundDustParticle.tickHaze();
        STORE.expire(level.getGameTime());
        SmokeTracker.tick(level);
        AdvancedLights.gpuUpdate(level);
    }
    private static void reset(ClientLevel level) {
        GroundDustParticle.clearHaze();
        ExtraLights.worldChanged(level);
        // Old world's light storage is discarded with that world; never touch a new world with old positions.
        AdvancedLights.clear(); GpuLightRenderer.clear(); effective=dev.sablelightoverhaul.LightingMode.Off; lastRequested=null; visualEpoch++;
        takenOver = Set.of(); lastTaken = List.of(); PENDING_REMOVAL.clear();
        lastTakeOverLog = Long.MIN_VALUE; STORE.clear(); SmokeTracker.clear(); owner = level;
    }
    public static boolean prepareEffects(ClientLevel level) {
        if (owner != level) reset(level);
        selectMode(level);
        return effectsEnabled();
    }
    public static void receive(FlashPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(payload.dimension())) return;
        if (owner != level) reset(level);
        selectMode(level);
        if(!Float.isFinite(payload.charge()) || payload.charge()<0 || !Float.isFinite(payload.plumeSize()) || payload.plumeSize()<=0) return;
        if(!effectsEnabled() || !FlashStore.valid(payload.lightX(),payload.lightY(),payload.lightZ(),payload.dx(),payload.dy(),payload.dz(),payload.kind())) return;
        // Which cannon fired, resolved before anything is built from the shot. A shot names its mount, the mount
        // names the cannon, and the cannon names its values -- so this string is what every per-cannon value on
        // this path is looked up with. It used to be resolved further down, after the light had already been
        // built from this client's configuration, which is why the light-following keys on the page did nothing.
        String cannon = payload.hasMount()
                ? ProfileCache.cannonAt(payload.dimension(), payload.mountPos()).orElse(null)
                : null;
        var pulse=payload.kind()==2 ? MuzzleClientConfig.TANK_PROFILE.curve(payload.hasProfile()?payload.profile():null)
            : payload.kind()==0 ? MuzzleClientConfig.bigLight(cannon) : MuzzleClientConfig.autoLight(cannon);
        var layout=payload.kind()==0 ? MuzzleClientConfig.bigLayout()
            : dev.sablelightoverhaul.LightLayout.AUTO;
        if(payload.kind()==0) {
            var stages=dev.sablelightoverhaul.MuzzleLightTiming.big(pulse,layout,
                MuzzleClientConfig.BLAST_PROFILE.curve(payload.hasProfile()?payload.profile():null),
                MuzzleClientConfig.SMOKE_PROFILE.curve(payload.hasProfile()?payload.profile():null));
            pulse=stages.lifetime();layout=stages.layout();
        }
        if (!STORE.add(payload.x(), payload.y(), payload.z(), payload.dx(), payload.dy(), payload.dz(),
                payload.kind(), level.getGameTime(), pulse,
                layout,
                new FlashStore.Emitter(payload.emitterId(),payload.lightX(),payload.lightY(),payload.lightZ(),payload.charge(),payload.plumeSize(),payload.hasProfile()?payload.profile():null))) return;
        if(payload.kind()==0) FireballParticle.bigMuzzle(level,payload.x(),payload.y(),payload.z(),payload.dx(),payload.dy(),payload.dz(),
            payload.charge(),MuzzleClientConfig.BLAST_PROFILE.curve(payload.hasProfile()?payload.profile():null).durationTicks(),
            payload.fireballSizeScale());
        // Big cannons retain native smoke/flame. Autocannons use an oriented flame, never a FLASH billboard.
        if (payload.kind() == 1 || payload.kind() == 3) Minecraft.getInstance().particleEngine.createParticle(SableLightOverhaul.AUTO_MUZZLE.get(),
                payload.x(), payload.y(), payload.z(), payload.dx(), payload.dy(), payload.dz());
        // One lighting submission per tick; particle visuals remain immediate.
    }
    public static boolean owns(ClientLevel level) { return effectsEnabled() && level == owner && level == Minecraft.getInstance().level; }
    /**
     * Whether a registered lamp has given its own vanilla light up, so the light engine must treat
     * that block as emitting nothing at all.
     */
    public static boolean takenOver(Object world, long pos) {
        return world == owner && owner != null && takenOver.contains(pos);
    }
    /**
     * Hands the vanilla light of every registered source over to the injected colour.
     *
     * <p>The white pool around a lamp is the lamp's own block light, not ours, and a shader can only
     * ever add to it. Making the block stop emitting is what removes it: what is left there is the
     * colour the in-pack path adds. Only the positions that actually entered or left the set this tick
     * are relit, so a lamp that stays put costs one update when it appears and nothing afterwards.
     *
     * <p>This has nothing to do with shadow casting. Which lights cast is decided in the pack by
     * {@code i < sloShadowCount && tints[i].w <= 0.5}, and {@code tints[i].w} is the
     * {@code selfCull} flag written by {@link IrisMuzzleBridge}: registered lamps and carried lights
     * are the ones marked, so they never cast, while the muzzle/tracer lights that do cast are
     * untouched by anything here. Zeroing a block's emission changes no light's position, shape,
     * brightness, order or {@code selfCull}, so the shadow slots are filled by exactly the same lights
     * as before.
     */
    private static void takeOverSourceLight(ClientLevel level) {
        // Additions are taken at once; removals wait. A source leaving the client's list is almost always the camera or
        // the activation distance rather than the lamp going away, and acting on it immediately made the set churn every
        // tick while flying near a ship: each flip re-checked a light and rebuilt its section, and a churn that never
        // settles is a stutter that becomes a freeze.
        Set<Long> raw = new HashSet<>();
        if (inPackLighting() && ExtraConfig.GPU_TAKEOVER.get()) {
            ExtraLights.forEachSourceBlock(level, pos -> raw.add(pos));
        }
        long now = level.getGameTime();
        Set<Long> next = new HashSet<>(lastTaken);
        for (long pos : raw) {
            next.add(pos);
            PENDING_REMOVAL.remove(pos);
        }
        for (long pos : lastTaken) {
            if (raw.contains(pos)) continue;
            if (!inPackLighting()) {
                next.remove(pos);
                PENDING_REMOVAL.remove(pos);
                continue;
            }
            long since = PENDING_REMOVAL.computeIfAbsent(pos, key -> now);
            if (now - since >= REMOVAL_GRACE_TICKS) {
                next.remove(pos);
                PENDING_REMOVAL.remove(pos);
            }
        }
        // A lamp that moved one block is one position out and one position in. Working out that pair
        // and touching only those two blocks is the whole job; the previous version treated any change
        // as "the set is new" and re-checked and re-meshed every source in the level, so a body with a
        // few lamps on it rebuilt dozens of sections per tick, forever, even while standing still.
        List<Long> after = new ArrayList<>(next);
        List<Long> delta = symmetricDifference(lastTaken, after);
        // Nothing entered or left the set: the previous choice is still the right one, and no light has
        // to be re-checked or any section rebuilt. This is the path a stationary lamp takes every tick.
        if (delta.isEmpty()) return;
        lastTaken = List.copyOf(after);
        takenOver = Set.copyOf(next);
        // One line per change, at most once a second: without it there is no way to tell "the option is
        // off" from "no registered source reached the client" from "the tier does not draw our light",
        // and a source that keeps moving is the interesting case, so the changed block is named.
        if (dev.sablelightoverhaul.lighting.LightingCommon.on(dev.sablelightoverhaul.lighting.LightingCommon.GEN_DEBUG)
                && (lastTakeOverLog == Long.MIN_VALUE || level.getGameTime() - lastTakeOverLog >= 20)) {
            var lost = new ArrayList<Long>();
            for (long pos : delta) if (!next.contains(pos)) lost.add(pos);
            var gained = new ArrayList<Long>();
            for (long pos : delta) if (next.contains(pos)) gained.add(pos);
            // The counts are for the tick the line was written on; a source that moves every tick keeps
            // this line coming once a second with the same shape, which is the signal to look for.
            lastTakeOverLog = level.getGameTime();
            com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul: source vanilla light {} ({} block(s), +{} -{} on this tick, tier {}{})",
                    next.isEmpty() ? "released" : "taken over", next.size(), gained.size(), lost.size(), effective,
                    next.isEmpty() ? "" : ", first=+" + (gained.isEmpty() ? "none" : BlockPos.of(gained.getFirst()).toShortString())
                            + " -" + (lost.isEmpty() ? "none" : BlockPos.of(lost.getFirst()).toShortString()));
        }
        var engine = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        var takenRenderer = Minecraft.getInstance().levelRenderer;
        // Re-assert the cleared light once a second. The zero below only holds as long as no light data arrives
        // for that block, and data does arrive: a chunk reload, a block update, a new lamp whose first packet
        // carries its own emission of fifteen. When it does, the section is baked from that number and the lamp
        // draws a pool of vanilla light around itself with the block itself dark -- which is exactly what was
        // reported, and why re-loading chunks made it come back. Reading the value first keeps this free for the
        // case that matters: a lamp that is already dark costs one light lookup per second.
        for (long pos : delta) {
            BlockPos block = BlockPos.of(pos);
            if (!level.hasChunkAt(block)) continue;
            // A section the server left without light storage has no node to re-evaluate, and then a
            // plain checkBlock is ignored: the section is kept active before the block is re-checked.
            var takenSection = SectionPos.of(block);
            engine.updateSectionStatus(takenSection, false);
            engine.checkBlock(block);
            // Vanilla block light is baked into the chunk meshes, so a light change with no re-mesh is
            // invisible on screen. Only the sections of the blocks that actually changed are rebuilt.
            if (takenRenderer != null) takenRenderer.setSectionDirtyWithNeighbors(takenSection.x(), takenSection.y(), takenSection.z());
        }
    }
    /** Which taken-over lamp the re-assert pass will refresh on this tick. */
    /** How long a source that vanished from the client's list keeps its take-over before it is released. */
    private static final long REMOVAL_GRACE_TICKS = 40;

    /** Sources currently missing from the list, with the tick they were first missed on. */
    private static final java.util.Map<Long,Long> PENDING_REMOVAL = new java.util.HashMap<>();

    /**
     * Re-applies the take-over's zero to one lamp per tick, round robin.
     *
     * <p>The zero is only as good as the last thing to touch that block's light, and light data keeps arriving:
     * a chunk reload, a block update, a fresh lamp whose first packet still carries the emission its block has.
     * Each arrival is baked into the section mesh, so a lamp draws a pool of vanilla light around a block that is
     * itself dark -- the fault as reported, and why reloading chunks brought it back.
     *
     * <p>The previous version only acted when the light level at the block read above zero, and that is exactly
     * the case it must handle but cannot see: the client's own engine reports zero while the chunk's light data,
     * and therefore the mesh, still holds fifteen. So this does not look at the level at all -- it refreshes one
     * lamp unconditionally every tick and lets the rotation bound the cost to a single section rebuild per tick.
     *
     * <p>Refreshing on this side rather than zeroing the emission on the server is deliberate: a server-side zero
     * would put the lamp out for every player who is not drawing this mod's light.
     */

    /**
     * Positions present in one list but not the other. The lists hold one entry per registered source,
     * so the quadratic scan is free and the common case — nothing moved — is a length check.
     */
    private static List<Long> symmetricDifference(List<Long> before, List<Long> after) {
        if (before.isEmpty() && after.isEmpty()) return List.of();
        var result = new ArrayList<Long>();
        for (long pos : after) if (!before.contains(pos)) result.add(pos);
        for (long pos : before) if (!after.contains(pos)) result.add(pos);
        return result;
    }
}
