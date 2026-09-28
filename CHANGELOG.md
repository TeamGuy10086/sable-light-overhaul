## 0.9.3-alpha

修复射灯斜照时地面少一个扇形（0.9.2 引入：法线项改用轴线垂足，轴线穿到地面以下后整片判为背向；改为光束入射方向 -axis）。射程上限 128→512（档距 16→32，共 17 档，末档正好 512），Compatible 档裁剪到 64。静态光源与射灯新增颜色：登记时按配置表→方块ID名字扫描（只看path、从左到右、双词优先、内置同义词表）解析，索引 0=透明、1..14=MC 除白黑外的染料、15=闪烁（10 tick 循环）；染料右键已登记光源可改色且不消耗（射灯除外）；颜色存在登记数据中，旧存档自动兼容；仅 Advanced 档渲染，DynamicOnly/Compatible/Legacy 不做颜色。补丁新增 16 个 cbcMuzzleTintN 并改为每灯乘自身颜色。协议仍为 6。90 项检查通过；实机验证待进行。

## 0.9.2-alpha

照明模式定为四档 Legacy/Compatible/DynamicOnly/Advanced；射灯改为沿方块局部+Y的角向光束（光强只与角度有关、距离为硬截止），红石三面分工（底=强度、顶=宽度、侧=颜色），衰减档 4→5 种，工具右键循环衰减、Shift+右键隐藏模型、空手循环距离；射灯与静态登记光进入 GPU 路径（动态优先、静态预算独立），新增 `cbcMuzzleAxisN` 桥接 uniform。命令升级为按中心点+半径批量登记/移除。协议 6，双端更新，Advanced 需重新覆盖补丁。86 项检查通过；颜色尚未接入渲染，实机验证待进行。

## 0.9.1-alpha

Sable Fancy防倒序网格提交、构建光场快照、重要区段刷新、Copycats虚拟取光坐标及AO缓存键适配、Yuushya独立缓存失效。检查83项通过；同场景实机复测待进行。协议5和配置不变，无剔除。

## 0.9.0-alpha

新增Advanced曳光弹光源、红石射灯、静态登记工具和命令。独立曳光弹预算、船体UUID局部锚点、按维度SavedData、状态变更同步。协议5，双端更新。73项检查通过，实机验证待进行。

## 0.8.2-alpha

机炮改为炮口最宽的定向棱形光场，后方部分裁剪；独立机炮/爆燃/热烟三套范围与时空衰减配置。按用户文件重建默认值，重组配置结构；协议仍为4。65项检查通过，游戏观感待验证。

## 0.8.1-alpha

Advanced 机炮前向锥形、独立双管、三段空间衰减；大炮按 CBC 装药强度及原生烟粒子绘制尺寸缩放。协议 4，双端更新。59 项检查通过，游戏验证待完成。0.8.0 跨空间正常及性能改善为用户反馈。

## 0.8.0-alpha

新增 Advanced/Legacy/None，默认 Advanced；Lamb 区域光、Sable 接收、Flywheel 补光、双管共享中心；协议升级至 3，客户端与服务器均需更新。构建与自动检查完成后交付，游戏验证待进行。

## 0.7.1-alpha

修复 plot 区块索引坐标、补 CBCAT 世界坐标转换、扩展诊断；38 项检查通过，游戏验证待完成。

## 0.7.0-alpha

新增可选 Sable 2.0.3 客户端世界光源到物理体 plot 的桥接、独立预算及诊断开关。36 项自动检查通过；游戏内效果待测。详细限制见 SABLE-BRIDGE.zh-CN.md。

# Changes in 0.6.0-alpha

- Add optional CBCAT 0.1.4c twin autocannon hook at its selected native muzzle coordinates.
- Follow native alternating barrel selection; send once per discharge, including the operator.
- Preserve separate autocannon shot records for sub-voxel left/right muzzles.
- Keep baseline 001 configuration, existing flame proportions and protocol v2.
- 33 automated checks pass; game alignment and runtime validation pending.

# Changes in 0.5.1-alpha

- Remove the transverse autocannon muzzle cap; keep only three longitudinal ribbons.
- Preserve existing size, proportions, timing and environmental lighting settings.

# Changes in 0.5.0-alpha

- Remove Photon/Iris shader injection, uniform bridge, vertex tags, config and test dependencies.
- Stop overriding native CBC smoke brightness/color/alpha; keep read-only particle tracking for environment light.
- Add independent auto flame length/width scales, default 0.8 / 1.3 relative to 0.4; preserve overall size.
- Provide a wider five-source tuning preset without changing existing illumination defaults.
- Large-radius renderer-side lighting remains a proposal; no performance guarantee or new backend in this release.
- Clean build and 28 checks pass; runtime verification pending. Protocol remains v2.

# Changes in 0.4.0-alpha

- Track bounded real hot-smoke references, including CBC fallback smoke, instead of the invisible plume emitter.
- Remove six-tick/four-block follow limits and permanent muzzle source; clear stale/cold sources.
- Add sparse configurable light layouts, level-15 auto peak and big-cannon peak hold.
- Raise new-install auto size default to 5, opacity and visible lifetime; preserve existing config.
- Add optional Photon-only HDR particle emission via in-memory Iris shader source patch and live strength uniform.
- Add Chinese config descriptions, source budgets and exact-library three-dimension offline shader parsing.
- Runtime/GPU/performance validation remains pending; network protocol stays v2.

# Changes in 0.3.0-alpha

- Default autocannon flame size is 3x the 0.2.0 length and width; configurable in game.
- Register a client config with NeoForge's built-in configuration screen and Chinese/English tooltips.
- Configure big-cannon duration, minimum brightness and per-tick linear decay as fractions of initial intensity.
- Default big-cannon light: 20 ticks, 25% minimum, 5% of initial intensity subtracted per tick.
- Snapshot settings per shot; saving applies to new shots without restarting the world.
- Use configured expiry for fixed and moving light sources; keep the six-tick plume motion limit separate from light expiry.
- Scale the near-muzzle flame cap and expand render bounds for the animated flame length.
- Add curve/config-lifetime regression tests. In-game validation remains pending.
- Network protocol remains v2, same as 0.2.0.

# Changes in 0.2.0-alpha

- Remove FLASH ball particles; add a narrow, barrel-aligned autocannon flame.
- Retain native big-cannon visuals and sample the hot plume near the muzzle.
- Keep independent invisible block lights; lower autocannon intensity.
- Remove drop-mortar addon hooks.
- Carry cannon direction in network protocol v2; upgrade server and clients together.
- Default-disable the optional Iris internal bridge; no Photon ZIP edits needed.
- Add original flame texture/generator and direction/lifecycle/asset/bytecode regression tests.
- Runtime validation of this version remains pending.


