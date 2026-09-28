# CBC Muzzle Light 0.8.2-alpha

Minecraft 1.21.1 / NeoForge。修正机炮光场方向，并拆分三个独立的光照配置。本版构建及 65 项自动检查通过，尚未实机验证。0.8.1 的双管独立闪烁已由用户确认，本次保留。没有新增剔除算法。

## 更新与配置

建议客户端、服务端都更新至 0.8.2。协议仍为 4，服务端发送结构与 0.8.1 相同。Lamb / CBC / CBCAT / Sable / Create 依赖不变，Advanced 默认启用；Legacy 为兼容回退，None 禁用本模组效果。保留已使用的 Lamb，不重新启用 Sodium Dynamic Lights 及其旧桥接。

退出游戏，备份旧 config/cbcmuzzlelight-client.toml，然后使用发布包中的同名文件替换。也可以移走旧文件，由游戏自动生成新默认值。新结构不按旧键逐项自动迁移；本次提供的用户配置数值已经尽量编入默认值。项目 examples/user-input-0.8.1.toml 保存了原文件的完整副本。

## 光场形状

机炮使用沿炮轴定向的棱形/八面体距离场：炮口是峰值位置及最大横截面，向前逐渐收窄，侧面与前方组成十字星/菱形轮廓。前向不是探照灯式扩张。默认前向 32、后向 16、侧向 16、上下 16 格；后方在 12 格处截掉尾部，保留一部分后向散光。rearClipDistance=0 可去掉全部后半部分，设为不小于 rearRange 则不裁剪。此裁剪是对炮口遮挡观感的近似，不读炮身形状、不提供真实阴影。

大炮两阶段均使用独立的非对称椭球光场。前后距离可以不同，峰值仍在炮口/热烟雾质心，不会因为前向距离较长而把最亮处移到前面。每个阶段都有前、后、侧、上下四种距离。侧向和上下轴依据炮轴及世界竖直方向构造，不跟随载具绕炮轴滚转；侧向/上下相同时这一点不影响形状。

双管环境光继续按实际开火炮管交替，各管独立寿命。重叠光照取最大值，不超过原版光照 15。闪烁仍受客户端 tick 与网格刷新限制。

## 新配置结构

| 分组 | 用途 |
| --- | --- |
| lightingMode | 顶层 Advanced / Legacy / None |
| visuals.autocannon | 可见枪焰尺寸、长宽比、透明度、寿命 |
| advanced.autocannon | 机炮照明 |
| advanced.bigCannon.blast | 大炮最初的炮口爆燃照明 |
| advanced.bigCannon.smoke | 大炮随后跟随热烟雾的照明 |
| advanced.performance | 区域数、接收区域数和日志 |
| tracking | 热烟寿命比例、粒子跟踪数量及后备速度 |
| legacy | 原后端布局、时间曲线、预算、Sable 桥接 |

每个 advanced 光照分组下有四个子分组：

- shape：forwardRange、rearRange、sideRange、verticalRange 分别是从光源到零亮度边界的距离，横向是半径。rearClipDistance 控制后部裁剪。
- spatial：coreFraction 是恒亮核心比例；middleFraction 是核心以外剩余距离中，中段所占比例；tailBrightness 是尾段起点亮度。尾段斜率被限制得比中段更缓。机炮和大炮两阶段三套数值完全独立。
- time：durationTicks、peakHoldTicks、minimumBrightness、decayPerTick。每 tick 减去初始亮度的指定比例；空间核心恒亮不影响时间衰减。每阶段有自己的时钟。
- scaling：referenceSize 为基准尺寸，maximumRange 为缩放后每方向上限。大炮 blast 的尺寸输入是 CBC 有效装药强度，smoke 的尺寸输入是热粒子 getQuadSize(0) 的热度加权平均；没有捕获到粒子时用原生 plume size / 4。机炮尺寸固定，referenceSize 不使用，maximumRange 仍生效。

爆燃持续 blast.time.durationTicks 后进入烟雾跟随阶段；烟雾阶段的时间从零开始。默认爆燃 2 tick，烟雾最长 28 tick，合计 30 tick；烟雾冷却或失踪仍可提前熄灭。阶段切换不做插值，若两组亮度/范围相差很大，视觉上会有跳变。调整持续时间建议停止射击后再改；已有炮火记录的总寿命与阶段分界在接收事件时确定。

## 从当前配置还原的默认值

可见枪焰：尺寸 9、长度 .5、宽度 2、透明度 1、寿命 3 tick。

机炮：范围 32/16/16/16，照明寿命 5 tick、保持 1 tick、每 tick 衰减 .5。旧 autoConeHalfAngle=60 不迁移，因为扩张圆锥已经停用；后裁剪 12 格是新的可调近似值。

大炮：两个阶段先各自填入前25/后8/侧12/上下12；装药基准与烟雾尺寸基准都为 2，上限64。保留 .02 的时间衰减、最低亮度0；原先 30 tick/保持4 拆为爆燃2 tick/保持2、烟雾28 tick/保持2，默认阶段亮度连续。现在可以分别修改两阶段范围与衰减，不再共用。

三套空间曲线先保留核心 .25、中段 .55、尾段 .2；这是相同初始值，而非参数联动。真实形状已变化，旧观感不能完全按数字复原。

保留区域预算32、接收区域64、Legacy源预算256、投影预算96、跟踪8粒子、热期比例.6及原Legacy布局。扩大范围仍会增加网格重建开销；本次没有性能剔除优化。

## 验证

1. 无光影、夜晚，机炮朝平地/墙面开火，观察最大横截面位于炮口、前端收窄，以及双管交替。
2. 单独调机炮 spatial.coreFraction，确认大炮不受影响；分别调 blast 与 smoke 的 shape.forwardRange，用慢放区分最初爆燃和随后跟随。
3. 同炮、同弹种，用安全的不同装药强度比较爆燃范围；检查热烟扩散、移动、冷却后的跟随与熄灭。
4. 回归世界/物理体全部方向、移动载具、Flywheel 部件、None 清理，再启用现有光影对比。

不修改 Photon，不新增 Voxy 支持，不承诺通用 HDR 或动态阴影。Legacy 原传播算法和原布局仍为兼容方案。

## 构建

Java 21。运行 prepare-libs.ps1，提供 CBC 5.11.7、CBCAT 0.1.4c、Sable 2.0.3、Lamb 4.8.11、Create 6.0.10 对应 JAR，然后 gradlew.bat build。完整项目不含第三方依赖及缓存；包含原用户配置、生成的新默认配置与测试结果。
