package dev.cbcmuzzlelight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import rbasamoyai.createbigcannons.CBCCompatTransformers;

public final class ServerFlashes {
    private ServerFlashes() {}
    public static void emitWorldAutocannon(Level level,Vec3 plume,Vec3 direction) {
        emitTransformed(level, plume, plume, direction.normalize(), 0.1, 1);
    }
    public static void emitTransformed(Level level, Vec3 plume, Vec3 root, Vec3 direction, double offset, int kind) {
        Vec3 point = plume.add(direction.scale(offset));
        Vec3 pos = CBCCompatTransformers.transformVec3(level, point, root);
        Vec3 axis = CBCCompatTransformers.transformVec3(level, point.add(direction), root).subtract(pos).normalize();
        emit(level, pos, axis, kind);
    }
    public static void emitGrouped(Level level, Vec3 plume, Vec3 root, Vec3 direction, double offset, int kind,
                                   int emitterId, Vec3 lightCenter) {
        Vec3 point = plume.add(direction.scale(offset));
        Vec3 pos = CBCCompatTransformers.transformVec3(level, point, root);
        Vec3 axis = CBCCompatTransformers.transformVec3(level, point.add(direction), root).subtract(pos).normalize();
        Vec3 center = CBCCompatTransformers.transformVec3(level, lightCenter.add(direction.scale(offset)), root);
        emit(level,pos,axis,kind,emitterId,center);
    }
    private static void emit(Level level, Vec3 pos, Vec3 axis, int kind) {
        emit(level,pos,axis,kind,0,pos);
    }
    private static void emit(Level level, Vec3 pos, Vec3 axis, int kind,int emitterId,Vec3 lightCenter) {
        emitSized(level,pos,axis,kind,emitterId,lightCenter,4,4);
    }
    public static void emitSized(Level level,Vec3 pos,Vec3 axis,int kind,int emitterId,Vec3 lightCenter,float charge,float plumeSize) {
        if (!(level instanceof ServerLevel server)) return;
        if (!FlashStore.valid(pos.x, pos.y, pos.z, axis.x, axis.y, axis.z, kind)) return;
        FlashPayload payload = new FlashPayload(server.dimension().location(), pos.x, pos.y, pos.z,
            (float)axis.x, (float)axis.y, (float)axis.z, kind, emitterId, lightCenter.x, lightCenter.y, lightCenter.z, charge, plumeSize);
        // Includes the gunner; independent of CBC's per-player plume suppression.
        for (var player : server.players()) {
            if (player.distanceToSqr(pos) <= 512.0 * 512.0) PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
