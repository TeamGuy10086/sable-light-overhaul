package dev.sablelightoverhaul;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
        emitSized(level,pos,axis,kind,emitterId,lightCenter,charge,plumeSize,null);
    }
    /**
     * The same, with the firing cannon's form attached.
     *
     * <p>The form rides on the flash because which cannon fired is only known here, on the server: a client
     * cannot map a flash back to a cannon even in principle, so it cannot look the same form up. Null means
     * the shot has no form and the client keeps its own configuration, which is every shot that has not been
     * configured.
     */
    public static void emitSized(Level level,Vec3 pos,Vec3 axis,int kind,int emitterId,Vec3 lightCenter,float charge,float plumeSize,float[] profile) {
            emitSized(level,pos,axis,kind,emitterId,lightCenter,charge,plumeSize,profile,Long.MIN_VALUE);
        }
        /**
         * The same, naming the mount that fired.
         *
         * <p>The mount's position packed as one long, or {@link Long#MIN_VALUE} for a shot no mount fired. A mount
         * is a definite coordinate whether it stands in the world or inside a body, so one number names it, and the
         * client keeps the table that turns it into a cannon -- which is what lets a form be per-cannon again.
         */
        public static void emitSized(Level level,Vec3 pos,Vec3 axis,int kind,int emitterId,Vec3 lightCenter,float charge,float plumeSize,float[] profile,long mountPos) {
        emitSized(level,pos,axis,kind,emitterId,lightCenter,charge,plumeSize,profile,mountPos,true);
    }
    public static void emitSized(Level level,Vec3 pos,Vec3 axis,int kind,int emitterId,Vec3 lightCenter,float charge,float plumeSize,float[] profile,long mountPos,boolean emitAutocannonHaze) {
        emitSized(level,pos,axis,kind,emitterId,lightCenter,charge,plumeSize,profile,mountPos,emitAutocannonHaze,false);
    }
    public static void emitSized(Level level,Vec3 pos,Vec3 axis,int kind,int emitterId,Vec3 lightCenter,float charge,float plumeSize,
                                 float[] profile,long mountPos,boolean emitAutocannonHaze,boolean halfSizeFireball) {
        emitSized(level,pos,axis,kind,emitterId,lightCenter,charge,plumeSize,profile,mountPos,emitAutocannonHaze,
            halfSizeFireball,false);
    }

    public static void emitSized(Level level,Vec3 pos,Vec3 axis,int kind,int emitterId,Vec3 lightCenter,float charge,float plumeSize,
                                 float[] profile,long mountPos,boolean emitAutocannonHaze,boolean halfSizeFireball,
                                 boolean groupedMuzzle) {
        if (!(level instanceof ServerLevel server)) return;
        if (!FlashStore.valid(pos.x, pos.y, pos.z, axis.x, axis.y, axis.z, kind)) return;
        // Thin autocannon haze has its own gate and material. MediumCannonEffects sends its own dust event.
        if (emitAutocannonHaze && (kind == 1 || kind == 3) && dev.sablelightoverhaul.lighting.LightingCommon.HAZE_ENABLED.get()
                && dev.sablelightoverhaul.lighting.LightingCommon.AUTO_HAZE_ENABLED.get())
            CannonDustEvents.emitAutocannon(server,pos,axis,emitterId);
        boolean hasMount = mountPos != Long.MIN_VALUE;
        long mountSlot = mountPos;
        if (!hasMount) {
            if (groupedMuzzle) mountSlot = FlashPayload.GROUPED_MUZZLE;
            else if (halfSizeFireball) mountSlot = FlashPayload.HALF_SIZE_FIREBALL;
        }
        FlashPayload payload = new FlashPayload(server.dimension().location(), pos.x, pos.y, pos.z,
            (float)axis.x, (float)axis.y, (float)axis.z, kind, emitterId, lightCenter.x, lightCenter.y, lightCenter.z,
            charge, plumeSize, profile, hasMount, mountSlot);
        // Includes the gunner; independent of CBC's per-player plume suppression.
        for (var player : server.players()) {
            if (player.distanceToSqr(pos) <= 512.0 * 512.0) SloNetwork.sendToPlayer(player, payload);
        }
    }
}
