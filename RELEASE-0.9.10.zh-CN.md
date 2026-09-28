# 0.9.10-alpha

按你这次的三条。

## 1. 履带扬尘不再有水平速度

你说得对，之前那次刷新给粒子带了水平分量（一部分是"随车身后拖"，一部分是随机扩散）。现在**纯垂直上抛**：`vx=0, vz=0`，只有 `上抛速度`（默认 0.35，0.75~1.25 倍随机）向上，然后靠摩阻+重力落回地面停住。车开过去，尘就留在履带经过的那条线上。

## 2. 炮火扬尘改为硬编码 ground_dust + 只留数值接口

之前炮火扬尘是**读配置里的粒子串**：你那份服务端配置里还是旧串（`minecraft:dust{...}`），而这版我把它当 `ground_dust` 以外的粒子处理——加上链路上一堆旧值，结果就是"完全不产生"。现在按你说的改法：

- **粒子硬编码**：炮火的两种扬尘永远用 `cbcmuzzlelight:ground_dust`（CBC debris_smoke 的 12 帧 + 自写控制程序），不再受配置字符串影响，配置里的 `groundDust.particle` / `hullDust.particle` 两个键**已删除**；
- **只留数值接口**，而且地面/车体的差别现在明明白白躺在配置里：

```toml
[tankCannon.groundDust]   # 地面（更宽、更扁、更久、可跟地面取色）
	count = 16
	radius = 6.0          # 采样半径：地面比车体大
	sizeMin = 1.8
	sizeMax = 4.0
	stretchMin = 1.8      # 水平拉伸：地面更扁更宽
	stretchMax = 3.2
	lifetime = 70
	color = 16777215      # 0xFFFFFF = 不染色 = debris_smoke 本色
	searchDistance = 24
	speed = 1.5
	upSpeed = 0.5
	tintFromBlock = true

[tankCannon.hullDust]     # 车体（更紧、更圆、更短）
	count = 14
	radius = 3.0          # 采样半径比地面小
	sizeMin = 1.4
	sizeMax = 2.6
	stretchMin = 1.0      # 水平拉伸：更接近圆
	stretchMax = 1.8
	lifetime = 40
	color = 16777215
	speed = 0.8
```

**为什么你"没在配置里看到车体和地面的差分"**：`radius / sizeMin / sizeMax / stretchMin / stretchMax / lifetime / color` 这些键是 0.9.8 之后才加的。NeoForge 会把新键补进已有配置文件，但如果你看的还是旧 jar 生成的文件（或者看的是 `cbcmuzzlelight-client.toml`），就不会有。确认方法：打开 `config\cbcmuzzlelight-server.toml`，搜 `stretchMin`——有就是新的。**如果确实没有**：先把文件备份（你改过的 `maximumDistance=512`、`distanceStep=32`、`[colors]` 要留着），删掉它让游戏重新生成一份完整键，再把那几项抄回去。

另外加了一行诊断（受 `generators.debugLog` 控制），开炮时会打：

```text
CBC Muzzle Light cannon dust: ground probes=9 hits=9 spawned=16
```

如果还是没尘，把这行发我：`hits=0` 就是找地面那步失败（我就换成从炮口正下方直接找），`spawned=16` 就说明粒子生成了但客户端没渲染（那是粒子/光影那边的问题）。

## 3. 生成器坐标与接管的修复仍在

- 生成器发射点/方向：改用 CBC 的坐标变换（0.9.9），`no viewer within 48.0` 应该消失；
- 接管：先 `updateSectionStatus(section,false)` 再 `checkBlock`（0.9.9），日志会带目标坐标（`first=...`），**这个还等你反馈**：坐标对不对、白光有没有消失。

版本 `0.9.10-alpha`，协议串 `"6"`，两端都换。
