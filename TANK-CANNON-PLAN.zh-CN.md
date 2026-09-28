# 坦克炮（CBCMW Medium Cannon）增强方案 v1

对象：`cbcmodernwarfare-0.0.6v+mc.1.21.1-neoforge` 的 **Medium Cannon**（`*_mediumcannon_barrel / _breech / _recoil_barrel`，材料 bronze / cast_iron / steel / nethersteel）。目标是四个效果里的 1、2、3；**4（抽烟）模组已实现，本方案不碰**。

本文只讨论方案，不含代码。所有结论都来自反编译源码与 jar 字节码，已标注哪些是"读到的事实"、哪些是"需要你拍板"。

---

## 0. 结论摘要

| 项 | 方案 |
|---|---|
| 接入点 | **一个软依赖 mixin**：`MountedMediumcannonContraption#fireShot` 里唯一的 `new BigCannonPlumeParticleData(FFI)` 构造点，用和我们主炮 `BigCannonMixin` 完全相同的 `@WrapOperation` 手法 |
| 拿到的数据 | 世界坐标炮口点 `plumePos`、世界坐标炮口方向 `vec1`、装药 `barrelTravelled`（= CBCMW 自己的 `power`）、**弹种** `round`、炮中心 `centerPos` |
| 效果 1 明亮火焰 | **建议新画**：1 张 4 帧火球 sheet + 1 帧白热核心。现有粒子（vanilla `flame`）是"篝火火苗"外观且**不发光**（`PARTICLE_SHEET_OPAQUE` + 受光照贴图影响），做不出炮口火球 |
| 效果 2 黯淡火星 + 不亮烟尘 | **全部复用**：CBC `spark_burst(color, deflect, count)` 天生就是"飞散火星 + 可反弹"，颜色可调；不亮烟尘用 CBC `cannon_smoke` 的低速长寿命层 + vanilla `dust` |
| 效果 3 地面/坦克扬尘 | **复用**：vanilla `dust{color,scale}`（颜色取落点方块的 map color，无需新贴图）+ `falling_dust` + CBC `cannon_smoke` 补体积 |
| 光照（我们模组的本职） | 新增 `FlashPayload` **kind = 2**（坦克炮），比主炮**更短更亮**（3 tick）；曳光弹沿用现有跟随光系统 |
| 依赖 | 新增 `cbcmuzzlelight.cbcmodernwarfare.mixins.json` + 门控插件（照 `CbcatMixinPlugin` 的模式）；**不改 CBCMW 的 jar**；没有 CBCMW 时整个功能静默关闭 |

---

## 1. 接入点（已用字节码验证）

```java
// MountedMediumcannonContraption#fireShot(ServerLevel, PitchOrientedContraptionEntity)
Vec3 centerPos = entity.toGlobalVector(Vec3.atCenterOf(BlockPos.ZERO), 0.0F);
...
Vec3 spawnPos = entity.toGlobalVector(Vec3.atCenterOf(currentPos.relative(this.initialOrientation)), 0.0F);
Vec3 vec1 = spawnPos.subtract(centerPos).normalize();
spawnPos = spawnPos.subtract(vec1.scale(1.5));
...
Vec3 plumePos = spawnPos.subtract(vec1);
BigCannonPlumeParticleData plumeParticle = new BigCannonPlumeParticleData(size, power, 10);  // ← 注入点
```

`javap -l` 确认这个类**带 LocalVariableTable**，`fireShot` 里可见的局部变量（正是我们要的）：

| 局部变量 | 类型 | 用途 |
|---|---|---|
| `plumePos` | Vec3 | **世界坐标炮口点**（弹丸生成点再后退 1 格） |
| `vec1` | Vec3 | **世界坐标炮口方向**（`spawnPos - centerPos` 归一化；两个端点都是 `toGlobalVector`，所以它已经是世界方向） |
| `barrelTravelled` / `power` / `size` | int / float / float | 装药（炮管里走过的格数）；CBCMW 自己用 `barrelTravelled/1.5` 当 size 与 power |
| `round` / `foundProjectile` | `MediumcannonAmmoItem` / `ItemStack` | **弹种**（AP / APDS / APFSDS / APHE / HE / HEF / canister / smoke） |
| `centerPos` / `currentPos` | Vec3 / BlockPos | 炮中心、炮口所在方块 |
| `level` / `entity` | ServerLevel / PitchOrientedContraptionEntity | 参数 |

`new BigCannonPlumeParticleData` 在整个方法里**只有一处**（字节码 1124，描述符 `(FFI)V`）——和主炮 mixin 抓的是同一个构造器，所以 `@WrapOperation` 的写法可以照抄：

```java
@WrapOperation(method="fireShot", at=@At(value="NEW",
    target="(FFI)Lrbasamoyai/createbigcannons/effects/particles/plumes/BigCannonPlumeParticleData;"))
```

**为什么不用"客户端看弹丸出现来猜"**：弹丸生成点比炮口前 1.5 格，客户端拿到实体时可能已经过了一 tick（高速弹一 tick 飞 5 格），炮口闪光会错位；而上面这个点能一次性拿到精确的炮口点、方向、装药、弹种，零启发式。

**坐标空间**：`vec1`/`plumePos` 已经是 `toGlobalVector` 的结果。若坦克是 Sable 物理体（体内坐标系另算），沿用我们曳光弹那条已经验证过的路径（`CBCCompatTransformers.transformVec3`）处理；这一点实机一眼可验（光是否正好在炮口）。

**依赖处理**：CBCMW 是可选模组，所以不能塞进 `required: true` 的 `cbcmuzzlelight.mixins.json`。做法照现有 `cbcmuzzlelight.cbcat.mixins.json` + `CbcatMixinPlugin`：新建一个 mixin 配置 + 检查 `ModList.isLoaded("cbcmodernwarfare")` 的插件，缺模组时整份配置不加载。

---

## 2. 模组已经做了什么（不要重复做）

1. **炮口灰烟已经存在**：`BigCannonPlumeParticleData(size=power=barrelTravelled/1.5, lifetime=10)`，对**所有玩家**发一次。plume 本体 10 tick，但它每 tick 吐 `CannonSmokeParticleData`，那些烟的寿命是 `30 + rand(10) + 10*progress + ceil(power²)/2`——也就是**开炮后已经有 1.5~3 秒的灰色中速烟团**。→ 我们的"不亮烟尘"只补"贴地、低速、长寿命"那一层，不要再加一团浓烟（否则开炮后视野全糊）。
2. **冲击波 + 音效**：`CannonBlastWaveEffectParticleData(shakeDistance, FIRE_MEDIUMCANNON, ..., volume, pitch, 2, power)`，只发给 `volume²*256*1.21` 内的玩家。→ 音效不用我们管。
3. **效果 4（你已确认存在）**：开闩时 `if (cannon.hasFired)` 在炮闩位置喷一次 `QuickFiringBreechSmokeParticleData`，并把 `hasFired` 复位。位置/方向都算好了（`Vec3.atCenterOf(localPos).add(normal*0.6)` + `dir = entity.applyRotation(normal)`）。
4. **弹丸自带拖尾**：`TrailSmokeParticleData`，按 CBC 配置 `bigCannonTrailType`（NONE/SHORT(long 100)/LONG(280+)）。
5. **CBC 现成粒子库**（本次的关键资源）：`spark{color}`、`spark_burst{color,deflect,count}`、`cannon_smoke{power,size,lifetime,friction}`、`quick_firing_breech_smoke`、`debris_smoke_burst`、`cannon_blast_wave`、`flak_smoke`、`trail_smoke` 等；vanilla 侧还有 `dust{color,scale}`、`dust_color_transition`、`falling_dust{block}`、`smoke`/`large_smoke`/`white_smoke`/`poof`/`campfire_cosy_smoke`、`flame`/`small_flame`/`lava`/`electric_spark`/`flash`。

---

## 3. 三个效果的方案

统一约定：`k = clamp(barrelTravelled, 1, 8)`（短管 3 格 ≈ 2，长管 12 格 ≈ 8）；所有粒子在**服务端同一 tick**发出，按玩家距离分级发送（见 §5）。`vec1` 为炮口方向，`plumePos` 为炮口点。

### 效果 1（v2，按你的提示改）：CBC 自己就有"会变色的炮口焰"，我们放大它

你说得对，我读完了 CBC 的实现，**它确实自带一段"火焰→烟"的炮口特效**，而且坦克炮已经在用同一套粒子：

| 读到的事实 | 位置 |
|---|---|
| 羽流开炮瞬间（`age==0`）喷一团 vanilla `CLOUD` 白团 | `BigCannonPlumeParticle` line 89 |
| 羽流**前 5 tick** 持续喷 vanilla `FLAME`（数量 = `size*0.25*5`） | 同上 line 101 |
| 羽流全程吐 `CannonSmokeParticleData(power, size*0.25, lifetime, 0.95)` | 同上 line 84 |
| `cannon_smoke` **出生时自发光**：`getLightColor` 给光照贴图 +240，按 `(1-1.5p)⁴` 衰减到环境光；透明度 `alpha = 1-p²` 淡出；贴图沿 11 帧推进 | `CannonSmokeParticle` |
| `power` 通过自定义着色器的**梯度贴图**决定"火焰色→烟色" | 同上（`cannon_smoke_particle_gradient.png` + `setOverlay(cannonPower)`） |
| 三个客户端开关默认**全开**：`showBigCannonPlumes` / `showExtraSmoke` / `showExtraFlames` | `CBCCfgClient` |
| 但 `useShaderCompatibleGraphics`（默认 false）一开，`cannon_smoke` 会被换成 `fallback_cannon_smoke`（普通半透明烟，**丢失火焰色梯度**） | `CannonSmokeParticle.Provider` |

**所以效果 1 不需要新贴图**（按你的偏好），改成"用 CBC 的料拼"：

| 层 | 用什么 | 参数 | 作用 |
|---|---|---|---|
| 闪光核心 | 面包 `whiteflash`（或 vanilla `flash`） | 1 粒、`count=0`、寿命 2 tick | 一瞬白热 |
| 明亮扩散烟 | CBC `big_cannon_plume` **额外补 1–2 件** | `size=power=(1.2–1.8)×模组自身`、`lifetime=10` | 直接放大 CBC 自带的"白团+火焰+烟"三段式；自动跟随客户端粒子设置 |
| 火焰色核心 | CBC `cannon_smoke` ×`3+2k` | **`power` 取大值 6–10**、`size 1.0–2.0`、`lifetime 6–12` | 出生自发光 + 快速淡出 = 你要的"明亮烟雾" |
| 火舌（可选） | vanilla `small_flame` / `flame`，方向驱动 | `count=0`、速度 2–4、`6+3k` 粒 | 想要"火"的形状感时加 |
| 光照 | `FlashPayload` **kind=2** | 3 tick、峰值 ~14、半径 `4+k*0.5` | 我们模组的本职；坦克炮比主炮更短更锐 |

**唯一的前置验证**：你的光影环境下 `useShaderCompatibleGraphics` 是开还是关。开着的话 `cannon_smoke` 变成普通烟、**火焰色消失**，那就用 `whiteflash`/`flame`/面包的 `explosionsmall` 顶上（仍然零新资产）；实在不行我再加一个"复用 CBC 贴图 + 标准渲染类型 + 自己控制透明度/亮度"的自家粒子类——**那也只需要一个 Java 类，不需要新贴图**。命令表 §四 有对照实验步骤。

### 效果 2（v2）：黯淡火星 + 用透明度做的"不亮大范围薄烟"

| 组件 | 参数 | 说明 |
|---|---|---|
| 火星 | CBC `spark_burst{color=(1.0,0.45,0.12), deflect=true, count=12+6k}` ×2 | 一次调用=一蓬飞散火星；**`deflect` 的一次反弹是 CBC 自己实现的，我们只是传参，零成本**（回答你的第 5 点）；数量按客户端粒子设置自动缩放 |
| 不亮薄烟 | CBC `cannon_smoke{power≈0.1–0.3, size=2.0–3.5, lifetime=50–80, friction=0.98}` ×`8+4k`，初速 0.2–0.6 | **透明度正是你要的**：CBC 自带 `alpha = 1-progress²`，`power` 越小越暗、`size` 越大越"薄而广"；这一层就是"飞散而出的大范围薄烟"，和效果 4 那簇"从炮口出来的一小簇"明确区分开 |
| 干土味 | vanilla `dust{color=落点方块地图色, scale=1.5–3.0}` ×`6+3k` | 让"烟"里带一点土，不至于纯灰 |
| 履带/车体 | 见效果 3 | |

**照明**：这一层刻意**不加光**——"黯淡"就是要它不亮，加了光反而和效果 1 的白热核心打架。

> 若 `size` 拉到 3.5 还是不够"薄"（边缘太实），我再加那个带 alpha 参数的自家粒子（复用 CBC 的 `cannon_smoke` 贴图，所以仍然不用画图）。

### 效果 3：地面与坦克上激起的扬尘（~0.5–3.0 s）

两个分量，都在同一炮触发：

**(a) 炮口前方的地面扬尘**（炮口冲击波打在地上）：
1. 从 `plumePos` 沿 `vec1` 做一次服务端 `clip`（最远 ~24 格）；
2. 命中方块 → 在命中点生成**环状/扇形**尘：`12 + 4k` 个粒子，水平径向初速 `1.0–2.5`、向上 `0.3–1.0`，寿命 40–80 tick，尺寸 `1.5–3.0` 并逐渐膨胀；
3. **颜色取命中方块的 map color**（草=绿、沙=黄、泥=棕、石=灰）——这样不用画任何新贴图就有"土"的感觉；水面/雪等特例可回退到配置色；
4. 没命中（炮口朝上/平射）→ 跳过，不生成。
5. 若炮口压得很低（夹角小），把这一股改成"向前下方喷射"的尘柱，而不是环——这个可以用命中距离自动区分。

**(b) 坦克本体上被吹掉的灰**（冲击波扫过车体）：
1. 以 `plumePos` 为中心、半径 `0.5–2.5` 的**壳层**里随机取 `8 + 3k` 个点，**偏向 `-vec1`（炮口后方）与下方**（也就是炮盾/炮塔/首上装甲的位置）；
2. 初速：径向向外 `0.4–1.2` + 略向上，寿命 30–60 tick；
3. 颜色：车底方块的 map color（没有就配置色）；
4. 补 `4–6` 个 `cannon_smoke` 增加体积感。
   为什么用"炮口壳层"而不是车体包围盒：**不需要知道坦克几何**，在任何载具/物理体上都成立。如果你希望更精确（沿车体顶面/侧裙洒一层），我可以用 `entity.getBoundingBox()`（炮 contraption 的盒子）或载具实体的盒子——这取决于你的坦克怎么搭（见 §7 问题 1）。

**(c) 履带扬尘改用你提的"生成器方块"方案**：不再猜履带位置，改为在车体上放**扬尘生成器 / 引擎烟生成器**两个方块（扳手调 offset、向下找地面、引擎烟向世界正上方飘）。完整设计见 **`GENERATORS-PLAN.zh-CN.md`**。本文效果 3 只保留 (a) 炮口前方地面尘 与 (b) 炮口壳层车体尘；持续行进的履带尘交给生成器方块。

**(d) 时机**：不要全部塞在同一 tick。建议 (b) 立刻、(a) 延后 1–3 tick、慢尘再延后 2–6 tick——读起来才像"压力波推动"而不是"一坨粒子"。实现上需要一个 3–5 条的短任务队列（我们服务端已经有 tick 循环可以挂）。

---

## 4. 光照与曳光弹接入（我们模组真正的活）

1. **炮口闪光**：新增 `kind = 2`。现有 `FlashStore` 已经按 kind 区分主炮（0）/自动炮（1），坦克炮走同一套：`flash 寿命曲线 / 半径 / 亮度` 单独配置。坦克炮建议：寿命 3 tick、峰值 14、半径小（4–8），比主炮"更短更亮"。
2. **曳光弹跟随光**：`AbstractMediumcannonProjectile` 有公开的 `isTracer()` / `setTracer()`，而且 CBCMW 的 `fireShot` 里已经按 CBC 配置 + 弹种算好了 `isTracer`。把它接进 `ExtraLights` 的弹丸追踪（目前只认 CBC 的 `AbstractAutocannonProjectile` / `AbstractBigCannonProjectile`），坦克炮的曳光就能照亮地面和船体。
3. **档位**：和现有四档完全一致——`Advanced` 走光影包内注入（有阴影），`DynamicOnly` 走屏幕空间叠加，`Compatible`/`Legacy` 只做 CPU 那套最低限度。坦克炮的炮口光**不需要**任何额外档位逻辑。
4. **顺带**：`ExtraLights` 现在按炮口闪光 + 曳光弹给光源；坦克炮接进来后，连射坦克的光源预算会和炮火/静态光一起排队（动态优先，静态在后），不用新机制。

---

## 5. 预算与开关（按你的性能敏感度先定死）

| 距离 | 发什么 |
|---|---|
| ≤ 48 格 | 全部（火焰 + 火星 + 地面尘 + 车体尘） |
| ≤ 96 格 | 火焰 + 火星 + 车体尘（不发地面尘） |
| ≤ 160 格 | 只发火焰核心 + 火星（保证远处也能看到开火） |
| > 160 格 | 不发（连主炮 plume 都不发也没关系，反正屏幕内看不到） |

- 每炮粒子上限：默认 `flame 40 / spark 30 / dust 40`（配置可调），连射坦克按此封顶。
- **尊重客户端粒子设置**：照 CBC 的做法，把数量缩放放在**粒子类客户端侧**（`SparkBurstParticle` 就是这么做的），这样"最少粒子"设置的玩家不会被我们淹没。
- 每效果独立开关（`[advanced.mediumCannon] flame/sparks/groundDust/hullDust`），方便你实机逐项开关对比。
- 地面尘需要一次 raycast + 一次 `getMapColor`：每炮各一次，可忽略。

---

## 6. 弹种差异（可选，零额外成本）

`fireShot` 的局部变量里有 `round` / `foundProjectile`，所以"按弹种换效果"不需要任何额外 hook：

| 弹种 | 建议差异 |
|---|---|
| AP / APFSDS | 火球更小更锐、火星多、烟少（动能弹"脆"） |
| APHE / HE / HEF | 火球更大更亮、烟更多、地面尘更强（装药多） |
| Canister | 火星爆量（`spark_burst` count ×2–3）、几乎无烟 |
| Smoke | 炮口额外喷一层 `cannon_smoke`（和它自己的烟雾弹效果呼应） |

要不要做、做到什么程度，你定。不做的话就是"按装药 `k` 缩放"的统一效果，也不会难看。

---

## 7. 已定 / 待定（v2）

**你已经拍板的**（本方案已按此更新）：坦克=多个物理体 → 效果 3(b) 用"炮口壳层"，不依赖车体几何；火焰复用 CBC 那套；不亮烟用透明度做，且与效果 4 的"炮口小簇烟"区分开；地面尘自动取落点方块颜色；火星做 **1 次**反弹（CBC `deflect` 现成，零成本）；炮口光 3 tick / 峰值 14；预算默认给保守值但**允许调大**（你的机器偏弱）；弹种差异延后到"弹着"项目。

**还需要你回三条**：

1. **CBC 四个客户端开关的对照实验**（命令表 §四）：`useShaderCompatibleGraphics` 关 / 开 各看一遍 `big_cannon_plume` 与 `cannon_smoke` → 告诉我**开了之后炮口还有没有"火焰色"**。这决定效果 1 是"纯靠 CBC 拼"还是要我们自带一层亮粒子。
2. **履带怎么定位**（第 6 点）：要么我走"几何接地采样"（4×3 网格向下射线 + 只取车体左右两侧、且只在该点相对地面有速度时出尘）——**通用、不需要你给信息**；要么你告诉我履带是什么方块（Create 的？CBC 的？某载具模组的？），我通过 Sable 的 plot 方块表按 ID 精确定位履带。
3. **CBCMW 的 `thruster`**：CBCMW 整个模组只有这一个粒子（火箭推进器排气），和坦克炮无关；我打算不碰它。确认？

---

## 8. 建议实施顺序（等你确认后）

1. 软依赖 mixin（`cbcmuzzlelight.cbcmodernwarfare.mixins.json` + 门控插件）+ 空 hook 打日志 → 先证明能抓到 `plumePos/vec1/barrelTravelled/round`。
2. `kind=2` 光照（炮口闪光）+ 曳光弹跟随 → 这一层立刻能看，且是我们模组的本职。
3. 效果 2（火星 + 不亮薄烟）→ 全复用现成粒子，最快见效。
4. 效果 1（放大 CBC 羽流 + `cannon_smoke` 高 `power` 核心 + 可选白闪）→ 按第 1 条实验结论定最终配方，**预计零新贴图**。
5. 效果 3（地面 + 车体扬尘）→ 加 raycast 与取色；(c) 履带尘按第 2 条结论做。
6. 配置段 + 预算 + 文档 + 测试（沿用现有 90 项检查的风格）。

---

## 9. 附：试看命令表

`PARTICLE-TEST-COMMANDS.zh-CN.md` —— 已核对 1.21.1 的 `/particle` 真实解析规则（`count=0` 单粒子精确速度 / `count>0` 高斯散布 / 参数用 SNBT），含 CBC、面包军火库（111 个全是无参类型）、vanilla 三组命令，每条都标了"看什么"，最后一节是"一次模拟坦克开炮"的组合。
