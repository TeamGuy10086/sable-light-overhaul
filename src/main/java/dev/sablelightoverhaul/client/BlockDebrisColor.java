package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.ParticleColorAverage;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

/** Client resource-pack color, following TerrainParticle's texture/model-data/tint choice. */
public final class BlockDebrisColor {
    private static final Map<TextureAtlasSprite,Integer> colors=new IdentityHashMap<>();
    private BlockDebrisColor(){}
    public static synchronized void reload(TextureAtlasStitchedEvent event){colors.clear();}
    static int resolve(ClientLevel level,int stateId,long sourcePos,int fallback){
        if(stateId<0)return fallback;
        var state=Block.stateById(stateId);var pos=BlockPos.of(sourcePos);
        if(state.isAir()||!level.hasChunkAt(pos))return fallback;
        try{
            var mc=Minecraft.getInstance();var models=mc.getBlockRenderer().getBlockModelShaper();
            // Model data only belongs to the original material, not a newly placed block at this position.
            var sprite=level.getBlockState(pos).equals(state)?models.getTexture(state,level,pos):models.getParticleIcon(state);
            int rgb=average(sprite);if(rgb<0)return fallback;
            int tint=IClientBlockExtensions.of(state).areBreakingParticlesTinted(state,level,pos)
                ?mc.getBlockColors().getColor(state,level,pos,0):0xFFFFFF;
            return ParticleColorAverage.tint(rgb,tint,.6); // TerrainParticle's own fixed vertex shade.
        }catch(RuntimeException unavailable){
            // Resource reload or a custom model may not provide readable sprite pixels.
            return fallback;
        }
    }
    private static synchronized int average(TextureAtlasSprite sprite){
        if(sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))return -1;
        Integer cached=colors.get(sprite);if(cached!=null)return cached;
        if(colors.size()>=256)colors.clear();
        var contents=sprite.contents();var mean=new ParticleColorAverage();
        int w=contents.width(),h=contents.height(),nx=Math.min(16,w),ny=Math.min(16,h);
        int[] frames=contents.getUniqueFrames().limit(4).toArray();
        for(int frame:frames)for(int y=0;y<ny;y++)for(int x=0;x<nx;x++){
            int px=Math.min(w-1,(int)((x+.5)*w/nx)),py=Math.min(h-1,(int)((y+.5)*h/ny));
            mean.add(sprite.getPixelRGBA(frame,px,py));
        }
        int result=mean.color();colors.put(sprite,result);return result;
    }
}
