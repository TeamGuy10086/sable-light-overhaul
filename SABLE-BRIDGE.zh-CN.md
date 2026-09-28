> 最新修正：请先阅读 FIX-0.7.1.zh-CN.md。

# 0.7.0-alpha：Sable 光照桥接实验版

针对本机提供的 Sable 2.0.3 编译验证；未进行游戏运行验证。36 项自动检查通过，其中 3 项检查实际 Sable 二进制契约，其余为既有回归检查。自动检查不能证明光照视觉效果或性能。

替换旧版 cbc-muzzle-light JAR，不要同时加载两个版本。原有客户端配置继续使用；新配置项由 NeoForge 补齐。没有修改基线、Photon 光影包或原生炮口视觉。网络协议保持不变。

新增 cbcmuzzlelight-client.toml 配置：
```toml
[sableBridge]
enabled = true
maxProjectedSources = 96
debugLogging = false
```
这是主世界原有光源预算之外的投射预算。掉帧时可先改为 32。关闭 enabled 可回退到原来的主世界照明。为得到可复现结果，编辑配置后重启客户端。

实现：从本轮世界光源快照查询相交物理体，通过 logicalPose 逆变换到 plot 坐标，仅注入已加载 plot 区块；与原光源一起进行亮度合并、旧位置清理及周期刷新。每 tick 重新计算物理体姿态，不依赖 500ms 环境采样。无 Sable 时不调用桥接类；接口异常会记录错误并在本次进程内关闭桥接，保留地形光照。不放置方块、不创建物理体区块、不打包 Sable。

验证顺序：
1. 夜晚关闭光影，用地面固定机炮朝紧邻船体开火，比较 enabled 开关；注意同时检查地面光。
2. 移动、旋转载具经过固定炮火；观察光照是否跟上，停止开火后是否消失。
3. 在载具上测试普通大炮、普通机炮、CBCAT 左右双管；分别看船体与地形。如果只在载具上发射时异常，可能属于炮口源坐标问题。
4. 开启现有 Photon 再重复。普通船体方块与 Create 活动机械部件分别观察，Colorwheel/Flywheel 路径尚未实测。
5. 两艘载具、持续扫射、移出范围、卸载载具、切换世界；检查残光和帧时间。

若无效果：开启 debugLogging 连续开火，在 latest.log 搜索 “CBC Muzzle Light bridge”。world>0/projected=0 表示未产生可用投射（没有附近物理体、内部光源位置未加载等）；projected>0 但船体不亮则继续查传播/渲染刷新。报错信息以 “Sable bridge disabled” 标识。请同时提供无光影对照结果。

限制：第一版跳过投射位置未加载的区块，没有边界补光；炮口远离船体或靠区块边缘时可能漏光。按方块取整可能造成移动时的亮度台阶；非单位缩放的光照范围不保证物理一致。跨世界地形和其他物理体的遮挡未做统一计算。上限仍是原版 15 级，不保证任意光影或 Colorwheel 的表现。不包含 Voxy 光照适配。0.7.1 已补 CBCAT 源坐标转换，运行效果待测。

构建：Java 21，先运行 prepare-libs.ps1 -CbcJar <CBC5.11.7> -SableJar <Sable2.0.3> -CbcatJar <CBCAT0.1.4c>，再 gradlew.bat build。脚本校验 JAR 并从 Sable 内提取 companion 1.6.0 编译依赖；依赖不随发布包分发。

Sable SHA256: da6c3b66238586603d1dcaa2afb012d36815fbce0a2d5938fbb2936701d42279