# 与光影（Iris）兼容的方案设计

> 状态：v4。**Stage 0 已实现并实机验证通过**（光影下可显示，`rebuilds=0` 保持）；Stage 0 的表面法线优化已实现。
> **Stage 1 的 Photon 补丁已实现并交付**（待实机验证）。Bliss / Complementary / BSL 尚未适配。

## 一、目标与约束

**目标**：开光影时炮火/曳光弹的动态光照也能正常显示，并尽量保留 GPU 模式的性能优势（网格零重建）。

**必须覆盖的光影包**（你实际在用）：

| 光影包 | 说明 |
|---|---|
| Photon（低配 voxy 版） | 你个人最常用 |
| Bliss Shader | Iris 当前选中项；**它实现了 Emission，能让烟雾和炮弹自发光** |
| Complementary Unbound r5.8.1 | |
| Complementary Reimagined r5.8.1 | |
| BSL v10.1.3 | |

## 二、为什么现在不兼容

我们的光写进主 framebuffer，Iris 也把最终画面写进主 framebuffer —— 我们写得更早，被 Iris 的合成覆盖；同时场景深度在 Iris 的 gbuffer 里，我们读到的是无效值。

## 三、已确认的关键事实

### 3.1 Iris 侧（源码调研确认）

| # | 结论 |
|---|---|
| 1 | **`AFTER_LEVEL` 时主 framebuffer 已是 Iris 最终成片**：Iris 的 `finalizeLevelRendering()`（composite + final）注入在 `renderLevel` 的 RETURN 之前；NeoForge 的 `AFTER_LEVEL` 在 `GameRenderer` 里、`renderLevel` 返回**之后**派发 |
| 2 | **`AFTER_LEVEL` 时主 framebuffer 深度仍是有效场景深度**：Iris final pass 只 `depthMask(false)`，不破坏深度内容 |
| 3 | **不存在动态光源注册 API**：`IrisApi` 仅 9 个方法；全源码 grep `addLight/dynamicLight/pointLight/LightSource/uploadLight` 零命中 |
| 4 | **自定义 uniform 机制真实可用**：`CommonUniforms#addNonDynamicUniforms` + `UniformHolder.uniform4f(PER_FRAME, name, supplier)` + `FrameUpdateNotifier.addListener` |
| 5 | **无第三方着色器补丁扩展点**：`TransformPatcher` 是 AST 级 GLSL 改写器，但所有 transformer 均硬编码静态方法，无 ServiceLoader/register |
| 6 | **colortex 纹理 id 公开 API 拿不到**：`IrisRenderingPipeline.renderTargets` 是 `private final` |
| 7 | **可运行时检测光影**：`IrisApi#isShaderPackInUse()` 是公开 API |

### 3.2 光影包侧（五包源码调研确认）

| 包 | 主光照函数 | 阶段 | 世界坐标 | 法线 | 已有可参照的点光函数 | 建议注入点 |
|---|---|---|---|---|---|---|
| **Photon** | `get_diffuse_lighting`（`include/lighting/diffuse_lighting.glsl`） | 延迟 (d4) | `view_to_scene_space(view_pos)`，+`cameraPosition` 得绝对坐标 | colortex1 解包的 `flat_normal` + 法线贴图 | `get_handheld_lighting` | 同文件 :258 旁 |
| **Bliss** | `dimensions/composite1.fsh` 的 `Indirect_lighting` 段 | 延迟 | `mat3(gbufferModelViewInverse) * viewPos` | 自解码 gbuffer → `slopednormal` / `FlatNormals` | **`createLightningPointLight`（现成的"世界坐标+颜色+N·L+衰减"通用点光）** | composite1.fsh :1246 旁 |
| **BSL** | `GetLighting`（`lib/lighting/forwardLighting.glsl`） | 前向 | `worldPos`（顶点传出，相机相对） | 顶点 normal / 法线贴图 | `ApplyDynamicHandlight`（把点光折进 `lightmap.x`） | `blockLighting` 之后、`albedo *=` 之前 |
| **Comp Unbound** | `DoLighting`（`lib/lighting/mainLighting.glsl`） | 前向 | `playerPos`（相机相对） | `normalM` / `worldGeoNormal` | `GetHeldLighting`（本身就是世界坐标点光） | :371 `blockLighting` 之后 |
| **Comp Reimagined** | 与 Unbound **同构、行号一致** | 前向 | 同上 | 同上 | 同上 | 同上（一稿两用） |

**两个极其有利的共性**：

1. **五个包的坐标空间一致**：都是**相机相对世界坐标**，加 `cameraPosition` 即绝对世界坐标。
2. **五个包的注入点都在 tonemap/曝光之前**，属 **HDR 线性空间** → 直接叠加线性色即可，不需要自己乘曝光。

**注入点都同时具备「世界坐标 + 法线 + 环境遮蔽」三要素** → 可以做真正的 `N·L` 漫反射，而不是简单加色。

### 3.3 数据通道（决定 Stage 1 无需光影作者配合）

旧版 0.1.0 的 demo 光影包给了决定性的证据：

- 它的 `shaders.properties` **是空的**（只有一行注释）
- 它只在 GLSL 里 `#include` 了声明 `uniform vec4 cbcMuzzleLight0;` 的文件
- 模组侧通过 `UniformHolder.uniform4f(PER_FRAME, "cbcMuzzleLight0", supplier)` 注册数值

**结论：只要 GLSL 里声明了 uniform，就能收到模组上传的值，不需要任何 `shaders.properties` 配置。**

⇒ Stage 1 的实现方式 = **注入 uniform 声明 + 注入光照项 + 用已验证的 uniform 注册机制喂数据**。
**这不需要光影作者做任何事**，只需要我们为每个包找到正确的注入宿主函数。

## 四、推荐路线

### Stage 0 —— 光影下"能看见"（通用、低风险）★先做这个

**检测到光影激活时，把光 pass 从 `AFTER_CUTOUT_BLOCKS` 改到 `AFTER_LEVEL` 执行。**

- 依据：事实 1 / 2 / 7。现有「屏幕空间四边形 + 深度重建」的着色逻辑几乎原样复用，只是换个触发阶段。
- 效果：**任意光影包都能看到炮火照明**，仍保持网格零重建。
- 代价：叠加在 tonemap **之后** → 观感偏平、不受光影泛光/曝光影响；拿不到法线 → 只能加色（**与你现在无光影路径能力相同，不是退步**）；第一人称手部在 `AFTER_LEVEL` 之后渲染，手不受光。
- 交付形态：模组内自动切换，无需用户配置。

### Stage 1 —— 光影下的高质量光照（逐包，但无需包作者配合）✅ Photon 已实现

按 3.2 的表格，把「一个通用 GLSL 片段 + 每包 1 处宿主函数」注入进去。

**Photon 实现细节（已交付，见 `patch/README.zh-CN.md`）**：

- 通道：mixin `CommonUniforms#addNonDynamicUniforms`（TAIL）→ `UniformHolder.uniform4f(PER_FRAME, name, supplier)`。
  已从源码确认 `CustomUniforms.assignTo` 会对**包声明了该 uniform 的程序**取 location 并逐帧推送，
  因此**只需要 GLSL 里声明，不需要改 `shaders.properties`**。
- 注入点：`shaders/include/lighting/diffuse_lighting.glsl` 里
  `lighting += get_cbc_muzzle_lighting(scene_pos, normal, ao);`（在方块光/手持光之后、emission 之前）。
  此处 `scene_pos`（相机相对世界轴）、`normal`（scene space，已由 `bent_normal` 的转换印证）、`ao` 齐备。
- 数据：每光源一个 `vec4`（xyz 相机相对位置 + w 归一化光级）+ 一个 `vec4`（半径 + core/middle/tail），最多 16 个；
  另有 `cbcMuzzleColor` / `cbcMuzzleIntensity` / `cbcMuzzleNormalInfluence` 全局量。
- 量级：乘 Photon 的 `blocklight_scale`(=6.0)，与它自己的方块光同体系；`emission_scale` 是 40。
- 开关：`[gpu] inPackLighting`——开启后模组的**屏幕空间叠加会停掉**，避免双份光照。
- 补丁只有 3 行改动（1 include + 1 调用 + 1 空行），已用 glslang（`#version 400 compatibility`，与 Photon 一致）校验通过。

**后续（未做）**：

- 顺序：**先 Photon（已完成），再 Bliss**。
- 注意：Bliss 的 `dimensions/` 是共享 program，但有 **4 套 world 目录副本需要同步**。
- 注意：**不要按行号定位注入点**，要锚定函数签名/唯一代码片段——包一更新行号就失效。
- 可选的精度提升：目前用球形衰减近似炮火的定向棱形光场；如需完全一致，可把完整形状参数（方向 + 前后/侧/上下范围 + diamond 标志）一并送进 shader。

### Stage 2 —— 覆盖面扩展

同法适配 BSL、Complementary×2（两者同构，基本一稿两用）。

### Stage 3 —— 生态提案（长期）

把光源数据契约整理成公开规范 + 参考 glsl 片段，推动光影包原生支持。这是唯一能兼得"正确光照 + 长期稳定"的终局，也避免我们一直跟着包更新改注入点。

## 五、风险与注意

| 风险 | 说明与缓解 |
|---|---|
| **包更新导致注入失效** | 锚定函数签名而非行号；注入失败要安全跳过（绝不破坏包），并在日志给出明确提示 |
| **曝光自适应** | 4 个包都有自动曝光，白天室外会压暗，点光需要显著提高强度。缓解：按 `light_levels.y`/`eyeBrightness` 做补偿，或提供用户可调强度（`[gpu]` 配置已有基础） |
| **色彩空间/量级** | 各包 HDR 线性但量级不同（Photon 参照 `blocklight_color×blocklight_scale`；Bliss 火把级 ≈1、闪电 ≈45，炮口闪光建议 1~10）。需要每包定标 + 用户可调 |
| **光源数量** | 高射速下可能有几十个光源。uniform 数组要按元素名逐个注册（实现时验证 Iris 对 `name[i]` 的处理），或改用纹理通道 |
| **自定义 image 通道** | 四包都声明了自定义 image（Photon `light_data_img`、BSL `lightimg0/1`、Comp `floodfill_img`、Bliss `imgBlockData`/`imgLpv1/2`），机制同类但**名字各异**，且**模组侧可写性未验证**。暂不依赖，作为备选 |
| **侵入性** | Stage 1 依赖 mixin `CommonUniforms`（0.1.0 已验证）+ 对包 GLSL 的文本注入。属于"可控但需维护"的折中 |

## 六、需要你确认的点

1. **Stage 0 是否现在开始实现？**（改动很小：判定光影 → 换阶段；无光影时行为完全不变）
2. **Stage 1 的注入，交付成什么形态？**
   - (a) 模组自动注入到包内（用户零操作，但包更新可能需跟随）
   - (b) 生成一个"补丁文件"由用户放进包目录（更透明，用户可见）
3. **关于 Bliss 的 Emission**：你提到它让烟雾和炮弹自发光。这是一个**与动态光不同的特性**（把 CBC 的烟雾/炮弹以"自发光"的身份交给光影），需要单独定范围——要不要一并纳入后续计划？

## 附 A：颜色/衰减（与本方案正交）

已加 `[gpu]` 配置段（`colorRed/Green/Blue`、`intensity`、`falloffPower`），改完即时生效。
走光影路径（Stage 0）时叠加发生在 tonemap 之后，观感与无光影路径不同，必要时再加一组"光影路径专用"缩放参数。

## 附 B：本轮已完成的成果（供回顾）

- 核心命题已验证：**高射速每帧连续跟上、运动 Sable 物理体正常、16 格边界棋盘消失、性能优秀、`rebuilds=0`**
- 修掉的三个真 bug：深度 feedback loop、深度格式不匹配（stencil 未镜像）、相机位于光体积内导致深度测试全剔
