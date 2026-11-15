package dev.sablelightoverhaul;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SmokeFlipbookResourceTest {
 @Test void allFramesHaveTransparentEdgesAndExtrudedDataGutters() throws Exception {
  try(var input=getClass().getResourceAsStream("/assets/sablelightoverhaul/textures/effect/thermal_smoke.png")){
   assertNotNull(input);var image=ImageIO.read(input);
   assertEquals(SmokeFlipbookLayout.WIDTH,image.getWidth());assertEquals(SmokeFlipbookLayout.HEIGHT,image.getHeight());
   var shapes=new java.util.HashSet<Integer>();
   for(int v=0;v<6;v++)for(int f=0;f<32;f++){
    int tile=SmokeFlipbookLayout.tileIndex(v,f),x=tile%8*132,y=tile/8*132,hash=1;
    for(int q=0;q<132;q++){
     assertEquals(image.getRGB(x+2,y+q),image.getRGB(x,y+q));
     assertEquals(image.getRGB(x+129,y+q),image.getRGB(x+131,y+q));
     assertEquals(image.getRGB(x+q,y+2),image.getRGB(x+q,y));
     assertEquals(image.getRGB(x+q,y+129),image.getRGB(x+q,y+131));
     assertEquals(0,image.getRGB(x,y+q)>>>24);assertEquals(0,image.getRGB(x+131,y+q)>>>24);
     assertEquals(0,image.getRGB(x+q,y)>>>24);assertEquals(0,image.getRGB(x+q,y+131)>>>24);
    }
    for(int q=2;q<130;q+=4)hash=31*hash+image.getRGB(x+q,y+66);
    if(f==0)assertTrue(shapes.add(hash),"each sequence must have a distinct initial shape");
   }
  }
 }

 @Test void cachedTankMasksAllHaveTransparentGuttersAndUniqueShapes() throws Exception {
  try(var input=getClass().getResourceAsStream("/assets/sablelightoverhaul/textures/effect/tank_smoke.png")){
   assertNotNull(input);var img=ImageIO.read(input);assertEquals(1056,img.getWidth());assertEquals(1056,img.getHeight());
   var hashes=new java.util.HashSet<Integer>();
   for(int id=0;id<64;id++){
    int x=id%8*132,y=id/8*132,hash=1;
    for(int q=0;q<132;q++){
     assertEquals(0,img.getRGB(x,y+q)>>>24);assertEquals(0,img.getRGB(x+131,y+q)>>>24);
     assertEquals(0,img.getRGB(x+q,y)>>>24);assertEquals(0,img.getRGB(x+q,y+131)>>>24);
     assertEquals(img.getRGB(x+2,y+q),img.getRGB(x,y+q));assertEquals(img.getRGB(x+q,y+129),img.getRGB(x+q,y+131));
    }
    for(int q=2;q<130;q+=4)hash=31*hash+img.getRGB(x+q,y+66);
    assertTrue(hashes.add(hash));
   }
  }
 }
}
