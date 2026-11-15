package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.AfterSmokeModel;
import dev.sablelightoverhaul.AfterSmokeModel.Node;
import dev.sablelightoverhaul.TankAfterSmokePayload;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Independent residual smoke controller. At most eight 64-node emitters, no per-frame object rebuild. */
public final class TankAfterSmokeClient {
    private static final Map<Key,Emitter> emitters=new LinkedHashMap<>();
    private static ClientLevel world;
    private static SpriteSet sprites;
    private record Key(UUID owner,long x,long y,long z){
        Key(TankAfterSmokePayload p){this(p.entityUuid(),Math.round(p.mouth().x*256),Math.round(p.mouth().y*256),Math.round(p.mouth().z*256));}
    }
    private TankAfterSmokeClient(){}
    static void sprites(SpriteSet value){sprites=value;}
    public static void receive(TankAfterSmokePayload p){
        var level=Minecraft.getInstance().level;
        if(level==null||!ModList.get().isLoaded("createbigcannons")||!level.dimension().location().equals(p.dimension()))return;
        checkWorld(level);
        if(!MuzzleClientConfig.AFTER_SMOKE_ENABLED.get()||!ClientFlashes.prepareEffects(level))return;
        if(!finite(p.mouth())||!finite(p.axis())||p.axis().lengthSqr()<.5||p.axis().lengthSqr()>2
                ||p.mouth().lengthSqr()>4096*4096||Math.abs(level.getGameTime()-p.firedAt())>40)return;
        Key key=new Key(p);
        Emitter existing=emitters.get(key);
        if(existing!=null){if(p.firedAt()>existing.firedAt){existing.model.restart();existing.firedAt=p.firedAt();}return;}
        if(emitters.size()>=8)return;
        emitters.put(key,new Emitter(level,p));
    }
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static void checkWorld(ClientLevel level){if(world!=level){for(var e:emitters.values())e.clear();emitters.clear();world=level;}}
    public static void tick(ClientTickEvent.Post ignored){
        var mc=Minecraft.getInstance();var level=mc.level;checkWorld(level);
        if(level==null||mc.isPaused())return;
        if(!MuzzleClientConfig.AFTER_SMOKE_ENABLED.get()||!ClientFlashes.effectsEnabled()){
            for(var e:emitters.values())e.clear();emitters.clear();return;
        }
        for(var it=emitters.values().iterator();it.hasNext();){
            var e=it.next();e.tick();if(e.model.finished()){e.clear();it.remove();}
        }
    }
    static Vec3 vec(AfterSmokeModel.Point p){return new Vec3(p.x(),p.y(),p.z());}
    static final class Emitter {
        final ClientLevel level;
        final TankSmokeAnchor anchor;
        final AfterSmokeModel model;
        final Map<Long,TankAfterSmokeParticle> puffs=new HashMap<>();
        long firedAt;
        Emitter(ClientLevel level,TankAfterSmokePayload p){
            this.level=level;anchor=new TankSmokeAnchor(p);firedAt=p.firedAt();
            model=new AfterSmokeModel(MuzzleClientConfig.afterSmokeSettings(),p.seed());
        }
        void tick(){
            model.tick(anchor.pose(level,1),fraction->anchor.pose(level,(float)fraction),ParticleWind.step());
            var nodes=model.nodes();Set<Long> live=new HashSet<>();
            for(Node node:nodes){
                live.add(node.id);
                var particle=puffs.get(node.id);
                if(particle==null&&sprites!=null){
                    particle=new TankAfterSmokeParticle(level,node,sprites);puffs.put(node.id,particle);
                    Minecraft.getInstance().particleEngine.add(particle);
                }
                if(particle!=null)particle.updatePosition();
            }
            puffs.entrySet().removeIf(entry->{if(live.contains(entry.getKey()))return false;entry.getValue().remove();return true;});
        }
        void clear(){for(var p:puffs.values())p.remove();puffs.clear();}
    }
}
