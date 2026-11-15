package dev.sablelightoverhaul.mixin;

import dev.sablelightoverhaul.client.LightFields;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tell the light fields about a block change, which is the one thing they could never find out by themselves.
 *
 * <p>A field caches the light cost of every cell in its box when it is flooded, so a block that appears or
 * disappears inside that box invalidates the flood. Nothing was ever calling the method that says so: placing
 * a lamp looked fine because a new lamp is a new field and a new field is built from scratch, but breaking a
 * block left the lamp lighting a room that no longer existed. The field was not stale in the sense of "not
 * recomputed" -- it was simply never asked to recompute, and no diagnostic could show that, because the data
 * in it was internally consistent.
 *
 * <p><b>Why this method.</b> {@code ClientLevel.setBlocksDirty} is the single point every block change on
 * this side passes through, and it is a client-only override, so a server can never be affected by this mixin:
 * the vanilla server's own update packet ({@code setServerVerifiedBlockState}), a section blocks update
 * packet, and the client's own prediction all end up in {@code Level.setBlock}, which calls this for every
 * state that actually changed. Hooking {@code sendBlockUpdated} instead would have missed the changes whose
 * flags do not ask for a client update, and hooking {@code destroyBlock} would have missed everything the
 * server did.
 */
@Mixin(ClientLevel.class)
public abstract class FieldBlockChangeMixin {
    @Inject(
            method = "setBlocksDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"))
    private void slo$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo info) {
        LightFields.blockChanged(pos);
        // The spot shadow maps are cast on this side too, so a wall that appears or is broken has to reach the
        // map the same way it reaches a field; without this the beam keeps shining through a hole that is gone.
        dev.sablelightoverhaul.client.SpotShadows.blockChanged(pos);
    }
}
