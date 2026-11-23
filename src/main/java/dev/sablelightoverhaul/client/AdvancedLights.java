package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class AdvancedLights {
    private static long lastLog=Long.MIN_VALUE;
    private AdvancedLights() {}
    /**
     * The keys of the lamps that may own a light field, which is what keeps their fields alive.
     *
     * <p>It used to collect {@code staticGround/} only, and {@code LightFields.retain} deletes every field that is
     * not in this set -- so a lamp mounted on a vehicle or any physics body ({@code staticShip/}) lost its field on
     * every tick and had it rebuilt on the next one. Two symptoms came from that one line: a lamp on a moving body
     * flickered, because its layer was rewritten from scratch each tick, and a lamp placed on one never lit solid
     * terrain at all, because its layer was empty whenever the shader read it. Entities kept responding because
     * they are lit through a different path.
     */
    private static java.util.Set<String> fieldKeys(Map<String,AreaSnapshot> world) {
        java.util.Set<String> keys=new java.util.HashSet<>();
        for(String key:world.keySet())
            if(key.startsWith("staticGround/")||key.startsWith("staticShip/")) keys.add(key);
        return keys;
    }
    /** GPU mode: publish lights to the shader buffer instead of marking any section dirty. */
    public static void gpuUpdate(ClientLevel level) {
        try {
            long now=level.getGameTime();
            BodyFrames.begin();
            Map<String,AreaSnapshot> world=computeWorld(level,now,true);
            var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            // The two backends take different numbers of lights, so the list has to be built for the one
            // that is actually consuming it: handing the pack a list built for the overlay would leave
            // lights it never receives, and the lights at the end of the order are the ones dropped.
            int total=ClientFlashes.inPackLighting()?IrisMuzzleBridge.MAX_LIGHTS:GpuLightRenderer.MAX_LIGHTS;
            var selected=orderForGpu(world,camera,total);

            // The key behind each selected light, by identity: the world map is rebuilt every tick, so this
            // tick's AreaSnapshot is never the same object as the last tick's, and the key is what links a light
            // to its registered source -- and therefore to its light field. Built once here because the bridge
            // needs it to find each lamp's field layer and the debug line below needs it to name the slots.
            var keys=new java.util.IdentityHashMap<AreaSnapshot,String>();
            for(var entry:world.entrySet()) keys.put(entry.getValue(),entry.getKey());
            GpuLightRenderer.setLights(selected,keys);
            // The Advanced tier expects the shader pack patch, so the pack lights the scene itself
            // and the screen-space overlay stands down. The mask travels with the list because its bits
            // are the list's indices.
            if(ClientFlashes.inPackLighting()) IrisMuzzleBridge.update(selected,cloudMask,cloudReach,shadowMask,lightSoftness,lightSteps,lightStyle,BodyFrames.table(),keys);
            if(ExtraConfig.on(MuzzleClientConfig.ADV_DEBUG) && !world.isEmpty() && (lastLog==Long.MIN_VALUE || now-lastLog>=20)) {
                int soft=0;
                for(int i=0;i<selected.size();i++) if((shadowMask&(1<<i))!=0&&lightSoftness[i]>0.0) soft++;
                com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul GPU: lights={} ({} spotlight(s), {} lamp(s), {} carried and {} peer light(s); {} casting a shadow, {} of them soft, {} lighting clouds with reaches {}; {} body frame(s), {} cone-bearing body(ies) spilled), rebuilds=0 (no section dirtying), spot shadows={}",world.size(),count(world,true),count(world,false),countHeld(world),countPeers(world),Integer.bitCount(shadowMask),soft,Integer.bitCount(cloudMask),describeCloudReach(),BodyFrames.used(),BodyFrames.spilled(),SpotShadows.stats());
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
                com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul GPU statics:{}",lamps.length()==0?" none":lamps.toString());
                lastLog=now;
            }
            // Debug formatting scans voxel fields and builds per-source descriptions: gate before evaluation.
            if(!ClientFlashes.sloOnly() && ExtraConfig.on(MuzzleClientConfig.ADV_DEBUG)) {
                var slots=new StringBuilder();
                for(int i=0;i<selected.size();i++) {
                    AreaSnapshot snapshot=selected.get(i);
                    String key=keys.get(snapshot);
                    slots.append(' ').append(i).append(':').append(key==null?"?":key)
                         .append('@').append(Math.round(Math.sqrt(distanceSquared(snapshot,camera))));
                }
                FieldLog.line("fields    {}",LightFields.describe());
                // What the field holds at the pixel under the crosshair, addressed exactly as the shader addresses
                // it. Aim at a surface that looks wrong and this line says whether the field is empty there.
                FieldLog.line("look      {}",LightFields.describeLook());
                FieldLog.line("gate      {}",IrisMuzzleBridge.describeGate());
                FieldLog.line("slots     {}",slots.length()==0?" none":slots.toString());
                // Every registered source as the client holds it: its id, the model its page asks for, whether it
                // knows which body it is on, and the world position the pose gives it. A lamp whose light sits in
                // one place instead of following its hull shows up here as a world position that does not change,
                // and a lamp that lost its body shows up with ship?0.
                FieldLog.line("sources   {}",ExtraLights.describeSources());
                // The values the shader was actually handed, not the ones the light list holds. A lamp that lights
                // the wrong place, or lights one, has its answer in this line.
                FieldLog.line("uniforms  {}",IrisMuzzleBridge.describeUniforms());
                // Which lights this client believes may cast, one letter per selected light: 'F' for a lamp on the
                // field model, 'R' for one whose page asks for a ray, 'S' when it also holds a shadow slot this
                // frame, '-' when it may not cast at all. A lamp on the field model has no use for a shadow slot --
                // the field already knows what it reached -- so an F that is also an S is a slot spent on nothing,
                // and it can push a real ray light out of the budget.
                var casting=new StringBuilder();
                for(int i=0;i<selected.size();i++) {
                    AreaSnapshot snapshot=selected.get(i);
                    String key=keys.get(snapshot);
                    boolean field=(lightStyle[i]&IrisMuzzleBridge.STYLE_FLOOD)!=0;
                    boolean mayCast=key!=null&&canCast(key,snapshot);
                    casting.append(field?'F':'R').append(mayCast?'.':'-')
                           .append((shadowMask&(1<<i))!=0?'S':' ')
                           // The letter is the light's style; what canCast actually decided on is the snapshot. Both are
                           // printed, because a light drawn as a ray can still carry flood=true, and canCast refuses a
                           // flood light before it ever looks at the shadow answer.
                           .append("[f").append(snapshot.flood?1:0).append(" s").append(snapshot.shadowMode).append("]")
                           .append(' ');
                }
                FieldLog.line("casting   mask=0x{} budget={} {}",Integer.toHexString(shadowMask),
                        ExtraConfig.GPU_SHADOW_COUNT.get(),casting.toString().trim());
            }
        } catch(RuntimeException | LinkageError ex) {
            GpuLightRenderer.clear();
            com.mojang.logging.LogUtils.getLogger().error("SLO could not update the GPU light list", ex);
        }
    }
    /**
     * Dynamic lights (muzzle flashes, tracers) and the lights players carry, each sorted nearest-first.
     * Both GPU backends truncate the list to their own slot count, so the budgets in {@link #capSources}
     * are what guarantee a wall of lamps can never push cannon fire out, and the class priorities below
     * are what decide the order when even the budgets do not fit.
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
        // Which priority class each light belongs to, worked out here while the keys are still to hand.
        var priority=new java.util.IdentityHashMap<AreaSnapshot,Integer>();
        // Which lights are even allowed a shadow slot is a per-light decision from the client's own
        // config, so it is worked out once here while the keys are still to hand.
        var allowed=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<AreaSnapshot,Boolean>());
        var reserved=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<AreaSnapshot,Boolean>());
        // What each light asked its shadow to look like, resolved against the client's own ceiling here,
        // while the keys are still to hand. Absent means hard edges and the client's tap count.
        var softWish=new java.util.IdentityHashMap<AreaSnapshot,Double>();
        var stepsWish=new java.util.IdentityHashMap<AreaSnapshot,Integer>();
        boolean nativeOnly = ClientFlashes.sloOnly();
        for(var e:world.entrySet()) {
            String key=e.getKey();
            AreaSnapshot snapshot=e.getValue();
            priority.put(snapshot,priorityRankOf(key));
            if(!nativeOnly && canCast(key,snapshot)) {
                allowed.add(snapshot);
                // The player's own flashlight beam keeps its slot whatever else is going on: it is the
                // light they are looking down, so it is the one guaranteed to be aimed at what they can
                // see, and a barrage must not be able to take it away.
                if(isHeld(key)&&isBeam(key)) reserved.add(snapshot);
            }
            if (!nativeOnly) {
                double soft=softnessFor(key,snapshot);
                if(soft>0.0) softWish.put(snapshot,soft);
                int steps=stepsFor(key,snapshot);
                if(steps>0) stepsWish.put(snapshot,steps);
            }
            if(isHeld(key)) held.add(snapshot);
            else if(isPeer(key)) peers.add(snapshot);
            else if(isSpotlight(key)) spots.add(snapshot);
            else if(isStaticSource(key)) statics.add(snapshot);
            else dynamic.add(snapshot);
        }
        Comparator<AreaSnapshot> nearest=Comparator.comparingDouble(s -> distanceSquared(s,camera));
        // Cannon fire is served before decoration: inside the dynamic class a higher priority sorts first
        // and distance only decides within one priority. The other classes are each trimmed to their own
        // budget before they get here, so they keep plain nearest-first.
        Comparator<AreaSnapshot> byPriorityThenNearest=
                Comparator.<AreaSnapshot>comparingInt(s -> -priority.getOrDefault(s,0)).thenComparing(nearest);
        dynamic.sort(byPriorityThenNearest); held.sort(nearest); peers.sort(nearest); spots.sort(nearest); statics.sort(nearest);
        // The lights the player is standing in are reserved their slots: the dynamic list is trimmed so
        // that everything else still fits. Without this a barrage pushed the carried light past the end
        // of the buffer, and it blinked back in whenever a flash expired.
        //
        // Only the carried lights are reserved ahead of the fire. Registered lamps and spotlights used to be
        // reserved too, and because the pool is 32 while their budgets are counted separately, a base with
        // enough of them made this room zero: the muzzle flash and the explosion light were dropped whole
        // while every lamp kept its slot. That is the starvation the priorities exist to prevent, so a lamp
        // that loses its slot now loses it to cannon fire, and the per-class budgets further down are what
        // still stop one class of lamp from emptying the rest.
        int reservedRoom=held.size()+peers.size();
        int dynamicRoom=Math.max(0,total-reservedRoom);
        if(dynamic.size()>dynamicRoom) dynamic=new ArrayList<>(dynamic.subList(0,dynamicRoom));
        var selected=new ArrayList<AreaSnapshot>(dynamic.size()+reservedRoom);
        selected.addAll(dynamic); selected.addAll(held); selected.addAll(peers);
        selected.addAll(spots); selected.addAll(statics);
        // The buffer is shorter than the candidate list whenever the player is anywhere near a lit base, so
        // who is left out has to be a decision that *persists*. Ordering the whole list by distance and
        // cutting the tail re-decided it on every rebuild, and walking reorders distances continuously, so
        // the lights at the cut blinked out and back several times a second -- the registered lamps, since
        // they are concatenated last. Slots are now sticky, the way the shadow slots are: a light that is
        // already lighting something keeps its slot until it leaves the candidate list or the buffer shrinks,
        // and when it does shrink the slot is given up by the least visible holder rather than by whoever
        // happens to be last.
        if(selected.size()>total) {
            var keys=new java.util.IdentityHashMap<AreaSnapshot,String>();
            for(var entry:world.entrySet()) keys.put(entry.getValue(),entry.getKey());
            GpuLightRenderer.setLights(selected,keys);
            var present=new java.util.HashSet<String>();
            for(AreaSnapshot snapshot:selected) {
                String key=keys.get(snapshot);
                if(key!=null) present.add(key);
            }
            bufferHolders.retainAll(present);
            double[] planes=frustum(camera);
            if(bufferHolders.size()>total) {
                var holders=new ArrayList<AreaSnapshot>();
                for(AreaSnapshot snapshot:selected) if(bufferHolders.contains(keys.get(snapshot))) holders.add(snapshot);
                // Keep the lights that matter: the higher priority class first, and inside one priority the
                // light that covers more of the view. This used to sort by screen weight *ascending* and
                // keep the first `total`, which keeps the least visible -- the opposite of the rule the
                // comment above describes. It is latent rather than visible, because both backends accept
                // 32 lights and this branch needs the buffer to shrink below what is already held.
                holders.sort(Comparator.<AreaSnapshot>comparingInt(s -> -priority.getOrDefault(s,0))
                        .thenComparing(Comparator.comparingDouble(
                                (AreaSnapshot s) -> LightScreenWeight.of(s.light,camera.x,camera.y,camera.z,planes)).reversed()));
                bufferHolders.clear();
                for(int i=0;i<total;i++) bufferHolders.add(keys.get(holders.get(i)));
            }
            var kept=new ArrayList<AreaSnapshot>(total);
            for(AreaSnapshot snapshot:selected) if(bufferHolders.contains(keys.get(snapshot))) kept.add(snapshot);
            if(kept.size()<total) {
                var extra=new ArrayList<AreaSnapshot>();
                for(AreaSnapshot snapshot:selected) if(!bufferHolders.contains(keys.get(snapshot))) extra.add(snapshot);
                extra.sort(Comparator.comparingDouble(
                        (AreaSnapshot s) -> -LightScreenWeight.of(s.light,camera.x,camera.y,camera.z,planes)));
                for(AreaSnapshot snapshot:extra) {
                    if(kept.size()>=total) break;
                    kept.add(snapshot);
                    String key=keys.get(snapshot);
                    if(key!=null) bufferHolders.add(key);
                }
            }
            selected=kept;
        } else {
            // Everything fits: nothing is being denied a slot, so there is nothing to hold on to.
            bufferHolders.clear();
        }
        var keys=new java.util.IdentityHashMap<AreaSnapshot,String>();
        for(var entry:world.entrySet()) keys.put(entry.getValue(),entry.getKey());
        if (nativeOnly) {
            shadowMask = cloudMask = 0;
            return selected;
        }
        shadowMask=maskFor(selected,camera,allowed,reserved,keys);
        lightSoftness=new double[selected.size()];
        lightSteps=new int[selected.size()];
        for(int i=0;i<selected.size();i++) {
            AreaSnapshot snapshot=selected.get(i);
            Double soft=softWish.get(snapshot);
            Integer steps=stepsWish.get(snapshot);
            lightSoftness[i]=soft==null?0.0:soft;
            lightSteps[i]=steps==null?0:steps;
        }
        // Cloud participation is decided here, where every selected light's class is still known (the
        // key is what separates a muzzle flash from a registered lamp). A reach of 0 also clears the bit,
        // so the shader can test the mask alone and never has to re-derive the classes.
        cloudMask=0;
        cloudReach=new float[selected.size()];
        lightStyle=new int[selected.size()];
        // The fields need the level itself, and this method only has the light list. Taken once, here,
        // rather than per slot: it is the same client level for every light in the frame.
        ClientLevel fieldLevel=net.minecraft.client.Minecraft.getInstance().level;
        // The array is filled slot by slot below, and the slots that are not filled must not keep last frame's
        // lamp: a stale layer number with a stale origin is a field as far as anything downstream can tell. See
        // LightFields.clearAll.
        LightFields.clearAll(IrisMuzzleBridge.fieldSlots());
        for(int i=0;i<selected.size();i++) {
            AreaSnapshot snapshot=selected.get(i);
            double reach=cloudReachFor(keys.get(snapshot),snapshot);
            cloudReach[i]=(float)reach;
            if(reach>0.0) cloudMask|=1<<i;
            // Every light carries a style word, but only a registered lamp ever says anything but "all
            // faces, shaped": a muzzle flash or a tracer has no page to ask.
            lightStyle[i]=IrisMuzzleBridge.style(snapshot);
            // The vanilla light field: a lamp whose page asks for the vanilla model gets a flood instead of a
            // ray. Both the ground and a physics body are supported, and the difference between them is one
            // block position: a body's blocks live in its plot, so its flood runs there and the shader reads
            // it there (see ExtraLights.fieldBlock and the body tables in the shader). A lamp with no field
            // gets all-zero here, which the shader reads as "no field", so the two sides can be updated
            // independently.
            String fieldKey=keys.get(snapshot);
            // A lamp on a body whose hull did not fit the frame table has nowhere to flood: publishing it at
            // its world position would sample a plot lattice in world space, which is worse than no field.
            var lattice=ExtraLights.fieldBlock(fieldKey);
            boolean onBody=fieldKey!=null&&fieldKey.startsWith("staticShip/");
            if(fieldLevel!=null&&isStaticSource(fieldKey)&&(lightStyle[i]&IrisMuzzleBridge.STYLE_FLOOD)!=0
                    &&(!onBody||lattice!=null)) {
                // Scene positions in this list are world positions; the camera-relative conversion happens
                // when the slots are uploaded. `owner` is the block the field lives on: the lamp's world
                // block on the ground, and its plot block on a body -- which is also the block the takeover
                // gave up, so the same value answers both questions.
                var at=BlockPos.containing(snapshot.light.x(),snapshot.light.y(),snapshot.light.z());
                var owner=lattice==null?at:lattice;
                var sourceBlock=ExtraLights.sourceBlock(fieldKey);
                if(ClientFlashes.takenOver(fieldLevel,(sourceBlock==null?owner:sourceBlock).asLong())) {
                    // The lamp's own brightness, which is also its reach: vanilla decays one level per block,
                    // so a level 12 light stops at twelve cells and a level 15 one at fifteen. This used to be
                    // the constant 15, which made every lamp on this model burn at full strength and reach
                    // fifteen blocks whatever its page said -- the whole lit area came out brighter than the
                    // same lamp on the ray model, and brighter than the vanilla torch beside it. The page's
                    // brightness is now the peak *and* the reach, exactly as it is for vanilla block light.
                    LightFields.slot(fieldKey,fieldLevel,owner,
                            (int)Math.round(Math.max(1.0,Math.min(15.0,snapshot.light.brightness()))),
                            ExtraLights.pocket(fieldKey),IrisMuzzleBridge.fieldSlots(),i);
                } else {
                    LightFields.clearSlot(IrisMuzzleBridge.fieldSlots(),i);
                }
            } else {
                LightFields.clearSlot(IrisMuzzleBridge.fieldSlots(),i);
            }
        }
        LightFields.retain(fieldKeys(world));
        LightFields.tick(fieldLevel);
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
    /**
     * How far this light's cloud term reaches, in blocks; 0 means it does not light the clouds at all.
     *
     * <p>These defaults are the agreed design (CLOUD-LIGHTING.zh-CN.md): a muzzle flash, tracer or
     * explosion does light the clouds and its reach is its own light radius, so "the light reaches here"
     * and "the cloud lights here" are one statement; a registered lamp and a lamp a player carries do not
     * (lamps are the most numerous lights in a scene and they sit under the clouds anyway, which is why a
     * lamp's reach is a separate, default-zero setting); and a spotlight only when the client asks.
     */
    private static double cloudReachFor(String key,AreaSnapshot snapshot) {
        if(key==null||!ExtraConfig.CLOUDS_ENABLED.get()) return 0.0;
        double scale=ExtraConfig.CLOUDS_REACH_SCALE.get();
        // A carried lamp is a beam: it participates under the same switch as a searchlight, and whether it
        // actually lights a cloud is decided by its own range in the shader (shape.x), which is exactly
        // the "only if it reaches" the user asked for. The value returned here is just the mask bit.
        if(isHeld(key)||isPeer(key)) return ExtraConfig.CLOUDS_SPOTLIGHTS.get()?1.0:0.0;
        if(isSpotlight(key)) return ExtraConfig.CLOUDS_SPOTLIGHTS.get()?sphereReach(snapshot)*scale:0.0;
        if(isStaticSource(key)) {
            // The lamp's own page, when it has one, and the global setting when it does not. Zero on the
            // page is the same "no opinion" the global is: it is not "reach zero", because the lamp's
            // owner is answering a question about a cloud deck 140 blocks above them, and the client's
            // own setting is the side that knows what a cloud term costs on this machine.
            int own=snapshot.cloudReach;
            double fallback=ExtraConfig.CLOUDS_SPHERE_REACH.get()*scale;
            if(own==AreaSnapshot.NO_CLOUD_OPINION||own<=0) return fallback;
            return own*scale;
        }
        // A tracer or a muzzle flash: the light is only a few blocks wide on the ground, and a patch
        // derived from that is a small ball -- so these two classes can name their cloud glow in blocks
        // instead (clouds.tracerGlow / blastGlow), for the "the shell spreads a wide soft light over the
        // deck" look. The value is returned NEGATED: a negative reach is the flag that tells the shader
        // "let the patch fill this gate", the same sign-carries-a-flag idiom AreaLight already uses for a
        // beam's range. See slo_cloud_slot's sphere branch.
        if(ExtraConfig.CLOUDS_DYNAMIC.get()) {
            double glow=key.startsWith("tracer/")
                    ?ExtraConfig.CLOUDS_TRACER_GLOW.get():ExtraConfig.CLOUDS_BLAST_GLOW.get();
            if(glow>0.0) return -glow;
            return sphereReach(snapshot)*scale;
        }
        return 0.0;
    }
    /** The single radius that approximates a light's reach -- the same average the shader's shape uses. */
    private static double sphereReach(AreaSnapshot snapshot) {
        double ex=(snapshot.bounds.maxX-snapshot.bounds.minX)*0.5;
        double ey=(snapshot.bounds.maxY-snapshot.bounds.minY)*0.5;
        double ez=(snapshot.bounds.maxZ-snapshot.bounds.minZ)*0.5;
        return (ex+ey+ez)/3.0;
    }
    /**
     * For the debug line: which slots light clouds and how far each reach is.
     *
     * <p>A trailing {@code *} means that slot asked for a glow in blocks (a tracer or a blast), which also
     * makes its patch fill the gate; a plain number is the reach the light's own size and the player's
     * reachScale produced.
     */
    private static String describeCloudReach() {
        StringBuilder text=new StringBuilder();
        for(int i=0;i<cloudReach.length;i++) {
            if((cloudMask&(1<<i))==0) continue;
            text.append(' ').append(i).append(':').append(Math.round(Math.abs(cloudReach[i])));
            if(cloudReach[i]<0.0F) text.append('*');
        }
        return text.length()==0?"none":text.toString();
    }
    private static boolean canCast(String key,AreaSnapshot snapshot) {
        // The rule, first and unconditional: a light on the vanilla light field does not cast a screen-space shadow.
        // A field already knows what it reached, because it spread there, and the shader's field branch returns before
        // the march -- so a bit set for such a light is spent on nothing, out of a budget shared with the ray lights
        // that would use it. This used to sit after the held and spotlight branches, which made it a property of some
        // lamps rather than the rule it is; the log showed two field lamps holding slots that changed nothing.
        if(snapshot!=null&&snapshot.flood) return false;
        if(key!=null&&key.startsWith("flare/")) return ExtraConfig.FLARE_SHADOWS.get();
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
                               java.util.Set<AreaSnapshot> allowed,java.util.Set<AreaSnapshot> reserved,
                               java.util.Map<AreaSnapshot,String> keys) {
        int mask=0;
        for(int i=0;i<selected.size();i++) {
            AreaSnapshot snapshot=selected.get(i);
            if(reserved.contains(snapshot)) mask|=1<<i;
        }
        int slots=ShadowBudget.effective(ExtraConfig.GPU_SHADOW_COUNT.get());
        if(slots<=0){shadowHolders.clear();return mask;}
        var candidates=new ArrayList<AreaSnapshot>();
        for(AreaSnapshot snapshot:selected) {
            if(allowed.contains(snapshot)&&!reserved.contains(snapshot)) candidates.add(snapshot);
        }
        if(candidates.isEmpty()){shadowHolders.clear();return mask;}
        // A slot is held by its light's key, not by the snapshot: the world map is rebuilt every tick, so
        // this tick's AreaSnapshot is never the same object as the last tick's, and an identity set would
        // have matched nothing and changed nothing.
        var candidateKeys=new java.util.LinkedHashSet<String>();
        for(AreaSnapshot snapshot:candidates) {
            String key=keys.get(snapshot);
            if(key!=null) candidateKeys.add(key);
        }
        // Sticky: a light that cast last time keeps its slot while it is still a candidate. The ranking
        // below is a function of where the camera is standing, so walking through a room reorders it
        // constantly, and every reorder used to hand a slot to a different light -- which is a brightness
        // pop at both of them, several times a second. A slot is now only given up when its light leaves
        // the buffer, stops being allowed, or the frame rate takes the slot away.
        shadowHolders.retainAll(candidateKeys);
        double[] planes=frustum(camera);
        if(shadowHolders.size()>slots) {
            var held=new ArrayList<AreaSnapshot>();
            for(AreaSnapshot snapshot:candidates) if(shadowHolders.contains(keys.get(snapshot))) held.add(snapshot);
            held.sort(Comparator.comparingDouble(
                    (AreaSnapshot s) -> LightScreenWeight.of(s.light,camera.x,camera.y,camera.z,planes)));
            shadowHolders.clear();
            for(int i=0;i<slots;i++) shadowHolders.add(keys.get(held.get(i)));
        }
        if(shadowHolders.size()<slots) {
            var fresh=new ArrayList<AreaSnapshot>();
            for(AreaSnapshot snapshot:candidates) if(!shadowHolders.contains(keys.get(snapshot))) fresh.add(snapshot);
            fresh.sort(Comparator.comparingDouble(
                    (AreaSnapshot s) -> -LightScreenWeight.of(s.light,camera.x,camera.y,camera.z,planes)));
            for(AreaSnapshot snapshot:fresh) {
                if(shadowHolders.size()>=slots) break;
                String key=keys.get(snapshot);
                if(key!=null) shadowHolders.add(key);
            }
        }
        for(int i=0;i<selected.size();i++) {
            if(shadowHolders.contains(keys.get(selected.get(i)))) mask|=1<<i;
        }
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
    /**
     * Which lights hold a shadow slot, by their world-map key.
     *
     * <p>Kept from one frame to the next so the ranking cannot hand the same slot back and forth: the
     * ranking depends on where the camera is, so it reorders as the player walks, and every reorder was a
     * brightness pop at two lights. See {@link #maskFor}.
     */
    private static final java.util.Set<String> shadowHolders=new java.util.LinkedHashSet<>();
    /**
     * Which lights hold a slot in the shader's light buffer, by their world-map key.
     *
     * <p>The buffer holds sixteen lights and a lit base offers far more candidates than that, so something
     * has to be left out. When that choice was re-made from scratch on every rebuild, ordered by distance
     * from the camera, walking towards a lamp was enough to change the tail of the list -- and because the
     * classes are concatenated with the registered lamps last, the lamps were the ones dropped. The area
     * they lit went black and came back, several times a second, faster the faster the player moved. A slot
     * is now held by its light's key until that light leaves the candidate list or the buffer shrinks.
     */
    private static final java.util.Set<String> bufferHolders=new java.util.LinkedHashSet<>();
    /** Per-light penumbra radius in blocks, parallel to the last GPU list; 0 is a hard edge. */
    private static double[] lightSoftness=new double[0];
    /** Per-light tap ceiling, parallel to the last GPU list; 0 means "take the client's". */
    private static int[] lightSteps=new int[0];
    /** Which lights light the clouds (bit i, same indexing as the shadow mask) and each one's reach. */
    private static int cloudMask;
    private static float[] cloudReach=new float[0];
    /**
     * Each light's style word, parallel to the last GPU list: which faces a lamp emits through and
     * whether it is lit like vanilla block light. Packed by the bridge that also publishes it, so the
     * bits exist in exactly one place.
     */
    private static int[] lightStyle=new int[0];
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
            boolean explosion = f.kind() == dev.sablelightoverhaul.FlashPayload.EXPLOSION_LIGHT;
            boolean big = f.kind() == 0 || explosion;
            boolean blast=big && age<f.layout().muzzleHoldTicks();
            // Three kinds, three lights: 0 is the big cannon (blast then smoke), 1 the autocannon family, 2 the tank
            // cannon, which has its own profile rather than borrowing the autocannon's.
            var profile=big ? (blast?MuzzleClientConfig.BLAST_PROFILE:MuzzleClientConfig.SMOKE_PROFILE)
                    : (f.kind()==2?MuzzleClientConfig.TANK_PROFILE:MuzzleClientConfig.AUTO_PROFILE);
            long stageAge=big && !blast ? age-f.layout().muzzleHoldTicks() : age;
            float[] slots=f.emitter().profile();
            double strength=profile.curve(slots).brightness(stageAge);
            if(strength<=0) continue;
            double x=big?f.x():f.emitter().x(), y=big?f.y():f.emitter().y(), z=big?f.z():f.emitter().z();
            if(big && !explosion && age>=f.layout().muzzleHoldTicks()) {
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
            if(big&&blast) size*=MuzzleClientConfig.BIG_BLAST_RANGE_GAIN.get();
            var light=profile.light(x,y,z,f.dx(),f.dy(),f.dz(),size,strength,slots);
            world.put(e.getKey(),new AreaSnapshot(light,AreaSnapshot.bounds(light),null).withRadiance(
                big&&blast?MuzzleClientConfig.BIG_BLAST_INTENSITY.get().floatValue():1));
            if(world.size()>=MuzzleClientConfig.ADV_MAX.get()) break;
        }
        ExtraLights.append(level,world,includeSources);
        DiscoBallLights.append(level,world);
        // The player's own flashlight is a light like any other here: one entry per tick, no packets.
        // Held lights are registered first in ExtraLights, reserving beam slots for the local torch.
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
        boolean reselect=GameClock.due(now,lastReselect,ExtraConfig.LIGHT_RESELECT_TICKS.get());
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
    /**
     * Which priority class a light belongs to, as a rank where higher wins a scarce slot.
     *
     * <p>This is where the specification's {@code LightBudget.priority} is honoured, and it is honoured by
     * <em>class</em> because the class is what the client can see: the payload carries no priority field, so
     * a declaration cannot travel with a light yet. This mod's own lights are the high class -- the muzzle
     * flash, the explosion, a shell in flight, and the torch in the player's own hand are what the pool
     * exists for -- while lamps, spotlights and other players' lights are the middle class and the disco
     * ball is the low one, so decoration is what gives way first. A wire field is what would let a third
     * party declare its own; until then a third party's light arrives through one of these classes or not
     * at all.
     */
    private static int priorityRankOf(String key) {
        dev.sablelightoverhaul.api.EffectApi.Priority priority;
        if(key.startsWith("shot/")||key.startsWith("tracer/")||isHeld(key)) {
            priority=dev.sablelightoverhaul.api.EffectApi.Priority.HIGH;
        } else if(isPeer(key)||isStaticSource(key)) {
            priority=dev.sablelightoverhaul.api.EffectApi.Priority.NORMAL;
        } else {
            priority=dev.sablelightoverhaul.api.EffectApi.Priority.LOW;
        }
        // The enum is declared weakest first, so its ordinal is the rank. A test pins that order, because
        // reordering the declaration would otherwise invert every choice made from it without a warning.
        return priority.ordinal();
    }
    private static double distanceSquared(AreaSnapshot snapshot,Vec3 camera) {
        var light=snapshot.light;
        double dx=light.x()-camera.x, dy=light.y()-camera.y, dz=light.z()-camera.z;
        return dx*dx+dy*dy+dz*dz;
    }
    private static boolean isStaticSource(String key) {
        return key.startsWith("staticGround/") || key.startsWith("staticShip/") || key.startsWith("spot/")
                || key.startsWith("entityPoint/");
    }
    /**
     * Whether this light belongs to something somebody configured, and therefore has a colour row of its own.
     *
     * <p>These take their colour from the palette in {@code SourceColor}; everything else -- muzzle flashes,
     * tracers, shots, plumes, explosion light -- carries no page and keeps the client's configured overlay
     * colour. The two used to be indistinguishable because the palette's first slot <em>was</em> that
     * configured colour, so a light with no opinion and a light that asked for the default landed on the same
     * value. Now that a lamp's default is the vanilla tint, sharing the slot would repaint every explosion, so
     * the split has to be made here instead of being implied by the palette.
     */
    static boolean isPaged(String key) {
        // A disco ball's beams carry their colours from the ball's own list, so they are paged too: without
        // this they took the client's configured overlay colour and every beam came out the same, which is
        // what "neither the index nor the hex changes anything" was.
        return key != null && (isStaticSource(key) || isHeld(key) || isPeer(key) || key.startsWith("disco/") || key.startsWith("flare/"));
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
    public static void clear() {
        lastLog = lastReselect = Long.MIN_VALUE;
        SELECTED.clear();
        bufferHolders.clear();
    }
}
