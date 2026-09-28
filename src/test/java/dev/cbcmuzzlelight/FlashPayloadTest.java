package dev.cbcmuzzlelight;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashPayloadTest {
    @Test void chargeAndPlumeSizeRoundTripWithCoordinates() {
        var payload=new FlashPayload(ResourceLocation.parse("minecraft:overworld"),100.25,80,200,0,0,1,1,42,100,80,200,8.5f,12.25f);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            FlashPayload.CODEC.encode(buf,payload);
            assertEquals(payload,FlashPayload.CODEC.decode(buf)); assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }
}
