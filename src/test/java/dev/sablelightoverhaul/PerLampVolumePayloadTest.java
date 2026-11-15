package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.SourceAnchor;
import dev.sablelightoverhaul.lighting.SourceColor;
import dev.sablelightoverhaul.lighting.SourcePayload;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A lamp's own volume mode -- 0 off, 1 without the shader's swept-space occlusion, 2 with it -- has to reach the
 * client intact, whichever road it takes: the source snapshot the spotlight uses, or the flashlight's own state
 * and peer list. The flashlight color is a hand-typed colour, packed as {@code CUSTOM | 0xRRGGBB}, so it needs a
 * full int on the wire as well.
 */
class PerLampVolumePayloadTest {
    private static final ResourceLocation OVERWORLD=ResourceLocation.parse("minecraft:overworld");
    private static final UUID PEER=UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static RegistryFriendlyByteBuf buffer(){
        return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
    }
    /**
     * A hand-typed sRGB color as the flashlight stores it: {@code CUSTOM | 0xRRGGBB}. Its low byte is zero and its
     * bits 16..23 are all set, so a byte or a short on the wire cannot carry it.
     */
    private static final int CUSTOM_COLOR=SourceColor.custom(0x12FF00);
    /** One lamp of a snapshot, with the volume mode that lamp was given. */
    private static SourcePayload.State source(int id,int volumeMode){
        return new SourcePayload.State(id,new SourceAnchor(null,new BlockPos(-12,100,18+id)),2,true,0,15,10,3,5,2,
            35.5F,-120.25F,0,2,12,96,true,61,0,0,0,0,0.0F,0.0F,0.0F,"",volumeMode);
    }
    private static <T> T viaCodec(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){
        var b=buffer();try{
            codec.encode(b,value);
            T decoded=codec.decode(b);
            assertEquals(0,b.readableBytes());
            return decoded;
        }finally{b.release();}
    }
    @Test void allThreeVolumeModesSurviveTheSourceSnapshotCodec(){
        var payload=new SourcePayload(OVERWORLD,List.of(source(0,0),source(1,1),source(2,2)));
        var decoded=viaCodec(SourcePayload.CODEC,payload);
        assertEquals(payload,decoded);
        assertEquals(List.of(0,1,2),decoded.states().stream().map(SourcePayload.State::volumeMode).toList(),
            "each lamp keeps its own mode: they are not one value for the whole list");
        SourcePayload.State oldStyle=new SourcePayload.State(7,new SourceAnchor(null,BlockPos.ZERO),2,true,0,15,10,3,
            5,2,35.5F,-120.25F,0,2,12,96,true,61,0,0,0,0,0.0F,0.0F,0.0F,"");
        assertEquals(2,oldStyle.volumeMode(),"a spotlight built without the field keeps the shader occlusion");
        assertEquals(0,new SourcePayload.State(8,new SourceAnchor(null,BlockPos.ZERO),2,false,15,0,0,0,0,0,
            0,0,0,0,0,0,false,63,0,0,0,0,0.0F,0.0F,0.0F,"").volumeMode(),
            "a static lamp built without the field has no beam to occlude");
    }
    @Test void allThreeVolumeModesAndACustomColorSurviveTheFlashlightStateCodec(){
        // As the server sends it: a palette index, then hand-typed colors whose low byte is zero.
        for(int color:new int[]{3,CUSTOM_COLOR,SourceColor.custom(0xFF0000)}){
            for(int volumeMode=0;volumeMode<=2;volumeMode++){
                var state=new FlashlightStatePayload(true,color,5,96,52,300,2,15,8,4,volumeMode);
                assertEquals(state,viaCodec(FlashlightStatePayload.CODEC,state),"color "+color+" mode "+volumeMode);
                var peer=new FlashlightPeersPayload.Peer(PEER,color,96,52,300,2,15,8,4,volumeMode);
                assertEquals(List.of(peer),viaCodec(FlashlightPeersPayload.CODEC,new FlashlightPeersPayload(List.of(peer))).peers(),
                    "color "+color+" mode "+volumeMode);
            }
        }
    }
}
