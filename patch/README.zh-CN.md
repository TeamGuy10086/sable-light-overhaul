# Photon 光影补丁 — 安装说明

让 CBC Muzzle Light 的炮火照明**进入 Photon 自己的光照阶段**（而不是叠加在画面上）。

## 安装：用脚本，不要手工拷

补丁历来靠**手工覆盖一个文件**安装，于是「换了实例目录」「补丁是在拷贝之后才打的」这类漏装
几乎无法察觉 —— 症状要到进游戏才发现（例如手电筒没有光锥）。为此有了部署脚本：

```powershell
# 只看不改（交接、换实例、怀疑漏装时先跑这个）
& 'D:\DSH\McMOD\work\sable-light-overhaul\patch\deploy-to-instance.ps1' -Check

# 真正部署；省略 -Instance 时会在已知实例根目录下自动找唯一候选
& 'D:\DSH\McMOD\work\sable-light-overhaul\patch\deploy-to-instance.ps1' -Instance '<版本目录>'
```

它管两件事，也只管这两件：

| 项 | 动作 |
| --- | --- |
| `mods\sable-light-overhaul-<版本>.jar` | 与 `build\libs` 里刚编出来的同名 jar **逐字节比对**；顺带删掉 `mods` 里**其它版本**的同名 jar（同 mod id 两份会让游戏起不来），**并连旧档名 `cbc-muzzle-light-*.jar` 一起清理**（改名后旧 jar 留在 mods 里同样会让游戏起不来） |
| `shaderpacks\<包>\shaders\include\lighting\diffuse_lighting.glsl` | **先重新生成**补丁产物（幂等），再与实例里那份比对、必要时覆盖 |

实例里的 `config\*.toml` **绝不会被改写**（那是用户调过的值，新键由模组自己补），脚本只报告存在性与
`lightingMode` 的值。退出码 `0` = 全部一致，`1` = 有需要处理的问题。

> **脚本本身必须是纯 ASCII。** 它跑在 Windows PowerShell 5.1 + ANSI 代码页 936 下，无 BOM 的
> `.ps1` 会按 GBK 解码，任何中文字面量都会直接变成解析错误。仓库里每个 `.ps1` 都是纯 ASCII，
> 这也是脚本**自动探测实例目录**（该目录名是中文）而不是硬编码路径的原因。

### 手工等价操作（脚本不可用时）

```
patch/photon/shaders/include/lighting/diffuse_lighting.glsl
   →  覆盖到 <你的Photon包>/shaders/include/lighting/diffuse_lighting.glsl
```

桥接代码已**内联**进这个文件，**不需要新增任何文件**。改动是**纯增量**：原文件 275 行一行未删，
新增 395 行（桥接代码 + 1 行调用 + 空行），补丁后 670 行。已用脚本核对过「原文件每一行都仍在补丁里」，
并用 glslang 编译过延迟/非延迟两种配置。

> 补丁产物由 `patch/source/cbc_muzzle_light.glsl` 经 `apply-photon-patch.ps1` **生成**，
> 不要直接手改产物 —— 手改会让「源片段」和「产物」脱节，下次重新生成就把改动冲掉了。
> （0.9.98 期间就发生过一次：两者注释排布不一致，代码其实相同。部署脚本每次都会重新生成并对齐。）

> **0.11.0 起必须重新覆盖**（本轮）：阴影协议换成**位掩码 + 每盏灯自己的 `axis.xy`**，并加了软阴影。
> `uniform int cbcMuzzleShadowCount` **已被删除**，改成 `cbcMuzzleShadowMask` 与 `cbcMuzzleShadowSteps`
> （`cbcMuzzleShadowSoftness` 一度存在，但既然每盏灯的软度都走 `axis.x`，那路 uniform 是多余的，**已删**）。
> `cbc_muzzle_visibility()` 多收 `softness` 与 `ceiling` 两个参数，射线停止距离改为 `CLEARANCE`。
> **旧补丁配新模组 = 编译失败 = 整个光影包不亮**（`no matching overloaded function found`）。
> 模组侧会照常推送这些名字，包不声明它们只是收不到。

> **0.10.0 起也要重新覆盖一次**：阴影射线的**采样次数改为自适应** ——
> `cbc_muzzle_visibility()` 多收一个 `strength` 参数，并按「灯的亮度 × 射线长度」决定这次行进分几段。
> 报错 `no matching overloaded function found` 就是在用旧补丁配新模组。
> 不换补丁的后果没有手电筒那次严重（只是每次都固定 12 次采样、省不下来），**但仍要换**：
> 旧补丁里 `cbc_muzzle_visibility` 是两参数版本，新模组传三个参数进去会**直接编译失败**，
> 表现为**整个光影包不亮**。模组侧的"名额按屏幕覆盖挑"不需要动包，那一半照常生效。

> **0.9.78 起也要重新覆盖一次**（本轮）：探照灯/射灯的**距离衰减**变成"每盏灯自己的属性"，
> 由 `light.axial()` 的**符号**表达（`radius < 0.0` 表示这盏灯要额外按长度衰减）。
> 补丁必须能读出这个符号，否则**手电筒不会随距离变暗**、而射灯也仍是老样子 ——
> 换句话说：**不换这一次补丁，手电筒的距离衰减在 Advanced 档完全不生效**。
> 补丁里的改动只有一处：`falloff` 的参数由 `across` 改为 `radius < 0.0 ? max(across, down) : across`，
> 其中新增 `float range = abs(radius);` 与 `float down = along / range;`。
> 射灯（`radius > 0`）走的分支与旧补丁**逐字节等价**，观感不变。

> **0.9.3 起必须重新覆盖一次**：桥接新增 16 个 `cbcMuzzleTintN` uniform（每盏灯自己的颜色），并把"先求和再乘全局色"改为"每盏灯乘自己的颜色"；同时修掉了射灯斜照时的扇形缺口（法线项改用 `-axis`）。**不换补丁 = 射灯扇形缺口不修、颜色不出现。**
>
> **0.9.2 起也要覆盖过**：16 个 `cbcMuzzleAxisN`（射灯光束轴向 + 半角正切）。旧补丁里没有光束分支，射灯会按球形衰减。

> **上一版为什么报 `GLSL source pre-processing failed`**：我用 PowerShell 写文件时带上了 UTF-8 **BOM**，而 BOM 会出现在文件最开头的 `#if !defined ...` 之前，Iris 的预处理器遇到"指令前有多余 token"就直接失败。Photon 自身所有文件都没有 BOM。现已改为**无 BOM** 写入（首字节 `23 69 66` = `#if`），并加了断言防止再犯。

## 补丁做了哪两处改动

**① 内联桥接代码**（在 `#include "/include/lighting/colors/blocklight_color.glsl"` 之后，因为要用它的 `blocklight_scale`）：声明 uniform + `cbc_muzzle_falloff()` + `get_cbc_muzzle_lighting()`。

**② 加一行调用**（在方块光/手持光之后、emission 之前）：

```glsl
#ifdef HANDHELD_LIGHTING
    lighting += get_handheld_lighting(scene_pos, ao);
#endif

    lighting += get_cbc_muzzle_lighting(scene_pos, normal, ao);   // ← 新增

    lighting += material.emission * emission_scale;
```

## 为什么这样接能让观感变好

注入点在 Photon 的 `get_diffuse_lighting` 内部，因此我们的光能拿到 Photon 的**真实法线、环境遮蔽（AO）和 HDR 线性色彩空间**，并且发生在**色调映射与曝光之前**——于是它会被 Photon 自己的泛光、曝光和色调映射一起处理，不再像"浮在画面上的一层"。量级直接沿用 Photon 的 `blocklight_scale`，和它自己的方块光处在同一亮度体系里。

## 安装步骤

**前提**：模组 JAR 必须是含桥接的新版本（`IrisMuzzleBridge` 已打进包里）。

1. **备份** `shaderpacks/低配photon-voxy-support.zip`。
2. **解压成文件夹**（推荐，比改 zip 简单；Iris 支持文件夹形式的光影包）：
   解压到 `shaderpacks/低配photon-cbc/`，应能看到 `低配photon-cbc/shaders/...`。
3. 用补丁文件**覆盖**：
   ```
   低配photon-cbc/shaders/include/lighting/diffuse_lighting.glsl
   ```
4. 在 Iris 里选择这个新包（`低配photon-cbc`）。
5. **改模组配置** `config/cbcmuzzlelight-client.toml` 的顶部：
   ```toml
   lightingMode = "Advanced"
   ```
   `Advanced` 档 = 走本补丁（光由 Photon 内部计算、且带阴影）。改完即时生效。

> 想直接改 zip 也行：用 7-Zip 打开 zip，把 `diffuse_lighting.glsl` 拖进 `shaders/include/lighting/` 覆盖即可。

## 档位与补丁的关系（重要）

模组的 `lightingMode` 现在分四档，**本补丁只对 `Advanced` 档有意义**：

| 档位 | 实现 | 需要本补丁 | 网格重建 |
|---|---|---|---|
| `Legacy` | 原版方块光传播（真实光照，其他读方块光的系统也能看到） | ❌ | 有 |
| `Compatible` | Lamb 区域光 + Sable 接收区；任意光影通用，能世界↔船体互换；高射速较卡 | ❌ | 有 |
| `DynamicOnly` | 屏幕空间叠加；任意光影通用，动态光 + 射灯 + 静态登记光 | ❌ | 无 |
| `Advanced` | 注入光影包光照阶段（**本补丁**），有阴影 | ✅ | 无 |

- 装了补丁 + `Advanced` → ✅ 光由 Photon 内部计算（本补丁的目标形态）
- 装了补丁 + 其他档位 → 走对应实现，**不冲突、也不会双份**（包内那些 uniform 没人推送时恒为 0）
- `Advanced` 但**补丁没装** → ❌ **完全不发光**，请改用 `DynamicOnly`
- `Advanced` 档下**关掉光影** → 模组会**自动退回** `DynamicOnly`，不会变黑

### 桥接用的 uniform（写自己的注入时需要）

| uniform | 含义 |
|---|---|
| `cbcMuzzleColor` (vec3) | 全局光色（线性空间） |
| `cbcMuzzleIntensity` / `cbcMuzzleNormalInfluence` (float) | 总强度 / N·L 权重 |
| `cbcMuzzleShadowCount` (int) | 最近几盏灯做阴影步进 |
| `cbcMuzzleLight{i}` (vec4) | xyz = 场景空间位置（相机相对），w = 亮度 0..1 |
| `cbcMuzzleShape{i}` (vec4) | x = 半径（光束时是**射程**），y/z/w = core/middle/tail 三段衰减 |
| `cbcMuzzleAxis{i}` (vec4) | xyz = 光束轴向（单位向量，场景空间），w = 半角正切；**w=0 表示不是光束**（球形） |
| `cbcMuzzleTint{i}` (vec4) | rgb = **这一盏灯自己的线性颜色**；炮火/曳光弹是配置里的全局色，登记的灯/射灯是各自的颜色 |

射灯的亮度只与到轴向的夹角有关（`side / (along * w)`，`along` 被约掉），射程是硬截止；非光束光源仍然用 `distance / radius` 的球形衰减。

射灯的法线项用的是**光束入射方向 `-axis`**（不是"指向轴线垂足"，那会让倾斜的光束在地面上少一个扇形）。

## 调参（都在 `[gpu]` 段，改完即时生效）

```toml
	colorRed = 1.0           # 光色（线性空间）
	colorGreen = 0.58
	colorBlue = 0.25
	packPathIntensity = 2.0  # 仅"包内光照"路径的强度；它再乘 Photon 的 blocklight_scale
	normalInfluence = 0.6    # N·L 强度：0 平铺、1 完整漫反射
	shadowLightCount = 1     # 最近几个光源投阴影；0 = 关（最省），每多一个多一趟深度步进
```

参考量级：Photon 的 `blocklight_scale = 6.0`（火把级），`emission_scale = 40`。默认 `packPathIntensity = 2.0` 约为火把亮度两倍。太暗就调大（试 3～5），过曝就调小。

## 阴影（世界/不透明表面）

炮口的闪光现在会被**几何体遮挡**：光从被照表面朝光源方向做一次屏幕空间深度步进，中途撞到东西就把这盏光压掉。

- **范围**：只作用于**不透明表面**（延迟着色路径）。水面等前向路径不含阴影——一方面是刻意的（水面本来就不该有明显的动态阴影），另一方面那些程序的深度采样器与延迟路径不是同一个，硬接会出错。
- **物理体**：船体写进的是同一张主深度缓冲，所以船壳**顺带**也能挡光，没有为它写任何专门代码。
- **需要模组侧** `lightingMode = "Advanced"`（即走了本补丁），且 `shadowLightCount > 0`。
- **谁投阴影由模组决定，不由光影包决定**（0.11.0 起）：光影包读的是一路 **16 位掩码** `cbcMuzzleShadowMask`，第 i 位 = 缓冲里第 i 盏灯投不投。**这是为了拆开一个耦合**：以前是 `i < cbcMuzzleShadowCount`（"前 N 盏投"），于是模组必须**重排灯列表**才能把某盏灯放到某个下标上，而列表顺序同时又决定谁被预算截断 —— 选谁投阴影和选谁留下被绑在一起。掩码没有这层耦合。
- **`tints[i].w` 现在只管一件事**：`> 0.5` 表示"别照亮这盏灯自己所在的那个方块"（避免加法光把灯具自己的贴图冲白）。**它不再参与阴影判定。** 以前它是一个 bit 管两件事，这正是"注册灯永远不投影"的由来 —— 那不是设计，是没有别的通道可用。
- **名额按「灯的衰减球 ∩ 视野锥」挑**（`LightScreenWeight`），不是按距离、也不是按"灯心落在屏幕哪里"。**这一点是关键**：会穿墙漏光的那盏灯**在墙的另一侧，因此在屏幕上根本不存在**，只看灯心屏幕位置的判据永远找不到它；而它就在你正前方两格，球体与视野锥相交得很充分。判据是每个侧面 `(signed + radius) / radius` 钳到 `[0,1]` 再连乘，乘上实心角 `半径²/(半径²+距离²)` 与亮度。**半径上限 32 格** —— 光束的 extent 是它的**长度**，512 格探照灯报一个 512 格半径，不封顶它会仅凭体积压过一切。
- **本地手电筒的光束有一个预留位**（不受 `shadowLightCount` 影响）：它是你正看着的那盏灯，齐射不该把它挤掉。
- **每一类光源可以由玩家单独关掉投影**（客户端配置，全部是本地偏好）：`shadowSources`（注册灯）、`shadowSpotlights`（射灯）、`shadowMuzzle`（炮火/曳光）、`flashlightShadow`（自己）、`flashlightPeerShadow`（他人）。**颜色和亮度是世界属性、投影是客户端偏好**：别人的载具开过来时，你不该被迫挨个去点光源设颜色；反过来你机器好也不该替所有人决定光影开销。

**在包里可调的参数**（`shaders/include/lighting/diffuse_lighting.glsl` 顶部，改光影包即可，不用动模组）：

```glsl
#define CBC_MUZZLE_SHADOW_STEPS_MAX 16   // 采样循环的编译期上界；实际步数由模组的 uniform 给（1..16）
#define CBC_MUZZLE_SHADOW_STEPS_NEAR 8.0 // 射线长度（格）不超过它时给满步数，更长则按比例递减（最低到一半）
#define CBC_MUZZLE_SHADOW_START 0.1      // 第一次采样距被照表面的距离（格）
#define CBC_MUZZLE_SHADOW_THICKNESS 4.0  // 假定的遮挡物厚度（格）；太小会漏光，太大会误挡
#define CBC_MUZZLE_SHADOW_BIAS 0.05      // 用来排除被照表面自身的自遮挡
#define CBC_MUZZLE_SHADOW_CLEARANCE 0.6  // 射线在距灯多少格处停下。**必须越过半格**，见下
```

**运行期由模组推的两路 uniform**：`cbcMuzzleShadowMask`（16 位掩码）、`cbcMuzzleShadowSteps`（默认步数上限）。

**每盏灯自己的阴影行为走 `axis.xy`**（**0 个新 uniform**）：球形灯的 `axis` 向量本来是空的
（`axis.w == 0` 正是"这不是光束"的标记），于是

```
axis.x = 这盏灯的半影半径（格），0 = 硬边
axis.y = 这盏灯自己的采样上限，0 = 用全局的
```

**光束完全不读它**：`axis.w > 0` 时 `axis` 就是它的方向，永远硬边。
这样"上一盏软、下一盏硬、再来一盏多给几次采样"不需要任何额外通道 —— 每盏灯的解算结果由模组算好放进去，
**客户端的上限永远压在上面**（`shadowSoftness = 0` 可强制全部硬边），所以别人船上的一盏灯
可以"希望"投一个很贵的阴影，但**不能替你花帧时间**。

**`CLEARANCE` 为什么必须是 0.6 而不是随便一个小值**：注册灯坐在它替换掉的那个方块的**中心**，而那个方块的模型
**写在深度缓冲里**。射线若被允许走到灯心，就会撞上灯自己那个方块，把灯整个遮死。旧值 0.35 一直没暴露问题，只是因为
那时**任何允许投影的光都不会位于一个方块内部**。

**步数为什么可以变**：采样点按**几何级数**铺，`step_count` 只决定"分几段"，跨度始终是 `[START, 射线长度]`。
所以少采样 = 覆盖不变、间距变粗，丢掉的是**靠灯那一端**最稀疏的几次。`taps = 上限 × 灯的亮度 × 距离渐隐`，钳在 `[上限/2, 上限]`。

**软阴影为什么几乎没有代价**：射线目标点从"灯心"改成"灯周围半径 softness 的小球上随机一点"，
就是**面积光的一次蒙特卡洛采样** —— 还是同一条射线，0 次额外采样。关键区别在于误差的性质：
采样次数不足产生的是**空间相关的误差**（相邻像素一起漏判），表现出来就是**波纹状断层**，**TAA 消不掉**；
而随机抖动的目标是**不相关噪声**，TAA 几帧就把它平均成柔和的半影。所以这两个设置是**配套的**：想要低步数就该开软阴影。
**光束（射灯、手电筒的锥）永远是硬边** —— 扫描灯的锐利轮廓是它的全部意义。代价：**没开 TAA 会看到噪点**。

**已知局限（务必知悉）**：

- **遮挡物必须在屏幕内可见**。光源或遮挡物在屏幕外时会漏光——这是屏幕空间方法的天花板。
- 这是**近似阴影**，不是真阴影：没有从光源视角的深度图，靠的是"假想厚度"来判定遮挡。
- 薄墙可能略漏光，厚结构可能出现轻微过挡，可调 `CBC_MUZZLE_SHADOW_THICKNESS`。
- **步进分布是几何级数**（靠近被照表面密、朝光源方向疏），和 Photon 自己算屏幕空间光追用的是同一种形状。早期版本用的是**均匀步长**，第一次采样就落在距离的 1/8 处，近表面的遮挡物完全采样不到——这是"精度不如 Photon"的主因，**已修**。
- **采样带 dither 抖动**（用 `noisetex` + `frameCounter`，每像素每帧变化），靠 Photon 的 TAA 做时间平均。早期版本是确定性采样，光源一移动采样点就整体平移、遮挡判定帧间反复翻转，于是出现**莫名的闪烁**——**已修**。
- **不依赖 `SHADOW_SSRT` 档位**：我们用自己写的步进，只用 `depthtex1` 和投影矩阵，所以 Photon 的 low/medium 档也能用。（没有复用 Photon 自带的 `raymarch_shadow`：它的步长是按屏幕包围盒算的，对有限距离的点光源会越过光源、把光源背后的几何误判成遮挡。）

## 怎么确认生效了

1. 开火，炮口附近出现橙色短时增亮；
2. 与 Stage 0 对比：光会**随 Photon 的曝光变化**（白天室外仍可见），暗处**受 AO 影响**；
3. `advanced.performance.debugLogging = true` 时，日志里的 `CBC Muzzle Light GPU render: ...` 这行会**停止出现**（屏幕空间叠加已被跳过），而 `GPU: lights=N, rebuilds=0` 仍会打印——说明"零网格重建"依然成立。

## 排查

- **仍然预处理失败** → 确认替换后的文件首字节不是 `EF BB BF`（即无 BOM），且路径确实是 `shaders/include/lighting/diffuse_lighting.glsl`。
- **完全不发光** → 多半是 `lightingMode = "Advanced"` 但补丁没生效；先改成 `DynamicOnly` 确认屏幕空间叠加正常，再检查文件替换。
- **光影包加载失败** → 在 Iris 里选回原来的 zip 即可，原包未被改动。模组侧桥接失败会在 `latest.log` 打印 `Iris muzzle-light bridge disabled for this session`。

## 其他

- `patch/source/cbc_muzzle_light.glsl` 是生成器用的源片段（带 include guard，便于阅读），**不需要安装**；`apply-photon-patch.ps1` 会把它的内容内联进上面那个文件。
- **Photon 更新后补丁会被覆盖**，需要重新跑一次生成脚本。
- 目前只覆盖 Photon。Bliss / Complementary / BSL 的注入点已调研清楚，尚未适配（见 `IRIS-COMPAT-PLAN.zh-CN.md`）。
- 光照仍是**近似**：炮火用球形衰减近似定向棱形光场；**射灯是精确的角向光束**（`cbcMuzzleAxisN`，光强只与角度有关，与距离无关）。Photon 的阴影贴图仍然只给太阳，动态光用的是本补丁自带的屏幕空间步进。
