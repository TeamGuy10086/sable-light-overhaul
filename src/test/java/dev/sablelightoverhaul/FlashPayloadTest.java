package dev.sablelightoverhaul;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashPayloadTest {
    @Test void chargeAndPlumeSizeRoundTripWithCoordinates() {
        var payload=new FlashPayload(ResourceLocation.parse("minecraft:overworld"),100.25,80,200,0,0,1,1,42,100,80,200,8.5f,12.25f);
        assertEquals(1.0f,payload.fireballSizeScale());
        assertEquals(payload,roundTrip(payload));
    }

    @Test void halfSizeFireballRoundTripsWithoutChangingActualMounts() {
        var dimension=ResourceLocation.parse("minecraft:overworld");
        var unmapped=new FlashPayload(dimension,1,2,3,0,0,1,0,42,1,2,3,8,8,null,false,FlashPayload.HALF_SIZE_FIREBALL);
        var mounted=new FlashPayload(dimension,1,2,3,0,0,1,0,42,1,2,3,8,8,null,true,FlashPayload.HALF_SIZE_FIREBALL);
        assertEquals(0.5f,roundTrip(unmapped).fireballSizeScale());
        assertEquals(1.0f,roundTrip(mounted).fireballSizeScale());
    }

    private static FlashPayload roundTrip(FlashPayload payload) {
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            FlashPayload.CODEC.encode(buf,payload);
            var decoded=FlashPayload.CODEC.decode(buf);
            assertEquals(0,buf.readableBytes());
            return decoded;
        } finally { buf.release(); }
    }
}
