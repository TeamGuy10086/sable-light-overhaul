package dev.cbcmuzzlelight.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4d;
import java.util.*;

/** Receiver-space area proxies. No block emission and no plot chunk creation. */
final class AdvancedSable {
    static void project(ClientLevel level,Map<String,AreaSnapshot> world,Map<String,AreaSnapshot> result) {
        var container=SubLevelContainer.getContainer(level);
        if(container==null) return;
        int remaining=MuzzleClientConfig.ADV_RECEIVERS.get();
        int extra=ExtraConfig.RECEIVERS.get();
        for(var entry:world.entrySet()) {
            boolean muzzle=entry.getKey().startsWith("shot/");
            if((muzzle?remaining:extra)<=0) continue;
            var snapshot=entry.getValue();
            var ships=new ArrayList<dev.ryanhcode.sable.sublevel.SubLevel>();
            container.queryIntersecting(new BoundingBox3d(snapshot.bounds)).forEach(ships::add);
            ships.sort(Comparator.comparing(s -> s.getUniqueId()));
            for(var ship:ships) {
                if(ship.isRemoved() || ship.getPlot().getLoadedChunks().isEmpty()) continue;
                var matrix=ship.logicalPose().bakeIntoMatrix(new Matrix4d());
                if(!matrix.isFinite() || Math.abs(matrix.determinant())<1e-12) continue;
                var b=AreaSnapshot.transform(snapshot.bounds,new Matrix4d(matrix).invert());
                var plot=ship.getPlot().getBoundingBox();
                var receiver=new AABB(plot.minX()-1.0,plot.minY()-1.0,plot.minZ()-1.0,
                        plot.maxX()+2.0,plot.maxY()+2.0,plot.maxZ()+2.0);
                if(!b.intersects(receiver)) continue;
                b=b.intersect(receiver);
                if(!Double.isFinite(b.minX+b.minY+b.minZ+b.maxX+b.maxY+b.maxZ)
                        || b.maxX-b.minX>256 || b.maxY-b.minY>256 || b.maxZ-b.minZ>256) continue;
                // The projection is a copy of the same light seen from the ship's side, so it keeps every
                // flag the original carried: dropping them here would quietly give a taken-over lamp its
                // white wash back and turn a lamp's own shadow answer into the default.
                result.put(entry.getKey()+"/"+ship.getUniqueId(),new AreaSnapshot(snapshot.light,b,matrix,
                        snapshot.color,snapshot.selfCull,snapshot.shadowMode,snapshot.shadowSteps));
                if(muzzle){if(--remaining<=0)break;}else if(--extra<=0)break;
            }
        }
    }
}
