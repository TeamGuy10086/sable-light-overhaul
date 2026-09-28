package dev.cbcmuzzlelight.client;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class ExtraConfig {
 public static ModConfigSpec.BooleanValue AUTO,BIG,AP_ENABLED,DISCO_ENABLED,DISCO_HEMISPHERE,TRAIL_ENABLED,TRAIL_DEBUG,TRAIL_DEBUG_DATA;
 /** The shockwave's look: measured on the server, drawn on the client from these values. */
 public static ModConfigSpec.BooleanValue SHOCKWAVE_ENABLED,SHOCKWAVE_DEBUG;
 public static ModConfigSpec.IntValue SHOCKWAVE_MAX_PER_TICK,SHOCKWAVE_EMBER_COUNT;
 public static ModConfigSpec.DoubleValue SHOCKWAVE_MINDENSITY,SHOCKWAVE_DENSITY,SHOCKWAVE_EXPANSION,SHOCKWAVE_GROUND_SLACK,SHOCKWAVE_GROUND_OFFSET,SHOCKWAVE_UP_BIAS,SHOCKWAVE_SPEED_JITTER;
 public static ModConfigSpec.IntValue AUTO_MAX,BIG_MAX,RECEIVERS,STATIC_BUDGET,SPOT_BUDGET,HELD_BUDGET,PEER_BUDGET,LIGHT_RESELECT_TICKS,AP_COLOR,AP_HIT_COUNT,AP_RICO_COUNT,AP_RICO_CHIPS,DISCO_BEAMS,DISCO_RANGE,DISCO_WIDTH,DISCO_FALLOFF,DISCO_SCAN_TICKS,DISCO_SCAN_CHUNKS,TRAIL_COLOR,TRAIL_TICKS,TRAIL_LIFE,TRAIL_MAX_PER_TICK,TRAIL_INTERVAL;
 public static ModConfigSpec.ConfigValue<String> DISCO_COLORS;
 public static ModConfigSpec.DoubleValue AUTO_RANGE,BIG_RANGE,AUTO_BRIGHT,BIG_BRIGHT,ACTIVATION,STATIC_RANGE,TRACER_CORE,AP_HIT_SPEED,AP_HIT_SPREAD,AP_HIT_SIZE,AP_HIT_LIFE,AP_RICO_SPEED,AP_RICO_SPREAD,AP_RICO_SIZE,AP_RICO_LIFE,AP_BOUNCE,DISCO_SPIN,DISCO_INTENSITY,DISCO_CULL,
   TRAIL_LIFETIME,TRAIL_SIZE,TRAIL_OPACITY,TRAIL_SPACING,TRAIL_DRIFT,TRAIL_RANGE,SPOT_ACTIVATION; public static ModConfigSpec.DoubleValue GPU_RED,GPU_GREEN,GPU_BLUE,GPU_INTENSITY,GPU_FALLOFF,GPU_SHADER_SCALE,GPU_NORMAL,GPU_PACK_INTENSITY;
 public static ModConfigSpec.IntValue GPU_SHADOW_COUNT;
 public static ModConfigSpec.IntValue GPU_SHADOW_STEPS;
 public static ModConfigSpec.DoubleValue GPU_SHADOW_SOFTNESS;
 /** Which classes of light the player lets spend their frame time on shadows. All local choices. */
 public static ModConfigSpec.BooleanValue GPU_SHADOW_SOURCES,GPU_SHADOW_SPOTLIGHTS,GPU_SHADOW_MUZZLE,GPU_SHADOW_MUZZLE_SOFT;
 public static ModConfigSpec.BooleanValue GPU_SELF_CULL,GPU_TAKEOVER,GPU_SHADOW_AUTO,TILED_LIGHTS,GPU_FLASHLIGHT_SHADOW,GPU_FLASHLIGHT_PEER_SHADOW;
 static void define(ModConfigSpec.Builder b){
  AUTO=b.define("advanced.tracers.autocannon.enabled",true);BIG=b.define("advanced.tracers.bigCannon.enabled",true);
  AUTO_MAX=b.comment("Concurrent autocannon tracer lights, nearest first; independent from big cannon budget.").defineInRange("advanced.tracers.autocannon.maximumCount",128,0,16384);
  BIG_MAX=b.defineInRange("advanced.tracers.bigCannon.maximumCount",128,0,16384);
  AUTO_RANGE=b.defineInRange("advanced.tracers.autocannon.radius",6.0,0.25,96.0);BIG_RANGE=b.defineInRange("advanced.tracers.bigCannon.radius",12.0,0.25,96.0);
  AUTO_BRIGHT=b.defineInRange("advanced.tracers.autocannon.brightness",15.0,0.0,15.0);BIG_BRIGHT=b.defineInRange("advanced.tracers.bigCannon.brightness",15.0,0.0,15.0);
  TRACER_CORE=b.comment("Constant-bright core fraction; linear spatial falloff outside core.").defineInRange("advanced.tracers.coreFraction",0.15,0.0,0.9);
  RECEIVERS=b.comment("Additional receiving ship/area pairs for tracer, spotlight and registered sources. Muzzle budget remains separate.").defineInRange("advanced.additionalReceiverBudget",4096,1,65536);
  STATIC_BUDGET=b.comment("Budget for registered static sources and spotlights, counted separately from the dynamic muzzle/tracer budget so they can never crowd out cannon fire. The nearest entries win.").defineInRange("advanced.performance.maxStaticSources",32,0,64);
  // Spotlights used to be exempt from every distance gate, so every lamp in the level became a light
  // in the buffer every tick, whatever the player was looking at. They now have their own reach.
  SPOT_BUDGET=b.comment("Budget for spotlights alone, counted separately from the registered lamps so a wall of searchlights cannot push the ordinary lamps out (and the other way round). Nearest first.").defineInRange("advanced.performance.maxSpotlights",24,0,64);
  SPOT_ACTIVATION=b.comment("How far away a spotlight may be and still be lit, in blocks; 0 removes the limit (the old behaviour). Measured from the player to the lamp, minus the beam's own length, so a long beam that actually reaches the player is kept even when the fixture is far off.").defineInRange("advanced.spotlights.activationDistance",512.0,0.0,4096.0);
  HELD_BUDGET=b.comment("Budget for lights carried by players (the flashlight), counted on its own so a crowd with flashlights can never eat the lamps or the cannon fire. Nearest first, which with one light each means the players closest to the camera.").defineInRange("advanced.performance.maxHeldLights",8,0,64);
  // Other players' lamps are a second class on purpose: yours is the one you are looking down, so it has
  // its own budget and its own reserved shadow slot, and a crowd cannot push either of them out.
  PEER_BUDGET=b.comment("Budget for the flashlights of OTHER players, counted separately from your own so a crowd can never push your lamp out of the buffer. Each peer costs one entry for its beam and one for the small light at its waist. Nearest first.").defineInRange("advanced.performance.maxPeerLights",8,0,64);
  // Lights are re-chosen on an interval rather than every tick, which stops the lighting from
  // reshuffling (and visibly popping) whenever the player drifts a fraction of a block.
  LIGHT_RESELECT_TICKS=b.comment("How often the light set is re-chosen, in ticks. Longer is steadier and cheaper; 1 re-chooses every tick, which is the old behaviour.").defineInRange("advanced.performance.reselectIntervalTicks",5,1,100);
  ACTIVATION=b.defineInRange("advanced.staticSources.activationDistance",512.0,1.0,4096.0);
  STATIC_RANGE=b.comment("Radius for emission level 15; scales linearly with actual block emission.").defineInRange("advanced.staticSources.radiusAtLevel15",15.0,1.0,96.0);
  b.push("gpu");
  GPU_RED=b.comment("Additive GPU light colour, red channel.").defineInRange("colorRed",1.0,0.0,4.0);
  GPU_GREEN=b.comment("Additive GPU light colour, green channel.").defineInRange("colorGreen",0.58,0.0,4.0);
  GPU_BLUE=b.comment("Additive GPU light colour, blue channel.").defineInRange("colorBlue",0.25,0.0,4.0);
  GPU_INTENSITY=b.comment("Overall additive strength for the GPU light pass. 0 disables the contribution.").defineInRange("intensity",1.0,0.0,4.0);
  GPU_FALLOFF=b.comment("Exponent on the normalized light level; above 1 concentrates the light nearer the source.").defineInRange("falloffPower",1.0,0.2,4.0);
  GPU_SHADER_SCALE=b.comment("Extra strength multiplier applied only while an Iris shader pack is active. The pass then adds light after the pack's tonemapping, so it usually needs a different value than the no-shader path.").defineInRange("shaderPathScale",1.0,0.0,8.0);
  GPU_NORMAL=b.comment("How strongly the depth-derived surface normal shapes the light: 0 is the old flat additive look, 1 is full N.L diffuse. Above 0 surfaces facing away from a flash receive no light, which also rejects most 'light through a wall' cases.").defineInRange("normalInfluence",0.6,0.0,1.0);
  GPU_PACK_INTENSITY=b.comment("Strength for the in-pack lighting path (Advanced tier). The pack's own block-light scale multiplies this, so the useful range is different from 'intensity'.").defineInRange("packPathIntensity",2.0,0.0,16.0);
  GPU_SHADOW_COUNT=b.comment("How many lights cast a screen-space shadow, beyond your own flashlight's reserved one (Advanced tier). The slots go to whichever lights cover the most of the VIEW, weighted by brightness -- not to the nearest, and not to the ones nearest the centre of the screen. That last part matters: the light that leaks through a wall is usually behind that wall and therefore nowhere on screen, and only a test on the part of the view its own reach intersects finds it. This is the whole shadow budget; a light source's own page can ask for its share of it but never more than this.").defineInRange("shadowLightCount",6,0,128);
  // The taps and the penumbra are the two knobs that trade shadow quality for frame time, and they are
  // deliberately the player's: a slow machine wants few taps, and few taps look like banding only because
  // the error is the same for neighbouring pixels. Softening the sample point turns that into noise, which
  // TAA averages away, so the two settings are meant to be lowered together.
  GPU_SHADOW_STEPS=b.comment("Depth taps one shadow ray gets at most, for a bright light close by; dimmer or more distant lights get proportionally fewer, down to half this. A light source's own page may ask for less than this and never for more, so this is the ceiling that keeps a lamp someone else placed from spending your frame time.").defineInRange("shadowSteps",12,1,16);
  GPU_SHADOW_SOFTNESS=b.comment("Radius of a soft light as far as its shadow ray is concerned, in blocks. Above 0 a ray for a light that asked for soft edges aims at a random point on a sphere of this size instead of at the light's centre -- one sample of an area light, which is all a soft shadow is once the pack's TAA has averaged a few frames. It costs no extra samples, and it is what makes a low shadowSteps look smooth rather than banded. Set it to 0 to force hard edges everywhere, whoever asked for what: this is the player's veto. Beams (searchlights and the flashlight's cone) are always hard regardless.").defineInRange("shadowSoftness",0.2,0.0,2.0);
  GPU_SHADOW_MUZZLE_SOFT=b.comment("Muzzle flashes and tracer shells get soft edges, using the radius above. They are the lights that move fastest and are least likely to be looked at directly, so a penumbra costs nothing in readability and hides the noise a small shadowSteps would otherwise leave.").define("shadowMuzzleSoft",true);
  // Which classes of light may take a slot at all. All client-side on purpose: shadows are this machine's
  // frame time, so a player picks what they are willing to pay for, and nobody arriving with a lit-up
  // airship can decide for them. Colour and brightness are the opposite -- those are the world's, set once
  // by whoever built the thing.
  GPU_SHADOW_SOURCES=b.comment("Registered lamps and hidden light sources may cast shadows. On by default: this is what stops a lamp outside a building from lighting its interior, which the wrapped N.L term cannot do and which vanilla's own block light used to handle before the mod took the lamp's white pool away.").define("shadowSources",true);
  GPU_SHADOW_SPOTLIGHTS=b.comment("Spotlights may cast shadows. They are beams, so they keep a hard edge whatever shadowSoftness says.").define("shadowSpotlights",true);
  GPU_SHADOW_MUZZLE=b.comment("Muzzle flashes and tracer shells may cast shadows. The original behaviour, kept as a switch because cannon fire is the one light source that arrives in bursts and can be the most expensive.").define("shadowMuzzle",true);
  // The shadow ray is the heaviest thing the pack is asked to do, and the player cannot retune a config
  // mid-barrage, so the count above is treated as a ceiling and spent according to the frame rate.
  GPU_SHADOW_AUTO=b.comment("Let the frame rate decide how much of the shadow count above is actually used: one shadow below 45 fps, two below 60, the full configured count above that (never zero, so the feature does not appear to switch itself off). It only ever lowers the count, so a setting of 0 stays 0. Turn this off to always use the configured count.").define("shadowLightCountAuto",true);
  TILED_LIGHTS=b.comment("Draw the screen-space light pass with a per-tile light list, so a fragment only evaluates the lights that actually cover its tile instead of every active light. Cheaper in scenes with many lights; turn it off if a driver dislikes the integer texture.").define("tiledLightListing",true);
 GPU_SELF_CULL=b.comment("Registered static sources stop lighting their own block in the in-pack path (Advanced tier only). Additive light inside the lamp washes its own texture out to white, while the coloured glow on the surrounding blocks is unaffected.").define("cullSourceSelfLighting",true);
  // The flashlight sits at the player's waist rather than at their eye, which is what makes it a usable
  // shadow caster: a ray that starts inside the camera reads the depth buffer in front of the lens and the
  // whole scene's lighting crawls. The pack decides who casts by index (i < cbcMuzzleShadowCount), and the
  // mod puts your own beam at index 0 and counts it in, so it shadows whatever else is going on.
  GPU_FLASHLIGHT_SHADOW=b.comment("Your own flashlight casts a screen-space shadow in the patched shader pack (Advanced tier). It is given a reserved shadow slot ahead of everything else, so a barrage cannot take it away; the muzzle flashes then use the 'shadowLightCount' slots on top of it. Turn it off if the extra depth march is not worth it.").define("flashlightShadow",true);
  GPU_FLASHLIGHT_PEER_SHADOW=b.comment("Other players' flashlights cast shadows too. Their beams then go right behind your own in the light list and are counted into the shadow slots, so the cost grows with how many peers are in view. Off by default: a peer's beam is a light you did not ask for. Note that with the default shadowLightCount of 1 the muzzle flashes still shadow; this setting decides whether a peer's beam is added to the shadow slots as well.").define("flashlightPeerShadow",false);
  GPU_TAKEOVER=b.comment("A registered lamp gives up its own vanilla light while a GPU tier is active (DynamicOnly or Advanced: those can only add light, so the lamp's white pool has to go for its colour to show). Costs one client light update per source when the set changes, and none while it is steady. Off keeps the lamp's original white light. The CPU tiers (Legacy/Compatible) never take a block over either way.").define("takeOverSourceLighting",true);
  // Armour-piercing sparks. The directions come from the game itself: a hit sprays back along the shot,
  // a ricochet sprays along the outgoing angle, and the ricochet one is deliberately the thicker of the two.
  AP_ENABLED=b.comment("Armour-piercing hits throw this mod's own sparks; the original ones are invisible under shader packs.").define("apSpark.enabled",true);
  AP_COLOR=b.defineInRange("apSpark.color",0xFFC070,0,0xFFFFFF);
  AP_HIT_COUNT=b.defineInRange("apSpark.hit.count",10,0,200);
  AP_HIT_SPEED=b.defineInRange("apSpark.hit.speed",3.5,0.0,32.0);
  AP_HIT_SPREAD=b.defineInRange("apSpark.hit.spread",0.45,0.0,4.0);
  AP_HIT_SIZE=b.defineInRange("apSpark.hit.size",0.12,0.02,4.0);
  AP_HIT_LIFE=b.defineInRange("apSpark.hit.lifetime",12.0,2.0,200.0);
  AP_RICO_COUNT=b.defineInRange("apSpark.ricochet.count",32,0,400);
  AP_RICO_CHIPS=b.defineInRange("apSpark.ricochet.chips",6,0,100);
  AP_RICO_SPEED=b.defineInRange("apSpark.ricochet.speed",5.0,0.0,32.0);
  AP_RICO_SPREAD=b.defineInRange("apSpark.ricochet.spread",0.8,0.0,4.0);
  AP_RICO_SIZE=b.defineInRange("apSpark.ricochet.size",0.18,0.02,4.0);
  AP_RICO_LIFE=b.defineInRange("apSpark.ricochet.lifetime",20.0,2.0,400.0);
  AP_BOUNCE=b.defineInRange("apSpark.bounce",0.3,0.0,1.0);
  // Disco ball: an easter egg, so none of this is budgeted or throttled beyond one scan per second.
  // Colours are palette indices (1..14 are the dyes), and the cones spin forever.
  DISCO_ENABLED=b.comment("The disco ball throws spinning coloured cones. Purely a view: the client decides it, no server work.").define("disco.enabled",true);
  DISCO_COLORS=b.comment("Palette indices to cycle through, comma separated: 0 is the configured default colour, 1..14 the dyes, 15 the flashing one.").define("disco.colors","1,4,11,14");
  DISCO_BEAMS=b.defineInRange("disco.beams",8,1,32);
  DISCO_SPIN=b.defineInRange("disco.spinDegreesPerTick",1.5,0.0,90.0);
  DISCO_RANGE=b.defineInRange("disco.range",32,1,128);
  DISCO_WIDTH=b.defineInRange("disco.width",2,0,15);
  DISCO_INTENSITY=b.defineInRange("disco.intensity",6.0,0.0,15.0);
  // The spots are the whole show now, so how they fade is a setting of their own. The ball no longer
  // has a steady glow: that is what used to make the light under it look static.
  DISCO_FALLOFF=b.comment("Spot falloff profile, the same five the spotlights use: 0 forced maximum (flat across the spot), 1 fast, 2 uniform, 3 slow, 4 dim. Each ball's own block entity overrides this once the page has touched it.").defineInRange("disco.falloff",0,0,4);
  DISCO_CULL=b.defineInRange("disco.cullDistance",64.0,4.0,512.0);
  DISCO_SCAN_TICKS=b.defineInRange("disco.scanIntervalTicks",20,1,400);
  DISCO_SCAN_CHUNKS=b.defineInRange("disco.scanRadiusChunks",4,1,16);
  // The beam fan covers the upper hemisphere, all of it above the horizontal, so it reads as a ball
  // hanging from a ceiling rather than as a ring of searchlights. The ambient sphere is the stable
  // pool of light it throws on the floor and ceiling underneath, which is what the eye looks for.
  DISCO_HEMISPHERE=b.comment("Keep every beam above the ball's own local horizontal, spread over its upper hemisphere; off puts them all on one shallow ring. 'Up' is the ball's up, so a body that rolls carries its hemisphere with it.").define("disco.upperHemisphere",true);
  // Tracer trails: one bright puff per frame left behind every shell that carries a tracer. The frames
  // are ordinary particles of this mod's own type, laid by the client at the exact points of the flight.
  TRAIL_ENABLED=b.comment("Tracer shells leave a bright trail behind them; the sprite is this mod's own, so shader packs draw it too.").define("trail.enabled",true);
  TRAIL_LIFETIME=b.comment("How long each frame of the trail exists, in ticks.").defineInRange("trail.lifetime",12.0,1.0,400.0);
  // How the step between two positions is filled: a shell crosses several blocks in a tick, so one frame
  // a tick is a dotted line. These are the two knobs that decide how solid the rope looks.
  TRAIL_SPACING=b.comment("Blocks between two trail frames along the flight path. Smaller is a denser, more continuous rope.").defineInRange("trail.spacing",0.5,0.05,8.0);
  TRAIL_MAX_PER_TICK=b.comment("Ceiling on the frames one shell may leave in a single tick, so an absurdly fast shell cannot flood the particle engine.").defineInRange("trail.maxPerTick",24,1,256);
  TRAIL_SIZE=b.defineInRange("trail.size",0.45,0.02,8.0);
  TRAIL_OPACITY=b.defineInRange("trail.opacity",0.9,0.02,1.0);
  TRAIL_COLOR=b.defineInRange("trail.color",0xFFD9A0,0,0xFFFFFF);
  // The frame keeps a little of the shell's speed, backwards, plus a whisper of jitter: without it
  // consecutive frames stack into one dot instead of reading as a rope. Deliberately small -- the frame
  // must stay on the shell's path.
  TRAIL_DRIFT=b.comment("How much of the shell's own speed a trail frame keeps, backwards. It never falls: gravity is off by design. A nudge to stop consecutive frames stacking, not a velocity that throws the frame off the path.").defineInRange("trail.drift",0.06,0.0,1.0);
  TRAIL_INTERVAL=b.comment("Ticks between two trail segments; 1 leaves one per tick. The gap the shell covered in the meantime is still filled, so this thins work rather than breaking the trail.").defineInRange("trail.interval",1,1,20);
  TRAIL_RANGE=b.comment("How far from the player this client lays a trail for the shells it can see itself, in blocks. Shells beyond it are covered by the server's feed.").defineInRange("trail.spawnDistance",160.0,8.0,1024.0);
  TRAIL_TICKS=b.comment("How many ticks of flight the trail spans, for the log line only: the frames' own lifetime is what decides how long the rope looks.").defineInRange("trail.ticks",14,1,200);
  TRAIL_LIFE=b.comment("Ticks before a shell nobody reports any more is forgotten, so a stale position cannot be re-used if its id is seen again. The frames already laid fade out on their own.").defineInRange("trail.fadeTicks",20,1,400);
  TRAIL_DEBUG=b.comment("Print one line a second while trails are being laid: how many frames were placed and how many shells each source covered. The way to tell a stopped feed from frames that are not drawn.").define("trail.debugLog",false);
  TRAIL_DEBUG_DATA=b.comment("Print every shell position the server sends, in world coordinates, with its motion. A coordinate that arrives as +-7.9998 means the wire encoding clamped it, which is a bug in the feed and not in the drawing. Use it together with the client command '/mkTrail <x1> <y1> <z1> <x2> <y2> <z2>', which lays a trail along a straight line through exactly the same code the feed uses: if the command draws and the feed does not, the problem is the positions arriving, not the particles.").define("trail.debugReceivedData",false);
  // ---- shockwave ------------------------------------------------------------------------------
  // The server measures an explosion and sends the numbers (ShockwavePayload); everything about how the
  // wave looks is decided here and drawn from this mod's own particles. Editing this file and rejoining
  // the world is enough to retune it, so there is nothing a scripting layer would add.
  b.push("explosions");
  b.push("shockwave");
  SHOCKWAVE_ENABLED=b.comment("Draw a shockwave for explosions the server measured. The measurement itself is the server's; this only decides the look.").define("enabled",true);
  SHOCKWAVE_MINDENSITY=b.comment("Fraction of each front's real particle count that must have accumulated before the wave is drawn at all. Below it a blast is measured and lit but not drawn, which is how many small explosions can happen without a strobe.").defineInRange("minimumDensity",0.15,0.0,1.0);
  SHOCKWAVE_DENSITY=b.comment("Scales the particle count per unit of front area. The count per tick is proportional to the ring area the front covers that tick, so a bigger blast spreads more particles rather than the same number over a wider shell.").defineInRange("density",0.6,0.01,16.0);
  SHOCKWAVE_MAX_PER_TICK=b.comment("Ceiling on the particles one shockwave may spawn in a single tick, so a very large charge cannot flood the client.").defineInRange("maxParticlesPerTick",180,1,4096);
  SHOCKWAVE_EXPANSION=b.comment("Exponent of the front's growth: r(t) = R * (1 - (1 - t/T)^expansion). 1 is a constant speed, below 1 starts fast and decelerates like a real blast, above 1 accelerates.").defineInRange("expansion",0.5,0.05,4.0);
  SHOCKWAVE_GROUND_SLACK=b.comment("How far below the blast centre a particle may be before it is pulled onto a floor found within that distance. This is what turns the lower half of the shell into a ring rolling outward along the ground.").defineInRange("groundSlack",6.0,0.0,64.0);
  SHOCKWAVE_GROUND_OFFSET=b.comment("Height above the floor that the ground ring is drawn at.").defineInRange("groundOffset",0.15,-2.0,4.0);
  SHOCKWAVE_UP_BIAS=b.comment("Extra speed given to particles with an upward component, so the column over the blast rises while the shell spreads.").defineInRange("upBias",0.25,0.0,4.0);
  SHOCKWAVE_SPEED_JITTER=b.comment("Random variation on a particle's speed, as a fraction of the front's own speed.").defineInRange("speedJitter",0.25,0.0,2.0);
  SHOCKWAVE_EMBER_COUNT=b.comment("Embers spawned at the front, over and above the shockwave particles, for the first few ticks. They carry the brightness the particle config gives them; 0 switches them off.").defineInRange("emberCount",60,0,4096);
  SHOCKWAVE_DEBUG=b.comment("Print one line per shockwave: the measured values it was built from and what it spawned. The way to tell a measurement that never arrived from one that drew nothing.").define("debugLog",false);
  b.pop();
  b.pop();
  b.pop();
 }
}