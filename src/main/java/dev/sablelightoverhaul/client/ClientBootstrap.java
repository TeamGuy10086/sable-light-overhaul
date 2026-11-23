package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.SableLightOverhaul;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SableLightOverhaul.ID, dist = Dist.CLIENT)
public final class ClientBootstrap {
    public ClientBootstrap(IEventBus bus, ModContainer container) {
// The server-side half asks these three questions and must not name a client class to do it. Installed here,
// once, on the only side where the answers exist.
dev.sablelightoverhaul.ClientHooks.install(
        HeldLights::number,
        MediumCannonTrails::shellType,
        MediumCannonTrails::inGround,
        MediumCannonTrails::tracer);
        // Before the file is read: the owner's tuned values live under section names that have since moved,
        // and NeoForge has no way to rename one, so a rename would otherwise read as "your value vanished".
        dev.sablelightoverhaul.ConfigMigration.migrateFile("sablelightoverhaul-client.toml");
        container.registerConfig(ModConfig.Type.CLIENT, MuzzleClientConfig.SPEC, "sablelightoverhaul-client.toml");
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> net.neoforged.fml.ModList.get().isLoaded("ldlib2")
                ? new dev.sablelightoverhaul.client.ui.ConfigWindow(new dev.sablelightoverhaul.client.ui.TomlSource(MuzzleClientConfig.SPEC, java.util.Set.of())) {
                    @Override public void onClose() { super.onClose(); net.minecraft.client.Minecraft.getInstance().setScreen(parent); }
                  }
                : new ConfigurationScreen(mod, parent));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ParticleWind::tick);
        SableLightOverhaul.dustReceiver = CannonDustEffects::receive;
        SableLightOverhaul.autocannonHazeReceiver = payload -> CannonDustEffects.receiveAutocannon(payload.shot());
        SableLightOverhaul.hostileSmokeReceiver = HostileSmokeClient::receive;
        SableLightOverhaul.navalSoundReceiver = NavalAudio::receive;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(NavalAudio::tick);
        SableLightOverhaul.groundFieldReceiver = GroundDustFieldClient::receive;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GroundDustFieldClient::tick);
        SableLightOverhaul.afterSmokeReceiver = TankAfterSmokeClient::receive;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TankAfterSmokeClient::tick);
        SableLightOverhaul.receiver = ClientFlashes::receive;
        SableLightOverhaul.sourceReceiver = ExtraLights::receive;
        SableLightOverhaul.flareReceiver = DebugFlares::receive;
        SableLightOverhaul.flashlightReceiver = HeldLights::receive;
        // Other players' lamps are drawn from the same place, from the list the server sends.
        SableLightOverhaul.flashlightPeersReceiver = HeldLights::receivePeers;
        // The trail is laid by the client, from these positions plus the shells it tracks itself.
        SableLightOverhaul.shellTrackReceiver = TrailTrack::receive;
        SableLightOverhaul.hostileShellReceiver = HostileShellClient::receive;
        SableLightOverhaul.hostileMathReceiver = HostileMathShells::receive;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HostileShellClient::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HostileShellClient::nativeJoined);
        SableLightOverhaul.autocannonTrackReceiver = TrailTrack::receiveAutocannon;
        SableLightOverhaul.phasedAutocannonTrackReceiver = TrailTrack::receivePhasedAutocannon;
        SableLightOverhaul.autocannonFlightReceiver = TrailTrack::receiveConfirmedAutocannon;
        // The shockwave is the same split: the server measured the explosion, the client draws it.
        SableLightOverhaul.shockwaveReceiver = Shockwave::receive;
        SableLightOverhaul.blastPreviewReceiver = ImpactExplosionSmoke::receivePreview;
        SableLightOverhaul.blastApproachReceiver = DebugMissileApproach::receive;
        SableLightOverhaul.interceptionReceiver = ImpactExplosionSmoke::receiveInterception;
        SableLightOverhaul.sizedInterceptionReceiver = payload ->
            ImpactExplosionSmoke.receiveInterception(payload.data(), payload.fragmentDiameter());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(InterceptionAudio::tick);
        // And the client's half of the impact event: the payload arrives once, and the dust ring and the
        // sphere handle it as two listeners so either can be cancelled by id.
        Shockwave.install();
        // A block hit is dispatched on the client too, and the armour piercing sparks are its listener.
        ApSparks.install();
        // The tracer shells the server offers: this side answers with a light and a rope, as two listeners.
        TrailTrack.install();
        if (net.neoforged.fml.ModList.get().isLoaded("wolfmisc")) {
            try {
                FarContactLayers.install();
                bus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                    event.registerReloadListener(new FarContactPortraitLibrary()));
            }
            catch (LinkageError incompatible) {
                com.mojang.logging.LogUtils.getLogger().warn("Far contacts require the WolfMisc hologram layer API", incompatible);
            }
        }
        // LOWEST observes Healthbar's completed tick, including clock-hold frames with no advanced event.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
            (net.neoforged.neoforge.client.event.ClientTickEvent.Post event)->HealthbarFlightBridge.tick(net.minecraft.client.Minecraft.getInstance().level));
        // The plume smoke this client can see, offered as particle hosts: one light follows its hot centre.
        SmokeTracker.install();
        if (net.neoforged.fml.ModList.get().isLoaded("flywheel")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FlywheelDebris::tick);
        }
        // The wand's highlight: the server sends which blocks are registered, this draws them.
        SableLightOverhaul.vortexReceiver = VortexHighlight::receive;
        // The server-config page: the catalogue opens a second window, a refusal closes it with a message.
        SableLightOverhaul.serverConfigReceiver = dev.sablelightoverhaul.client.ui.ConfigKey::receiveServerCatalog;
        SableLightOverhaul.serverConfigDenied = dev.sablelightoverhaul.client.ui.ConfigKey::denyServerConfig;
        bus.addListener(ClientBootstrap::particles);
        bus.addListener(BlockDebrisColor::reload);
        bus.addListener(ClientBootstrap::blockEntityRenderers);
        bus.addListener(SpotlightRenderer::registerModels);
        bus.addListener(GpuLightRenderer::registerShader);
        bus.addListener(MuzzleParticleLayer::registerShader);
        bus.addListener(NativeRefraction::registerShader);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GpuLightRenderer::onRenderLevelStage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VortexHighlight::onRenderLevelStage);
        // The axis lines of a lamp's angle frame, drawn while the registrar tool is held: the same shape,
        // and the same reason -- a frame is not something a player should have to discover by trial.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(LampAxes::onRenderLevelStage);
        // The shockwave's surface: one translucent band per wave, zero particles. See ShockwaveMesh.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(NativeRefraction::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ShockwaveMesh::onRenderLevelStage);
        // Temporary: proves the scene depth can be read back where the occlusion work needs it.
        // Physical bodies outlive the client's view distance, but the block entities on them do not:
        // this fills in the cannon barrels and control surfaces Sable's section-based pass leaves out
        // (migrated from cbcmuzzlelight 0.1.26, see HANDOFF 8.27).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SubLevelFarEntities::onRenderLevelStage);
        // The trail cannot tell "no positions arrived" from "no frames drawn" on its own, so it gets a
        // command that lays a trail through its own code and nothing else.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TrailDebugCommand::register);
        // The smoke material contract is testable on its own: /slosmoke switches one candidate at a
        // time (depth write, blend, one lobe) so a screenshot can be attributed to exactly one of them.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SmokeDebugCommand::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SmokeBenchmark::onFrame);
        // F9 and the mod-list button open the same configuration manager.
        bus.addListener(dev.sablelightoverhaul.client.ui.ConfigKey::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(dev.sablelightoverhaul.client.ui.ConfigKey::tick);
    }
    /** The stand-in draws the model of the block it replaced, so it needs a renderer of its own. */
    private static void blockEntityRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(dev.sablelightoverhaul.lighting.LightingBlocks.LASER_TEST_ENTITY.get(), LaserTestRenderer::new);
        event.registerBlockEntityRenderer(dev.sablelightoverhaul.lighting.LightingBlocks.LIGHT_SOURCE_ENTITY.get(), LightSourceRenderer::new);
        event.registerBlockEntityRenderer(dev.sablelightoverhaul.lighting.LightingBlocks.SPOTLIGHT_ENTITY.get(), SpotlightRenderer::new);
    }
    private static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(SableLightOverhaul.AUTO_MUZZLE.get(), AutoMuzzleParticle.Provider::new);
        event.registerSpriteSet(SableLightOverhaul.GROUND_DUST.get(), GroundDustParticle.Provider::new);
        event.registerSpriteSet(SableLightOverhaul.PLUME.get(), PlumeEmitterParticle.Provider::new);
        event.registerSpriteSet(SableLightOverhaul.PLUME_SMOKE.get(), PlumeSmokeParticle.Provider::new);
        event.registerSpriteSet(SableLightOverhaul.FIREBALL.get(), FireballParticle.Provider::new);
        event.registerSpriteSet(SableLightOverhaul.IMPACT.get(), ImpactParticle.Provider::new);
        event.registerSpriteSet(SableLightOverhaul.TRAIL.get(), TrailParticle.Provider::new);
        event.registerSpriteSet(SableLightOverhaul.PROJECTILE_STREAK.get(), ProjectileStreaks.Provider::new);
        event.registerSpriteSet(SableLightOverhaul.FLAK_PUFF.get(), FlakCloudPuffParticle.Provider::new);
        // The shockwave puff: our own type so it gets its own render queue (see ShockwaveParticle).
        event.registerSpriteSet(SableLightOverhaul.SHOCKWAVE.get(), ShockwaveParticle.Provider::new);
        // A smoke vent's puff: the vanilla smoke frames, with the page's opacity and fade.
        event.registerSpriteSet(SableLightOverhaul.SMOKE.get(), SmokeParticle.Provider::new);
    }
}
