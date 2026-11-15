package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DustMaterialPayloadTest {
    @Test void materialOriginSurvivesNetworkAndGeneratorRandomisation(){
        var p=new GroundDustParticleData(3,0xAAAAAA,60,1,false,.75f,.08f,.3f,.4f,false)
            .withMaterial(1234,BlockPos.asLong(-100,74,600));
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{
            GroundDustParticleData.STREAM_CODEC.encode(b,p);b.writeInt(0x12345678);
            assertEquals(p,GroundDustParticleData.STREAM_CODEC.decode(b));assertEquals(0x12345678,b.readInt());
            var changed=p.with(6,80,2,.8f,.06f,.4f);
            assertEquals(p.materialState(),changed.materialState());assertEquals(p.materialPos(),changed.materialPos());
            assertEquals(-1,p.withColor(0x112233).materialState(),"explicit fixed tint replaces material source");
        }finally{b.release();}
    }
    @Test void materialIdsAreBoundedAndOrdinaryDustHasNoSource(){
        var p=new GroundDustParticleData(3,0xFFFFFF,60,1,false,.75f,.08f,.3f);
        assertEquals(-1,p.materialState());assertEquals(0,p.materialPos());
        assertThrows(IllegalArgumentException.class,()->p.withMaterial(-2,0));
        assertThrows(IllegalArgumentException.class,()->p.withMaterial(Integer.MAX_VALUE,0));
    }
}
