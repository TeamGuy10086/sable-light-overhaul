package dev.sablelightoverhaul;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TransientParticleBatchTest {
 @Test void temporaryMergeDoesNotChangeTickOrEvictionQueues(){var source=new ArrayDeque<>(List.of(1,2));var dest=new ArrayDeque<>(List.of(3,4));Map<String,Queue<Integer>> map=new HashMap<>();map.put("s",source);map.put("d",dest);var batch=new TransientParticleBatch<String,Integer>();assertTrue(batch.begin(map,"s","d",4));assertTrue(map.get("s").isEmpty());assertEquals(List.of(3,4,1,2),new ArrayList<>(map.get("d")));map.get("d").clear();assertEquals(List.of(1,2),new ArrayList<>(source));batch.close();assertSame(source,map.get("s"));assertSame(dest,map.get("d"));assertEquals(List.of(3,4),new ArrayList<>(dest));}
 @Test void finallyRestoresQueuesOnDrawFailureAndScratchCanBeReused(){Map<String,Queue<Integer>> map=new HashMap<>();var source=new ArrayDeque<>(List.of(1));var dest=new ArrayDeque<>(List.of(2));map.put("s",source);map.put("d",dest);var batch=new TransientParticleBatch<String,Integer>();assertThrows(IllegalStateException.class,()->{assertTrue(batch.begin(map,"s","d",5));try(batch){throw new IllegalStateException();}});assertSame(source,map.get("s"));assertSame(dest,map.get("d"));assertTrue(batch.begin(map,"s","d",5));assertEquals(2,map.get("d").size());batch.close();}
 @Test void budgetAndMissingQueuesSkipWithoutMutation(){Map<String,Queue<Integer>> map=new HashMap<>();var source=new ArrayDeque<>(List.of(1,2));var dest=new ArrayDeque<>(List.of(3));map.put("s",source);map.put("d",dest);var batch=new TransientParticleBatch<String,Integer>();assertFalse(batch.begin(map,"s","d",2));assertSame(source,map.get("s"));assertFalse(batch.begin(map,"other","d",0));}
}
