package dev.cbcmuzzlelight.lighting;

import dev.cbcmuzzlelight.MuzzleLight;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Runs the generators.
 *
 * <p>Discovery is a scan of the physics bodies' own block entities rather than a registry: a
 * generator is an ordinary block of ours, so blueprint copies, assembly and disassembly are all
 * correct without bookkeeping, and nothing can go stale. Bodies are rescanned rarely (blocks do not
 * teleport), while emission runs on its own shorter interval.
 *
 * <p>Motion comes from the change in the emission point's own world position, not from the physics
 * API: that works the same for any body, needs no velocity accessor, and is exactly what the
 * effects are keyed on.
 */
@EventBusSubscriber(modid=MuzzleLight.ID)
public final class GeneratorServer {
 private static final Map<ResourceKey<Level>,Bodies> LEVELS=new HashMap<>();
 /** One discovered generator: its body anchor and the block entity that holds its values. */
 record Found(SourceAnchor anchor,GeneratorBlockEntity generator){}
 private static final class Bodies {
  List<Found> found=List.of();
  long lastScan=Long.MIN_VALUE;
  long lastLog=Long.MIN_VALUE;
  /** Last world position per generator, keyed by its position in the host level. */
  final Map<Long,Vec3> previous=new HashMap<>();
 }
 private GeneratorServer(){}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){
  if(!(event.getLevel() instanceof ServerLevel level))return;
  Bodies bodies=LEVELS.computeIfAbsent(level.dimension(),key->new Bodies());
  if(!LightingCommon.GEN_ENABLED.get()){
   if(!bodies.found.isEmpty()){bodies.found=List.of();bodies.previous.clear();}
   return;
  }
  long now=level.getGameTime();
  if(bodies.lastScan==Long.MIN_VALUE||now-bodies.lastScan>=LightingCommon.GEN_SCAN_TICKS.get()){
   bodies.lastScan=now;
   bodies.found=scan(level);
   Set<Long> live=new HashSet<>();
   for(Found found:bodies.found){
    BlockPos pos=found.anchor().resolve(level);
    if(pos!=null)live.add(pos.asLong());
   }
   bodies.previous.keySet().retainAll(live);
  }
  if(now%LightingCommon.GEN_EMIT_TICKS.get()!=0)return;
  double cull=LightingCommon.GEN_CULL.get();
  double interval=LightingCommon.GEN_EMIT_TICKS.get();
  int budget=LightingCommon.GEN_BUDGET.get();
  boolean debug=LightingCommon.GEN_DEBUG.get();
  int spawned=0;
  List<String> blocked=debug?new ArrayList<>():List.of();
  for(Found found:bodies.found){
   GeneratorBlockEntity generator=found.generator();
   if(generator.isRemoved()||!generator.flag(GeneratorFields.ENABLED)){
    if(debug)blocked.add("#"+generator.getBlockPos().toShortString()+" disabled");
    continue;
   }
   BlockPos pos=found.anchor().resolve(level);
   if(pos==null){if(debug)blocked.add("#"+generator.getBlockPos().toShortString()+" body unloaded");continue;}
   boolean smoke=generator.kind()==GeneratorKind.SMOKE;
   Vec3 local=Vec3.atCenterOf(pos).add(generator.offset());
   // Only the vent has a height of its own: dust has to land on whatever the ground search finds.
   if(smoke)local=local.add(0,generator.number("offsetY"),0);
   // What the anchor answers is body space, which is not where the world, the ground or the player
   // are. CBC's own transform is what the muzzle effects already use to get from there to here, so
   // the ground search and the viewer check finally agree with reality.
   Vec3 point=world(level,local);
   if(point==null){if(debug)blocked.add("#"+generator.getBlockPos().toShortString()+" no pose");continue;}
   Vec3 previous=bodies.previous.put(pos.asLong(),point);
   Vec3 motion=previous==null?Vec3.ZERO:point.subtract(previous).scale(1.0/interval);
   // The jet is aimed in body space and then carried into the world, so a vent keeps pointing out of
   // the same pipe however the hull is turned.
   Vec3 direction=Vec3.ZERO;
   if(smoke){
    double yaw=Math.toRadians(generator.number("directionYaw")),pitch=Math.toRadians(generator.number("directionPitch"));
    double flat=Math.cos(pitch);
    Vec3 localDir=new Vec3(-Math.sin(yaw)*flat,Math.sin(pitch),Math.cos(yaw)*flat);
    Vec3 turned=world(level,local.add(localDir)).subtract(point);
    direction=turned.lengthSqr()<1.0E-8?localDir:turned.normalize();
   }
   List<ServerPlayer> viewers=viewers(level,point,cull);
   if(viewers.isEmpty()){if(debug)blocked.add("#"+generator.getBlockPos().toShortString()+" no viewer within "+cull);continue;}
   int before=budget;
   int made=smoke
     ?emitSmoke(level,generator,pos,point,direction,viewers,budget)
     :emitDust(level,generator,point,motion,viewers,budget);
   spawned+=made;
   budget-=made;
   if(debug&&made==0)blocked.add("#"+generator.getBlockPos().toShortString()+" "+reason(level,generator,pos,point,motion));
   if(budget<=0)break;
  }
  // One line every few seconds: enough to tell "found nothing" from "found it but a condition
  // blocks it", which is otherwise invisible from inside the game.
  // The first line is forced: now - Long.MIN_VALUE overflows to a negative number, so a plain
  // "elapsed >= 100" test would silently never print and the diagnostic would look disabled.
  if(debug&&(bodies.lastLog==Long.MIN_VALUE||now-bodies.lastLog>=100)){
   bodies.lastLog=now;
   var where=new StringBuilder();
   for(int i=0;i<Math.min(3,bodies.found.size());i++){
    var one=bodies.found.get(i);
    BlockPos p=one.anchor().resolve(level);
    Vec3 point=p==null?null:world(level,Vec3.atCenterOf(p).add(one.generator().offset()));
    where.append((char)32).append(one.generator().kind().id()).append((char)64).append(point==null?"unresolved":point.toString());
   }
   com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light generators: found={}{}, spawned={}{}",bodies.found.size(),where.toString(),spawned,blocked.isEmpty()?"":" blocked="+blocked);
  }
 }
 /**
  * The first real surface below {@code from}, in world space.
  *
  * <p>An empty collision context, so a passing entity is never taken for ground. Hits on the body
  * itself (any block inside a Sable sub-level) and on our own emitters are stepped over and the
  * search continues underneath them: without that, an emitter buried in a hull finds the hull, and
  * its dust is born inside the vehicle where nobody can see it.
  *
  * <p>Null when nothing was hit within {@code depth}.
  */
 public static BlockHitResult groundBelow(ServerLevel level,Vec3 from,double depth){
  Vec3 cursor=from;
  for(int step=0;step<8;step++){
   BlockHitResult hit=level.clip(new ClipContext(cursor,cursor.subtract(0,depth,0),
     ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
   if(hit.getType()!=HitResult.Type.BLOCK)return null;
   if(!owned(level,hit.getBlockPos()))return hit;
   cursor=hit.getLocation().subtract(0,0.05,0);
  }
  return null;
 }
 /** Body space to world space, through the same transform the muzzle effects use. */
 static Vec3 world(ServerLevel level,Vec3 bodyPoint){
  return rbasamoyai.createbigcannons.CBCCompatTransformers.transformVec3(level,bodyPoint);
 }
 /** Whether a block belongs to the physics body rather than to the world. */
 static boolean owned(ServerLevel level,BlockPos pos){
  if(level.getBlockState(pos).getBlock() instanceof GeneratorBlock)return true;
  return ModList.get().isLoaded("sable")&&SableGenerators.owned(level,pos);
 }
 /**
  * Why one generator produced nothing this step, in the terms its own page uses. Only called while
  * diagnosing, so it may re-read values the emitter already read.
  */
 private static String reason(ServerLevel level,GeneratorBlockEntity generator,BlockPos pos,Vec3 point,Vec3 motion){
  boolean force=generator.flag(GeneratorFields.FORCE);
  if(generator.kind()==GeneratorKind.SMOKE){
   int signal=force?15:signal(level,pos);
   return signal<=0?"no redstone (or turn on continuous emission in the page)":"redstone "+signal+" but off this step";
  }
  double speed=Math.hypot(motion.x,motion.z);
  if(!force&&speed<generator.number("minSpeed"))return String.format(java.util.Locale.ROOT,"speed %.3f below minSpeed %.2f (or turn on continuous emission)",speed,generator.number("minSpeed"));
  if(groundBelow(level,point,generator.number("searchDepth"))==null)return "no ground within "+generator.number("searchDepth")+" blocks below "+point;
  return "parsed particle or count was zero";
 }
 /** Every generator of ours inside this level's physics bodies. */
 private static List<Found> scan(ServerLevel level){
  if(!ModList.get().isLoaded("sable"))return List.of();
  return SableGenerators.scan(level);
 }
 /**
  * Apply one edit sent by a configuration page.
  *
  * <p>The value is validated here rather than on the page, so a malformed or out-of-range edit
  * cannot reach the block: {@link GeneratorValues} rejects unknown fields and clamps numbers.
  */
 public static void applyEdit(net.minecraft.world.entity.player.Player player,GeneratorEditPayload payload){
  if(!(player.level() instanceof ServerLevel level))return;
  // The page is only reachable at the block, so anything far away is not a real edit.
  if(player.distanceToSqr(payload.pos().getCenter())>64*64||!player.mayBuild())return;
  // Three pages send through this one message. Whichever block is actually there answers; the calls
  // are tried in turn because the payload carries a position, not a block type.
  if(level.getBlockEntity(payload.pos()) instanceof GeneratorBlockEntity generator){
   switch(payload.kind()){
    case GeneratorEditPayload.NUMBER->generator.setNumber(payload.field(),payload.number());
    case GeneratorEditPayload.TOGGLE->generator.setFlag(payload.field(),payload.number()!=0);
    default->generator.setText(payload.field(),payload.text());
   }
   return;
  }
  if(applyDisco(player,payload))return;
  applySpotlight(player,payload);
 }
 /** The spotlight page's own fields; only the ones the lamp owns are taken. */
 private static void applySpotlight(net.minecraft.world.entity.player.Player player,GeneratorEditPayload payload){
  if(!(player.level() instanceof ServerLevel level))return;
  if(!(level.getBlockEntity(payload.pos()) instanceof SpotlightBlockEntity lamp))return;
  if(!player.mayBuild())return;
  switch(payload.kind()){
   case GeneratorEditPayload.NUMBER,GeneratorEditPayload.TOGGLE->lamp.setNumber(payload.field(),payload.number());
   default->{/* The lamp has no text fields: a stray message changes nothing. */}
  }
 }
 /**
  * Ground dust: find the surface below the emission point, take that block's own colour, and kick
  * the dust outwards and backwards. Moving bodies only, unless the block was set to emit
  * continuously for tuning.
  */
 private static int emitDust(ServerLevel level,GeneratorBlockEntity generator,Vec3 point,Vec3 motion,List<ServerPlayer> viewers,int budget){
  boolean force=generator.flag(GeneratorFields.FORCE);
  double speed=Math.hypot(motion.x,motion.z);
  if(!force&&speed<generator.number("minSpeed"))return 0;
  double rate=generator.number("rate");
  int count=force?Math.max(1,(int)Math.round(rate)):(int)Math.min(generator.number("maxPerScan"),Math.round(speed*rate));
  count=Math.min(count,budget);
  if(count<=0)return 0;
  ParticleOptions options=options(level,generator);
  if(options==null)return 0;
  // Terrain only: a passing entity must not be mistaken for the ground, and the blocks of the body
  // the emitter rides on are stepped over, so dust lands on the world under the hull, not inside it.
  BlockHitResult hit=groundBelow(level,point,generator.number("searchDepth"));
  if(hit==null&&!force)return 0;
  Vec3 ground=hit==null?point:new Vec3(hit.getLocation().x,hit.getLocation().y+0.05,hit.getLocation().z);
  if(generator.flag("tintFromBlock")&&hit!=null){
   options=ParticleSpec.retint(options,mapColor(level,hit.getBlockPos()));
  }
  double upMin=generator.number("upSpeedMin"),upMax=generator.number("upSpeedMax");
  double scatter=generator.number("scatter");
  double sizeMin=generator.number("sizeMin"),sizeMax=generator.number("sizeMax");
  double stretchMin=generator.number("stretchMin"),stretchMax=generator.number("stretchMax");
  // Straight up, with nothing sideways: track dust is kicked off the ground by the tracks, so it
  // rises and falls where the track was. The vertical speed is rolled in a small range so a patch of
  // dust has some clouds higher than others, and the whole patch is scattered over 1.5 blocks so it
  // reads as an area being stirred instead of one point puffing.
  for(int i=0;i<count;i++){
   ParticleSpec.spawn(level,viewers,particle(level,options,sizeMin,sizeMax,stretchMin,stretchMax),
     ground.x+(level.random.nextDouble()-0.5)*2.0*scatter,ground.y,ground.z+(level.random.nextDouble()-0.5)*2.0*scatter,0,
     0.0,upMin+level.random.nextDouble()*Math.max(0,upMax-upMin),0.0,1.0);
  }
  return count;
 }
 /** Vent smoke: redstone strength picks the cadence, and the plume is left behind in world space. */
 private static int emitSmoke(ServerLevel level,GeneratorBlockEntity generator,BlockPos pos,Vec3 point,Vec3 direction,List<ServerPlayer> viewers,int budget){
  boolean force=generator.flag(GeneratorFields.FORCE);
  int signal=force?15:signal(level,pos);
  if(signal<=0)return 0;
  int interval=(int)Math.round(lerp(generator.number("intervalAtMinSignal"),generator.number("intervalAtMaxSignal"),signal/15.0));
  if(!force&&level.getGameTime()%Math.max(1,interval)!=0)return 0;
  int count=Math.min((int)Math.round(lerp(generator.number("countAtMinSignal"),generator.number("countAtMaxSignal"),signal/15.0)),budget);
  if(count<=0)return 0;
  ParticleOptions options=options(level,generator);
  if(options==null)return 0;
  // The jet carries nothing of the body's motion: a particle never follows the vehicle, so
  // inheriting could only smear the cloud away from the vent. The direction arrives already turned
  // from body space into the world, so a pipe on a turning hull keeps pointing out of the same pipe.
  double speed=generator.number("speed");
  for(int i=0;i<count;i++){
   ParticleSpec.spawn(level,viewers,options,
     point.x+(level.random.nextDouble()-0.5)*0.4,point.y,point.z+(level.random.nextDouble()-0.5)*0.4,0,
     direction.x*speed,direction.y*speed,direction.z*speed,1.0);
  }
  return count;
 }
 /** The configured particle, falling back to the config default when the stored string stops decoding. */
 private static ParticleOptions options(ServerLevel level,GeneratorBlockEntity generator){
  ParticleOptions options=ParticleSpec.parse(level,generator.text(GeneratorFields.PARTICLE));
  if(options==null)options=ParticleSpec.parse(level,GeneratorFields.defaultText(generator.kind(),GeneratorFields.PARTICLE));
  if(options instanceof GroundDustParticleData dust){
   // Our own cloud reads the page's lifetime range; size and stretch are rolled per particle, so
   // those fields stay live even though a spec string cannot be edited per block.
   double min=generator.number("lifetimeMin"),max=generator.number("lifetimeMax");
   int life=(int)Math.round(min+level.random.nextDouble()*Math.max(0,max-min));
   options=dust.with(dust.size(),life,dust.stretch(),LightingCommon.DUST_DAMPING.get().floatValue(),LightingCommon.DUST_GRAVITY.get().floatValue(),LightingCommon.DUST_ALPHA.get().floatValue());
  }
  return options;
 }
 /** One cloud with its size and stretch rolled inside the range the page asks for. */
 private static ParticleOptions particle(ServerLevel level,ParticleOptions base,double sizeMin,double sizeMax,double stretchMin,double stretchMax){
  if(!(base instanceof GroundDustParticleData dust))return base;
  double size=sizeMin+level.random.nextDouble()*Math.max(0,sizeMax-sizeMin);
  double stretch=stretchMin+level.random.nextDouble()*Math.max(0,stretchMax-stretchMin);
  return dust.with((float)size,dust.lifetime(),(float)stretch,dust.damping(),dust.gravity(),dust.alpha());
 }
 private static List<ServerPlayer> viewers(ServerLevel level,Vec3 point,double cull){
  double limit=cull*cull;
  List<ServerPlayer> result=new ArrayList<>();
  for(ServerPlayer player:level.players())if(player.distanceToSqr(point)<=limit)result.add(player);
  return result;
 }
 /** Strongest signal entering the block through any face, never loading a chunk just to read it. */
 private static int signal(ServerLevel level,BlockPos pos){
  int strongest=0;
  for(Direction face:Direction.values()){
   BlockPos neighbour=pos.relative(face);
   if(!level.hasChunkAt(neighbour))continue;
   strongest=Math.max(strongest,level.getSignal(neighbour,face));
  }
  return strongest;
 }
 private static int mapColor(ServerLevel level,BlockPos pos){
  BlockState state=level.getBlockState(pos);
  return state.getMapColor(level,pos).col;
 }
 private static double lerp(double from,double to,double t){return from+(to-from)*t;}
 /** The disco ball reads the generator page's own edit messages; only the fields it owns are taken. */
 private static boolean applyDisco(net.minecraft.world.entity.player.Player player,GeneratorEditPayload payload){
  if(!(player.level() instanceof ServerLevel level))return false;
  if(!(level.getBlockEntity(payload.pos()) instanceof DiscoBallBlockEntity settings))return false;
  if(player.distanceToSqr(payload.pos().getCenter())>64*64||!player.mayBuild())return true;
  switch(payload.kind()){
   case GeneratorEditPayload.NUMBER,GeneratorEditPayload.TOGGLE->settings.setNumber(payload.field(),payload.number());
   default->settings.setText(payload.field(),payload.text());
  }
  return true;
 }
}