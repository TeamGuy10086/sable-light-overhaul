package dev.sablelightoverhaul.client;
import dev.sablelightoverhaul.*;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class MuzzleClientConfig {
 public static final net.neoforged.neoforge.common.ModConfigSpec.IntValue HULL_QUERY_BUDGET;
 public enum HullRenderer { SOFT_CLUSTER, SURFACE_SKIN }
 public static final ModConfigSpec.EnumValue<HullRenderer> HULL_RENDERER;
 public static final ModConfigSpec.BooleanValue HULL_VISUAL_SURFACE;
 public static final ModConfigSpec.DoubleValue HULL_VISUAL_REACH,HULL_CLUSTER_THICKNESS,HULL_SKIN_THICKNESS;
 public static final ModConfigSpec SPEC;
 public enum FlameRenderer { BASELINE_MESH, VOLUME }
 public static final ModConfigSpec.EnumValue<FlameRenderer> FLAME_RENDERER;
 public static final ModConfigSpec.EnumValue<MuzzleGasModel.SmokeStyle> TANK_SMOKE_STYLE;
 public static final ModConfigSpec.BooleanValue TANK_SMOKE_CACHED;
 public static final ModConfigSpec.DoubleValue TANK_FLAME_SCALE,TANK_CORE_SCALE,TANK_GAS_SCALE,TANK_JET_SCALE,TANK_SMOKE_SCALE,TANK_SMOKE_SIZE;
 public static final ModConfigSpec.DoubleValue TANK_CLOUD_FORWARD,TANK_CLOUD_SPREAD,TANK_CLOUD_GROWTH,TANK_CLOUD_BRAKE;
 public static final ModConfigSpec.IntValue TANK_CLOUD_LIFETIME;
 public static final ModConfigSpec.DoubleValue TANK_CLOUD_LIFETIME_JITTER,TANK_CLOUD_FADE_START,TANK_CLOUD_FADE_RATE,TANK_CLOUD_FADE_CURVE;
 public static final ModConfigSpec.EnumValue<LightingMode> MODE;
 public static final ModConfigSpec.DoubleValue BIG_BLAST_RANGE_GAIN,BIG_BLAST_INTENSITY;
 public static final ModConfigSpec.IntValue TANK_FLAME_TICKS;
 public static final ModConfigSpec.BooleanValue BIG_FIREBALL_ENABLED;
 public static final ModConfigSpec.IntValue BIG_FIREBALL_LIFETIME;
 public static final ModConfigSpec.DoubleValue BIG_FIREBALL_LENGTH,BIG_FIREBALL_ANGLE,BIG_FIREBALL_ALPHA;
 public static final ModConfigSpec.DoubleValue BIG_FLAME_SCALE,BIG_CORE_SCALE,BIG_GAS_SCALE,BIG_JET_SCALE;
 public static final LightProfile AUTO_PROFILE,BLAST_PROFILE,SMOKE_PROFILE,TANK_PROFILE;
 public static final ModConfigSpec.DoubleValue AUTO_SIZE;
 public static final ModConfigSpec.DoubleValue AUTO_LENGTH_SCALE;
 public static final ModConfigSpec.DoubleValue AUTO_WIDTH_SCALE;
 public static final ModConfigSpec.DoubleValue AUTO_OPACITY;
 public static final ModConfigSpec.IntValue AUTO_FLAME_DURATION;
 public static final ModConfigSpec.IntValue AUTO_LOBE_COUNT;
 public static final ModConfigSpec.DoubleValue AUTO_LOBE_STRENGTH,AUTO_LOBE_TWIST,AUTO_LOBE_LENGTH_SPREAD;
 public static final ModConfigSpec.DoubleValue AUTO_LOBE_SHARPNESS,AUTO_CORE_FOCUS,AUTO_EDGE_SOFTNESS;
 public static final ModConfigSpec.DoubleValue AUTO_TRAIL_START;
 public static final ModConfigSpec.DoubleValue TANK_TRAIL_START;
 public static final ModConfigSpec.IntValue GROUND_FIELD_LIMIT;
 public static final ModConfigSpec.BooleanValue GROUND_FIELD_DEBUG,GROUND_FIELD_CACHED;
 public static final ModConfigSpec.BooleanValue AFTER_SMOKE_ENABLED;
 public static final ModConfigSpec.DoubleValue AFTER_SMOKE_DELAY,AFTER_SMOKE_DURATION,AFTER_SMOKE_LIFE,
   AFTER_SMOKE_BIRTH,AFTER_SMOKE_END,AFTER_SMOKE_SPEED,AFTER_SMOKE_RISE,AFTER_SMOKE_INHERIT,
   AFTER_SMOKE_CURL,AFTER_SMOKE_ALPHA,AFTER_SMOKE_OFFSET;
 public static final ModConfigSpec.IntValue AUTO_DURATION;
 public static final ModConfigSpec.IntValue BIG_DURATION;
 public static final ModConfigSpec.IntValue BIG_HOLD;
 public static final ModConfigSpec.DoubleValue BIG_MINIMUM;
 public static final ModConfigSpec.DoubleValue BIG_DECAY;
 public static final ModConfigSpec.DoubleValue FALLBACK_SPEED;
 public static final ModConfigSpec.DoubleValue HOT_FRACTION;
 public static final ModConfigSpec.IntValue TRACKED_PARTICLES;
 public static final ModConfigSpec.IntValue ADV_MAX;
 public static final ModConfigSpec.BooleanValue ADV_DEBUG;
 static {
  var b=new dev.sablelightoverhaul.AcceptedDefaults("client");
  BigSmokeConfig.define(b);
  GROUND_FIELD_CACHED=b.comment("地面烟场使用预烘焙单面软烟；保留取色、受光、运动和通道裁切，减少重叠面与逐片噪声开销。关闭恢复三片程序烟作对照。只作用于FIELD地面扬尘，不改车体尘。").define("particles.groundDust.fieldCached",true);
  GROUND_FIELD_DEBUG=b.comment("地面烟场采样调试：用蓝色（尚未抵达）/绿色（已抵达）小点代替烟团，显示服务端实际地表命中。仅用于验证坐标、地形和传播，正常游玩关闭。").define("particles.groundDust.fieldDebug",false);
  HULL_QUERY_BUDGET=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；客户端车体尘每tick碰撞短查询与表面覆盖检查总预算（128..8192），默认1024；只复用已加载的物理体方块碰撞形状，不创建刚体。超额贴面烟暂停推进，已脱离烟可跳过该tick的碰撞查询。").defineInRange("particles.hullDust.queryBudget",1024,128,8192);
  HULL_SKIN_THICKNESS=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；车体贴面薄尘的可见厚度，格（0..2），默认0.2；0恢复平片。实际厚度不超过该碰撞面较短半宽的65%，只改变渲染，不增加碰撞查询。间隙仍由服务端clearance控制。").defineInRange("particles.hullDust.skinThickness",0.2,0.0,2.0);
  HULL_RENDERER=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；车体近面尘载体：SOFT_CLUSTER为宽扁软烟簇，SURFACE_SKIN为旧碰撞面薄片对照；仅改变显示，不改已有运动配置。").defineEnum("particles.hullDust.renderer",HullRenderer.SOFT_CLUSTER);
  HULL_VISUAL_SURFACE=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；使用客户端静态烘焙模型表面校正软烟簇位置和法线；动态渲染部件不能完整覆盖，找不到时回退碰撞表面。旧薄片对照不校正。").define("particles.hullDust.visualSurface",true);
  HULL_VISUAL_REACH=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；从碰撞面沿法线寻找视觉表面的最大距离，格（0.05..4），默认0.75；只查询来源方块模型，不扫描整车。").defineInRange("particles.hullDust.visualReach",0.75,0.05,4.0);
  HULL_CLUSTER_THICKNESS=b.comment("【已停用】仅保留旧配置兼容，不参与自由车体烟计算；近面软烟簇法向半径/切向半径（0.05..1），默认0.3；离面后连续变为普通烟簇。切向尺寸使用原车体尘sizeMin/sizeMax，并受连续表面支撑范围限制；跨方块接缝，不裁成单方块薄片。").defineInRange("particles.hullDust.clusterThickness",0.3,0.05,1.0);
  GROUND_FIELD_LIMIT=b.comment("同时存活的地面烟场上限（0..8）；默认4，0关闭客户端烟场。每场最多512软烟团（由服务端数量倍率决定），达到上限后跳过新场，不打断旧场。只作用于 FIELD 模式。").defineInRange("particles.groundDust.fieldLimit",4,0,8);
  b.push("particles.muzzle.tankCannon.afterSmoke");
  AFTER_SMOKE_ENABLED=b.comment("炮口持续余烟：新烟跟随炮口，旧烟留在世界中。独立于爆燃烟团、闪燃照明与曳光尾迹；关闭立即清除该效果。最多同时8个发射器，每个64烟团；扩大尺寸和寿命不会提高数量预算。").define("enabled",true);
  AFTER_SMOKE_DELAY=b.comment("开炮后开始余烟的延迟，秒；只作用于新发射器。").defineInRange("delaySeconds",.65,0,10);
  AFTER_SMOKE_DURATION=b.comment("持续吐烟时间，秒。再次开炮刷新时长，不无限叠加发射器。").defineInRange("durationSeconds",1.8,.1,30);
  AFTER_SMOKE_LIFE=b.comment("每团烟出生后的留存时间，秒；最后消失时间约为延迟+排烟时间+留存时间。").defineInRange("lifetimeSeconds",2.5,.1,20);
  AFTER_SMOKE_BIRTH=b.comment("余烟出口全直径，格，不是半径。").defineInRange("birthDiameter",.3,.02,16);
  AFTER_SMOKE_END=b.comment("余烟扩散后的全直径，格；独立于原大烟团尺寸。").defineInRange("endDiameter",1.4,.02,16);
  AFTER_SMOKE_SPEED=b.comment("出生时沿炮口方向的速度，格/秒，之后逐渐减速、散开。").defineInRange("exitSpeed",2.6,0,32);
  AFTER_SMOKE_RISE=b.comment("冷却后烟气的世界向上漂移速度，格/秒。").defineInRange("riseSpeed",.15,0,12);
  AFTER_SMOKE_INHERIT=b.comment("出生时继承炮口运动速度的比例，包括车身与转炮；不会让旧烟继续跟随炮口。").defineInRange("motionInheritance",.5,0,1);
  AFTER_SMOKE_CURL=b.comment("余烟空间涡动与侧向扩散强度；0关闭侧向卷曲，保留前进与上升。").defineInRange("curl",.7,0,8);
  AFTER_SMOKE_ALPHA=b.comment("余烟浓度，0~1；不改变旧炮口烟团。").defineInRange("opacity",.55,0,1);
  AFTER_SMOKE_OFFSET=b.comment("沿当前炮口轴向前移余烟出口，格。默认0.15，穿管时可加大；跟随炮口转动和CBCMW后坐，不改变炮弹、闪燃或旧烟团。修改对正在吐出的新烟点立即生效。").defineInRange("forwardOffset",.15,0,16);
  b.pop(4);
  TANK_TRAIL_START=b.comment("【已停用】CBC 坦克炮尾烟已全部移除；此键仅保留旧配置兼容。新亮线请调整 projectileStreak 分组。").defineInRange("particles.muzzle.tankCannon.tracerTrailStart",3.0,0.0,32.0);
  MODE=b.comment("光照档位：Advanced 按光影支持情况自动选择；无光影默认使用 SloOnly，未适配光影使用屏幕空间近似。DynamicOnly 是屏幕空间近似的配置值，也可在无光影时手动选用。Off 关闭。").defineEnum("lighting.mode",LightingMode.Advanced);
  FLAME_RENDERER=b.comment("自动炮与坦克炮共同的火焰对照模式。BASELINE_MESH：原火焰纹理的三片轴向mesh，共同淡出且不独立扭动；VOLUME：像素内沿视线积分的燃气体。切换立即生效，不改烟柱、积烟或动态光参数。").defineEnum("particles.muzzle.flameRenderer",FlameRenderer.BASELINE_MESH);
  BIG_BLAST_RANGE_GAIN=b.comment("大炮爆燃光范围倍率，默认2；仅放大爆燃阶段，仍受maximumRange封顶。GPU 档生效，不改变烟光或火球尺寸。").defineInRange("lightProfile.bigCannon.blast.rangeMultiplier",2.0,0.1,8.0);
  BIG_BLAST_INTENSITY=b.comment("大炮爆燃HDR辐射倍率，默认3，范围0~8；SloOnly、屏幕空间近似与 Advanced 生效。烟光不变，不增加光源个数。").defineInRange("lightProfile.bigCannon.blast.intensityMultiplier",3.0,0.0,8.0);
  TANK_FLAME_TICKS=b.comment("坦克可见火球寿命覆盖，默认2tick，接近大炮短促爆燃；0使用服务端原寿命。只影响新火球，不改烟雾寿命、亮度或现有尺寸倍率。").defineInRange("particles.muzzle.tankCannon.flameDurationTicks",2,0,40);
  BIG_FIREBALL_ENABLED=b.comment("大炮开炮瞬间产生一个短促燃气火球，保留原CBC烟雾与跟烟光；只控制视效。").define("particles.muzzle.bigCannon.fireball.enabled",true);
  BIG_FIREBALL_LIFETIME=b.comment("大炮火球寿命，0表示跟随本发爆燃光阶段时长；1~40为独立tick数，只影响新发次。").defineInRange("particles.muzzle.bigCannon.fireball.lifetimeTicks",0,0,40);
  BIG_FIREBALL_LENGTH=b.comment("大炮火球基准长度，格；按装药量做有限缩放，原点为炮口。").defineInRange("particles.muzzle.bigCannon.fireball.length",6.0,0.1,24.0);
  BIG_FIREBALL_ANGLE=b.comment("大炮主燃气团的锥角，度；控制横向膨胀。").defineInRange("particles.muzzle.bigCannon.fireball.coneAngle",30.0,1.0,70.0);
  BIG_FIREBALL_ALPHA=b.comment("大炮燃气火球光学透明度，不修改世界光源亮度。").defineInRange("particles.muzzle.bigCannon.fireball.alpha",0.65,0.0,1.0);
  BIG_FLAME_SCALE=b.comment("大炮火球整体长宽倍率，基线/体积都生效，不缩放原生烟与光场。").defineInRange("particles.muzzle.bigCannon.fireball.scale",1.5,0.1,8.0);
  BIG_CORE_SCALE=b.comment("大炮体积火球亮核几何倍率。").defineInRange("particles.muzzle.bigCannon.fireball.coreScale",1.0,0.1,4.0);
  BIG_GAS_SCALE=b.comment("大炮体积火球主燃气团几何倍率。").defineInRange("particles.muzzle.bigCannon.fireball.gasScale",1.0,0.1,4.0);
  BIG_JET_SCALE=b.comment("大炮体积火球前向喷流几何倍率。").defineInRange("particles.muzzle.bigCannon.fireball.jetScale",1.0,0.1,4.0);
  TANK_SMOKE_CACHED=b.comment("坦克主烟和余烟使用64种预烘焙烟形，每烟团单面受光；保留场景光照，减少重复噪声/阴影/逐灯计算。默认开启，关闭恢复原三片软烟供对照。几何、数量、寿命、颜色配置不改，三瓣按共同局部坐标合成。").define("particles.muzzle.tankCannon.smokeCached",true);
  TANK_SMOKE_STYLE=b.comment("坦克烟雾对照。NATIVE：原版云烟纹理、蓬松成长、冷却和至少30tick留存，只按释放进度设置出生透明度；COMPACT：沿用18tick等服务端寿命、膨胀上限和额外逐粒淡出。两者保留每发烟量上限、短冲程与无深度写入排序；只影响新出生的烟，不修改旧配置值。").defineEnum("particles.muzzle.tankCannon.smokeStyle",MuzzleGasModel.SmokeStyle.NATIVE);
  TANK_FLAME_SCALE=b.comment("坦克炮火焰整体几何倍率，0.1~8，默认2；以炮口为锚点同时缩放长度和宽度，不改亮度、烟量或光照半径；基线和体积都生效。").defineInRange("particles.muzzle.tankCannon.flameScale",2.0,0.1,8.0);
  TANK_CORE_SCALE=b.comment("坦克体积焰亮核的独立几何倍率，0.1~4，默认1；不缩放外层燃气团和喷流，Photon体积模式即时生效。").defineInRange("particles.muzzle.tankCannon.coreScale",1.0,0.1,4.0);
  TANK_GAS_SCALE=b.comment("坦克体积焰主燃气团的独立几何倍率，0.1~4，默认1；含翻卷薄片，不改变亮核、前向喷流、alpha或烟量。").defineInRange("particles.muzzle.tankCannon.gasScale",1.0,0.1,4.0);
  TANK_JET_SCALE=b.comment("坦克体积焰前向喷流的独立几何倍率，0.1~4，默认1；单个共同代理内缩放，不增加粒子或绘制次数。").defineInRange("particles.muzzle.tankCannon.jetScale",1.0,0.1,4.0);
  TANK_SMOKE_SIZE=b.comment("坦克单个烟团的基准半宽，单位格，0~32，默认2；0沿用服务端plume.width×plumeScale。再乘烟团尺寸倍率和出生随机量，NATIVE另有0.9~4.1倍成长。只改烟团几何，不改数量、寿命、透明度、散布或前向冲程；只对新烟生效。").defineInRange("particles.muzzle.tankCannon.smokeSize",2.0,0.0,32.0);
  TANK_SMOKE_SCALE=b.comment("坦克烟团的尺寸倍率，0.1~16，默认1；乘在烟团基准半宽上，只影响新生烟，不改数量、寿命、透明度、散布或前向冲程。").defineInRange("particles.muzzle.tankCannon.smokeScale",1.0,0.1,16.0);
  TANK_CLOUD_FORWARD=b.comment("坦克膨胀云的初始前向速度倍率，0~32，默认1。乘在轴向基准速度上；0 是原地膨胀，1 是当前，>1 喷得更远。实际运动还会被 cloud.brake 快速刹停，不是匀速。只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.forward",1.0,0.0,32.0);
  TANK_CLOUD_SPREAD=b.comment("坦克膨胀云的非均匀横向扩散，0~16，默认0.35。每个云团出生时获得不同方向/不同幅度的侧向和竖向速度，云团之间不会像同一朵球一样整齐展开；0 是纯轴向，16 最大。只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.spread",0.35,0.0,16.0);
  TANK_CLOUD_GROWTH=b.comment("坦克膨胀云的非均匀长大，0~3，默认0.35。每个云团在寿命中横向/竖向长大比例不同，形成不规则体积；0 是各向同性，3 差异最大。只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.growth",0.35,0.0,3.0);
  TANK_CLOUD_BRAKE=b.comment("坦克膨胀云的减速强度，0~1，默认0.5。速度按 1/(1+brake·age·3) 快速衰减：出膛时快、几 tick 内明显减速、之后近乎停在原地；0 不减速，1 最快停住。用来实现“快速喷出、快速减速”，不是匀速。只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.brake",0.5,0.0,1.0);
  TANK_CLOUD_LIFETIME=b.comment("坦克膨胀云的总存活时间，单位 tick，0~200，默认0=沿用服务端每个烟团的寿命。设成 60~120 可让烟停留更久；只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.lifetime",0,0,200);
  TANK_CLOUD_LIFETIME_JITTER=b.comment("每个子烟团的寿命随机范围，0~1，默认0.35。0=所有烟团同寿命；0.35=在基准寿命的 ±35% 内随机（例如 80 tick 会在 52~108 之间），这样不会所有烟团一起卡在最后一点消失。只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.lifetimeJitter",0.35,0.0,1.0);
  TANK_CLOUD_FADE_START=b.comment("从寿命的哪个比例开始淡出，0~1，默认0.20。0.2=前20%保持不透明，之后开始淡出；设大更晚淡出。只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.fadeStart",0.20,0.0,1.0);
  TANK_CLOUD_FADE_RATE=b.comment("淡出速度倍率，0~3，默认1.2。淡出区间内的进度乘这个值；1=在寿命结束时刚好到0，>1=提前淡到0，0=不淡出。只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.fadeRate",1.2,0.0,3.0);
  TANK_CLOUD_FADE_CURVE=b.comment("淡出曲线指数，0.25~4，默认1.6。1=线性；<1 先快后慢（尾部更淡）；>1 先慢后快（更久保持厚度，最后较快消失）。只影响新生云。").defineInRange("particles.muzzle.tankCannon.cloud.fadeCurve",1.6,0.25,4.0);
  // ---- 客户端火焰粒子（每个档位都会生成，与光照档位无关）------------------------------------------
  AUTO_SIZE=b.comment("自动炮口火焰粒子的整体尺寸倍率，范围 0.1~16：火焰长度和半宽都乘它，是唯一的总尺寸开关。粒子在收到炮口闪光数据时就会生成，与光照档位无关。").defineInRange("particles.muzzle.autocannon.flame.sizeMultiplier",9.0,0.1,16.0);
  AUTO_LENGTH_SCALE=b.comment("火焰沿炮口轴向的长度倍率，范围 0.1~3：实际长度 = 0.65~0.9 的随机数 × 尺寸倍率 × 本条。它只改这一个粒子的长度，不改变光。").defineInRange("particles.muzzle.autocannon.flame.flameLengthScale",0.5,0.1,3.0);
  AUTO_WIDTH_SCALE=b.comment("火焰横向半宽的倍率，范围 0.1~3：实际半宽 = 0.075~0.11 的随机数 × 尺寸倍率 × 本条。它只控制火焰胖瘦，不改变长度。").defineInRange("particles.muzzle.autocannon.flame.flameWidthScale",2.0,0.1,3.0);
  AUTO_OPACITY=b.comment("火焰粒子的最大不透明度，范围 0.1~1.0：上限就是 1.0，调不出更实的；越小火焰越透。只在粒子生成时读一次。").defineInRange("particles.muzzle.autocannon.flame.flameOpacity",1.0,0.1,1.0);
  AUTO_FLAME_DURATION=b.comment("自动炮口火焰粒子存活多少 tick，范围 1~20：只在生成时读一次；调大只是让同一团火多停一会儿，不会变亮。").defineInRange("particles.muzzle.autocannon.flame.flameDurationTicks",3,1,20);
  // The lobed field only modifies the VOLUME renderer's existing gas body. It adds no particles
  // and no draw calls; strength 0 is the exact 0.1.505 stream, so this is the live A/B switch.
  AUTO_LOBE_COUNT=b.comment("机炮体积焰的角向瓣数，3~6，默认4。只改 VOLUME 模式下同一个燃气体的轮廓，不增加粒子、绘制次数或烟量；与瓣强度共同生效。").defineInRange("particles.muzzle.autocannon.flame.lobeCount",4,3,6);
  AUTO_LOBE_STRENGTH=b.comment("机炮体积焰瓣强度，0~1，默认0.65。0 是 0.1.505 的原始连续锥体，完全可回退；越大瓣越明显、体轮廓越不光滑。只在 VOLUME 模式生效，不改亮度、光源或烟。").defineInRange("particles.muzzle.autocannon.flame.lobeStrength",0.65,0.0,1.0);
  AUTO_LOBE_TWIST=b.comment("机炮体积焰沿炮轴和年龄的扭转速度，0~3，默认1.1。越大瓣的相位越会随年龄/长度转动，减少静止花瓣感；0 固定相位。只在 VOLUME 模式生效。").defineInRange("particles.muzzle.autocannon.flame.lobeTwist",1.1,0.0,3.0);
  AUTO_LOBE_LENGTH_SPREAD=b.comment("机炮体积焰各瓣的前向长度差，0~1，默认0.55。越大瓣与瓣之间的末端越参差，避免整齐的星形/花形截面；0 所有瓣同长。只在 VOLUME 模式生效。").defineInRange("particles.muzzle.autocannon.flame.lobeLengthSpread",0.55,0.0,1.0);
  AUTO_LOBE_SHARPNESS=b.comment("机炮体积焰瓣边缘锐度，1~4，默认1.6。越大瓣越窄、间隙越清楚；过大会读成几何花瓣，建议先试 1.3~2.0。只在 VOLUME 模式生效。").defineInRange("particles.muzzle.autocannon.flame.lobeSharpness",1.6,1.0,4.0);
  AUTO_CORE_FOCUS=b.comment("机炮体积焰亮核收束，0~1，默认0.5。越大亮核越窄、越集中，外瓣相对更明显；0 保持原亮核宽度。只在 VOLUME 模式生效。").defineInRange("particles.muzzle.autocannon.flame.coreFocus",0.5,0.0,1.0);
  AUTO_EDGE_SOFTNESS=b.comment("机炮体积焰外缘柔化，0~1，默认0.6。越大外缘过渡越宽、越像冷却中的燃气；越小越硬。只在 VOLUME 模式生效。").defineInRange("particles.muzzle.autocannon.flame.edgeSoftness",0.6,0.0,1.0);
  AUTO_TRAIL_START=b.comment("【已停用】CBC 自动炮尾烟已全部移除；此键仅保留旧配置兼容。新亮线请调整 projectileStreak 分组。").defineInRange("particles.muzzle.autocannon.tracerTrailStart",3.0,0.0,32.0);
  // ---- 烟雾光团跟踪（SmokeTracker 每 tick 用，与档位无关）-----------------------------------------
  TRACKED_PARTICLES=b.comment("每发炮弹（每个烟雾组）最多保留多少个烟雾粒子用来算光团的重心与大小，范围 1~24：超出上限就丢掉最早的那个。注意它不是“每发生成多少烟”，而是已经从烟里留下多少个采样点，所以它决定的是这团烟能被代表得多完整。没有烟雾粒子被跟踪时（例如炮口光未启用）不产生作用。").defineInRange("particles.trackedPerShot",8,1,24);
  HOT_FRACTION=b.comment("一个烟雾粒子在它自己寿命的哪个比例之内仍算“热”，范围 0.05~1.0：heat = 1 - age/(lifetime × 本条)，heat 是它在光团加权里的权重，降到 0 就不再参与发光。1.0 表示整条命都算热，越小越只有刚喷出的那一瞬有光。只在有烟雾跟踪时生效。").defineInRange("particles.hotLifetimeFraction",0.6,0.05,1.0);
  // ---- 预算：每 tick 能处理多少光 ----------------------------------------------------------------
  ADV_MAX=b.comment("每 tick 最多构造多少个炮口/曳光面光源快照，范围 1~64，按离相机最近优先，到上限就停止收集。它是 GPU 档位共用的世界光列表上限（不是“所有面光”的通用预算：注册灯的上限是 staticSources.maximum）。**注意**：这个上限之外还有一层取舍 —— 池子（GPU 档 32 个槽）不够装下全部光时，按“类别优先级”先服务炮口光/爆炸光/曳光/手持灯（高），再是别人手上的灯与登记灯/射灯（中），迪斯科球最后（低）；同一优先级内才比距离与屏幕可见度。").defineInRange("budgets.areaLights",32,1,64);
  // ---- 调试开关 ----------------------------------------------------------------------------------
  ADV_DEBUG=b.comment("打印光照选择的调试日志：光源分类的筛选与预算结果（AdvancedLights）、GPU 叠加光路每 2 秒一行的渲染统计（GpuLightRenderer），以及涡流高亮的部分日志。用来排查“某盏灯为什么没进光源列表”，正常游玩保持关闭。").define("debug.lighting",false);
  // ---- 炮口光的形状与时间曲线（LightProfile）------------------------------------------------------
  // 42 个叶子由 LightProfile.java 定义，它们的 .comment(...) 也在那个文件里。
  // 这些值由 GPU 档使用：AdvancedLights.computeWorld 用 profile.light()/curve() 造面光，
  // ClientFlashes 还用 BLAST/SMOKE 的时长拼出整条闪光寿命。
  b.push("lightProfile").push("autocannon");
  AUTO_PROFILE=new LightProfile(b,true,32,16,16,12,5,1,0.5,1);
  b.pop().push("bigCannon").push("blast");
  BLAST_PROFILE=new LightProfile(b,false,25,8,12,8,2,2,0.02,2);
  b.pop().push("smoke");
  SMOKE_PROFILE=new LightProfile(b,false,25,8,12,8,28,2,0.02,2);
  // The tank cannon's own light. Values copied from the big cannon's blast stage on purpose: the shape is the one
  // that was tuned for a barrel weapon, and it is here as a separate section so it can be tuned on its own rather
  // than through the shortcut of sharing the autocannon's.
  b.pop().push("tankCannon");
  // referenceSize 1, like the autocannon: neither barrel weapon is scaled by charge, so a size of 1 means
  // "the numbers above, as written".
  TANK_PROFILE=new LightProfile(b,false,25,8,12,8,2,2,0.02,1);
  b.pop().pop().pop();
  // ---- 炮口光的时长与光团布局--------------------------------
  b.push("particles").push("muzzle").push("autocannon");
  AUTO_DURATION=b.comment("自动炮炮口光在闪光库里的存在时长，单位 tick，范围 1~40。由 autoLight() 读取。").defineInRange("lightDurationTicks",5,1,40);
  b.pop().push("bigCannon");
  BIG_DURATION=b.comment("大型炮炮口光在闪光库里的存在时长，单位 tick，范围 1~1200：这段时间结束后进入烟雾阶段，烟雾的时长由 lightProfile.bigCannon.smoke.time.durationTicks 决定。由 bigLight() 读取。").defineInRange("durationTicks",30,1,1200);
  BIG_HOLD=b.comment("大型炮口光亮度拉满并保持的 tick 数，范围 0~1200：0 表示一出现就开始衰减。由 bigLight() 读取。").defineInRange("peakHoldTicks",4,0,1200);
  BIG_MINIMUM=b.comment("大型炮口光衰减到的下限亮度，范围 0~1：设成大于 0 会留一层余光不熄灭，直到时间走完。由 bigLight() 读取。").defineInRange("minimumBrightness",0.0,0.0,1.0);
  BIG_DECAY=b.comment("保持期结束后每 tick 损失初始亮度的比例，范围 0~1：0 表示不衰减（一直亮到时间结束），1 表示一 tick 就灭。由 bigLight() 读取。").defineInRange("decayPerTick",0.02,0.0,1.0);
  FALLBACK_SPEED=b.comment("服务器停止跟踪后，大型炮口光沿炮弹方向继续前移的速度，单位格每 tick，0 表示停在最后位置。").defineInRange("fallbackSpeed",0.9,0.0,4.0);
  b.pop().pop().pop(); ExtraConfig.define(b); ParticleWind.define(b); HostileSmokeConfig.define(b); NavalAudioConfig.define(b); SPEC=b.build();
 }
 public static AfterSmokeModel.Settings afterSmokeSettings(){return new AfterSmokeModel.Settings(
   AFTER_SMOKE_DELAY.get(),AFTER_SMOKE_DURATION.get(),AFTER_SMOKE_LIFE.get(),AFTER_SMOKE_BIRTH.get(),
   AFTER_SMOKE_END.get(),AFTER_SMOKE_SPEED.get(),AFTER_SMOKE_RISE.get(),AFTER_SMOKE_INHERIT.get(),
   AFTER_SMOKE_CURL.get(),AFTER_SMOKE_ALPHA.get());}

 // ---- Per cannon -------------------------------------------------------------------------------
 //
 // Every builder above now has a form that takes the cannon the shot resolved, and resolves each of its
 // keys through SloProfiles: the cannon's own record first, then the kind's preset, then this client's
 // configuration -- which is exactly what the form page shows the owner, so a value edited on that page and
 // a value read here are the same value. The argument-less forms remain for callers that genuinely have no
 // cannon (a preview, a test), and they simply skip the first two levels.
 //
 // The paths are the configuration paths the page shows, spelled out rather than derived, because a typo here
 // would be a value that silently never applies -- the very failure this change exists to end.

 /** The light-following keys the big cannon's page offers, as configuration paths. */
 private static final String BIG_DURATION_TICKS = "particles.muzzle.bigCannon.durationTicks";
 private static final String AUTO_DURATION_TICKS = "particles.muzzle.autocannon.lightDurationTicks";

 public static LightCurve bigLight(String cannonId) {
  return new LightCurve(
      SloProfiles.resolveInt(cannonId, dev.sablelightoverhaul.api.EffectApi.WeaponKind.CANNON, BIG_DURATION_TICKS, BIG_DURATION.get(), 1),
      BIG_MINIMUM.get(), BIG_DECAY.get(), BIG_HOLD.get());
 }
 public static LightCurve autoLight(String cannonId) {
  return new LightCurve(
      SloProfiles.resolveInt(cannonId, dev.sablelightoverhaul.api.EffectApi.WeaponKind.AUTOCANNON, AUTO_DURATION_TICKS, AUTO_DURATION.get(), 1),
      0, 0.25, 2);
 }
 public static LightLayout bigLayout() {
  return new LightLayout(2, FALLBACK_SPEED.get());
 }
}
