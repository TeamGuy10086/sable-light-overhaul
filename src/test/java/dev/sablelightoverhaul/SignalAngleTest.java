package dev.sablelightoverhaul;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SignalAngleTest {
 @Test void fullSphereRetainsLegacyRangeAndAllDirections(){
  var old=new AreaLight(0,0,0,1,0,0,12,12,.12,6,false,.6,.3);
  var signal=AreaLight.signalPoint(0,0,0,6,360);
  for(double[] p:List.of(new double[]{0,5,0},new double[]{0,-5,0},new double[]{5,0,0},new double[]{13,0,0}))
   assertEquals(old.at(p[0],p[1],p[2]),signal.at(p[0],p[1],p[2]),1e-9);
 }
 @Test void downwardAnglesCoverNarrowHemisphereAndWideCaps(){
  var narrow=AreaLight.signalPoint(0,0,0,6,90);
  assertTrue(narrow.at(0,-5,0)>0);assertEquals(0,narrow.at(0,5,0));assertEquals(0,narrow.at(5,-1,0));
  var hemi=AreaLight.signalPoint(0,0,0,6,180);
  assertTrue(hemi.at(5,-2,0)>0);assertEquals(0,hemi.at(0,5,0));
  var wide=AreaLight.signalPoint(0,0,0,6,270);
  assertTrue(wide.at(5,1,0)>0);assertEquals(0,wide.at(0,5,0));
  assertEquals(0,narrow.at(0,-13,0));
  for(int angle:new int[]{1,90,180,270,359,360})assertTrue(Double.isFinite(AreaLight.signalPoint(0,0,0,6,angle).at(0,0,0)));
 }
 @Test void brightnessAndOpeningTravelIndependentlyForEveryFlare(){
  var states=List.of(new FlarePayload.State(1,false,0,50,0,0,1200,0xFF0000,4,90),
                    new FlarePayload.State(2,false,0,80,0,0,600,0x0000FF,8,270));
  var payload=new FlarePayload(ResourceLocation.parse("minecraft:overworld"),states);
  var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try{FlarePayload.CODEC.encode(b,payload);assertEquals(payload,FlarePayload.CODEC.decode(b));assertEquals(0,b.readableBytes());}finally{b.release();}
  assertThrows(IllegalArgumentException.class,()->new FlarePayload.State(1,false,0,50,0,0,1200,0,4,0));
  assertThrows(IllegalArgumentException.class,()->new FlarePayload.State(1,false,0,50,0,0,1200,0,4,361));
 }
}
