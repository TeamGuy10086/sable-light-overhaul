package dev.cbcmuzzlelight.client;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;

public final class LambInitializer implements DynamicLightsInitializer {
    @Override public void onInitializeDynamicLights(DynamicLightsContext context) {
        LambAreaBackend.install(context.dynamicLightBehaviorManager());
    }
    @Override public void onInitializeDynamicLights(ItemLightSourceManager ignored) {}
}
