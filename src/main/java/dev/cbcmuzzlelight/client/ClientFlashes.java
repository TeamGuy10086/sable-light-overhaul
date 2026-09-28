package dev.cbcmuzzlelight.client;

import dev.cbcmuzzlelight.FlashPayload;
import dev.cbcmuzzlelight.FlashStore;
import dev.cbcmuzzlelight.MuzzleLight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

@EventBusSubscriber(modid = MuzzleLight.ID, value = Dist.CLIENT)
public final class ClientFlashes {
    public static final FlashStore STORE = new FlashStore(64);
    private static final boolean BLOCK_LIGHT_ENABLED = !Boolean.getBoolean("cbcmuzzlelight.disableBlockLight");
    private static volatile ClientLevel owner;
    private static dev.cbcmuzzlelight.LightingMode effective=dev.cbcmuzzlelight.LightingMode.None;
    private static dev.cbcmuzzlelight.LightingMode lastRequested;
    private static int visualEpoch;
    public static int visualEpoch() { return visualEpoch; }
    public static boolean effectsEnabled() { return effective!=dev.cbcmuzzlelight.LightingMode.None && MuzzleClientConfig.MODE.get()!=dev.cbcmuzzlelight.LightingMode.None; }
    /** True when the active tier expects the shader pack patch, so the screen-space overlay stands down. */
    public static boolean inPackLighting() { return effective==dev.cbcmuzzlelight.LightingMode.Advanced; }
    /**
     * True for the CPU-backed tier, where every light change dirties sections instead of a shader
     * buffer. Only that tier has to clip a spotlight's range.
     */
    public static boolean cpuTier() { return effective==dev.cbcmuzzlelight.LightingMode.Compatible; }
    private static void selectMode(ClientLevel level) {
        var requested=MuzzleClientConfig.MODE.get();
        var next=requested.resolve(GpuLightRenderer.ready(),AdvancedLights.ready());
        // The in-pack tier only means anything while a shader pack is actually loaded.
        if(next==dev.cbcmuzzlelight.LightingMode.Advanced && !IrisCompat.shaderPackInUse()) next=dev.cbcmuzzlelight.LightingMode.DynamicOnly;
        boolean changed=effective!=next;
        if(changed) {
            AdvancedLights.clear(level); GpuLightRenderer.clear(); STORE.clear(); SmokeTracker.clear(); visualEpoch++;
            effective=next;
            updateLights(level); // clears old Legacy emission before Advanced starts
        }
        if(changed || requested!=lastRequested) {
            String message="CBC Muzzle Light: requested="+requested+", active="+next;
            com.mojang.logging.LogUtils.getLogger().info(message);
            if(Minecraft.getInstance().player!=null) Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.translatable("cbcmuzzlelight.mode.active", requested.toString(), next.toString()),false);
        }
        lastRequested=requested;
    }
    private static volatile Map<Long, Integer> emission = Map.of();
    /** Positions whose own vanilla light a registered source has given up (in-pack path only). */
    private static Set<Long> takenOver = Set.of();
    /** Where the sources were last seen, so a move can be turned into a pair of single-block updates. */
    private static List<Long> lastTaken = List.of();
    private static long lastTakeOverLog = Long.MIN_VALUE;
    private static long lastBridgeLog = Long.MIN_VALUE;
    private static Set<SectionPos> forcedSections = Set.of();
    private ClientFlashes() {}
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (owner != level) reset(level);
        if (level == null || Minecraft.getInstance().isPaused()) return;
        selectMode(level);
        // Released as well as taken, so turning the option off hands the lamp its own light back.
        takeOverSourceLight(level);
        if(!effectsEnabled()) return;
        STORE.expire(level.getGameTime());
        SmokeTracker.tick(level.getGameTime());
        switch(effective) {
            case Compatible -> { if(!AdvancedLights.update(level)) selectMode(level); }
            case DynamicOnly, Advanced -> { if(!AdvancedLights.gpuUpdate(level)) selectMode(level); }
            case Legacy -> updateLights(level);
            default -> {}
        }
    }
    private static void reset(ClientLevel level) {
        ExtraLights.worldChanged(level);
        // Old world's light storage is discarded with that world; never touch a new world with old positions.
        AdvancedLights.clear(null); GpuLightRenderer.clear(); effective=dev.cbcmuzzlelight.LightingMode.None; lastRequested=null; visualEpoch++;
        emission = Map.of(); takenOver = Set.of(); lastTaken = List.of(); lastTakeOverLog = Long.MIN_VALUE; STORE.clear(); SmokeTracker.clear(); forcedSections = Set.of(); lastBridgeLog = Long.MIN_VALUE; owner = level;
    }
    public static void receive(FlashPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().equals(payload.dimension())) return;
        if (owner != level) reset(level);
        selectMode(level);
        if(!Float.isFinite(payload.charge()) || payload.charge()<0 || !Float.isFinite(payload.plumeSize()) || payload.plumeSize()<=0) return;
        if(!effectsEnabled() || !FlashStore.valid(payload.lightX(),payload.lightY(),payload.lightZ(),payload.dx(),payload.dy(),payload.dz(),payload.kind())) return;
        if (!STORE.add(payload.x(), payload.y(), payload.z(), payload.dx(), payload.dy(), payload.dz(),
                payload.kind(), level.getGameTime(), payload.kind()==0 ? (effective==dev.cbcmuzzlelight.LightingMode.Compatible ? MuzzleClientConfig.advancedBigLifetime() : MuzzleClientConfig.bigLight()) : (effective==dev.cbcmuzzlelight.LightingMode.Compatible ? MuzzleClientConfig.AUTO_PROFILE.curve() : MuzzleClientConfig.autoLight()),
                payload.kind()==0 ? (effective==dev.cbcmuzzlelight.LightingMode.Compatible ? MuzzleClientConfig.advancedBigLayout() : MuzzleClientConfig.bigLayout()) : MuzzleClientConfig.autoLayout(),
                new FlashStore.Emitter(payload.emitterId(),payload.lightX(),payload.lightY(),payload.lightZ(),payload.charge(),payload.plumeSize()))) return;
        // Big cannons retain native smoke/flame. Autocannons use an oriented flame, never a FLASH billboard.
        if (payload.kind() == 1) Minecraft.getInstance().particleEngine.createParticle(MuzzleLight.AUTO_MUZZLE.get(),
                payload.x(), payload.y(), payload.z(), payload.dx(), payload.dy(), payload.dz());
        // One lighting submission per tick; particle visuals remain immediate.
    }
    public static boolean owns(ClientLevel level) { return effectsEnabled() && level == owner && level == Minecraft.getInstance().level; }
    public static int emission(Object world, long pos) {
        return world == owner && owner != null ? emission.getOrDefault(pos, 0) : 0;
    }
    /**
     * Whether a registered lamp has given its own vanilla light up, so the light engine must treat
     * that block as emitting nothing at all.
     */
    public static boolean takenOver(Object world, long pos) {
        return world == owner && owner != null && takenOver.contains(pos);
    }
    /**
     * True for the tier that draws our light additively on the GPU, which is the only place the
     * "take the lamp's own light over" trick applies: those tiers can only ever <em>add</em> to what
     * the world already lit, so a lamp's own white pool has to be removed for its colour to show.
     * The CPU tiers (Legacy, Compatible) paint the light themselves through the light engine and are
     * left completely alone — no block is ever taken over there, and nothing about them changes.
     */
    private static boolean gpuTier() {
        return effective==dev.cbcmuzzlelight.LightingMode.Advanced || effective==dev.cbcmuzzlelight.LightingMode.DynamicOnly;
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
     * {@code i < cbcMuzzleShadowCount && tints[i].w <= 0.5}, and {@code tints[i].w} is the
     * {@code selfCull} flag written by {@link IrisMuzzleBridge}: registered lamps and carried lights
     * are the ones marked, so they never cast, while the muzzle/tracer lights that do cast are
     * untouched by anything here. Zeroing a block's emission changes no light's position, shape,
     * brightness, order or {@code selfCull}, so the shadow slots are filled by exactly the same lights
     * as before.
     */
    private static void takeOverSourceLight(ClientLevel level) {
        Set<Long> next = new HashSet<>();
        if (gpuTier() && ExtraConfig.GPU_TAKEOVER.get()) {
            ExtraLights.forEachSourceBlock(level, pos -> next.add(pos));
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
        if (lastTakeOverLog == Long.MIN_VALUE || level.getGameTime() - lastTakeOverLog >= 20) {
            var lost = new ArrayList<Long>();
            for (long pos : delta) if (!next.contains(pos)) lost.add(pos);
            var gained = new ArrayList<Long>();
            for (long pos : delta) if (next.contains(pos)) gained.add(pos);
            // The counts are for the tick the line was written on; a source that moves every tick keeps
            // this line coming once a second with the same shape, which is the signal to look for.
            lastTakeOverLog = level.getGameTime();
            com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light: source vanilla light {} ({} block(s), +{} -{} on this tick, tier {}{})",
                    next.isEmpty() ? "released" : "taken over", next.size(), gained.size(), lost.size(), effective,
                    next.isEmpty() ? "" : ", first=+" + (gained.isEmpty() ? "none" : BlockPos.of(gained.getFirst()).toShortString())
                            + " -" + (lost.isEmpty() ? "none" : BlockPos.of(lost.getFirst()).toShortString()));
        }
        var engine = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        var takenRenderer = Minecraft.getInstance().levelRenderer;
        for (long pos : delta) {
            BlockPos block = BlockPos.of(pos);
            if (!level.hasChunkAt(block)) continue;
            // A section the server left without light storage has no node to re-evaluate, and then a
            // plain checkBlock is ignored: the section is kept active first, exactly the way the
            // Legacy injection path has to do it, and only then is the block re-checked.
            var takenSection = SectionPos.of(block);
            engine.updateSectionStatus(takenSection, false);
            engine.checkBlock(block);
            // Vanilla block light is baked into the chunk meshes, so a light change with no re-mesh is
            // invisible on screen. Only the sections of the blocks that actually changed are rebuilt.
            if (takenRenderer != null) takenRenderer.setSectionDirtyWithNeighbors(takenSection.x(), takenSection.y(), takenSection.z());
        }
    }
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
    private static void updateLights(ClientLevel level) {
        Map<Long, Integer> next = new HashMap<>();
        Set<SectionPos> nextSections = new HashSet<>();
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var samples = BLOCK_LIGHT_ENABLED && effective==dev.cbcmuzzlelight.LightingMode.Legacy ? STORE.nearest(camera.x,camera.y,camera.z,MuzzleClientConfig.MAX_SOURCES.get(),level.getGameTime()) : java.util.List.<FlashStore.LightSample>of();
        for (var flash : samples) {
            BlockPos pos = BlockPos.containing(flash.x(), flash.y(), flash.z());
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) continue;
            next.merge(pos.asLong(), flash.level(), Math::max);
            nextSections.add(SectionPos.of(pos));
        }
        if (effective==dev.cbcmuzzlelight.LightingMode.Legacy && MuzzleClientConfig.SABLE_ENABLED.get() && net.neoforged.fml.ModList.get().isLoaded("sable")) {
            var projected = SableLightBridge.project(level, next);
            if (MuzzleClientConfig.SABLE_DEBUG.get() && !samples.isEmpty()
                    && (lastBridgeLog == Long.MIN_VALUE || level.getGameTime() - lastBridgeLog >= 20)) {
                com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light bridge: samples={}, world={}, projected={}, firstSample={}", samples.size(), next.size(), projected.size(), samples.getFirst());
                lastBridgeLog = level.getGameTime();
            }
            projected.forEach((pos, value) -> next.merge(pos, value, Math::max));
            for (long pos : next.keySet()) nextSections.add(SectionPos.of(BlockPos.of(pos)));
        }
        var engine = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        // An all-air section otherwise has no light storage. Temporarily keep source sections active.
        for (var section : nextSections) {
            if (!forcedSections.contains(section)) engine.updateSectionStatus(section, false);
        }
        Set<Long> dirty = new HashSet<>(emission.keySet()); dirty.addAll(next.keySet());
        // Periodic refresh repairs server light updates without needlessly rechecking stationary sources every tick.
        if(level.getGameTime()%10!=0) dirty.removeIf(p -> java.util.Objects.equals(emission.get(p),next.get(p)));
        emission = Map.copyOf(next);
        // Changed cells plus the periodic refresh above; old cells are checked to remove moving light trails.
        for (long pos : dirty) if (level.hasChunkAt(BlockPos.of(pos))) engine.checkBlock(BlockPos.of(pos));
        for (var section : forcedSections) {
            if (nextSections.contains(section)) continue;
            BlockPos pos = section.origin();
            if (!level.hasChunkAt(pos)) continue;
            var chunk = level.getChunk(section.x(), section.z());
            int index = level.getSectionIndexFromSectionY(section.y());
            if (index >= 0 && index < chunk.getSections().length)
                engine.updateSectionStatus(section, chunk.getSection(index).hasOnlyAir());
        }
        forcedSections = Set.copyOf(nextSections);
    }
}
