# CBC Muzzle Light —— 交接文档（上下文压缩版）

> 这份文档取代"翻聊天记录"。只保留**现在还有用的东西**：怎么构建、代码在哪、第三方 API 的逆向结论、
> 配置语义、诊断日志、还没验证的待办。已经完成并被用户确认的功能不再复述。

## 0A. 交接快照（2026-09-29）—— 读这一节，不要翻聊天记录

### 已由用户实机验收通过
- **曳光弹尾迹**（本轮主线，0.9.70→0.9.98）。用户原话："终于搞定了"。**不要再改它。**

### 实例与部署（2026-09-29）

- **游戏已从 `G:` 拷到 `D:`（SSD）**，实例目录名不变：`D:\DSH\HelloMC\.minecraft\versions\你好,新蒸程v1.7.1\`。
  **两份实例现在都存在**，改完要各自同步（脚本逐份跑）。
- **新增 `patch/deploy-to-instance.ps1`**：把「这个实例该装什么」变成可执行事实。
  `-Check` 只报告不改；不带则部署。它比对 `mods\` 的 jar（与 `build\libs` 逐字节核对，并删掉
  同 mod id 的旧 jar —— 双份会让游戏起不来），并**重新生成**光影补丁产物后与实例比对/覆盖。
  实例的 `config\*.toml` **绝不被改写**。**换实例、交接、怀疑漏装时先跑 `-Check`。**
- **已踩的坑**：拷到 D: 之后才发现光影补丁是**在拷贝之后**才打的 → D: 那份是未打补丁的旧文件。
  这正是该脚本要消灭的一类问题（症状要到进游戏才看得见，例如手电筒没有光锥）。
- **`patch/*.ps1` 必须纯 ASCII**：本机是 Windows PowerShell **5.1** + ANSI 代码页 **936**，
  无 BOM 的 `.ps1` 会按 GBK 解码，任何中文字面量直接变成解析错误。仓库里每个 `.ps1` 都是纯 ASCII；
  新脚本也因此**自动探测实例目录**，而不是硬编码中文路径。
- **补丁产物是生成的，不要手改**：`patch/source/cbc_muzzle_light.glsl`
  →（`apply-photon-patch.ps1`）→ `patch/photon/shaders/include/lighting/diffuse_lighting.glsl`。
  手改产物会让「源片段」与「产物」脱节，下次重新生成就把手改冲掉。
  （0.9.98 期间发生过一次：两者只有**注释排布**不同，去掉注释后 427 行逐行相同。）

### 手电筒重做（0.10.0，**待实测**）

七项改动全部落在模组侧 —— **光影补丁不用换**。阴影判定仍是补丁里那一行
`if (i < cbcMuzzleShadowCount && tints[i].w <= 0.5)`，改的只是"谁排在前面、谁的标志位为 0"。

| 项 | 实现 |
| --- | --- |
| **位置改腰部** | `HeldLights.waist()` = `脚下 + eyeHeight × 0.55`（站着 0.89 格）。锚在 eyeHeight 而不是固定格数，所以下蹲/游泳会跟着降。点光和光束**共用这个原点**，光束的 apex 就落在光晕上。 |
| **右键=开关，Shift+右键=页面** | `FlashlightItem.use`：`isShiftKeyDown()` 走配置页面（仍限管理员），否则 `FlashlightSettings.toggle`。**开关不限管理员** —— 那是你自己的灯。 |
| **默认计算阴影** | 手电筒光束排到灯列表**第 0 位**且 `selfCull=false`。补丁按**索引**选阴影，所以"预留一个槽"只能靠"排在前面 + 把 count 加一"：`cbcMuzzleShadowCount = reservedShadowLights + effective(shadowLightCount)`。是**加**而不是从里面扣，所以开手电筒阴影不会偷偷花掉炮口闪光的槽。手电筒的**光晕**不投影（`selfCull=true`）—— 为一个 3 格光晕多跑一趟深度步进不划算。 |
| **他人手电筒一并渲染** | 新协议 `FlashlightPeersPayload`。**只发数字**：位置和朝向本来就跟实体一起来，持握状态本来就在装备里，不能推导的只有"设置"和"开关"。服务端每 5 tick 比对一次，变了才发。 |
| **宽度改整数度** | 5°–90°，默认 22°（等于旧 signal 5 的观感）。`FlashlightBeam.halfAngleTangent()`。 |
| **衰减速度（新）** | 20%–300%，默认 100%。**取代**了原来 5 档 falloff：`share = 0.5 / (decay/100)` 再钳到 `[0.02, 0.95]`，且 `middle = tail = share`，于是衰减曲线成了"从轴线满亮到射程末端归零"这条直线的两段近似 —— 100% 正好复现旧默认那一档的形状。 |
| **新默认值** | 点光半径 6→**3**、光束强度 12→**10**、距离 step 8（240 格）→**3（80 格）**。 |

**GPU 灯缓冲排序**（`AdvancedLights.orderForGpu`）：
`本地光束 → 本地光晕 →（他人光束，仅当开了他人阴影）→ 炮口/曳光 → 他人光晕 → 射灯 → 登记灯`。
前排那几盏就是预留的阴影槽（`leadingShadowLights`）。预算账没变，仍是"先扣掉全部非动态光，剩下的给动态"，
所以**炮口闪光不会因为多了手电筒而被挤掉**，只是索引位置变了。

**新配置键**
- 服务端 `[flashlight]`：`decay`(20..300，默认 100)、`syncRange`(0..128，默认 48；**0 = 不同步他人**)。
- 客户端 `[gpu]`：`flashlightShadow`(默认**开**)、`flashlightPeerShadow`(默认**关**)。
- 客户端 `advanced.performance.maxPeerLights`(默认 8)：他人手电筒**自己的预算**，每人占 2 个灯位（光束+光晕），
  单独计账 = 一群人的手电筒挤不掉你自己那盏。

**⚠ 配置迁移（这一条最容易漏，已处理）**
NeoForge **从不覆盖已存在的键**，所以旧 TOML 里的 `width = 5` 在新单位下会变成"**5 度**"（原来 22 度）、
`range = 8` 会变成 240 格而不是 80 格 —— 单位换了，值没换就等于没改。`patch/migrate-flashlight-units.ps1`
专门处理：确认各项仍是旧默认后**删掉整个 `[flashlight]` 段**，让模组按新 spec 重建（这是唯一能同时拿到
新键和新默认的办法）；只要有一项被人工改过就**拒绝执行**并列出。两个实例都已迁移，备份是
`config\cbcmuzzlelight-server.toml.bak-flashlight-units-<时间戳>`。客户端配置不需要迁移（新键是纯新增）。

**为什么阴影以前一直是关的**：`HeldLights.snapshot` 传 `selfCull=true`，而补丁里 `tint.w` 同时表示
"别照亮自己所在方块"和"别投影"（一个 bit 管两件事，"为眼睛上的灯投影会让整个场景的深度步进发抖"）。
挪到腰部之后这个理由不成立了，于是本地手电筒改用 `selfCull=false`。

### 阴影：位掩码 + 视野锥∩球 + 软阴影 + 分类开关（0.11.0，**待实测**）

**先纠正一个常见误解**：光影包里**只有一轮**延迟光照 pass。四种光源（炮火 / 射灯 / 手电筒 / 登记灯）
是同一个 `for i in 0..15` 循环里的四个条目，**不是四遍**。代价 =
Σ「允许投 且 拿到名额 且 像素真的落在它范围内」的灯 × 每盏的采样次数（照不到你的灯花 0）。
深度排序解决"前后"，**不解决"中间隔着什么"** —— 这是下一条的由来。

**为什么以前静态灯永远不投影**：补丁里 `tints[i].w`（`selfCull`）**一个 bit 管两件事** ——
"别照亮自己那个方块"和"别投影"。那不是设计，是**没有别的通道可用**。而且代码注释给的理由
（"射线从灯泡自己那个方块里出发"）是**错的**：射线从**被照表面**出发、且在距灯 `CLEARANCE` 处就停。
真正的自遮挡风险是另一回事：`LightSourceBlock` 用 BER 画**被替换方块的模型**，那个模型**写在深度缓冲里**，
所以射线必须**越过半格**（`CBC_MUZZLE_SHADOW_CLEARANCE` 提到 0.6），否则灯会被自己那个方块遮死。

**为什么"照亮"和"阴影"是两件事**（写下来免得再绕）：
- 延迟着色确实"按深度只照亮最近的面"，G-buffer 每像素只存一个位置 —— 这一步没问题。
- 但阴影问的不是"照亮哪个面"，而是"**这盏灯的光能不能到达这个面**"。
- **深度缓冲是"从相机看出去"的答卷；阴影要的是"从灯看出去"的答卷。相机和灯不在同一个位置。**
- 太阳能预生成一张（阴影图）；16 盏点光源要 16 张。照片里只有太阳有阴影图，Photon 也是。
- 原版/Legacy 档看起来对，是因为原版方块光**根本不是"光"**，是**体素洪水填充**（撞到不透光方块就停）——
  结构上就带遮挡。`takeOverSourceLighting` 抹掉那份白光后遮挡也跟着没了，**这是升级到 GPU 档的真实代价**。
- 屏幕空间射线是"拿相机的答卷硬凑灯的答案"，所以天花板是**遮挡物必须在屏幕内**。

**A —— 名额按「灯的衰减球 ∩ 视野锥」挑**（`LightScreenWeight`，纯函数、9 个单测）
- 每个侧面 `clamp((signed + radius) / radius, 0, 1)` 连乘，再乘实心角 `半径²/(半径²+距离²)` 与亮度。
  `AdvancedLights.frustum()` 用 `Camera.getNearPlane()` 四角构造四个侧面（已验证四角是**相机相对**坐标）。
- **关键**：以前用"灯心落在屏幕哪里"，而**会穿墙漏光的那盏灯在墙的另一侧、屏幕上根本不存在**，
  那个判据永远找不到它。改成视野锥相交后，它就在你正前方两格，球体与锥相交得很充分 → 拿得到名额。
- **半径上限 32 格**：光束的 extent 是它的**长度**，512 格探照灯报 512 格半径，不封顶会仅凭体积压过一切。
- 本地手电筒**光束**仍是**预留位**（不进 `shadowLightCount` 的竞争），齐射挤不掉它。

**B —— 位掩码取代"前 N 盏"**（协议改动，`cbcMuzzleShadowCount` 已删）
- `uniform int cbcMuzzleShadowMask`：第 i 位 = 缓冲里第 i 盏灯投不投。
- 好处不只是表达力：以前"前 N 盏投"逼着模组**重排灯列表**，而列表顺序又决定谁被预算截断 ——
  **选谁投影和选谁留下被绑死了**。掩码把这个耦合彻底拆开，`orderForGpu` 恢复了自然顺序。
- `ShadowBudget` 的帧率渐隐移到 `AdvancedLights.maskFor()` 挑人时应用，效果不变。

**C —— 软阴影 + 每盏灯自己的阴影行为，走 `axis.xy`（0 个新 uniform）**
- **球形灯的 `axis` 向量本来是空的**（`axis.w == 0` 正是"这不是光束"的标记），于是：
  `axis.x` = 这盏灯的半影半径（0 = 硬边）、`axis.y` = 这盏灯自己的采样上限（0 = 用全局）。
  **光束完全不读它**（`axis.w > 0` 时 axis 就是方向），永远硬边。这样"上一盏软、下一盏硬、
  再来一盏多给几次采样"不需要任何额外通道。
- 射线目标点从"灯心"改成"灯周围半径 softness 的小球上随机一点" = **面积光的一次蒙特卡洛采样**，
  **0 次额外采样**。
- 为什么这是省钱而不是费钱：采样次数不足产生的是**空间相关的误差**（相邻像素一起漏判）→ 表现为
  **波纹状断层，TAA 消不掉**；随机抖动把误差变成**不相关噪声 → TAA 几帧就平均成柔和半影**。
  所以"低步数 + 软阴影"是配套的。
- **谁说了算（这一条是关键设计）**：光源方块页面上的 `shadow`/`shadowSteps` 是**作者的愿望**（世界属性），
  客户端配置是**玩家的决定**，而**决定永远压过愿望**：`shadowSoftness = 0` 强制全部硬边、
  `shadowSteps` 是每盏灯的上限、`shadowSources=false` 让所有注册灯一个都不投。
  于是"作者想给这盏灯一个漂亮的软阴影"和"访客不该被迫为别人船上的灯花帧时间"两件事同时成立。

**E —— 每盏灯独立的阴影配置**（静态光源页面，`shadow` + `shadowSteps` 两行）
- `shadow`：0 不投影 / 1 硬边 / 2 软边（新登记默认 **2 软边**）。
- `shadowSteps`：0 = 用客户端上限，否则 1..16，**只会调低不会调高**。
- 存储：`RegisteredSources.Entry.shadowMode/shadowSteps`（SavedData，老存档缺字段时默认 `2 / 0`，
  **不能默认 0** —— 那会让升级后所有已有灯的阴影静默关闭）→ `SourcePayload.State`（+2 字节）
  → `ExtraLights` 塞进 `AreaSnapshot.shadowMode/shadowSteps` → `AdvancedLights` 解算。
- 炮火/曳光那一类是**客户端配置**说了算（`shadowMuzzleSoft`），因为它们不是方块、没有页面。

**D —— 分类开关，全部是客户端偏好**（`ExtraConfig`）
- `shadowSources`（注册灯）/ `shadowSpotlights`（射灯）/ `shadowMuzzle`（炮火曳光）/
  `flashlightShadow`（自己）/ `flashlightPeerShadow`（他人）；`shadowSteps`(默认 8，1..16) / `shadowSoftness`(0..2)。
- **为什么投影是客户端偏好、而颜色亮度是世界属性**（用户定的）：
  别人的载具开过来时，你不该被迫挨个去点光源设颜色亮度；反过来你机器好也不该替所有人决定光影开销。
  → **不改 `SourcePayload`、不改方块状态、服务端零改动**。
- 代价：**类别级**，不是每盏灯级。灯随载具出现消失，客户端没法对"某一盏具体的灯"留偏好（也没有稳定标识）。

**补丁哈希**：`patch/photon/.../diffuse_lighting.glsl` 现在 **27879 字节 / `8CAF4CAF…`**，
670 行、added=395 removed=0，glslang 双配置 exit=0。**旧补丁配新模组 = 编译失败 = 整个光影包不亮**：
`cbcMuzzleShadowCount` 被删、`cbc_muzzle_visibility` 变成五参数。协议字符串 `"7"`→`"8"`，
且 `SourcePayload` 每盏灯多了 3 字节（radius + shadowMode + shadowSteps）。

### 静态光源页面（0.11.0，**待实测**）

- **数据结构是散的，这和射灯不同**：`emission` 是 `LightSourceBlock.EMISSION` **方块状态**，
  `mode`/`color`/**`radius`** 在 `RegisteredSources`（**SavedData**，按 anchor 索引），
  `LightSourceBlockEntity` **只存被替换的方块**。页面因此在两个存储之间读写。
- 页面字段（`LightSourceServer.FIELDS` 的顺序，分两组）：`mode`(取消/启用/强制) / `color`(16 色板) /
  `emission`(1..15) / `radius`(0..64，**0 = 按亮度自动推导**) ‖ `shadow`(不投影/硬边/软边，默认软边) /
  `shadowSteps`(0..16，**0 = 用客户端上限**)。
- **`takeover` 一开始被我放进了页面，是个设计错误，已删**：接管改的是"这里放哪个方块"，作用在**被接管的那个
  lamp** 上，而页面开在 **stand-in** 上、只能报出自己的坐标，够不到目标方块 → 每次点都会被
  "这个方块带方块实体，暂不支持接管"拒掉。**接管/还原留在登记工具**（Shift+右键，光标下的那个方块才是正确的操作对象）。
- 半径链路：`RegisteredSources.Entry.radius` → `SourcePayload.State.radius` → `ExtraLights`
  （0 时退回原来的 `STATIC_RANGE × emission/15`）。**`SourcePayload` 线格式每盏灯多了 1 字节**，两端必须同一构建
  （协议串已 `"7"`→`"8"`）。
- **两条旧交互已删**（用户明确要求）：**亮度增减**（原来空手右键 +1、Shift 右键 −1）和**染料改色**
  （原来 `LightingServer` 里的 `DyeItem` 处理器）。**空手右键 = 开页面**，颜色亮度全在页面里改。
- 登记工具的两个手势**保留为快捷**：右键 = 循环登记模式，Shift+右键 = 接管/还原。
  另外 `TakeoverInteractions` 里"手放的 stand-in 用 Shift 当减值"那条也改成了开页面。
- 打开手势曾经四个全满（空手右键 / 空手 Shift 右键 / 工具右键 / 工具 Shift 右键），
  删掉亮度那条才腾出来 —— 这是删它的**主要原因**，不只是"设置项太多"。

### 本轮增量（0.9.72 → 0.11.0）

| 版本 | 内容 | 状态 |
|---|---|---|
| **0.11.0** | **阴影重做**（位掩码 / 视野锥∩球 / 软阴影 / 分类开关）+ **静态光源配置页面** + 删亮度增减与染料改色 | **待实测** |
| **0.10.0** | **手电筒重做**（腰部 / 右键开关 / 默认投影 / 他人同步 / 宽度改度 / 衰减速度 / 新默认值） | **待实测** |
| 0.9.72 | 寄存器灯具"取替"churn 修复（对称差 + 按解算方块 + GPU 档收口） | 未测 |
| 0.9.73–0.9.75 | 手电筒形状反复（cone ↔ beam）、迪斯科球三项（局部坐标/只留光束/光斑衰减）、接管颜色与提示 | 手电筒见下 |
| 0.9.76/0.9.77 | 距离衰减改为"每盏灯自己的属性"，编码在 **`axial` 的符号**上 | 已实现 |
| 0.9.79–0.9.95 | 尾迹反复失败（见"踩过的坑"） | 历史 |
| **0.9.96** | **尾迹从零重做：服务端发两端点+速度，客户端跑 CBC 插值** | **验收通过** |
| 0.9.97/0.9.98 | **尾迹最后两个根因**（维度遍历误删 / `EntityLeaveLevelEvent` 误删） | **验收通过** |
| 0.9.83/0.9.84 | 冲击波测量 + 客户端波前绘制 | 未测 |
| 补丁修复 | **手电筒在包内全黑的根因**（补丁把 beam 的负号夹掉了） | 待用户实测 |

### 尾迹最终实现（照抄 CBC，只把"在哪画"搬到客户端）

**协议** `ShellTrackPayload`：`(dimension, [Shell(id, x,y,z, px,py,pz, mx,my,mz)])` —— 全部**绝对 double**，
无量化、无参照点、无预测、无 lookahead。服务端只发数据，粒子在**客户端**生成。

**服务端** `TracerFeed`（`LevelTickEvent.Post`，每 tick 每维度各触发一次）：
1. `EntityJoinLevelEvent` 收集 `AbstractBigCannonProjectile` 与 `MediumCannonTrails.shellType`（机炮按设计排除）。
2. 逐弹顺序（**顺序就是正确性**）：
   - `if(!entity.level().dimension().equals(level.dimension())) continue;` ← **只跳过，绝不删除**
   - `if(entity.isRemoved())` → 移除
   - 落地（`big.isInGround()` / 反射 `inGround`）→ 移除。**CBC 落地后要保留 400 tick（20 秒）**，
     等 `isRemoved()` 就会在同一坐标每 tick 加 10 帧 → 那团"不散的白烟"
   - 曳光判定（`hasTracer()` / 反射 `isTracer()`）
   - 距离闸 `trail.feedRange`（512）
   - 发 `previous`(`xOld`) + `now`(`position()`) + `motion`(`getDeltaMovement()`)，坐标全部过 `CBCCompatTransformers.transformVec3`
3. `EntityLeaveLevelEvent` **故意不删除**（Sable 重新归属会触发它）。

**客户端** `TrailTrack`：收到即跑 CBC 的循环，逐字照抄
```java
int frames = from.distanceToSqr(to) > 0.5625 ? 10 : 1;
for (int i = 0; i < frames; i++) {
    Vec3 at = new Vec3(Mth.lerp(i*0.1, from.x, to.x), ...);
    level.addParticle(MuzzleLight.TRAIL.get(), true, at.x, at.y, at.z,
        -motion.x*drift + (rand-0.5)*0.03, ...);   // 速度是弹的 motion，不是步长
}
```
**没有任何本地实体路径、状态、预测、自绘几何。**

**配置**：客户端 `[gpu.trail]` `enabled`/`drift`/`size`/`opacity`/`lifetime`/`color`/`debugLog`；
服务端 `[trail]` `feedEnabled`/`feedRange`(512)。**调试命令**：`/mkTrail <x1> <y1> <z1> <x2> <y2> <z2>`（客户端命令，走同一个 `lay()`）。

### 尾迹踩过的坑（每一个都吃掉过整条尾迹，别再犯）
1. **`LevelTickEvent.Post` 每 tick 对每个维度各触发一次**，而 `shells` 是**全局集合**：主世界那趟喂完，
   下界那趟以 `entity.level()!=level` 为名把这发弹删了 → **每发只喂 1 tick**，每炮只画一节。
   （改成"按维度判断"**同样中招**，因为下界那趟看主世界的弹维度也不匹配。）修法：**跳过、不删**。
2. 早前 `short × 1/4096` 量化 → **±8 格静默夹取**；再改成"相对玩家眼睛"又引入两侧参照点取整不一致。现在**裸 double**。
3. 客户端 `level.getEntity(id)!=null` 即跳过远端绘制 → 弹飞离跟踪范围后**实体对象仍在** → 全部跳过 → 完全无线。
   **现在客户端对每一条收到的数据无条件落帧。**
4. 服务端逐粒子发包时，把"速度"当成了粒子包的 spread —— 那三个 float 是**高斯散布**，于是 10 帧撒成几格宽的团。
5. 自绘四边形带 = 第二套渲染器，纯负担。
6. `nextVelocity` 字段在 CBC `tick()` 开头就被消费清空，在 `LevelTickEvent.Post` 读**必为 null**；
   实体自己的 `getDeltaMovement()` 才是下一 tick 的速度。

### 手电筒：包内全黑的根因（2026-09-29 已修，待实测）
- **症状**：Advanced 档（光影包+补丁）拿手电筒**完全无光锥**；射灯正常；无包档正常。
- **根因**：两份补丁都有 `float radius = max(shape.x, 0.01);`，而手电筒靠 **`axial` 取负**表达"沿长度也衰减"。
  夹取把负数变成 **0.01** → `along > radius` 对 1 厘米外的一切成立 → `continue` → **一个像素都不画**，
  `radius < 0.0` 分支成死代码。射灯 `axial` 为正所以无恙；叠加通道 `cbc_gpu_light.fsh` 先 `abs(axial)` 所以从未坏过。
- **修法**：符号与量值分开读 —— `float range = abs(shape.x);` 用于 cut-off，`shape.x < 0.0` 用于衰减分支。
  已改 `patch/source/cbc_muzzle_light.glsl` 与 `patch/photon/.../diffuse_lighting.glsl`；
  `validate-photon-patch.ps1` 通过（added=327 removed=0，两种配置 glslang exit=0）。
- **已装入两个实例**（D: 与 G:，2026-09-29 用 `deploy-to-instance.ps1` 部署）：
  `shaderpacks/低配photon-cbc/shaders/include/lighting/diffuse_lighting.glsl`
  **22934 字节 / SHA-256 `4C705E5DCB187719…`**（打补丁前的备份 `diffuse_lighting.glsl.bak-torchbug` 22286 字节）。
  > 上一轮手工装的是 22686 字节 `E0DB93F6…`，与现在这份**代码逐行相同**，只是注释位置不同。
- **纯补丁修复，mod jar 无需改动**（模组侧 `axial = -range` 的约定本来是对的）。
- **生效方式**：源码变了必须**重新加载光影包**（光影界面重新点一次该包，或重启）；**F3+R 不会重载 shader**。

### 冲击波（0.9.84 已实现，待实测）
- 服务端 `ExplosionLights.detonate`（`ExplosionEvent.Detonate`，原版事件、来源不限）测量三个量：
  `yield = Explosion.radius()`（= CBC 的 `block_damaging_explosive_power`，无值回落 6）、
  `reach[8]`（`affectedBlocks` 按八象限取最远；**只有三个轴都有符号的方块才归象限**）、
  `solidity = 1 - 平均reach/当量`。
- 档位判据换成**当量**：`explosions.yieldTiers`(6,12,18,24,32)；门槛 `explosions.minimumBlocks` **200**
  （用户实测 TNT≈400，所以 TNT 能进、苦力怕被挡）。
- 光照跟随测量：`charge = 基础 ×(yield/6)×(0.35+0.65×暴露度)`、`size = 实测 reachMean × lightSizeScale`。
- 客户端 `Shockwave`：波前 `r(t)=R·(1−(1−t/T)^p)`，每 tick 只在前沿新扫过的环面落粒子，
  方向按八象限 `reach²` 加权，下半壳在 `groundSlack` 内遇地面则贴地 → 地滚环。
  配置在客户端 `[explosions.shockwave]`（enabled/minimumDensity/density/maxParticlesPerTick/expansion/
  groundSlack/groundOffset/upBias/speedJitter/emberCount/debugLog）；两颗粒子写在
  **服务端** TOML 的 `explosions.shockwaveParticle`/`emberParticle`，语法与 `/particle` 完全一致。

### 已证实的通信协议模式（新功能照这个抄）
服务端测量 → `PacketDistributor.sendToPlayer` → 客户端 `receive` → **客户端本地 `level.addParticle(type, true, …)`**。
实例：`ShockwavePayload`（冲击波）与 `ShellTrackPayload`（尾迹）。爆炸与尾迹两条链都已实机跑通。

### 待清理的陈旧配置键（保留只为不破坏旧 TOML，代码已不引用）
- 客户端 `[gpu.trail]`：`spacing`、`spawnDistance`、`ticks`、`fadeTicks`、`maxPerTick`、`interval`、`debugReceivedData`。
- 服务端 `[trail]`：`frames`（客户端按端点距离决定 10 帧，不再读它）。
## 0. 项目是什么

NeoForge 1.21.1 mod（`dev.cbcmuzzlelight`），给 Create: Big Cannons 及附属提供：炮口闪光/曳光的**动态光照**、
AP 弹命中/跳弹火花、炮口烟、扬尘、火炮后坐、爆炸闪光、迪斯科灯球。

**最重要的约束：本 agent 不能启动 Minecraft。** 所有游戏内行为结论只能来自用户提供的日志行或截图。
交付时必须区分"已验证（编译/单测/资源存在）"和"未验证（需用户进游戏确认）"。

## 1. 路径与环境

| 项 | 值 |
| --- | --- |
| 仓库 | `D:\DSH\McMOD\work\sable-light-overhaul`。**已纳入 git**（远端是私有的 GitHub 仓库，见 §11） |
| 用户实例（D:，**主用**，SSD） | `D:\DSH\HelloMC\.minecraft\versions\你好,新蒸程v1.7.1\` |
| 用户实例（G:，旧） | `G:\HelloMC\.minecraft\versions\你好,新蒸程v1.7.1\`（仍在，内容需与 D: 同步） |
| 编译用依赖 jar | `libs/`（`cbc.jar` 5.11.7、`sable.jar`、`sable-companion.jar`、`iris.jar`、`ldlib2.jar`+`taffy.jar`+`yoga.jar`、`yuushya.jar`、`cbcat.jar`、`copycats.jar` …），由 `prepare-libs.ps1` 解 jar-in-jar |
| 反编译工具 | CFR：`java -jar D:\DSH\McMOD\work\cfr.jar <jar> --jarfilter <前缀> --outputdir <目录>` |
| PowerShell | **Windows PowerShell 5.1**（不是 pwsh 7），ANSI 代码页 **936**；脚本用 `& '<file>.ps1'` 直接跑。`patch/*.ps1` 必须**纯 ASCII**（见 §0A） |

## 2. 构建、测试、交付

```powershell
cd D:\DSH\McMOD\work\sable-light-overhaul
.\gradlew.bat build        # 产出 build/libs/sable-light-overhaul-<version>.jar（约 3 分钟）
.\gradlew.bat test         # src/test 下 20 个 JUnit5 测试文件
```

- **版本号唯一真源 = `build.gradle` 的 `version`**。`src/main/resources/META-INF/neoforge.mods.toml` 里现在写
  `version="${version}"`，由 `build.gradle` 的 `processResources` 展开（0.9.50 起）。
  *历史坑：mods.toml 曾是硬编码 `0.9.5-alpha`，导致游戏 mod 列表永远显示 0.9.5，无法判断装的是哪个 jar。*
- **交付 = 跑部署脚本，不要手工拷文件**：
  `& 'D:\DSH\McMOD\work\sable-light-overhaul\patch\deploy-to-instance.ps1' -Instance '<版本目录>'`。
  它把 jar 与光影补丁都对齐到仓库产物，并删掉同 mod id 的旧 jar。
  **每次产出新 jar / 新补丁后，对每个实例各跑一遍**（现在有 D: 与 G: 两份）。
  只想确认状态就加 `-Check`：退出码 `0` = 全部一致，`1` = 有问题（并列出）。
  只改 Java 不动 GLSL 时，重新 `build` 后再跑一次即可：jar 会更新，补丁那项显示 `OK`。
- **补丁产物由脚本生成，不要手改**：`patch/apply-photon-patch.ps1` 从
  `patch/source/cbc_muzzle_light.glsl` + Photon 原文件生成 `patch/photon/.../diffuse_lighting.glsl`
  （两锚点、幂等、纯增量：275→602 行，added=327 removed=0，glslang 双配置 exit=0）。
- **改完 GLSL 必须让 Iris 重新加载光影包**（在光影列表里重选该包，或重启游戏）；**F3+R 不会重载 shader**。
- **配置默认值不会覆盖用户已有键**（NeoForge 行为）：改 `defineInRange` 的默认值对老用户无效。
  要么让用户手改，要么重新生成 TOML（必须保留用户已调的值），改完明确告诉用户要改哪几项。
- 编辑事故的恢复路径：`build/libs/sable-light-overhaul-<version>-sources.jar` 里有完整源码。

## 3. 档位（tiers）

`Legacy` / `Compatible` / `DynamicOnly` / `Advanced`（见 `LightingMode`）。
- 只有 `Advanced` 出颜色（依赖 Photon 补丁）。
- GPU 灯上限：`IrisMuzzleBridge.MAX_LIGHTS = 16`、`GpuLightRenderer.MAX_LIGHTS = 32`（想升 16→32 必须用户重抄补丁）。
- `AdvancedLights.orderForGpu` 的排序（0.10.0 起）= **本地手电筒光束 → 本地光晕 →（他人手电筒光束，若开他人阴影）
  → 炮口/曳光 → 他人光晕 → 射灯 → 登记灯**。前排即"预留阴影槽"；预算账仍是"先扣掉全部非动态光，剩下给动态"，
  所以炮口闪光不会被手电筒挤掉。

## 4. 代码地图（按功能，不按包）

| 功能 | 文件 |
| --- | --- |
| 闪光数据流 | `mixin/BigCannonMixin`、`AutocannonMixin`、`MediumCannonMixin` → `ServerFlashes.emit/emitSized` → `FlashPayload`(kind 0/1/2，协议字符串 `"6"`) → `FlashStore`（客户端，`valid` 接受 0/1/2）→ `client/ClientFlashes` |
| 光引擎接线 | `mixin/LightEngineAccessor`、`SectionCompileLightMixin`、`BlockLightMixin`、`Copycat*Mixin`、`client/LightWorldView`、`LightReadScope`、`AreaSnapshot` |
| Lamb 动态光后端 | `client/LambInitializer`、`LambAreaBackend`、`mixin/LambTickMixin`、`client/YuushyaLighting` + `mixin/Yuushya*` |
| Advanced / GPU | `client/AdvancedLights.computeWorld` → `client/IrisMuzzleBridge`（uniform，含 `uniform4f` 单独装 leftover）→ 补丁 `cbc_muzzle_light.glsl`；`client/GpuLightRenderer`；`client/IrisCompat`、`mixin/IrisUniformMixin`、`IrisMixinPlugin` |
| Sable 桥 | `client/SableBuildScope`、`SableLightBridge`、`AdvancedSable`、`lighting/SableAnchors`、`SourceAnchor` |
| 粒子 | `client/GroundDustParticle`(+`lighting/GroundDustParticleData`)、`client/ImpactParticle`(+`lighting/ImpactParticleData`)、`client/AutoMuzzleParticle`、`client/SmokeTracker`/`TrackedSmoke`、`client/EmissiveParticles`、`AreaLight`、`BeamLight` |
| CBC 集成 | `MediumCannonEffects`（扬尘/火花/烟，实际已改名/合并见下）、`client/ApSparks` + `mixin/BlockHitEffectMixin`（AP 火花，**所有弹丸共用**）、`ExplosionLights` |
| 标记 / 表面采样 | `lighting/MarkerBlock`、`HullMarkerBlock`(+`HullMarkerBlockEntity`，实现 `BlockEntitySubLevelActor`)、`SableMarkers`、`SableSurfaces` |
| 光源管理器 | `lighting/LightSourceBlock`(+`BE`)、`client/LightSourceRenderer`、`TakeoverInteractions`、`RegistrarItem` |
| 发生器（光源方块 + UI） | `lighting/GeneratorBlock`(+`BE`)、`GeneratorUi`、`GeneratorLdlib`、`client/GeneratorLdlibClient`、`GeneratorFields`、`GeneratorValues`、`GeneratorKind`、`GeneratorEditPayload`、`GeneratorInteractions`、`SableGenerators` |
| 迪斯科灯球 | `lighting/DiscoBallBlock`(+`BE`)、`DiscoBallLdlib`、`DiscoBallUi`、`client/DiscoBallLdlibClient`、`client/DiscoBallLights` |
| **静态光源页面**（0.11.0） | `LightSourceEditPayload` → `lighting/LightSourceServer`（读/写/钳制，跨方块状态 + SavedData）、`LightingBlocks` 注册、`client/LightSourceLdlibClient`；页面类 `lighting/LightSourceLdlib`、`LightSourceUi`。**空手右键开页面** |
| 阴影名额 | `LightScreenWeight`（纯函数：视野锥∩球 + 实心角 + 亮度，有单测）、`client/AdvancedLights.maskFor/frustum`、`client/ShadowBudget`（帧率渐隐）、`client/IrisMuzzleBridge`（`cbcMuzzleShadowMask`/`Steps`/`Softness` 三路 uniform） |
| 后坐 | `SableRecoil`（排队 + `HullMarkerBlockEntity.sable$physicsTick` 里施加） |
| **手电筒**（0.10.0 重做） | `FlashlightItem`（右键开关 / Shift+右键页面）、`FlashlightBeam`（宽度度数 + 衰减速度 → 曲线，**纯算术，有单测**）、`lighting/FlashlightSettings`（服务端权威 + 开关 + **他人手电筒广播**）、`lighting/FlashlightLdlib`/`FlashlightUi`、`client/HeldLights`（腰部原点 + 本地与**他人**光束的绘制）、`FlashlightStatePayload`、`FlashlightPeersPayload` |
| 配置 | 服务端 `lighting/LightingCommon`（TOML `cbcmuzzlelight-server.toml`）；客户端 `client/ExtraConfig`（`[gpu]`）、`client/MuzzleClientConfig` |
| mixin 配置 | `cbcmuzzlelight.mixins.json`、`cbcmuzzlelight.cbcat.mixins.json`、`cbcmuzzlelight.advanced.mixins.json`、`cbcmuzzlelight.iris.mixins.json`、`cbcmuzzlelight.cbcmodernwarfare.mixins.json`（都在 `neoforge.mods.toml` 注册） |

> `MediumCannonEffects` 相关行为（扬尘、AP 火花、烟）在重构中拆到了 `client/ApSparks` 与 `lighting/SableSurfaces`；
> 改之前先 `glob`/`grep` 确认真实位置，不要凭记忆改文件。

## 5. 第三方 API 事实卡（逆向得来，别重复劳动）

### Create: Big Cannons
- `BlockHitEffect.playEffect(level, deflect, forceDisplay, x, y, z, dx, dy, dz, entityType, blockState, projectileEffect)`
  —— 每方块命中特效，**客户端执行，所有弹丸共用**。包字段 `bounced` = 跳弹，`effectNormal`：
  命中 = `curVel.reverse()`；跳弹 = `curVel - normal*(normal·curVel*elasticity)` → 正好是 AP 火花要的方向。
  `ProjectileHitEffect` 在 `rbasamoyai.createbigcannons.block_hit_effects`。
- CBC 的火花粒子用自定义 `ParticleRenderType` + 手设 entity shader + `disableBlend()` + 实心 1×1 纹理
  （`createbigcannons:textures/entity/color.png`），**没有 atlas sprite**（`particles/spark.json` 是 `{}`）→ Iris 下不可见；
  `useShaderCompatibleGraphics` 不覆盖火花。火花还一 `onGround` 就 `remove()`（不弹跳）。
  → 这就是我们自己写 `ImpactParticle`（sprite 粒子 + 手动弹跳 + `FULL_BRIGHT`）的原因。

### Sable
- `RigidBodyHandle`：`applyLinearAndAngularImpulse(Vector3dc, Vector3dc, boolean)`、`addLinearAndAngularVelocity`、
  `applyImpulseAtPoint(Vector3dc point, Vector3dc impulse)`（**arg0=作用点，arg1=冲量**）、
  `getLinearVelocity/getAngularVelocity`、`of(ServerSubLevel)`、`isValid()`。
- **只有 `dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor#sable$physicsTick(ServerSubLevel, RigidBodyHandle, double)`
  里施加的力不会被丢弃**（物理管线每 tick 用自身状态重建刚体）。
  `SablePrePhysicsTickEvent`/`PostPhysicsTickEvent` 是回调**接口**，不是事件总线事件。
- Flight Control 惯用写法（`create_flight_control-neoforge-0.5.1.jar`）：
  `if (force.lengthSquared() > 1.0E-18) handle.applyLinearAndAngularImpulse(force.mul(dt), torque.mul(dt), true);`
- 其它：`SubLevel.boundingBox()` 观测到是**世界空间**；`SubLevelHelper.getConnectedChain`；
  `SubLevelContainer.getContainer/getAllSubLevels/getSubLevel(UUID)`；`LevelPlot.getLoadedChunks()`；
  `MassData.getMass/getInverseMass/getCenterOfMass`；`ServerSubLevel.getMassTracker()`。

### Mianbao's Modern Warfare（`mianbaos_modernwarfare-2.5.3-neoforge.jar`，MCreator `net.mcreator.myfirstmod`）
- 有**精灵粒子**（着色器可见）：`explosionblast` / `explosionlarge` / `explosionsmall(_2)`。
- 拖尾公式：每轴 `Mth.nextDouble(-1,1)*particleRadius`，外加一个零速拖尾精灵。

### LDLib2
- 可选依赖（compileOnly），生成器页 / 迪斯科页靠它。缺失时 `*Ldlib` 门控 → 退回聊天栏读数。

## 6. 配置地图

服务端 `cbcmuzzlelight-server.toml`（`LightingCommon`）：

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `tankCannon.recoil.enabled` | `false` | 唯一能"弄坏存档"的功能，默认关 |
| `tankCannon.recoil.velocity` | `0.02` | **每物理步**的 Δv（blocks/tick），范围 0..5.0 |
| `tankCannon.recoil.physicsSteps` | `4` | 总计 = velocity × steps |
| `tankCannon.sparks.bursts` | `4` | AP/炮口火花数 |
| `tankCannon.flame.speed` | `0.325` | 炮口烟速度 |
| `explosions.minimumBlocks` | `300` | 一档爆炸光照阈值；`tier2`/`tier3`、`charge`、`size` 同理 |
| `[disco]` | — | enabled/colors/beams/spin/range/width/intensity |
| `[flashlight]` | — | `color` 0、`range` 3（=80 格）、`width` **22 度**、`decay` **100%**、`intensity` **10**、`pointRadius` **3**、`pointIntensity` 10、`syncRange` **48**（0 = 不同步他人手电筒） |
| `generators.debugLog` | `true` | **所有诊断日志的总开关**，调完可关 |

客户端 `cbcmuzzlelight-client.toml`（`ExtraConfig` 的 `[gpu]`）：
`GPU_TAKEOVER`、`DUST_GROUND_PLANE`/`DUST_CAMERA_FACING`、`AP_*`（enabled/color 0xFFC070/hit count/speed/spread/size/lifetime、
ricochet count/chips/speed/spread/size/lifetime/bounce）、`DISCO_*`（enabled/colors/beams/range/width/intensity/cull/scanTicks/scanChunks）、
`shadowLightCount` 0..128、`maxStaticSources` 32、`activationDistance` 512、`intensity`（用户调到 0.2）、
`packPathIntensity` 2.0、`normalInfluence` 1.0、
**`flashlightShadow`（默认开）**、**`flashlightPeerShadow`（默认关）**、**`advanced.performance.maxPeerLights`（默认 8）**、
**`shadowSources`（默认开，注册灯是否投影）**、**`shadowSpotlights`（默认开）**、**`shadowMuzzle`（默认开，炮火/曳光）**、
**`shadowMuzzleSoft`（默认开，炮火/曳光用软边）**、**`shadowLightCount`（默认 6）**、
**`shadowSteps`（默认 12，1..16，是每盏灯的上限）**、**`shadowSoftness`（默认 0.2 格，**0 = 强制全部硬边**）**。
> 最后五项 + `flashlightShadow`/`flashlightPeerShadow` 是**客户端偏好**，不是世界属性 —— 见 §0A。

静态灯自己的设置**不在 TOML 里**：`mode`/`color`/`radius` 在 `RegisteredSources`（`SavedData`，
`data/cbcmuzzlelight_sources.dat`），`emission` 是 `LightSourceBlock.EMISSION` 方块状态，由**页面**改。

**配置坑**
- 带**冒号**的 `String` 配置值会让第三方 GUI 崩游戏
  （`net.createmod.catnip…ServerboundConfigPacket.serialize` → `IllegalArgumentException: unknown type createbigcannons:cannon_smoke{po`）。
  → 所有粒子字符串键已**从配置里删掉**，粒子改成代码硬编码。新增配置项**不要用带冒号的字符串**。
- `[colors] overrides` 列表还在，未测过 GUI 表现。

## 7. 诊断日志速查（都由 `generators.debugLog` 控制）

| 日志片段 | 含义 |
| --- | --- |
| `recoil: applied <Δv>/tick to <uuid> at <muzzle> (anchor=hull-marker\|largest-box)` | 已排队一次后坐 |
| `recoil: velocity delta <vec> applied in the physics step on body <uuid> (speed A -> B, inverseMass=…, stepsLeft=…, angular=…)` | 每物理步真实生效值；**`angular` > 0 才说明有抬头力矩** |
| `dust surfaces: body=… anchor=… radius=… blocks=… first=…` | 扬尘采样面（真方块扫描结果） |
| `cannon dust: ground probes/hits/spawned` | 地面扬尘预算 |
| `sable space: …` | 包围盒空间自动判定 |
| `explosion: blocks=… tier=…` | 爆炸光照档位 |
| `disco: …`、`generators found=…` | 迪斯科 / 发生器 |

## 8. 0.9.50 变更（本版）

1. **后坐上限 0.5 → 5.0**（每物理步），保留"特色火炮航天"：配置允许把车掀起来，钳制只防手滑数值把建筑送出地图。
   配置注释同步说明"大炮允许发射自己的载具"。
2. **修复 mod 版本号冻结**：`mods.toml` 用 `${version}` + `processResources` 展开，游戏 mod 列表从此显示真实版本。
3. **修复后坐日志**：格式串缺占位符导致 `stepsLeft`/`angular` 被静默丢弃 → 现在真的会打印（这是验证抬头力矩的唯一手段）。
4. 清掉 `SableRecoil` 里的死代码（未使用的 `impulse` 局部变量）与自相矛盾的注释。

## 8.5 迪斯科灯球：为什么"放下去完全不亮"（已修）

**先回答两个常见误解：不需要红石，也不需要打开配置页。** 服务端 `disco.enabled=true`、客户端 `disco.enabled=true`，
放置时 `DiscoBallBlock.setPlacedBy` 调 `DiscoBallBlockEntity.useConfiguredDefaults()` 把服务端配置写进球自己的方块实体。

**真正的根因（0.9.52 修）**：`DiscoBallBlock.getStateForPlacement` 旧代码要求
`SourceAnchor.capture(...).ship()!=null`，即**只能放在 Sable 物理体上**；而 `DiscoBallLights.scan` 只在**主世界区块**里找球。
船的方块存在船自己的 **plot**（另一套区块，见 `SableMarkers` 用 `body.getPlot().getLoadedChunks()` 读 plot），
所以主世界扫描**永远找不到已经放下的球** → 一个光锥都没有、没有任何报错。这是"完全不亮"的完整解释。

0.9.52 起 `scan` 同时扫两处：主世界区块 + `SubLevelContainer.getContainer(level).queryIntersecting(玩家周围 cull+16 的盒)`
拿到附近物理体，逐个遍历其 plot 区块（`section.maybeHas` 先过滤 palette）。plot 里的球记录为 `(SubLevel, plot 坐标)`，
**每帧**用 `ship.logicalPose().bakeIntoMatrix(...)` 把 plot 坐标变换到世界坐标，所以船在动、在转，灯球的光也跟得上
（每秒只重新扫描一次球的位置，但世界坐标是每帧算的）。同时放开了放置限制：地上也能放（地上走主世界扫描那条路）。

其余仍会让它"看起来没反应"的条件：
1. 档位必须是 `Compatible` / `DynamicOnly` / `Advanced`（`ClientFlashes` 的 switch：`Legacy` 只走原版光引擎，不画光锥）。
   `Advanced` 还要求确实加载了光影包，否则自动降级为 `DynamicOnly`（仍会画）。进游戏时会有一条聊天提示
   `requested=…, active=…`，日志里也有同样一行。
2. 客户端每 `disco.scanIntervalTicks=20` tick 扫一次、主世界半径 `scanRadiusChunks=4` 区块；玩家距球超过 `cullDistance=64` 不画。
3. 光锥是**光照**（`BeamLight` → `AreaSnapshot`），不是可见的几何体：它照亮周围方块，不会在空中画出实心光柱。

新日志行（`generators.debugLog=true` 时）：
`CBC Muzzle Light disco: found N ball(s) near <pos> (X in the world, Y on bodies)` —— 这一行直接告诉我们扫描到没有：
`N=0` 说明区块/plot 没扫到（位置或加载问题），`Y>0` 说明物理体路径生效。

0.9.51 起光锥参数**优先读球自己的方块实体**（页面改的值），客户端配置只作为 BE 尚未同步时的兜底。
在此之前 `DiscoBallLights.append` 从头到尾只读 `ExtraConfig`，**页面上调什么都不生效**
（与 `DiscoBallBlockEntity` 类注释声称的"所有人看到同一场表演"矛盾）。
`cullDistance` / `scanIntervalTicks` / `scanRadiusChunks` 仍属客户端个人偏好，故意保留为本地配置。

## 8.6 粒子与尾迹（0.9.53）

### 强制加载：vanilla 的 32 格剔除（仍然有效）
> **本节其余部分是历史，不要照着做。** 尾迹已在 0.9.96 从零重做并验收（见 §0A）：
> 服务端**只发数据**（每发弹两个端点 + 速度），客户端跑 CBC 的插值循环自己生成粒子。
> 下文提到的 `ExtraLights.trails` / `TrailFeedPayload` / `client/TrailTrack` 早期形态 / 自绘带子
> **都已不存在**。这里只保留两条仍然有用的结论：下面这段"32 格剔除 + ForcedParticles"，
> 以及 `trail` 粒子类型的 `overrideLimiter=true`。

`ServerLevel.sendParticles(...)` 内部会丢掉 32 格以外的玩家**包括那个接收玩家列表的重载**——
距离判断在方法体里（`longDistance ? 512 : 32`），跟"我们自己的 `tankCannon.cullDistance=160`"无关。
所以远看开炮只有光、没有尘和火花，这就是"超出 32 格就被剔除"的真相。
`ClientboundLevelParticlesPacket` **没有距离字段**（构造器只有
`(ParticleOptions, boolean overrideLimiter, x,y,z, xDist,yDist,zDist, maxSpeed, count)`），
距离纯粹是服务端自己决定发不发的。
→ 新增 `lighting/ForcedParticles`：自己建包直接 `player.connection.send(...)`，绕过距离判断；
所有服务端粒子发送点（`ParticleSpec.spawn`、`MediumCannonEffects.plume`、`MediumcannonBreechMixin` 的 CBC 炮尾烟）
都改走它。开关：服务端 `particles.forceLoad`（默认 true）。
另外把 `IMPACT` / `GROUND_DUST` 两个 `ParticleType` 的 `overrideLimiter` 从 false 改成 true，`TRAIL` 也是 true。
客户端粒子（`level.addParticle`）本来就没有距离剔除，无需处理。

> 注意：`getDeltaMovement()`/实体坐标在物理体上仍是船空间，所有位置都要过 `CBCCompatTransformers.transformVec3`。

### 曳光弹尾迹（`client/TrailParticle` + `ExtraLights.trails`，0.9.56 改版）
- **纯客户端跟踪**：`ClientFlashes.tick` → `ExtraLights.trails(level)`，用的是客户端通过
  `EntityJoinLevelEvent` 自己收集的弹丸集合与 `e.position()`（已过 `CBCCompatTransformers.transformVec3`）：
  不发任何包，也没有 32 格剔除。**每个档位都跑**（Legacy 档不进光照 pass，所以不能写在 `append` 里）。
- **只给中炮和大炮**：机炮（`AbstractAutocannonProjectile`）**明确排除**——一轮点射是密集弹丸流，
  每发都拖尾会把整串连成一条实心绳。判定：
  - 大炮：`AbstractBigCannonProjectile.hasTracer()`
  - 中炮（CBC Neo Warfare，modId `cbcmodernwarfare`，可选依赖）：`AbstractMediumcannonProjectile.isTracer()`。
    所有对附属类型的引用都关在 `client/MediumCannonTrails` 里，调用前先问 `present()`，
    没装该附属时不会触发它的类加载。注意中炮**不继承** `AbstractBigCannonProjectile`，
    它直接继承 `AbstractCannonProjectile`，所以大炮的判定看不到它。
    （中炮自己的 `tick()` 里有一套基于 CBC `bigCannonTrailType` 配置的淡淡尾迹，这就是之前那条不够显眼的曳光。）
- **连续性靠补段**：不再"每 tick 在当前位置丢一颗"，而是用
  `TRAIL_LAST`（`WeakHashMap<Entity,Vec3>`）记住每发弹上次落帧的位置，本次按 `trail.spacing`（默认 0.5 格）
  沿两点插值填满整段，上限 `trail.maxPerTick`（24）。一 tick 跨好几格的高速炮弹因此不再是虚线。
  `trail.interval` 变成"每 N tick 补一段"，间隔期间走的路照样会被填上，所以调大只是省算力，不会断线。
- 贴图：取自 `mianbaos_modernwarfare:textures/particle/rocketmissiletrail_*`（16×16，40 帧其实只有 9 张不重复），
  选 4 张不同内容的满帧重上色成"热白光"（`L*2.2` → 暖白，`L>0.8` 推白，alpha ×1.35），
  再用 Java 工具做两次 3×3 alpha 模糊羽化（原始 alpha 是硬边 {0,255}，不羽化就是个卡通圆点）。
  输出 `assets/cbcmuzzlelight/textures/particle/trail_0..3.png` + `particles/trail.json`。
- 新粒子类型 `cbcmuzzlelight:trail`（`MuzzleLight.TRAIL`，`SimpleParticleType(true)`），provider 在 `ClientBootstrap` 注册。
- 物理：**无重力**（`gravity=0`、`hasPhysics=false`），速度 = 炮弹速度反向 × `trail.drift`，
  每 tick ×`trail.damping` 衰减 → 原地悬停，形成"光绳"而不是第二发炮弹。
- 配置（客户端 TOML，`[trail]`）：`enabled` / `lifetime`(12 tick) / `interval`(1) / `spacing`(0.5 格，连续度主旋钮) /
  `maxPerTick`(24) / `size`(0.45) / `opacity`(0.9) / `color`(0xFFD9A0) / `drift`(0.06) / `damping`(0.90) / `spawnDistance`(160)。

### 铁花（火花）持续时间在哪改
- 炮口火花（`ImpactParticle` STREAK，`MediumCannonEffects.sparks`）：
  **服务端** `cbcmuzzlelight-server.toml` → `[tankCannon.sparks]`：`lifetime`（默认 18 tick）、
  `bounce`（0.45，弹跳次数 ≤3 次，调低会更快消失）、`bursts`、`speed`、`size`、`color`。
- AP 命中/跳弹火花（客户端）：`cbcmuzzlelight-client.toml` → `[apSpark.hit] lifetime`（12）、
  `[apSpark.ricochet] lifetime`（20）。

### 灯球：上半球 + 稳定光斑
- `DiscoBallLights` 的光锥改成走**上半球**：仰角从近乎正上方到略高于水平
  （`y = 1-0.85*h`，`h=(i+0.5)/beams`），方位角用黄金角 137.5° 错开并随 `spin` 整体旋转。
  开关 `disco.upperHemisphere`（默认 true，关掉回到原来那条 y=0.45 的浅环）。
- **它以前不照地面**，因为光锥按设计全部朝上，永远到不了地板；而且所有亮斑都在转，
  所以永远看不到"稳定的光斑"。现在每颗球额外加一个**全向球光**（`sphere()`，与注册灯具同形）：
  `disco.ambientRadius`（12，0 关）与 `disco.ambientIntensity`（8），颜色取球调色板第一项。

### 扬尘：不再"扩大"，水平尘图层已删（0.9.54/0.9.55）
- **"扩大"的真正来源不是尺寸**，而是逐帧换图：`ground_dust.json` 原来引用 vanilla 的
  `minecraft:big_smoke_0..11`。实测这 12 帧（覆盖像素 / 包围盒）：
  `0:33(7x8) 1:52(10x8) 2:81(12x10) 3:89(11x11) **4:107(12x12 最大)** 5:86 6:72 7:47 8:36 9:21 10:12 11:4`，
  也就是说它在**第 4 帧涨到最大、之后一路消散**，从第 0 帧开始播自然就是"自己变大"。
- **0.9.55：拷贝成我们自己的粒子，并且从最大帧开始播**。
  已把第 4..11 帧复制为 `assets/cbcmuzzlelight/textures/particle/dust_0..7.png`（`dust_0` = 原第 4 帧 = 最大），
  `particles/ground_dust.json` 改为引用这 8 张（不再依赖 `minecraft:big_smoke_*`）。
  代码逻辑保持原样（`tick()` 里 `sprites.get(age, lifetime)` 逐帧推进），只是 sprite 集合本身从最大帧开始，
  所以云一出场就是满尺寸、之后只会消散，**lifetime 不变**。
- **水平尘图层已删除**：那是 `GroundDustParticle.render` 里的 world-XZ 平铺 quad（`dust.groundPlane`），
  和相机面图层同时开时两边各乘 0.6 alpha，正常视角基本看不到。现在只剩相机面一个 quad，
  `dust.groundPlane` / `dust.cameraFacing` 两个配置键一并移除（TOML 里这两个键会在下次启动时被清掉）。
- 想继续微调大小：扬尘尺寸仍走服务端 `[tankCannon]` 的 `ground.size*` / `hull.size*`（`DUST_SIZE_MIN/MAX` 一路）。

## 8.7 射灯：三种控制模式 + 锁存 + 射角（0.9.57）

- **方块实体化**：新增 `SpotlightBlockEntity`（`LightingBlocks.SPOTLIGHT_ENTITY`）。`RANGE/MODE/VISIBLE`
  **仍留在 blockstate**（服务端读前两个构建光束、`getRenderShape` 读第三个），BE 的 `number/setNumber`
  对这三个字段直接读写 blockstate —— 一个值只有一个家，不做两份。其余全在 BE。
- **三种模式**（`control`）：
  - `0 FULL`（旧行为）：底面=强度、顶面=宽度、侧面=颜色；页面上的 `switch` 是**总闸**（与强度相乘，所以全红石灯也能手动关）。
  - `1 SEMI`：红石只当开关。不锁存 = 有信号即开；锁存 = **上升沿翻转** `on`。
  - `2 MANUAL`：红石完全忽略，页面 `switch` 说了算。
  - 半红石/手动模式下 `intensity/width/color` **全部来自页面**（`color` 就是 0..15 调色板索引）。
- **锁存**：`SpotlightBlockEntity.tick()` 服务端 ticker，**逐 tick** 读 6 面最大信号（`anySignal`，每面先 `hasChunkAt` 防加载区块），
  上升沿翻转 `latchedOn`。只有 `control==SEMI && latch` 时才做任何读取，其余灯零开销。
  `lastSignal` **写进 NBT**，且 `setNumber` 之后会 `arm()` 重读一次：否则"开着拉杆时打开锁存"或"载入存档时拉杆还立着"
  都会被误判成一次上升沿，灯会自己翻转一次。
- **射角**：`SpotlightBlockEntity.aim(tilt,azimuth)` = 从方块局部 +Y 起算的仰角 + 方位角（局部空间）。
  - FULL 模式：`sweep(signal)` = `clamp(信号 1→15 映射到 angleMin→angleMax) 再 + angleOffset`，最后 clamp 到 ±90
    （先限位、后加补偿：限位是限位，补偿是整体偏置）。
  - SEMI/MANUAL：直接用页面的 `angle`。
  - 上下限/补偿都允许 **-90..90**，所以可以往下照。方位角默认取放置时玩家朝向（`90 - yaw`）。
  - **没有挪用 blockstate 的 `FACING`**：它已被写进所有旧存档射灯，赋予含义会让它们集体偏转（代码里注明了）。
- **客户端**：`ExtraLights` 用 `a.world(pos + aim(tilt,azimuth))` 求世界方向 —— 与原来 "+1 Y" 同构，
  所以船转向时射灯仍然跟着转。
- **页面**：空手右键开页面（`SpotlightUi` → `SpotlightLdlib`，14 行数字输入；LDLib2 缺失时退回聊天栏读数）。
  登记工具仍是快捷键：右键循环衰减、Shift 切换模型；**距离改到页面里**（`range` 上限 = `LightingCommon.count()`，
  不是 blockstate 的 32）。
- **协议**：`SourcePayload.State` 新增 `float tilt` / `float azimuth`。协议串按用户要求**未动**，换 jar 时两边一起换。
- **顺手修的真 bug**：`GeneratorServer.applyDisco` 从来**没有被调用过** → 迪斯科页面改任何值都不生效。
  现在 `applyEdit` 依次尝试 generator → disco → spotlight（三张页面共用一条编辑消息）。
- **老存档兼容**：旧射灯没有方块实体，而"没有 BE 就永远不会 tick"（锁存会废）。`LightingServer.lampAt()`
  在扫描时按需 `setBlockEntity` 创建，无需迁移整张地图。

## 8.8 隐藏光源：被控光源物品化（0.9.56/0.9.57）

- `light_source` 本来就有物品、物品模型、名字，也在创造栏里；直接放置时 `LightSourceRenderer` 因为
  `originalState()==null` 什么都不画、`getShape/getCollisionShape` 返回空 → **无模型、无碰撞、可穿行**。
  缺的只是"它不发光"：原版 `lightLevel` 恒为 0，而本模组光照只认 `RegisteredSources` 条目。
- `LightSourceBlock.onPlace` → `RegisteredSources.ensure(anchor, 2)`（**幂等**：已登记的灯具保留原有 mode 与染料颜色），
  `onRemove` 只在"没有 original（就是手放的）"时反注册；接管来的替身保留登记，因为原方块会回来。
- 破坏：有 original → 还原原方块物品（旧行为）；手放的 → **掉回 `light_source` 本体**（在此之前会凭空消失，因为没有掉落表）。
- 亮度：登记工具右键 +1、Shift -1（1..15 循环）。逻辑在 `LightSourceBlock.stepEmission`，
  `TakeoverInteractions` 对"手放的替身"Shift 右键也走同一条路（否则那条分支什么都不做）。
- 染料照旧可上色（它是非射灯条目，`LightingServer.dye` 只排除射灯）。
- `LightingServer` 新增：清掉**指向空气**的非射灯登记（灯被拆掉/推走会留下永久残留，占满 8192 上限的元凶）。
  只查已加载区块、只认空气，所以"灯只是被关掉"不会被误删。

## 8.9 Voxy / LSS：超视距看到炮击地形变化（0.9.58 已实现原型）

**目标**：炮弹把远处地形打出弹坑后，超出原版渲染距离的客户端也能在 LOD 上看到这个变化（弹着即触发，频率可控）。

**两边的实际内容**
- 客户端：`mods/voxy-offline-0.2.15-beta`（Cortex 的 Voxy，Fabric 经 Connector；`ingest_enabled=true`，LOD 存于 `.voxy/<存档或服务器地址>/`）。
  **该构建完全没有联网路径**（416 项、217 个反编译源里没有任何包/通道/握手），只 ingest 原版客户端自己加载的区块（经 Sodium 的 `RenderSectionManager.onChunkAdded` 等）。
  LOD 数据**冻结在 ingest 时刻**：唯一的重新 ingest 反应是 `MixinClientLevel` 注入 `setBlocksDirty`，且只在"新状态是空气（**只认移除**）且位于区块边界 x/y/z∈{0,15}"时触发 → **放置永不重 ingest，普通位置的破坏也不重 ingest**。
- 工作区 `D:\DSH\McMOD\voxy-server-side-neoforge.jar` = **LSS「Voxy Server Side」0.14.0**（modId `lss`，作者 Xantha/VoX，`side=BOTH`，**自带客户端半边**）。**实例里目前没装它。**

**LSS 已有的链路（全部在反编译代码里确认）**
`ChunkSerializer.write` → `ChunkSaveDataHook`(@Inject RETURN) → `ServerReceiverGlue.onChunkSaveData` → `DirtyContentFilter.observeSave`（序列化后 section 的 FNV-1a64 哈希，没变就跳过）→ `DirtyColumnTracker.markDirty` + `RegionStampTable.bumpLiveSaveMark` + 删 LOD store → ≤ `dirtyBroadcastIntervalSeconds`（默认 10s）→ `DirtyColumnsS2CPayload`（包 id `lss:dirty_columns`，只发给 `lodDistanceChunks`＝默认 512 区块内的玩家）→ 客户端 `LodRequestManager.onDirtyColumns` → 重新请求 → `VoxelColumnS2CPayload` → LSS 反射调用 Voxy 的 `rawIngest` → `WorldEngine.markDirty` → 重建网格。

**服务端数据阶梯（决定实现方式）**：时间戳缓存命中（"已最新"，不读）→ **内存中已加载的 `LevelChunk`**（`getChunkNow` + `serializeColumn`，含光照）→ **磁盘 region 文件** → 真正生成区块（`lss_gen` ticket）。
→ 已加载区块里的弹坑**只要 markDirty 就能取到实时数据，不必先存档**；但请求若发生在区块卸载后、存档前，磁盘会返回**旧字节**。所以实现要"**存一下 + 戳一下**"。

**原版保存入口（已用 NeoForge 源码 jar 验证）**：`ChunkAccess#setUnsaved(boolean)`、`ServerChunkCache#blockChanged(BlockPos)`（public）、`ServerChunkCache#save(boolean flush)` → `ChunkMap#saveAllChunks`，**只写 `isUnsaved()` 的区块**；`ChunkMap#save(ChunkAccess)` 是 **private**，所以"只存这一个区块"没有公开入口，只能"标记 unsaved 后整批存"（正常只写我们弄脏的那几个）。

**方案**
- **第一层（零反射）**：弹着后对受影响的已加载区块 `blockChanged(pos)`（或 `setUnsaved(true)`），再限流调一次 `chunkSource.save(flush)`。LSS 挂在 `ChunkSerializer.write` 上的钩子会自己完成 markDirty + 哈希判定 + 推送。
- **第二层（反射）**：`LssBridge` 反射走 `LSSServerNetworking.getRequestService().getDirtyTracker().markDirty(dim,cx,cz)`（照 `MediumCannonTrails` 的"缺席静默 no-op + 版本守卫"写法）。作用是**立刻**作废时间戳/缓存/在途读取并抑制磁盘读取的 `headerFresh` 短路（只调 `invalidateTimestamps` 而不 `markDirty` 会白读一次旧数据）。**绝不要自己调 `drainDirty`**（破坏 `confirmInvalidated` 引用计数）。
- **限流**：自己的去抖（同列 N tick 一次、每次上限、最小受影响方块数）+ LSS 的 10 秒批次 + LSS 带宽闸（`sendQueueLimitPerPlayer=1024`、`mbPerSecondLimitPerPlayer=-1`=AUTO 25MB/s、probe 512/tick/player 与 2048 global）。**必须限流**，因为客户端 Voxy 的 ingest 队列**无上限也无背压**，且内存上限是硬编码的（WorldEngine section cache 1024、geometry 0x100000 等）。
- **触发点**：复用 `ExplosionLights` 已订阅的 `ExplosionEvent.Detonate`（`getAffectedBlocks()` 直接给列集合）；穿甲弹的方块破坏要另找服务端路径（CBC 的 `BlockHitEffect` 是纯客户端）。

**客户端兼容性：离线已验证 ✅**
LSS 的 `VoxyCompat.init()` 只反射两个 Voxy 入口：`WorldIdentifier.of(Level)`（static，返回 `WorldIdentifier`）和
`VoxelIngestService.rawIngest(WorldIdentifier, LevelChunkSection, int,int,int, DataLayer, DataLayer)`（static boolean）。
逐个对掉 `voxy-offline-0.2.15` 的反编译源：**两个签名完全一致**（该构建还多一个 `rawIngest(WorldEngine, …)` 重载，以及 `ofEngine/ofEngineNullable`）。
所以 LSS 描述里"客户端半边需要社区 Voxy 构建"这一条，对 API 层面来说已经满足——仍需游戏内确认握手与 ingest 不报错。

**Plan B（不装 LSS）**：自己写传输 + 客户端半边调用同样的**公开** API（`WorldUpdater.insertUpdate(WorldEngine, VoxelizedSection)` / `VoxelIngestService.rawIngest(...)`，数据变了调 `WorldEngine.markDirty(section,1,0x3F)`；`WorldEngine` 的 dirty 回调已接到渲染节点管理器，所以 **remesh 是免费的**）。
注意：该构建**没有** `WorldEngine.insertUpdate/sectionUpdate/getSectionData`；`VoxelizedSection` 是 `long[4681]` 且 **mip 链必须自己填**；区域要在 `section_render_distance`（当前 11.25×512 ≈ 5760 格）内才会立刻成网格，否则先存起来等玩家靠近。工作量比装 LSS 大一个数量级，故列备选。

**已实现（0.9.58，快速原型）**
- 新包 `dev.cbcmuzzlelight.voxy`：
  - `LssBridge`：反射解析 `LSSServerNetworking#getRequestService()` → `RequestProcessingService#getDirtyTracker()` → `DirtyColumnTracker#markDirty(String,int,int)`，解析一次并缓存 Method；`getRequestService()` 每次现读（服务器重启会重建）。缺 mod / 内部改名 / 服务器未启动 → 一律静默 no-op，绝不抛异常。**没有**调 `drainDirty`。解析成功会打一条 `voxy: LSS bridge resolved`，失败打 warn 说明原因。
  - `VoxyLodNudger`：`queue()`（爆炸时登记列，**不**立刻动手）+ `drain()`（在既有的 5 tick 扫描里做标记与写盘）。
    四重限流：同列 `debounceTicks` 内不重复标记、每次事件 `maxColumnsPerEvent` 上限、5 tick 批量、以及 LSS 自己的 10 秒广播批次。
    写盘：`getChunkNow(cx,cz)` 拿到已加载区块 → `setUnsaved(true)`；`saveIntervalTicks` 允许时 `chunkSource.save(true)`（`ChunkMap#saveAllChunks` 只写 unsaved 的区块，所以实际只写弹坑那块）。未加载的列只交给 LSS，不写盘。
- 触发：`ExplosionLights.detonate`（`ExplosionEvent.Detonate`）里在**光照逻辑之前**调用 `queue()`，且用 `blocks >= voxy.minimumBlocks` 单独判定 —— 这样即使把爆炸光照关掉，LOD 刷新仍然工作。
- 配置（服务端 TOML `[voxy]`）：`enabled`(true)、`saveChunks`(true)、`maxColumnsPerEvent`(24)、`debounceTicks`(40)、`minimumBlocks`(4)、`saveIntervalTicks`(40)。全部在没装 LSS 时无副作用。
- 调试命令（权限 2）：
  - `/cbcmuzzlelight voxy status` —— 开关、LSS 是否安装/是否解析成功、待处理列数、累计标记数。
  - `/cbcmuzzlelight voxy nudge <center> <radius>` —— 手动把一片列交给 LSS（等同"一发炮弹落在这里"），**整条链路可以不开炮就验证**。
- 已知未覆盖：**穿甲/实心弹的方块破坏**走的是 CBC 自己的服务端路径（不是爆炸事件），目前不会触发刷新；要覆盖需要 mixin 进 CBC 的穿透/破坏点，留待下一轮。

**各类"能看见"的半径（实测自代码，问"要不要给远处玩家刷新"时看这张表）**
| 效果 | 半径 | 出处 |
|---|---|---|
| CBC 大炮高爆弹效果包 | **512.8 格**（`distSqr<263000`） | `ShellExplosion.sendExplosionToClient` |
| CBC 机炮榴霰/破片 | **500 格**（250000） | `FlakExplosion` / `ShrapnelExplosion` |
| CBC 穿甲命中爆炸 / 迫击炮石弹 | 100 格（10000） | `ImpactExplosion` / `MortarStoneExplosion` |
| CBC 流体弹 | 128 格（16400） | `FluidExplosion` |
| 本模组爆炸闪光（只有光） | 512 格（硬编码） | `ServerFlashes.emitSized` |
| 本模组炮口粒子 | 160 格（`tankCannon.cullDistance`） | `MediumCannonEffects` |
| LSS 脏列推送 | `lodDistanceChunks`=**512 区块＝8192 格** | `ServerConfigBase` |

**见证者门槛（0.9.59）**：`voxy.witnessRange`（默认 **512** 格，0＝不限制）。drain 时逐列检查"是否至少有一名玩家在这个半径内"，没有就跳过该列（日志里计入 `unseen=`）。
理由：CBC 的效果包只发到 500～512 格，更远的玩家根本看不到爆炸，若还给他刷新 LOD，就会凭空多出一个坑。
**已知局限**：这是**全局**门槛（"没人看见就不刷"），**不能**阻止"近处有人、远处玩家照样收到推送"的情况——因为 `markDirty` 是全局的，LSS 用它自己的 `lodDistanceChunks`（512 **区块**）过滤收件人，而这个值同时是 Voxy 的 LOD 服务半径，不能为了这个调小。
若要做到真正的按玩家推送，只能绕开 `markDirty`：自己调 `getRegionStamps().bumpLiveSaveMark` + `getOffThreadProcessor().invalidateTimestamps`，再自己把 LSS 的 `DirtyColumnsS2CPayload` 只发给半径内的玩家——约 60 行，但会绕过 LSS 自己的线格式/dialect（v16/18/19/20）处理，存在客户端解码失败的风险，需要实测。**尚未实现，按需再做。**

**两层优先（0.9.60，取代 0.9.59 的"排除"写法）**
- 上一版我理解错了需求：做成了"没人看见就不刷"（`witnessRange` 跳过列）。**已改回不排除任何人**。
- 现在是 `voxy.immediateRange`（默认 **512** 格，0＝关闭即时层）：
  - **所有人**照样 `markDirty` → LSS 按自己的 `dirtyBroadcastIntervalSeconds`(默认 10s) 广播（这是"后到的"那一层，谁都不会漏）。
  - **半径内的玩家**额外走即时层：我们的 5 tick 扫描里立刻 `invalidateTimestamps`（否则客户端重问会被时间戳缓存回答"已最新"，拿了旧数据）+ 直接把 LSS 的 `DirtyColumnsS2CPayload` 发给这些玩家 → 他们在 ~0.25 s 内收到并重问，趁烟还没散就更新完 LOD。
- **安全门（必须）**：即时推送只发给 **LSS 已完成握手的玩家**（`getPlayers()` 的 map 里存在且 `hasCompletedHandshake()`）。原因不是礼貌：没装 LSS 的客户端没有这个包的处理器，而 NeoForge 收到未注册 payload 会**断开连接** → 无脑群发会把原版玩家踢下线。
- 涉及的 LSS public 内部（全部反射、逐个核对过）：`getOffThreadProcessor()`、`OffThreadProcessor#invalidateTimestamps(String,long[])`、`getPlayers()`、`AbstractPlayerRequestState#hasCompletedHandshake()`、`DirtyColumnsS2CPayload(long[])`（最简单的 `long[]` 包，**没有** v16/18/19/20 dialect 复杂度）+ `PacketDistributor.sendToPlayer`。任一解析失败 → 立即层自动降级，标记与常规广播照常工作（只打一条 warn）。
- 日志：`voxy: marked N column(s), wrote M chunk(s), saved=…, told X player(s) now, lss=bridge (total marks …, pushes …)`。

**0.9.61：单机下 LSS 根本不启动服务 —— 这是第一次实测"看不到 LOD 更新"的根因**
```java
// LSSServerNetworking.onServerStarted
if (!server.isDedicatedServer() && !Boolean.getBoolean("lss.test.integratedServer")) {
    LSSLogger.info(Brand.shortName() + " LOD request processing deferred until LAN");
    return;                       // ← 集成服务器（单人存档）直接不创建 requestService
}
requestService = new RequestProcessingService(server);
```
- 后果：单机下 `getRequestService()` 恒为 **null** → 我们的 `markDirty` 每次静默失败（日志 `total marks 0`），
  **并且 LSS 自己的区块保存钩子也拿到 null service** → 我原先"写盘能兜住"的假设在单机下不成立。
- **要跑通必须**：① **开放到 LAN**（LSS 的 `IntegratedServerLanHook` 会启动服务并触发主机握手），
  或 ② 用**专用服务器**，或 ③ 加 JVM 参数 `-Dlss.test.integratedServer=true`。
- 0.9.61 的改动（避免下次还要靠翻日志猜）：
  - `markDirty` 现在区分**四种结果**：正常接受 / `service==null`（单机未开 LAN）/ 反射未解析 / 调用抛异常；
    首次遇到 `service==null` 会打一条**明确警告**说明 LSS 的设计原因和三种解决办法。
  - 日志行不再撒谎：`tried N column(s) … LSS accepted=…, serviceNull=…, unresolved=…, errors=…, clients=X (handshaked Y)`。
  - `/cbcmuzzlelight voxy status` 现在显示**服务是否在运行**、LSS 客户端数与已完成握手数。
  - **选列改为"离爆心最近的 N 列"**：一次 28090 方块的爆炸覆盖的列远超 `maxColumnsPerEvent`，
    旧代码按受影响列表的任意顺序截取前 N 个 → 可能只刷新弹坑的一侧，中间还是旧地形。现在按到爆心的距离排序取最近的。

**验证阶梯**
- **T0**（零代码）：装 LSS（两边同一个 jar）。客户端日志应出现 `Server session config received (protocol v20, LOD distance: N chunks, enabled: true)`，以及 Voxy 侧的 `Voxy detected — registered raw ingest bridge`；`/lss stats`、`/lss diag` 可用。
- **T1**（零代码）：`lodStore=on` + `/lss backfill start` → 远处出现 LOD（证明"超视距"本身成立，与炮击无关）。
- **T2**（零代码，一条命令）：远处 `/fill` 挖洞 → `/save-all flush` → ~10 秒内远端 LOD 更新（证明整条脏传播链路通）。
- **T3**：接上上面两层钩子，弹着即生效，第二个客户端看到弹坑。

**报告原文**：`D:\DSH\McMOD\work\voxy-analysis\LSS-REPORT.md`（648 行）、`VOXY-REPORT.md`（756 行）、`lss-src/`、`dec-all/`（217 个反编译源）、`methods.txt`。

## 8.10 光源与阴影开销分析（0.9.65）

**热路径与量级（全部读代码得出，非估算的部分已标注）**

| 档位 | 每像素/每 tick 的成本 | 量级 |
|---|---|---|
| **Advanced（光影包+补丁）** | 补丁在 `PROGRAM_DEFERRED4` 里循环 `CBC_MUZZLE_LIGHTS=16` 盏灯；对最近 `cbcMuzzleShadowCount` 盏各做一次屏幕空间射线，`CBC_MUZZLE_SHADOW_STEPS=12` 次 depth `texelFetch`（对数分布+抖动+提前 break） | **主成本 = 像素 × 阴影灯数 × 12 次采样**。1080p 默认 1 盏 ≈ 2500 万次/帧；4 盏 ≈ 1 亿/帧。每帧 uniform ≈ 70 次调用，可忽略 |
| **我们的 GPU 叠加（无包时 DynamicOnly）** | `cbc_gpu_light.fsh` 每 fragment 循环灯数；每盏灯一个覆盖其**投影包围盒**的屏幕四边形，加法混合 | 原设计 = Σ(每盏灯屏幕面积) × 灯数（**随灯数平方增长**）；`quadForBounds` 曾在一角越过近平面时**退化成全屏四边形** → 站在大光源里时每盏灯都是一次全屏 pass。另有每帧一次**全屏深度拷贝** |
| **Compatible（CPU/Lamb）** | 每 tick：对每盏灯枚举包围盒内所有 section（64 格光束≈400+）→ 差集 → 脏 section 逐个 `onLightUpdate` + `muzzle$dirty`（原版光照引擎 + 区块网格重建）；网格化时每方块走 `Behavior.lightAtPos` | **CPU 第一大项**，且被 `AreaSnapshot.same()` 的精确比较放大（见下） |
| **服务端** | 每 5 tick 遍历全部登记光源（射灯还要查 BE + 6 次 `getSignal` + 4 次区块检查），再对每个玩家做整表相等比较 | 随 灯具数 × 玩家数 增长；原来**没有按距离过滤**，任何变化都整表群发 |

**0.9.65 做的优化（本轮 = 1/2/5）**
1. **阴影自适应**（`client/ShadowBudget`）：`shadowLightCount` 现在被当作**玩家设定的上限**，实际用量按客户端自己的 FPS 花：
   <30fps → 0 盏，<45 → 1，<60 → 2，否则用满配置。**只会降低不会提高**（设 0 仍是 0），采样用客户端每秒更新一次的 FPS，本身零开销。
   开关：客户端 `shadowLightCountAuto`（默认 true）。
   （补丁里 `CBC_MUZZLE_SHADOW_STEPS` 是**可在光影包内直接改**的常量，注释也写着 editable here in the pack：12→8 在对数分布下观感几乎不变，不需要重抄补丁。）
2. **叠加通道消掉全屏退化与 N²**：
   - `screenRect()` 用**12 条边对近平面裁剪**取代"有角在近平面后 → 全屏"；完全在相机后方的灯直接丢弃。
   - **屏幕分块灯表**（16×16 tile）：CPU 每帧把每盏灯覆盖的 tile 用 `1<<i` 打位（≤32 盏→每 tile 一个 `uint`），上传 `R32UI` 纹理；fragment 只循环本 tile 内的灯。
     开关：客户端 `tiledLightListing`（默认 true，驱动不喜欢整数纹理时可关）。
   - 修的坑：tile 的位必须是**灯在光照数组里的下标**，不是"第几个被画出来的四边形"——否则被裁剪掉的灯会让后面所有位错位。
3. **服务端按距离下发**：`sources.sendRange`（默认 1024 格，0=不限）——只把玩家范围内的光源放进给他的 `SourcePayload`；
   **mode 2（强制启用）永远下发**（客户端被告知这类任何距离都要留），anchor 解析不出来的也保留（宁可多发不可漏发）。
   客户端本来就会按自己的 activation 距离丢弃，所以之前那部分是纯浪费的带宽与客户端开销。

**兼容模式（Compatible / Lamb）的性能风险 —— 定位：非优化目标，仅记录**
- `AreaSnapshot.same()` 是**精确 double 比较**：船体轻微起伏、玩家走动（将来的手电筒）都会让灯**每 tick 判为已变** →
  `Behavior.hasChanged()` 每 tick 为真 → Lamb 对整个包围盒 relight。一束 64 格的光 ≈ **405 个 section 每 tick 重建**。
- `publish()` 每 tick 对每盏灯枚举其包围盒覆盖的 section（`sections(bounds)` inflate 1），再对**每个脏 section**
  调 `level.getChunkSource().onLightUpdate(...)` 与 `levelRenderer.muzzle$dirty(...)`。
- 网格化热路径：`AdvancedLights.atSection` → 遍历该 section 的 area → `AreaSnapshot.at`（包围盒早退 + ~20 flops + 一次 sqrt），
  每重建一个 section 要跑 4096 次方块的量级。
- **结论**：Compatible 档在"大量灯具 + 大光束 + 移动平台"下会明显掉帧，随灯具数量线性恶化。
  它**不是**本模组的目标档位（目标是 DynamicOnly/Advanced，走 GPU），因此**暂不优化**。
  若将来要用它，先做两件事：把 `AreaSnapshot.same()` **量化**（位置 1/8 格、亮度 1/64、方向 ~1°），
  并只在量化后真的变化时才 `onLightUpdate`/`muzzle$dirty`（即原分析里的第 3 项）。

**仍未做的优化点（备选，按收益）**
- **光束包围盒收紧**：`AreaLight.beamExtent` 给出的是保守盒（512 距离 → 巨大），叠加通道里几乎必然接近全屏；
  改为投影锥体顶点 + 环取多边形可显著减少覆盖面积。（叠加通道与 Compatible 档都受益，见原分析第 4 项。）
- `GpuLightRenderer.setLights` 每 tick `new float[640]` → 双缓冲复用；Sable 快照未变时不 `new Matrix4d`。
- 叠加通道的 tile 表可再按"灯是否真的覆盖 tile 中心"做保守收紧（现在是包围盒即 tile 矩形）。

## 8.11 优化候选清单（等用户挑选，含风险/收益/是否需要重抄补丁）

**先排除一个假的"大项"**：补丁里的阴影射线**已经是门控的**——`cbc_muzzle_lighting` 里
`if (falloff <= 0.0) continue;` 出现在 `cbc_muzzle_visibility(...)` **之前**，所以只有灯真正照到的像素才做射线；
注册灯具（`tints[i].w > 0.5`）从不投影。所以"阴影每像素白跑"不成立，不需要改动。
（补丁还已有：`light.w<=0` 跳过空槽、自身剔除、`along<=0||along>radius`、`falloff<=0` 早退。）

| # | 项目 | 改什么 | 收益 | 风险/代价 | 重抄补丁 |
|---|---|---|---|---|---|
| A | **光束包围盒 → 投影锥体多边形** | `AreaSnapshot.bounds` 是保守 AABB，512 距离光束 → 基本恒为全屏四边形，tile 表里它的位点亮**每个** tile＝每像素都求值这盏灯。改为投影锥体（顶点+远cap 一圈点）成屏幕多边形 | **叠加通道最大剩余项**（长光束从全屏降到实际覆盖，常见场景数倍） | 中：相机在锥内需回退；顶点在近平面后要处理 | 否 |
| B | **叠加通道先算光照再算法线项** | `cbc_gpu_light.fsh` 现在对每盏灯无条件算 `length(toLight)`+归一化+`dot(normal,…)`（~10 flops+一次 sqrt），即使 `areaLight()` 返回 0。改为先 `areaLight` >0 才算法线项 | 每 fragment 每盏无效灯省一次 sqrt+10 flops；灯多时中 | 极低（纯重排） | 否 |
| C | **无可画四边形时跳过全屏深度拷贝** | `render()` 现在先 `copyDepthFrom`（1080p≈8MB 带宽/帧）再判断；改为 `rects.isEmpty()` 直接 return | 小~中 | 极低 | 否 |
| D | **半分辨率光照通道 + 加法合成** | 叠加通道渲到半分辨率 target，再合成回主目标（深度按 UV 双线性采样全分辨率） | 叠加通道 fragment 数 **1/4** | 中高：多一个 target+合成步；边缘略糊（光低频，多半看不出） | 否 |
| E | **阴影名额按屏幕面积而非距离** | 现在是"最近 N 盏"（`i < cbcMuzzleShadowCount`）；若最近那盏是远处小火花就白占名额。改为按投影面积×亮度排序取前 N | 中（同样预算用得更值） | 低（只影响谁投影，改我们发布顺序即可） | 否 |
| F | **补丁光照循环按活跃数上界** | 补丁固定循环 16 盏，靠 `light.w<=0` 提前 continue；加 `cbcMuzzleLightCount` uniform 后循环只跑到活跃数 | 小（省的是 load+比较+continue） | 低 | **是** |
| G | **阴影步数 12 → 8** | 补丁里的 `#define CBC_MUZZLE_SHADOW_STEPS`（注释写明可在包内直接改） | 阴影成本 **≈ -33%** | 低（对数分布观感几乎不变） | 不用（改包内常量） || H | **阴影步数随射线长度自适应** | 远处灯射线更长却仍固定 12 步；按 `march_distance` 缩到 6~12 | 中 | 低~中（插值实现，观感待确认） | **是** |
| I | **按屏幕面积丢弃极小的灯** | 投影 < ~2px 的灯直接不画 | 小 | 极低 | 否 |
| J | **`setLights` 双缓冲 / 免重复矩阵** | 每 tick `new float[640]`×1；Sable 快照未变故免 `new Matrix4d` | 极小（GC churn） | 极低 | 否 |
| K | **地面光源位置缓存** | `ExtraLights.append` 每 tick 对每个 source 调 `anchor.resolve(level)`；地面灯位置不变，可按 payload 版本缓存 | 小~中（灯多时） | 低（只缓存 `ship()==null` 的） | 否 |
| L | **未变光源的快照缓存** | 每 tick 为每个 source 重建 `AreaLight`/`AABB`/`AreaSnapshot`，可按 (id, payload 版本, 位置) 缓存 | 小~中（CPU/GC） | 中（失效逻辑要写对） | 否 |
| M | **`SourcePayload` 增量下发** | 现在任何变化整表发给每个玩家（已按距离过滤）；改增量/版本号 | 带宽随灯数线性改善 | 中（协议改动，两端同换） | 否 |
| N | **服务端微优化** | `neighboursLoaded` 里每灯每 tick `new int[]{-2,2}`；无光源时整段扫描可跳过 | 极小 | 极低 | 否 |
| O | **补丁 `MAX_LIGHTS` 16→32** | 光影包路径能点亮更多灯 | 功能项（灯多时更完整），非性能项 | 中（要重抄补丁，每帧 uniform 翻倍，成本随灯数上升） | **是** |
| P | **Compatible 档量化更新** | 位置 1/8、亮度 1/64、方向 ~1° 后比较；脏 section 也按量化判定 | CPU 档可能数倍 | **已后置**（非目标档位，见 §8.10） | 否 |

**已完成（0.9.66 = A/B/C）**
- **A 光束按锥体投影**：新增 `beamRect()` —— 投影**顶点 + 0.35/0.7/1.0 三个环（每环 8 点）**，
  取 NDC 极值作屏幕矩形；任何采样点在近平面之后就返回 null，回退到原来的 box 路径（相机被光束包住时那是**正确**的保守结果）。
  **关键是 `AreaSnapshot.bounds` 一个字没改**：它仍是光的影响范围，CPU 档的 `at()` 早退与 Lamb 的 relight 区域靠它。
  效果：512 距离光束的覆盖从"必然全屏"降到实际锥体投影，tile 表里它的位也不再点亮每个 tile。
- **B 叠加通道先算光照再算法线项**：`areaLight()` 返回 0 就直接 continue，省掉每盏无效灯的
  `length()`+归一化+`dot`（约 10 flops + 一次 sqrt）。纯重排，观感不变。
- **C 无可画四边形时跳过全屏深度拷贝**：`rects.isEmpty()` 时不再 `copyDepthFrom`（1080p ≈ 8MB/帧）。
  注意实现细节：仍然 `buildOrThrow()` 后 `mesh.close()`，否则 Tesselator 的池化缓冲不会归还。
- 校验：`cbc_gpu_light.fsh` 通过 glslang（exit 0）。

**已完成（G，用户侧）**
- **G 阴影步数 12 → 8**：用户已在自己的光影包里把 `CBC_MUZZLE_SHADOW_STEPS` 改成 8（阴影成本约 -33%）。
  为了让这个选择**不会因为重抄补丁而被改回 12**，仓库里两处的默认值已同步改为 8：
  `patch/source/cbc_muzzle_light.glsl`、`patch/photon/shaders/include/lighting/diffuse_lighting.glsl`（以及 `patch/README.zh-CN.md` 的说明）。
  → G 从候选清单移出，归入"已完成"。

**推荐**：只做三项就 **A → B → C**（全在叠加通道，零补丁重抄、风险低，A 是最大项）；
要压阴影则 **G**（你自己改一行）→ **E**（我们改排序）→ **H/F**（需重抄补丁）。
**D** 收益最大但实现最重，建议等 A/B/C 落地复测帧率后再定。

## 8.12 为什么"射灯比静态灯便宜"：寄存器灯具的取替（takeover）热路径（0.9.72）

**结论：这不是错觉，但原因不在灯的类型，而在两种灯在客户端各自走的代码分支。**（用户在实机中提出"射灯性能比静态灯好"。）

**日志证据（用户实例 `logs/2026-09-28-3.log` 等，tier Advanced）**
```
04:22:53.150  source vanilla light taken over (4 block(s), tier Advanced, first=10, 67, 50)
04:22:53.401  ... first=10, 67, 49
04:22:53.451  ... first=9, 67, 49
04:22:53.665  ... first=8, 67, 48
```
`04:43:27` 一秒内 18 行，坐标只在 `6,65,49` / `9,82,-9` / `9,80,-9` / `9,66,-10` 之间跳。旧代码只在"集合与上一 tick 不同"时才打这行，所以**每一行＝那一 tick 真的做了整轮取替工作**。

**旧实现（≤0.9.71）的问题**
1. `ExtraLights.forEachSourceBlock` 用 `a.world(level,pos,centerOf(pos))` 再 `BlockPos.containing`：**船体挂载灯的中心被船的姿态矩阵变换**，船一俯仰/转向，中心就在方块边界上来回穿越 → 该方块"取替→释放→取替"每 tick 翻转。
2. `ClientFlashes.takeOverSourceLight` 只要集合有**任何**变化就 `dirty = 旧集合 ∪ 新集合`，对**每个**位置 `updateSectionStatus(false)` + `checkBlock` + `setSectionDirtyWithNeighbors`（27 个 section 重建网格）。一盏灯抖一下 = 整个世界的灯具全部重算重网格化。
3. 结果：静止舰船上的几盏灯就能让每 tick 几十个 section 重建；射灯通常在**固定方块**上（走得稳），自然显得"更便宜"。

**0.9.72 的修法（三件事 + 一处档位收口）**
- `forEachSourceBlock` 改用 **`anchor().resolve(level)` 的解算方块**，不再用子方块中心；船的灯只在船**真的移动一整格**时才跳变。
- `takeOverSourceLight` 改成**对称差**（`symmetricDifference`），只处理这一 tick **真正进出**的方块：静止时零开销，移动时每盏灯 2 个方块。
- 日志改为**每秒最多一行**，并打印 `+新增 -移除` 计数与首个变化的方块（`+x,y,z -x,y,z`），便于实机判断"是不是还在抖"。
- **档位收口**：取替原来对 `effective != None` 的所有档位生效，但只有 GPU 叠加通道（`DynamicOnly`/`Advanced`）才需要它
  —— 那两个档位**只能加光**，不抹掉灯自己的白光就看不出颜色；`Legacy`/`Compatible` 是自己往光照引擎里写光，取替是纯开销。
  现在由 `ClientFlashes.gpuTier()` 把关，`Legacy`/`Compatible` **完全不被碰**；从 GPU 档切走时会走一次空集的对称差，把已取替的方块**原样释放**。
  配置键名未变（`takeOverSourceLighting`），只有注释改写（新键注释不会覆盖已有 TOML）。

**明确与阴影无关（用户问"这会影响影子的跟踪吗"）**
- 谁投影只由补丁这一行决定：`if (i < cbcMuzzleShadowCount && tints[i].w <= 0.5)`；
  `tints[i].w` 是 `IrisMuzzleBridge.update` 写的 **`selfCull`**（`ExtraConfig.GPU_SELF_CULL`），
  寄存器灯具与随身光为 1（**从不投影**），炮口闪光/曳光弹为 0（**才投影**）。
- `takeOverSourceLight` 只把某个方块的**原版方块光发射值**改成 0（`BlockLightMixin.getEmission`）。
  它不改**灯的列表、顺序、位置、形状、亮度、`selfCull`、`cbcMuzzleShadowCount`、`ShadowBudget`** 中的任何一项。
- 灯列表顺序仍由 `AdvancedLights.orderForGpu` 决定（dynamic 在最前 → 阴影名额仍归炮口闪光），**本次没有改它**。
- 船载灯具的差异只有一处：取替的方块从"视觉中心所在方块"变成"解算出的灯具方块"。
  前者会随船体姿态在方块间抖动，后者才是**灯真正在的那个方块**（原版光就是从它发出的）。
  而且该方块本身**永不投影**，所以对影子追踪零影响。
- 因此**阴影质量没有任何牺牲**：`CBC_MUZZLE_SHADOW_STEPS` 仍是 12（用户包里自己设的 8/12 也不受影响）。

**顺带确认的其它量级（回答"为什么射灯便宜"的第二半；本轮的改动范围＝只看 GPU 档）**
- **叠加通道（`DynamicOnly`，无包）**：成本 ≈ Σ(灯的屏幕覆盖像素) × 每像素灯数。寄存器灯是 `sphere()`，包围盒在玩家旁边时**投影接近全屏**；射灯是 `AreaLight.beam()`，走 `beamRect()` 的锥体轮廓投影，通常只占屏幕一条带。所以叠加通道里"贴脸的静态灯"单价远高于"指向别处的光束"。
- **包内通道（`Advanced`，有包，用户的目标档）**：16 盏灯循环里，只有 `tints[i].w <= 0.5` 的前 `cbcMuzzleShadowCount` 盏做阴影射线，且 `falloff <= 0` 早退；寄存器灯 `tints[i].w > 0.5` **从不投影**。所以包内通道里两者差异很小 —— 用户感到的差距主要来自上面的取替 churn，而不是渲染本身。
- **Compatible（Lamb）档只是参照，不在改动范围**：两者的 `AreaSnapshot.bounds` 都会被 `publish()` 枚举 section，静态灯半径 15 格 ≈ 3³ section，射灯 512 格光束则是几千个 —— 那一档里射灯反而最贵，与用户观感相反；这也**反证**了用户跑的是 Advanced/DynamicOnly，病灶就是取替 churn。Compatible 档本轮**一行都没改**。

**待实机验证（0.9.72）**：站在一艘有若干盏寄存器灯的移动船边上，观察日志里那行是否退化成"每秒一行、`+0 -0`"（静止）或每次只有 1~2 个方块变化（移动）；同一场景对比射灯，帧率差距应消失。

## 9. 待办队列

> 尾迹已完成并验收（见 §0A）。本节只列**现在要做的**；尾迹那十几次反复归档在 §0A 的"踩过的坑"里。

**待用户实测（按优先级）**
1. **手电筒（0.10.0）—— 本轮全部改动都在这里**：
   - 光锥从**腰部**发出（站立约 0.89 格），不再是眼睛；
   - **右键**开关（动作栏提示"手电筒：开 / 关"），**Shift+右键**打开配置页面；
   - **默认投阴影**：站到墙边，光锥在地面/墙上的阴影应随你移动变化；把 `gpu.flashlightShadow` 关掉可做 A/B 对比；
   - 页面里**宽度现在写的是度**（默认 22°）、**衰减速度是百分比**（默认 100）；调到 300 光束应明显变短变暗，调到 20 则几乎整条都亮；
   - **他人手电筒**需要第二个玩家（单机可"开放到 LAN"再开一个客户端）：48 格内对方的灯应当可见；
     `flashlight.syncRange=0` 应让它们全部消失；`gpu.flashlightPeerShadow=true` 之后对方的光束才开始投影；
   - 若全黑：先确认 shader 真的重载过 —— **F3+R 不重载 shader**（光影列表里重选该包，或重启）。
   - 配置若仍是旧值（宽度显示 5 而不是 22），说明迁移没生效，见 §0A 的"配置迁移"。
2. **阴影重做（0.11.0）—— 本轮重点**：
   - **验证"屋外的灯不再照进屋里"**（这是这一轮存在的理由）：白天空手右键一盏**室外**灯看页面能开；
     然后进屋（门关上），室内墙面**不应该**再被那盏室外灯照亮。
   - **验证软阴影**：`shadowSoftness` 0 → 0.5 对比，边缘应从硬变柔和；**光束必须仍然是硬边**
     （拿射灯或手电筒扫地面看轮廓有没有糊掉）。
   - **步数与帧率**：`shadowSteps` 16 / 8 / 4 三档对比；4 配上 `shadowSoftness>0` 应当只是**噪点**而不是**波纹**。
     若没开 TAA 会看到噪点，这是预期的。
   - **分类开关**：`shadowSources`/`shadowSpotlights`/`shadowMuzzle` 各自关掉一次，确认对应类别的阴影消失而其他的不受影响。
   - **`shadowMuzzleSoft`** 开关一次，炮口闪光的阴影边缘应从软变硬。
   - **每盏灯独立**：放两盏灯，一盏页面设「硬边」、一盏设「软边」，**两盏的边缘应当不同**；
     再给其中一盏 `shadowSteps` 设 4、另一盏 16，采样预算应当不同。
   - **客户端的否决权**：把 `shadowSoftness` 设 0 → **所有**灯变硬边（哪怕页面写着软边）；
     把 `shadowSteps` 设 4 → 页面写着 16 的灯也只能用 4。
   - `advanced.performance.debugLogging=true` 时日志里有 `... casting a shadow, N of them soft`。
   - **接管一盏灯**（登记工具 Shift+右键）看它投影是否正常 —— `CLEARANCE 0.6` 就是为这条改的，
     若灯自己变黑说明射线又被它自己的方块挡住了。
3. **静态光源页面（0.11.0）**：
   - **空手右键**开页面；`mode`/`color`/`emission`/`radius`/`shadow`/`shadowSteps` 六行都能改并立即生效。
   - `radius = 0` 应与旧行为**完全一致**（按亮度推导）；设成 20 后光照范围应明显变大且与亮度解耦。
   - **升级存档要验**：老世界里已有的灯在升级后**阴影应当仍是软的**（load 缺字段时默认 2 而不是 0）。
   - 确认**旧的两条交互真的没了**：空手右键不再调亮度、染料右键不再改颜色。
   - 登记工具右键（循环模式）与 Shift+右键（接管/还原）**仍然可用**。
   - 重登后半径/mode/颜色应当保留（`SavedData`），接管状态应当保留。
4. **冲击波**：同一发弹打空气 vs 打山体 —— 档位（当量）应相同、**形状**应完全不同；
   `explosion.expansion` 0.5 vs 1.0 的手感；`minimumDensity` 调 0 看连爆是否频闪。
5. **取替 churn（0.9.72）**：站在有若干寄存器灯的**移动**船旁看日志是否退化成"每秒一行、`+0 -0`"。
6. **射灯三组语义（0.9.71）**：朝向（仰角/方位角）/ 光锥（张角）/ 红石扫描（基准角+幅度）；
   锁存对按钮短脉冲是否每次都响应。
7. **Voxy / LSS 即时层**（0.9.60/0.9.61）：`told X player(s) now`、`LSS accepted=…`。
   单机必须**开放到 LAN**（否则 LSS 不自启，日志会明确说明）。
8. **0.9.64–0.9.66 性能项**：长光束照向别处、站在自己炮口烟里、开关 `tiledLighting` 对比帧率。

**待做（尚未实现）**
- **光源优化候选**（§8.11）：**D**（叠加通道半分辨率+合成，收益最大但最重）、**E**（阴影名额按屏幕面积）、
  F/H（补丁循环上界 / 阴影步数自适应，**需重抄补丁**）、I/J/K/L/M/N（小项）、O（补丁 `MAX_LIGHTS` 16→32）。
- **陈旧配置键清理**：客户端 `[gpu.trail]` 的 `spacing`/`spawnDistance`/`ticks`/`fadeTicks`/`maxPerTick`/
  `interval`/`debugReceivedData`，服务端 `[trail].frames` 与 `[flashlight].falloff`（0.10.0 起被 `decay` 取代）
  —— 已无代码引用，删除会让旧 TOML 出现未知键，需用户同意。
- **HE 破片粒子**（用户暂缓）。

**已通过（用户实测确认，不要再动）**
- **曳光弹尾迹**（0.9.98）—— 本轮主线，验收通过。
- Voxy/LSS 超视距弹坑刷新（0.9.58）；单机需开放到 LAN。
- 扬尘（不再膨胀、水平尘图层已删）。
- 迪斯科球页面、后坐 `angular`/`speed`、尾迹密度与机炮排除、AP 火花与 Photon 补丁。
## 10. 工具/编辑踩坑

- PowerShell 里多行 here-string `.Replace()` 常因 CRLF 失败 → 用**行数组手术**，或直接整文件 `write`。
- **按内容删行极其危险**（曾两次删掉共享声明行）→ 优先整文件重写。
- 编辑坏了的恢复来源：`build/libs/sable-light-overhaul-<version>-sources.jar`。
- **PowerShell 函数里不要把参数命名成 `$args`** —— 那是函数的自动变量，`@args` splat 出去会是**空的**，
  于是你精心构造的参数**一个都没传下去**，而命令看起来"成功"，你得出一条完全错误的结论。
  （排查代理时就这么被骗了两轮。）同理：验证外部命令行为时要**打印它实际用到的目的地/参数**，别只看退出码。
- **git 的 `http.<url>.proxy` 优先级高于 `http.proxy`** —— 想临时清掉代理，要覆盖 `http.<url>.proxy` 本身
  （`-c http.https://github.com.proxy=`），覆盖 `http.proxy` 是没用的。
- **重命名仓库顶层目录会被 Windows 拒绝**（`Move-Item` 报 Access denied，`cmd move` 也一样），
  但目录**内部**读写与**子目录改名**都正常 —— 说明是**有句柄挂在这个顶层目录上**
  （这台机器上最可能是 Windows Search 索引器 `SearchIndexer`，也可能是有 Explorer 窗口开在那个路径）。
  绕法：`robocopy <旧> <新> /E /MOVE /COPY:DAT /DCOPY:DAT /R:2 /W:1`（**退出码 1 是成功**，含义是"有文件被复制"）。
  移完**必须核对文件数**（本次 2201 → 2201）并 `git -C <新路径> status` —— `.git` 是跟着一起搬的。
- 单位/物理护栏：`velocity` 是**每物理步** Δv（≤5.0），`physicsSteps` 是倍数；
  永远不要把位置当冲量传（`applyImpulseAtPoint` 的参数顺序曾经搞反，Δv=1e-15 就把载具打飞了）；
  施加前后都要打日志。
- 用户偏好：扬尘不要垂直偏移（位置由代码决定），排气烟才有 `offsetY`；船体标记只对 Sable 有效、无碰撞、每簇一个；
  光源管理器替换任何会发光的方块（带 BE 的方块拒绝）；GPU 路径不必保守（真正的开销旋钮是阴影灯数量）。

## 11. 版本控制（2026-09-29 起）

**本地**：`D:\DSH\McMOD\work\sable-light-overhaul`，分支 `main`。
**远端**：`https://github.com/TeamGuy10086/SableLightOverhaul`（**私有**）。

> 在这之前仓库**没有版本控制，改动不可回滚** —— 这是挂了很久的长期风险，现已消除。
> **改动验证通过后要提交并推送**；未实测的在提交信息里写明"未实测"。

**忽略规则**（`.gitignore`）—— 它决定"克隆下来能不能直接编译"：
```
build/  .gradle/  run/  logs/  libs/*.jar  libs/**/*.jar  .idea/  *.iml
```
- `libs/**/*.jar` 是补的：单级 `libs/*.jar` **不匹配子目录**，`prepare-libs.ps1` 从 `lamb-runtime.jar`
  抽出来的 13 个 jar 本来会被提交进去（3.6 MB 第三方二进制）。
- 后果：**克隆下来不能直接构建**。必须先在**有游戏实例**的机器上跑 `prepare-libs.ps1` 从 `mods/` 抽 jar。
  也就是**游戏实例是构建的必要依赖**：实例没了，仓库也编不了。缓解：把 `libs/`（34 MB）另行归档到别的盘。

**行尾**（`.gitattributes`）：`* -text`，**禁止任何 EOL 转换**。本仓库**故意**混着 LF 和 CRLF
（`.ps1` 和部分老 Java 是 CRLF；`HANDOFF`、`patch/*.md`、新 Java 是 LF）。若 `core.autocrlf` 哪天变成
`true`，**每个 LF 文件都会在检出时被改成 CRLF，整棵树看起来全被改过**，真实改动会被埋掉。
仓库级 `core.autocrlf=false` 也已设（不动全局）。

**⚠ 网络：git 走代理，浏览器不一定**（这台机器的实测，2026-09-29）
- 跑着 `v2rayN` + `v2ray`，本地 **HTTP** 代理 `127.0.0.1:19788`。
- **两件事是分开的，别混**：`v2ray` **核心在跑**（→ 19788 在听）才是 git 需要的；
  v2rayN 里的**"系统代理"开关**（→ WinINET `ProxyEnable`）只影响浏览器那类程序，**git 用不到**。
  用户把系统代理关了，git 照样能推 —— 因为 git 是**显式指向 19788** 的。
- **已配**（只对 github.com 生效，其它 remote 不受影响）：
  `git config --global http.https://github.com.proxy http://127.0.0.1:19788`
  撤销 `--unset`；**端口若在 v2rayN 里改过，这条要跟着改**。
- **实测的三组对照**（`GIT_CURL_VERBOSE` 验了实际目的地，不是猜的）：

  | | 目的地 | 结果 |
  |---|---|---|
  | 清空代理真直连 | `20.205.243.166:443` | ✅ 4.6s —— 但**同一天早些时候直连超时过两次，各 21 秒** |
  | 死端口 19799 | `127.0.0.1:19799` | ❌ 2.2s（快速失败，好判断） |
  | 真实代理 19788 | `127.0.0.1:19788` | ✅ 3.5s，**稳定** |

  → **直连"时通时不通"，走代理稳定。** 忘了开 v2ray 不会卡 21 秒，会**立刻**报连接被拒。
- **不用账号密码**：GitHub 自 2021-08 起不接受密码做 git 认证。系统级
  `credential.helper = manager-core`，首次推送**浏览器授权一次**即可。
  凭据类的东西**绝不入库、绝不写进任何配置**。

**命名**（2026-09-29 定）—— 只改了"外壳"这一层：
| | 旧 | 新 |
|---|---|---|
| 目录 / `rootProject.name` / `archivesName` | `cbc-muzzle-light` | `sable-light-overhaul` |
| mod 显示名 | `CBC Muzzle Light (Experimental)` | `Sable Light Overhaul (Experimental)` |
| **`modId` / Java 包 / 资源命名空间 / 配置文件 / SavedData 键** | `cbcmuzzlelight` 等 | **未改，故意** |

**为什么 `modId` 和包名没动**：改它们会让**现有存档里的方块和物品全部失效**，并丢掉用户调过的配置与已登记光源。
测试期（0.1.x）恰恰是最需要"改完就能进去看"的阶段，所以留到用户**决定设基线并 release** 时一次做完，
那时顺手写配置与 SavedData 的迁移。**在那之前不要动 `modId`。**
（`group = 'dev.cbcmuzzlelight'` 同理留着 —— 它跟包名绑在一起，跟包名一起改。）

**版本策略**：`0.1.0` 起，测试期只递增**第三位**（`0.1.1`、`0.1.13`、`0.1.91` …），
**在用户明确说"设基线并 release"之前不许升到 `0.2`**。之前用的 `0.11.0-alpha` 那套已废弃。

**约定**
- 版本号唯一真源 = `build.gradle` 的 `version`；jar 档名唯一真源 = `build.gradle` 的 `base.archivesName`
  （`deploy-to-instance.ps1` 这两个都从 `build.gradle` 读，不再硬编码，所以再改名不会漏）。
- 每个版本打一个附注 tag（现状 `v0.1.0`）。
- 首个提交的作者/SMTP 已设为本机身份（`TeamGuy10086 <ylyl051234@qq.com>`）；
  **密码类凭据绝不入库、绝不写进任何配置**。
- `patch/photon/.../diffuse_lighting.glsl` 是**生成物但入库** —— 它是交付物（README 让用户直接拷这个文件），
  且 `apply-photon-patch.ps1` 幂等，所以入库让补丁在没有 Photon 原文件时也可复现。
- **`RELEASE-*` / `COLORS-*` / `GENERATORS-*` / `GPU-VERIFY` / `SABLE-BRIDGE` / `SPOTLIGHT-*` / `README.zh-CN.md`
  里的旧档名与旧路径一律不改** —— 它们记录的是当年的文件名，改了就是篡改历史。
  代价是仓库里没有正经的首页 README（GitHub 不会把 `README.zh-CN.md` 当首页渲染），这条记在待办里。
