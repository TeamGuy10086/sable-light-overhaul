package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.LightingCommon;
import dev.sablelightoverhaul.lighting.SourceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.CBCCompatTransformers;

/** Optional CBC entry, called only from the existing CBCMW firing hook. Independent of flash lighting. */
public final class TankAfterSmokeEvents {
    private TankAfterSmokeEvents(){}
    public static void fire(ServerLevel level,PitchOrientedContraptionEntity owner,Vec3 mouth,Vec3 axis){
        if(!LightingCommon.CANNON_ENABLED.get())return;
        Vec3 local=owner.toLocalVector(mouth,0);
        Vec3 localAxis=owner.toLocalVector(mouth.add(axis),0).subtract(local).normalize();
        Vec3 world=CBCCompatTransformers.transformVec3(level,mouth,owner.position());
        if(world==null||localAxis.lengthSqr()<.5)return;
        var body=SourceAnchor.capture(level,BlockPos.containing(owner.position())).ship();
        var payload=new TankAfterSmokePayload(level.dimension().location(),owner.getId(),owner.getUUID(),body,
                local,localAxis,level.getGameTime(),level.random.nextLong());
        double range=LightingCommon.CANNON_CULL.get();
        for(var player:level.players())if(player.position().distanceToSqr(world)<=range*range)
            SloNetwork.sendToPlayer(player,payload);
    }
}
