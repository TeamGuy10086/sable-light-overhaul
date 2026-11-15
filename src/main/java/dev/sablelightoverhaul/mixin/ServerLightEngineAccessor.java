package dev.sablelightoverhaul.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Same accessor as the client one, in a config that also applies on the server: the server's light engine has to
 * be able to ask which level it belongs to before it can know whether a position is one of this mod's lamps.
 */
@Mixin(LightEngine.class)
public interface ServerLightEngineAccessor {
    @Accessor("chunkSource") LightChunkGetter slo$serverChunks();
}
