package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuzzleGasTest {
    @org.junit.jupiter.api.Test void softSmokeVertexContractPreservesLightAndLocalCoordinates() {
        for(int sky=0;sky<=240;sky+=16)for(int block=0;block<=240;block+=16){
            int light=GasVertexData.smokeLight((sky<<16)|block);
            assertEquals(15,light&15);assertEquals(11,(light>>16)&15);
            assertEquals(block,(light&65535)&~15);assertEquals(sky,(light>>>16)&~15);
        }
        for(int seed=0;seed<64;seed++)for(float x:new float[]{-1,1}){
            float encoded=seed*4+x,decodedSeed=(float)Math.floor((encoded+1.5)/4);
            assertEquals(seed,decodedSeed);assertEquals(x,encoded-decodedSeed*4);
        }
    }

    @org.junit.jupiter.api.Test void explicitPuffSizeRestoresOldTwelveBarrelScaleAndAllowsLegacyFallback() {
        float oldBase=(12f/1.5f)*.25f;
        float current=MuzzleGasModel.smokeHalfWidth(.45f,0,1,1);
        float restored=MuzzleGasModel.smokeHalfWidth(.45f,2,1,1);
        org.junit.jupiter.api.Assertions.assertEquals(.45f,current,1e-6f);
        org.junit.jupiter.api.Assertions.assertEquals(oldBase,restored,1e-6f);
        org.junit.jupiter.api.Assertions.assertEquals(4.444444f,restored/current,1e-5f);
        org.junit.jupiter.api.Assertions.assertEquals(2*restored*MuzzleGasModel.nativeSmokeGrowth(.5f),10,1e-5f);
        org.junit.jupiter.api.Assertions.assertEquals(4,MuzzleGasModel.smokeHalfWidth(.45f,2,2,1),1e-6f);
        org.junit.jupiter.api.Assertions.assertEquals(.9f,MuzzleGasModel.smokeHalfWidth(.45f,0,2,1),1e-6f);
        // Scatter and travel remain server data; changing the inherited width does not alter an explicit override.
        org.junit.jupiter.api.Assertions.assertEquals(restored,MuzzleGasModel.smokeHalfWidth(4,2,1,1),1e-6f);
    }

    @org.junit.jupiter.api.Test void nativeCloudExpandsAndSurvivesTheShortFireball() {
        org.junit.jupiter.api.Assertions.assertEquals(.9f,MuzzleGasModel.nativeSmokeGrowth(0),1e-6f);
        org.junit.jupiter.api.Assertions.assertEquals(4.1f,MuzzleGasModel.nativeSmokeGrowth(1),1e-6f);
        org.junit.jupiter.api.Assertions.assertTrue(MuzzleGasModel.nativeSmokeAlpha(1,2f/30)>.99f);
        org.junit.jupiter.api.Assertions.assertTrue(MuzzleGasModel.nativeSmokeAlpha(1,.5f)>.7f);
        org.junit.jupiter.api.Assertions.assertEquals(0,MuzzleGasModel.nativeSmokeAlpha(1,1));
    }

    @Test void nativeSmokePreservesTheOriginalCoolingWindowAndReleaseOpacity() {
        var nativeStyle=MuzzleGasModel.SmokeStyle.NATIVE;
        var compact=MuzzleGasModel.SmokeStyle.COMPACT;
        assertEquals(30,MuzzleGasModel.smokeLifetime(18,nativeStyle));
        assertEquals(80,MuzzleGasModel.smokeLifetime(80,nativeStyle));
        assertEquals(18,MuzzleGasModel.smokeLifetime(18,compact));
        assertEquals(0.8f,MuzzleGasModel.smokeBirthAlpha(.8f,.25f,.5f,1.5f,nativeStyle),1e-6);
        assertEquals(0.5f,MuzzleGasModel.smokeBirthAlpha(.8f,.75f,.5f,1.5f,nativeStyle),1e-6);
        assertEquals(0.68f,MuzzleGasModel.smokeBirthAlpha(.8f,.25f,.5f,1.5f,compact),1e-6);
        assertEquals(0,MuzzleGasModel.smokeBirthAlpha(1,1,0,2,nativeStyle));
    }
    @Test void shotSeedSurvivesSignedShortAndIsIndependentOfCoordinate() {
        for(double seed:new double[]{0,0.1,8,32,48,63.99,64})for(double z:new double[]{-100,-1,0,1,100}) {
            int packed=GasVertexData.light(z,seed);
            int throughAttributes=(((int)(short)(packed>>>16)&65535)<<16)|((int)(short)packed&65535);
            assertEquals(seed,GasVertexData.seed(throughAttributes),0.0005);
            assertEquals(z,GasVertexData.transverse(throughAttributes),0.006+Math.abs(z)*0.006);
        }
    }
    @Test void muzzleMetadataRestoresLightingAndDoesNotUseNativeSkyNibble() {
        for(int block=0;block<=240;block++)for(int sky=0;sky<=240;sky+=16)for(boolean flame:new boolean[]{false,true}) {
            int tagged=GasVertexData.materialLight((sky<<16)|block,flame);
            assertEquals(11,(tagged>>16)&15);
            assertEquals(sky,(tagged>>16)&65520);
            assertEquals(block&~15,(tagged&65535)&~15);
            assertEquals(flame?13:14,tagged&15);
        }
    }
    @Test void localCoordinatesSurviveSignedShortParticleAttributes() {
        for(double z:new double[]{-10000,-100,-4,-1,-0.01,0,0.01,1,4,100,10000}) {
            int encoded=GasVertexData.light(z);
            short attribute=(short)encoded;
            int raw=attribute & 65535;
            assertEquals(GasVertexData.TAG,raw & 15);
            // Native attributes are signed shorts; Photon must reinterpret before decoding.
            int photonRaw=((int)Math.floor((double)attribute+0.5)) & 65535;
            assertEquals(raw,photonRaw);
            assertEquals(z,GasVertexData.transverse(raw),0.006+Math.abs(z)*0.006);
        }
    }
    @Test void integrationStepSurvivesEightBitVertexColor() {
        for(double ratio:new double[]{0.02,0.05,0.1,0.5,1,3,10,100,1000}) {
            float wire=Math.round(GasVertexData.step(ratio)*255)/255f;
            assertEquals(ratio,GasVertexData.stepRatio(wire),0.024*(1+ratio));
        }
    }
    @Test void aspectRatioRetainsRelativePrecisionAcrossThinAndWideVolumes() {
        for(double aspect:new double[]{0.004,0.02,0.1,0.5,1,3,20,100}) {
            float wire=(int)(GasVertexData.aspect(aspect)*255)/255f;
            assertEquals(aspect,GasVertexData.aspectRatio(wire),aspect*0.024);
        }
    }
    @Test void smokeAmountDoesNotDependOnReleaseDuration() {
        for(int total:new int[]{0,1,3,24,128}) for(int duration:new int[]{1,4,7,12}) for(int quality:new int[]{1,3,5}) {
            int count=0;
            for(int tick=0;tick<duration;tick++)count+=MuzzleGasModel.smokeChildren(total,duration,tick,quality);
            assertEquals(Math.round(total*quality/5f),count);
            assertEquals(0,MuzzleGasModel.smokeChildren(total,duration,duration,quality));
        }
    }
    @Test void puffsSlowDownAndDoNotExceedTheirForwardTravelEnvelope() {
        // Births cover the front half of the travel; the brake envelope is largest in the first
        // few ticks and then leaves the cloud nearly stationary. The tail stays inside.
        for(double reach:new double[]{0.1,4,20,96}) for(int i=0;i<=20;i++) {
            double t=i/20.0,v0=MuzzleGasModel.smokeSpeed(reach,t),distance=reach*(0.15+0.45*t);
            double v=v0;
            for(int tick=1;tick<=100;tick++) {v=v0/(1+0.5*tick*3);distance+=v;}
            assertTrue(distance<reach*2.5,"travel envelope");
            assertTrue(v<v0*0.02,"jet must slow down");
        }
    }
    @Test void smokeExpansionIsIndependentOfReleaseDurationAndBounded() {
        for(float expansion:new float[]{1,1.8f,4}) {
            assertEquals(1,MuzzleGasModel.smokeGrowth(0,expansion));
            assertEquals(expansion,MuzzleGasModel.smokeGrowth(1,expansion));
            float previous=1;
            for(int i=0;i<=100;i++) {
                float g=MuzzleGasModel.smokeGrowth(i/100f,expansion);
                assertTrue(g>=previous&&g<=expansion);previous=g;
            }
        }
    }
    @Test void tankFadeReachesZeroBeforeRemovalAndKeepsItsCurve() {
        assertEquals(1f,MuzzleGasModel.tankFadeAlpha(1,0,0.2f,1.2f,1.6f),1e-6);
        assertEquals(0f,MuzzleGasModel.tankFadeAlpha(1,1,0.2f,1.2f,1.6f),1e-6);
        // With rate 1 the curve reaches zero exactly at the end; with rate > 1 it reaches zero
        // earlier, so removing the particle can never be the moment alpha pops out.
        assertEquals(0f,MuzzleGasModel.tankFadeAlpha(1,0.9f,0.2f,1.2f,1.6f),1e-6);
        assertTrue(MuzzleGasModel.tankFadeAlpha(1,0.8f,0.2f,1.2f,1.6f)
                < MuzzleGasModel.tankFadeAlpha(1,0.8f,0.2f,1.2f,0.7f));
        float previous=1;
        for(int i=0;i<=100;i++) {
            float a=MuzzleGasModel.tankFadeAlpha(1,i/100f,0.2f,1.2f,1.6f);
            assertTrue(a>=0&&a<=previous);previous=a;
        }
    }
    @Test void childLifetimeJitterIsBoundedAndActuallyVaries() {
        assertEquals(52,MuzzleGasModel.tankLifetime(80,0.35f,0));
        assertEquals(80,MuzzleGasModel.tankLifetime(80,0.35f,0.5f));
        assertEquals(108,MuzzleGasModel.tankLifetime(80,0.35f,1));
        assertEquals(2,MuzzleGasModel.tankLifetime(1,1,0));
        assertEquals(200,MuzzleGasModel.tankLifetime(200,1,1));
    }
    @Test void particleCodecsPreserveWholeEventGeometryWithoutEatingNextPacket() {
        var flame=new FireballParticleData(1.2f,7,0.8f,5.4f,24,12);
        var plume=new PlumeEmitterParticleData(0.6f,10,4,0.7f,0.2f,1.8f,6,18,1.7f);
        var puff=new PlumeSmokeParticleData(0.6f,18,0.3f,0.2f,1.8f,1.7f);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            FireballParticleData.STREAM_CODEC.encode(b,flame);
            PlumeEmitterParticleData.STREAM_CODEC.encode(b,plume);
            PlumeSmokeParticleData.STREAM_CODEC.encode(b,puff);
            b.writeInt(123456);
            assertEquals(flame,FireballParticleData.STREAM_CODEC.decode(b));
            assertEquals(plume,PlumeEmitterParticleData.STREAM_CODEC.decode(b));
            assertEquals(puff,PlumeSmokeParticleData.STREAM_CODEC.decode(b));
            assertEquals(123456,b.readInt());assertEquals(0,b.readableBytes());
        } finally {b.release();}
    }
}
