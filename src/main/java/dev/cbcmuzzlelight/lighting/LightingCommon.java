package dev.cbcmuzzlelight.lighting;
import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class LightingCommon {
 public static final ModConfigSpec SPEC;
 public static final ModConfigSpec.IntValue MIN,MAX,STEP;
 /** Colour overrides by block id, {@code namespace:block=colour}; wins over the name scan. */
 public static final ModConfigSpec.ConfigValue<List<? extends String>> OVERRIDES;
 /** Whether a block id's own name may decide the colour at all. */
 public static final ModConfigSpec.BooleanValue NAME_SCAN;
 // Generators: global limits, plus the defaults every newly placed generator starts from.
 public static final ModConfigSpec.BooleanValue GEN_ENABLED;
 public static final ModConfigSpec.IntValue GEN_CULL,GEN_SCAN_TICKS,GEN_EMIT_TICKS,GEN_BUDGET;
 /** Says why a generator produced nothing; turn off once tuning is done. */
 public static final ModConfigSpec.BooleanValue GEN_DEBUG;
 /**
  * Sends this mod's particles with the long-distance flag, so a shot stays visible past vanilla's
  * 32 blocks. It costs bandwidth, which is why it can be switched off.
  */
 public static final ModConfigSpec.BooleanValue PARTICLE_FORCE;

 public static final ModConfigSpec.DoubleValue DUST_RATE,DUST_MIN_SPEED,DUST_SIZE_MIN,DUST_SIZE_MAX,DUST_STRETCH_MIN,DUST_STRETCH_MAX,DUST_UP_MIN,DUST_UP_MAX,DUST_DAMPING,DUST_GRAVITY,DUST_ALPHA,DUST_SCATTER,SMOKE_RISE,SMOKE_INERTIA,SMOKE_SIZE;
 /** The vent jet: where it points and how fast it leaves. It never inherits the body's motion. */
 public static final ModConfigSpec.DoubleValue SMOKE_YAW,SMOKE_PITCH,SMOKE_SPEED,SMOKE_OFFSET_Y;
 public static final ModConfigSpec.IntValue DUST_MAX_PER_SCAN,DUST_DEPTH,DUST_LIFE_MIN,DUST_LIFE_MAX,SMOKE_LIFE;
 public static final ModConfigSpec.IntValue SMOKE_INTERVAL_MAX,SMOKE_INTERVAL_MIN,SMOKE_COUNT_MAX,SMOKE_COUNT_MIN;
 public static final ModConfigSpec.BooleanValue DUST_TINT;
 // Tank cannon: one group per muzzle effect, all of it meant to be tuned by firing the gun.
 public static final ModConfigSpec.BooleanValue CANNON_ENABLED,CANNON_RECOIL_ENABLED,EXPL_ENABLED,DISCO_ENABLED,TRAIL_FEED_ENABLED;
 public static final ModConfigSpec.IntValue CANNON_CULL,CANNON_BUDGET;
 public static final ModConfigSpec.ConfigValue<String> DISCO_COLORS;
 public static final ModConfigSpec.IntValue CANNON_FLAME_COUNT,CANNON_SPARK_BURSTS;
 public static final ModConfigSpec.IntValue CANNON_GROUND_COUNT,CANNON_GROUND_DISTANCE,CANNON_HULL_COUNT,CANNON_GROUND_LIFETIME,CANNON_GROUND_COLOR,CANNON_HULL_LIFETIME,CANNON_HULL_COLOR,CANNON_SPARK_COLOR,CANNON_SPARK_LIFETIME,EXPL_MIN_BLOCKS,DISCO_BEAMS,DISCO_RANGE,DISCO_WIDTH,DISCO_FALLOFF,CANNON_RECOIL_STEPS;
 public static final ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> EXPL_YIELD_TIERS;
 /** The two particles a shockwave is made of, written the way {@code /particle} writes them. */
 public static final ModConfigSpec.ConfigValue<String> SHOCKWAVE_PARTICLE,SHOCKWAVE_EMBER_PARTICLE;
 public static final ModConfigSpec.DoubleValue CANNON_FLAME_SPEED,CANNON_FLAME_SIZE,CANNON_PLUME_SCALE;
 public static final ModConfigSpec.DoubleValue CANNON_SPARK_SPEED,CANNON_GROUND_SPEED,CANNON_GROUND_UP;
 public static final ModConfigSpec.DoubleValue CANNON_HULL_RADIUS,CANNON_HULL_SPEED,CANNON_GROUND_RADIUS,CANNON_HULL_SIZE_MIN,CANNON_HULL_SIZE_MAX,CANNON_GROUND_SCATTER,CANNON_HULL_SCATTER,CANNON_GROUND_DAMPING,CANNON_HULL_DAMPING,CANNON_GROUND_GRAVITY,CANNON_HULL_GRAVITY,CANNON_GROUND_ALPHA,CANNON_HULL_ALPHA,CANNON_RECOIL_VELOCITY,CANNON_SPARK_SIZE,CANNON_SPARK_BOUNCE,EXPL_CHARGE,EXPL_SIZE_SCALE,SHOCKWAVE_RANGE,EXPL_CHARGE_TIER,EXPL_SIZE,EXPL_SIZE_TIER,DISCO_SPIN,DISCO_INTENSITY;
 /** Size and horizontal stretch are rolled per particle inside these ranges; the ground range is
  * deliberately the wider of the two, which is what separates moving ground dust from hull dust. */
 public static final ModConfigSpec.DoubleValue CANNON_GROUND_SIZE_MIN,CANNON_GROUND_SIZE_MAX;
 public static final ModConfigSpec.DoubleValue CANNON_GROUND_STRETCH_MIN,CANNON_GROUND_STRETCH_MAX,CANNON_HULL_STRETCH_MIN,CANNON_HULL_STRETCH_MAX;
 public static final ModConfigSpec.BooleanValue CANNON_GROUND_TINT;

 // The mod's own breech puff is emitted in contraption space and drifts at 0.075 blocks a tick;
 // these two knobs put it where the breech actually is and let it move.
 public static final ModConfigSpec.BooleanValue CANNON_BREECH_FIX;
 public static final ModConfigSpec.DoubleValue CANNON_BREECH_SPEED;
 // Voxy/LSS: making a crater visible in the far-distance LOD of clients that have it installed.
 public static final ModConfigSpec.BooleanValue VOXY_ENABLED,VOXY_SAVE_CHUNKS;
 public static final ModConfigSpec.IntValue VOXY_MAX_COLUMNS,VOXY_DEBOUNCE_TICKS,VOXY_MIN_BLOCKS,VOXY_SAVE_INTERVAL_TICKS,VOXY_IMMEDIATE_RANGE;public static final ModConfigSpec.IntValue TRAIL_FEED_RANGE,TRAIL_FRAMES;
 public static final ModConfigSpec.DoubleValue SEND_RANGE;
 /** Defaults for a player's flashlight; each player can be given their own by an operator. */
 public static final ModConfigSpec.IntValue FL_COLOR,FL_RANGE,FL_WIDTH,FL_DECAY,FL_INTENSITY,FL_POINT_RADIUS,FL_POINT_INTENSITY,FL_SYNC_RANGE;

 static {
  var b=new ModConfigSpec.Builder();
  MIN=b.defineInRange("spotlight.minimumDistance",16,1,512);
  MAX=b.defineInRange("spotlight.maximumDistance",512,1,512);
  STEP=b.defineInRange("spotlight.distanceStep",32,1,512);
  OVERRIDES=b.defineListAllowEmpty("colors.overrides",List.of(),()->"create:red_ramp=red",SourceColor::overrideRow);
  NAME_SCAN=b.define("colors.guessFromName",true);

  GEN_ENABLED=b.define("generators.enabled",true);
  GEN_CULL=b.defineInRange("generators.cullDistance",48,8,512);
  GEN_SCAN_TICKS=b.defineInRange("generators.scanBodyIntervalTicks",40,5,1200);
  GEN_EMIT_TICKS=b.defineInRange("generators.emitIntervalTicks",2,1,20);
  GEN_BUDGET=b.defineInRange("generators.particlesPerTick",60,0,2000);
  GEN_DEBUG=b.define("generators.debugLog",true);
  PARTICLE_FORCE=b.comment("Send this mod's particles with the long-distance flag so they are not dropped at 32 blocks; off restores vanilla behaviour.").define("particles.forceLoad",true);

  // Voxy/LSS. A crater only reaches a distant client's LOD if the server tells it to ask again, and
  // LSS ("Voxy Server Side") is what does that; without it installed these settings do nothing at all.
  VOXY_ENABLED=b.comment("After an explosion that destroys blocks, ask Voxy/LSS to re-send the columns it changed, so the crater shows up in the far-distance LOD of clients that run Voxy. Does nothing unless the LSS server mod is installed.").define("voxy.enabled",true);
  VOXY_SAVE_CHUNKS=b.comment("Also mark the changed chunks unsaved and write them out. The server serves loaded chunks from memory, so this is only needed for correctness: a request that arrives after the chunk unloads would otherwise be answered from the stale region file. Costs a main-thread save, which is why it is rate limited.").define("voxy.saveChunks",true);
  VOXY_MAX_COLUMNS=b.comment("Most columns one event may mark. A large blast covers many chunks and every column the client re-asks for is a full column payload, so this is the ceiling on one explosion's cost.").defineInRange("voxy.maxColumnsPerEvent",24,1,512);
  VOXY_DEBOUNCE_TICKS=b.comment("A column is not marked again within this many ticks, so a burst of shells landing in one place is one refresh, not one per shell.").defineInRange("voxy.debounceTicks",40,0,1200);
  VOXY_MIN_BLOCKS=b.comment("Ignore blasts whose reported block list is smaller than this; a grenade is not worth a LOD refresh. Note what the number actually is on a big shell: CBC's list is the blocks the blast reports around itself, which grows with the SOLID material it is buried in (that is how a KubeJS script tells a rock hit from a surface hit from an airburst), not the number it destroys. So this is a shape gate, not a damage gate.").defineInRange("voxy.minimumBlocks",4,1,100000);
  VOXY_SAVE_INTERVAL_TICKS=b.comment("Shortest gap between two rate-limited saves.").defineInRange("voxy.saveIntervalTicks",40,1,1200);
  VOXY_IMMEDIATE_RANGE=b.comment("Players this close to a changed column, in blocks, are told about it at once instead of waiting for LSS's own broadcast (10 s by default). Nobody is ever left out: everyone still gets the normal push, this only decides who hears about it first, so the people who could actually see the blast get their LOD refreshed while the dust is still in the air. 0 turns the immediate tier off.").defineInRange("voxy.immediateRange",512,0,8192);
  SEND_RANGE=b.comment("How far a registered light source is sent to a player, in blocks; 0 sends every source to everyone. The client already drops sources beyond its own activation distance, so sending them only costs bandwidth and a little client work. Force-enabled sources are always sent, because the client is told to keep those at any distance.").defineInRange("sources.sendRange",1024.0,0.0,32768.0);

  // The flashlight: a small point light on the player plus a cone down the barrel of their gaze. These
  // are the values a player starts from; an operator can change any player's own set from its page, and
  // the page's writes are what the light actually reads.
  FL_COLOR=b.comment("Flashlight colour, as the same palette index the lamps use.").defineInRange("flashlight.color",0,0,15);
  // A literal default, not count()/2: reading a config value inside the spec's own static initialiser
  // throws before the file is loaded. Step 3 is 80 blocks on the shipped 16/32/512 table, and
  // FlashlightSettings clamps it to whatever the table actually offers.
  FL_RANGE=b.comment("Flashlight beam length, as a distance step (the page shows the blocks it means; the shipped table makes step 3 equal 80 blocks).").defineInRange("flashlight.range",3,1,32);
  FL_WIDTH=b.comment("Flashlight beam width as a full angle in degrees, 5 (a tight spot) to 90 (a flood). Stored in degrees rather than as a narrow-to-wide signal, so the number in the page is the number that is drawn.").defineInRange("flashlight.width",22,5,90);
  FL_DECAY=b.comment("How fast the beam gives up its brightness along its own length, as a percentage of the reference shape. 100 is the reference: full brightness on the axis, half at half the range, nothing at the end. 300 is a short bright pool, 20 keeps almost the whole beam lit, which is how the searchlights behave.").defineInRange("flashlight.decay",100,20,300);
  FL_INTENSITY=b.comment("Flashlight beam brightness, 0..15.").defineInRange("flashlight.intensity",10,0,15);
  FL_POINT_RADIUS=b.comment("Radius of the small point light carried on the player, in blocks; 0 turns it off. This one falls off with distance, unlike the beam.").defineInRange("flashlight.pointRadius",3,0,24);
  FL_POINT_INTENSITY=b.comment("Brightness of that point light, 0..15.").defineInRange("flashlight.pointIntensity",10,0,15);
  FL_SYNC_RANGE=b.comment("How far away another player's flashlight is still drawn, in blocks; 0 stops the mod sending other players' lamps at all. The default matches vanilla's player tracking range, which is also the limit on what a client can be told to draw: a peer it is not tracking has no position to put the beam at.").defineInRange("flashlight.syncRange",48,0,128);
  // Tracer trails. The client draws them from the shells it is tracking, and vanilla stops tracking a
  // projectile long before the muzzle flash's own 512 blocks, so the server feeds the positions instead.
  TRAIL_FEED_ENABLED=b.comment("Spawn the trail of every tracer shell on the server, so it is visible at any distance instead of stopping where the client stops tracking the shell.").define("trail.feedEnabled",true);
  TRAIL_FEED_RANGE=b.comment("How far a tracer shell's trail is spawned, in blocks. Match it to the muzzle flash's own reach (512) so a shell is lit and trailed over the same distance.").defineInRange("trail.feedRange",512,32,8192);
  TRAIL_FRAMES=b.comment("Trail frames laid along each tick of flight. CBC lays ten for its own shell trail, and one message per shell per player carries all of them, so this is a look knob rather than a bandwidth one.").defineInRange("trail.frames",10,1,64);

  DUST_RATE=b.defineInRange("generators.dust.rate",8.0,0.0,200.0);
  DUST_MAX_PER_SCAN=b.defineInRange("generators.dust.maxPerScan",6,0,200);
  DUST_DEPTH=b.defineInRange("generators.dust.searchDepth",12,1,128);
  DUST_MIN_SPEED=b.defineInRange("generators.dust.minSpeed",0.05,0.0,4.0);
  DUST_LIFE_MIN=b.defineInRange("generators.dust.lifetimeMin",30,1,400);
  DUST_LIFE_MAX=b.defineInRange("generators.dust.lifetimeMax",50,1,400);
  DUST_SIZE_MIN=b.defineInRange("generators.dust.sizeMin",1.6,0.1,16.0);
  DUST_SIZE_MAX=b.defineInRange("generators.dust.sizeMax",3.4,0.1,16.0);
  DUST_STRETCH_MIN=b.defineInRange("generators.dust.stretchMin",1.4,1.0,6.0);
  DUST_STRETCH_MAX=b.defineInRange("generators.dust.stretchMax",2.6,1.0,6.0);
  DUST_UP_MIN=b.defineInRange("generators.dust.upSpeedMin",0.06,0.0,4.0);
  DUST_UP_MAX=b.defineInRange("generators.dust.upSpeedMax",0.16,0.0,4.0);
  // Shared by every kind of dust: a heavy horizontal damping (dust does not glide, it stops almost where it was stirred) and gravity as the only vertical term.
  DUST_DAMPING=b.defineInRange("dust.horizontalDamping",0.75,0.1,1.0);
  DUST_GRAVITY=b.defineInRange("dust.gravity",0.08,0.0,4.0);
  DUST_ALPHA=b.defineInRange("generators.dust.alpha",0.35,0.02,1.0);
  DUST_SCATTER=b.comment("Radius the track dust is scattered over around the spot the ground was found on. Live on the generator page too.").defineInRange("generators.dust.scatter",1.5,0.0,16.0);
  DUST_TINT=b.define("generators.dust.tintFromBlock",true);

  SMOKE_INTERVAL_MAX=b.defineInRange("generators.smoke.intervalAtMaxSignal",2,1,200);
  SMOKE_INTERVAL_MIN=b.defineInRange("generators.smoke.intervalAtMinSignal",10,1,200);
  SMOKE_COUNT_MAX=b.defineInRange("generators.smoke.countAtMaxSignal",2,0,64);
  SMOKE_COUNT_MIN=b.defineInRange("generators.smoke.countAtMinSignal",1,0,64);
  SMOKE_RISE=b.defineInRange("generators.smoke.riseSpeed",0.03,0.0,2.0);
  SMOKE_INERTIA=b.defineInRange("generators.smoke.inertia",0.0,0.0,1.0);
  SMOKE_YAW=b.comment("Where the vent points, in degrees: 0 is south (+Z), 90 is west (-X), 180 north, 270 east.").defineInRange("generators.smoke.directionYaw",0.0,0.0,360.0);
  SMOKE_PITCH=b.comment("Upward tilt of the vent jet, in degrees; 90 is straight up.").defineInRange("generators.smoke.directionPitch",0.0,-90.0,90.0);
  SMOKE_SPEED=b.comment("How fast the cloud leaves the vent, in blocks per tick.").defineInRange("generators.smoke.speed",0.15,0.0,4.0);
  SMOKE_OFFSET_Y=b.comment("Height of the vent relative to the generator block, in blocks. Only the vent may move vertically.").defineInRange("generators.smoke.offsetY",0.0,-16.0,16.0);
  SMOKE_LIFE=b.defineInRange("generators.smoke.lifetime",90,1,600);
  SMOKE_SIZE=b.defineInRange("generators.smoke.size",0.9,0.05,16.0);

  CANNON_ENABLED=b.define("tankCannon.enabled",true);
  CANNON_CULL=b.defineInRange("tankCannon.cullDistance",160,8,1024);
  CANNON_BUDGET=b.defineInRange("tankCannon.particlesPerShot",120,0,4000);

  CANNON_FLAME_COUNT=b.defineInRange("tankCannon.flame.count",3,0,64);
  CANNON_FLAME_SPEED=b.defineInRange("tankCannon.flame.speed",0.325,0.0,8.0);
  CANNON_FLAME_SIZE=b.defineInRange("tankCannon.flame.size",1.6,0.05,16.0);
  CANNON_PLUME_SCALE=b.defineInRange("tankCannon.flame.plumeScale",1.4,0.0,4.0);

  CANNON_SPARK_BURSTS=b.defineInRange("tankCannon.sparks.bursts",4,0,16);
  CANNON_SPARK_SPEED=b.defineInRange("tankCannon.sparks.speed",2.5,0.0,16.0);
  CANNON_GROUND_LIFETIME=b.defineInRange("tankCannon.groundDust.lifetime",60,2,1200);
  CANNON_GROUND_COLOR=b.defineInRange("tankCannon.groundDust.color",0xFFFFFF,0,0xFFFFFF);
  CANNON_GROUND_COUNT=b.defineInRange("tankCannon.groundDust.count",16,0,200);
  CANNON_GROUND_DISTANCE=b.defineInRange("tankCannon.groundDust.searchDistance",24,1,128);
  CANNON_GROUND_SPEED=b.defineInRange("tankCannon.groundDust.speed",1.5,0.0,16.0);
  CANNON_GROUND_UP=b.defineInRange("tankCannon.groundDust.upSpeed",0.5,0.0,8.0);
  CANNON_GROUND_TINT=b.define("tankCannon.groundDust.tintFromBlock",true);
  CANNON_HULL_LIFETIME=b.defineInRange("tankCannon.hullDust.lifetime",40,2,1200);
  CANNON_HULL_COLOR=b.defineInRange("tankCannon.hullDust.color",0xFFFFFF,0,0xFFFFFF);
  CANNON_HULL_SIZE_MIN=b.defineInRange("tankCannon.hullDust.sizeMin",1.4,0.1,16.0);
  CANNON_HULL_SIZE_MAX=b.defineInRange("tankCannon.hullDust.sizeMax",2.6,0.1,16.0);
  // The muzzle sparks are this mod's own particle: the original one is drawn through a custom render type and disappears under shader packs.
  CANNON_SPARK_COLOR=b.defineInRange("tankCannon.sparks.color",0xFFA030,0,0xFFFFFF);
  CANNON_SPARK_SIZE=b.defineInRange("tankCannon.sparks.size",0.16,0.02,4.0);
  CANNON_SPARK_LIFETIME=b.defineInRange("tankCannon.sparks.lifetime",18,2,1200);
  CANNON_SPARK_BOUNCE=b.defineInRange("tankCannon.sparks.bounce",0.45,0.0,1.0);
  // Explosion lights: the size is the block count the blast destroys, the same number and the same
  // three tiers the shockwave script uses, so light and particles always agree on how big it was.
  EXPL_ENABLED=b.define("explosions.enabled",true);
  EXPL_MIN_BLOCKS=b.comment("Smallest block list that counts as an explosion worth a light and a shockwave. The list is the blocks the blast's rays walked through, which grows with the solid material around it; a vanilla TNT blast reports roughly 400 and a creeper far less, so this floor keeps grenades and creepers out while letting anything the size of TNT through, from any mod.").defineInRange("explosions.minimumBlocks",200,1,1000000);
  // The charge, not the block list, decides how big an event this was. CBC's CustomExplosion hands its
  // block_damaging_explosive_power straight to the vanilla Explosion, so radius() is the shell's own
  // number; an explosion carrying none of its own is treated as the smallest charge worth drawing.
  EXPL_YIELD_TIERS=b.comment("Block-damaging explosive power at which each shockwave tier starts, ascending. Six values give six tiers: below the first is tier 1, at or above the last is tier 6.").defineList("explosions.yieldTiers",java.util.List.of(6,12,18,24,32),value->value instanceof Integer);
  EXPL_SIZE_SCALE=b.comment("Radius of the explosion light, as a multiple of the blast's measured mean reach. 1.0 makes the light exactly as wide as the blast actually got; lower it if the light reads as too broad without a shader pack to tone-map it.").defineInRange("explosions.lightSizeScale",1.0,0.05,8.0);
  SHOCKWAVE_RANGE=b.comment("How far a player is sent an explosion's measurements, in blocks. The client draws the shockwave from them, so this is also how far away the wave can be seen.").defineInRange("explosions.shockwaveRange",512.0,8.0,8192.0);
  // The two particles a shockwave is made of, written the way /particle writes them, so anything that
  // works in the command works here. Both are drawn by the client.
  SHOCKWAVE_PARTICLE=b.comment("The shockwave itself: one of these is spawned per frame, spread over the expanding front. Written exactly as /particle writes it, so a bare id or an id with SNBT both work.").define("explosions.shockwaveParticle","minecraft:cloud");
  SHOCKWAVE_EMBER_PARTICLE=b.comment("The embers inside the blast, spawned at the front for the first third of the wave. A particle with a colour of its own, such as minecraft:dust{color:[1.0,0.6,0.2],scale:1.2}, reads as fire; the mod's own cbcmuzzlelight:impact is the other obvious choice.").define("explosions.emberParticle","minecraft:end_rod");
  EXPL_CHARGE=b.defineInRange("explosions.lightCharge",5.0,0.0,64.0);
  EXPL_CHARGE_TIER=b.defineInRange("explosions.lightChargePerTier",3.0,0.0,64.0);
  EXPL_SIZE=b.defineInRange("explosions.lightSize",3.0,0.05,64.0);
  EXPL_SIZE_TIER=b.defineInRange("explosions.lightSizePerTier",3.0,0.0,64.0);
  // Disco ball defaults. They are stored on each ball when it is placed, so tuning them here applies to
  // new balls and the values travel to every client: one show, everybody watching the same one.
  DISCO_ENABLED=b.define("disco.enabled",true);
  DISCO_COLORS=b.define("disco.colors","1,4,11,14");
  DISCO_BEAMS=b.defineInRange("disco.beams",8,1,32);
  DISCO_SPIN=b.defineInRange("disco.spinDegreesPerTick",1.5,0.0,90.0);
  DISCO_RANGE=b.defineInRange("disco.range",32,1,128);
  DISCO_WIDTH=b.defineInRange("disco.width",2,0,15);
  DISCO_INTENSITY=b.defineInRange("disco.intensity",6.0,0.0,15.0);
  // The spot's own falloff, the same five profiles the spotlights offer. The ball has no light of its
  // own any more: the spots are the whole show, so how they fade is a setting rather than a constant.
  DISCO_FALLOFF=b.comment("Disco spot falloff profile, the same five the spotlights have: 0 forced maximum, 1 fast, 2 uniform, 3 slow, 4 dim.").defineInRange("disco.falloff",0,0,4);
  CANNON_GROUND_SCATTER=b.comment("Radius the ground dust is scattered over around each spot it was found on. The cannon scatters wider than the hull dust does.").defineInRange("tankCannon.groundDust.scatter",2.5,0.0,16.0);
  CANNON_HULL_SCATTER=b.defineInRange("tankCannon.hullDust.scatter",1.2,0.0,16.0);
  CANNON_GROUND_DAMPING=b.defineInRange("tankCannon.groundDust.damping",0.75,0.1,1.0);
  CANNON_HULL_DAMPING=b.defineInRange("tankCannon.hullDust.damping",0.75,0.1,1.0);
  CANNON_GROUND_GRAVITY=b.defineInRange("tankCannon.groundDust.gravity",0.06,0.0,4.0);
  CANNON_HULL_GRAVITY=b.defineInRange("tankCannon.hullDust.gravity",0.08,0.0,4.0);
  CANNON_GROUND_ALPHA=b.defineInRange("tankCannon.groundDust.alpha",0.3,0.02,1.0);
  CANNON_HULL_ALPHA=b.defineInRange("tankCannon.hullDust.alpha",0.2,0.02,1.0);
  CANNON_RECOIL_ENABLED=b.comment("A shot pushes the physics body that carries the cannon, from the muzzle and against the shell. The vehicle is the largest collision box in the body chain; a cannon that is not part of a physics body is skipped.").define("tankCannon.recoil.enabled",false);
  // A recoil is delivered the way a thruster works: a little every physics step, for a few steps, instead
  // of one velocity write that the ships own stabiliser eats before it is ever integrated.
  CANNON_RECOIL_VELOCITY=b.comment("Velocity change applied on each physics step of a kick, in blocks per tick. The total a shot delivers is this times physicsSteps. The upper end is deliberately absurd: a large cannon is allowed to launch its own vehicle.").defineInRange("tankCannon.recoil.velocity",0.02,0.0,5.0);
  CANNON_RECOIL_STEPS=b.defineInRange("tankCannon.recoil.physicsSteps",4,1,40);
  CANNON_HULL_COUNT=b.defineInRange("tankCannon.hullDust.count",14,0,200);
  CANNON_HULL_RADIUS=b.defineInRange("tankCannon.hullDust.radius",3.0,0.1,16.0);
  CANNON_GROUND_RADIUS=b.comment("How far around the muzzle the ground is searched for dust. The ground range is meant to be wider than the hull's.").defineInRange("tankCannon.groundDust.radius",6.0,0.1,32.0);
  CANNON_GROUND_SIZE_MIN=b.defineInRange("tankCannon.groundDust.sizeMin",1.8,0.1,16.0);
  CANNON_GROUND_SIZE_MAX=b.defineInRange("tankCannon.groundDust.sizeMax",4.0,0.1,16.0);
  CANNON_GROUND_STRETCH_MIN=b.defineInRange("tankCannon.groundDust.stretchMin",1.8,1.0,6.0);
  CANNON_GROUND_STRETCH_MAX=b.defineInRange("tankCannon.groundDust.stretchMax",3.2,1.0,6.0);
  CANNON_HULL_STRETCH_MIN=b.defineInRange("tankCannon.hullDust.stretchMin",1.0,1.0,6.0);
  CANNON_HULL_STRETCH_MAX=b.defineInRange("tankCannon.hullDust.stretchMax",1.8,1.0,6.0);
  CANNON_HULL_SPEED=b.defineInRange("tankCannon.hullDust.speed",0.8,0.0,16.0);
  CANNON_BREECH_FIX=b.define("tankCannon.breechSmoke.fixPositionAndSpeed",true);
  CANNON_BREECH_SPEED=b.defineInRange("tankCannon.breechSmoke.speedScale",5.0,0.0,64.0);

  SPEC=b.build();
 }
 public static int distance(int index){return dev.cbcmuzzlelight.BeamLight.distance(index,MIN.get(),MAX.get(),STEP.get());}
 public static int count(){return dev.cbcmuzzlelight.BeamLight.count(MIN.get(),MAX.get(),STEP.get());}
 /**
  * Palette index for one block id, using the live config table and toggle.
  * @param blockId - block registry id, e.g. {@code create:red_ramp}.
  * @returns a {@link SourceColor} palette index.
  */
 public static int color(String blockId){return SourceColor.resolve(blockId,SourceColor.parse(OVERRIDES.get()),NAME_SCAN.get());}
 /**
  * Palette index for one block, resolved from its registry id.
  * @param block - the registered block.
  * @returns a {@link SourceColor} palette index.
  */
 public static int colorOf(net.minecraft.world.level.block.Block block){return color(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString());}
}