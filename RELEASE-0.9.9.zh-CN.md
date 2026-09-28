# 0.9.9-alpha：按你发的日志定位到的两个真正原因

你那份日志一次说清了两件事，两个都是坐标/光照引擎层面的硬问题，不是调参问题。

## 1. 生成器：发射点算在了物理体空间，不是世界空间

日志：

```text
CBC Muzzle Light generators: found=5 ... spawned=0
  blocked=[... no viewer within 48.0, ...]
```

发现是好的（`found=5`），但每个生成器都被"48 格内没有玩家"挡住——**玩家当然不在**，因为算出来的发射点是**物理体空间**的坐标，不是世界坐标。之前"找不到地面"也是同一个原因：射线在一个跟真实世界无关的坐标系里往下打。

炮口那套效果一直是对的，因为它用的是 **CBC 自己的坐标变换**（`CBCCompatTransformers.transformVec3`），而不是我们给生成器用的 anchor。这版把生成器也改成同一套：

- 发射点：体空间点 → `CBCCompatTransformers.transformVec3` → 世界点；
- 引擎烟的喷出方向：同一变换下"原点+方向"的点减去原点，再归一化（体空间方向 → 世界方向）；
- 诊断行里打印的位置也改成世界坐标，方便你核对。

预期：日志里 `no viewer within 48.0` 消失、`spawned` 变成非 0，扬尘出现在车下的真实地面上。如果那次你人确实离坦克超过 48 格，仍然会被挡（`generators.cullDistance` 默认 48，可以调大）。

## 2. 接管：接管执行了，但重算被光照引擎忽略

日志：

```text
CBC Muzzle Light: source vanilla light taken over (1 block(s), tier Advanced)
```

说明代码走到了、层级也对（Advanced）、也认出 1 个光源方块——**问题是那次"重算"没有生效**。原因：客户端光照引擎里，这个区块被服务端标记为"没有光照数据"（那块本来是亮的，但服务端把它当作普通光照数据发下来后，客户端没有可重算的节点），所以单独一次 `checkBlock` 会被跳过。

这版按 Legacy 注入路径里的同一招处理：**先把该 section 标记为"活跃"（`updateSectionStatus(section,false)`），再 `checkBlock`**。日志也加上了目标方块坐标：

```text
CBC Muzzle Light: source vanilla light taken over (1 block(s), tier Advanced, first=49, 65, 97)
```

请把这一行再发我一次，两个信息很关键：

- **`first=` 那个坐标是不是那盏灯所在的方块**？不是的话说明我们认出来的光源位置不对（那就是注册时的位置问题，不是接管问题）。
- **白光是否消失**？如果坐标对、但白光还在，那就只剩一条路：**不在发光查询上动手，改在补丁着色器里把"光源自己那一格的原始方块光"减掉**（Advanced 层级可以做到，代价是那一格附近的原生方块光会被压掉，需要限定很小半径）。这条我可以做，但会动补丁文件，所以说一声再动。

## 3. 其它仍然有效

- 粒子：`cbcmuzzlelight:ground_dust`（CBC `debris_smoke` 的 12 帧资产 + 自写控制程序）、履带上抛/落地停留、火炮贴地移动、车体与地面不同的水平拉伸范围、颜色不染色=debris_smoke 本色。
- 引擎烟：方向相对物理体、不继承物理体速度、新增高度偏移 `offsetY`；默认原版 campfire 烟（偏白）。
- 版本 `0.9.9-alpha`，协议串仍是 `"6"`，两端都要换。
