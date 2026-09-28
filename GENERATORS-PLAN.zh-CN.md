# 扬尘 / 引擎烟生成器 设计书（v2，按你的拍板定稿）

两个新方块（信标模型）：**扬尘生成器** `dust_generator`、**引擎烟生成器** `smoke_generator`。机械动力扳手调水平偏移 + 显示/隐藏；扬尘从"生成器 + 偏移"的世界坐标向下找地面；引擎烟不找地面、向世界正上方飘；两者都**只允许放在 Sable 物理体上**；`thruster` 不碰。

---

## 1. 坐标模型（现有机制，零新增）

`SourceAnchor` / `SableAnchors` 已经解决"物理体上的方块"这件事，射灯在用：

| 空间 | 取法 | 用途 |
|---|---|---|
| **plot 空间** | `SourceAnchor.capture(level,pos)` → `(shipUUID, 局部pos)` | 方块/方块实体实际所在处，读 offset |
| **真实世界空间** | `anchor.world(level, resolved, plotPoint)`（内部 `subLevel.logicalPose()`） | 生成粒子、向下 raycast、算速度 |

**一条重要推论（解决了一个我原本担心的问题）**：物理体的方块存在 **plot 坐标**（主世界的另一片区块区），而 raycast 走的是**世界坐标** —— 所以从生成器世界位置向下打射线时，**永远不会打到载具自己的方块**，只会打到真实地形。因此：

- 生成器挂在坦克**内部/顶部**都没关系，尘一定出现在**车底真实地面**上；
- 不需要"跳过自家方块"的特殊处理；
- **Y 由代码决定**（正如你说的），玩家只调水平面 → **offset 只有 X/Z 两个 float**。

---

## 2. 方块与交互（定稿）

### 方块状态 / 方块实体

| 位置 | 内容 |
|---|---|
| blockstate | `visible`(bool，默认 true) —— 复用射灯那套 `getRenderShape` INVISIBLE，客户端无需 BE 同步 |
| blockentity NBT | `offsetX` / `offsetZ`（float，**plot 局部**，默认 0） |

- offset 是 plot 局部：物理体旋转时偏移跟着转（"相对物理体"）。
- 存 NBT → 蓝图/剪贴板复制时一起带走。
- **放置限制**：`getStateForPlacement` 里检查 `SourceAnchor.capture(...).ship() != null`，不在 Sable 物理体内 → **拒绝放置** + 聊天栏提示（"扬尘/引擎烟生成器必须放在 Sable 物理体上"）。没有 Sable 时这两个方块永远放不下去。

### 扳手交互（服务端，最高优先级，取消事件）

| 操作 | 行为 |
|---|---|
| 扳手 + 右键**四个水平面** | 该方向 `offset ± 0.1`（plot 局部），回显当前 offset |
| 扳手 + 右键**顶面/底面** | **不移动**，回显"只能水平调整（高度由地面决定）" |
| 扳手 + **shift** + 右键 | 切换 `visible`（只影响模型，不影响生成） |
| 空手右键 | 不做任何事 |

- **必须拦掉 Create 扳手自身行为**（它右键旋转、shift+右键拆方块）。用 `RightClickBlock` 最高优先级 + 取消 + `SUCCESS`（射灯现成的拦法）。**实机第一件事就是验：shift+右键不能把方块拆下来。**
- 识别 Create 扳手**不引入编译依赖**：查物品注册 id `create:wrench`，或物品类名以 `WrenchItem` 结尾（覆盖附属模组的扳手）。

---

## 3. 生成逻辑（服务端）

### 3.1 怎么找到生成器：**扫描物理体内的自家方块**（不用登记表）

原方案想用 `RegisteredSources` 那样的 SavedData 登记表，现在改成**扫描**，因为更省事也更不容易脏：

1. `SubLevelContainer.getContainer(level).getAllSubLevels()` 拿到所有物理体；
2. 每个物理体 `plot.getLoadedChunks()` → `plot.toGlobal(chunkPos)` → `level.getChunk(gx,gz).getBlockEntities()`；
3. 挑出我们这两个方块类型（带 BE 的）→ 得到 `(物理体, plot坐标, BE)`。

成本：正比于"物理体占几个区块"（一辆坦克最多十几块），**每 2 秒扫一次**足够（生成器方块不会瞬移），完全可忽略。好处：拆装/蓝图/区块卸载都自动正确，没有过期登记、没有 NBT 同步问题。

### 3.2 每 2 tick 的发射循环

- 只处理**玩家在 `cullDistance` 内的物理体**（粒子超出渲染距离看不见）。
- 世界位置 `P = anchor.world(level, resolvedPlotPos, center(plotPos) + (offsetX, 0, offsetZ))`。
- **速度**：缓存上一次的 `P`，`v = (P - Pprev)/dt`（不依赖 Sable 物理 API，最稳）。

**扬尘生成器**

1. 水平速度 `< minSpeed`（默认 0.05 格/tick）→ 不出尘（停车不扬尘）。
2. 从 `P` 沿世界 `-Y` 做 `clip`，最远 `searchDepth`（默认 12 格）：命中 → 在命中点生成；没命中（悬空/水面）→ 跳过。
3. 粒子 `n = clamp(round(水平速度 × rate), 0, maxPerScan)`：
   - 位置：命中点 ± 0.3 随机，再从 `P` 补 1–2 粒（尘从履带附近升起）
   - 初速：主要沿 `-v`（**拖在车后**）+ 向外/向上 0.1–0.3
   - 寿命 20–40，尺寸随速度 0.8→2.0，颜色取**命中方块 map color**（`tintFromBlock`，仅对 vanilla `dust` 有意义）

**引擎烟生成器**

1. **红石控制**：读六个面的红石信号取最大值（同射灯底面读法）。
   - `0` = 停；`1..15` = 出烟量（映射到 `intervalTicks` 与 `count`，见配置）。
   - 判定用 `level.getSignal(pos.relative(face), face)`，并且沿用射灯的 `neighboursLoaded` 保护（绝不为了读信号加载区块）。
2. **不找地面**：直接在 `P` 生成。
3. 初速：`(0, riseSpeed, 0) + v × inertia`（默认 `riseSpeed=0.03`、`inertia=0.15`；车开快了烟被甩在车后——粒子是世界空间的，之后不跟车，这正是要的）+ 少量水平抖动。
4. 寿命 60–140，尺寸 0.5→1.2 膨胀，深灰。

---

## 4. 两个坑的处理

### 坑 1：CBC `cannon_smoke` 不能做引擎烟（**这条直接导致"粒子写进配置"**）

`CannonSmokeParticle.getLightColor` 出生时**无条件**给光照贴图 +240（按 `(1-1.5p)⁴` 衰减），**与 `power` 无关** → 哪怕 `power=0.05`，排气口也会闪一下白光，不像柴油烟。

你的决定：**写成配置项**。所以配置里直接放**粒子 SNBT 字符串**，你在游戏里试出哪个好就填哪个，不用我重新编译：

```toml
	# 直接写 /particle 里那段（可以带参数）；解析失败会一次性警告并回退到默认值
	dustParticle = "minecraft:dust{color:[0.55,0.5,0.42],scale:1.4}"
	smokeParticle = "minecraft:large_smoke"
```

- 解析方式与 `/particle` 命令**完全一致**（`TagParser` + `ParticleType.codec()`），所以你在命令表里试通的任何一条，**去掉命令头和坐标就能直接粘进来**。
- 参数化粒子（`dust`、`dust_color_transition`、`falling_dust{block_state}`、CBC `cannon_smoke{...}`、面包那些无参 id）全部支持。
- 用非 `dust` 的粒子时 `tintFromBlock` 自动失效（会在文档里注明）。

### 坑 2：Create 传动装置读不到 BE —— **被你的规则直接消掉了**

你定了"生成器只允许放在 Sable 物理体上"，所以"方块被 contraption 拿走、世界里读不到 BE"这条不再存在。Create 传动装置的坦克如果要生成器，只能把它做进 Sable 体里。

---

## 5. 配置（定稿，默认按你机器偏弱给保守值）

```toml
[generators]
	enabled = true
	cullDistance = 48
	scanBodyIntervalTicks = 40     # 多久重新扫一遍物理体里的生成器（方块不会瞬移）
	emitIntervalTicks = 2          # 发射循环
	particlesPerTick = 40          # 全局上限，与炮口特效共享
	dustParticle = "minecraft:dust{color:[0.55,0.5,0.42],scale:1.4}"
	smokeParticle = "minecraft:large_smoke"

	[generators.dust]
		minSpeed = 0.05
		searchDepth = 12
		rate = 8.0
		maxPerScan = 6
		lifetimeMin = 20
		lifetimeMax = 40
		size = 1.2
		tintFromBlock = true

	[generators.smoke]
		intervalAtMaxSignal = 2    # 红石 15 时的间隔
		intervalAtMinSignal = 10   # 红石 1 时的间隔
		countAtMaxSignal = 2
		countAtMinSignal = 1
		riseSpeed = 0.03
		inertia = 0.15
		lifetime = 90
		size = 0.9
```

---

## 6. 我打算这么定（**你没反对我就照做**）

1. **引擎烟生成器也限 Sable**（和扬尘一致）。理由：它也是载具部件；如果你以后想当固定烟囱用，改成一行判断即可。
2. **红石语义**：六个面取最大值，`0 = 停`，`1..15 = 出烟量`（不是"有信号就开"）。理由：和射灯"底面=强度"的读法一致，一个红石比较器就能调浓淡。
3. **发现方式**：扫描物理体内的自家方块（§3.1），不用 SavedData 登记表。
4. **不做**（按你说的"先确定功能"）：命令、调试日志、offset 重置、转向额外加尘。

---

## 7. 落地顺序

1. 两个方块 + 方块实体 + 放置限制 + 模型/配方/语言条目；
2. 扳手交互（含拦 Create 扳手）；
3. 扫描器 + 发射循环 + 速度估计 + 红石读法；
4. 扬尘（raycast + 取色）与引擎烟（世界 +Y）；
5. 配置段 + 粒子 SNBT 解析 + 预算；
6. 测试（偏移步进、红石→速率映射、粒子字符串解析回退、速度估计）+ 文档。
