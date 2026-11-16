package dev.sablelightoverhaul.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config for the big cannon muzzle thermal smoke cloud: geometry, lifetime, and a separate live budget.
 * Defined from the existing muzzle spec builder via {@link #define}; disabling it falls back to native CBC muzzle smoke.
 */
public final class BigSmokeConfig {
 public static ModConfigSpec.BooleanValue ENABLED,SINGLE_PASS,FLIPBOOK,SIMPLE_LIGHTING,PERFORMANCE,SOFT_EDGE_CULL;
 public static ModConfigSpec.DoubleValue SCALE, LENGTH, DIAMETER, OPACITY, RISE, SPREAD, EXPANSION, FADE_FROM, FLASH_LINK, CHARGE_REFERENCE, CHARGE_EXPONENT, CHARGE_MAX, AMBIENT_GAIN, EMISSION_GAIN, ROOT_HOT_SCALE, TIP_HOT_SCALE, COLD_FRACTION, COLD_SIZE, CURL, DENSITY_DETAIL,SOFT_EDGE_THRESHOLD;
 public static ModConfigSpec.IntValue LIFE, HOT_TICKS, RELEASE_TICKS, COUNT, LIMIT;

 public static void define(ModConfigSpec.Builder b) {
  b.push("particles.muzzle.bigCannon.thermalSmoke");
  ENABLED = b.comment("是否启用大炮炮口热烟团。关闭时恢复CBC原生大炮炮口烟，不回退到爆炸烟；只影响大炮炮口，不改变坦克炮行为；热烟仍参与原有跟烟照明。")
      .define("enabled", true);
  SCALE = b.comment("热烟团整体尺寸倍率，0.1~16，默认1.1925；调整几何及跟烟光源覆盖范围，不改变数量预算。")
      .defineInRange("scale", 1.1925000000000001, 0.1, 16.0);
  LENGTH = b.comment("热烟团沿炮口方向的最大前冲距离，单位格，0.25~128，默认42.1575；只改几何，不改变生成数量。")
      .defineInRange("length", 42.157500000000006, 0.25, 128.0);
  DIAMETER = b.comment("单个烟瓣的基础直径（实际边界有软化和分瓣），单位格，0.25~64，默认8.925；全直径不是半径，只改几何，不改变生成数量。")
      .defineInRange("diameter", 8.924999999999999, 0.25, 64.0);
  LIFE = b.comment("每个热烟团的存活时间，单位tick，10~1200，默认160；只改寿命，不改变生成数量。")
      .defineInRange("lifetimeTicks", 160, 10, 1200);
  HOT_TICKS = b.comment("热烟团从出生起保持炙热的tick数，1~200，默认20；控制材质冷却及跟烟照明的热权重，不改变生成数量。")
      .defineInRange("hotTicks", 20, 1, 200);
  ROOT_HOT_SCALE=b.comment("近炮口烟团的热斑冷却时间倍率，0.05~8，默认0.35；与远端倍率平滑过渡，调低使根部更早变为冷烟。只影响新烟，保留烟量/几何/寿命。").defineInRange("rootHotScale",.35,.05,8.0);
  TIP_HOT_SCALE=b.comment("远端烟团的热斑冷却时间倍率，0.05~8，默认1.5；乘热斑冷却时间，调高使前端热核保留更久。只影响新烟，跟烟照明共享该热时钟。").defineInRange("tipHotScale",1.5,.05,8.0);
  RELEASE_TICKS = b.comment("每发炮弹热烟释放过程持续的tick数，1~40，默认5；只改释放节奏，不改变单发数量预算。")
      .defineInRange("releaseTicks", 5, 1, 40);
  COUNT = b.comment("每单位 CBC 等效装药的烟瓣数，默认4；按装药线性缩放，再按粒子画质缩减。单发最多256瓣，3药及以下使用CBC原生烟。")
      .defineInRange("lobesPerCharge", 4, 4, 256);
  LIMIT = b.comment("同时存活的烟团总预算上限，16~4096，默认512；独立于碰撞几何与单发数量，是性能预算，满额时优先移除最早生成的烟瓣。")
      .defineInRange("maxActive", 512, 16, 4096);
  OPACITY = b.comment("热烟团不透明度，0~1，默认0.8；再乘旧大炮炮口烟 initial 主倍率。与冷却时间独立，不修改世界光源亮度。")
      .defineInRange("opacity", 0.8, 0.0, 1.0);
  RISE = b.comment("热烟团的向上漂移速度，单位格/秒，0~8，默认0.35；只改运动，不改变生成数量。")
      .defineInRange("riseSpeed", 0.35, 0.0, 8.0);
  SPREAD=b.comment("烟团横向散布倍率，0~4，默认0.85；控制瓣中心离开炮向轴线的幅度，不增加数量。").defineInRange("spread",.85,0.0,4.0);
  EXPANSION=b.comment("冷烟后续膨胀量，0~8，默认1.6；最终尺寸约为初期的1+该值倍。扩散自动稀释alpha，不增加数量。").defineInRange("expansion",1.6,0.0,8.0);
  FADE_FROM=b.comment("寿命中开始额外消散的比例，0~0.95，默认0.95；扩散造成的变淡始终独立进行，寿命末端归零。").defineInRange("fadeFrom",.95,0.0,.95);
  FLASH_LINK=b.comment("烟与现有炮口闪燃尺寸衔接，0~1，默认0.65；0仅用烟瓣直径，1完全参考更大的火球半宽，最多放大到烟瓣基础半径3倍。只在原火球开关开启时生效，不改火球或光源参数。").defineInRange("flashSizeLink",.65,0.0,1.0);
  SINGLE_PASS=b.comment("Advanced光影路径合并三瓣材质后只做一次场景照明，减少重复透明绘制；默认开启。原生材质路径保留旧绘制，避免增加纯材质计算。关闭仅供与旧三片网格对照，不改变数量预算。").define("singlePass",true);
  CHARGE_REFERENCE=b.comment("装药缩放参考值（0.1~128），默认4；CBC实际传来的power等于该值时尺寸倍率为1，不保证与所有推进剂方块个数一一相等。").defineInRange("chargeReference",4.0,.1,128.0);
  CHARGE_EXPONENT=b.comment("装药尺寸曲线指数（0~2），默认0.5；横向、纵向及大炮闪燃一并缩放，0关闭装药尺寸差异，不增加粒子数。").defineInRange("chargeExponent",.5,0.0,2.0);
  CHARGE_MAX=b.comment("装药尺寸倍率上限（0.1~16），默认6；替代旧固定1.8上限。极大尺寸仍可能有明显透明绘制开销。").defineInRange("chargeMaxScale",6.0,.1,16.0);
  FLIPBOOK=b.comment("六组预烘焙烟雾动画，默认开启；每瓣随机选一组并插值播放。关闭恢复程序化噪声供A/B；不改变运动、数量和透明层。开启时统一单面绘制，singlePass仅控制旧程序化路径。").define("flipbook",true);
  PERFORMANCE=b.comment("使用预烘焙遮罩计算热烟材质的边缘侵蚀和冷烟透明度纹理。可实时切换对比，保留烟量、运动及消散曲线。").define("performanceMode",false);
  SOFT_EDGE_CULL=b.comment("默认开启。几乎透明的热烟材质片元在后续受光与混合前退出，弱边缘平滑渐退。关闭可恢复原始透明度曲线。").define("softEdgeCull",true);
  SOFT_EDGE_THRESHOLD=b.comment("弱透明烟边早退的 Alpha 丢弃阈值，范围 0.002~0.100，默认 0.016（1.6%）。弱边缘渐退带宽为 0.034。可实时调整，仅在早退开启时生效。").defineInRange("softEdgeThreshold",0.016,0.002,0.100);
  SIMPLE_LIGHTING=b.comment("大炮热烟使用环境底色和燃烧自发光，默认开启；跳过逐灯受光和太阳阴影。跟烟光源仍照亮周围，冷却/消散不变。关闭恢复完整受光供A/B，仅Advanced路径生效。").define("simpleLighting",true);
  AMBIENT_GAIN=b.comment("简化受光的环境底色倍率，0~8，默认1；随环境亮度变化，不影响照亮周围的光源。").defineInRange("ambientGain",1.0,0.0,8.0);
  EMISSION_GAIN=b.comment("Advanced热斑自发光倍率，0~128，默认128；简化和完整受光均生效，冷烟不发光；不改变跟烟光源的亮度。").defineInRange("emissionGain",128.0,0.0,128.0);
  COLD_FRACTION=b.comment("出生即为冷烟的比例，0~1，默认0.2；从原烟量中均匀分配，不额外加烟。冷烟不产生热斑或跟烟热光。").defineInRange("coldFraction",.2,0.0,1.0);
  COLD_SIZE=b.comment("初始冷烟尺寸倍率，0.25~4，默认1.5；冷烟出生膨胀比例也较大，所有烟量上限保持不变。").defineInRange("coldSize",1.5,.25,4.0);
  CURL=b.comment("烟团翻卷位移强度，0~2，默认0.25；以烟瓣半径为单位沿有限短弧错相流动，0关闭。不会整片持续自转。").defineInRange("curl",.25,0.0,2.0);
  DENSITY_DETAIL=b.comment("冷烟透明度纹理强度，0关闭，3最强。寿命30%至75%逐渐加强，稀薄区域更透明，浓烟保留。").defineInRange("densityDetail",3.0,0.0,3.0);
  b.pop(4);
 }
}
