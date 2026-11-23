package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.NavalAudio;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
abstract class NavalAudioDuckingMixin {
    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow private float calculateVolume(SoundInstance sound) { throw new AssertionError(); }
    @Unique private float slo$lastGain = 1;

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "play",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float slo$interceptionInitialGain(float gain, SoundInstance sound) {
        return dev.sablelightoverhaul.client.InterceptionAudio.mixGain(sound, gain);
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At("RETURN"), cancellable = true)
    private void slo$interceptionUpdatedGain(SoundInstance sound,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> result) {
        float normal = result.getReturnValueF();
        float adjusted = dev.sablelightoverhaul.client.InterceptionAudio.mixGain(sound, normal);
        if (adjusted != normal) result.setReturnValue(adjusted);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void slo$duckVanilla(boolean paused, CallbackInfo ci) {
        float gain = NavalAudio.vanillaGain();
        if (gain == 1 && slo$lastGain == 1) return;
        slo$lastGain = gain;
        instanceToChannel.forEach((sound, handle) -> {
            if (!sound.getLocation().getNamespace().equals("minecraft")) return;
            float volume = calculateVolume(sound) * gain;
            handle.execute(channel -> channel.setVolume(volume));
        });
    }
}
