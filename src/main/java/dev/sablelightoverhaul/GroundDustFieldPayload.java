package dev.sablelightoverhaul;

import dev.sablelightoverhaul.AfterSmokeModel.Point;
import dev.sablelightoverhaul.GroundDustFieldModel.*;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One authoritative terrain snapshot per shot; no individual particle packets. */
public record GroundDustFieldPayload(ResourceLocation dimension,Point origin,Point axis,long firedAt,long seed,
        Settings settings,List<Patch> patches) implements CustomPacketPayload {
    public GroundDustFieldPayload {
        Objects.requireNonNull(dimension);Objects.requireNonNull(settings);patches=List.copyOf(patches);
        if(origin==null||!origin.finite()||origin.length()>6e7||axis==null||!axis.finite()
                ||axis.length()<.9||axis.length()>1.1||patches.size()>GroundDustFieldModel.MAX_PATCHES)
            throw new IllegalArgumentException("Invalid ground field event");
        for(var patch:patches){
            Point d=patch.position().subtract(origin);
            if(Math.hypot(d.x(),d.z())>settings.radius()+.1||d.y()>1||d.y() < -129)
                throw new IllegalArgumentException("Dust patch outside sampling bounds");
            if(patch.materialState()>=0){
                var pos=net.minecraft.core.BlockPos.of(patch.materialPos());
                Point at=patch.position();
                if(Math.abs(at.x()-pos.getX()-.5)>1.5||Math.abs(at.z()-pos.getZ()-.5)>1.5||Math.abs(at.y()-pos.getY())>4)
                    throw new IllegalArgumentException("Material source outside patch");
            }
        }
    }
    public static final Type<GroundDustFieldPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID,"ground_dust_field"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GroundDustFieldPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeResourceLocation(p.dimension);point(b,p.origin);point(b,p.axis);b.writeLong(p.firedAt);b.writeLong(p.seed);
        Settings s=p.settings;
        for(double v:new double[]{s.radius(),s.spacing(),s.lifetime(),s.spreadSeconds(),s.speed(),s.lift(),s.turbulence(),s.opacity(),s.sizeScale(),s.maxStep(),s.density(),s.corridorWidth(),s.outerLift(),s.motionScale(),s.heightScale()})b.writeDouble(v);
        b.writeVarInt(p.patches.size());for(var tile:p.patches){
            point(b,tile.position());point(b,tile.normal());b.writeInt(tile.color());
            b.writeVarInt(tile.materialState()+1);if(tile.materialState()>=0)b.writeLong(tile.materialPos());
        }
    },b->{
        var dimension=b.readResourceLocation();Point origin=point(b),axis=point(b);long time=b.readLong(),seed=b.readLong();
        var settings=new Settings(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble());
        int count=b.readVarInt();if(count<0||count>GroundDustFieldModel.MAX_PATCHES)throw new IllegalArgumentException("Invalid patch count");
        var patches=new ArrayList<Patch>();for(int i=0;i<count;i++){
            Point at=point(b),normal=point(b);int color=b.readInt(),tag=b.readVarInt();
            if(tag<0||tag>0x1000000)throw new IllegalArgumentException("Invalid material state");
            patches.add(new Patch(at,normal,color,tag-1,tag==0?0:b.readLong()));
        }
        return new GroundDustFieldPayload(dimension,origin,axis,time,seed,settings,patches);
    });
    private static void point(RegistryFriendlyByteBuf b,Point p){b.writeDouble(p.x());b.writeDouble(p.y());b.writeDouble(p.z());}
    private static Point point(RegistryFriendlyByteBuf b){return new Point(b.readDouble(),b.readDouble(),b.readDouble());}
    @Override public Type<GroundDustFieldPayload> type(){return TYPE;}
}
