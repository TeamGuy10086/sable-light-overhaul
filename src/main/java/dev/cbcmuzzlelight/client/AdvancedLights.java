package dev.cbcmuzzlelight.client;

import dev.cbcmuzzlelight.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class AdvancedLights {
    private static boolean failed;
    private static long lastLog=Long.MIN_VALUE;
    private static Map<String,AreaSnapshot> previous=Map.of();
    private static volatile Map<Long,List<AreaSnapshot>> lookup=Map.of();
    private AdvancedLights() {}
    public static boolean ready() {
        if(failed || !ModList.get().isLoaded("lambdynlights_runtime")) return false;
        try { return LambAreaBackend.ready(); }
        catch(RuntimeException | LinkageError ex) { fail(ex); return false; }
    }
    private static void fail(Throwable ex) {
        failed=true;
        com.mojang.logging.LogUtils.getLogger().error("CBC Muzzle Light: Advanced backend unavailable, falling back to Legacy",ex);
    }
    public static boolean update(ClientLevel level) {
        try {
            long now=level.getGameTime();
            Map<String,AreaSnapshot> world=computeWorld(level,now,true);
            Map<String,AreaSnapshot> next=new LinkedHashMap<>(world);
            if(ModList.get().isLoaded("sable")) AdvancedSable.project(level,world,next);
            next.keySet().removeIf(k -> k.startsWith("staticGround/") && k.chars().filter(ch -> ch=='/').count()==1);
            publish(level,next);
            LambAreaBackend.sync(next);
            if(MuzzleClientConfig.ADV_DEBUG.get() && !world.isEmpty() && (lastLog==Long.MIN_VALUE || now-lastLog>=20)) {
                com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light Advanced: worldAreas={}, receiverAreas={}, indexedSections={}, staleBuildsRejected={}, oldBuildsSkipped={}",world.size(),next.size()-world.size(),lookup.size(),BuildOrder.REJECTED.sum(),BuildOrder.SKIPPED.sum());
                lastLog=now;
            }
            return true;
        } catch(RuntimeException | LinkageError ex) {
            fail(ex); clear(level); return false;
        }
    }
    /** GPU mode: publish lights to the shader buffer instead of marking any section dirty. */
    public static boolean gpuUpdate(ClientLevel level) {
        try {
            long now=level.getGameTime();
            Map<String,AreaSnapshot> world=computeWorld(level,now,true);
            var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            // The two backends take different numbers of lights, so the list has to be built for the one
            // that is actually consuming it: handing the pack a list built for the overlay would leave
            // lights it never receives, and the lights at the end of the order are the ones dropped.
            int total=ClientFlashes.inPackLighting()?IrisMuzzleBridge.MAX_LIGHTS:GpuLightRenderer.MAX_LIGHTS;
            var selected=orderForGpu(world,camera,total);
            GpuLightRenderer.setLights(selected);
            // The Advanced tier expects the shader pack patch, so the pack lights the scene itself
            // and the screen-space overlay stands down. The mask travels with the list because its bits
            // are the list's indices.
            if(ClientFlashes.inPackLighting()) IrisMuzzleBridge.update(selected,shadowMask,lightSoftness,lightSteps);
            if(MuzzleClientConfig.ADV_DEBUG.get() && !world.isEmpty() && (lastLog==Long.MIN_VALUE || now-lastLog>=20)) {
                int soft=0;
                for(int i=0;i<selected.size();i++) if((shadowMask&(1<<i))!=0&&lightSoftness[i]>0.0) soft++;
                com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light GPU: lights={} ({} spotlight(s), {} lamp(s), {} carried and {} peer light(s); {} casting a shadow, {} of them soft), rebuilds=0 (no section dirtying)",world.size(),count(world,true),count(world,false),countHeld(world),countPeers(world),Integer.bitCount(shadowMask),soft);
                // Which registered lamps actually reached the shader, with their distance and their
                // palette index. A lamp that drops out of this list as the player walks is being cut
                // by a gate (activation distance, the static budget, or the server's own source list);
                // a lamp that stays listed while the colour still changes is a shader-side problem.
                var lamps=new StringBuilder();
                for(var entry:world.entrySet()) {
                    if(!isStaticSource(entry.getKey())) continue;
                    lamps.append(' ').append(entry.getKey())
                         .append('@').append(Math.round(Math.sqrt(distanceSquared(entry.getValue(),camera))))
                         .append(":palette").append(entry.getValue().color);
                }
                com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light GPU statics:{}",lamps.length()==0?" none":lamps.toString());
                lastLog=now;
            }
            return true;
        } catch(RuntimeException | LinkageError ex) {
            fail(ex); clear(level); return false;
        }
    }
    /**
     * Dynamic lights (muzzle flashes, tracers) and the lights players carry, each sorted nearest-first.
     * Both GPU backends truncate the list to their own slot count, so the budgets in {@link #capSources}
     * are what guarantee a wall of lamps can never push cannon fire out.
     *
     * <p>The order no longer has anything to do with shadows. It used to, because the pack took "the
     * first N lights" as its shadow casters and the mod had to reorder the list to put a chosen light at
     * a chosen index; the pack now takes a bitmask instead, so who casts is decided here and the order is
     * free to stay what the budgets want it to be.
     */
    private static List<AreaSnapshot> orderForGpu(Map<String,AreaSnapshot> world,Vec3 camera,int total) {
        var dynamic=new ArrayList<AreaSnapshot>();
        var held=new ArrayList<AreaSnapshot>();
        var peers=new ArrayList<AreaSnapshot>();
        var spots=new ArrayList<AreaSnapshot>();
        var statics=new ArrayList<AreaSnapshot>();
        // Which lights are even allowed a shadow slot is a per-light decision from the client's own
        // config, so it is worked out once here while the keys are still to hand.
        var allowed=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<AreaSnapshot,Boolean>());
        var reserved=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<AreaSnapshot,Boolean>());
        // What each light asked its shadow to look like, resolved against the client's own ceiling here,
        // while the keys are still to hand. Absent means hard edges and the client's tap count.
        var softWish=new java.util.IdentityHashMap<AreaSnapshot,Double>();
        var stepsWish=new java.util.IdentityHashMap<AreaSnapshot,Integer>();
        for(var e:world.entrySet()) {
            String key=e.getKey();
            AreaSnapshot snapshot=e.getValue();
            if(canCast(key,snapshot)) {
                allowed.add(snapshot);
                // The player's own flashlight beam keeps its slot whatever else is going on: it is the
                // light they are looking down, so it is the one guaranteed to be aimed at what they can
                // see, and a barrage must not be able to take it away.
                if(isHeld(key)&&isBeam(key)) reserved.add(snapshot);
            }
            double soft=softnessFor(key,snapshot);
            if(soft>0.0) softWish.put(snapshot,soft);
            int steps=stepsFor(key,snapshot);
            if(steps>0) stepsWish.put(snapshot,steps);
            if(isHeld(key)) held.add(snapshot);
            else if(isPeer(key)) peers.add(snapshot);
            else if(isSpotlight(key)) spots.add(snapshot);
            else if(isStaticSource(key)) statics.add(snapshot);
            else dynamic.add(snapshot);
        }
        Comparator<AreaSnapshot> nearest=Comparator.comparingDouble(s -> distanceSquared(s,camera));
        dynamic.sort(nearest); held.sort(nearest); peers.sort(nearest); spots.sort(nearest); statics.sort(nearest);
        // The lights the player is standing in are reserved their slots: the dynamic list is trimmed so
        // that everything else still fits. Without this a barrage pushed the carried light past the end
        // of the buffer, and it blinked back in whenever a flash expired.
        int reservedRoom=held.size()+peers.size()+spots.size()+statics.size();
        int dynamicRoom=Math.max(0,total-reservedRoom);
        if(dynamic.size()>dynamicRoom) dynamic=new ArrayList<>(dynamic.subList(0,dynamicRoom));
        var selected=new ArrayList<AreaSnapshot>(dynamic.size()+reservedRoom);
        selected.addAll(dynamic); selected.addAll(held); selected.addAll(peers);
        selected.addAll(spots); selected.addAll(statics);
        if(selected.size()>total) selected=new ArrayList<>(selected.subList(0,total));
        shadowMask=maskFor(selected,camera,allowed,reserved);
        lightSoftness=new double[selected.size()];
        lightSteps=new int[selected.size()];
        for(int i=0;i<selected.size();i++) {
            AreaSnapshot snapshot=selected.get(i);
            Double soft=softWish.get(snapshot);
            Integer steps=stepsWish.get(snapshot);
            lightSoftness[i]=soft==null?0.0:soft;
            lightSteps[i]=steps==null?0:steps;
        }
        return selected;
    }
    /**
     * Whether this light is allowed to cast at all.
     *
     * <p>For everything except a registered lamp that is the client's own class switch, which is what
     * keeps a lamp somebody else placed from spending this player's frame time. A registered lamp has its
     * own answer on its page as well, and the two have to agree: the page decides whether <em>this</em>
     * lamp casts, the client's switch decides whether any lamp may.
     */
    private static boolean canCast(String key,AreaSnapshot snapshot) {
        if(isHeld(key)) return isBeam(key)&&ExtraConfig.GPU_FLASHLIGHT_SHADOW.get();
        if(isPeer(key)) return isBeam(key)&&ExtraConfig.GPU_FLASHLIGHT_PEER_SHADOW.get();
        if(isSpotlight(key)) return ExtraConfig.GPU_SHADOW_SPOTLIGHTS.get();
        if(isStaticSource(key)) return ExtraConfig.GPU_SHADOW_SOURCES.get()&&snapshot.shadowMode!=0;
        return ExtraConfig.GPU_SHADOW_MUZZLE.get();
    }
    /**
     * The penumbra this light asked for, in blocks, or 0 for a hard edge.
     *
     * <p>Three things can ask, and the player's own ceiling settles all of them: the light's own page (a
     * registered lamp), the class it belongs to (muzzle flashes, which are the lights least worth looking
     * at directly), and nothing at all. Setting the client's softness to zero forces hard edges whoever
     * asked for what — that is the player's veto, and it is the reason the wish and the decision are kept
     * apart.
     */
    private static double softnessFor(String key,AreaSnapshot snapshot) {
        double ceiling=ExtraConfig.GPU_SHADOW_SOFTNESS.get();
        if(ceiling<=0.0) return 0.0;
        // A beam is hard by construction, and the pack ignores all of this for it anyway: its axis vector
        // is its direction, not free space. The halo at a waist is small and diffuse enough that a
        // penumbra would cost a chip of readability for nothing.
        if(isBeam(key)||isHeld(key)||isPeer(key)) return 0.0;
        if(isStaticSource(key)) return snapshot.shadowMode==2?ceiling:0.0;
        return ExtraConfig.GPU_SHADOW_MUZZLE_SOFT.get()?ceiling:0.0;
    }
    /** This light's own tap ceiling, or 0 to take the client's. Only ever lowers it. */
    private static int stepsFor(String key,AreaSnapshot snapshot) {
        if(snapshot.shadowSteps<=0) return 0;
        return Math.min(snapshot.shadowSteps,ExtraConfig.GPU_SHADOW_STEPS.get());
    }
    /**
     * The one bit per light the pack reads, in the order the buffer was just built in.
     *
     * <p>The reserved lights are set outright; the remaining slots go to whichever of the others covers
     * the most of the <em>view</em>, weighted by brightness and measured against the light's own reach
     * rather than against where its centre landed on screen — see {@link LightScreenWeight} for why that
     * distinction is the whole point.
     */
    private static int maskFor(List<AreaSnapshot> selected,Vec3 camera,
                               java.util.Set<AreaSnapshot> allowed,java.util.Set<AreaSnapshot> reserved) {
        int mask=0;
        for(int i=0;i<selected.size();i++) {
            AreaSnapshot snapshot=selected.get(i);
            if(reserved.contains(snapshot)) mask|=1<<i;
        }
        int slots=ShadowBudget.effective(ExtraConfig.GPU_SHADOW_COUNT.get());
        if(slots<=0) return mask;
        var candidates=new ArrayList<AreaSnapshot>();
        for(AreaSnapshot snapshot:selected) {
            if(allowed.contains(snapshot)&&!reserved.contains(snapshot)) candidates.add(snapshot);
        }
        if(candidates.isEmpty()) return mask;
        double[] planes=frustum(camera);
        candidates.sort(Comparator.comparingDouble(
                (AreaSnapshot s) -> -LightScreenWeight.of(s.light,camera.x,camera.y,camera.z,planes)));
        var picked=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<AreaSnapshot,Boolean>());
        picked.addAll(candidates.subList(0,Math.min(slots,candidates.size())));
        for(int i=0;i<selected.size();i++) if(picked.contains(selected.get(i))) mask|=1<<i;
        return mask;
    }
    /** The camera's four side planes, as {@link LightScreenWeight} wants them. */
    private static double[] frustum(Vec3 camera) {
        var near=Minecraft.getInstance().gameRenderer.getMainCamera().getNearPlane();
        Vec3 topLeft=near.getTopLeft(), topRight=near.getTopRight();
        Vec3 bottomRight=near.getBottomRight(), bottomLeft=near.getBottomLeft();
        return LightScreenWeight.sidePlanes(camera.x,camera.y,camera.z,new double[]{
                topLeft.x,topLeft.y,topLeft.z,
                topRight.x,topRight.y,topRight.z,
                bottomRight.x,bottomRight.y,bottomRight.z,
                bottomLeft.x,bottomLeft.y,bottomLeft.z});
    }
    /** Which lights of the last GPU list cast a shadow, as the bitmask the pack reads. */
    private static int shadowMask;
    /** Per-light penumbra radius in blocks, parallel to the last GPU list; 0 is a hard edge. */
    private static double[] lightSoftness=new double[0];
    /** Per-light tap ceiling, parallel to the last GPU list; 0 means "take the client's". */
    private static int[] lightSteps=new int[0];
    private static Map<String,AreaSnapshot> computeWorld(ClientLevel level,long now,boolean includeSources) {
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // Every barrel keeps its own pulse; no twin-barrel grouping.
        var selected=ClientFlashes.STORE.areaFlashes();
        var entries=new ArrayList<>(selected.entrySet());
        entries.sort(Comparator.comparingDouble(e -> e.getValue().distanceSquared(camera.x,camera.y,camera.z)));
        Map<String,AreaSnapshot> world=new LinkedHashMap<>();
        for(var e:entries) {
            var f=e.getValue(); long age=now-f.born();
            if(age<0 || now>=f.expires()) continue;
            boolean big=f.kind()==0;
            boolean blast=big && age<f.layout().muzzleHoldTicks();
            var profile=big ? (blast?MuzzleClientConfig.BLAST_PROFILE:MuzzleClientConfig.SMOKE_PROFILE) : MuzzleClientConfig.AUTO_PROFILE;
            long stageAge=big && !blast ? age-f.layout().muzzleHoldTicks() : age;
            double strength=profile.curve().brightness(stageAge);
            if(strength<=0) continue;
            double x=big?f.x():f.emitter().x(), y=big?f.y():f.emitter().y(), z=big?f.z():f.emitter().z();
            if(big && age>=f.layout().muzzleHoldTicks()) {
                if(f.trackedAt()!=Long.MIN_VALUE) {
                    if(!f.hot() || now-f.trackedAt()>2) continue;
                    x=f.tipX(); y=f.tipY(); z=f.tipZ();
                } else {
                    double distance=f.layout().fallbackTravel(age);
                    x+=f.dx()*distance; y+=f.dy()*distance; z+=f.dz()*distance;
                }
            }
            double size=!big ? 1 : blast ? f.emitter().charge()
                    : f.smokeSize()>0 ? f.smokeSize() : f.emitter().plumeSize()*0.25;
            var light=profile.light(x,y,z,f.dx(),f.dy(),f.dz(),size,strength);
            world.put(e.getKey(),new AreaSnapshot(light,AreaSnapshot.bounds(light),null));
            if(world.size()>=MuzzleClientConfig.ADV_MAX.get()) break;
        }
        ExtraLights.append(level,world,includeSources);
        DiscoBallLights.append(level,world);
        // The player's own flashlight is a light like any other here: one entry per tick, no packets.
        if(includeSources) HeldLights.append(level,world);
        if(includeSources) capSources(world,camera,now);
        return world;
    }
    /**
     * Keeps each class of static light inside its own budget, nearest first.
     *
     * <p>Two things changed here for the light-system work. Registered lamps and spotlights now have
     * separate budgets, because sharing one meant a wall of searchlights could empty the list of
     * ordinary lamps (and the reverse); and the choice is only remade every
     * {@code reselectIntervalTicks}, so walking a few blocks no longer reshuffles which lights survive
     * the budget and makes them visibly pop in and out. Between re-selections the previous choice is
     * what is drawn, with each light's own snapshot still rebuilt every tick.
     */
    private static void capSources(Map<String,AreaSnapshot> world,Vec3 camera,long now) {
        boolean reselect=lastReselect==Long.MIN_VALUE || now-lastReselect>=ExtraConfig.LIGHT_RESELECT_TICKS.get();
        if(reselect) lastReselect=now;
        var lamps=new ArrayList<Map.Entry<String,AreaSnapshot>>();
        var spots=new ArrayList<Map.Entry<String,AreaSnapshot>>();
        var held=new ArrayList<Map.Entry<String,AreaSnapshot>>();
        var peers=new ArrayList<Map.Entry<String,AreaSnapshot>>();
        for(var entry:world.entrySet()) {
            if(isHeld(entry.getKey())) held.add(entry);
            else if(isPeer(entry.getKey())) peers.add(entry);
            else if(isSpotlight(entry.getKey())) spots.add(entry);
            else if(isStaticSource(entry.getKey())) lamps.add(entry);
        }
        capClass(world,held,ExtraConfig.HELD_BUDGET.get(),isHeldSource,"held",camera,reselect);
        // Other players' lamps have their own budget, so a crowd cannot push the player's own lamp out.
        capClass(world,peers,ExtraConfig.PEER_BUDGET.get(),isPeerSource,"peers",camera,reselect);
        capClass(world,lamps,ExtraConfig.STATIC_BUDGET.get(),isLampSource,"lamps",camera,reselect);
        capClass(world,spots,ExtraConfig.SPOT_BUDGET.get(),AdvancedLights::isSpotlight,"spots",camera,reselect);
    }
    /**
     * Trims one class of light to its budget. The class name keys the remembered selection, so each class
     * keeps its own winners between re-selections — keying it by the budget number instead meant two
     * classes that happened to share a budget fought over one set.
     */
    private static void capClass(Map<String,AreaSnapshot> world,List<Map.Entry<String,AreaSnapshot>> entries,
                                 int budget,java.util.function.Predicate<String> belongs,String name,Vec3 camera,boolean reselect) {
        if(!reselect) {
            // Between re-selections the previous winners stay; anything not in the set is dropped.
            Set<String> keep=SELECTED.get(name);
            if(keep==null) { SELECTED.put(name,Set.of()); return; }
            world.keySet().removeIf(key -> belongs.test(key) && !keep.contains(key));
            return;
        }
        if(entries.size()<=budget) {
            SELECTED.put(name,Set.copyOf(keysOf(entries)));
            return;
        }
        entries.sort(Comparator.comparingDouble(e -> distanceSquared(e.getValue(),camera)));
        var keep=new HashSet<String>();
        for(int i=0;i<budget;i++) keep.add(entries.get(i).getKey());
        SELECTED.put(name,Set.copyOf(keep));
        for(int i=budget;i<entries.size();i++) world.remove(entries.get(i).getKey());
    }
    private static List<String> keysOf(List<Map.Entry<String,AreaSnapshot>> entries) {
        var keys=new ArrayList<String>(entries.size());
        for(var entry:entries) keys.add(entry.getKey());
        return keys;
    }
    private static double distanceSquared(AreaSnapshot snapshot,Vec3 camera) {
        var light=snapshot.light;
        double dx=light.x()-camera.x, dy=light.y()-camera.y, dz=light.z()-camera.z;
        return dx*dx+dy*dy+dz*dz;
    }
    private static boolean isStaticSource(String key) {
        return key.startsWith("staticGround/") || key.startsWith("staticShip/") || key.startsWith("spot/");
    }
    private static boolean isSpotlight(String key) { return key.startsWith("spot/"); }
    /** A light carried by a player: own budget, and it outranks the lamps in the world. */
    private static boolean isHeld(String key) { return key.startsWith("held/"); }
    /** A light carried by somebody else, in its own budget and behind the player's own in the list. */
    private static boolean isPeer(String key) { return key.startsWith("peer/"); }
    /** Which of a carried lamp's two entries this is. The beam is the one that casts shadows. */
    private static boolean isBeam(String key) { return key.endsWith("/beam"); }
    private static final java.util.function.Predicate<String> isHeldSource=AdvancedLights::isHeld;
    private static final java.util.function.Predicate<String> isPeerSource=AdvancedLights::isPeer;
    private static final java.util.function.Predicate<String> isLampSource=
            key -> isStaticSource(key) && !isSpotlight(key) && !isHeld(key) && !isPeer(key);
    /** How many live lights of one class are in the buffer, for the debug line. */
    private static int count(Map<String,AreaSnapshot> world,boolean spots) {
        int n=0;
        for(var key:world.keySet()) if(isSpotlight(key)==spots && isStaticSource(key)) n++;
        return n;
    }
    /** How many carried (flashlight) lights are in the buffer, for the debug line. */
    private static int countHeld(Map<String,AreaSnapshot> world) {
        int n=0;
        for(var key:world.keySet()) if(isHeld(key)) n++;
        return n;
    }
    /** How many other players' lamp entries are in the buffer, for the debug line. */
    private static int countPeers(Map<String,AreaSnapshot> world) {
        int n=0;
        for(var key:world.keySet()) if(isPeer(key)) n++;
        return n;
    }
    /** Last chosen set per light class, kept between re-selections so the lighting does not reshuffle. */
    private static final Map<String,Set<String>> SELECTED=new HashMap<>();
    private static long lastReselect=Long.MIN_VALUE;
    public static void clear(ClientLevel level) {
        lastLog=Long.MIN_VALUE;
        if(ModList.get().isLoaded("lambdynlights_runtime")) {
            try { LambAreaBackend.clear(); }
            catch(RuntimeException | LinkageError ex) { fail(ex); }
        }
        if(level!=null) publish(level,Map.of());
        else { lookup=Map.of(); previous=Map.of(); }
    }
    private static void publish(ClientLevel level,Map<String,AreaSnapshot> next) {
        Map<Long,List<AreaSnapshot>> index=new HashMap<>();
        for(var area:next.values()) for(long section:sections(area.bounds)) index.computeIfAbsent(section,k -> new ArrayList<>()).add(area);
        index.replaceAll((k,v) -> List.copyOf(v));
        lookup=Map.copyOf(index);
        Set<Long> dirty=new HashSet<>();
        for(var e:previous.entrySet()) if(!e.getValue().same(next.get(e.getKey()))) dirty.addAll(sections(e.getValue().bounds));
        for(var e:next.entrySet()) if(!e.getValue().same(previous.get(e.getKey()))) dirty.addAll(sections(e.getValue().bounds));
        previous=Map.copyOf(next);
        if(!dirty.isEmpty() && ModList.get().isLoaded("yuushya_modelling")) YuushyaLighting.refresh(dirty);
        // Render/Flywheel invalidation only: deliberately no checkBlock or block-light propagation.
        for(long packed:dirty) {
            var section=SectionPos.of(packed);
            if(!level.isOutsideBuildHeight(section.origin()) && level.hasChunkAt(section.origin())) {
                level.getChunkSource().onLightUpdate(LightLayer.BLOCK,section);
                ((dev.cbcmuzzlelight.mixin.LightRefreshAccessor)Minecraft.getInstance().levelRenderer).muzzle$dirty(section.x(),section.y(),section.z(),true);
            }
        }
    }
    private static Set<Long> sections(AABB bounds) {
        AABB b=bounds.inflate(1);
        Set<Long> result=new HashSet<>();
        for(int x=((int)Math.floor(b.minX))>>4;x<=((int)Math.floor(b.maxX))>>4;x++)
            for(int y=((int)Math.floor(b.minY))>>4;y<=((int)Math.floor(b.maxY))>>4;y++)
                for(int z=((int)Math.floor(b.minZ))>>4;z<=((int)Math.floor(b.maxZ))>>4;z++) result.add(SectionPos.asLong(x,y,z));
        return result;
    }
    public static Map<Long,List<AreaSnapshot>> snapshot() { return lookup; }
    public static List<AreaSnapshot> atSection(long section) { return LightReadScope.current().getOrDefault(section,List.of()); }
    public static double lightAt(BlockPos pos) {
        double value=0;
        for(var area:atSection(SectionPos.asLong(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4)))
            value=Math.max(value,area.at(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5));
        return value;
    }
    public static int lightmap(BlockPos pos,int packed) {
        if(LightReadScope.current().isEmpty() || (packed & 0xFFFF)>=240) return packed;
        int light=(int)(lightAt(pos)*16);
        return (packed & ~0xFFFF) | Math.max(packed & 0xFFFF,light);
    }
}
