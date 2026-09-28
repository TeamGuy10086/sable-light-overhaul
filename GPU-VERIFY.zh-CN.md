# CBC Muzzle Light — GPU 动态光验证版 测试说明

> **命名已变更（0.9.2）**：本文里的 `lightingMode = "GPU"` 现在叫 **`DynamicOnly`**；「只 GPU 化炮火/曳光弹」的范围限制也已取消。四档语义与射灯行为见 `SPOTLIGHT-0.9.2.zh-CN.md`。
>
> 本版在 0.9.1-alpha 基础上新增 `GPU` 光照模式（验证版）。协议、配置结构、注册项均未改动，仅新增 `lightingMode = "GPU"` 选项。

## 一、这一版改了什么

把炮火/曳光弹的**短时动态照明**从「写入区段网格 → 标脏重建」改为「更新 GPU 光源数据 → 着色阶段计算」。

- 新增 `dev.cbcmuzzlelight.client.GpuLightRenderer`：在 `RenderLevelStageEvent.AFTER_CUTOUT_BLOCKS`（opaque 地形 + Sable sublevel 都已画完、深度缓冲已写入）之后，用一个自定义 additive pass，按每个光源画一个光体积立方体。
- 片段着色器**从主深度缓冲重建片元的世界坐标**，在 GPU 上执行 `AreaLight.at()` 的等价计算，叠加炮火颜色。
- 光变化时只更新一个 uniform 数组（`uLightData`），**不调用 `onLightUpdate` / `setSectionDirty`，零网格重建**。

**技术路线**：光体积叠加层（加色增亮），覆盖普通地面（Sodium）与运动 Sable 物理体（两者都写主深度缓冲）。

## 二、安装

1. **备份**当前整合包里的 `mods/cbc-muzzle-light-0.9.1-alpha.jar`（改名加 `.bak` 或移到别处）。
2. 用新构建的 jar 替换：
   ```
   D:\DSH\McMOD\work\cbc-muzzle-light\build\libs\cbc-muzzle-light-0.9.1-alpha.jar
   ```
   → 放入整合包 `mods/` 目录（文件名保持不变）。
3. 双端（客户端/服务端）如需联机则都换；单人只需换客户端。

## 三、切换到 GPU 模式

编辑客户端配置 `config/cbcmuzzlelight-client.toml`，把：

```toml
lightingMode = "Advanced"
```

改为：

```toml
lightingMode = "GPU"
```

也可在游戏内「Mod 配置界面 → CBC Muzzle Light」里把 `lightingMode` 选为 `GPU`。

> 若 GPU shader 未成功注册（例如渲染环境异常），会自动回退到 `Advanced`，并在此界面上显示实际生效的模式（聊天栏会打印 `requested=GPU, active=...`）。

## 三·五、调光色 / 强度（`[gpu]` 段，改完即时生效）

GPU 光的颜色与强度在客户端配置里可调，**改完回到游戏即可生效，不需要重启**：

```toml
[gpu]
	colorRed = 1.0        # 加色光的红通道
	colorGreen = 0.58     # 绿通道（越大越偏黄，越小越偏红）
	colorBlue = 0.25      # 蓝通道（越小颜色越饱和）
	intensity = 1.0       # 总强度，0 就是完全不叠加
	falloffPower = 1.0    # 归一化光级的指数；>1 会让光更集中在光源附近
	shaderPathScale = 1.0 # 仅"开光影时"额外乘的强度（见下）
	normalInfluence = 0.6 # 0 = 旧的"平铺加色"，1 = 完整 N·L 漫反射（见下）
```

调色建议：
- **太亮/糊成一片死橙** → 降 `intensity`（试 0.6～0.8），或降 `colorRed`。
- **颜色太红/太土** → 提高 `colorGreen`（0.6～0.75）和 `colorBlue`（0.3～0.45），会更接近白热的火光。
- **想要光更"贴"在炮口** → 提高 `falloffPower`（1.5～2.5）。

着色器里已加入柔和高光压缩（只压亮部），所以调大强度时核心不会直接削平成纯色块。

**关于 `normalInfluence`（表面朝向）**：本版从深度缓冲重建了表面法线，让光按 `N·L` 落在表面上。

- `0` → 回到旧的"平铺加色"（只要在光场范围内，所有朝向都一样亮）
- `0.6`（默认）→ 有明显漫反射层次，同时保留一定的包裹感
- `1` → 完整 `N·L`，明暗对比最强

**它同时解决了最常见的"穿墙"观感**：朝背离光源的表面会归零，所以「炮口在墙后、墙正面却被点亮」这种情况会自动消失。觉得光整体变暗了就把 `intensity` 相应调高一点（`N·L` 平均小于 1）。

**关于 `shaderPathScale`（开光影时）**：开光影后叠加发生在光影色调映射**之后**，观感与无光影路径不同，
所以单独给了一档倍率。开光影时觉得太暗就调大（试 1.5～3），太亮就调小。它只在光影激活时生效，不影响无光影的表现。

## 四、验证步骤（按顺序）

先**不加载光影**，夜晚，用一块实心石墙/白色平台作为受光面。

1. **基础显示**：机炮朝平地/墙面开火，确认炮口附近表面出现橙色短时增亮，且开火停止后熄灭。曳光弹飞行时应有一条跟随光。
2. **普通地面 vs 物理体**：在 Sable 船上或载具上朝地面/另一艘船开火，确认物理体表面也被照亮（GPU 光通过深度重建直接照亮，无需 plot 坐标投影）。
3. **高射速**：机炮连续高速扫射，观察光是否**每帧连续跟上**（这是本版要解决的核心痛点）。对照 `Advanced` 模式同场景，感受差异。
4. **16 格边界棋盘**：复现你之前「暗亮/亮暗」的场景（静止 + 运动），确认沿 16 格区段边界不再出现整块缺光（GPU 光按片元连续计算，没有区段边界概念）。
5. **运动载具**：移动/旋转载具，观察炮火与曳光弹的光是否跟随。
6. **开光影**：启用 Iris 光影包（你当前选中的是 Bliss；也可试 Photon），重复第 1、3 项。本版会**自动**把光 pass 切到 `AFTER_LEVEL`，因此开光影也能看到炮火照明（观感与无光影不同，见 `shaderPathScale` 说明）。切光影**不需要重启**。

## 五、如何确认「零网格重建」

1. 打开调试日志：`config/cbcmuzzlelight-client.toml` 里 `[advanced.performance]` 的 `debugLogging = true`。
2. 复现开火，观察 `logs/latest.log` 出现：
   ```
   CBC Muzzle Light GPU: lights=N, rebuilds=0 (no section dirtying)
   ```
   `rebuilds=0` 即证明光变化没有触发任何区段标脏/重建。
3. 同一份日志里的 `GPU render: ...` 行会显示 `irisPath=true/false`，用来确认**当前走的是哪条路径**（开光影时应为 `true`）。
4. 对照：切回 `Advanced`，同样开 debugLogging，会打印 `worldAreas/.../staleBuildsRejected/oldBuildsSkipped`，可见其走的是「区段重建」路径。

> 说明：GPU 模式下 `Advanced` 的 `LambAreaBackend`、`AdvancedSable.project`、`publish()`（标脏）整条链路都被跳过。

## 六、已知限制（验证版边界，务必知悉）

- **加色增亮，非正确材质照明**：光直接加在表面颜色上，没有真实阴影、没有材质反射率。已加入从深度重建的**表面法线**（`normalInfluence`），可做 `N·L` 漫反射并剔除背向光源的表面；但仍不是完整材质光照。
- **没有动态光阴影**：`N·L` 只能剔除**背向**光源的表面，管不了"光被柱子挡住、柱子后面的地面本该有阴影"。真正的动态光阴影需要每光源阴影贴图（对几十个炮火光源代价过高）或屏幕空间光线步进（代价高、且只能看到屏幕内遮挡物）。**屏幕空间光线步进后来已在 `Advanced` 档的包内路径实现**（见 `patch/README.zh-CN.md`）；本文件描述的是模组自身屏幕空间叠加这一层，它仍然没有阴影。
- **遮挡是近似**：用「光体积 + 深度重建」判断，从光源视角的精确遮挡（阴影贴图）未实现，可能轻微穿墙。
- **范围**：0.9.2 起屏幕空间叠加**不再只做 `shot/` 与 `tracer/`**——射灯与静态登记光也在同一批光源里（动态永远排在前面，见 `SPOTLIGHT-0.9.2.zh-CN.md`）。射灯用真正角向光束（fsh 里 `flags>=7.5` 分支），不是球形近似。
- **光影下可显示，但不受光影后处理**：光影激活时光 pass 自动改到 `AFTER_LEVEL` 执行（此时主 framebuffer 已含 Iris 的最终成片、且深度仍有效），所以对**任意光影包通用、无需光影作者适配**。代价是叠加发生在光影色调映射**之后**：观感比场景"平"、不受光影泛光/曝光影响，且拿不到法线（只能加色）。用 `shaderPathScale` 单独调强度。
- **光影下第一人称手部不受光**：手部在 `AFTER_LEVEL` 之后渲染，影响很小。
- **尚未进入光影自己的光照阶段**：要让光照真正进入光影的色彩空间并做法线漫反射（`N·L`），需要按包注入 GLSL，属 Stage 1；已确认 Photon/Bliss/BSL/Complementary 的注入点，详见 `IRIS-COMPAT-PLAN.zh-CN.md`。
- **不含 Photon 专用改造**：旧版的 Photon 专用增强已按你的要求删除，本版没有恢复它。

## 七、如果看不到光 / 异常

1. 确认聊天栏提示的实际模式是 `DynamicOnly`（不是回退到 `Advanced`；0.9.2 前叫 `GPU`）。
2. 确认 `latest.log` 里 `GPU: lights=N` 的 N > 0（开火时）。N=0 说明没有光场产生（检查 CBC/CBCAT 依赖、事件是否正常）。
3. 若崩溃或画面异常，保留 `latest.log` + `crash-reports/`，并说明：发生在普通地面还是 Sable 物理体、静止还是运动、是否只有特定方块/材质异常。
4. 若光的颜色/强度不理想，直接调 `[gpu]` 段的 `colorRed/Green/Blue`、`intensity`、`falloffPower`（改完即时生效）；开光影时另外用 `shaderPathScale`。不需要改代码或重新编译。

## 八、构建

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
& 'D:\DSH\McMOD\work\gradle-runtime\gradle-9.2.1\bin\gradle.bat' -p 'D:\DSH\McMOD\work\cbc-muzzle-light' build
```

构建产物 `build/libs/cbc-muzzle-light-0.9.1-alpha.jar`；自动检查 83 项通过（0 failures/errors/skipped），无回归。
