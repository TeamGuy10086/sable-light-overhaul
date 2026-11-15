package dev.sablelightoverhaul;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** One start event; nodes and their motion never cross the network. Local means cannon-local, not plot. */
public record TankAfterSmokePayload(ResourceLocation dimension,int entityId,UUID entityUuid,UUID body,
        Vec3 mouth,Vec3 axis,long firedAt,long seed) implements CustomPacketPayload {
    public static final Type<TankAfterSmokePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID,"tank_after_smoke"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TankAfterSmokePayload> CODEC=StreamCodec.of((b,p)->{
        b.writeResourceLocation(p.dimension);b.writeVarInt(p.entityId);b.writeUUID(p.entityUuid);
        b.writeBoolean(p.body!=null);if(p.body!=null)b.writeUUID(p.body);
        write(b,p.mouth);write(b,p.axis);b.writeLong(p.firedAt);b.writeLong(p.seed);
    },b->new TankAfterSmokePayload(b.readResourceLocation(),b.readVarInt(),b.readUUID(),
            b.readBoolean()?b.readUUID():null,read(b),read(b),b.readLong(),b.readLong()));
    private static void write(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 read(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @Override public Type<TankAfterSmokePayload> type(){return TYPE;}
}
