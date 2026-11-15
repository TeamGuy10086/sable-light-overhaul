package dev.sablelightoverhaul;
import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HullDustPayloadTest {
 @Test void flowSettingsRoundTripAndLeaveFollowingMessageIntact(){
  var data=new GroundDustParticleData(2.5f,0xB39A78,20,1.2f,false,.8f,.1f,.2f,.5f,false);
  var event=new CannonDustPayload(ResourceLocation.parse("minecraft:overworld"),new Vec3(1,2,3),new Vec3(1,0,0),99,List.of(new CannonDustPayload.Surface(new Vec3(1,1,3),new Vec3(0,1,0))),40,0,1,data,data,0,0,0,0,0,0,1,7,new HullDustModel.Settings(.8,.7,.22));
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);CannonDustPayload.CODEC.encode(buf,event);buf.writeInt(42);assertEquals(event,CannonDustPayload.CODEC.decode(buf));assertEquals(42,buf.readInt());buf.release();
 }
 @Test void allAnchoredFacesFitEventBudgetAndRoundTrip(){
  var data=new GroundDustParticleData(2.5f,0xB39A78,20,1.2f,false,.8f,.1f,.2f,.5f,false);
  var surfaces=new java.util.ArrayList<CannonDustPayload.Surface>();var id=java.util.UUID.randomUUID();
  for(int i=0;i<64;i++)surfaces.add(new CannonDustPayload.Surface(new Vec3(i*.1,1,0),new Vec3(0,1,0),id,new Vec3(1000000+i*.1,2,2000000),new Vec3(0,1,0)));
  var event=new CannonDustPayload(ResourceLocation.parse("minecraft:overworld"),new Vec3(1,2,3),new Vec3(1,0,0),99,surfaces,40,0,1,data,data,0,0,0,0,0,0,1,7,new HullDustModel.Settings(.8,.7,.22,.65,.35,.15,.06,.8,3.5,.42,.16,.73,1.4));
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);CannonDustPayload.CODEC.encode(buf,event);assertTrue(buf.readableBytes()<8192);assertEquals(event,CannonDustPayload.CODEC.decode(buf));buf.release();
 }
}
