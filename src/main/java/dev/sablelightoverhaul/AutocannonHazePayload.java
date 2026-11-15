package dev.sablelightoverhaul;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The same firing event on a separate optional channel so medium-cannon haze keeps its own material. */
public record AutocannonHazePayload(CannonDustPayload shot) implements CustomPacketPayload {
    public static final Type<AutocannonHazePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "autocannon_haze"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AutocannonHazePayload> CODEC =
        CannonDustPayload.CODEC.map(AutocannonHazePayload::new, AutocannonHazePayload::shot);

    @Override public Type<AutocannonHazePayload> type() { return TYPE; }
}
