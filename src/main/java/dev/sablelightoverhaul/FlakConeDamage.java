package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.LightingCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import rbasamoyai.createbigcannons.CBCCompatTransformers;
import rbasamoyai.createbigcannons.index.CBCDamageTypes;
import rbasamoyai.createbigcannons.index.CBCEntityTypes;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.CannonDamageSource;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;

/** One server query per detonation, no subprojectile entities, tracking packets or terrain-fragment hits. */
public final class FlakConeDamage {
    private FlakConeDamage(){}
    public static void apply(Entity shell,Vec3 localOrigin,Vec3 velocity,int count,double spread){
        if(shell.level().isClientSide||count<=0||!Double.isFinite(spread)||velocity.lengthSqr()<1e-10)return;
        var server=shell.getServer();if(server==null)return;
        ServerLevel world=server.getLevel(shell.level().dimension());if(world==null)return;
        Vec3 origin=CBCCompatTransformers.transformVec3(shell.level(),localOrigin);
        // Transform both points against the same local root, including rotated physics bodies.
        Vec3 axis=CBCCompatTransformers.transformVec3(shell.level(),localOrigin.add(velocity),localOrigin).subtract(origin).normalize();
        if(axis.lengthSqr()<.5)return;
        double range=LightingCommon.FLAK_CONE_RANGE.get(),tangent=Math.clamp(Math.abs(spread),0,4);
        var properties=CBCMunitionPropertiesHandlers.PROJECTILE_BURST.getPropertiesOf(CBCEntityTypes.FLAK_BURST.get()).damage();
        float damage=(float)(properties.entityDamage()*LightingCommon.FLAK_CONE_DAMAGE.get());
        if(damage<=0)return;
        var source=new CannonDamageSource(CannonDamageSource.getDamageRegistry(world).getHolderOrThrow(CBCDamageTypes.FLAK),properties.ignoresEntityArmor());
        Vec3 end=origin.add(axis.scale(range));
        AABB bounds=new AABB(origin,end).inflate(range*tangent);
        for(Entity target:world.getEntities(shell,bounds,e->e.isAlive()&&e.isPickable()&&!e.isSpectator()&&!(e instanceof AbstractCannonProjectile))){
            AABB box=target.getBoundingBox();Vec3 centre=box.getCenter(),offset=centre.subtract(origin);
            double radius=Math.sqrt(box.getXsize()*box.getXsize()+box.getYsize()*box.getYsize()+box.getZsize()*box.getZsize())*.5;
            if(!FlakConeMath.intersects(offset.x,offset.y,offset.z,axis.x,axis.y,axis.z,range,tangent,radius))continue;
            // Probe the closest box point to the cone axis, centre and corners; the sphere above is broad-phase only.
            Vec3 onAxis=origin.add(axis.scale(Math.clamp(offset.dot(axis),0,range)));
            Vec3 closest=new Vec3(Math.clamp(onAxis.x,box.minX,box.maxX),Math.clamp(onAxis.y,box.minY,box.maxY),Math.clamp(onAxis.z,box.minZ,box.maxZ));
            Vec3 visible=null;
            for(int i=0;i<10;i++){
                Vec3 p=i==0?closest:i==1?centre:new Vec3(((i-2)&1)==0?box.minX:box.maxX,((i-2)&2)==0?box.minY:box.maxY,((i-2)&4)==0?box.minZ:box.maxZ);
                Vec3 d=p.subtract(origin);
                if(!FlakConeMath.intersects(d.x,d.y,d.z,axis.x,axis.y,axis.z,range,tangent,0.001))continue;
                if(!loaded(world,origin,p))continue;
                var hit=world.clip(new ClipContext(origin,p,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shell));
                if(hit.getType()==HitResult.Type.MISS||hit.getLocation().distanceToSqr(origin)+1e-6>=p.distanceToSqr(origin)){visible=p;break;}
            }
            if(visible==null)continue;
            if(properties.ignoresInvulnerability())target.invulnerableTime=0;
            target.hurt(source,(float)(damage*FlakConeMath.falloff(Math.clamp(visible.subtract(origin).dot(axis),0,range),range)));
            if(!properties.rendersInvulnerable())target.invulnerableTime=0;
        }
    }
    private static boolean loaded(ServerLevel level,Vec3 a,Vec3 b){
        int minX=BlockPos.containing(Math.min(a.x,b.x),0,0).getX()>>4,maxX=BlockPos.containing(Math.max(a.x,b.x),0,0).getX()>>4;
        int minZ=BlockPos.containing(0,0,Math.min(a.z,b.z)).getZ()>>4,maxZ=BlockPos.containing(0,0,Math.max(a.z,b.z)).getZ()>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(!level.hasChunk(x,z))return false;
        return true;
    }
}
