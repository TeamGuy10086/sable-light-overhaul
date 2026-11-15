package dev.sablelightoverhaul.lighting;
import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class LightingCommon {
 public static final ModConfigSpec.BooleanValue VEHICLE_DUST_EXPERIMENT;
 /** Muzzle haze is a feature of its own now, not the vehicle dust experiment's companion. */
 public static final ModConfigSpec.BooleanValue HAZE_ENABLED;
 /** Separate gate for the thin haze of the single- and twin-barrel autocannons. */
 public static final ModConfigSpec.BooleanValue AUTO_HAZE_ENABLED;
 public static final ModConfigSpec.IntValue VEHICLE_HAZE_INTERVAL,VEHICLE_HAZE_COUNT,VEHICLE_HAZE_LIFE;
 public static final ModConfigSpec.DoubleValue VEHICLE_HAZE_ALPHA,VEHICLE_HAZE_SIZE,VEHICLE_DUST_RADIUS;
 /** The haze motion, which belongs to the server: the client only keeps budgets and switches. */
 public static final ModConfigSpec.DoubleValue HAZE_SPREAD,HAZE_DRIFT,HAZE_SINK,HAZE_CLEARANCE,HAZE_PUSH,HAZE_PUSH_RADIUS,HAZE_LIGHT_GAIN;
 /** The tank cannon keys that are doubles: the fireball opacity multiplier and the spark cone. */
 public static final ModConfigSpec.DoubleValue CANNON_FIREBALL_LENGTH,CANNON_FIREBALL_CONE_ANGLE,CANNON_FIREBALL_SIZE,CANNON_FIREBALL_ALPHA;
 public static final ModConfigSpec.DoubleValue FLAK_CONE_RANGE,FLAK_CONE_DAMAGE;
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

 public static final ModConfigSpec.DoubleValue DUST_RATE,DUST_MIN_SPEED,DUST_SIZE_MIN,DUST_SIZE_MAX,DUST_STRETCH_MIN,DUST_STRETCH_MAX,DUST_UP_MIN,DUST_UP_MAX,DUST_DAMPING,DUST_GRAVITY,DUST_ALPHA,DUST_FADE,DUST_SCATTER,SMOKE_RISE,SMOKE_SIZE,SMOKE_OPACITY,SMOKE_FADE;
 /** The vent jet: where it points and how fast it leaves. It never inherits the body's motion. */
 public static final ModConfigSpec.DoubleValue SMOKE_YAW,SMOKE_PITCH,SMOKE_SPEED,SMOKE_OFFSET_Y;
 public static final ModConfigSpec.IntValue DUST_MAX_PER_SCAN,DUST_DEPTH,DUST_LIFE_MIN,DUST_LIFE_MAX,SMOKE_LIFE;
 public static final ModConfigSpec.IntValue SMOKE_INTERVAL_MAX,SMOKE_INTERVAL_MIN,SMOKE_COUNT_MAX,SMOKE_COUNT_MIN;
 public static final ModConfigSpec.BooleanValue DUST_TINT;
 // Tank cannon: one group per muzzle effect, all of it meant to be tuned by firing the gun.
 public static final ModConfigSpec.BooleanValue CANNON_ENABLED,EXPL_LIGHT_ENABLED,EXPL_SHOCKWAVE_ENABLED,TRAIL_FEED_ENABLED,EFFECTS_DEBUG,PROFILES_ENABLED,CANNON_FIREBALL_ENABLED;
 public static final ModConfigSpec.IntValue CANNON_CULL,CANNON_BUDGET;
 public static final ModConfigSpec.ConfigValue<String> DISCO_COLORS;
 public static final ModConfigSpec.IntValue CANNON_FIREBALL_COUNT,CANNON_FIREBALL_LIFETIME;
 public static final ModConfigSpec.IntValue CANNON_GROUND_COUNT,CANNON_GROUND_DISTANCE,CANNON_HULL_COUNT,CANNON_GROUND_LIFETIME,CANNON_GROUND_COLOR,CANNON_HULL_LIFETIME,CANNON_HULL_COLOR,EXPL_MIN_BLOCKS,DISCO_BEAMS,DISCO_RANGE,DISCO_WIDTH,DISCO_FALLOFF,SHOCKWAVE_BURIED_DEPTH;
 public static final ModConfigSpec.DoubleValue SHOCKWAVE_GROUND_BAND,SHOCKWAVE_BURIED_SOLIDITY,SHOCKWAVE_DOWN_UP,SHOCKWAVE_MIN_POWER,SHOCKWAVE_AIR_BAND;
 public static final ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> EXPL_YIELD_TIERS;
 public static final ModConfigSpec.DoubleValue CANNON_PLUME_SCALE;
 public static final ModConfigSpec.DoubleValue CANNON_PLUME_WIDTH,CANNON_PLUME_TRAVEL,CANNON_PLUME_EXPANSION;
 public static final ModConfigSpec.IntValue CANNON_PLUME_RELEASE,CANNON_PLUME_LIFE,CANNON_PLUME_AMOUNT;
 public enum GroundDustMode { LEGACY, FIELD }
 public static final ModConfigSpec.EnumValue<GroundDustMode> GROUND_DUST_MODE;
 public static final ModConfigSpec.IntValue GROUND_FIELD_RESOLUTION;
 public static final ModConfigSpec.DoubleValue HULL_FRONT_COVERAGE,HULL_DETACH_FADE,HULL_DETACH_EXPANSION,HULL_CLUSTER_DENSITY,HULL_CLUSTER_SPREAD,HULL_CLUSTER_STAGGER,HULL_ATTACH_SECONDS,HULL_SURFACE_SPEED,HULL_CLEARANCE,HULL_INHERIT_MOTION,GROUND_HEIGHT_SCALE,HULL_FLOW_MOTION,GROUND_CORRIDOR_WIDTH,GROUND_OUTER_LIFT,GROUND_MOTION_SCALE,GROUND_DUST_DENSITY,HULL_FLOW_LIFT,HULL_FLOW_TURBULENCE,HULL_FLOW_RELEASE;
 public static final ModConfigSpec.BooleanValue HULL_FLOW_ENABLED,HULL_SURFACE_COUPLING;
 public static final ModConfigSpec.IntValue HULL_FLOW_COLOR;
 public static final ModConfigSpec.DoubleValue GROUND_FIELD_SPREAD,GROUND_FIELD_SPEED,GROUND_FIELD_LIFT,GROUND_FIELD_TURBULENCE,GROUND_FIELD_SIZE,GROUND_FIELD_STEP;
 public static final ModConfigSpec.DoubleValue CANNON_GROUND_SPEED;
 public static final ModConfigSpec.DoubleValue CANNON_HULL_RADIUS,CANNON_HULL_SPEED,CANNON_GROUND_RADIUS,CANNON_HULL_SIZE_MIN,CANNON_HULL_SIZE_MAX,CANNON_GROUND_SCATTER,CANNON_GROUND_DAMPING,CANNON_HULL_DAMPING,CANNON_GROUND_GRAVITY,CANNON_HULL_GRAVITY,CANNON_GROUND_ALPHA,CANNON_HULL_ALPHA,CANNON_GROUND_FADE,CANNON_HULL_FADE,EXPL_CHARGE,EXPL_SIZE_SCALE,SHOCKWAVE_RANGE,DISCO_SPIN,DISCO_INTENSITY;
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
 public static final ModConfigSpec.BooleanValue VOXY_ENABLED;
 public static final ModConfigSpec.IntValue VOXY_MAX_COLUMNS,VOXY_MIN_BLOCKS,VOXY_IMMEDIATE_RANGE,VOXY_NUDGE_DELAY;
 public static final ModConfigSpec.IntValue TRAIL_FEED_RANGE;
    public static final ModConfigSpec.BooleanValue TRAIL_FEED_DEBUG;
    public static final ModConfigSpec.IntValue EXPLOSION_CULL;
 public static final ModConfigSpec.DoubleValue SEND_RANGE;
 /** Defaults for a player's flashlight; each player can be given their own by an operator. */
 public static final ModConfigSpec.IntValue FL_COLOR,FL_RANGE,FL_WIDTH,FL_DECAY,FL_FALLOFF,FL_INTENSITY,FL_POINT_RADIUS,FL_POINT_INTENSITY,FL_SYNC_RANGE;
 // Far entities (an assembled cannon is an entity): server-authoritative, the integrated server reads it.
 public static final ModConfigSpec.BooleanValue FAR_ENTITIES,FAR_CONTACTS;
 public static final ModConfigSpec.ConfigValue<List<? extends String>> FAR_ENTITY_NAMES;
 public static final ModConfigSpec.IntValue FAR_ENTITY_RANGE;
 public static final ModConfigSpec.BooleanValue FAR_ENTITY_DEBUG;

 static {
  var b=new dev.sablelightoverhaul.AcceptedDefaults("server");
  // they describe how a cannon behaves, and they reach the client through the same table every other per-kind
  // value already uses, so there is no second sync path for them.
  HAZE_ENABLED=b.comment("**炮口薄烟总开关**（默认开）。薄烟是自动炮、双管自动炮与坦克炮共同的炮口效果；巨炮不走这条路（它自己的烟已经够大）。薄烟与车体尘幕是两件互不相干的效果，各自有开关：这一项管薄烟，[hullDust] 那一项管车体尘幕实验，两者可以同时开。").define("haze.enabled",true);
  AUTO_HAZE_ENABLED=b.comment("自动炮和双管自动炮的炮口薄烟。连射时按炮口积烟，接近镜头时柔和淡出。此开关独立于坦克炮薄烟。").define("haze.autocannon",false);
  VEHICLE_DUST_EXPERIMENT=b.comment("**实验：车体尘幕**（默认关闭以便 A/B）。尘落在车体表面上，支持 Sable 连接链；与炮口薄烟无关，薄烟无需载具、普通大炮不变。").define("hullDust.enabled",false);
  VEHICLE_DUST_RADIUS=b.comment("实验车体尘幕的三维表面搜索半径：尘要落在车体表面上，就得先在炮口附近搜出表面，长炮管需适当增大，单位格（1..48）。与炮口薄烟无关——薄烟不需要搜索。").defineInRange("hullDust.radius",16.0,1.0,48.0);
  HAZE_SPREAD=b.comment("薄烟出生时的向外速度，单位格/tick（0~0.4）。0 表示只停在炮口不动；这是「看得出扩散」的起点。默认 0.03：机炮要的是一小团，不是炸开一圈。").defineInRange("haze.muzzleSpread",0.03,0.0,0.4);
  HAZE_DRIFT=b.comment("向外速度的上限，单位格/tick（0~0.6）。薄烟朝出生方向缓慢加速到这个速度后保持，所以扩散看得见、又不会一路加速。默认 0.05：配上面那项正好团在炮口附近。").defineInRange("haze.muzzleDrift",0.05,0.0,0.6);
  HAZE_SINK=b.comment("薄烟的下沉加速度，单位格/tick²（0~0.04）。默认 0：**不下沉**，薄烟只做水平扩散然后散掉。设成 0.004 之类才会慢慢沉，且下沉时受下面那项保护、不会没入地面。").defineInRange("haze.muzzleSink",0.0,0.0,0.04);
  HAZE_CLEARANCE=b.comment("只在薄烟会下沉时不小于这个离地高度，单位格（0~8），默认 1.0。烟团中心到下方方块顶面小于它时会被抬回这个高度并停止下沉。不下沉时这一项不参与。").defineInRange("haze.muzzleClearance",1.0,0.0,8.0);
  HAZE_PUSH=b.comment("每次开炮对附近已有薄烟的水平推力，单位格/tick（0~0.4）；0 禁用推烟。方向是远离炮口的水平方向。默认 0.02：机炮的烟只被轻轻推开一点、成团聚在炮口，而不是被吹散。").defineInRange("haze.muzzlePush",0.02,0.0,0.4);
  HAZE_PUSH_RADIUS=b.comment("推烟作用半径，单位格（0.5~24）；不影响灯光范围。默认 3.0：与薄烟自成团的范围同量级，再大的烟本来也飘在别处。").defineInRange("haze.muzzlePushRadius",3.0,0.5,24.0);
  HAZE_LIGHT_GAIN=b.comment("薄烟受动态光影响的倍率（0~4）：0 只看原版光照，1.6 等于按灯在该点的强度把烟提亮到两倍多。粒子是半透明几何、画在 GPU 光路之后，所以那条光路照不到它——这一条是让薄烟能被炮口闪光与射灯照亮的办法。只在有灯覆盖的位置生效。").defineInRange("haze.lightGain",1.6,0.0,4.0);
  VEHICLE_HAZE_INTERVAL=b.comment("同一发射器两次补烟的最小间隔 tick（0..100）。**默认 0：每一炮都补烟**——用户实测 0.1.32 会「隔几炮才冒一次烟」，看不出规律，薄烟本应每炮都有。设成 6 之类会省一半以上烟粒子（性能吃紧时的旋钮），代价就是连射时烟会一跳一跳地出现。不影响火焰、灯光和推烟事件。").defineInRange("haze.hazeInterval",0,0,100);
  VEHICLE_HAZE_COUNT=b.comment("每次补充烟层的粒子上限。").defineInRange("haze.hazeCount",4,0,24);
  VEHICLE_HAZE_LIFE=b.comment("稀薄烟层寿命 tick。").defineInRange("haze.hazeLifetime",100,10,400);
  VEHICLE_HAZE_ALPHA=b.comment("烟层峰值不透明度，不发光，受现有渲染通道照明。").defineInRange("haze.hazeAlpha",0.3,0.02,0.8);
  VEHICLE_HAZE_SIZE=b.comment("烟层粒子直径，单位格。").defineInRange("haze.hazeSize",8.0,0.5,16.0);

  // 静态光源：距离档位表、颜色怎么决定、以及注册光源发给谁。这里每一项都是全局的，页面上没有同名项。
  MIN=b.comment("距离档位表的最小值，单位格（1..512）。页面上每一档代表多少格由这张表换算，手电筒的 range 也用它；最小值抬高后，比它低的档位会被顶到这个下限。").defineInRange("staticSources.spotlight.minimumDistance",16,1,512);
  MAX=b.comment("距离档位表的最大值，单位格（1..512），不能小于最小值，否则整张表会被反过来算。512 是炮口闪光能照到的距离，调小等于让最远的灯也照不到那么远。").defineInRange("staticSources.spotlight.maximumDistance",512,1,512);
  STEP=b.comment("相邻两档之间隔多少格（1..512）。档数 = 1 + ceil((最大值 - 最小值) / 本值)，最多 32 档；步长越小档位越细，页面上的档数越多。").defineInRange("staticSources.spotlight.distanceStep",32,1,512);
  OVERRIDES=b.comment("点名指定某个方块用什么颜色：一行一条，写法是 命名空间:方块=颜色名（例如 create:red_ramp=red），也可以留空。写在这里的方块直接用这个颜色，不再看下面的名字猜测；列表为空时全部靠名字猜。").defineListAllowEmpty("staticSources.colors.overrides",List.of(),()->"create:red_ramp=red",SourceColor::overrideRow);
  NAME_SCAN=b.comment("允许按方块自己的名字猜颜色。关掉后只有上面 overrides 里点名的方块有颜色，其它方块一律用调色板默认色；名字里本来就没有颜色词的方块，开着也猜不出来。").define("staticSources.colors.guessFromName",true);
  SEND_RANGE=b.comment("注册光源最远发给多少格之外的玩家，单位格（0..32768）；0 表示所有光源发给所有人。客户端本来就会丢掉超出自己激活距离的光源，所以发过去只费带宽和一点客户端算力。强制启用的光源（模式 2）永远都发，因为客户端被告知要一直留着它们。").defineInRange("staticSources.sendRange",1024.0,0.0,32768.0);

  // 生成器（地面扬尘、排烟口、涡流）：全局开关与调度。下面每一项的默认值同时是生成器页面上该栏目的初始值，
  // 页面改过的方块用自己的数，所以改这里只影响之后新放的生成器（页面还没动过的）。
  GEN_ENABLED=b.comment("所有生成器的总开关。关掉后立刻停止产生粒子，已经记住的目标表也会清空；每个生成器自己的页面上还有单独的开关，两个都开着才会发。").define("generators.enabled",true);
  GEN_CULL=b.comment("**生成器周围多近必须有玩家**才会产生粒子，单位格（8..2048，默认 1024）。附近没人时这一轮什么都不发。").defineInRange("particles.generatorCull",1024,8,2048);
  GEN_SCAN_TICKS=b.comment("每隔多少 tick 重新扫描一次世界里已装配的生成器，单位 tick（5..1200）。调大省性能：新放下的生成器最多要等这么久才开始工作；两次扫描之间一直用上次记住的位置。").defineInRange("generators.scanBodyIntervalTicks",40,5,1200);
  GEN_EMIT_TICKS=b.comment("每隔多少 tick 发射一轮，单位 tick（1..20）。它同时是速度的测量间隔（位置差除以本值得到格/tick），所以调大等于把量到的速度按比例放大，扬尘会跟着变多。").defineInRange("generators.emitIntervalTicks",2,1,20);
  GEN_BUDGET=b.comment("所有生成器每轮共用的粒子预算，单位个（0..2000）。按生成器顺序先到先得，额度用光的生成器这一轮什么都不发；0 等于全部关掉。").defineInRange("generators.particlesPerTick",60,0,2000);
  PROFILES_ENABLED=b.comment("逐炮档案总开关。**暂时默认关闭**：关闭时任何一门炮都只按配置文件里的值表现，开炮不会读取逐炮档案，档案页也会拒绝写入并说明原因。这样你可以只改配置、看行为变化，来核对每个配置项的分类与说明是否正确；确认无误后再打开它。").define("profiles.enabled",false);
  EFFECTS_DEBUG=b.comment("**一次打开服务端全部诊断日志的总开关**：打开它等于把下面每一项都打开（炮口扬尘、Voxy/LSS、迪斯科、曳光喂送、爆炸测量）。各项仍可单独打开。正常游玩保持关闭。").define("debug.effects",false);
 
  GEN_DEBUG=b.comment("全局调试日志开关（默认关闭）：不只管生成器，炮口扬尘、Voxy/LSS 刷新、迪斯科、曳光弹喂位置和几个页面也会读它（共 20 多处）。打开后这些地方都会写日志，调参时才开，免得正式服务器刷屏。想一次全开就用上面的 debug.effects。").define("generators.debugLog",false);

  DUST_RATE=b.comment("地面扬尘的产生速率，按车速线性缩放（0..200，每 tick 的粒子数上限）。实际数量 = 车速(格/tick) × 本值，再受 maxPerScan 和 particlesPerTick 夹一次；车速为 0 时什么也不发（除非该方块开了连续发射）。").defineInRange("generators.dust.rate",8.0,0.0,200.0);
  DUST_MAX_PER_SCAN=b.comment("一次扫描最多发几个地面扬尘粒子（0..200）。它只夹住按车速算出来的数量，是防止速度异常时爆量的上限；比产生的量低时以本值为准。").defineInRange("generators.dust.maxPerScan",6,0,200);
  DUST_DEPTH=b.comment("从生成器位置往下最多找多少格地面，单位格（1..128）。找得太浅，车在低处或桥上会找不到地面而不发尘；调大只是多一点射线检测。").defineInRange("generators.dust.searchDepth",12,1,128);
  DUST_MIN_SPEED=b.comment("低于这个水平速度就不发地面扬尘了，单位格/tick（0..4）。0 等于只要动一下就发；该方块开了连续发射时这项不生效。").defineInRange("generators.dust.minSpeed",0.05,0.0,4.0);
  DUST_LIFE_MIN=b.comment("地面扬尘每个粒子的存活时间在这个区间内随机，单位 tick（1..400，最小值）。和 lifetimeMax 一起决定一蓬尘从新到旧的层次。").defineInRange("generators.dust.lifetimeMin",40,1,400);
  DUST_LIFE_MAX=b.comment("地面扬尘存活时间的上限，单位 tick（1..400）。比最小值低时按最小值算（区间反过来读不会报错，只是没有随机）。").defineInRange("generators.dust.lifetimeMax",60,1,400);
  DUST_SIZE_MIN=b.comment("地面扬尘每个粒子的大小在这个区间内随机，单位格（0.1..16，最小值）。").defineInRange("generators.dust.sizeMin",1.0,0.1,16.0);
  DUST_SIZE_MAX=b.comment("地面扬尘粒子大小的上限，单位格（0.1..16）。比最小值低时按最小值算。").defineInRange("generators.dust.sizeMax",2.0,0.1,16.0);
  DUST_STRETCH_MIN=b.comment("地面扬尘每个粒子的水平拉伸倍数下限（1..6）。1 是圆的，越大越扁。只对本模组的 sablelightoverhaul:ground_dust 粒子有效。").defineInRange("generators.dust.stretchMin",1.0,1.0,6.0);
  DUST_STRETCH_MAX=b.comment("地面扬尘水平拉伸的上限（1..6）。比最小值低时按最小值算。同样只对本模组自己的 ground_dust 粒子有效。").defineInRange("generators.dust.stretchMax",1.0,1.0,6.0);
  DUST_UP_MIN=b.comment("地面扬尘被扬起的垂直初速度下限，单位格/tick（0..4）。轨道尘是向上扬再落回来的，横向没有速度。").defineInRange("generators.dust.upSpeedMin",0.05,0.0,4.0);
  DUST_UP_MAX=b.comment("地面扬尘垂直初速度的上限，单位格/tick（0..4）；在这个区间里随机，让一蓬尘有高有低。比最小值低时按最小值算（等于全都一样高）。").defineInRange("generators.dust.upSpeedMax",0.08,0.0,4.0);
  DUST_SCATTER=b.comment("轨道尘撒在多大的半径内，单位格（0..16），以找到的那点地面为中心。调大让它读起来是一片地被搅动，而不是一个点在冒烟。生成器页面里也有同名项，页面上的值优先。").defineInRange("generators.dust.scatter",1.0,0.0,16.0);
  DUST_TINT=b.comment("让地面扬尘取它落到的那块方块的颜色（自有ground_dust按破坏粒子纹理取色；自定义其它粒子仍为地图色）。关掉就用粒子自己写在贴图参数里的颜色。生成器页面里也有同名项，页面上的值优先。").define("generators.dust.tintFromBlock",false);

  SMOKE_INTERVAL_MAX=b.comment("红石信号 15 时每隔多少 tick 发一次烟，单位 tick（1..200）。信号越低间隔按比例拉长，到 intervalAtMinSignal；该方块开了连续发射时按 15 算。").defineInRange("generators.smoke.intervalAtMaxSignal",2,1,200);
  SMOKE_INTERVAL_MIN=b.comment("红石信号 1（最弱）时每隔多少 tick 发一次烟，单位 tick（1..200）。比 maxSignal 那项大才是正常的，反过来的话信号越强反而越慢。").defineInRange("generators.smoke.intervalAtMinSignal",10,1,200);
  SMOKE_COUNT_MAX=b.comment("红石信号 15 时每次发几个烟粒子（0..64）。信号越低按比例减少到 countAtMinSignal；0 等于信号满时也不发。").defineInRange("generators.smoke.countAtMaxSignal",2,0,64);
  SMOKE_COUNT_MIN=b.comment("红石信号 1 时每次发几个烟粒子（0..64）。要让它真的冒烟，这项至少是 1。").defineInRange("generators.smoke.countAtMinSignal",1,0,64);
  SMOKE_RISE=b.comment("生成器页面里 riseSpeed 一栏的初始值，单位格/tick（0..2）。这一项**现在是生效的**：排烟口默认用的是本模组自己的 sablelightoverhaul:smoke 粒子，发烟时它作为向上的初速加到方向×速度之上（页面里每台还能单独调）。").defineInRange("generators.smoke.riseSpeed",0.03,0.0,2.0);
  SMOKE_YAW=b.comment("排烟口朝哪个方向，单位度（0..360）：0 是南（+Z），90 是西（-X），180 是北，270 是东。方向在载具自身坐标系里算好再转到世界，所以船转了管子仍然朝着同一个出口。").defineInRange("generators.smoke.directionYaw",0.0,0.0,360.0);
  SMOKE_PITCH=b.comment("排烟口的仰角，单位度（-90..90）；90 是正上方，-90 是正下方。同样跟着载具的姿态走。").defineInRange("generators.smoke.directionPitch",0.0,-90.0,90.0);
  SMOKE_SPEED=b.comment("烟离开管口的速度，单位格/tick（0..4）。烟不继承载具自己的速度：粒子不跟车走，继承只会把云从管口抹开。").defineInRange("generators.smoke.speed",0.15,0.0,4.0);
  SMOKE_OFFSET_Y=b.comment("管口相对生成器方块的高度，单位格（-16..16）。只有排烟口能自己定高度：地面扬尘的高度由找到的地面决定。").defineInRange("generators.smoke.offsetY",0.0,-16.0,16.0);
  SMOKE_LIFE=b.comment("生成器页面里 lifetime 一栏的初始值，单位 tick（1..600）。这一项**现在是生效的**：排烟口默认用的是本模组自己的 sablelightoverhaul:smoke 粒子，寿命由它接管。").defineInRange("generators.smoke.lifetime",90,1,600);
  SMOKE_SIZE=b.comment("生成器页面里 size 一栏的初始值，单位格（0.05..16）。烟默认用的是本模组自己的 sablelightoverhaul:smoke 粒子，所以**这一栏现在真的决定烟团大小**；把它指向任何原版粒子则这一栏无效（原版粒子尺寸由它自己定）。").defineInRange("generators.smoke.size",0.9,0.05,16.0);

  // 共享的尘土物理：所有会动的尘埃（轨道尘、炮口扬尘、车体扬尘）都从这里取水平和竖直的运动，
  // 只对本模组自己的 sablelightoverhaul:ground_dust 粒子有效，把粒子换成原版写法时这三项都没有作用。
  DUST_DAMPING=b.comment("尘埃水平速度每 tick 保留多少（0.1..1.0）。1 是不减速、会让尘一直滑；默认的 0.75 让它几乎停在被搅起来的地方。只对本模组自己的 ground_dust 粒子有效。").defineInRange("generators.dustPhysics.horizontalDamping",0.75,0.1,1.0);
  DUST_GRAVITY=b.comment("尘埃的下沉加速度，单位格/tick²（0..4）。竖直方向只有它：尘不会被风托住。只对本模组自己的 ground_dust 粒子有效。").defineInRange("generators.dustPhysics.gravity",0.08,0.0,4.0);
  DUST_ALPHA=b.comment("尘埃的不透明度（0.02..1.0）。只对本模组自己的 ground_dust 粒子有效，原版粒子没有这个参数。").defineInRange("generators.dustPhysics.alpha",0.35,0.02,1.0);
 DUST_FADE=b.comment("尘埃淡出占寿命的比例（0.02..1.0）。0.4 是本模组一直以来的行为：寿命前 60% 保持出生不透明度，最后 40% 按二次曲线淡出（见 ParticleFade）。调大 = 更早开始变淡、结尾更柔；1.0 = 出生即开始淡出；越小越接近「一直不透明、到寿命末端突然消失」。**只对本模组自己的 ground_dust 粒子有效**，而且生成器页面里每台还有个 fade 一栏会盖过它。").defineInRange("generators.dustPhysics.fade",0.4,0.02,1.0);
 SMOKE_OPACITY=b.comment("排烟口出生不透明度（0.02..1.0），默认 0.5 = 烟离开管口时半透明。它决定「这团烟有多浓」，与浓度无关的另一半是每 tick 发几个（countAtMaxSignal/countAtMinSignal）。**只对本模组自己的 sablelightoverhaul:smoke 粒子有效**（原版粒子自己决定不透明度，改这里没用），生成器页面里每台还有个 opacity 一栏会盖过它。").defineInRange("generators.smoke.opacity",0.5,0.02,1.0);
 SMOKE_FADE=b.comment("排烟口淡出占寿命的比例（0.02..1.0），默认 0.4 = 寿命前 60% 保持出生不透明度、最后 40% 按二次曲线淡出。调大 = 更早变淡（烟更「薄」、更快散）；调小 = 更久保持浓，接近寿命末端才消失。配合 lifetime 使用：寿命长 + fade 小 = 远处慢慢散开的大片烟。**只对本模组自己的 sablelightoverhaul:smoke 粒子有效**，页面里的 fade 一栏会盖过它。").defineInRange("generators.smoke.fade",0.4,0.02,1.0);

  // 本模组粒子的发送方式：只影响本模组自己发的粒子。
  PARTICLE_FORCE=b.comment("把本模组与白名单里的爆炸粒子用长距离标记送出去，绕过原版的 32 格。关掉恢复原版行为，代价是带宽。").define("particles.forceLoad",true);

  // 炮口：一发炮弹打出去看到的全部东西，按“打一炮调一次”的顺序排。
  CANNON_ENABLED=b.comment("坦克炮口效果的总开关。关掉后这一炮的炮口闪光、火焰、火星、地面扬尘和车体扬尘都不再生成。").define("muzzle.tankCannon.enabled",true);
  CANNON_CULL=b.comment("**炮口粒子发给多远内的玩家**，单位格（8..2048，默认 1024），所有炮种共用。").defineInRange("particles.muzzleCull",1024,8,2048);
  CANNON_BUDGET=b.comment("每一炮的粒子预算，单位个（0..4000）。火焰、火星、地面扬尘按顺序从这个额度里扣，扣到 0 后面的效果就不再生成；车体扬尘有自己的一份，不占这个数。").defineInRange("muzzle.tankCannon.particlesPerShot",600,0,4000);
  CANNON_BREECH_FIX=b.comment("把炮尾那股烟放回炮尾真正的位置，并让它沿炮膛方向真的动起来。关掉恢复 CBC Modern Warfare 原本的样子：烟冒在炮尾旁边、以 0.075 格/tick 几乎不动。只影响装在物理载具上的中型炮，普通方块炮本来位置就是对的。").define("muzzle.tankCannon.breechSmoke.fixPositionAndSpeed",true);
  CANNON_BREECH_SPEED=b.comment("那股炮尾烟速度的倍数（0..64）。原版只给 0.075 格/tick，几乎看不出在移动；0 等于让它固定在原地。").defineInRange("muzzle.tankCannon.breechSmoke.speedScale",10.0,0.0,64.0);
  CANNON_PLUME_SCALE=b.comment("坦克炮单股燃气烟的整体空间倍率（0..4），只缩放宽度和冲程，不再放大发射强度。0 关闭这股烟；替换原生重复烟柱，炮口积烟独立控制。").defineInRange("muzzle.tankCannon.plumeScale",1.4,0.0,4.0);
  CANNON_PLUME_WIDTH=b.comment("单股燃气烟出生半宽，单位格（0.05..16），再乘 plumeScale。与冲程、烟量和炮口积烟分开控制。").defineInRange("muzzle.tankCannon.plume.width",0.45,0.05,16.0);
  CANNON_PLUME_TRAVEL=b.comment("前向烟团的目标冲程，单位格（0.1..128），再乘 plumeScale。出生点沿前半程铺开，前缘速度最快、尾部最慢，燃尽前前缘约冲到 1.25 倍冲程处：烟云沿炮轴拉长，而不是原地一个团。").defineInRange("muzzle.tankCannon.plume.travel",4.0,0.1,128.0);
  CANNON_PLUME_AMOUNT=b.comment("每发烟柱的烟团总量（0..128），默认24，独立于释放时长和烟团大小；降低粒子质量设置会按比例减少。0关闭前向烟柱，不影响炮口积烟。").defineInRange("muzzle.tankCannon.plume.amount",24,0,128);
  CANNON_PLUME_RELEASE=b.comment("每发燃气烟释放时长，单位 tick（1..12）。只控制短时释放，不延长已生成烟团寿命。").defineInRange("muzzle.tankCannon.plume.releaseTicks",4,1,12);
  CANNON_PLUME_LIFE=b.comment("单个燃气烟团寿命，单位 tick（2..100）。与自动炮/炮口积烟的寿命分开。").defineInRange("muzzle.tankCannon.plume.lifetime",18,2,100);
  CANNON_PLUME_EXPANSION=b.comment("烟团寿命末期相对出生半宽的扩散倍率（1..4），不再固定四倍膨胀。").defineInRange("muzzle.tankCannon.plume.expansion",1.8,1.0,4.0);
  CANNON_GROUND_LIFETIME=b.comment("地面扬尘存活多少 tick（2..1200）。尘被掀起来之后要飘一会儿才落下，寿命就是这段时间；两组各调各的，互不参照。").defineInRange("muzzle.tankCannon.groundDust.lifetime",60,2,1200);
  CANNON_GROUND_COLOR=b.comment("地面扬尘的颜色，0xRRGGBB（0..0xFFFFFF）。只有 tintFromBlock 关掉时才看它；开着时用被找到的地面方块自己的颜色。").defineInRange("muzzle.tankCannon.groundDust.color",0xB39A78,0,0xFFFFFF);
  CANNON_FIREBALL_ENABLED=b.comment("坦克炮连续燃气喷焰总开关，每发一个共同形状与时间包络的燃气体。").define("muzzle.tankCannon.fireball.enabled",true);
  CANNON_FIREBALL_COUNT=b.comment("连续燃气体采样预算（0..32）。0 关闭火焰；正数控制体积模式的视线积分采样数（8..24），每发只生成一个整体，不再生成互相独立的火球。保留原键以兼容已有配置。").defineInRange("muzzle.tankCannon.fireball.count",12,0,32);
  CANNON_FIREBALL_LENGTH=b.comment("火球沿炮管轴线铺多远，单位格（0.2..8）。装药越大铺得越长。").defineInRange("muzzle.tankCannon.fireball.length",2.5,0.2,8.0);
  CANNON_FIREBALL_CONE_ANGLE=b.comment("火球张开的半角，单位度（0..70）。默认 22 度；用于设置横向包络，体积模式内部独立形成近口宽燃气球与前向细喷流。").defineInRange("muzzle.tankCannon.fireball.coneAngle",22.0,0.0,70.0);
  CANNON_FIREBALL_SIZE=b.comment("连续燃气体的最小横向直径，单位格（0.2..6）；锥角和长度也可增大横向范围，不再表示每个独立粒子的大小。").defineInRange("muzzle.tankCannon.fireball.size",1.6,0.2,6.0);
  CANNON_FIREBALL_LIFETIME=b.comment("每次炮口燃气闪光存活多少 tick（2..60），默认 3；体积模式在此期间经历爆开、伸展与衰减。").defineInRange("muzzle.tankCannon.fireball.lifetime",3,2,60);
  CANNON_FIREBALL_ALPHA=b.comment("燃气体参考光程的不透明度（0.05..1），采样层数不会成倍放大它；发光由材质独立处理。").defineInRange("muzzle.tankCannon.fireball.alpha",0.85,0.05,1.0);
  GROUND_DUST_MODE=b.comment("地面扬尘模式：FIELD 使用有传播前沿的程序烟场；LEGACY 使用原有撒播粒子。仅替换地面扬尘，车体烟、炮口烟不变。FIELD 有独立数量预算，不使用旧 count/speed/scatter/damping/gravity/size/stretch/fade。").defineEnum("muzzle.tankCannon.groundDust.mode",GroundDustMode.FIELD);
  GROUND_HEIGHT_SCALE=b.comment("地面尘整体高度倍率（0.1..4），默认0.6；同时控制翻卷升幅、扰动升幅和烟团厚度。原lift仍控制基准翻卷半径。").defineInRange("muzzle.tankCannon.groundDust.field.heightScale",.6,.1,4);
  HULL_FLOW_MOTION=b.comment("车体剥离尘运动速度倍率（0.1..4），默认0.65；扫出、剥离和增长一起减慢，不改变释放窗口与实际存活时间。").defineInRange("muzzle.tankCannon.hullDust.flow.motionScale",.65,.1,4);
  GROUND_CORRIDOR_WIDTH=b.comment("地面尘炮轴保护通道全宽，格（0..64），默认6；沿实际炮口方向在世界空间清出圆柱通道，边缘最多约0.8格柔化并带稳定的扰动。0关闭。").defineInRange("muzzle.tankCannon.groundDust.field.corridorWidth",6.0,0,64);
  GROUND_OUTER_LIFT=b.comment("最远处地面尘的翻卷高度比例（0..1），默认0.15；向烟场外缘连续衰减，近处仍由lift控制。0远端贴地，1恢复等高翻卷。").defineInRange("muzzle.tankCannon.groundDust.field.outerLift",.15,0,1);
  GROUND_MOTION_SCALE=b.comment("地面尘运动/翻卷速度倍率（0.1..4），默认0.65；不改变烟量、寿命、传播窗口及尺寸。").defineInRange("muzzle.tankCannon.groundDust.field.motionScale",.65,.1,4);
  GROUND_DUST_DENSITY=b.comment("地面尘数量倍率（0..8），默认1.5，0关闭；FIELD复用地表采样增加软烟瓣，不增加射线；LEGACY乘粒子数，仍受单发预算限制。只影响新射击，不改变单片alpha。FIELD每场最多512片。").defineInRange("muzzle.tankCannon.groundDust.density",1.5,0,8);
  HULL_FLOW_COLOR=b.comment("表面剥离车体尘颜色，0xRRGGBB；默认BEA98C浅土色，比地面尘稍偏白，独立于历史车体尘和炮口薄烟共用颜色。").defineInRange("muzzle.tankCannon.hullDust.flow.color",0xBEA98C,0,0xFFFFFF);
  HULL_FRONT_COVERAGE=b.comment("按当前水平炮向保留车体前部的比例（0.05..1），默认0.60；根据同组候选表面的前后跨度确定，柔化后边界。1覆盖整车；接近垂直射击时不强行指定朝向。只影响生成来源，不改变贴面速度。").defineInRange("muzzle.tankCannon.hullDust.flow.frontCoverage",.60,.05,1);
  HULL_DETACH_FADE=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；贴面烟脱体后的淡出时长，秒（0.05..10），默认0.45；从离开车体时开始，仍受原寿命上限约束。原贴面阶段不变。").defineInRange("muzzle.tankCannon.hullDust.flow.detachFadeSeconds",.45,.05,10);
  HULL_DETACH_EXPANSION=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；贴面烟脱体后的额外扩散倍率（0..8），默认0.8，即结束时尺寸为脱体时的1.8倍；扩散同时稀释透明度，0只淡出。").defineInRange("muzzle.tankCannon.hullDust.flow.detachExpansion",.8,0,8);
  HULL_CLUSTER_DENSITY=b.comment("车体烟簇数量倍率（0..8），默认1，0关闭；乘现有车体count，每炮及客户端存活节点仍最多512。烟在世界空间中自由生成，不再约束在车体表面上。").defineInRange("muzzle.tankCannon.hullDust.flow.density",1.0,0.0,8.0);
  HULL_CLUSTER_SPREAD=b.comment("同来源烟簇在表面切平面内的采样半径，格（0..2），默认0.3；移动物理体上在plot局部坐标采样再转世界。0为同点对照。").defineInRange("muzzle.tankCannon.hullDust.flow.clusterSpread",0.3,0.0,2.0);
  HULL_CLUSTER_STAGGER=b.comment("同来源附加烟点的出生错时上限，秒（0..1），默认0.10；叠加原释放窗口，保留每个烟点完整寿命，0同时出现。").defineInRange("muzzle.tankCannon.hullDust.flow.clusterStagger",0.10,0.0,1.0);
  HULL_SURFACE_COUPLING=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；车体软尘使用物理体局部表面约束：短时贴面、边缘脱离、继承车体运动。关闭恢复0.1.543自由烟团；另需surfaceFlow和车体尘总开关开启。").define("muzzle.tankCannon.hullDust.flow.surfaceCoupling",false);
  HULL_ATTACH_SECONDS=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；车体尘近表面约束最长时间，秒（0..3），默认0.35；抵达边缘可提前脱离，0立即脱离。上限同时不超过该团寿命的65%。").defineInRange("muzzle.tankCannon.hullDust.flow.attachSeconds",.35,0,3);
  HULL_SURFACE_SPEED=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；车体尘贴面速度倍率（0..2），默认0.15；乘旧车体速度的格/秒值及motionScale，单tick贴面位移最多0.22格。").defineInRange("muzzle.tankCannon.hullDust.flow.surfaceSpeed",.15,0,2);
  HULL_CLEARANCE=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；薄尘与车壳表面的间隙，格（0.01..1），默认0.06；过大会显得悬浮，过小可能与车壳交叠。").defineInRange("muzzle.tankCannon.hullDust.flow.clearance",.06,.01,1);
  HULL_INHERIT_MOTION=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；车体尘脱离时继承车体点运动的倍率（0..2），默认0.8；包括旋转产生的局部点速度。已脱离烟不会跟随车体。异常位姿跳变不继承。").defineInRange("muzzle.tankCannon.hullDust.flow.inheritMotion",.8,0,2);
  HULL_FLOW_ENABLED=b.comment("车体尘使用世界空间中的自由软烟团：在炮口附近生成后自行扩散、消散，不贴车壳、不跟随车体。关闭回退旧粒子。另需开启车体尘幕总开关。").define("muzzle.tankCannon.hullDust.surfaceFlow",true);
  HULL_FLOW_LIFT=b.comment("自由车体烟向外的抬升/扩散幅度，格（0..16）；直接作用于世界空间中的烟团，不依赖车体表面。").defineInRange("muzzle.tankCannon.hullDust.flow.lift",.8,0,16);
  HULL_FLOW_TURBULENCE=b.comment("车体尘随机气流扰动强度（0..8），0保留切向扫出和向外剥离，不产生程序化绕圈。").defineInRange("muzzle.tankCannon.hullDust.flow.turbulence",.7,0,8);
  HULL_FLOW_RELEASE=b.comment("车体表面尘依次剥离的时间窗口，秒（0..3）；0同时释放。只是短时表面快照，不持续附着移动载具。").defineInRange("muzzle.tankCannon.hullDust.flow.releaseSeconds",.22,0,3);
  GROUND_FIELD_RESOLUTION=b.comment("烟场采样网格边长（2..8）；圆形范围裁切后最多64探测点。每世界每tick总探测预算256，超限整场跳过；不加载区块。").defineInRange("muzzle.tankCannon.groundDust.field.resolution",8,2,8);
  GROUND_FIELD_SPREAD=b.comment("烟场从中心向外传播的基准时间，秒（0.05..5）；前方传播更快。").defineInRange("muzzle.tankCannon.groundDust.field.spreadSeconds",.55,.05,5);
  GROUND_FIELD_SPEED=b.comment("烟场初始向外速度，格/秒（0..32），随后减速；与旧粒子 speed 的格/tick 单位不同。").defineInRange("muzzle.tankCannon.groundDust.field.speed",3.5,0,32);
  GROUND_FIELD_LIFT=b.comment("烟场翻卷的竖向半径，格（0..16）；前方烟带先外扫再向内回卷，最高升幅约两倍此值，后方更低。").defineInRange("muzzle.tankCannon.groundDust.field.lift",1.8,0,16);
  GROUND_FIELD_TURBULENCE=b.comment("烟场连续空间涡动强度（0..8）；0保留径向扩散，关闭涡动。").defineInRange("muzzle.tankCannon.groundDust.field.turbulence",.7,0,8);
  GROUND_FIELD_SIZE=b.comment("烟场软烟团尺寸倍率（0.25..4）；相对采样间距，0.1.540起基准烟团约为上一版的两倍。尺寸不再抵消浓度，浓淡独立使用alpha；极大尺寸会增加透明片元开销。").defineInRange("muzzle.tankCannon.groundDust.field.sizeScale",1.0,.25,4);
  GROUND_FIELD_STEP=b.comment("烟场允许跨越的采样地表高差，格（0..8）；大落差或缺失地表时停止水平扩散。这是粗采样约束，不是逐方块碰撞。").defineInRange("muzzle.tankCannon.groundDust.field.maxStep",1.25,0,8);
  CANNON_GROUND_COUNT=b.comment("每炮地面扬尘最多几个（0..200），还要受 particlesPerShot 余额限制。实际撒在几个探测点上是取 3..8 个点，尘在这些点之间分配。").defineInRange("muzzle.tankCannon.groundDust.count",120,0,200);
  CANNON_GROUND_DISTANCE=b.comment("每个探测点往下最多找多少格地面，单位格（1..128）。找不到地面的探测点直接作废，全部作废时这一炮没有地面扬尘（调试日志里会说明）。").defineInRange("muzzle.tankCannon.groundDust.searchDistance",24,1,128);
  CANNON_GROUND_RADIUS=b.comment("炮口周围多大范围内去找地面，单位格（0.1..64）。尘是绕着车被掀起来的，所以按半径找一圈地面，而不是只沿炮管方向找一条线。").defineInRange("muzzle.tankCannon.groundDust.radius",16.0,0.1,64.0);
  CANNON_GROUND_SPEED=b.comment("扬尘被推离炮口的速度，单位格/tick（0..16），实际乘 (0.6 + 0.4 × min(1, 装药/6))。越靠近炮口的尘被推得越快，边缘几乎不动（还有一个按距离的衰减）。").defineInRange("muzzle.tankCannon.groundDust.speed",0.5,0.0,16.0);
  CANNON_GROUND_TINT=b.comment("让地面扬尘取该方块破坏粒子纹理的代表色，并遵循原版/NeoForge破坏粒子染色规则（包括草方块例外）；客户端资源包生效，不再取地图色。关掉使用groundDust.color。").define("muzzle.tankCannon.groundDust.tintFromBlock",false);
  CANNON_GROUND_SCATTER=b.comment("每个落点上，地面扬尘撒在多大的半径内，单位格（0..16）。撒得开一点才读得出“地面被掀起”，而不是一个点。").defineInRange("muzzle.tankCannon.groundDust.scatter",2.5,0.0,16.0);
  CANNON_GROUND_DAMPING=b.comment("地面扬尘水平速度每 tick 保留多少（0.1..1.0）。尘不滑行，几乎停在被搅起来的地方；只对本模组自己的 ground_dust 粒子有效。").defineInRange("muzzle.tankCannon.groundDust.damping",0.75,0.1,1.0);
  CANNON_GROUND_GRAVITY=b.comment("地面扬尘的下沉加速度，单位格/tick²（0..4）。竖直方向只有重力，没有任何向上的力。只对本模组自己的 ground_dust 粒子有效。").defineInRange("muzzle.tankCannon.groundDust.gravity",0.06,0.0,4.0);
  CANNON_GROUND_ALPHA=b.comment("地面扬尘的不透明度（0.02..1.0）。只对本模组自己的 ground_dust 粒子有效。").defineInRange("muzzle.tankCannon.groundDust.alpha",0.6,0.02,1.0);
  CANNON_GROUND_SIZE_MIN=b.comment("地面扬尘每个粒子的大小在这个区间内随机，单位格（0.1..16，下限）。尘被搅起来会散成大小不一的一片，这是随机区间的下限。").defineInRange("muzzle.tankCannon.groundDust.sizeMin",2.0,0.1,16.0);
  CANNON_GROUND_SIZE_MAX=b.comment("地面扬尘粒子大小的上限，单位格（0.1..16）。比下限低时按下限算。").defineInRange("muzzle.tankCannon.groundDust.sizeMax",4.0,0.1,16.0);
  CANNON_GROUND_STRETCH_MIN=b.comment("地面扬尘每个粒子的水平拉伸倍数下限（1..6）。1 是圆的，越大越扁（水平方向被拉长）；与上限一起给每个粒子随机取一个倍数。").defineInRange("muzzle.tankCannon.groundDust.stretchMin",1.0,1.0,6.0);
  CANNON_GROUND_STRETCH_MAX=b.comment("地面扬尘水平拉伸的上限（1..6）。比下限低时按下限算。").defineInRange("muzzle.tankCannon.groundDust.stretchMax",1.5,1.0,6.0);
  CANNON_HULL_LIFETIME=b.comment("车体扬尘存活多少 tick（2..1200）。贴在车壳上的一层薄尘，散掉就是它的全部寿命。").defineInRange("muzzle.tankCannon.hullDust.lifetime",20,2,1200);
  CANNON_HULL_COLOR=b.comment("车体扬尘的颜色，0xRRGGBB（0..0xFFFFFF）。车体尘不做方块取色（没有“落到哪块方块”这回事），所以这一项一直是它实际用的颜色。").defineInRange("muzzle.tankCannon.hullDust.color",0xFFFFFF,0,0xFFFFFF);
  CANNON_HULL_COUNT=b.comment("每炮车体扬尘最多几个（0..200）。它有独立的一份，不占 particlesPerShot 的余额；装了 Sable 并且附近能找到载具表面时，尘落在炮向前部的实际表面上；无表面时不生成车体尘。").defineInRange("muzzle.tankCannon.hullDust.count",40,0,200);
  CANNON_HULL_RADIUS=b.comment("在炮口周围多大半径内找载具表面，单位格（0.1..16）；同一个半径还用来判断哪些方块属于这辆车（离得比它远的方块不参与）。").defineInRange("muzzle.tankCannon.hullDust.radius",16.0,0.1,16.0);
  CANNON_HULL_SIZE_MIN=b.comment("车体扬尘每个粒子的大小在这个区间内随机，单位格（0.1..16，下限）。从车壳上蹭下来的一层尘，尺寸取这个区间内的随机值。").defineInRange("muzzle.tankCannon.hullDust.sizeMin",2.0,0.1,16.0);
  CANNON_HULL_SIZE_MAX=b.comment("车体扬尘粒子大小的上限，单位格（0.1..16）。比下限低时按下限算。").defineInRange("muzzle.tankCannon.hullDust.sizeMax",3.0,0.1,16.0);
  CANNON_HULL_SPEED=b.comment("车体扬尘贴着表面被吹开的速度，单位格/tick（0..16），还要乘一个按距离的衰减：越靠近炮口的尘吹得越快。").defineInRange("muzzle.tankCannon.hullDust.speed",1.0,0.0,16.0);
  CANNON_HULL_STRETCH_MIN=b.comment("车体扬尘每个粒子的水平拉伸倍数下限（1..6），默认 3。1 是圆的，越大越扁（水平方向被拉长）；与上限一起给每个粒子随机取一个倍数。").defineInRange("muzzle.tankCannon.hullDust.stretchMin",3.0,1.0,6.0);
  CANNON_HULL_STRETCH_MAX=b.comment("车体扬尘水平拉伸的上限（1..6）。比下限低时按下限算。").defineInRange("muzzle.tankCannon.hullDust.stretchMax",6.0,1.0,6.0);
  CANNON_HULL_DAMPING=b.comment("车体扬尘水平速度每 tick 保留多少（0.1..1.0）。只对本模组自己的 ground_dust 粒子有效。").defineInRange("muzzle.tankCannon.hullDust.damping",0.8,0.1,1.0);
  CANNON_HULL_GRAVITY=b.comment("车体扬尘的下沉加速度，单位格/tick²（0..4）。竖直方向只有重力、没有向上的力，让它贴着车壳往下掉。只对本模组自己的 ground_dust 粒子有效。").defineInRange("muzzle.tankCannon.hullDust.gravity",0.1,0.0,4.0);
  CANNON_GROUND_FADE=b.comment("地面扬尘淡出占寿命的比例（0.02~1.0）。0.5 表示后半生逐渐透明到 0；这个粒子是靠透明度而不是缩小散掉的，所以这一项直接决定「散得干不干净」。").defineInRange("muzzle.tankCannon.groundDust.fade",0.5,0.02,1.0);
 CANNON_HULL_FADE=b.comment("车体扬尘淡出占寿命的比例（0.02~1.0）。贴在车壳上的一层薄尘，同样靠透明度散掉；嫌它消失得太突然就调大，嫌糊住车体就调小。").defineInRange("muzzle.tankCannon.hullDust.fade",0.5,0.02,1.0);
 CANNON_HULL_ALPHA=b.comment("车体扬尘的不透明度（0.02..1.0）。一层薄尘不需要太实，免得糊住车体。只对本模组自己的 ground_dust 粒子有效。").defineInRange("muzzle.tankCannon.hullDust.alpha",0.2,0.02,1.0);

  // 爆炸：一炸就亮一下。决定大小的是装药，不是被炸掉的方块数——CBC 的 CustomExplosion 把
  // block_damaging_explosive_power 直接交给原版 Explosion，所以 radius() 就是炮弹自己的数。
  // 爆炸效果分成两个开关，因为它们的花费不一样：一盏（这里其实是两盏）光很省，而发给客户端的
  // 一次测量数据会在那里展开成很多粒子，不见得省。原来只有一个 explosions.enabled 同时管两件事。
  EXPL_LIGHT_ENABLED=b.comment("**爆炸闪光**的开关，只管那两盏光。爆炸仍会被测量，也仍会给客户端发冲击波数据（见下一条），只是不再亮。为什么与载荷分开：光很省，载荷在客户端展开成很多粒子，不见得省，所以各有各的开关。").define("explosions.light.enabled",true);
  EXPL_SHOCKWAVE_ENABLED=b.comment("**是否把爆炸的测量数据发给客户端**（客户端据此画尘环与球面波）。服务端这一侧只管发；客户端还要它自己的 `shockwave.enabled` 才会真的画。关掉后爆炸仍会亮，只是不再有冲击波。旧键 `explosions.enabled` 已拆成 `explosions.light.enabled` 与这一条：若你曾把它关掉，请把这两条也关掉。").define("explosions.shockwave.enabled",true);
  EXPL_MIN_BLOCKS=b.comment("爆炸报告的方块表小于这个数就不算数，不会有光和冲击波（1..1000000）。注意这个大数的真实含义：CBC 给的是爆炸周围走到的方块，会随炮弹埋进的实心材料变多（KubeJS 脚本就是靠它区分打石头、打地表和空爆），不是它炸掉的方块数；原版 TNT 大约报告 400，苦力怕少得多。所以这是形状门槛：把苦力怕和手雷挡在外面，让 TNT 大小的爆炸通过，任何模组都一样。").defineInRange("explosions.minimumBlocks",200,1,1000000);

SHOCKWAVE_BURIED_DEPTH=b.comment("**判定地心爆炸**：从爆心向上找多少格内有实体方块就算埋在东西底下（0..32，默认 6）。注意它会自动处理弹坑 —— 弹坑上方是天空，所以弹坑仍算地爆。副作用：打在船体内部的爆炸也算埋着，于是不出外围两组（船舱里滚出尘环本来就不对）。").defineInRange("explosions.blastBuriedDepth",6,0,32);

 SHOCKWAVE_MIN_POWER=b.comment("**爆炸规模的第二个门槛：爆炸力（= `Explosion.radius()`，原版 TNT 是 4、爬行者 3、CBC 炮弹 6~32）**，范围 0~64，默认 4 = TNT 及以上都算数。意义：**不是每个来源都有 CBC 的装药数据** —— 原版 TNT 与别的模组的爆炸只有这个半径，而它们可能炸掉的方块少于 200。于是关卡是：**爆炸力 ≥ 本值，或受影响方块数 ≥ explosions.minimumBlocks，任一满足就画**（手雷/爬行者两个都不满足 ⇒ 仍然保持不亮，不会把枪战变成频闪灯）。两种信号各自驱动各自的尺寸：光与球面波按爆炸力缩放，尘环按方块数分档。").defineInRange("explosions.minimumPower",4.0,0.0,64.0);
 SHOCKWAVE_AIR_BAND=b.comment("判空爆的高度带：爆心高出本地地表这么多格以上就直接算空爆（0~128，默认 12）。比它低时才看下/上触达比（地爆被地面挡住 ⇒ 下远小于上）。实测教训：y=93、地表 65 的空爆，向下射线被几十格外的地面挡住，比例判据把它误判成地爆，所以高度必须先于比例生效。").defineInRange("explosions.airBand",12.0,0.0,128.0);
SHOCKWAVE_GROUND_BAND=b.comment("**判定地爆/空爆**：爆心高出本地地表这么多格以内就算地爆（0..64，默认 4）。地表用 MOTION_BLOCKING 高度图读，弹坑里读到的就是坑底，所以弹坑自然归地爆。").defineInRange("explosions.blastGroundBand",4.0,0.0,64.0);
SHOCKWAVE_BURIED_SOLIDITY=b.comment("**判定地心爆炸的第二条**：实测 solidity（0=开阔，1=深埋岩石）超过它也算埋着（0..1，默认 0.85）。").defineInRange("explosions.blastBuriedSolidity",0.85,0.0,1.0);
SHOCKWAVE_DOWN_UP=b.comment("**判定地爆/空爆的第二条**：四个向下八分仪的实测触达 ÷ 四个向上的，小于它就算地爆（0..4，默认 0.75）。地爆被地面挡住 ⇒ 下≪上；空爆 ⇒ 下≈上。它与高度判据是「或」的关系。").defineInRange("explosions.blastDownUpRatio",0.75,0.0,4.0);
  EXPL_YIELD_TIERS=b.comment("每一档冲击波从多大的破片爆炸力开始，升序（整数，列表默认 6/12/18/24/32）。五个数就是六档：低于第一个是第 1 档，达到或超过最后一个是第 6 档；档位只写进发给客户端的消息里（客户端目前只在日志里用它），光的亮度和尺寸由 lightCharge/lightSizeScale 和实测范围决定。留空则所有爆炸都是第 1 档。").defineList("explosions.yieldTiers",java.util.List.of(6,12,18,24,32),value->value instanceof Integer);
  EXPL_CHARGE=b.comment("爆炸光的强度基准（0..64）。实际强度 = 本值 × (爆炸力 / 参考爆炸力) × (0.35 + 0.65 × 暴露度)：埋得越深、被挡得越多，光越暗。它只管亮度；光的半径不是被炸掉的方块数，而是爆炸实测的平均作用距离乘 lightSizeScale（见下面那一项）。").defineInRange("explosions.lightCharge",5.0,0.0,64.0);
  EXPL_SIZE_SCALE=b.comment("爆炸光半径相对实测平均作用距离的倍数（0.05..8）。1.0 等于光和爆炸实际达到的范围一样宽；没有光影包压高光、觉得光太散太大时往下调。").defineInRange("explosions.lightSizeScale",1.0,0.05,8.0);
  SHOCKWAVE_RANGE=b.comment("**冲击波数据发给多远内的玩家**，单位格（8..8192，默认 1024）。客户端照这些数据画，所以它也是冲击波能被看见的距离。").defineInRange("particles.shockwaveRange",1024.0,8.0,8192.0);

  // 曳光弹尾迹：客户端只在自己追踪得到的距离内画尾迹，所以服务端负责喂位置。
  TRAIL_FEED_ENABLED=b.comment("在服务端为每一发曳光弹生成尾迹，这样它在任何距离都看得见，而不是在客户端停止追踪炮弹的地方断掉。关掉后尾迹只剩客户端自己画的那一段（约 160 格）。").define("tracerFeed.feedEnabled",true);
  TRAIL_FEED_DEBUG=b.comment("**每秒打印一行曳光喂送日志**（默认关闭）：这一秒喂了几发、几发没有曳光、几发超出范围、通知了几个人。用来分辨是服务器数据源停了还是客户端没画出来。").define("tracerFeed.debug",false);
    TRAIL_FEED_RANGE=b.comment("**曳光弹尾迹在多远内生成**，单位格（32..8192，默认 1024）。调小会让远处的炮弹只剩光没有尾。").defineInRange("particles.tracerFeedRange",1024,32,8192);
    EXPLOSION_CULL=b.comment("**爆炸粒子发给多远内的玩家**，单位格（8..2048，默认 1024）。只对白名单里的 CBC 爆炸粒子生效（blast wave、烟、碎片），近距离细节粒子不受影响。").defineInRange("particles.explosionCull",1024,8,2048);

  // Voxy/LSS：让爆炸炸出的坑出现在装了 Voxy 的客户端的远处 LOD 里。没有装 LSS（Voxy Server Side）服务端模组时，这一整段都没有作用。
  VOXY_ENABLED=b.comment("爆炸破坏方块之后，请 Voxy/LSS 重发它改动过的列，好让坑出现在客户端远处的 LOD 里。没有装 LSS 服务端模组时完全没有作用；它也不依赖上面那两条爆炸开关，把爆炸光与冲击波都关掉之后坑一样会刷新。").define("voxy.enabled",true);
  VOXY_MAX_COLUMNS=b.comment("一次事件最多标记多少列（1..512）。大爆炸会覆盖很多区块，客户端每重新问一列都是一整列的数据，这个数是单次爆炸开销的天花板；超过的列这一轮不再标记。").defineInRange("voxy.maxColumnsPerEvent",64,1,512);
  VOXY_NUDGE_DELAY=b.comment("**爆炸后多久才把 LOD 重推出去（tick）**，0~400，默认 40（2 秒）。背景：Voxy 一旦摄入某段就基本冻结，只有重新摄取才能让弹坑出现在远处 LOD 里，所以我们爆炸后会主动标记这些列变脏；而客户端重新摄取的那一两秒里那些段读不到，我们的 LOD 遮挡射线就会看到「空洞」而放行，于是只在爆炸时漏。把这个延迟调大（比如 60）能让弹坑稍晚出现、但彻底避开与遮挡的竞态；调 0 = 立即推送（旧行为）。").defineInRange("voxy.nudgeDelay",20,0,400);
  VOXY_MIN_BLOCKS=b.comment("爆炸报告的方块表小于这个数就不做 LOD 刷新，手雷不值得刷一次（1..100000）。注意大炮弹上这个数的真实含义：CBC 给的是爆炸周围走到的方块，会随炮弹埋进的实心材料变多，不是炸掉的方块数，所以它是形状门槛而不是伤害门槛。").defineInRange("voxy.minimumBlocks",4,1,100000);
  VOXY_IMMEDIATE_RANGE=b.comment("距离改动的列这么近的玩家（单位格，0..8192）会立刻收到通知，而不用等 LSS 自己的广播（默认 10 秒）。没有人会被漏掉：其它人仍然会收到正常推送，这一项只决定谁先知道，好让真正看得见爆炸的人在烟还没散的时候 LOD 就刷新了。0 关闭这一档。").defineInRange("voxy.immediateRange",512,0,8192);

  // 手电筒：玩家身上一盏小点光 + 视线方向一个锥。下面这些是玩家一开始用的值，操作员可以在每个玩家
  // 自己的页面上改；页面写下的值存在玩家身上，之后就以页面为准，这里只影响还没被改过的玩家。
  FL_COLOR=b.comment("手电筒光束的颜色，用和灯一样的调色板索引（0..15）。只是新玩家的初始值，页面改过的玩家用自己存下来的颜色。").defineInRange("flashlight.color",0,0,15);
  FL_RANGE=b.comment("手电筒光束的长度，以距离档位表示（1..32）。页面显示的是它换算出来的格数，按默认的 16/32/512 表第 3 档等于 80 格；FlashlightSettings 会把它夹到当前表实际有的档位。这里只能是字面量默认值，不能写 count()/2：在 spec 自己的静态初始化里读配置值会在文件加载前抛异常。").defineInRange("flashlight.range",3,1,32);
  FL_WIDTH=b.comment("手电筒光束的宽度，写成完整的度数（5..90）：5 是很窄的聚光，90 是泛光。存的是度数而不是 1..15 的粗细信号，所以页面上的数就是画出来的数。").defineInRange("flashlight.width",22,5,90);
  FL_DECAY=b.comment("光束沿自身长度变暗的快慢，写成参考形状的百分比（20..300）。100 是参考：轴心全亮、半程一半亮、到头上刚好没有；300 是短而亮的亮斑，20 几乎整条光束都亮着（探照灯就是这样）。它只决定长度方向，横截面看下面的 falloff。").defineInRange("flashlight.decay",100,20,300);
  FL_FALLOFF=b.comment("手电筒的横截面：射灯那五档角度曲线之一（0 强制最大、整个锥面一样亮，1 快，2 均匀，3 慢，4 暗）。和 decay 互相独立，decay 只管长度。页面里没被操作员改过的玩家用这个默认值。").defineInRange("flashlight.falloff",2,0,4);
  FL_INTENSITY=b.comment("手电筒光束的亮度（0..15）。0 等于光束不亮，但点光还可能亮着（那是下面两项管的）。").defineInRange("flashlight.intensity",10,0,15);
  FL_POINT_RADIUS=b.comment("玩家身上那盏小点光的半径，单位格（0..24）；0 关掉。和光束不同，这盏是会随距离衰减的。").defineInRange("flashlight.pointRadius",3,0,24);
  FL_POINT_INTENSITY=b.comment("那盏点光的亮度（0..15）。").defineInRange("flashlight.pointIntensity",10,0,15);
  FL_SYNC_RANGE=b.comment("别人的手电筒最远在多少格内还画出来（0..128）；0 表示完全不再把别人的灯发给客户端。默认值等于原版的玩家追踪距离，那也正是客户端能被要求画的极限：没在追踪的玩家没有位置，光束就没有起点。").defineInRange("flashlight.syncRange",48,0,128);

  // 灯球：这些数只在灯球被放下时由 DiscoBallBlockEntity.useConfiguredDefaults() 抄到球自己身上
  // （DiscoBallBlock.java:48 放置时调用），之后跟着方块走并发给每个客户端，所以一球一景、全场看到
  // 的是同一场表演。改这里只影响以后新放的灯球。客户端自己的 disco.* 只是兜底：只有还没拿到球上
  // 存值的旧球（或同步还没到）才用它，球上存了值就以球为准。
  DISCO_COLORS=b.comment("灯球循环用的调色板索引，逗号分隔（每一项 0..15）：0 是配置的默认色，1..14 是染料色，15 是闪光色；解析不了的项会被忽略。放下灯球时由 useConfiguredDefaults() 抄到球自己身上，之后以球上的值为准；客户端自己的 disco.colors 只在球上还没有存值时兜底。").define("disco.colors","1,4,11,14");
  DISCO_BEAMS=b.comment("灯球几束光（1..32）。放下灯球时抄到球上，之后以球上的值为准（页面改过的球用自己的）；客户端自己的 disco.beams 只在球上还没有存值时兜底。").defineInRange("disco.beams",8,1,32);
  DISCO_SPIN=b.comment("灯球每 tick 转多少度（0..90）；0 等于不转。放下灯球时抄到球上，之后以球上的值为准；客户端自己的 disco.spin 只在球上还没有存值时兜底。").defineInRange("disco.spinDegreesPerTick",1.5,0.0,90.0);
  DISCO_RANGE=b.comment("每束光多长，单位格（1..128）。放下灯球时抄到球上，之后以球上的值为准；客户端自己的 disco.range 只在球上还没有存值时兜底。").defineInRange("disco.range",32,1,128);
  DISCO_WIDTH=b.comment("每束光多宽，用射灯的宽度档（0..15；0 最窄，15 最宽）。放下灯球时抄到球上，之后以球上的值为准；客户端自己的 disco.width 只在球上还没有存值时兜底。").defineInRange("disco.width",2,0,15);
  DISCO_INTENSITY=b.comment("每束光的亮度（0..15）。放下灯球时抄到球上，之后以球上的值为准；客户端自己的 disco.intensity 只在球上还没有存值时兜底。").defineInRange("disco.intensity",6.0,0.0,15.0);
  DISCO_FALLOFF=b.comment("灯球的衰减曲线，和射灯那五档一样（0 强制最大、整个光斑一样亮、1 快、2 均匀、3 慢、4 暗）。放下灯球时抄到球上，之后以球上的值为准；客户端自己的 disco.falloff 只在球上还没有存值时兜底。").defineInRange("disco.falloff",0,0,4);

  FAR_CONTACTS=b.comment("专用服务器允许玩家请求视距外的生物标记。开启后会向客户端发送位置与类型。").define("farContacts.enabled",false);

  // 超视距实体：Sable 把物理体（子场景）的方块数据发到很远（SSRD 的 physicsRenderDistance，本实例 122 区块），
  // 所以几百格外看得到船体；但坐在船上的 contraption 实体——组装后的炮就是这种实体——仍然在原版的客户端视距处
  // 就被丢掉：实测视距 3 时 48 格、视距 4 时 64 格就没了，而同一根炮的挂架方块实体远到 175 格都还能画出来
  // （HANDOFF 8.27 有完整实测数据）。这一节让服务端把名字匹配的实体也发到更远。
  // 追踪是服务端权威的：单人游戏里集成服务器读的就是这份服务端配置；联机时服务端也必须装本模组，只装客户端不生效。
  FAR_ENTITIES=b.comment("把名字匹配的实体（默认就是组装后的炮这种 contraption）发送到 farEntities.range 那么远，而不是原版的客户端视距。关掉即回到原版行为：视距外只剩船体、看不见炮。功能上炮一直是好的（能开火），这一项只管它有没有被发到你的客户端。").define("farEntities.enabled",true);
  FAR_ENTITY_NAMES=b.comment("实体注册名 path 里含有这些片段的才算超视距实体（区分大小写）。默认 [cannon, flap, pitch_contraption, munitions_contraption]。留空等于关闭。匹配的是实体类型名而不是实体自定义名，所以给船上的炮改名不影响匹配。有两个名字必须写全：CBC 组装后的炮注册为 createbigcannons:pitch_contraption，CBCMW 的为 cbcmodernwarfare:munitions_contraption，两者都既不含 cannon 也不含 flap（CBCMW 的 MountedMediumcannonContraption 只是挂在 pitch_contraption 上的炮种，不是实体类型）。cannon 负责 CBC 的 cannon_carriage 等，flap 负责 compactflap。加得越多，远处玩家要收的位置包越多。").defineList("farEntities.names",java.util.List.of("cannon","flap","pitch_contraption","munitions_contraption"),v->v instanceof String);
  FAR_ENTITY_RANGE=b.comment("超视距实体最多发给多少格以内的玩家，单位格（16..8192）。默认 512。Sable 自己的 sub_level_tracking_range 默认 320，SSRD 那根滑条在本实例被设成 122 区块（约 1950 格）；想让炮和船体一样远，就把这里调到不小于它，代价是远处的实体同步量。").defineInRange("farEntities.range",512,16,8192);
  FAR_ENTITY_DEBUG=b.comment("每秒在服务端日志里打印一行超视距实体的实况：服务端一共认得几个名字匹配的实体、各自离每个玩家多远、所在区块是否已经发给这个玩家（原版的 isChunkTracked）、以及它是不是骑在别的实体上。这是分辨两种截然不同的情况唯一的手段——实体在服务端就已经不在你附近（Sable 把整个子场景停到 plot 坐标去了，此时补渲染和补追踪都没用），还是实体就在你附近、只是原版按视距没发给你（此时才是追踪钳制的问题）。正常游玩保持关闭。").define("farEntities.debug",false);

  FLAK_CONE_RANGE=b.comment("高射弹破片替代锥的最大轴向距离（格）；锥角读取 CBC 弹药 burstSpread。主爆炸保持原样。").defineInRange("flakCone.range",24.0,1.0,128.0);
  FLAK_CONE_DAMAGE=b.comment("锥形破片伤害倍率；基准取 CBC flak_burst 单破片伤害，每个目标只命中一次，末端衰减至35%。不生成子弹体、不额外破坏方块。").defineInRange("flakCone.damageScale",1.0,0.0,32.0);
  SPEC=b.build();
 }
 public static int distance(int index){return dev.sablelightoverhaul.BeamLight.distance(index,MIN.get(),MAX.get(),STEP.get());}
 public static int count(){return dev.sablelightoverhaul.BeamLight.count(MIN.get(),MAX.get(),STEP.get());}
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

 /**
  * Whether one diagnostic switch is on: its own value, or the server-side master switch.
  *
  * <p>Every server-side reader goes through here rather than reading its own key, so that "turn on all
  * diagnostics" is one action on this side too, matching the client's {@code ExtraConfig.on}.
  */
 public static boolean on(ModConfigSpec.BooleanValue specific){return EFFECTS_DEBUG.get()||specific.get();}
}

