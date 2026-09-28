package dev.cbcmuzzlelight.client;
import java.util.Set;
import com.yuushya.modelling.client.anvilcraft.rendering.CacheableBERenderingPipeline;
/** Only called when Yuushya is installed. Never creates empty cache regions. */
public final class YuushyaLighting {
 public interface Pipeline {void muzzle$invalidate(Set<Long> sections);}
 public interface Region {void muzzle$invalidate(Set<Long> sections);void muzzle$finished();}
 public static void refresh(Set<Long> dirty){var pipeline=CacheableBERenderingPipeline.getInstance();if(pipeline instanceof Pipeline bridge)bridge.muzzle$invalidate(dirty);}
}
