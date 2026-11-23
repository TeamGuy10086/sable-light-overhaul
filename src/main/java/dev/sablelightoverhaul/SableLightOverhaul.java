package dev.sablelightoverhaul;

import com.mojang.serialization.MapCodec;
import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import dev.sablelightoverhaul.lighting.ImpactParticleData;
import dev.sablelightoverhaul.lighting.ShockwaveParticleData;
import dev.sablelightoverhaul.lighting.SmokeParticleData;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(SableLightOverhaul.ID)
public final class SableLightOverhaul {
    public static final String ID = "sablelightoverhaul";
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AUTO_MUZZLE =
        PARTICLES.register("auto_muzzle", () -> new SimpleParticleType(true));
        /** The puffs our plume emits: CBC's smoke shapes and ramp, dissipating by our opacity. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<dev.sablelightoverhaul.lighting.PlumeSmokeParticleData>> PLUME_SMOKE =
        PARTICLES.register("plume_smoke", () -> new ParticleType<dev.sablelightoverhaul.lighting.PlumeSmokeParticleData>(true) {
            @Override public MapCodec<dev.sablelightoverhaul.lighting.PlumeSmokeParticleData> codec() { return dev.sablelightoverhaul.lighting.PlumeSmokeParticleData.CODEC; }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, dev.sablelightoverhaul.lighting.PlumeSmokeParticleData> streamCodec() { return dev.sablelightoverhaul.lighting.PlumeSmokeParticleData.STREAM_CODEC; }
        });
    /** Our copy of CBC's muzzle plume, so the smoke it makes can be given an opacity of our own. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<dev.sablelightoverhaul.lighting.PlumeEmitterParticleData>> PLUME =
        PARTICLES.register("plume", () -> new ParticleType<dev.sablelightoverhaul.lighting.PlumeEmitterParticleData>(true) {
            @Override public MapCodec<dev.sablelightoverhaul.lighting.PlumeEmitterParticleData> codec() { return dev.sablelightoverhaul.lighting.PlumeEmitterParticleData.CODEC; }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, dev.sablelightoverhaul.lighting.PlumeEmitterParticleData> streamCodec() { return dev.sablelightoverhaul.lighting.PlumeEmitterParticleData.STREAM_CODEC; }
        });
    /** Our own muzzle fireball: our copy of CBC's shapes and colour ramp, stopped before its grey and faded out. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<dev.sablelightoverhaul.lighting.FireballParticleData>> FIREBALL =
        PARTICLES.register("fireball", () -> new ParticleType<dev.sablelightoverhaul.lighting.FireballParticleData>(true) {
            @Override public MapCodec<dev.sablelightoverhaul.lighting.FireballParticleData> codec() { return dev.sablelightoverhaul.lighting.FireballParticleData.CODEC; }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, dev.sablelightoverhaul.lighting.FireballParticleData> streamCodec() { return dev.sablelightoverhaul.lighting.FireballParticleData.STREAM_CODEC; }
        });
    /** A spark or a chip thrown by an impact: our own drawing, so shader packs can render it. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ImpactParticleData>> IMPACT =
        PARTICLES.register("impact", () -> new ParticleType<ImpactParticleData>(true) {
            @Override public MapCodec<ImpactParticleData> codec() { return ImpactParticleData.CODEC; }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, ImpactParticleData> streamCodec() { return ImpactParticleData.STREAM_CODEC; }
        });
    /** Dust cloud on CBC's debris-smoke frames, with this mod's own control program. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<GroundDustParticleData>> GROUND_DUST =
        PARTICLES.register("ground_dust", () -> new ParticleType<GroundDustParticleData>(true) {
            @Override public MapCodec<GroundDustParticleData> codec() { return GroundDustParticleData.CODEC; }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, GroundDustParticleData> streamCodec() { return GroundDustParticleData.STREAM_CODEC; }
        });
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAK_PUFF = PARTICLES.register("flak_puff", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PROJECTILE_STREAK =
        PARTICLES.register("projectile_streak", () -> new SimpleParticleType(true));
    /** Bright puff reserved for missile effects: our own sprite, so shader packs draw it too. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRAIL =
        PARTICLES.register("trail", () -> new SimpleParticleType(true));
    /**
     * One puff of a shockwave. Its own particle type for one reason above all: the type decides the
     * render queue, and this one gets a queue of its own, so a wave can never evict another mod's smoke.
     * See {@link ShockwaveParticleData}.
     */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ShockwaveParticleData>> SHOCKWAVE =
        PARTICLES.register("shockwave", () -> new net.minecraft.core.particles.ParticleType<ShockwaveParticleData>(true) {
            @Override public com.mojang.serialization.MapCodec<ShockwaveParticleData> codec() { return ShockwaveParticleData.CODEC; }
            @Override public net.minecraft.network.codec.StreamCodec<? super RegistryFriendlyByteBuf, ShockwaveParticleData> streamCodec() { return ShockwaveParticleData.STREAM_CODEC; }
        });
    /**
     * A smoke vent's puff: the vanilla smoke frames with this mod's own opacity and fade, because a
     * vanilla particle decides both for itself and a vent has to be able to tune them. See
     * {@link SmokeParticleData}.
     */
    public static final DeferredHolder<ParticleType<?>, ParticleType<SmokeParticleData>> SMOKE =
        PARTICLES.register("smoke", () -> new net.minecraft.core.particles.ParticleType<SmokeParticleData>(true) {
            @Override public com.mojang.serialization.MapCodec<SmokeParticleData> codec() { return SmokeParticleData.CODEC; }
            @Override public net.minecraft.network.codec.StreamCodec<? super RegistryFriendlyByteBuf, SmokeParticleData> streamCodec() { return SmokeParticleData.STREAM_CODEC; }
        });
    // The client installs this receiver. No client class is resolved on a dedicated server.
    public static Consumer<GroundDustFieldPayload> groundFieldReceiver = payload -> {};
    public static Consumer<TankAfterSmokePayload> afterSmokeReceiver = payload -> {};
    public static Consumer<CannonDustPayload> dustReceiver = payload -> {};
    public static Consumer<AutocannonHazePayload> autocannonHazeReceiver = payload -> {};
    public static Consumer<HostileSmokePayload> hostileSmokeReceiver = payload -> {};
    public static Consumer<FlarePayload> flareReceiver = payload -> {};
    public static Consumer<FlashPayload> receiver = payload -> {};
    public static Consumer<NavalSoundPayload> navalSoundReceiver = payload -> {};
    public static Consumer<dev.sablelightoverhaul.lighting.SourcePayload> sourceReceiver = payload -> {};
    /** This player's flashlight settings, as the server resolved them. */
    public static Consumer<FlashlightStatePayload> flashlightReceiver = payload -> {};
    /** The lamps of the players near this one, so their beams are drawn here too. */
    public static Consumer<FlashlightPeersPayload> flashlightPeersReceiver = payload -> {};
    /** Where the tracer shells are, so the client can draw their trails itself. */
    public static Consumer<AutocannonTrackPayload> autocannonTrackReceiver = payload -> {};
    public static Consumer<AutocannonTrackPayload.Phased> phasedAutocannonTrackReceiver = payload -> {};
    public static Consumer<AutocannonFlightPayload> autocannonFlightReceiver = payload -> {};
    public static Consumer<ShellTrackPayload> shellTrackReceiver = payload -> {};
    public static Consumer<HostileShellPayload> hostileShellReceiver = payload -> {};
    public static Consumer<HostileMathFlightPayload> hostileMathReceiver = payload -> {};
    /** What an explosion measured, so the client can draw its shockwave. */
    public static Consumer<ShockwavePayload> shockwaveReceiver = payload -> {};
    /** Which blocks the nearby vortex generators are registered to, so the wand can outline them. */
    public static Consumer<dev.sablelightoverhaul.lighting.VortexMarksPayload> vortexReceiver = payload -> {};
    // The server-config page: a catalogue on request, and a close-the-window signal when the server says no.
    public static Consumer<ServerConfigCatalogPayload> serverConfigReceiver = payload -> {};
    public static Runnable serverConfigDenied = () -> {};
    public SableLightOverhaul(IEventBus bus, net.neoforged.fml.ModContainer container) { PARTICLES.register(bus); dev.sablelightoverhaul.munitions.FlareMunitions.register(bus); dev.sablelightoverhaul.lighting.LightingBlocks.register(bus); dev.sablelightoverhaul.lighting.ServerProfiles.install(); dev.sablelightoverhaul.lighting.ProfileUi.install(); dev.sablelightoverhaul.ConfigMigration.migrateFile("sablelightoverhaul-server.toml"); container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,dev.sablelightoverhaul.lighting.LightingCommon.SPEC); bus.addListener(SableLightOverhaul::register);
        // The mod's own effects join the same bus a third party would: registered at the lowest priority, so
        // anybody else may run first and may cancel a section. Installed on both sides, because a firing
        // happens on the server while the drawing happens on the client.
        dev.sablelightoverhaul.effects.DefaultListeners.install();
        // The tracer feed produces an offer every tick and is also its own default delivery, registered at
        // the same lowest priority as everything else this mod does.
        TracerFeed.install();
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(HealthbarServerBridge::installAutocannonFeed));
    }
    public static Consumer<BlastPreviewPayload> blastPreviewReceiver = payload -> {};
    public static Consumer<BlastPreviewPayload.Approach> blastApproachReceiver = payload -> {};
    public static Consumer<InterceptionPayload> interceptionReceiver = payload -> {};
    public static Consumer<InterceptionPayload.Sized> sizedInterceptionReceiver = payload -> {};

    private static void register(RegisterPayloadHandlersEvent event) {
        var blastPreview = event.registrar("1").optional();
        blastPreview.playToClient(InterceptionPayload.TYPE, InterceptionPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> interceptionReceiver.accept(payload)));
        blastPreview.playToClient(InterceptionPayload.Sized.TYPE, InterceptionPayload.Sized.CODEC,
            (payload, context) -> context.enqueueWork(() -> sizedInterceptionReceiver.accept(payload)));
        blastPreview.playToClient(BlastPreviewPayload.Impact.TYPE, BlastPreviewPayload.Impact.CODEC,
            (payload, context) -> context.enqueueWork(() -> blastPreviewReceiver.accept(payload.visual())));
        blastPreview.playToClient(BlastPreviewPayload.TYPE, BlastPreviewPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> blastPreviewReceiver.accept(payload)));
        blastPreview.playToClient(BlastPreviewPayload.Approach.TYPE, BlastPreviewPayload.Approach.CODEC,
            (payload, context) -> context.enqueueWork(() -> blastApproachReceiver.accept(payload)));
        blastPreview.playToServer(BlastPreviewPayload.Adjust.TYPE, BlastPreviewPayload.Adjust.CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                    BlastDebugItem.adjust(player, payload);
            }));
        // 32 adds the independent tank residual-smoke emitter event.
        // Protocol 46 adds carrier UUIDs for optional per-shot mathematical-flight deduplication.
        // Protocol 45 adds the bounded remote-autocannon position channel.
        // Protocol 37: body-local hull surface anchors and attachment controls.
        event.registrar("46").playToClient(FlarePayload.TYPE,FlarePayload.CODEC,
            (payload,context)->context.enqueueWork(()->flareReceiver.accept(payload)));
        event.registrar("46").playToClient(GroundDustFieldPayload.TYPE,GroundDustFieldPayload.CODEC,
            (payload,context)->context.enqueueWork(()->groundFieldReceiver.accept(payload)));
        event.registrar("46").playToClient(TankAfterSmokePayload.TYPE,TankAfterSmokePayload.CODEC,
            (payload,context)->context.enqueueWork(()->afterSmokeReceiver.accept(payload)));
        // The channel version is the mod's network version, and it moves whenever any payload's shape
        // changes. A mismatch is refused at join with the two numbers named, which is the whole point: every
        // change below would otherwise decode cleanly into nonsense.
        //
        // Chronology, most recent first:
        //   34  Ground dust carries source material state/position for client particle-texture tint.
        //   33  GroundDustFieldPayload: bounded authoritative terrain patches.
        //   29  Server configuration page: request/catalogue/edit/denied payloads.
        //   28  LightSourceEditPayload gained the registration id. A page opened from a rendered position
        //       could not find a lamp on a hull, showed zeros, and wrote them back; identity now travels.
        //   27  SourcePayload's spotlight width byte became a varint of tenths of a degree, because a
        //       redstone strength was the only thing the wire could carry and a hand-tuned lamp needs finer
        //       steps than one fifteenth of its range.
        //   17  FlashPayload gained the per-cannon form: fourteen extra floats.
        //   15  SourcePayload gained the light style (how it is lit, which faces it emits through).
        // The numbers before 15 are in the old handoff; do not add to this list without bumping the literal
        // on every registrar() call below.
        event.registrar("46").playToClient(CannonDustPayload.TYPE, CannonDustPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> dustReceiver.accept(payload)));
        event.registrar("46").optional().playToClient(AutocannonHazePayload.TYPE, AutocannonHazePayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> autocannonHazeReceiver.accept(payload)));
        event.registrar("1").optional().playToClient(NavalSoundPayload.TYPE, NavalSoundPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> navalSoundReceiver.accept(payload)));
        event.registrar("1").optional().playToClient(HostileSmokePayload.TYPE, HostileSmokePayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> hostileSmokeReceiver.accept(payload)));
        event.registrar("46").playToClient(FlashPayload.TYPE, FlashPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> receiver.accept(payload)));
        event.registrar("46").playToClient(dev.sablelightoverhaul.lighting.SourcePayload.TYPE,dev.sablelightoverhaul.lighting.SourcePayload.CODEC,(payload,context)->context.enqueueWork(()->sourceReceiver.accept(payload)));
        // The flashlight's settings are the server's to decide (they are operator-gated), so the client
        // is told what to draw rather than reading a config of its own.
        event.registrar("46").playToClient(FlashlightStatePayload.TYPE,FlashlightStatePayload.CODEC,
            (payload,context)->context.enqueueWork(()->flashlightReceiver.accept(payload)));
        // The neighbours' lamps: settings only, since where they are and where they look already arrive
        // with their entities. The server sends the whole set, so a peer who switches off disappears.
        event.registrar("46").playToClient(FlashlightPeersPayload.TYPE,FlashlightPeersPayload.CODEC,
            (payload,context)->context.enqueueWork(()->flashlightPeersReceiver.accept(payload)));
        // The trail's path, not the trail itself: the client draws the ribbon from these positions.
        event.registrar("47").playToClient(AutocannonTrackPayload.TYPE,AutocannonTrackPayload.CODEC,
            (payload,context)->context.enqueueWork(()->autocannonTrackReceiver.accept(payload)));
        event.registrar("1").optional().playToClient(AutocannonTrackPayload.Phased.TYPE,AutocannonTrackPayload.Phased.CODEC,
            (payload,context)->context.enqueueWork(()->phasedAutocannonTrackReceiver.accept(payload)));
        event.registrar("1").optional().playToClient(AutocannonFlightPayload.TYPE,AutocannonFlightPayload.CODEC,
            (payload,context)->context.enqueueWork(()->autocannonFlightReceiver.accept(payload)));
        event.registrar("1").optional().playToClient(AutocannonFlightPayload.Phased.TYPE,AutocannonFlightPayload.Phased.CODEC,
            (payload,context)->context.enqueueWork(()->autocannonFlightReceiver.accept(payload.data())));
        event.registrar("1").optional().playToClient(AutocannonFlightPayload.Sampled.TYPE,AutocannonFlightPayload.Sampled.CODEC,
            (payload,context)->context.enqueueWork(()->autocannonFlightReceiver.accept(payload.data())));
        event.registrar("1").optional().playToClient(HostileShellPayload.TYPE,HostileShellPayload.CODEC,
            (payload,ctx)->ctx.enqueueWork(()->hostileShellReceiver.accept(payload)));
        event.registrar("1").optional().playToClient(HostileMathFlightPayload.TYPE,HostileMathFlightPayload.CODEC,
            (payload,ctx)->ctx.enqueueWork(()->hostileMathReceiver.accept(payload)));
        event.registrar("46").playToClient(ShellTrackPayload.TYPE,ShellTrackPayload.CODEC,
            (payload,context)->context.enqueueWork(()->shellTrackReceiver.accept(payload)));
        // What an explosion was, not the explosion particles: the client generates the shockwave from
        // these measurements, which is the same split the trail uses.
        event.registrar("46").playToClient(ShockwavePayload.TYPE,ShockwavePayload.CODEC,
            (payload,context)->context.enqueueWork(()->shockwaveReceiver.accept(payload)));
        // The vortex generator's registered blocks: the server owns which blocks they are, the client
        // owns drawing them, and the wand in the player's hand is what says when to send.
        event.registrar("46").playToClient(dev.sablelightoverhaul.lighting.VortexMarksPayload.TYPE,
            dev.sablelightoverhaul.lighting.VortexMarksPayload.CODEC,
            (payload,context)->context.enqueueWork(()->vortexReceiver.accept(payload)));
        // Dedicated-server radar snapshots negotiate independently of the existing required channels.
        event.registrar("46").optional().playToServer(FarContacts.Request.TYPE,FarContacts.Request.CODEC,
            (payload,context)->context.enqueueWork(()->{
                if(context.player() instanceof net.minecraft.server.level.ServerPlayer player) FarContacts.request(player,payload);
            }));
        event.registrar("46").optional().playToClient(FarContacts.Snapshot.TYPE,FarContacts.Snapshot.CODEC,
            (payload,context)->context.enqueueWork(()->FarContacts.receiver.accept(payload)));
        event.registrar("46").optional().playToClient(FarContacts.IdentifiedSnapshot.TYPE,FarContacts.IdentifiedSnapshot.CODEC,
            (payload,context)->context.enqueueWork(()->FarContacts.receiver.accept(payload.snapshot())));
        event.registrar("46").optional().playToClient(FarContacts.HealthSnapshot.TYPE, FarContacts.HealthSnapshot.CODEC,
            (payload, context) -> context.enqueueWork(() -> FarContacts.receiver.accept(payload.snapshot())));
        // The per-cannon table: asked for once by a client when it joins, answered with everything, and after
        // that only changes travel. The catalogue is not carried -- both sides derive it from the same code --
        // so the table is the server's presets plus the values it holds, which is what a page reads.
        event.registrar("46").playToServer(ProfileRequestPayload.TYPE,ProfileRequestPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.lighting.ProfileSync.send(context.player())));
        event.registrar("46").playToClient(ProfileTablePayload.TYPE,ProfileTablePayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.client.ProfileCache.accept(payload)));
        event.registrar("46").playToClient(ProfileChangedPayload.TYPE,ProfileChangedPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.client.ProfileCache.apply(payload)));
        // A generator's page, pasted back in as values: the same parse-on-the-client shape as the cannon's,
        // and the same one-value-at-a-time validation on the way in.
        event.registrar("46").playToServer(GeneratorImportPayload.TYPE,GeneratorImportPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.lighting.GeneratorServer
                .applyImport(context.player(),payload)));
        // A cannon's form: one validated write per edit, and a pasted form as one message whose every type
        // is checked against the cannon that is actually there before anything is stored.
        event.registrar("46").playToServer(ProfileEditPayload.TYPE,ProfileEditPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.lighting.ProfileServer
                .applyEdit(context.player(),payload)));
        // Forgetting values: what the page does when a single row, or a whole page, goes back on its preset.
        event.registrar("46").playToServer(ProfileClearPayload.TYPE,ProfileClearPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.lighting.ProfileServer
                .applyClear(context.player(),payload)));
        event.registrar("46").playToServer(ProfileImportPayload.TYPE,ProfileImportPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.lighting.ProfileServer
                .applyImport(context.player(),payload)));
        event.registrar("46").playToServer(FlashlightEditPayload.TYPE,FlashlightEditPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.lighting.FlashlightSettings
                .setNumber(context.player(),payload.field(),payload.number())));
        // A configuration page edits the block entity through one validated write per field.
        event.registrar("46").playToServer(dev.sablelightoverhaul.lighting.GeneratorEditPayload.TYPE,dev.sablelightoverhaul.lighting.GeneratorEditPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.lighting.GeneratorServer.applyEdit(context.player(),payload)));
        // A registered light source's page: one validated write per field, the same shape as the
        // generator's. It is a block's settings, so the position travels; the server resolves it against
        // the level the editing player is in, which is what lets a lamp on a physics body work exactly
        // like one on the ground.
        event.registrar("46").playToServer(LightSourceEditPayload.TYPE,LightSourceEditPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.sablelightoverhaul.lighting.LightSourceServer
                .set(payload.pos(),payload.id(),payload.field(),payload.number(),payload.text(),context.player())));
        // The server configuration page. The server spec is readable only here; the client asks for a
        // catalogue, gets one snapshot, and writes one field per edit. Every branch checks the operator bit
        // again -- hiding the button on the client is convenience, not security.
        event.registrar("46").playToServer(ServerConfigRequestPayload.TYPE, ServerConfigRequestPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                var player = context.player();
                if (!player.hasPermissions(2)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            (net.minecraft.server.level.ServerPlayer) player, new ServerConfigDeniedPayload());
                    return;
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        (net.minecraft.server.level.ServerPlayer) player,
                        new ServerConfigCatalogPayload(ServerConfigService.snapshot(), true));
            }));
        event.registrar("46").playToClient(ServerConfigCatalogPayload.TYPE, ServerConfigCatalogPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> serverConfigReceiver.accept(payload)));
        event.registrar("46").playToClient(ServerConfigDeniedPayload.TYPE, ServerConfigDeniedPayload.CODEC,
            (payload, context) -> context.enqueueWork(serverConfigDenied));
        event.registrar("46").playToServer(ServerConfigEditPayload.TYPE, ServerConfigEditPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                var player = context.player();
                if (!player.hasPermissions(2)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            (net.minecraft.server.level.ServerPlayer) player, new ServerConfigDeniedPayload());
                    return;
                }
                ServerConfigService.apply(payload.key(), payload.value());
            }));
    }
}
