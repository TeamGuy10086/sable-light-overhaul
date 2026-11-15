package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One firing behaviour, not one packet per particle. Surface positions are world-space snapshots.
 *
 * <p>The haze's motion travels with the shot rather than being read from the client's own configuration.
 * Haze is simulated on the client, but how it moves is behaviour, and behaviour belongs to the server: the
 * client keeps budgets and switches and nothing else. The four values the particle needs while it lives --
 * drift, sink, clearance and the light gain -- are copied into the particle itself when it is born, because
 * the particle outlives this packet.
 */
public record CannonDustPayload(ResourceLocation dimension, Vec3 muzzle, Vec3 axis, long seed,
        List<Surface> surfaces, int burstCount, int hazeCount, float burstSpeed,
        GroundDustParticleData burst, GroundDustParticleData haze,
        float hazeSpread, float hazeDrift, float hazeSink, float hazeClearance,
        float hazePush, float hazePushRadius, float hazeLightGain, int emitterId,HullDustModel.Settings hullFlow) implements CustomPacketPayload {
    public CannonDustPayload(ResourceLocation dimension,Vec3 muzzle,Vec3 axis,long seed,List<Surface> surfaces,int burstCount,int hazeCount,float burstSpeed,GroundDustParticleData burst,GroundDustParticleData haze,float hazeSpread,float hazeDrift,float hazeSink,float hazeClearance,float hazePush,float hazePushRadius,float hazeLightGain,int emitterId){
        this(dimension,muzzle,axis,seed,surfaces,burstCount,hazeCount,burstSpeed,burst,haze,hazeSpread,hazeDrift,hazeSink,hazeClearance,hazePush,hazePushRadius,hazeLightGain,emitterId,null);
    }
    /** Source compatibility for callers without a moving emitter; the wire uses the current mod protocol. */
    public CannonDustPayload(ResourceLocation dimension, Vec3 muzzle, Vec3 axis, long seed,
            List<Surface> surfaces, int burstCount, int hazeCount, float burstSpeed,
            GroundDustParticleData burst, GroundDustParticleData haze, float hazeSpread, float hazeDrift,
            float hazeSink, float hazeClearance, float hazePush, float hazePushRadius, float hazeLightGain) {
        this(dimension, muzzle, axis, seed, surfaces, burstCount, hazeCount, burstSpeed, burst, haze,
                hazeSpread, hazeDrift, hazeSink, hazeClearance, hazePush, hazePushRadius, hazeLightGain, 0);
    }
    public static final int MAX_SURFACES = 64;
    public record Surface(Vec3 position,Vec3 normal,java.util.UUID body,Vec3 local,Vec3 localNormal){
        public Surface(Vec3 position,Vec3 normal){this(position,normal,null,Vec3.ZERO,Vec3.ZERO);}
        public Surface{
            if(!finite(position,6e7)||!finite(normal,2)||(normal.lengthSqr()<.8||normal.lengthSqr()>1.2))throw new IllegalArgumentException("Invalid hull surface");
            if(body!=null&&(!finite(local,2e9)||!finite(localNormal,2)||(localNormal.lengthSqr()<.8||localNormal.lengthSqr()>1.2)))throw new IllegalArgumentException("Invalid hull anchor");
        }
        private static boolean finite(Vec3 p,double limit){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.max(Math.abs(p.x),Math.max(Math.abs(p.y),Math.abs(p.z)))<=limit;}
    }
    public CannonDustPayload {
        surfaces = List.copyOf(surfaces);
        if (surfaces.size() > MAX_SURFACES || burstCount < 0 || burstCount > 200 || hazeCount < 0 || hazeCount > 24)
            throw new IllegalArgumentException("Invalid cannon dust event bounds");
    }
    public static final Type<CannonDustPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "cannon_dust"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CannonDustPayload> CODEC = StreamCodec.of((b, v) -> {
        b.writeResourceLocation(v.dimension); vector(b, v.muzzle); vector(b, v.axis); b.writeLong(v.seed);
        b.writeVarInt(v.surfaces.size());
        for(var s:v.surfaces){vector(b,s.position);vector(b,s.normal);b.writeBoolean(s.body!=null);if(s.body!=null){b.writeUUID(s.body);vector(b,s.local);vector(b,s.localNormal);}}
        b.writeVarInt(v.burstCount); b.writeVarInt(v.hazeCount); b.writeFloat(v.burstSpeed);
        GroundDustParticleData.STREAM_CODEC.encode(b, v.burst); GroundDustParticleData.STREAM_CODEC.encode(b, v.haze);
        b.writeFloat(v.hazeSpread); b.writeFloat(v.hazeDrift); b.writeFloat(v.hazeSink);
        b.writeFloat(v.hazeClearance); b.writeFloat(v.hazePush); b.writeFloat(v.hazePushRadius);
        b.writeFloat(v.hazeLightGain);
        b.writeVarInt(v.emitterId);
        b.writeBoolean(v.hullFlow!=null);if(v.hullFlow!=null){b.writeDouble(v.hullFlow.lift());b.writeDouble(v.hullFlow.turbulence());b.writeDouble(v.hullFlow.releaseSeconds());b.writeDouble(v.hullFlow.motionScale());b.writeDouble(v.hullFlow.attachSeconds());b.writeDouble(v.hullFlow.surfaceSpeed());b.writeDouble(v.hullFlow.clearance());b.writeDouble(v.hullFlow.inheritMotion());b.writeDouble(v.hullFlow.density());b.writeDouble(v.hullFlow.spread());b.writeDouble(v.hullFlow.stagger());b.writeDouble(v.hullFlow.detachFadeSeconds());b.writeDouble(v.hullFlow.detachExpansion());}
    }, b -> {
        var dimension = b.readResourceLocation(); var muzzle = vector(b); var axis = vector(b); long seed = b.readLong();
        int count = b.readVarInt();
        if (count < 0 || count > MAX_SURFACES) throw new IllegalArgumentException("Too many dust surfaces");
        var surfaces = new ArrayList<Surface>(count);
        for(int i=0;i<count;i++){var world=vector(b);var normal=vector(b);surfaces.add(b.readBoolean()?new Surface(world,normal,b.readUUID(),vector(b),vector(b)):new Surface(world,normal));}
        int burstCount = b.readVarInt(); int hazeCount = b.readVarInt(); float burstSpeed = b.readFloat();
        var burst = GroundDustParticleData.STREAM_CODEC.decode(b);
        var haze = GroundDustParticleData.STREAM_CODEC.decode(b);
        // Read in the order they were written: the data blocks are variable length, so the seven floats
        // cannot be read before them even though they come last in the record.
        float hazeSpread = b.readFloat(); float hazeDrift = b.readFloat(); float hazeSink = b.readFloat();
        float hazeClearance = b.readFloat(); float hazePush = b.readFloat(); float hazePushRadius = b.readFloat();
        float hazeLightGain = b.readFloat();
        int emitter=b.readVarInt();
        var flow=b.readBoolean()?new HullDustModel.Settings(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()):null;
        return new CannonDustPayload(dimension, muzzle, axis, seed, surfaces, burstCount, hazeCount, burstSpeed,
            burst, haze, hazeSpread, hazeDrift, hazeSink, hazeClearance, hazePush, hazePushRadius, hazeLightGain, emitter,flow);
    });
    private static void vector(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vector(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @Override public Type<CannonDustPayload> type() { return TYPE; }
}
