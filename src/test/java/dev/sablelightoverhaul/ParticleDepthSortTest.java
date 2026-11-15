package dev.sablelightoverhaul;

import dev.sablelightoverhaul.client.ParticleDepthSort;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The draw order for one particle layer.
 *
 * <p>What matters here is not only "is it sorted" but "is every particle still in the list": the helper
 * hands a permutation back to a render hook that rebuilds the queue from it, so an index that goes
 * missing is a particle that silently stops being drawn -- and one that repeats is a particle drawn
 * twice. Both are checked directly rather than inferred from the order.
 */
class ParticleDepthSortTest {
    private static int[] order(double[][] points,double cx,double cy,double cz) {
        int n=points.length;
        double[] x=new double[n],y=new double[n],z=new double[n];
        for(int i=0;i<n;i++){ x[i]=points[i][0]; y[i]=points[i][1]; z[i]=points[i][2]; }
        return ParticleDepthSort.farToNear(x,y,z,n,cx,cy,cz,new long[n],new int[n]);
    }

    private static double distance(double[][] points,int index,double cx,double cy,double cz) {
        double dx=points[index][0]-cx, dy=points[index][1]-cy, dz=points[index][2]-cz;
        return dx*dx+dy*dy+dz*dz;
    }

    @Test void theFarthestParticleIsDrawnFirst() {
        double[][] points={{0,0,1},{0,0,40},{0,0,7},{0,0,90},{0,0,20}};
        int[] order=order(points,0,0,0);
        assertEquals(3,order[0],"the far end of the layer has to go into the buffer first");
        assertEquals(0,order[4],"and the near end last");
        for(int i=1;i<order.length;i++) {
            assertTrue(distance(points,order[i-1],0,0,0)>=distance(points,order[i],0,0,0),
                "distances must not increase along the draw order");
        }
    }

    @Test void everyParticleIsDrawnExactlyOnce() {
        double[][] points=new double[64][3];
        for(int i=0;i<points.length;i++) {
            points[i]=new double[]{(i%7)-3,(i%5)-2,(i*13%23)-11};
        }
        int[] order=order(points,1,-1,0.5);
        boolean[] seen=new boolean[points.length];
        for(int index:order) {
            assertTrue(index>=0&&index<points.length,"index out of range");
            assertFalse(seen[index],"index repeated");
            seen[index]=true;
        }
        for(int i=0;i<seen.length;i++) assertTrue(seen[i],"index missing");
    }

    @Test void tiesAreKeptAndTheOrderIsStable() {
        double[][] points={{1,0,0},{-1,0,0},{0,1,0},{0,0,1},{0,0,-1}};
        int[] order=order(points,0,0,0);
        assertEquals(points.length,Arrays.stream(order).distinct().count());
        assertArrayEquals(order,order(points,0,0,0),"equal input must give an equal order");
    }

    @Test void rebuildingTheQueueEachFrameMustNotFlipEqualDepthSmoke() {
        for(boolean seeded:new boolean[]{false,true}) {
            int[] identities={10,11,12,13};
            double[] x={0,0,0,0},y={0,0,0,0},z={20,20,20,20};
            for(int frame=0;frame<60;frame++) {
                int[] indices={0,1,2,3};
                if(seeded) assertTrue(ParticleDepthSort.farToNearSeeded(x,y,z,4,0,0,0,new long[4],indices,16));
                else indices=ParticleDepthSort.farToNear(x,y,z,4,0,0,0,new long[4],indices);
                int[] next=new int[4];
                for(int i=0;i<4;i++)next[i]=identities[indices[i]];
                assertArrayEquals(new int[]{10,11,12,13},next,"equal-depth queue flipped at frame "+frame+", seeded="+seeded);
                identities=next;
            }
        }
    }
    @Test void aCameraFarInsideTheLayerStillOrdersEverything() {
        // The camera sitting exactly on a particle is the case that would divide by zero if the key were
        // a distance rather than a squared one: that particle sorts first and everything else follows.
        double[][] points={{5,5,5},{5,5,5.0001},{5,5,40}};
        int[] order=order(points,5,5,5);
        assertEquals(2,order[0]);
        assertEquals(3,Arrays.stream(order).distinct().count());
    }

    @Test void hugeDistancesDoNotLeakIntoTheIndex() {
        // The distance rides in the high half of the long and the index in the low half; the far points
        // here are beyond any render distance, which is where a packing mistake would show up as the
        // wrong index coming back.
        double[][] points={{0,0,0},{1e7,0,0},{0,1e7,0},{0,0,0.5}};
        int[] order=order(points,0,0,0);
        assertEquals(2,Math.max(order[0],order[1]),"the two far points are drawn first");
        assertTrue((order[0]==1&&order[1]==2)||(order[0]==2&&order[1]==1),"both far points are present");
        assertEquals(3,order[2]);
        assertEquals(0,order[3],"the nearest point is drawn last");
    }

    @Test void scratchSmallerThanTheLayerIsRefused() {
        double[] x={0,1},y={0,1},z={0,1};
        assertThrows(IllegalArgumentException.class,
            ()->ParticleDepthSort.farToNear(x,y,z,2,0,0,0,new long[1],new int[2]));
        assertThrows(IllegalArgumentException.class,
            ()->ParticleDepthSort.farToNear(x,y,z,2,0,0,0,new long[2],new int[1]));
    }

    @Test void anEmptyOrSingleParticleLayerIsLeftAlone() {
        double[] x={3},y={4},z={5};
        int[] order=ParticleDepthSort.farToNear(x,y,z,1,0,0,0,new long[1],new int[1]);
        assertEquals(1,order.length);
        assertEquals(0,order[0]);
    }
    // ------------------------------------------------------------------------------------------------
    // Starting from the previous frame's order instead of sorting from scratch.
    //
    // The claim to check is not "it is fast" -- that is the simulation's job -- but "it is the same
    // answer". The seeded pass is allowed to change how the order is reached, never what the order is.
    // ------------------------------------------------------------------------------------------------

    private static double[][] scattered(int n, long seed) {
        java.util.Random random = new java.util.Random(seed);
        double[][] points = new double[n][3];
        for (int i = 0; i < n; i++) {
            // Spread out enough that no two squared distances collide: with ties the seeded pass may
            // legally return a different permutation of equal keys, and the comparison would be noise.
            points[i] = new double[]{random.nextDouble() * 200 - 100,
                random.nextDouble() * 40 - 20, random.nextDouble() * 200 - 100};
        }
        return points;
    }

    private static double[] axis(double[][] points, int which) {
        double[] out = new double[points.length];
        for (int i = 0; i < points.length; i++) out[i] = points[i][which];
        return out;
    }

    @Test void aSeedThatIsNearlyRightGivesTheSameOrderAsSortingFromScratch() {
        double[][] points = scattered(400, 11L);
        int n = points.length;
        int[] fromScratch = ParticleDepthSort.farToNear(
            axis(points, 0), axis(points, 1), axis(points, 2), n, 0, 0, 0, new long[n], new int[n]);

        // One frame of drift: swap a handful of neighbouring pairs, which is all that motion ever does.
        int[] seed = fromScratch.clone();
        for (int k = 0; k < 40; k++) {
            int i = (k * 7) % (n - 1);
            int swap = seed[i];
            seed[i] = seed[i + 1];
            seed[i + 1] = swap;
        }
        boolean finished = ParticleDepthSort.farToNearSeeded(
            axis(points, 0), axis(points, 1), axis(points, 2), n, 0, 0, 0, new long[n], seed, 8 * n);
        assertTrue(finished, "a nearly right seed has to be accepted");
        assertArrayEquals(fromScratch, seed, "the seeded pass must land on the same order");
    }

    @Test void aSeedThatIsUselessIsRefusedInsteadOfCrawling() {
        double[][] points = scattered(400, 13L);
        int n = points.length;
        int[] fromScratch = ParticleDepthSort.farToNear(
            axis(points, 0), axis(points, 1), axis(points, 2), n, 0, 0, 0, new long[n], new int[n]);
        int[] backwards = new int[n];
        for (int i = 0; i < n; i++) backwards[i] = fromScratch[n - 1 - i];
        boolean finished = ParticleDepthSort.farToNearSeeded(
            axis(points, 0), axis(points, 1), axis(points, 2), n, 0, 0, 0, new long[n], backwards, 4 * n);
        assertFalse(finished, "a reversed seed must be handed back for a full sort, not fixed in place");
    }

    @Test void thePreviousFrameHoldsUpWhenTheCameraMoves() {
        // The realistic case: sort once, move the camera a fraction of a block, and seed from that order.
        double[][] points = scattered(500, 17L);
        int n = points.length;
        int[] frameOne = ParticleDepthSort.farToNear(
            axis(points, 0), axis(points, 1), axis(points, 2), n, 0, 0, 0, new long[n], new int[n]);
        int[] seeded = frameOne.clone();
        boolean finished = ParticleDepthSort.farToNearSeeded(
            axis(points, 0), axis(points, 1), axis(points, 2), n, 0.05, -0.02, 0.03, new long[n], seeded, 4 * n);
        assertTrue(finished, "a camera that moved a fraction of a block must not invalidate the order");
        int[] fromScratch = ParticleDepthSort.farToNear(
            axis(points, 0), axis(points, 1), axis(points, 2), n, 0.05, -0.02, 0.03, new long[n], new int[n]);
        assertArrayEquals(fromScratch, seeded, "and the answer must be the sorted one either way");
    }
}
