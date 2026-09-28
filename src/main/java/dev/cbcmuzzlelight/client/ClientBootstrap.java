package dev.cbcmuzzlelight.client;

import dev.cbcmuzzlelight.MuzzleLight;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MuzzleLight.ID, dist = Dist.CLIENT)
public final class ClientBootstrap {
    public ClientBootstrap(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, MuzzleClientConfig.SPEC, "cbcmuzzlelight-client.toml");
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        MuzzleLight.receiver = ClientFlashes::receive;
        MuzzleLight.sourceReceiver = ExtraLights::receive;
        MuzzleLight.flashlightReceiver = HeldLights::receive;
        // Other players' lamps are drawn from the same place, from the list the server sends.
        MuzzleLight.flashlightPeersReceiver = HeldLights::receivePeers;
        // The trail is laid by the client, from these positions plus the shells it tracks itself.
        MuzzleLight.shellTrackReceiver = TrailTrack::receive;
        // The shockwave is the same split: the server measured the explosion, the client draws it.
        MuzzleLight.shockwaveReceiver = Shockwave::receive;
        bus.addListener(ClientBootstrap::particles);
        bus.addListener(ClientBootstrap::blockEntityRenderers);
        bus.addListener(GpuLightRenderer::registerShader);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(GpuLightRenderer::onRenderLevelStage);
        // The trail cannot tell "no positions arrived" from "no frames drawn" on its own, so it gets a
        // command that lays a trail through its own code and nothing else.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TrailDebugCommand::register);
    }
    /** The stand-in draws the model of the block it replaced, so it needs a renderer of its own. */
    private static void blockEntityRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(dev.cbcmuzzlelight.lighting.LightingBlocks.LIGHT_SOURCE_ENTITY.get(), LightSourceRenderer::new);
    }
    private static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MuzzleLight.AUTO_MUZZLE.get(), AutoMuzzleParticle.Provider::new);
        event.registerSpriteSet(MuzzleLight.GROUND_DUST.get(), GroundDustParticle.Provider::new);
        event.registerSpriteSet(MuzzleLight.IMPACT.get(), ImpactParticle.Provider::new);
        event.registerSpriteSet(MuzzleLight.TRAIL.get(), TrailParticle.Provider::new);
    }
}
