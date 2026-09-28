# 0.9.12-alpha

按上一轮对齐的结果开码，除"光照接管"按你说的先跳过（等你测 0.9.11）。

## 1. 扬尘程序：改成"按轴积分"

不再用原版那套三轴同一个 `friction`，改成自己积分：

- **横向：高阻尼** `dust.horizontalDamping`（默认 **0.75**）——尘土不滑行，几刻内就停在原地，"拖出一条带"完全来自源在移动 + 不停重新撒点；
- **纵向：只有重力**（`dust.gravity` 默认 0.08，履带用同一套），**没有托举项**，所以离开车体的尘只有下沉；
- **尺寸几乎不扩散**：出生多大就多大（只长 1.1×），成片感交给"大而淡的重叠"；
- **透明度低**（履带 0.35 / 火炮地面 0.30 / 车体 0.20），末段 40% 平滑淡出；
- **寿命**：履带 30~50（页面两项，平均 40）、火炮地面 **60**、车体 40。

生成方式：

- **履带**：只垂直上抛，速度在 `upSpeedMin`~`upSpeedMax`（0.06~0.16）小范围随机，做出高高低低的质感；**撒点半径 1.5 格**；
- **火炮地面**：初速按"离炮口距离"衰减 `(1-d/R)^1.5`（越近越快，边缘几乎为零），方向是离炮口的径向外 + 一点沿炮管向下的压；**撒点半径 2.5 格**（`scatter` 可调）；
- **车体**：同一套衰减与下沉，撒点 1.2 格，更淡更短。

## 2. 两个绘制层（各自开关）

同一个贴图画两遍：

- **贴地层**：四边形躺在世界水平面上（每个粒子随机朝向），这才是"落在地表的土"的观感；缺点是低视角会变薄；
- **面向相机层**：现在这种，负责低视角仍然可见；
- **两层同时开时，各按 0.6× 透明度绘制**，所以关掉其中一层不会让画面变淡一半；
- 开关在**客户端**配置：`[gpu] dust.groundPlane` / `[gpu] dust.cameraFacing`（默认都开）。
  放客户端是因为它们是"本机怎么画"，和别人的客户端无关；车体尘也画两层。

## 3. 反冲（新）

新增 `SableRecoil`，开炮时执行（`tankCannon.recoil.*`）：

```java
RigidBodyHandle.of(hull).applyImpulseAtPoint(shellDirection.scale(-mass * deltaV), muzzleWorld);
```

- **施力点 = 炮口世界坐标**，**方向 = 炮弹飞出的反方向**，所以既有平移反冲也有抬头的力矩；
- **谁是车体**：炮口所在物理体 → `SubLevelHelper.getConnectedChain(...)` 连通簇 → **取包围盒体积最大的那个**（`boundingBox().volume()`）。**完全不看质量**，理由就是你说的：MC 方块质量不现实，碰撞盒才现实；
- 炮口伸出车体（不在任何包围盒内）时，退回到"最近的物理体"；**完全不是物理体（世界里的固定炮）→ 跳过**；
- 配置写成**速度变化 Δv** 而不是裸冲量：`recoil.velocity` 默认 **0.02 格/刻**，代码里 `冲量 = 车体质量 × Δv`，所以换车不用重调；
- 不随装药缩放；`recoil.enabled` 默认开，出问题可以关。

## 4. 新增/改动的配置键

```toml
[dust]                              # 所有扬尘共用
	horizontalDamping = 0.75
	gravity = 0.08

[generators.dust]
	upSpeedMin = 0.06      # 履带垂直初速的小范围随机（替代原来的 upSpeed）
	upSpeedMax = 0.16
	alpha = 0.35
	lifetimeMin = 30
	lifetimeMax = 50

[tankCannon.groundDust]             # 火炮地面：更宽、更扁、更久
	scatter = 2.5          # 撒点半径
	damping = 0.75
	gravity = 0.06
	alpha = 0.3
	lifetime = 60

[tankCannon.hullDust]               # 车体：更紧、更淡、更短
	scatter = 1.2
	damping = 0.75
	gravity = 0.08
	alpha = 0.2

[tankCannon.recoil]
	enabled = true
	velocity = 0.02        # 车体被推回的速度变化（格/刻）
```

老的 `generators.dust.upSpeed`、`tankCannon.groundDust.upSpeed` 已不再使用（键还在，忽略即可）。

## 5. 这次要你验的

1. 扬尘是否有"缓慢下沉、贴地、几乎不变大、淡淡一片"的观感（不对就调 `damping` / `gravity` / `alpha` / 寿命）；
2. 两个绘制层：哪个该留（`[gpu] dust.groundPlane` / `dust.cameraFacing`）；
3. 反冲方向与力度：炮一响车身是否有后退+抬头（方向反了就是符号问题，太小就加 `recoil.velocity`）；
4. 光照接管仍等你测 0.9.11 的结果（看 `first=` 坐标与白光是否随区块重建消失）。
