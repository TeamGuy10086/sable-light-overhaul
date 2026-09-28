# 0.9.5-alpha：坦克炮模块 + 生成器方块与配置页（含 0.9.4 内容）

本版一次交付两件事：**坦克炮（CBCMW Medium Cannon）的开炮效果与光照**，以及上一批的**两个生成器方块 + LDLib2 配置页**。

构建与 **99 项自动检查**通过；**未实机运行**。

## 本版新增（测试期间追加）：静态光源不再照亮自己的方块

**问题**：登记过的光源方块自身会被自己的光洗成白色——因为光是从方块内部发出的，加色叠加在它自己的贴图上就饱和成白；而周围被照出来的光是彩色的。

**做法**（**仅 `Advanced` 档**，也就是走光影补丁的那条路）：每盏灯多带一个标志位，补丁里对"登记过的静态光源"做一次剔除——

```glsl
if (tints[i].w > 0.5) {
    vec3 self = scene_pos - light.xyz;
    if (all(lessThan(abs(self), vec3(0.55))) && dot(normal, self) > 0.0) continue;
}
```
只剔除**灯自己那一格的外表面**（`dot(normal,self) > 0` 用来放过紧贴着同一个边界面的邻居方块面，不会在灯周围留一圈黑边）。炮火/曳光弹/射灯不受影响（它们不带这个标志）。

- 开关：客户端配置 `[gpu] cullSourceSelfLighting = true`（想 A/B 对比就设 false）。
- **必须重新覆盖光影补丁文件**：`patch/photon/shaders/include/lighting/diffuse_lighting.glsl` → `<你的Photon包>/shaders/include/lighting/`。补丁现在是 **589 行（原 275 行未删，新增 314 行）**，已通过纯增量与 glslang 校验。不覆盖 = 这条新功能不存在（其余功能照常）。
- 只作用于 `Advanced`；`DynamicOnly` 的屏幕空间叠加与 CPU 两档都没做这个剔除（那些档本来也不渲染每灯光色）。

## 安装

1. 换 JAR：`cbc-muzzle-light-0.9.5-alpha.jar`（**协议仍为 6**）。
2. **不用改任何配置**：`[tankCannon]` / `[generators]` 新段会在启动时自动补进你的 `config/cbcmuzzlelight-server.toml`（已有的 `maximumDistance=512` / `distanceStep=32` / `[colors]` 保持不变）。
3. 光影补丁无需重装（0.9.3 那个补丁文件继续有效）。
4. 建议双端都换：新客户端才知道"坦克炮闪光"这一种（旧客户端只是不亮那一种闪光，不会崩）。

## 一、坦克炮（新）

**接入方式**：一个**软依赖 mixin**（`cbcmuzzlelight.cbcmodernwarfare.mixins.json` + 门控插件），钩在 CBCMW `MountedMediumcannonContraption#fireShot` 里**唯一一处** `new BigCannonPlumeParticleData(...)` 上——那一行上炮口位置、炮口方向、装药都已经算好了，所以：

- **不动 CBCMW 的 jar**，没装它时整块静默关闭；
- 插件会先校验那个方法签名与 `plumePos`/`vec1` 局部变量还在，CBCMW 更新后**退化为"没有效果"而不是崩**；
- 原方法构造的 plume **原样返回**，是纯增量。

**效果（三组，全部按 `[tankCannon]` 配置）**：

| 效果 | 做法 |
|---|---|
| 明亮火焰 | 先**放大 CBC 自己的 plume**（`flame.plumeScale`，它自带白团+火焰期+灰烟），再补 `flame.count` 个配置粒子沿炮口方向高速喷出（`flame.particle`，默认 CBC `cannon_smoke{power:8.0,...}` — 出生自发光、快速淡出） |
| 黯淡火星 | `sparks.bursts` 次 `sparks.particle`（默认 `createbigcannons:spark_burst{color:[1.0,0.45,0.12],deflect:true,count:24}`）；`deflect` 打在地上/车体上会弹开，是 CBC 自己实现的，零成本 |
| 地面尘 | 从炮口沿炮口轴线 raycast `groundDust.searchDistance` 格，命中处出 `count` 粒（默认 `minecraft:dust{...}`），**颜色取命中方块地图色**（`tintFromBlock`），水平散开 + 一点上抛 |
| 车体尘 | 炮口周围半径 `hullDust.radius` 的壳层取点，**偏向炮口后方与下方**（冲击波扫过车体），向外吹 |

- 炮口朝上/平射时地面尘不出（打不到地面），这是预期。
- **寿命写在粒子串里**（例如 `lifetime:10`），所以我删掉了单独的"寿命"配置项——少一个会打架的旋钮。
- 炮口**光照**：`kind=2`，短促明亮的脉冲（我们模组的老本行）。**目前复用自动炮那套曲线/布局**（小、快），"比自动炮更亮更大"的逐种调参是后续的一个小改动，先看观感。
- **效果 4（抽烟）保持模组原样**，我们没碰；也没给它加可调层（要的话单独加一个钩子）。

## 二、生成器方块 + 配置页（0.9.4 内容，细节见 `GENERATORS-0.9.4.zh-CN.md`）

- 两个方块（`dust_generator` / `smoke_generator`），**只能放在 Sable 物理体上**；扳手推 0.1 / shift 隐藏模型 / 顶底面拒绝；空手右键开页面。
- 引擎烟：六面红石取最大值，`0=停`、`1..15=出烟量`。
- 扬尘：按速度出尘、向下找地面、颜色取地形；页面里有"**持续喷射**"调试开关。
- 页面用 LDLib2（与 Synaxis 同观感），**可选依赖**；编辑走我们自己的校验后写入的包，不用第三方同步层。

## 三、`[tankCannon]` 配置一览（你要一一试的就是这些）

```toml
[tankCannon]
	enabled = true
	cullDistance = 160        # 超出这个距离的玩家不接收粒子
	particlesPerShot = 120    # 一次开炮的粒子总上限
	[tankCannon.flame]   particle / count(3) / speed(0.25) / size(1.6) / plumeScale(1.4)
	[tankCannon.sparks]  particle / bursts(2) / speed(2.5)
	[tankCannon.groundDust] particle / count(16) / searchDistance(24) / speed(1.5) / upSpeed(0.5) / tintFromBlock(true)
	[tankCannon.hullDust]   particle / count(10) / radius(2.5) / speed(0.8)
```
粒子字段照旧**直接粘 `/particle` 里那段**；解析失败会一次性警告并回退到内置默认，不会中断开炮。

## 四、建议测试顺序

1. **先开一炮**：确认炮口有明亮扩散（plume 放大 + 火焰粒子）、有火星、地面上有尘、车体附近有尘。
2. 侧面对着地面压低炮口开炮 → 地面尘应该出现在**弹道落点**并且**颜色跟着地面变**。
3. 炮口抬高/平射 → 只有火焰与火星，没有地面尘（预期）。
4. 把 `[tankCannon]` 里每组 `count` 调成 0 再逐组打开，确认四个效果各自独立、互不干扰。
5. 远处（>160 格）观察 → 没有粒子（距离剔除）；开炮时应该有炮口光照亮车体与地面。
6. 然后按 `GENERATORS-0.9.4.zh-CN.md` 里的步骤验生成器与配置页。

## 五、未验证 / 风险

- **mixin 是否真的应用**：我用字节码校验了目标方法签名与局部变量名（`fireShot`、`plumePos`、`vec1` 都在），但只有实机能确认它真的挂上——若开炮没有任何新粒子，先看 `latest.log` 有没有 mixin 相关报错。
- **坐标变换**：炮口位置/方向走 `CBCCompatTransformers`（和曳光弹同一条路），Sable 物理体上是否正确需实机确认（粒子位置是否正好在炮口）。
- **观感**：火焰/火星/尘的数量与速度都是初始值，等你的测试结论调。
- **炮口光**：现在与自动炮同曲线，可能偏小；确认后我给它单独一套（更短更亮）。
- **弹种差异**：按之前说好的，留到"弹着"项目；现在只按装药缩放。
- 生成器那边的三个未验证点（页面观感、扫描假设、扳手方向）依旧见 0.9.4 文档。
