package dev.cbcmuzzlelight;

import com.mojang.serialization.MapCodec;
import dev.cbcmuzzlelight.lighting.GroundDustParticleData;
import dev.cbcmuzzlelight.lighting.ImpactParticleData;
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

@Mod(MuzzleLight.ID)
public final class MuzzleLight {
    public static final String ID = "cbcmuzzlelight";
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AUTO_MUZZLE =
        PARTICLES.register("auto_muzzle", () -> new SimpleParticleType(true));
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
    /** Bright puff left behind a tracer shell: our own sprite, so shader packs draw it too. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRAIL =
        PARTICLES.register("trail", () -> new SimpleParticleType(true));
    // The client installs this receiver. No client class is resolved on a dedicated server.
    public static Consumer<FlashPayload> receiver = payload -> {};
    public static Consumer<dev.cbcmuzzlelight.lighting.SourcePayload> sourceReceiver = payload -> {};
    /** This player's flashlight settings, as the server resolved them. */
    public static Consumer<FlashlightStatePayload> flashlightReceiver = payload -> {};
    /** The lamps of the players near this one, so their beams are drawn here too. */
    public static Consumer<FlashlightPeersPayload> flashlightPeersReceiver = payload -> {};
    /** Where the tracer shells are, so the client can draw their trails itself. */
    public static Consumer<ShellTrackPayload> shellTrackReceiver = payload -> {};
    /** What an explosion measured, so the client can draw its shockwave. */
    public static Consumer<ShockwavePayload> shockwaveReceiver = payload -> {};
    public MuzzleLight(IEventBus bus, net.neoforged.fml.ModContainer container) { PARTICLES.register(bus); dev.cbcmuzzlelight.lighting.LightingBlocks.register(bus); container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,dev.cbcmuzzlelight.lighting.LightingCommon.SPEC); bus.addListener(MuzzleLight::register); }
    private static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("8").playToClient(FlashPayload.TYPE, FlashPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> receiver.accept(payload)));
        event.registrar("8").playToClient(dev.cbcmuzzlelight.lighting.SourcePayload.TYPE,dev.cbcmuzzlelight.lighting.SourcePayload.CODEC,(payload,context)->context.enqueueWork(()->sourceReceiver.accept(payload)));
        // The flashlight's settings are the server's to decide (they are operator-gated), so the client
        // is told what to draw rather than reading a config of its own.
        event.registrar("8").playToClient(FlashlightStatePayload.TYPE,FlashlightStatePayload.CODEC,
            (payload,context)->context.enqueueWork(()->flashlightReceiver.accept(payload)));
        // The neighbours' lamps: settings only, since where they are and where they look already arrive
        // with their entities. The server sends the whole set, so a peer who switches off disappears.
        event.registrar("8").playToClient(FlashlightPeersPayload.TYPE,FlashlightPeersPayload.CODEC,
            (payload,context)->context.enqueueWork(()->flashlightPeersReceiver.accept(payload)));
        // The trail's path, not the trail itself: the client draws the ribbon from these positions.
        event.registrar("8").playToClient(ShellTrackPayload.TYPE,ShellTrackPayload.CODEC,
            (payload,context)->context.enqueueWork(()->shellTrackReceiver.accept(payload)));
        // What an explosion was, not the explosion particles: the client generates the shockwave from
        // these measurements, which is the same split the trail uses.
        event.registrar("8").playToClient(ShockwavePayload.TYPE,ShockwavePayload.CODEC,
            (payload,context)->context.enqueueWork(()->shockwaveReceiver.accept(payload)));
        event.registrar("8").playToServer(FlashlightEditPayload.TYPE,FlashlightEditPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.cbcmuzzlelight.lighting.FlashlightSettings
                .setNumber(context.player(),payload.field(),payload.number())));
        // A configuration page edits the block entity through one validated write per field.
        event.registrar("8").playToServer(dev.cbcmuzzlelight.lighting.GeneratorEditPayload.TYPE,dev.cbcmuzzlelight.lighting.GeneratorEditPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.cbcmuzzlelight.lighting.GeneratorServer.applyEdit(context.player(),payload)));
        // A registered light source's page: one validated write per field, the same shape as the
        // generator's. It is a block's settings, so the position travels; the server resolves it against
        // the level the editing player is in, which is what lets a lamp on a physics body work exactly
        // like one on the ground.
        event.registrar("8").playToServer(LightSourceEditPayload.TYPE,LightSourceEditPayload.CODEC,
            (payload,context)->context.enqueueWork(()->dev.cbcmuzzlelight.lighting.LightSourceServer
                .setNumber(context.player(),payload.pos(),payload.field(),payload.number())));
    }
}
