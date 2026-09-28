# 冲击波生成器 —— 精细化设计（0.9.79 之后的新活）

> 本文是**设计**，不是交付。落地形式（KubeJS 脚本换新 / 直接进 mod）在 §9 给出建议，等你挑。
> 所有"游戏里真实存在的数据"都标了出处：读的是 vanilla/CBC 的哪个方法或字段。

---

## 1. 现在这版（`ShockWave_Client_ver_0.3_Lite.js` + `ShockWave_Server.js`）在做什么

服务端（22 行）：`LevelEvents.afterExplosion` → 取 `event.affectedBlocks.size()` → 连坐标一起 `sendData('explosion_shockwave')` 给所有玩家。

客户端（184 行）：
- `blockCount <= 550` 直接忽略；
- 按 `blockCount` 落到三档（550/2000/18000）；
- `speed = (speedBase + blockCount × speedFactor) × 1.5`；
- `count = min(blockCount × countFactor, maxCount) × countMultiplier`；
- 在 `(x, y+2.5, z)` 用 `level.addParticle(type, true, x, y+2.5, z, vx, vy, vz)` 撒粒子：
  `vx/vz` 是随机水平角 × 速度，`vy` 是 `±vyRange/2` 的随机数；
- 分 1~2 批（`batchSize`）在 `ClientEvents.tick` 里放完；
- 粒子按权重混合：`shrapnel_smoke{lifetime:15}` : `cloud` : `end_rod` = 1 : 5 : 5。

粒子**全部在一瞬间、从同一个点、以同一个速率**出发，之后只受自己的运动影响。所以它读起来是"一团向外炸开的火花球"，而不是"一道从爆心推出去的波"。

### 1.1 三个根本问题

| # | 问题 | 为什么 |
|---|---|---|
| **A** | **`affectedBlocks.size()` 不是爆炸规模** | vanilla `Explosion.explode()` 对 1352 个方向各发一条射线（立方体表面 `j/k/l ∈ {0,15}` 的点，归一化后 16×16×6−重合 ≈ 1352），每步沿射线走 **0.3 格**，把**沿途遇到的每个方块**都塞进 `set`；只要 `shouldBlockExplode` 为真就进 `affectedBlocks`。**空气不掉能量**（`getBlockExplosionResistance` 返回空 → 不扣 `f`），实心方块按 `f -= (resistance + 0.3) × 0.3` 扣。<br>结果：这个数 = **射线走过的方块总数 ≈ 射线长度 × 密度**。同一发弹打天空、打土坡、打进岩层，它能差一个数量级 —— 这就是你已经用来分档的那个"暴露度"信号，但它同时被**地形硬度**污染了：软而厚的土坡会给出很大的数，硬而薄的装甲板会给出很小的数。 |
| **B** | **速度与粒子数从 `blockCount` 线性推** | 物理上冲击波速度由**当量**决定，粒子和"沿途卷起多少东西"有关。现在两者都挂在同一个被污染的数上，于是"打进山体的一发"会被当成比"空爆的一发"更大。 |
| **C** | **没有波前** | 所有粒子 t=0 同点同速出发，没有"半径随时间增长"的东西，也没有"波前扫过某处时才在那里掀东西"的效果。视觉上无法表达"波正在经过你身边"。 |

---

## 2. mod 环境里我们能拿到什么（这是设计的地基）

挂在 `ExplosionEvent.Detonate` 上（`ExplosionLights` 已经挂在那里了，同一个事件再加一个订阅者即可，互不干扰）。

| 数据 | 来源 | 语义 | 是否被地形污染 |
|---|---|---|---|
| **当量 / 方块威力** | `event.getExplosion().radius()`（**public 方法，已确认存在**） | CBC 的 `CustomExplosion` 把它当 `blockRadius` 直接交给 vanilla `Explosion` 构造器，其值来自弹药的 `block_damaging_explosive_power`（`ExplosionPropertiesComponent`） | **否**。同一发弹恒定 |
| **实体威力** | CBC `CustomExplosion.getEntityRadius()` | 同一份配置里的 `entity_damaging_explosive_power`，可以**与方块威力不同**（两个字段分开读） | 否 |
| **爆心** | `event.getExplosion().center()` | 已是 public | — |
| **被跳过的方块集合** | `event.getAffectedBlocks()` | 现有脚本用的那个 | 是 |
| **每个方向的真实到达距离** | 从 `getAffectedBlocks()` 自己算（§3.2） | **vanilla 已经替我们算好了这次的爆炸腔体形状** | 否（这就是它要表达的东西） |
| **腔体暴露度** | 同上 + `radius()` | 空爆接近 1，埋在岩层里接近 0 | 否（除掉了硬度） |
| **来源实体** | `getExplosion().getDirectSourceEntity()` | 反推是哪门炮/哪发弹 | — |

**CBC 相关事实（来自反编译 `ExplosionPropertiesComponent` / `CustomExplosion` / `FlakExplosion`）**
- `block_damaging_explosive_power` 与 `entity_damaging_explosive_power` **是两个独立字段**，可分别配置；
- `CustomExplosion.Impl` 的构造器签名含 `float blockRadius, float entityRadius`，`FlakExplosion` 也是 —— 所以"破片弹"与"穿甲弹"在数据上就能区分，不必靠猜；
- 顺带更正交接文档里的一处措辞：那个数是**"跳过的方块"不是"摧毁的方块"**（用户早已纠正过，这里再固化一次）。

---

## 3. 更精细的测量

### 3.1 三个标量

```
Y  = explosion.radius()                      // 当量（方块威力），与实际地形无关
N  = affectedBlocks.size()                   // 被跳过的方块数（现有脚本用的）
V  = (4/3)π · (0.3·Y)³ / 0.3³               // 若全程空爆，射线会走过的方块数上界
                                             // 等价于 (4/3)π · Y³ · 0.3⁻³ … 见下注
```

> **注（量纲）**：射线每 0.3 格一步，`f` 从 `≈Y` 开始每步扣 `0.3`（空气不扣），所以空爆时射线长度 ≈ `0.3·Y/0.3 = Y` 格、步数 ≈ `Y/0.3`。把这些步的方块去重后，空爆的 `N` 约为半径 `Y` 的球内方块数 ≈ `4.19·Y³`。所以：

```
N_raw   = N                                    // 实测
N_air   = 4.19 · Y³                            // 同当量空爆的理论值
exposure = clamp(N_raw / N_air, 0, 1)          // 腔体暴露度：1 = 完全空爆
solidity = 1 - exposure                        // 埋深/包裹度
```

**`exposure` 就是我们真正想要的那个轴**：它把"这次爆炸是在空中还是在山体里"和"当量多大"分离开了 —— 现有脚本只有前者被硬度带偏的混合体。

一个近似恒等式可以用来自检（可写进诊断日志）：
`Y ≈ (N / 4.19)^(1/3) / exposure^(1/3)` —— 空爆时 `exposure≈1` 退化成 `Y ≈ (N/4.19)^(1/3)`，也就是"方块数开三次方"；这正是老脚本用 `sqrt` 级别线性档位之所以"能凑出大概"的原因。

### 3.2 每个方向的真实到达距离（腔体形状）

`affectedBlocks` 里每个 `BlockPos` 到爆心的距离已知。按方向分桶（8 个卦限就够，也可用 26 个方向）：

```
对于每个卦限 o：
    maxDist[o] = max over { p ∈ affectedBlocks : octant(p - center) == o } of |p - center|
reach[o]     = min(maxDist[o], 1.35 · Y)       // 夹上限，防单点异常
reachMean    = mean(reach[o])
reachMax     = max(reach[o])
reachMin     = min(reach[o])
anisotropy   = reachMax / max(reachMin, 0.5)   // 1 = 球形空爆；大 = 沿某一轴打出去的（山体、炮管、甲板）
```

**这组数是这次设计的核心新增**：vanilla 已经按 `getBlockExplosionResistance` 逐方块地算过一次"这次爆炸实际铺到了多远"，我们不必重做那个射线行军（重做要读方块、要随机数，服务端 tick 里做很贵），只要把它的结果按方向归一化。

用途：
1. **波的形状**：`reach[o]` 直接当波前的方向半径，于是打进山体的一发会在开口方向鼓出来、其余方向瘪下去 —— 形状自己就说明了地形；
2. **粒子配额**：每个方向分到的粒子数 ∝ `reach[o]^2`（按立体角×半径的球面面积）；
3. **向上的那一项**（`+Y` 卦限）单独拎出来当"烟柱强度"，向下那一项当"地坑扬起"。

### 3.3 分层判据（替代老脚本的 550 / 2000 / 18000）

**主分类轴换成"当量 Y + 暴露度 exposure"的二维格子**，老脚本的 `N` 只保留为兜底（当 `radius()` 读到 0 或负数时）。

| 档 | 当量 Y | 暴露度 exposure | 观感 |
|---|---|---|---|
| 0 忽略 | < 2 | 任意 | 手雷、苦力怕，不生成冲击波 |
| 1 小 | 2 ~ 4 | > 0.6 空爆 | 一发短促的半球形尘环 |
| 2 中 | 4 ~ 8 | 0.25 ~ 0.6 半埋 | 完整的球壳 + 地面环 + 少量烟柱 |
| 3 大 | 8 ~ 16 | < 0.25 深埋 | 定向喷出（按 `reach[o]`），几乎没有完整球壳 |
| 4 特大 | ≥ 16 | 任意 | 球壳 + 地面环 + 烟柱 + 余烬，分 2~3 批放 |

阈值**全部可配**（§8）。**关键**：档位只决定"强度预算"（粒子总数、寿命、速度上限），**形状永远由 `reach[o]` 决定** —— 这样同一档里空爆和钻地也长得完全不同。

---

## 4. 波前传播（§1.1-C 的解）

不要把粒子"一次性从爆心扔出去"，而是把冲击波当成一个**半径随时间增长的前沿**，粒子在前沿扫过时被留下。

### 4.1 前沿运动

```
r(t) = R · (1 - (1 - t/T)^p)
```

- `t` 从 0 到 T（T = 波的寿命，tick）；
- `R = reachMax`（本次爆炸的实测最大到达距离，§3.2）；
- `p` 控制减速：
  - `p = 1` → 匀速（`r = R·t/T`），最像"爆轰波在空气里"；
  - `p = 0.5` → 起步快、明显减速（`r = R(1-(1-t/T)²)`，初速 `2R/T`）；
  - `p ≥ 1.5` → 后段加速，**不建议**，物理上不对但可以做"地滚波"的味道。

当前沿速度 `v(t) = R·p/T·(1-t/T)^(p-1)`：`p=0.5` 时 `v(0) = 2R/T`，`v(T)=0`，这正是"炸开一下然后停住"的形状。

### 4.2 每 tick 做什么

```
对于 t = 0..T：
    rPrev = r(t-1), rNow = r(t)
    面积 ∝ rNow² - rPrev²        // 这一 tick 新增的"壳面积"
    n_tick = clamp( areaFactor · (rNow² - rPrev²) · density, 0, maxPerTick )
    从 n_tick 个方向里抽方向，方向按 §3.2 的 reach[o]² 加权
    对每个方向 d：
        在 rNow 处（沿 d 的实测半径比例）放一帧
        速度 = d · v(t) · (1 ± jitter)，换句话说粒子随波前沿一起走
        再叠一点垂直扰动（旧脚本的 vyRange，现在随 exposure 变小：埋得越深越贴地）
```

这样在任何一个观察点看过去，波前是**一圈正在扩大的壳**，而且壳的厚度由 `rNow² - rPrev²` 自动给出（前期薄、后期厚），不需要额外参数。

### 4.3 与老脚本的衔接

老脚本的"分 1~2 批放完"其实是这个模型的**退化解**：`T = 1`、`p = 1`、所有方向等权、`r(0)=0` → 粒子同点同速。所以新模型可以看作把它的三个自由度打开：`T`（寿命）、`p`（减速律）、`reach[o]`（方向权重）。**老脚本的所有可调项都能映射过来**（§8 给了对照表），迁移不是重做。

---

## 5. 方向权重与配额

```
octantWeight[o] = reach[o]²                      // 球面面积
particleTotal   = clamp(baseParticles · Y^1.5 · (0.4 + 0.6·exposure), min, max)
```

- `Y^1.5`：粒子总数随当量次线性增长（现在老脚本是线性 × 系数，特大爆炸会过量）；
- `(0.4 + 0.6·exposure)`：埋得越深、可见壳越少，粒子越省（深埋时大部分能量进了岩体）；
- 上限 `maxParticles`（老脚本 `maxCount` 的位置）保留，防爆量弹把客户端炸掉。

**分层的粒子构成**（沿用你的权重机制，但按层给不同权重）：

| 层 | 位置 | 建议权重构成 | 备注 |
|---|---|---|---|
| 火球核心 | `r < 0.15R`，仅 `t < 3` | `end_rod` 高权 | 只在大档出现 |
| 壳 | `r ≈ r(t)` | `cloud` 主力 | 主体 |
| 尘环 | `y ≈ 地面`，`r ≈ r(t)` 的水平截面 | 你现有的地面尘粒子 | 只在 `exposure > 0.3` 时生成 |
| 烟柱 | `+Y` 卦限，`r < 0.5R`，`vy > 0` | `shrapnel_smoke{lifetime:15}` | 强度 ∝ `reach[上]²` |
| 余烬 | 全方向，低数、长寿命 | 你的 AP 火花/`impact` | 只在档 3/4 |

**权重配置保持你现在的格式**（key = 粒子 id + 可选 `{lifetime:n}`，value = 权重），只是从"一个全局表"变成"每层一张表"。

---

## 6. mod 环境才能做、建议一并加的三件

这三件都不是粒子，但都建立在 §3 的测量上，而且都在 mod 里是几行：

1. **爆炸光照的半径跟着测量走**。`ExplosionLights` 现在的 `charge/size` 是 `档位 → 常数`。改成 `size ∝ reachMean`、`charge ∝ Y·exposure`：空爆的一发亮而小，钻地的一发暗而广，光与粒子**共用同一套测量**，不会再出现"光很大但尘很小"。
2. **粒子用"强制发送"通道**。你现在走 `level.addParticle(..., true, ...)`（**客户端本地生成**，粒子只在客户端存在），所以任何超出客户端生成范围/被 provider 丢掉的情况都看不见。mod 侧有 `lighting/ForcedParticles`（绕过 vanilla 的 32 格剔除）；冲击波粒子应当由**服务端**发一帧、走 `ForcedParticles` 给范围内玩家，这样远处也能看到波前 —— 这正是"尾迹"那件事踩过的坑，不要再踩一次。
3. **与 Voxy/LSS 的联动**：`ExplosionLights` 已经在同一事件里调 `VoxyLodNudger.queue(...)`（`voxy.minimumBlocks` = 4）。冲击波的档位判据换新后，应把那个阈值也换成 `Y/exposure` 口径，否则"远处看到弹坑"和"近处看到冲击波"会对不同的事件生效。

---

## 7. 诊断（必须有，否则没法调）

一次爆炸一行，`shockwave.debug` 开时打印：

```
[shockwave] Y=12.0 exposure=0.18 solid=0.82 reach[min/mean/max]=3.1/7.4/19.2 aniso=6.2
            tier=3 particles=210 T=18 p=0.5 dirs=(+Y 14.2, -Y 4.0, ...) source=cbc:ap_shell#1234
```

有了 `reach[o]` 和 `exposure`，就能直接看出"这一发为什么长得不对"，而不是对着 `blockCount` 猜。

---

## 8. 配置对照（迁移用）

| 老脚本项 | 新设计对应 | 说明 |
|---|---|---|
| `tiers[n].blockCountMin/Max` | `tiers[n].yieldMin/Max` + `exposureMin/Max` | 主判据换成二维 |
| `speedBase` / `speedFactor` | `front.speedAt(R, T, p)` 自动得出 | 不再从方块数线性推 |
| `countFactor` / `maxCount` / `countMultiplier` | `particles.base` / `Y^1.5` / `maxParticles` | 超线性改次线性 |
| `vyRange` | `verticalSpread × (1 - solidity)` | 埋得越深越贴地 |
| `speedVariance` | `front.speedJitter` | 语义不变 |
| `batchSize` / `totalTicks` | `front.ticks` (=T) + 每 tick 自动配额 | 由面积增量决定 |
| `particleWeights` | 每层一张权重表 | 格式不变 |
| `globalSpeedMultiplier` | `front.speedScale` | 保留 |
| 忽略阈值 550 | `minYield` / `minExposure` | 保留兼容：`N < 550` 仍可作为兜底忽略 |

---

## 9. 落地形式（请你挑）

| 方案 | 做什么 | 优点 | 代价 |
|---|---|---|---|
| **甲：换 KubeJS 脚本** | 服务端脚本改成发 `Y / exposure / reach[8] / source`；客户端脚本按 §4 写波前 | 你可以**先不改 mod**，改完 `/kubejs reload` 就能试；调参不用重新打包 | 拿不到 §6 的三件（光照半径、forced 通道、Voxy 阈值）；粒子仍是客户端本地生成，远处看不到 |
| **乙：进 mod** | 新增 `Shockwaves` 类挂 `ExplosionEvent.Detonate`，粒子走 forced 通道；配置进服务端 TOML | §6 三件全都能做；和爆炸光照共用一套测量；服务端权威、多人一致 | 要改 mod（一轮打包），调参要重启 |
| **丙：先甲后乙**（我推荐） | 先用甲把**算法和手感**定下来（`Y/exposure/reach` 的口径、`T/p/配额` 的手感），确认后再搬进 mod 加 §6 | 风险最低：先验证"看起来对不对"，再决定值不值得进 mod | 有两轮 |

**如果你同意走丙**，我下一步就直接产出一份可 `reload` 的 KubeJS 脚本对（服务端 + 客户端），把 §3/§4/§5 全部实现，并带上 §7 的诊断行。

---

## 10. 需要你确认的三点

1. **CBC 弹药的 `block_damaging_explosive_power` 实际取值**：我打算把档位阈值设成 `Y`（2/4/8/16），这要求我先知道你常用弹种的这个数。要么你给我几个数，要么我用 §7 的诊断行先跑一轮把分布打出来再定档。
2. **破片弹要不要单独的样子**：`FlakExplosion` 在数据上区分得出来（`entityRadius` 与 `blockRadius` 可不同）。要不要给"实体威力高、方块威力低"的弹一种**破片优先**的观感（少尘、多高速小粒子）？
3. **地面环的判据**：我打算用"`center.y` 下方多少格内有实体地面"来决定要不要画地滚环（比老脚本直接用 `y+2.5` 合理）。你希望这个探测深度可配，还是固定 4 格？
