package dev.sablelightoverhaul.lighting;

import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import net.minecraft.world.level.ClipContext;

/** Isolated optional linkage: the world-space dust sampler never enters a vessel's plot. */
final class SableGroundClip {
    private SableGroundClip(){}
    static boolean terrainOnly(ClipContext context){
        if(context instanceof ClipContextExtension extension){extension.sable$setSubLevelIgnoring(body->true);return true;}
        return false;
    }
}
