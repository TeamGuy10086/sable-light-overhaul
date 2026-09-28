# 0.9.1 stability update

Current details: STABILITY-0.9.1.zh-CN.md. Fancy scheduler request tickets pin light maps and serialize commit version checks. No batch-atomic mesh swap or dedicated Sodium compilation scope. Cached Yuushya regions receive dirty notifications and main-thread task scope; copycat wrappers are resolved before AO light cache keys and LevelRenderer reads. Optional integrations use target existence checks. Uploaded binary method descriptors are regression tested, not runtime Mixin validated. Previous sections are historical.

# 0.9.0 implementation update

Current feature guide: LIGHTING-0.9.zh-CN.md. Entity join/leave events retain weak CBC projectile candidates; synchronized isTracer/hasTracer flags select active lights per tick, separate nearest-first budgets. Snapshot creation uses CBC world transformation and immutable area geometry. SourcePayload protocol5 synchronizes loaded source state lists on change, checked every5ticks. Server source state never depends on client rendering mode.

RegisteredSources is per-dimension SavedData; SourceAnchor stores optional Sable UUID and block position relative to plot chunk origin. Resolution only uses existing loaded sublevel chunks. Client transforms the anchor through the current pose each tick. Ground static areas are receiver-only and removed from world publication after Sable projection. Extra receiver budget is separate from muzzle budget. Spotlight block stores facing/range/mode; no BE or beam renderer. Source list bound8192. No chunk tickets or explicit chunk generation. Server redstone queries require loaded 2-block neighbor region.

Tests verify source cycle/NBT, packet reset/round-trip/count bound, full-angle beams, profiles, budgets and exact CBC/Sable binary methods. Runtime game/packet delivery/Sable assembly migration and performance remain untested. Previous sections below are historical.

# 0.8.2 implementation update

Current schema/behavior: README.zh-CN.md; sections below are historical. AreaLight.directional uses a gun-aligned L1 distance for autocannon, and asymmetric L2 distance for big cannon. Peak stays at native source. Independent side/vertical radii; basis is world-up referenced. Back plane clipping approximates muzzle masking; it is not occlusion. Older cone constructors remain for prior regression tests but are no longer used by Advanced rendering. Receiver transformations and twin muzzle hooks are unchanged.

LightProfile defines three independent shape/spatial/time/scaling groups. Blast then smoke each use their own time origin; per-shot storage covers the sum of both durations and stores the blast transition tick. Native scalar metadata and protocol 4 unchanged. Generated default TOML comes from ModConfigSpec.correct and is validated in ProfileConfigTest. No culling added.

# 0.8.1 implementation update

Current behavior and tuning: README.zh-CN.md. Older sections below are historical.

BigCannonMixin now wraps the single BigCannonPlumeParticleData(float,float,int) allocation in fireShot; captures size, effective power, live plumePos/vec and argument level/entity. plumePos is already in world space; axis uses CBC transformLocationNormal exactly as native emission, muzzle advances 1.6 blocks. Protocol 4 carries both scalar values. Binary tests verify allocation count, constructor descriptor and local scope against the supplied CBC JAR; these are not a runtime Mixin launch test.

Advanced per-shot keys replace entity grouping; CBCAT preserves its selected muzzle for environmental light. Immutable AreaLight supports forward truncated cones and ellipsoids, with continuous core/middle/tail falloff. Existing worker-safe world/plot transforms are reused. Initial big flash scales by power/reference, follower by heat-weighted native getQuadSize(0)/reference; no particle available uses plumeSize/4. Each reach has an explicit cap. Legacy geometry is unchanged. No new culling or shader modifications.

# Advanced 0.8 architecture

Immutable AreaLight ellipsoid and copied AreaSnapshot matrices provide worker-safe world/receiver queries. Each big shot has one logical area; autocannons share the latest pulse by authoritative cannon entity ID. CBCAT center undoes native side/factor offset before world transformation, without altering visible barrel position. Protocol 3 carries both positions.

Lamb Behavior bounds cover the full custom function support. Sable 2.0.3 intersection query and inverse transformed bounds register plot-space receiver proxies, clipped to loaded plot geometry bounds. No plot chunks are created. Only owned Lamb sources bypass adaptive tick throttling. Disabled/missing Lamb falls back to Legacy; other mods' configuration is untouched.

Read-only LevelRenderer packed-light overlay covers vanilla/Sable and Sodium getLightColor queries. Flywheel 1.0.6 collector establishes a scoped ThreadLocal section snapshot, and the existing write method merges only block light. No native pointer arithmetic or light storage mutations. ClientChunkCache.onLightUpdate invalidates render and Flywheel data. Advanced does not call checkBlock/updateSectionStatus; Legacy retains those calls. Removed Lamb behaviors immediately return zero even if an old lookup entry remains for one tick. Dirty old/new regions restore base lighting after changes/removal.

Lamb and own dirty notifications can overlap; large regions still rebuild meshes. Runtime compatibility and performance are unverified. Snapshot, API/binary, pulse, packet and cleanup tests do not replace transformed client startup or gameplay validation.

Historical notes below refer to older backends; use README.zh-CN.md for current setup.

# CBC Muzzle Light 0.6.0-alpha — implementation and verification

Scope: ordinary big cannons and autocannons only. Native CBC particle visuals are untouched. No Iris linkage, Photon source patch, shader uniform bridge or shader-specific marker remains. No mortar addon hook or Voxy-specific support.

## Binary provenance

Exact original CBC SHA-256: `ea017ab94637032d039899d88404838d173fa0e6286657cae6a6c3af459e87ca`.
The original conversation supplied the two Jars through Windows attachment-preview paths; `/mnt/data` was unavailable on this host. No substitute web download was used.

## Server firing and coordinates

Targets under `rbasamoyai.createbigcannons.cannon_control.contraption`:

- `MountedBigCannonContraption.fireShot(ServerLevel, PitchOrientedContraptionEntity)`.
- `MountedAutocannonContraption.fireShot(ServerLevel, PitchOrientedContraptionEntity)`.

Each wraps the single invocation of `CBCCompatTransformers.transformVec3(Level, Vec3, Vec3): Vec3` in its successful effects path. Uploaded bytecode offsets are respectively 2134 and 1195. The captured direction locals are `vec` and `vec1`. MixinExtras retains the original invocation and result, then performs separate transforms for the addon's point and axis. There is no `actuallyFireDropMortar` hook in this build.

Using CBC's own transformed barrel direction d and A = entity.toGlobalVector(center(currentPos.relative(initialOrientation)), 0), the normal straight-barrel muzzle face is interpreted as A - 1.5*d. The light is placed at A - 1.4*d, 0.1 blocks outside it. CBC's big plume input is A - 3*d (offset +1.6*d); auto plume input is A - 1.5*d (offset +0.1*d). Apply CBC's compatibility transform F to the corrected point. The payload axis is normalize(F(point+d)-F(point)), so it follows rotations supported by F. Unusual nonlinear/ship transforms remain unverified.

The v2 clientbound payload carries dimension, double position, float direction and a two-value cannon kind. Protocol v2 is unchanged from 0.2/0.3. Server and all clients must upgrade together. Recipients are same-dimension players within 512 blocks, including the autocannon operator. This does not depend on CBC's operator-suppressed plume packet.

## Actual smoke tracking

BigPlumeMixin wraps the plume tick in a try/finally ThreadLocal emission context. The initial emitter position + normalized direction * 1.6 associates with an unbound authoritative shot within 1.5 blocks, four ticks and axis dot > 0.9. This association is still heuristic; it is not a server-assigned native particle ID. It retries up to age 4 and excludes other client/Ponder worlds.

CannonSmokeMixin captures constructors executed under that context. FallbackCannonSmokeParticle subclasses CannonSmokeParticle, including the constructor and inherited tick, so synchronous provider fallback is included. No particle-engine scan or duplicate collision/wind simulation is performed. Each group retains at most the configured real particle references; inactive references are removed and the oldest reference is replaced when new children exceed the budget. There are at most 64 groups; retired groups cannot resume capture. Active status is cached per group each client tick to avoid store scans for every rendered vertex.

After normal particle motion, ClientTickEvent.Post computes a heat-weighted centroid. Heat is max(0,1-age/(lifetime*hotFraction)), an approximate temporal mask. Emission initially stays at the muzzle for two ticks, then the entire sparse layout follows this centroid. There is no permanent stronger muzzle sample, no six-tick cutoff and no four-block clamp. Cold/stale tracked data stops lighting instead of pinning it. Positions beyond 128 blocks from the muzzle fail closed. With no captured particles, a 0.95-drag forward trajectory approximates the effect. This is explicitly not exact native motion.

The most recent bounded set of children represents the cloud; it is not the centroid of every rendered pixel or all particles. Replacement may shift the sampled center. Group/shot expiry and world reset clear references; serial tokens are never reused across worlds.

## Lighting and visible effects

Both cannon kinds start at block level 15. Sparse layout has up to seven sites: center and three axis pairs. Default big span 6 x 4 blocks, five sites; auto span 3, three sites ahead of muzzle. Global nearest-source budget defaults to 96. Voxel overlap uses maximum, not addition. Unloaded/out-of-height positions are ignored. Only block-light sections are activated; terrain is never edited. Changed old/new voxels are rechecked, with a ten-tick refresh to recover server light updates, and empty section state is restored when sources disappear.

LightCurve includes peak hold; shot settings are captured at receive time. Automatic gun flame uses the original tapered texture and crossed ribbons, default size 5, lifetime 3, opacity 1-progress^2. Native big smoke retains CBC color, alpha and getLightColor; the mixin only captures references and exposes position, age-derived heat and liveness. Auto flame now has independent length/width multipliers (0.8 / 1.3 defaults), with render bounds derived from the resulting dimensions. The white vertex tint is ordinary RGB (1,1,1), without shader tags.

## Large-area lighting scope

The existing bounded sparse layout remains in use. A separate tuning preset spreads the same five sources over 12 x 8 blocks and slows decay (peak hold 6, decay .025, floor .5, max duration 24). It is not an automatic config migration or the default. More spread and higher sustained block levels may increase propagation work even at a fixed source count.

A renderer-side configurable radial/capsule light field is only a proposed future alternative. No renderer lightmap injection, global attenuation change, screen-space lighting or new shader backend has been added in this revision.

## Verification boundary

28 automated tests pass, including pure tracking/layout/curve behavior, uploaded CBC firing and smoke inheritance signatures, no mortar/ball effect, no Iris bytecode linkage and no CBC particle color/alpha/light override. Clean build ensures retired shader mixins cannot survive as stale compiled classes.

No in-game Mixin application, GPU output, multiplayer performance or frame-time benchmark was run for 0.5.1. Current renderer-side alternatives have not been profiled or verified against the user's renderer. Photon-specific offline parser checks were removed along with the feature and its test dependencies.

## 0.5.1 geometry fix

Removed the transverse muzzle cap. AutoMuzzleParticle renders only the three longitudinal flame ribbons; no replacement front-facing cap was introduced. Existing length/width settings and environment lighting remain in use. Build and the existing 28 checks passed; no in-game visual validation was run.

## CBCAT twin integration (0.6.0)

Inspected exact upload: cbc_at_Neoforge_1.21.1_0.1.4c.jar; mod id cbc_at, metadata version 0.1.4c-1.21.1. SHA-256 c7a08eea5ef2d69eb8052b9da2a5628f10a78cb786896352891d203f36a890e7. Upstream binaries/decompiled source are not redistributed.

Target: com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption.fireShot(ServerLevel, PitchOrientedContraptionEntity). It extends AbstractMountedCannonContraption directly and never delegates to CBC MountedAutocannonContraption.fireShot, so the normal auto hook does not duplicate effects.

Native coordinate sequence: A = toGlobalVector(center(currentPos.relative(initialOrientation)),0), C = transformed contraption center, d = normalize(A-C). Start at A-1.5*d. Set right = normalize((-d.z,0,d.x)), top = right cross d, offset = vertical ? top : right, factor = firstFire ? -0.25 : +0.25, then subtract offset*factor and flip firstFire. particlePos stores this final position. Thus horizontal barrels are separated by 0.5 blocks. Do not read the already-flipped boolean to choose the current side.

The injection runs immediately before the unique ServerLevel.players():List invocation, bytecode offset 1331. Both particlePos (slot 26, bytecode live range 1066..1589) and vec1 (Vec3 direction slot 21, live range 949..1589) are captured with MixinExtras Local. This is after squib/failure early returns and native side selection, outside the per-player smoke loop. It fires even with an empty player list and sends through the existing addon distributor, including the controlling passenger. Native sound, projectiles, recoil and side toggling are not modified.

emitWorldAutocannon uses particlePos + normalized direction * 0.1 and kind 1. These coordinates are already native world coordinates; no CBCCompatTransformers call is applied to them. CBCAT itself has no such call here. Exact vertical aim may degenerate CBCAT's horizontal-right vector; this addon does not repair upstream projectile geometry. Shared vertical-twin path is covered structurally, not visually validated.

The optional mixin has Pseudo plus a class-bytecode-presence plugin without class initialization. CBCAT is not a compile dependency or a required mod. Only the supplied binary has been checked; different versions need revalidation. Protocol v2 and config format are unchanged.

Autocannon store coalescing now compares positions within 0.01 blocks instead of voxel equality. Twin muzzles sharing one voxel remain separate shot records; downstream block light still merges by maximum at a voxel. Big-cannon coalescing remains voxel-based. Global shot/source budgets still apply.

Current verification: 33 checks passed, no skips, with the exact supplied CBCAT jar provided. These include hook uniqueness, live local scopes, firstFire flip before hook, plus/minus 0.25 constants, no base-auto delegation, no direct CBCAT class calls and same-voxel two-barrel lifecycle tests. No game session, live Mixin transformation, installed/absent CBCAT boot test, screenshot alignment or performance benchmark was run for this revision.
