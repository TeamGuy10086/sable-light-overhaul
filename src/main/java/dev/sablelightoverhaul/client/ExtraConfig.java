package dev.sablelightoverhaul.client;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class ExtraConfig {
 public static ModConfigSpec.DoubleValue BIG_PLUME_ALPHA,BIG_PLUME_FADE_FROM,BIG_PLUME_FADE_RATE;
 public static ModConfigSpec.DoubleValue TANK_PLUME_ALPHA,TANK_PLUME_FADE_FROM,TANK_PLUME_FADE_RATE;
 public static ModConfigSpec.DoubleValue CLOUDS_FOG_SCALE;
 public static ModConfigSpec.DoubleValue CLOUDS_NEAR_CULL;
 public static ModConfigSpec.IntValue VEHICLE_HAZE_MAX;
 /** 炮口薄烟的推烟：开炮时把附近已有的薄烟往前推，0 等于关掉。来自 0.1.28 那条分支。 */
 /** 炮口薄烟的运动由客户端自己管：出生外扩速度、外扩上限、下沉加速度、离地保底，以及受动态光的增益。 */
 /** Local quota: how many haze puffs may sit near a muzzle before it stops adding more. */
 public static ModConfigSpec.IntValue MUZZLE_HAZE_LOCAL_MAX;
 public static ModConfigSpec.DoubleValue MUZZLE_HAZE_LOCAL_RADIUS;
 /** 火花的粗细：细长带的半宽与碎片厚度，都写成 quadSize 的比例。 */
 public static ModConfigSpec.DoubleValue AP_STREAK_WIDTH,AP_CHIP_WIDTH;
 /** The shells that deserve a bigger spray: CBC fires the giant cannon's AP shells under these names. */
 public static ModConfigSpec.ConfigValue<java.util.List<? extends String>> AP_BIG_NAMES;
 public static ModConfigSpec.DoubleValue AP_BIG_COUNT_SCALE,AP_BIG_SIZE_SCALE;
 /** 云层照明：各向同性是默认，相位函数与多次散射是两级手动质量档；参与与否按光源类别决定。 */
 public static ModConfigSpec.BooleanValue CLOUDS_ENABLED,CLOUDS_PHASE,CLOUDS_MULTI_SCATTER,CLOUDS_DYNAMIC,CLOUDS_SPOTLIGHTS;
 public static ModConfigSpec.DoubleValue CLOUDS_INTENSITY,CLOUDS_SPHERE_REACH,CLOUDS_REACH_SCALE,CLOUDS_INTEGRATION,CLOUDS_FALLOFF_SCALE,CLOUDS_TRACER_GLOW,CLOUDS_BLAST_GLOW;
 public static ModConfigSpec.BooleanValue STREAK_ENABLED,FLAK_COMPACT,EXPLOSION_MESH_TRAILS;
 public static ModConfigSpec.IntValue FLAK_SMOKE_LIMIT;
 public static ModConfigSpec.DoubleValue FLAK_DENSITY_DETAIL;
 public static ModConfigSpec.IntValue FAR_CONTACT_RANGE, FAR_CONTACT_FOCUS_SIZE;
 public static ModConfigSpec.DoubleValue FAR_CONTACT_FOCUS_ANGLE;
 public static ModConfigSpec.BooleanValue FAR_CONTACT_HEALTH;
 public static ModConfigSpec.DoubleValue STREAK_WIDTH,STREAK_TICKS,STREAK_LENGTH,STREAK_MIN_PIXELS,STREAK_MAX_WIDTH;
 public static ModConfigSpec.DoubleValue STREAK_AUTO_WIDTH,STREAK_AUTO_MIN_PIXELS;
 public static ModConfigSpec.IntValue STREAK_COLOR,STREAK_MAX;
 public static ModConfigSpec.BooleanValue AUTO,BIG,AP_ENABLED,DISCO_ENABLED,DISCO_HEMISPHERE,TRAIL_ENABLED,TRAIL_DEBUG,EFFECTS_DEBUG;
 /** 粒子按深度排序（同一渲染层内从远到近画）；见 research/CBC-CANNON-SMOKE-2026-10-05.zh-CN.md §5。 */
 public static ModConfigSpec.BooleanValue PARTICLE_DEPTH_SORT,PARTICLE_SORT_DEBUG;
    public static ModConfigSpec.IntValue PARTICLE_SORT_MAX_SHIFTS;
    public static ModConfigSpec.DoubleValue OCCLUSION_MARGIN_BLOCKS;
    public static ModConfigSpec.IntValue OCCLUSION_COARSE_STEP;
    public static ModConfigSpec.IntValue OCCLUSION_REFINE_LOOKBACK;
 public static ModConfigSpec.IntValue OCCLUSION_BUDGET,OCCLUSION_HYSTERESIS,OCCLUSION_HORIZON_TICKS,OCCLUSION_PRECAST,OCCLUSION_HOLD_TICKS;
 public static ModConfigSpec.DoubleValue OCCLUSION_CELL_DEGREES;
 /** 排序只在粒子数不超过这个值的层上跑；0 = 不限。见 [particles] sortMaxCount。 */
 public static ModConfigSpec.IntValue PARTICLE_SORT_MAX;
 /** 冲击波的外观：服务器测得数据，客户端照这里的数值绘制。 */
 public static ModConfigSpec.BooleanValue SHOCKWAVE_ENABLED,SHOCKWAVE_DEBUG,SHOCKWAVE_RING_ENABLED,OCCLUSION_ENABLED;
 public static ModConfigSpec.IntValue SHOCKWAVE_MESH_SEGMENTS,SHOCKWAVE_RING_MIN_BLOCKS;
    public static ModConfigSpec.DoubleValue SHOCKWAVE_MESH_SCALE;
 public static ModConfigSpec.EnumValue<ShockwaveMode> SHOCKWAVE_MODE;
 public static ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> GROUND_DUST_FRAMES;
 public static ModConfigSpec.IntValue GROUND_DUST_FRAME_TICKS;
 /** How far a puff''s sprite may be turned, in degrees either way. */
 public static ModConfigSpec.DoubleValue GROUND_DUST_ROTATION;
 public static ModConfigSpec.ConfigValue<java.util.List<? extends String>> SHOCKWAVE_RING_CORE,SHOCKWAVE_RING_GROUPS;
 /** 冲击波用哪一种画法，或者两种都画。见 {@link Shockwave} 与 SHOCKWAVE-REDESIGN.zh-CN.md。 */
 static ShockwaveMode effectiveShockwaveMode() {
  return ImpactExplosionConfig.enabled.get() ? ShockwaveMode.MESH : SHOCKWAVE_MODE.get();
 }
 public enum ShockwaveMode {
  /** 只画我们自己生成的半透明波面：0 粒子，不占粒子的公共层（见 ShockwaveMesh）。 */
  MESH,
  /** 只撒粒子：原来的做法，粒子类型由服务器配置 explosions.shockwaveParticle 决定。 */
  PARTICLES,
  /** 两个都画：波面给形状，粒子给碎屑感。 */
  BOTH
 }
 public static ModConfigSpec.BooleanValue SHOCKWAVE_SHADER;
 public static ModConfigSpec.BooleanValue SLO_ONLY_REFRACTION;
    public static ModConfigSpec.DoubleValue SHOCKWAVE_REFRACT,SHOCKWAVE_DISPERSION;
    public static ModConfigSpec.DoubleValue BEAM_VOLUME,BEAM_GLOW,PLASMA_VOLUME_MIX;
    public static ModConfigSpec.DoubleValue FLARE_GLOW_SHRINK_DISTANCE;
    public static ModConfigSpec.BooleanValue FLARE_SHADOWS;
    public static ModConfigSpec.IntValue BEAM_SAMPLES,BEAM_OCCLUSION,BEAM_SHADOW_RANGE,BEAM_SHADOW_BUDGET,BEAM_DEBUG;
    public static ModConfigSpec.DoubleValue BEAM_WIDTH;
    public static ModConfigSpec.DoubleValue SHOCKWAVE_MESH_BRIGHTNESS,SHOCKWAVE_MESH_INITIAL,SHOCKWAVE_MESH_FINAL,SHOCKWAVE_MESH_SPEED,SHOCKWAVE_MESH_THICKNESS,SHOCKWAVE_MESH_DURATION,SHOCKWAVE_MESH_EXPANSION,SHOCKWAVE_MESH_SIZE,SHOCKWAVE_RING_SPEED_SCALE,SHOCKWAVE_RING_VARIANCE,SHOCKWAVE_RING_COUNT_SCALE;
 public static ModConfigSpec.IntValue AUTO_MAX,BIG_MAX,STATIC_BUDGET,SPOT_BUDGET,HELD_BUDGET,PEER_BUDGET,LIGHT_RESELECT_TICKS,AP_COLOR,AP_HIT_COUNT,AP_RICO_COUNT,AP_RICO_CHIPS,DISCO_BEAMS,DISCO_RANGE,DISCO_WIDTH,DISCO_FALLOFF,DISCO_SCAN_TICKS,DISCO_SCAN_CHUNKS,TRAIL_COLOR;
 public static ModConfigSpec.ConfigValue<String> DISCO_COLORS;
 public static ModConfigSpec.DoubleValue AUTO_RANGE,BIG_RANGE,AUTO_BRIGHT,BIG_BRIGHT,ACTIVATION,STATIC_RANGE,TRACER_CORE,AP_HIT_SPEED,AP_HIT_SPREAD,AP_HIT_SIZE,AP_HIT_LIFE,AP_RICO_SPEED,AP_RICO_SPREAD,AP_RICO_SIZE,AP_RICO_LIFE,AP_BOUNCE,DISCO_SPIN,DISCO_INTENSITY,DISCO_CULL,
   TRAIL_LIFETIME,TRAIL_SIZE,TRAIL_OPACITY,TRAIL_DRIFT,SPOT_ACTIVATION; public static ModConfigSpec.DoubleValue GPU_RED,GPU_GREEN,GPU_BLUE,GPU_INTENSITY,GPU_FALLOFF,GPU_SHADER_SCALE,GPU_NORMAL,GPU_PACK_INTENSITY;
 public static ModConfigSpec.IntValue GPU_SHADOW_COUNT;
 public static ModConfigSpec.IntValue GPU_SHADOW_STEPS;
  /** 质量模式：等距采样取代变长步长，另有步距；两者都只在 Advanced 档读取。 */
  public static ModConfigSpec.BooleanValue GPU_QUALITY_STEPS;
  public static ModConfigSpec.DoubleValue GPU_STEP_SIZE;
  public static ModConfigSpec.DoubleValue GPU_QUALITY_THICKNESS;
  /** 物理体（Sable 子场景）里 Sable 自己在视距外不再枚举的方块实体：炮管、襟翼之类。
   *  这批是从 cbcmuzzlelight 0.1.26 迁过来的（见 HANDOFF 8.27）。 */
  public static ModConfigSpec.BooleanValue SUBLEVEL_FAR;
  public static ModConfigSpec.ConfigValue<java.util.List<? extends String>> SUBLEVEL_FAR_NAMES;
  public static ModConfigSpec.BooleanValue SUBLEVEL_DEBUG;
 public static ModConfigSpec.DoubleValue GPU_SHADOW_SOFTNESS;
 /** 哪些类别的光被允许花掉本机的帧时间来投影；全部是各人自己的选择。 */
 public static ModConfigSpec.BooleanValue GPU_SHADOW_SOURCES,GPU_SHADOW_SPOTLIGHTS,GPU_SHADOW_MUZZLE,GPU_SHADOW_MUZZLE_SOFT,GPU_SHADOW_LONG_RANGE;
 public static ModConfigSpec.BooleanValue GPU_SELF_CULL,GPU_TAKEOVER,GPU_SHADOW_AUTO,TILED_LIGHTS,GPU_FLASHLIGHT_SHADOW,GPU_FLASHLIGHT_PEER_SHADOW;
 /** 船上灯的位姿相位补偿，单位 tick：把灯的位置在两次 tick 之间往前/往后挪。 */
 public static ModConfigSpec.DoubleValue SHIP_POSE_PHASE;
 static void define(ModConfigSpec.Builder b){
  MUZZLE_HAZE_LOCAL_MAX=b.comment("同一发射器在炮口附近使用多少个薄烟绘制载体（0..128，0 不限局部数量），默认 12。达到后优先把新烟量并入同源邻近烟团，仍会继续积浓；每批烟保留出生时间，不会给旧烟续命。这个预算不限制累计不透明度。").defineInRange("haze.muzzleHazeLocalMax",12,0,128);
 MUZZLE_HAZE_LOCAL_RADIUS=b.comment("上面那个配额的作用半径，单位格（0.5..24）。只数这个球内的烟团，其它地方的烟不算数。默认 3.0：与推动半径同量级，正好是机炮那团烟的范围。").defineInRange("haze.muzzleHazeLocalRadius",3.0,0.5,24.0);
 GROUND_DUST_FRAMES=b.comment("地面扬尘粒子按 sprite 的第几帧播放，写成一串帧号（例如 [0, 2, 4] 表示只播这三帧、循环）。留空 = 按原样逐帧播完（旧行为）。帧号会被夹进粒子自己的帧数里，写超了不会出问题。注意：视频率与贴图帧数有关，帧数由粒子贴图决定。").defineList("particles.groundDust.frames",java.util.List.<Integer>of(),v->v instanceof Number);
 GROUND_DUST_ROTATION=b.comment("地面扬尘的贴图随机旋转幅度（度，0~180，默认 90，即每颗粒子在 ±90° 内随机转）。只转贴图，不转我们的拉伸与尺寸：旋转做在贴图坐标上，并按 1/(|cos|+|sin|) 收缩，保证转出来的角不会采到图集里相邻的那一帧。设 0 即关闭（回到全部朝上）。薄烟不受此影响——它那三个瓣的位置是刻意摆的。").defineInRange("particles.groundDust.rotation",90.0,0.0,180.0);
 GROUND_DUST_FRAME_TICKS=b.comment("上面每一帧持续几 tick（1..40）。默认 2：比每刻换一帧慢一倍，扬尘看起来是「翻」而不是「抖」。只在 frames 非空时生效；留空时按原样逐帧。").defineInRange("particles.groundDust.frameTicks",2,1,40);
 VEHICLE_HAZE_MAX=b.comment("薄烟本机绘制载体总上限（0 禁用）。满额时优先合并同源邻近烟量；没有可合并载体才丢弃，并记入 SLO muzzle haze 的 dropped 计数。降低正在使用的上限会移除超额载体。不影响火焰和地面扬尘。").defineInRange("haze.vehicleHazeMax",128,0,1024);
  AP_STREAK_WIDTH=b.comment("坦克炮与 AP 命中共用的细长火花，其总宽写成粒子尺寸的比例（0.05~0.8）：0.12 是 0.1.28 的形状（总宽只有尺寸的 12%，实机偏细），0.30 是现在。").defineInRange("apSparks.streakWidth",0.30,0.05,0.8);
  AP_CHIP_WIDTH=b.comment("跳弹碎片的厚度，同样写成粒子尺寸的比例（0.05~0.8）：0.18 是 0.1.28 的（偏薄），0.26 是现在。碎片的宽度不受这一项影响。").defineInRange("apSparks.chipWidth",0.26,0.05,0.8);
  b.push("lighting");
  GPU_PACK_INTENSITY=b.comment("**Advanced 包内强度**，范围 0~16：Advanced 档由着色器包自己发这些光，包内的方块光刻度会在这个值上再乘一次，所以可用区间与 屏幕空间近似 的 screenScale 不同。0 等于完全关掉这条路。只有在 Advanced 档、且装了带本模组补丁的着色器包时才读取；无包或低档位下这条不产生任何作用。").defineInRange("inPackScale",2.0,0.0,16.0);
  GPU_INTENSITY=b.comment("**屏幕空间近似 屏幕强度**，范围 0~4：屏幕空间近似 档用本模组自己的屏幕空间叠加光路，这是那层光的整体强度；0 时这层光完全不亮（不影响阴影开关，只是不发光）。SloOnly 与 屏幕空间近似 读取。").defineInRange("screenScale",1.0,0.0,4.0);
  GPU_SHADER_SCALE=b.comment("**Iris 附加强度**，范围 0~8：只在 Iris 着色器包生效时再乘一层的强度倍率；有包时 GPU 光在包的色调映射之后相加，通常需要与无包路径不同的值。没装着色器包时这个系数不参与计算。").defineInRange("shaderScale",1.0,0.0,8.0);
  GPU_FALLOFF=b.comment("对归一化光照度取的指数，范围 0.2~4：大于 1 把光收拢到光源附近，小于 1 更平铺。只改变 GPU 光路（屏幕空间近似 的屏幕叠加 / Advanced 的包内注入）的亮度曲线，不改变光源的几何半径、亮度上限或阴影。").defineInRange("falloffPower",1.0,0.2,4.0);
  GPU_NORMAL=b.comment("用深度反推的法线对光的塑形强度，范围 0~1：0 是旧的平面叠加外观，1 是完全的 N·L 漫反射。大于 0 时背对光的面收不到光，因此也顺带挡掉大部分“光穿墙”。只在 GPU 光路（屏幕空间近似 的屏幕叠加 / Advanced 的包内注入）读取，CPU 档位不读。").defineInRange("normalInfluence",1.0,0.0,1.0);
  b.push("color");
  GPU_RED=b.comment("**GPU 光路色调·红**，范围 0~4：给本模组 GPU 光路画出的**所有光**乘一个全局色调；灯自己的调色板颜色仍然另行相乘，所以最终颜色 = 灯色 × 这个色调。只作用于 屏幕空间近似 的屏幕叠加和 Advanced 的包内注入。").defineInRange("red",1.0,0.0,4.0);
  GPU_GREEN=b.comment("**GPU 光路色调·绿**，范围 0~4：同红色通道，乘在本模组 GPU 光路的所有光上；屏幕空间近似 与 Advanced 读取。").defineInRange("green",0.58,0.0,4.0);
  GPU_BLUE=b.comment("**GPU 光路色调·蓝**，范围 0~4：同红色通道，乘在本模组 GPU 光路的所有光上；屏幕空间近似 与 Advanced 读取。").defineInRange("blue",0.25,0.0,4.0);
  b.pop();
  SHIP_POSE_PHASE=b.comment("船上灯的位姿相位补偿，单位 tick，范围 -1~1，默认 0。船体是按两 tick 之间插值画出来的，灯的位置也按同样的插值走 —— 但两边不一定完全同相：灯可能领先或落后船体一小段，表现是船在倾斜/移动时，甲板上的光斑轻微闪烁、灯的方块偶尔被别的方块顶替成白色。这个值把灯的位置往前（正）或往后（负）挪一点，绝对值 0.1~0.3 就能看出来；调到闪烁和跳块消失为止。0 表示完全按插值走，与旧行为一致。只在船上（物理体上的灯）读取，地面灯不受影响。").defineInRange("shipPosePhase",0.0,-1.0,1.0);  TILED_LIGHTS=b.comment("屏幕叠加光路改用逐 tile 的光源列表：一个片元只算真正覆盖它那一格的光，而不是遍历全部活动光源，光源多时省得多。只在 GPU 叠加光路生效；关掉后开销随光源数线性上涨，某些驱动不喜欢整数纹理贴图时也可以关。").define("tiled",true);
  b.pop();
  b.push("shadows");
  GPU_SHADOW_COUNT=b.comment("允许投射屏幕空间阴影的光源数量上限，范围 0~128：这是整个阴影预算，不含你自己手电筒占用的那个保留槽位。槽位给的是“在画面里覆盖面积最大”的光（按亮度加权），不是最近的、也不是最靠屏幕中心的；这一点很关键——从墙里漏出来的光通常正好在那面墙后面，屏幕上根本没有它，只有拿光自己的作用范围去和视野求交才找得回来。每盏灯自己的设置页只能申请其中一部分，绝不会超过这个数。0 表示完全不画阴影。").defineInRange("count",8,0,128);
  GPU_SHADOW_AUTO=b.comment("让帧率决定上面那个阴影数实际用多少：低于 45 fps 用 1 个，低于 60 fps 用 2 个，再高才用满配置值（最低也不会掉到 0，免得看起来像功能自己坏了）。它只会往下调，配置成 0 就一直是 0。关掉则一直使用配置值。**换档带迟滞**：帧率要高出当前档阈值 5 fps 才升档、低于 5 fps 才降档，且每次换档后至少保持 1.5 秒 —— 因为改阴影数是一次可见的跳变（少一盏灯，它照的地方会整体变亮），没有迟滞的话帧率在 60 上下摆动就会让整个房间每秒闪好几次。质量模式（shadows.qualitySteps）打开时这一项被忽略，见那条的说明。").define("countAuto",false);
  GPU_SHADOW_STEPS=b.comment("一条阴影射线最多取几个深度采样点，范围 1~255：又亮又近的光给满，越暗越远的光按比例减少，最少减到一半。光源自己的设置页可以要得更少，但绝不会多于这个数，所以它是“别人放的灯不能吃掉你帧时间”的天花板。").defineInRange("steps",12,1,255);
  GPU_QUALITY_STEPS=b.comment("质量模式（默认关）：射线不再“近密远疏”地变长步长，而是每 stepSize 格打一次等距采样。细的东西——天线、栏杆、一根柱子——因此在哪里都不会被跨过去；默认的变长步长在十几格之外间隔就有好几格，它们会被直接跳过。**性能警告**：等距采样的次数由步距和射线长度决定，每盏投影的灯都要在每个被照像素上跑完它们。量级参照：默认 12 次、1080p、1 盏投影灯 ≈ 2500 万次深度采样/帧；质量模式 0.1 格、一条 25 格射线要 250 次，约是它的 21 倍，4 盏就是 2 亿次/帧。这是出图档：**要出图才打开它**（关掉即回到按亮度与距离分配的 12 次），或者只把 stepSize 调大（0.2 就省一半）。另外质量模式下 countAuto（按帧率降阴影数）会被忽略——否则同一场景两帧的阴影数不同，出图就废了；同时建议把 count 设成 1~2。").define("qualitySteps",false);
  GPU_STEP_SIZE=b.comment("质量模式下的步距，单位格，范围 0.05~2.0，默认 0.1：越小越细腻、也越贵（成本与它成反比）。一条射线最多 255 步，所以 0.1 时能完整分辨约 25 格，更远的灯在这条射线上不再产生阴影（不是变粗，是没有）。判据：1 格厚的遮挡物在 0.1 下必被采到；默认的变长步长常常跨过它。").defineInRange("stepSize",0.1,0.05,2.0);
  GPU_QUALITY_THICKNESS=b.comment("质量模式下“假定遮挡物有多厚”，单位格，范围 0.05~8，默认 0.5：射线在某次采样处落在已有深度**之后**时，只有落后量小于 2×本值才算被这面挡住。非质量模式用的是写死的 4.0（那是为变长步长的宽间隔准备的），质量模式把步距压到 0.1 之后，采样本不再是精度瓶颈，容差就成了瓶颈 —— 容差偏大正是 grazing 角下地面被涂黑的原因（掠射时相邻像素的深度离射线有好几格）。**调小更干净、但厚几何可能漏光；调大更实、但更容易误挡**，这就是这个旋钮的取舍；只在质量模式下读取。").defineInRange("qualityThickness",0.5,0.05,8.0);
  GPU_SHADOW_SOFTNESS=b.comment("软光在阴影射线眼里有多大，单位格，范围 0~2：大于 0 时，要求软边的光源会让射线瞄向这个尺寸球面上的随机一点，而不是光源中心——面光源采样一次，在包做完 TAA 之后本来就是软阴影的全部。它不增加采样次数，正是它让低 steps 看起来平滑而不是一圈圈色带。设为 0 表示对所有光源强制硬边（这是玩家的一票否决）。光柱（探照灯和手电筒锥）无论如何都保持硬边。默认 0.1：静止灯上用 0.2 能看出噪声，0.1 不抖。").defineInRange("softness",0.1,0.0,2.0);
  GPU_SHADOW_LONG_RANGE=b.comment("是否继续画超出射线诚实分辨距离的阴影（Advanced 档）：一次深度采样大约只能分辨 1.25 格，12 次采样对应约 15 格的诚实射程，再远采样点之间隔得太开，中间挡着的墙或柱子会被直接跨过去，画出来的是满是缺口的“阴影”。关掉后每条射线只画到自己的诚实射程：这段范围内的缺口消失，需要更长射线的阴影不再绘制；射程之内的一切完全不变，所以近处看起来和原来一模一样。开是出厂行为。").define("longRange",false);
  GPU_SHADOW_MUZZLE_SOFT=b.comment("炮口闪光和曳光弹壳使用上面那个半径的软边：它们是移动最快、也最不会被直视的光，所以加一圈半影不损失辨识度，还能盖住小 steps 留下的噪声。关掉后它们只有硬边。只在 Advanced 档的 GPU 光路里读取。").define("muzzleSoft",false);
  b.push("classes");
  GPU_SHADOW_SOURCES=b.comment("注册灯与隐藏光源可以投影（Advanced 档）。默认开：这是阻止屋外的灯照亮屋内的关键——包裹式的 N·L 项做不到这件事，而原版方块光在本模组接管灯的白色光池之前本来能挡住。注册灯自己的设置页还要同意（shadowMode 不为 0），两者都同意才真的投影。").define("staticSources",true);
  GPU_SHADOW_SPOTLIGHTS=b.comment("探照灯可以投影（Advanced 档）。它们是光柱，所以无论 softness 设成什么都保持硬边。").define("spotlights",true);
  GPU_SHADOW_MUZZLE=b.comment("炮口闪光和曳光弹壳可以投影（Advanced 档）。这是原有行为，保留成开关是因为炮火是唯一成串到来的光源，也可能是最贵的。").define("muzzle",true);
  GPU_FLASHLIGHT_SHADOW=b.comment("你自己的手电筒在打过补丁的着色器包里投影（Advanced 档）。它有一个排在所有光源之前的保留槽位，炮火再多也抢不走；炮口闪光再用 count 那些槽位。觉得多一趟深度步进不值就关掉。").define("flashlight",true);
  GPU_FLASHLIGHT_PEER_SHADOW=b.comment("其他玩家的手电筒也投影（Advanced 档）。它们的光束紧跟在你自己的后面进光源列表并计入阴影槽位，所以视野里的同伴越多越贵。默认关：别人的光束不是你要的光。它只决定同伴的光束是否也占阴影槽位，不改变炮口闪光是否投影（那是 classes.muzzle）；炮口闪光本来就有槽位，因为 shadows.count 的默认值是 6，不是一个。").define("flashlightPeers",false);
  b.pop();
  b.pop();
  b.push("staticSources");
  GPU_SELF_CULL=b.comment("注册的静态光源不再照亮自己所在的方块（只在 Advanced 的包内光路生效）：叠加光打在灯自己身上会把它的贴图洗成白色，而周围方块上的彩色辉光不受影响。").define("selfLightingCull",true);
  GPU_TAKEOVER=b.comment("Advanced 生效时，注册灯让出自己的原版光照：包内只能加光，要让灯的颜色显出来就必须拿走它原本那一池白光。光源集合变化时每个光源一次客户端光照更新，稳定时不产生开销。关掉则保留灯原来的白光。").define("takeOverVanilla",true);
  STATIC_BUDGET=b.comment("注册静态光源的独立预算，范围 0~64，按离玩家最近优先；探照灯另有预算，两者分开计数，所以谁也不会挤掉谁。0 表示不画这类光。只在 SloOnly / 屏幕空间近似 / Advanced 档读取。").defineInRange("maximum",32,0,64);
  ACTIVATION=b.comment("非探照灯的注册光源离玩家多远之内才亮，单位格，范围 1~4096：超出这个距离的灯这一 tick 不进光源列表；mode 为 2（强制启用）的灯不受它限制。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("activationDistance",512.0,1.0,4096.0);
  STATIC_RANGE=b.comment("发光等级 15 时的照明半径，单位格，范围 1~96：发光等级更低的光源按实际等级线性缩放。光源自己设了 radius 时以光源的数值为准，这条不参与（radius=0 即“自动”时才用它）。只在 SloOnly / 屏幕空间近似 / Advanced档位的面光路径读取。").defineInRange("radiusAtLevel15",15.0,1.0,96.0);
  b.pop();
  b.push("spotlights");
  SPOT_BUDGET=b.comment("探照灯的独立预算，范围 0~64，按离玩家最近优先。它与注册灯分开计数，所以一面墙的探照灯推不掉普通灯，反过来也一样。0 表示不画探照灯。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("maximum",24,0,64);
  SPOT_ACTIVATION=b.comment("探照灯离玩家多远之内才亮，单位格，范围 0~4096；0 表示不设限（旧行为）。距离从玩家量到灯具再减去光束自身长度，所以一束真能照到玩家的长光柱，即使灯具很远也会保留。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("activationDistance",512.0,0.0,4096.0);
  b.pop();
  b.push("budgets");
  HELD_BUDGET=b.comment("玩家手持光（手电筒）的独立预算，范围 0~64，按离相机最近优先（一人一束时就是离镜头最近的那几个人）。它与注册灯、探照灯、炮火分开计数，人多也挤不掉灯或炮火。0 表示不画手持光。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("heldLights",8,0,64);
  PEER_BUDGET=b.comment("其他玩家手电筒的独立预算，范围 0~64（每个同伴占两个名额：光束一个、腰间小光一个），按离相机最近优先。与自己的手电筒分开计数，所以人群挤不掉你自己那束。0 表示不画同伴的光。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("peerLights",8,0,64);
  LIGHT_RESELECT_TICKS=b.comment("每隔多少 tick 重新挑一次“哪些光源进入各自的预算”，单位 tick，范围 1~100：越长越稳、越省；1 表示每 tick 重挑（旧行为），玩家挪动一点点就会看到光源进出而闪烁。只在有预算筛选的档位（SloOnly / 屏幕空间近似 / Advanced）生效。").defineInRange("reselectTicks",5,1,100);
  b.pop();
  b.push("tracerLight");
  AUTO=b.comment("自动炮的曳光弹给自己点一盏光（在弹体位置放一个球形光），关掉就没有这盏光。只在 SloOnly / 屏幕空间近似 / Advanced 档生效。").define("autocannon.enabled",true);
  AUTO_MAX=b.comment("同时存在的自动炮曳光光上限，范围 0~16384，按离玩家最近优先；与大型炮的预算各自独立，互不挤占。0 等于关掉自动炮曳光光。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("autocannon.maximumCount",128,0,16384);
  AUTO_RANGE=b.comment("自动炮曳光球形光的半径，单位格，范围 0.25~96；它不随亮度或炮弹大小变化。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("autocannon.radius",6.0,0.25,96.0);
  AUTO_BRIGHT=b.comment("自动炮曳光光的亮度，按原版光照等级 0~15 计；15 是最亮。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("autocannon.brightness",15.0,0.0,15.0);
  BIG=b.comment("大型炮的曳光弹给自己点一盏光，关掉就没有这盏光。只在 SloOnly / 屏幕空间近似 / Advanced 档生效。").define("bigCannon.enabled",true);
  BIG_MAX=b.comment("同时存在的大型炮曳光光上限，范围 0~16384，按离玩家最近优先；与自动炮预算各自独立。0 等于关掉大型炮曳光光。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("bigCannon.maximumCount",128,0,16384);
  BIG_RANGE=b.comment("大型炮曳光球形光的半径，单位格，范围 0.25~96；默认比自动炮大，因为大型炮弹体更大、更值得被看见。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("bigCannon.radius",12.0,0.25,96.0);
  BIG_BRIGHT=b.comment("大型炮曳光光的亮度，按原版光照等级 0~15 计。只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("bigCannon.brightness",15.0,0.0,15.0);
  TRACER_CORE=b.comment("曳光光球里亮度恒定的核心占比，范围 0~0.9：核心之外按线性空间衰减到 0。0 表示从球心就开始衰减，数值越大中心那团越实、边缘越短。自动炮与大型炮共用这一条，只在 SloOnly / 屏幕空间近似 / Advanced档位读取。").defineInRange("coreFraction",0.15,0.0,0.9);
  b.pop();
  FLAK_DENSITY_DETAIL=b.comment("高射烟冷却后期的透明度纹理强度。0关闭，1为上一版强度，调高会使稀薄处更透明。").defineInRange("flakSmoke.densityDetail",1.5,0.0,3.0);
  FAR_CONTACT_RANGE=b.comment("远景生物请求距离，单位区块，0关闭。客户端需要 WolfMisc，专用服务器须开启远景生物标记，实际范围还受服务器视距限制。").defineInRange("farContacts.requestChunks",0,0,32);
  FAR_CONTACT_FOCUS_ANGLE=b.comment("视线与远景生物中心方向之间的最大夹角，单位度，0关闭注视放大。").defineInRange("farContacts.focusAngle",10.0,0.0,45.0);
  FAR_CONTACT_FOCUS_SIZE=b.comment("注视时远景生物名字的行高或头像最长边，以GUI像素计，与距离无关。").defineInRange("farContacts.focusSize",24,8,64);
  FAR_CONTACT_HEALTH=b.comment("远景生物标记显示当前生命值 / 最大生命值。需要服务器支持发送生命值。").define("farContacts.showHealth",true);
  FLAK_SMOKE_LIMIT=b.comment("高射爆点烟同时存活的粒子上限。预算不足时先移除最早生成的高射烟瓣。").defineInRange("flakSmoke.maxActive",256,4,4096);
  FLAK_COMPACT=b.comment("高射爆点紧凑软烟：一朵热烟冷却后膨胀并淡出，替代主烟点和放射烟迹；火焰换为半数短寿命细线火花，隐藏CBC月牙爆炸粒子。关闭回退CBC爆点烟和火焰，不恢复破片或炮弹尾烟。").define("flakSmoke.compact",true);
  EXPLOSION_MESH_TRAILS=b.comment("实验：普通CBC高爆放射尾烟改为连续网格烟带，主烟不变。默认关闭，开启后新爆炸生效；最多256条烟带，每条8段。仅出生时检测地形碰撞，不跟随移动船体；支持的材质不可用时回退原版。").define("explosions.meshTrails",false);
  ImpactExplosionConfig.define(b);
  b.push("projectileStreak");
  STREAK_ENABLED=b.comment("炮弹视觉暂留亮线；独立于导弹烟尾迹。每枚弹体一条短线，无扩散、漂移或烟点。").define("enabled",true);
  STREAK_WIDTH=b.comment("亮线基础总宽度（格）；远距可由minPixels适度增宽，并受maxWorldWidth限制。").defineInRange("width",0.035,0.005,2.0);
  STREAK_AUTO_WIDTH=b.comment("机炮亮线的基础总宽度（格），适用于单管和双管机炮。").defineInRange("autocannonWidth",0.05,0.005,2.0);
  STREAK_AUTO_MIN_PIXELS=b.comment("远处机炮亮线的最小屏幕宽度（像素），随距离增宽至最多 1 格。").defineInRange("autocannonMinPixels",1.25,0.0,4.0);
  STREAK_MIN_PIXELS=b.comment("远距亮线最小屏幕宽度（像素）；避免离开实体追踪距离后细到不可见，受最大世界宽度限制。").defineInRange("minPixels",0.75,0.0,4.0);
  STREAK_MAX_WIDTH=b.comment("远距亮线增宽上限（格），不会无限随距离放大；不会压低手动设定的基础宽度。").defineInRange("maxWorldWidth",1.0,0.01,8.0);
  STREAK_TICKS=b.comment("视觉暂留时间（tick），20 tick=1秒；决定亮线记录多长的已飞行轨迹。").defineInRange("persistenceTicks",3.0,0.1,8.0);
  STREAK_LENGTH=b.comment("亮线长度上限（格）；高速弹不会产生无限长光柱。").defineInRange("maxLength",48.0,0.1,256.0);
  STREAK_COLOR=b.comment("亮线颜色，RGB 整数 0xRRGGBB。").defineInRange("color",0xFFE3AC,0,0xFFFFFF);
  STREAK_MAX=b.comment("世界中同时可见的弹体亮线数量上限，包含密集阵曳光。密集阵满额时优先显示最新发射的曳光。").defineInRange("maxProjectiles",1024,16,4096);
  b.pop();
  b.push("trail");
  TRAIL_ENABLED=b.comment("保留的导弹发光烟尾迹/调试效果；不再自动附着到 CBC 炮弹。炮弹使用独立 projectileStreak 分组。").define("enabled",false);
  TRAIL_COLOR=b.comment("尾迹帧的颜色，写成 0xRRGGBB，范围 0~0xFFFFFF。所有弹壳共用，不区分口径。").defineInRange("color",0xFFD9A0,0,0xFFFFFF);
  TRAIL_LIFETIME=b.comment("每一帧尾迹存在多少 tick，范围 1~400：它还乘一个 0.8~1.2 的随机数，所以同一串尾迹会参差地消失。它决定绳子看起来有多长，与服务器送来的帧数无关。").defineInRange("lifetime",12.0,1.0,400.0);
  TRAIL_SIZE=b.comment("尾迹帧的基准尺寸，范围 0.02~8.0（大致以格为单位）：实际大小再乘 0.75~1.25 的随机数，并随寿命增长 35%。").defineInRange("size",0.45,0.02,8.0);
  TRAIL_OPACITY=b.comment("尾迹帧的峰值不透明度，范围 0.02~1.0：实际曲线是在一成寿命内淡入，再按平方淡出，所以看到的每一帧都不是满不透明。").defineInRange("opacity",0.9,0.02,1.0);
  TRAIL_DRIFT=b.comment("尾迹帧保留多少弹体自身速度（反向），范围 0~1：它永远不会下落，重力是刻意关掉的。这点值只是让相邻帧不叠成一个点，不是能让帧飞出弹道的速度。关掉尾迹时这条不参与计算。").defineInRange("drift",0.06,0.0,1.0);
  b.pop();
  b.push("apSparks");
  AP_BIG_NAMES=b.comment("哪些弹体算「大炮的弹」，按注册名 path 做包含匹配（区分大小写）。命中这些弹体时下面两个倍率生效——巨炮的 AP 弹比自动炮的大得多，火花理应更多更大。默认六项覆盖三家的穿甲弹：CBC 巨炮 ap_shell / ap_shot；CBCMW 中炮的全口径与次口径 ap_medium / apds_medium / apfsds_medium / aphe_medium；火控那一发是 create_fire_control.apfsds_medium_shell，它被 apfsds_medium 一并覆盖（注意两家的拼写不同：CBCMW 是 apfsds_mediumshell，火控是 apfsds_medium_shell）。自动炮的 ap_autocannon / hvap_autocannon 故意不列，它们保持原样。").defineList("bigShells.names",java.util.List.of("ap_shell","ap_shot","ap_medium","apds_medium","apfsds_medium","aphe_medium"),v->v instanceof String);
 AP_BIG_COUNT_SCALE=b.comment("命中上面那些弹体时，火花与碎屑的**数量**乘这个系数（1~6）。默认 1.8：巨炮的 AP 弹要多出一截，但不至于糊成一片。命中与跳弹同时生效。").defineInRange("bigShells.countScale",1.8,1.0,6.0);
 AP_BIG_SIZE_SCALE=b.comment("命中上面那些弹体时，火花与碎屑的**尺寸**乘这个系数（1~4）。默认 1.6。数量与尺寸一起调才像「更大号的弹」；只调数量会变成一团细碎。").defineInRange("bigShells.sizeScale",1.6,1.0,4.0);
 AP_ENABLED=b.comment("穿甲弹命中时抛出本模组自己的火花；原版火花在着色器包下看不见。关掉后命中和跳弹都不再生成任何粒子，下面这些数值也全部不生效。").define("enabled",false);
  AP_COLOR=b.comment("火花颜色，写成 0xRRGGBB，范围 0~0xFFFFFF；命中与跳弹共用同一种颜色。穿甲弹命中时才生成。").defineInRange("color",0xFFC070,0,0xFFFFFF);
  AP_BOUNCE=b.comment("火花触地后保留多少速度（0~1 的弹性系数）：0 表示落到地面就停住。命中与跳弹共用；只影响已经生成的粒子，不影响生成数量。").defineInRange("bounce",0.3,0.0,1.0);
  AP_HIT_COUNT=b.comment("命中时抛出的火花粒子数，范围 0~200；0 表示命中不冒火花（但跳弹仍按 ricochet.count 生成）。").defineInRange("hit.count",10,0,200);
  AP_HIT_SPEED=b.comment("命中火花的初速，单位 格/tick，范围 0~32：每个粒子还会乘 0.7~1.3 的随机数。0 表示火花原地不飞。").defineInRange("hit.speed",3.5,0.0,32.0);
  AP_HIT_SPREAD=b.comment("命中火花的锥形张角系数，范围 0~4：横截面越远越大，0 表示全部沿一条直线喷出。").defineInRange("hit.spread",0.45,0.0,4.0);
  AP_HIT_SIZE=b.comment("命中火花粒子的尺寸，范围 0.02~4.0（大致以格为单位）；碎屑尺寸是它的 2.4 倍。").defineInRange("hit.size",0.12,0.02,4.0);
  AP_HIT_LIFE=b.comment("命中火花存活时长，单位 tick，范围 2~200；碎屑存活时间是它的两倍。").defineInRange("hit.lifetime",12.0,2.0,200.0);
  AP_RICO_COUNT=b.comment("跳弹时抛出的火花粒子数，范围 0~400：默认明显多于命中，所以跳弹那道更粗。0 表示跳弹不冒火花。").defineInRange("ricochet.count",32,0,400);
  AP_RICO_CHIPS=b.comment("跳弹时额外抛出的碎屑粒子数，范围 0~100：碎屑比火花更大（2.4 倍）、更慢（0.55 倍速）、活得更久（两倍）。0 表示不要碎屑。").defineInRange("ricochet.chips",6,0,100);
  AP_RICO_SPEED=b.comment("跳弹火花的初速，单位 格/tick，范围 0~32；默认比命中更快，因为跳弹是沿着出射角甩出去的。").defineInRange("ricochet.speed",5.0,0.0,32.0);
  AP_RICO_SPREAD=b.comment("跳弹火花的锥形张角系数，范围 0~4；碎屑用的是它的 1.4 倍，所以碎屑团更散。").defineInRange("ricochet.spread",0.8,0.0,4.0);
  AP_RICO_SIZE=b.comment("跳弹火花的尺寸，范围 0.02~4.0；默认比命中更大，让跳弹更容易被看见。").defineInRange("ricochet.size",0.18,0.02,4.0);
  AP_RICO_LIFE=b.comment("跳弹火花存活时长，单位 tick，范围 2~400；碎屑是它的两倍。").defineInRange("ricochet.lifetime",20.0,2.0,400.0);
  b.pop();
  b.push("disco");
  DISCO_ENABLED=b.comment("迪斯科球抛出旋转的彩色光锥。纯粹是客户端观感，服务器不参与计算。关掉后球还在，只是一盏普通光源；下面所有数值都不再生效。").define("enabled",true);
  DISCO_COLORS=b.comment("循环使用的调色板序号，逗号分隔：0 是配置的默认色，1~14 是染料色，15 是闪烁色；留空表示只用 0 号色。只在开启迪斯科光锥时读取一次。").define("colors","1,4,11,14");
  DISCO_BEAMS=b.comment("光锥数量，范围 1~32；越多越热闹，也越贵。它只是“球自己还没存过值”时的默认值：球方块实体的设置页一被改过，就以球自己的值为准。迪斯科整组关闭时不生效。").defineInRange("beams",8,1,32);
  DISCO_SPIN=b.comment("光锥每 tick 旋转多少度，范围 0~90；0 表示停转。与上面一样，只在球自己没存过值时生效；它只影响旋转速度，不影响光锥数量或长度。（键名与服务器侧的同名项一致：spinDegreesPerTick。）").defineInRange("spinDegreesPerTick",1.5,0.0,90.0);
  DISCO_RANGE=b.comment("每根光锥的长度，单位格，范围 1~128；这是光束能照多远，不是它多亮。只在球自己没存过值时生效。").defineInRange("range",32,1,128);
  DISCO_WIDTH=b.comment("每根光锥的横向宽度档位，范围 0~15（0 最细）；只在球自己没存过值时生效，它控制光束的胖瘦，不改变长度。").defineInRange("width",2,0,15);
  DISCO_INTENSITY=b.comment("光锥亮度，按原版光照等级 0~15 计；0 表示光锥不发光。只在球自己没存过值时生效。").defineInRange("intensity",6.0,0.0,15.0);
  DISCO_FALLOFF=b.comment("光锥的衰减档位，与探照灯同一套五种：0 强制最大（整个锥面一样亮）、1 快、2 均匀、3 慢、4 暗。球自己方块实体的设置页一旦被改过，就以它自己的值为准，这条不再生效。").defineInRange("falloff",0,0,4);
  DISCO_CULL=b.comment("离玩家多远之内才计算和绘制迪斯科球，单位格，范围 4~512；超出就不参与，省下每 tick 的扫描与光源开销。").defineInRange("cullDistance",64.0,4.0,512.0);
  DISCO_SCAN_TICKS=b.comment("每隔多少 tick 扫描一次附近的迪斯科球，单位 tick，范围 1~400：越大越省，越小越能及时跟上球被放下或移除。它不改变光锥的更新频率。").defineInRange("scanIntervalTicks",20,1,400);
  DISCO_SCAN_CHUNKS=b.comment("扫描半径，单位区块，范围 1~16：只在这个范围内已加载的区块里找迪斯科球区块实体；球超出范围后不再被计入，直到玩家走近。").defineInRange("scanRadiusChunks",4,1,16);
  DISCO_HEMISPHERE=b.comment("所有光锥保持在球自身局部水平面之上、铺满上半球；关掉则全部挤在一个浅环上。‘上’是球自身的上，所以球滚起来会把半球一起带走。").define("upperHemisphere",true);
  b.pop();
  FLARE_SHADOWS=b.comment("照明弹/信号弹是否参与屏幕空间阴影；默认关闭以减少大范围照明的GPU深度步进。关闭仍保留光照、颜色和辉光，但不计算该光源的遮挡阴影。独立于炮口光阴影，受全局阴影预算上限约束。").define("flares.shadows",false);
  FLARE_GLOW_SHRINK_DISTANCE=b.comment("照明弹/信号弹辉光开始随距离缩小的阈值（格，1..8192，默认160）。阈值内保持现有光晕宽度，超过后按阈值/距离缩小；只保留燃烧点的像素下限，不改变地面照明范围。无需重载光影。").defineInRange("flares.glowShrinkDistance",160.0,1.0,8192.0);
  b.push("shockwave");
  SLO_ONLY_REFRACTION=b.comment("SLO Only 的原生冲击波折射。默认关；开启后只在可见波面出现时复制背景。").define("sloOnlyRefraction",false);
  SHOCKWAVE_ENABLED=b.comment("为服务器测量过的爆炸画冲击波。测量是服务器的事，这条只决定画不画。关掉后爆炸仍会被测量、仍然发光，但不再画波，下面所有数值都不生效。").define("enabled",true);
  SHOCKWAVE_MODE=b.comment("冲击波的画法，枚举 ShockwaveMode，**默认 PARTICLES**：MESH = 只画我们自己生成的半透明波面（**0 粒子**，不占粒子的公共层、没有每 tick 物理与每帧取光）；PARTICLES = 只撒粒子（原来的做法，粒子类型由下面的 ringCore / ringGroups 决定）；BOTH = 两者同时画（波面给形状、粒子给碎屑感）。MESH 那条的几何来自服务器测出的八分仪触达，所以波面会往被炸开的方向鼓出去。**另外**：这一项是你自己的偏好；第三方模组要关其中一路，用的是事件里的子项取消（`cancel(SPAWN_PARTICLES, sablelightoverhaul:ring)` 只关尘环、留下波面），不必改这一项。").defineEnum("mode",ShockwaveMode.BOTH);
  SHOCKWAVE_REFRACT=b.comment("**冲击波锋面的折射强度**，0~1.5，默认 1.0：波面把背后像素拉动多少。0 表示只保留增亮的锋面、不扭曲背景。").defineInRange("refract",1.0,0.0,1.5);
    SHOCKWAVE_DISPERSION=b.comment("**色散**，0~0.2，默认 0.06：三个颜色通道分别偏移多少（R/G/B = 1-δ / 1 / 1+δ），让边缘出现彩色分裂。0 关闭色散。").defineInRange("dispersion",0.06,0.0,0.2);
  b.pop();
  b.push("spotlights.beam");
PLASMA_VOLUME_MIX=b.comment("等离子束红色柱光的混色权重。0 仅叠加红光；1 在柱光最浓处给红色最大权重。").defineInRange("plasmaVolumeMix",0.5,0.0,1.0);
    BEAM_VOLUME=b.comment("**体积光亮度倍率**，0~2，默认 0.25：光柱整体的强度。0 关闭体积光（灯口亮斑仍在）。光柱的颜色始终是灯自己的颜色，这里只缩放亮度。").defineInRange("volume",0.25,0.0,2.0);
    BEAM_GLOW=b.comment("**灯口亮斑亮度倍率**，0~4，默认 1.8：射灯灯口的亮斑与环状光晕的强度。0 关闭灯口效果，只剩体积光。")
        .defineInRange("glow",1.8,0.0,4.0);
    BEAM_WIDTH=b.comment("**光柱的宽度系数**，0.4~1.5，默认 1.0。1.0 = 光柱按灯的几何锥量；小于 1 更窄、大于 1 更宽。只对剖面带曲线的档位（1~4）有意义：档 0「强制最亮」是全平剖面，光柱本来就和受照面同宽，调它只会让它比灯窄。").defineInRange("width",1.0,0.4,1.5);
    BEAM_DEBUG=b.comment("体积光自检色：0关闭；1白色表示采样均受光，蓝色表示部分被灯光空间遮挡，红色表示没有有效锥内采样。").defineInRange("debug",0,0,1);
    BEAM_OCCLUSION=b.comment("【已迁移至每盏灯及玩家手电筒】此旧全局键已停用，仅保留配置兼容。体积光遮挡档位：0关闭体积光；1仅按相机可见深度裁剪光锥；2使用灯光空间32×32 R16距离图和四点遮挡比较过滤。档2未取得本灯图时暂不画，不再用灯口单点显隐整束光。视线背向灯口也可看到被灯照亮的空气。地图仍为方块体素采样，细小物体可能漏采。").defineInRange("occlusion",2,0,2);
    BEAM_SAMPLES=b.comment("体积光沿视线的积分采样次数（3..64），默认12。增加可改善遮挡及细光束的积分精度，但GPU耗时近似随次数增加；多盏大面积光束可降至6或8。固定采样，不引入逐帧随机抖动；不改变遮挡图分辨率。").defineInRange("samples",12,3,64);
    BEAM_SHADOW_RANGE=b.comment("**阴影图的射程**，8~96 格，默认 40。阴影图只记录这个距离以内的遮挡：更远的墙不会切断光柱。调大能挡住远处的山，代价是每张图多走几倍的格子；调小则只在灯附近有效。只在 beam.occlusion=2 时读取。").defineInRange("shadowRange",40,8,96);
    BEAM_SHADOW_BUDGET=b.comment("每次光表刷新最多重建几张灯光空间遮挡图（1..4），默认2；每图1024条体素射线，比旧24×24图多78%。静止且未失效的灯复用缓存，移动/转向或遮挡变化才重算。排队旧图按其建图姿态查询，首次没有图的灯等待预算。刷新频率由光表更新控制。").defineInRange("shadowBudget",2,1,4);
  b.pop(); b.pop();
  b.push("shockwave");
        SHOCKWAVE_SHADER=b.comment("**把引爆波改由光影包合成**（默认开）。打开后波面在包的合成阶段按每像素求交绘制，因此会被雾、泛光、TAA 一起处理，也能吃到 LOD 深度；关掉回到本模组自己的网格绘制。需要配套的着色器补丁，包不对时什么都画不出来，所以这是一个开关而不是自动判断。").define("shaderWave",true);
    SHOCKWAVE_MESH_BRIGHTNESS=b.comment("**球面激波的亮度（≈不透明度）**，范围 0~2，默认 0.2：环前缘那一条的峰值 alpha。它用的是**加法混合**（好处是一条闭合球面的前后两半不需要排序），所以调低 = 变暗变淡、调高 = 更亮；两边轮廓叠加处最容易过曝成纯白，觉得刺眼就先调它。只在 MESH/BOTH 下有意义。").defineInRange("meshBrightness",0.2,0.0,2.0);
  
  SHOCKWAVE_MESH_THICKNESS=b.comment("**波面的总厚度**，范围 0.02~1.0，默认 1.0，单位是**半径的比例**：1.0 = 覆盖整个半径（对称后就是整球），0.25 = 最厚时占四分之一。它是上限，实际厚度 = 本值 × 初始/最终比例。").defineInRange("thickness",1.0,0.02,1.0);
    SHOCKWAVE_MESH_INITIAL=b.comment("**出生时的厚度比例**，0~1，默认 1.0：刚炸开时波面占「总厚度」的多少。1.0 表示一开始就填满整个半径。").defineInRange("initialRatio",1.0,0.0,1.0);
    SHOCKWAVE_MESH_FINAL=b.comment("**末期的厚度比例**，0~1，默认 0.25：压缩结束时占「总厚度」的多少。越小马赫杆越细。").defineInRange("finalRatio",0.25,0.0,1.0);
    SHOCKWAVE_MESH_SPEED=b.comment("**压缩速度**，0.2~4.0，默认 1.0：指数。>1 先慢后快（长时间保持厚壳再急速收拢），<1 一上来就迅速变薄。").defineInRange("compressionSpeed",1.0,0.2,4.0);
  SHOCKWAVE_MESH_SCALE=b.comment("**冲击波的整体尺寸倍率**，范围 0.25~4.0，默认 1.0：所有等级（小/中/大炮弹、不同装药）共用这一个系数，按等比例放大或缩小波面外半径。它只改外观尺寸，不改伤害、波及范围或持续时间。").defineInRange("meshScale",1.0,0.25,4.0);
  SHOCKWAVE_MESH_SEGMENTS=b.comment("球面激波的细分：经线方向多少段，范围 8~64，默认 36（纬线 = 它的一半，即默认 36×18）。它只影响圆滑程度与开销（顶点数随段数的平方增长），不影响亮度与厚度。觉得圆的边缘有折角就调大，想省一点就调小。").defineInRange("meshSegments",36,8,64);
  SHOCKWAVE_MESH_SIZE=b.comment("**球面爆轰波的大小系数**，范围 0.05~20，默认 1.5。**半径 = 系数 × 爆炸威力 × 2.5 格**，标定点是用户实机定的：**威力 24 的强化高爆弹 ⇒ 60 格**（1.5 = 你现在这套效果，1.0 是原始标定）。于是同一套设置下 **威力 4 的 TNT ⇒ 10 格**、6 ⇒ 15、12 ⇒ 30、18 ⇒ 45、32 ⇒ 80，**更小的当量自动按比例缩小**。威力 = CBC 传进来的 `block_damaging_explosive_power`（`Explosion.radius()`，就是配置里 yieldTiers = [6,12,18,24,32] 那个数），与实测触达无关。要整体放大就往 2.0、3.0 调，缩小就 0.5。**只在 MESH/BOTH 下有意义**。").defineInRange("meshSize",1.5,0.05,20.0);
  SHOCKWAVE_MESH_DURATION=b.comment("**球面爆轰波的全动画时间，单位秒**，范围 0.05~5.0，默认 0.6（= 12 tick）。从出生到消失共用多久；与 meshSize 一起决定它铺开得多快：**平均速度 = meshSize ÷ meshDuration**（[debug] shockwave 会把这个数印出来）。**只在 MESH/BOTH 下有意义**，与粒子模式的寿命是两个互不相干的数。").defineInRange("meshDuration",0.6,0.05,5.0);
  SHOCKWAVE_MESH_EXPANSION=b.comment("**球面爆轰波的节奏**，范围 0.05~4，默认 1.0，**只影响 mesh**（粒子的那条是上面的 expansion）。它只决定「这段距离在时间里怎么分配」：**1.0 = 匀速**（全程均匀扫过，最好推理的基线）；**大于 1 = 开头猛、后段拖**（4 时半程就到 94% 半径 ⇒ 像「跳一下就定住」）；**小于 1 = 先慢后快**（末段最快）。真实爆炸是开头最快随后减速，所以 1.5~2.5 更像真实爆炸；想要「一路扫出去」就用 1.0。").defineInRange("meshExpansion",1.0,0.05,4.0);
  SHOCKWAVE_RING_ENABLED=b.comment("**地面尘环的总开关**（KubeJS 脚本的移植）。关掉后只剩球面爆轰波（MESH），一个环粒子都不发。**只在 PARTICLES/BOTH 下有意义**。").define("ringEnabled",true);

  SHOCKWAVE_RING_MIN_BLOCKS=b.comment("尘环的最低门槛：方块数低于它的爆炸一个粒子都不发（1..1000000，默认 200，与本模组的 explosions.minimumBlocks 同步）。**KubeJS 原脚本这里是 550**，会让较弱的炮弹完全不被识别，所以调到我们的 200。注意这个数的真实含义：它数的是爆炸射线**走过**的方块，会随炮弹埋进的材料变多，不是炸掉的方块数。").defineInRange("ringMinimumBlocks",200,1,1000000);
  SHOCKWAVE_RING_COUNT_SCALE=b.comment("**冲击波环粒子总量的比例系数**，0~4，默认 2.0（上限 4 = 基础量已烘培为原来的 0.5，所以 4.0 对应「最多能喷的量」与烘培前一致）。代码里已把原先配置中的 0.5 烘培为基础量，所以 1.0 就等于你之前跑到现在的浓淡。环的总数 = 脚本档位公式算出的数量 × 本值（四舍五入）。调小 = 更稀（也更省），调大 = 更浓；**设 0 = 该档不生成任何环粒子**（等于只关环，其余功能照旧）。注意它与 ringEnabled 的区别：后者是总开关，这条只缩放数量。").defineInRange("ringCountScale",2.0,0.0,4.0);
  SHOCKWAVE_RING_CORE=b.comment("尘环的**主体**粒子（权重写在最前面，格式：权重 空格 粒子串），默认 [1 createbigcannons:shrapnel_smoke{lifetime:15}]。这一组在**地爆与地心爆炸**都生成，是「扫过的尘环」的本体。默认那个 `createbigcannons:shrapnel_smoke` 是 CBC 的**叶子烟粒子**（`extends BaseAshSmokeParticle`、走原版 `PARTICLE_SHEET_TRANSLUCENT`、**自己不再生成任何东西**；2026-10-09 字节码核对），所以它就是那团灰本身 —— 旧注释里「不渲染的控制器、真正的烟由它自己吐出来」是错的，已更正。").defineList("ringCore",java.util.List.of("1 createbigcannons:shrapnel_smoke{lifetime:15}"),v->v instanceof String);
  SHOCKWAVE_RING_GROUPS=b.comment("尘环的**外围**两组（同一格式），默认 [2 minecraft:cloud, 2 minecraft:end_rod]。**只在真正的地爆生成**：空爆一个粒子都不发，地心爆炸只留上面那一组主体 —— 这就是用户要的三分类。权重与主体合起来算总抽签（默认 1:2:2，与原脚本逐位一致）。").defineList("ringGroups",java.util.List.of("2 minecraft:cloud","2 minecraft:end_rod"),v->v instanceof String);
  SHOCKWAVE_RING_SPEED_SCALE=b.comment("尘环速度的全局倍率（0..10，默认 1.5 = 原脚本的 globalSpeedMultiplier）。档位速度 = (speedBase + 方块数×speedFactor) × 这个值。").defineInRange("ringSpeedScale",1.5,0.0,10.0);
  SHOCKWAVE_RING_VARIANCE=b.comment("每颗粒子在速度上再加的随机浮动（0..4，默认 0 = 原脚本的 globalSpeedVarianceAdd）。").defineInRange("ringSpeedVariance",0.0,0.0,4.0);
  b.pop();
  b.push("subLevels");
  SUBLEVEL_FAR=b.comment("物理体（Sable 子场景）本身在视距外仍会被画出来，但它上面的方块实体不会：Sable 枚举方块实体用的是“已编译的渲染段”，而段在客户端视距外并不编译，于是几百格外看战舰只有船体、炮管不见了（视距 3 时 48 格就会发生）。开着它，本模组会自己按 sub-level 的 plot 区块把名字匹配的方块实体补画出来。**只画 Sable 没画的那部分**：水平距离在客户端视距之内的交给 Sable（实测视距内一切正常），之外的才补画，所以两者互斥、不会重复叠加。纯客户端渲染，与光影补丁无关；关掉即回到原状。（0.1.26 首次实测未生效，原因是当时用 isSectionCompiled 当判据 —— SSRD 会让 sub-level 的段在视距外保持可渲染，于是该画的被整批跳过；详见 HANDOFF 8.27。）").define("farBlockEntities",true);
  SUBLEVEL_FAR_NAMES=b.comment("名字里含有这些片段的方块实体才补画，按注册名 path 做包含匹配（区分大小写）。默认 [cannon, flap]。留空等于关闭。加得越多，被拉进超视距绘制的方块实体越多，代价随数量线性上涨（几个炮管级别可以忽略）。注意：组装后的炮是 contraption 实体、不是方块实体，本通道看不到它；CBC 的 quickfiring_breech 也不含 cannon/flap，需要自己加 breech。").defineList("farBlockEntityNames",java.util.List.of("cannon","flap"),v->v instanceof String);
  b.pop();
  b.push("muzzle.bigCannon.plumeAlpha");
 BIG_PLUME_ALPHA=b.comment("**巨炮炮口烟的初始透明度倍率（0.05~1.0，默认 0.6）**。CBC原生烟与新热烟均使用这个主倍率，服务端同名键对巨炮无效。新热烟另乘 thermalSmoke.opacity。").defineInRange("initial",0.6,0.05,1.0);
 BIG_PLUME_FADE_FROM=b.comment("仅CBC原生烟：巨炮炮口烟从寿命的百分之几开始变淡（0~1，默认 0.35）。").defineInRange("fadeFrom",0.35,0.0,1.0);
 BIG_PLUME_FADE_RATE=b.comment("仅CBC原生烟：巨炮炮口烟的变淡速率（每 tick 减去初始倍率的多少，0.1~10，默认 1.5）。").defineInRange("fadeRate",1.5,0.1,10.0);
 // push() splits on dots, so this block is three path segments deep and needs three pops to get back
 // to the root. It used to pop once and leave [muzzle, bigCannon] behind, which is how clouds, particles
 // and debug all ended up nested under the big cannon.
  b.pop(); b.pop(); b.pop();
  b.push("muzzle.tankCannon.plumeAlpha");
 TANK_PLUME_ALPHA=b.comment("**坦克炮炮口烟的初始透明度倍率（0.05~1.0，默认 0.3）**。坦克炮的烟是 CBC 自己的粒子，由客户端画，所以这三个键在【客户端】配置里；服务端那份同名键已删除（客户端读不到服务端配置）。1.0 = CBC 原样。默认取用户实例里那组（0.3 / 1.0 / 1.5）。").defineInRange("initial",0.3,0.05,1.0);
 TANK_PLUME_FADE_FROM=b.comment("坦克炮炮口烟从寿命的百分之几开始变淡（0~1，默认 1.0 = 不淡出，靠寿命结束消失）。").defineInRange("fadeFrom",1.0,0.0,1.0);
 TANK_PLUME_FADE_RATE=b.comment("坦克炮炮口烟的变淡速率（每 tick 减去初始倍率的多少，0.1~10，默认 1.5）。").defineInRange("fadeRate",1.5,0.1,10.0);
  b.pop(); b.pop(); b.pop();
  b.push("clouds");
 CLOUDS_FOG_SCALE=b.comment("**云吃地面雾的比例（0~1，默认 0.35）**。1.0 = 云的颜色完全被雾接管（远了云就变成雾色 ✗）；0 = 云完全不吃雾（✗ 与雾脱节）。云是有自己受光颜色的实体，不是一团空气，所以默认只吃三分之一，让云的固有色占主导，同时仍随距离向雾里融合。").defineInRange("fogScale",0.35,0.0,1.0);
CLOUDS_NEAR_CULL=b.comment("**体积云贴脸渐隐的距离，单位格（-1~64，默认 6）**。云层贴到镜头前那一层既最费填充率（一个采样铺满全屏）又最不像云，所以 `sloCloudNear` 格以内按 smoothstep 渐隐，到两倍距离恢复成正常的云——没有硬边。0 = 用默认 6；**负数 = 关闭这个渐隐**。只对 Photon 系列生效（Complementary 两套画云的方式不同，那里这个键不起作用）。").defineInRange("nearCull",6.0,-1.0,64.0);
  CLOUDS_ENABLED=b.comment("让光影包里的**云**被本模组的动态光照亮（炮口闪光、曳光弹、爆炸；射灯与静态灯默认不参与，见下面三项）。默认按**各向同性散射**处理：只把灯在云体上的能量加进去，不引入相位函数。关掉即完全回到原状，云只由光影包自己的太阳/天空光照亮。设计与通道见 CLOUD-LIGHTING.zh-CN.md。").define("enabled",true);
  CLOUDS_PHASE=b.comment("质量档一：按视线与「云→灯」方向的夹角做相位函数（前向散射更亮），云在逆光/顺光下会有方向感。默认关：先保证「亮起来」这件事成立，再谈形状。").define("phase",false);
  CLOUDS_MULTI_SCATTER=b.comment("质量档二：在相位函数之上再做一次多次散射近似（云内部反复散射的补光），让厚云更实。默认关；比相位档更贵，建议先确认相位档的效果再说。").define("multiScatter",false);
  CLOUDS_INTENSITY=b.comment("云层照明的整体强度倍率（0~8），**默认 2.0**。这是唯一决定「云上的光有多亮」的旋钮：灯的亮度到达着色器时已经是 0~1（桥里除以 15 了），所以 1.0 ≈ 光斑中心约半亮、2.0 ≈ 约三分之二亮、4.0 接近白、8.0 到 0.89。再往上不会变成一片死白——着色器末尾有一次**软压缩**（m→m/(1+m)，单调、保色相、上限 1），所以无论怎么调都还能看出「中心亮、边缘暗」的层次。0 = 只留开关不发光。**注意**：旧版除上述之外还乘了包自己的方块光刻度（6.0），单盏满亮度灯到达约 12 而被整片截白——那才是「怎么调都是一片均匀色」的原因；另外 0.1.49 一度又误除了一次 15（灯的亮度其实已经归一化），导致整体变暗，0.1.50 已修。").defineInRange("intensity",2.0,0.0,8.0);
  CLOUDS_DYNAMIC=b.comment("炮口闪光、曳光弹、爆炸这类「动态光」是否照云。默认开，触达 = 它们自己的光照半径（光到哪、云到哪）。这是云层照明最主要的用途：夜里开炮时头顶的云会被闪一下。").define("dynamic",true);
  CLOUDS_SPOTLIGHTS=b.comment("射灯（探照灯）是否照云，默认开。射灯的触达是它的射程，并且在云的采样点上做锥体判定；一整晚扫射的探照灯会让云的逐采样开销明显上升，所以一整晚扫射时值得考虑关掉。").define("spotlights",true);
  CLOUDS_SPHERE_REACH=b.comment("登记式静态灯（含灯球）的云触达半径，单位格（0~128），默认 0 = 完全不参与。球灯数量最多、又基本在云的下方，所以默认 0；给一个正值就是「这些灯也照云」，代价是每朵云都要多循环它们。").defineInRange("sphereReach",0.0,0.0,128.0);
  CLOUDS_REACH_SCALE=b.comment("所有云触达的统一倍率（0.1~16），默认 1.0 = 严格按各灯自己的光照半径。**几何提醒**：原版云在 y≈192，而炮口闪光的半径只有十几格——船在云下开炮时，按 1.0 云几乎不会亮。想让「下面开炮、头顶的云闪一下」成立，就要把它调大（例如 4~8），或者干脆从甲板高度算：那是把光送到云上的唯一办法，物理上属于「散射把光带上去」，不是灯真的照那么远。曳光/爆炸的半径本来就大，1.0 往往就够。**它只决定「够不够得着」，不再决定亮斑多大**——见下面的 falloffScale。").defineInRange("reachScale",1.0,0.1,16.0);
  CLOUDS_FALLOFF_SCALE=b.comment("云上亮斑的尺寸：亮斑半径 ≈ 灯自己的光照半径 × 这个倍率（0.5~16，默认 3.0），球灯按横向偏移衰减、手电/探照灯按沿光束的距离衰减，曲线是 (1-q)²——**中心最亮、到半径处归零**。**它还受一个上限约束**：衰减半径永远不会超过「灯的云触达在云这个高度上允许的那个圆盘」——否则曲线还没来得及下降就被触达闸门硬切，看到的就是「一块等色的圆盘 + 一圈硬边」（静态灯之前正是这样）。所以：球灯的亮斑大小 = min(灯半径 × 这个倍率, 云触达在云高度的横向半径)，调大它只会让亮斑更接近触达的边界，不会变均匀。**炮弹（曳光/闪光）不吃这一条**，它们有自己的 tracerGlow / blastGlow 两条（单位是格）。**想更亮调 intensity，想更大调这个或灯的云触达**。").defineInRange("falloffScale",3.0,0.5,16.0);
  CLOUDS_TRACER_GLOW=b.comment("**炮弹曳光在云上的光斑半径，单位格（0~512），默认 0 = 用旧规则**。旧规则是「曳光的球形光半径（自动炮 6 / 大型炮 12 格）× falloffScale」，还要被它自己的云触达夹住——所以云上只有一小团，而且曳光飞在云下几十格时根本照不到云。**填一个正值就直接指定它的云亮斑半径，同时也成为它在云上的触达距离**：例如 96 = 以弹体为中心、半径 96 格的柔和光盘（飞在云下 96 格内就开始照亮头顶）。建议先试 96；想更散就 128~192。只影响云，地面上那个光球仍是 autocannon.radius / bigCannon.radius。").defineInRange("tracerGlow",0.0,0.0,512.0);
  CLOUDS_BLAST_GLOW=b.comment("**炮口闪光与爆炸在云上的光斑半径，单位格（0~512），默认 0 = 用旧规则**（按装药折算的包围盒半径 × falloffScale × reachScale）。填正值 = 直接指定亮斑半径与云触达，例如 128~256：一次舰炮齐射在云上摊开一整片光，而不是一小团。闪光本身还有一条亮度曲线（闪一下就渐隐），这一条只改它摊开多大。").defineInRange("blastGlow",0.0,0.0,512.0);
  CLOUDS_INTEGRATION=b.comment("云层照明里「沿射线积分」与「当前帧即时」两半的比例（0~1），**默认 0.0 = 全即时**（2026-10-06 用户实机拍板）。背景：光影包的云是低分辨率渲染后再做时域上采样（跨帧混合）的，而它三道「该刷新历史」的守卫全都只看相机移动——所以相机一停，灯照在云上的旧位置会被历史以 75% 的权重一直留住，形成拖尾；相机一动反而几乎看不见。0.0 让灯从那条历史里完全退出：灯灭即灭、移动的灯不留彗尾，代价是只剩下云所在位置的单点采样（没有沿射线的体积感）。想要那种体积感的人可以自己把它调上去（0.25 / 0.5 / 1.0），拖尾会按比例回来。**只影响花式云**：块状云走 d4 的逐帧通道，本来就没有时域累积。").defineInRange("integration",0.0,0.0,1.0);
  b.pop();
  b.push("particles");
  PARTICLE_DEPTH_SORT=b.comment("同一渲染层内的粒子改成**从远到近**画（原版完全不排序：层内就是生成顺序）。原版这样画的问题在半透明层上是实打实的：一整层共用一次带 alpha 混合的绘制，而粒子**会写深度**，所以先画上去的那片淡淡的烟会占据深度、把之后才生成、还在它后面的粒子直接剔掉——淡的东西却把后面全挡了。连续弹着时最明显（薄烟长寿又不断补充，几乎总排在队首）。打开后远的先混进像素、近的再盖上来：烟只把后面的东西染一层色，而不是抹掉它。**代价**：每帧给自认为半透明的层排一次序（不透明层与 NO_RENDER 跳过；开销随该层粒子数 n log n 增长，满层 16384 时约零点几到一毫秒，而且 Iris 一帧会调两次 render，所以是两遍）。**副作用**：队列满 16384 时原本淘汰最旧的那个，排序后淘汰最远的那个。关掉即完全回到原版顺序。").define("depthSort",true);
  PARTICLE_SORT_MAX=b.comment("**只给粒子数不超过这个值的层排序**，范围 0~65536，默认 8192；0 = 不限。它是一道开销闸门：满层排序是上面那条的主要代价，而一层真的堆到上万时画面早就糊成一片，排序也救不回观感。超过它时该层**整层跳过排序**，于是：(1) 每帧的排序开销被钉住；(2) 那一层的淘汰规则回到原版的「淘汰最旧」。（注意：「回到淘汰最旧」**并不能**保护 CBC 的烟——烟本身也常常是较旧的；真正保护它的是**少生成**我们的粒子，见 shockwave 那几项。）").defineInRange("sortMaxCount",8192,0,65536);
  PARTICLE_SORT_MAX_SHIFTS=b.comment("**自适应排序的位移上限（×粒子数）**，0~64，默认 8；0 = 关闭自适应、每帧全量排序。粒子层每帧的绘制顺序与上一帧几乎相同（相机每帧移动不到一格、粒子只是漂移），而以插入法从上一帧的顺序出发，实测只需全量排序约一半的工作量，且对【1500 同向 + 500 反向 + 相机侧移】这种运动模式几乎不敏感。上限用于兜底：传送、重生或一波爆发会让旧顺序失效，此时位移数会迅速超过上限，于是放弃并回退到全量排序（避免退化成平方级）。").defineInRange("sortMaxShifts",8,0,64);
  PARTICLE_SORT_DEBUG=b.comment("每秒打印一行排序器的实况：这一秒排了几层/多少个粒子、花了多少毫秒、有几层因为超过 sortMaxCount 被跳过、见过最大的层多大。**当「卡不卡」只是感觉时，这行是唯一能把感觉换成数字的东西**（注意 Iris 一帧调两次 render，所以计数会双算，毫秒数也会双算）。正常游玩保持关闭。").define("sortDebug",false);

  OCCLUSION_ENABLED=b.comment("**LOD 遮挡总开关**，默认开。关掉后 VoxyAccess 一次射线都不发、任何东西都不会被远景地形剔除（等价于 occlusionBudget = 0，但语义清楚）。客户端 [particles] 段。").define("occlusionEnabled",true);
  OCCLUSION_BUDGET=b.comment("每帧（准确说：每次粒子渲染通道）允许的**新**遮挡查询上限，0..2048，默认 2048。它只限制突发：答案按**方向格**缓存（见下面的 occlusionCellDegrees），同一方向上的成百上千颗粒子只花一次查询，所以稳态每秒只需几十次。2048 是给爆炸瞬间想立刻收敛准备的，代价约数毫秒每帧。").defineInRange("occlusionBudget",2048,0,2048);


  OCCLUSION_HOLD_TICKS=b.comment("**一次「被挡」至少保持多少 tick**，0~100，默认 10（0.5 秒）。用途：飞行/移动时相机每 8 格换一次角格，地面附近的粒子在「被挡/畅通」之间以约 1~2 Hz 翻转，看起来整层烟在闪；有了保持时间，单次「畅通」样本推翻不了已判定的遮挡。到期后仍需连续两次「畅通」才恢复，所以也不会永久钉死。0 = 关闭保持。").defineInRange("occlusionHoldTicks",10,0,100);






  OCCLUSION_COARSE_STEP=b.comment("**粗筛步长（格）**，1~16，默认 8。沿射线每隔这么多格问一次「包含该点的 mip 立方体是否非空」；非空就在这附近按 1 格细走。调小 = 漏检更少但更贵；设 1 等效于全程逐格（最准最贵）。观测到「同心圆环漏粒子」时，若环的间距≈本值，就说明是壳层间距造成的漏检。").defineInRange("occlusionCoarseStep",8,1,16);
  OCCLUSION_REFINE_LOOKBACK=b.comment("**细走回看步数（×粗筛步长）**，0~8，默认 1（实测 1 即可让 1 格薄墙稳定挡住）。命中候选立方体后，从「采样点往回这么多步」开始按 1 格细走；另外若上一个采样点的立方体非空，则两采样点之间的这一段也会细走（补「薄墙夹在两壳之间」的缺口）。调大 = 覆盖更宽、更贵；0 = 只查当前立方体那一步。").defineInRange("occlusionRefineLookback",1,0,8);
  OCCLUSION_MARGIN_BLOCKS=b.comment("**判定余量（格）**，0~32，默认 4.0：只有当遮挡点落在粒子前方【至少这么多格】时才算被挡。用途是吸收掠射：视线几乎平行于地面时，命中点会在粒子前方一点点反复进出，留一点余量可以避免粒子在阈值上来回跳。旧键 occlusionMargin / occlusionMarginScale 已停用（后者随距离漂移，是刻意去掉的）。").defineInRange("occlusionMarginBlocks",4.0,0.0,32.0);


  OCCLUSION_HYSTERESIS=b.comment("**迟滞**：判定要连续出现这么多次才被采纳（两个方向都算，1..20，默认 2）。山脊边缘擦过一帧不会让烟一闪一闪。").defineInRange("occlusionHysteresis",2,1,20);

  OCCLUSION_CELL_DEGREES=b.comment("**方位角/俯仰角的格子边长（度）**，0.1~15，默认 1.0。越小越准、查询越多；0.1 是用户要求放开的极限档。远处一团烟在角度上只占很小的锥，所以按方向缓存比按世界格缓存既省又稳（烟柱上升时方向几乎不变）。").defineInRange("occlusionCellDegrees",1.0,0.1,15.0);


  OCCLUSION_PRECAST=b.comment("**每次渲染通道允许预铸多少个方向**，0~2048，默认 128。爆炸发生时客户端会把它的核心区与圆盘方位排队，在它的粒子被画出来之前先把 horizon 铸好，齐射就不会出现整团来不及判而冒回来。预铸额度独立于 occlusionBudget；烟柱不预铸（它会升会飘，方向在变）。").defineInRange("occlusionPrecast",128,0,2048);
  OCCLUSION_HORIZON_TICKS=b.comment("一个方向的 horizon（遮挡距离）保留多少 tick 后重算，1~40，默认 5。它比按格缓存可以更长寿，因为方向比位置稳定得多。").defineInRange("occlusionHorizonTicks",5,1,40);
  b.pop();
  b.push("debug");
  EFFECTS_DEBUG=b.comment("**一次打开全部诊断日志的总开关**：打开它等于把下面每一项都打开（包括另一份配置里的 Sable 桥与光照选择日志），排查时不必再逐个回忆键名。每一项仍然可以单独打开，各自的值互不影响。正常游玩保持关闭——这些日志是每秒或每次事件一行，量不小。").define("effects",false);
  
  TRAIL_DEBUG=b.comment("每秒打印一行尾迹日志：这一秒铺了多少帧、数据源覆盖了多少弹壳。用来分辨“服务器数据源停了”还是“数据到了但粒子没画出来”，正常游玩保持关闭。").define("trails",false);
  SHOCKWAVE_DEBUG=b.comment("每画一个冲击波打印一行：它是由哪些测量值算出来的、又生成了什么。用来分辨“测量根本没到”还是“到了但什么都没画”，正常游玩保持关闭。").define("shockwave",false);

  SUBLEVEL_DEBUG=b.comment("每秒打印两行物理体补画通道的计数：考虑了几艘 sub-level、几个区块、名字命中几个、多少在客户端视距内（归 Sable）、多少在视锥外、实际画了几个；第二行给出每个命中的方块实体是什么、被摆到离你多远，以及客户端上每个实体的类型与距离。这是分辨“通道没跑／区块里没有方块实体／名字没命中／被判成视距内／被视锥剔除／实体压根不在客户端”的唯一手段，正常游玩保持关闭。").define("subLevelFar",false);
  b.pop();
 }

 /**
  * Whether one diagnostic switch is on: its own value, or the master switch.
  *
  * <p>Every reader in this mod goes through here instead of reading its own key, which is the whole point
  * of a master switch: "turn on all diagnostics" has to be one action, and it has to work for switches
  * that live in another spec (the Sable bridge and lighting ones are in {@code MuzzleClientConfig}).
  */
 public static boolean on(ModConfigSpec.BooleanValue specific) { return EFFECTS_DEBUG.get() || specific.get(); }
}
