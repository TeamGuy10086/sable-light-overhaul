package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.GroundDustFieldModel;
import dev.sablelightoverhaul.GroundDustFieldPayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Fixed world fields: bounded node count, eight terrain validity checks per tick, no new shaders. */
public final class GroundDustFieldClient {
    private static final List<Field> fields=new ArrayList<>();
    private static ClientLevel world;
    private static SpriteSet sprites;
    private static int epoch,validationCursor;
    private GroundDustFieldClient(){}
    static void sprites(SpriteSet value){sprites=value;}
    private static void clear(){for(var f:fields)f.clear();fields.clear();validationCursor=0;}
    private static void checkWorld(ClientLevel level){
        if(world!=level||epoch!=ClientFlashes.visualEpoch()){clear();world=level;epoch=ClientFlashes.visualEpoch();}
    }
    public static void receive(GroundDustFieldPayload p){
        var level=Minecraft.getInstance().level;
        if(level==null||!level.dimension().location().equals(p.dimension())||p.patches().isEmpty())return;
        if(!ClientFlashes.prepareEffects(level))return;
        checkWorld(level);long now=level.getGameTime();
        if(p.firedAt()<now-100||p.firedAt()>now+20||fields.size()>=MuzzleClientConfig.GROUND_FIELD_LIMIT.get())return;
        for(var f:fields)if(f.event.seed()==p.seed()&&f.event.firedAt()==p.firedAt())return;
        var field=new Field(p);
        // Transport delay advances the front instead of replaying a stale shot from the beginning.
        for(long t=p.firedAt();t<now;t++)field.model.tick();
        if(!field.model.finished())fields.add(field);
    }
    public static void tick(ClientTickEvent.Post ignored){
        var mc=Minecraft.getInstance();var level=mc.level;checkWorld(level);
        if(level==null||mc.isPaused())return;
        if(!ClientFlashes.effectsEnabled()||MuzzleClientConfig.GROUND_FIELD_LIMIT.get()==0){clear();return;}
        while(fields.size()>MuzzleClientConfig.GROUND_FIELD_LIMIT.get())fields.remove(fields.size()-1).clear();
        // Round-robin all fields, so later fields do not starve when the first is large.
        int total=fields.stream().mapToInt(f->f.model.nodes().size()).sum();
        for(int i=0;i<Math.min(8,total);i++){
            int index=(validationCursor++ & Integer.MAX_VALUE)%total;
            for(var f:fields){int count=f.model.nodes().size();if(index<count){f.validate(level,index);break;}index-=count;}
        }
        for(var it=fields.iterator();it.hasNext();){
            var f=it.next();f.model.tick(ParticleWind.step());
            if(f.model.finished()){f.clear();it.remove();}else f.update(level);
        }
    }
    private static final class Field {
        final GroundDustFieldPayload event;
        final GroundDustFieldModel model;
        final GroundDustFieldParticle[] particles;
        final boolean[] invalid;
        Field(GroundDustFieldPayload p){
            event=p;model=new GroundDustFieldModel(p.origin(),p.axis(),p.patches(),p.settings(),p.seed());
            particles=new GroundDustFieldParticle[model.nodes().size()];invalid=new boolean[particles.length];
        }
        void validate(ClientLevel level,int index){
            if(invalid[index])return;
            var at=event.patches().get(model.nodes().get(index).patchIndex).position();var pos=BlockPos.containing(at.x(),at.y()-.01,at.z());
            if(!level.hasChunkAt(pos)||level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                    ||!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.above()).isEmpty()){
                invalid[index]=true;model.invalidate(index);
            }
        }
        void update(ClientLevel level){
            for(var node:model.nodes()){
                int i=node.index;
                if(node.finished()){if(particles[i]!=null)particles[i].remove();continue;}
                if(particles[i]==null&&sprites!=null&&!invalid[i]&&(node.alpha(1)>0||MuzzleClientConfig.GROUND_FIELD_DEBUG.get())){
                    particles[i]=new GroundDustFieldParticle(level,node,sprites);
                    Minecraft.getInstance().particleEngine.add(particles[i]);
                }
                if(particles[i]!=null)particles[i].updatePosition();
            }
        }
        void clear(){for(var p:particles)if(p!=null)p.remove();}
    }
}
