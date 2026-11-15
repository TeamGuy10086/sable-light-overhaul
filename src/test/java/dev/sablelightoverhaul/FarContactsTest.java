package dev.sablelightoverhaul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FarContactsTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    @Test void maximumSizeSnapshotPreservesTypeBoundsAndWorldPosition() {
        var contact = new FarContacts.Contact(17, ResourceLocation.withDefaultNamespace("zombie"),
            new Vec3(-1_000_000.25, 183, 1_000_000.125), .6f, 1.95f, 0xFF6464);
        var snapshot = new FarContacts.Snapshot(DIMENSION, Collections.nCopies(FarContacts.LIMIT, contact));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            FarContacts.Snapshot.CODEC.encode(buffer, snapshot);
            assertEquals(snapshot, FarContacts.Snapshot.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void v3PreservesHealthAndV2KeepsItsPreviousWireFormat() {
        var type = ResourceLocation.withDefaultNamespace("zombie");
        var uuid = java.util.UUID.randomUUID();
        var health = new FarContacts.Health(12.5f, 20);
        var living = new FarContacts.Contact(17, type, Vec3.ZERO, .6f, 1.95f, 0xFF6464, uuid, true, false, health);
        var unavailable = new FarContacts.Contact(18, type, Vec3.ZERO, .6f, 1.95f, 0xFF6464, uuid, true, false);
        var snapshot = new FarContacts.Snapshot(DIMENSION, java.util.List.of(living, unavailable));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var updated = new FarContacts.HealthSnapshot(snapshot);
            FarContacts.HealthSnapshot.CODEC.encode(buffer, updated);
            int v3Bytes = buffer.readableBytes();
            assertEquals(updated, FarContacts.HealthSnapshot.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());

            FarContacts.IdentifiedSnapshot.CODEC.encode(buffer, new FarContacts.IdentifiedSnapshot(snapshot));
            assertEquals(v3Bytes - 4 * Float.BYTES, buffer.readableBytes());
            var legacy = FarContacts.IdentifiedSnapshot.CODEC.decode(buffer).snapshot().contacts().getFirst();
            assertEquals(uuid, legacy.uuid());
            assertNull(legacy.health());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void rejectsOversizedSnapshotBeforeDecodingAnyEntries() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeResourceLocation(DIMENSION);
            buffer.writeVarInt(FarContacts.LIMIT + 1);
            assertThrows(DecoderException.class, () -> FarContacts.Snapshot.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test void identifiedSnapshotPreservesTheServerEntityIdentityAndClassification() {
        var contact=new FarContacts.Contact(17,ResourceLocation.withDefaultNamespace("zombie"),
                new Vec3(210,183,-80),.6f,1.95f,0xFF6464,UUID.randomUUID(),true,false);
        var payload=new FarContacts.IdentifiedSnapshot(new FarContacts.Snapshot(DIMENSION,List.of(contact)));
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            FarContacts.IdentifiedSnapshot.CODEC.encode(buffer,payload);
            assertEquals(payload,FarContacts.IdentifiedSnapshot.CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
