# 0.7.1-alpha 修正测试版

修复已确认错误：Sable LevelPlot.getChunk 接收 plot 相对区块索引，0.7.0 错传存储坐标，导致已加载船体光源被错误过滤。现在先调用 plot.toLocal。
CBCAT 双管炮的发光源现在经过 CBCCompatTransformers 转换位置和方向，再执行世界距离过滤与发送；已是世界坐标时保持原路径的空间含义。普通 CBC 沿用已有转换。
诊断日志新增 samples 和 firstSample，以便定位源坐标错误或世界光源被过滤。

替换旧版 JAR，保留配置。若测试多人服务器上的 CBCAT 载具炮，服务器也需要更新本模组（发送坐标修正发生在服务器）。仅客户端桥接修正不需要服务端更新。
在 cbcmuzzlelight-client.toml 的 [sableBridge] 下将 debugLogging = true，重启客户端后测试。
先关闭光影，分别测试：地面炮->地面；地面炮->船体；普通 CBC 载具炮->地面；CBCAT 载具炮->地面；载具->另一载具。之后开启光影对照。
无效时提供 latest.log 中 CBC Muzzle Light bridge / Sable bridge disabled 相关行，以及炮型和无光影对照结果。日志含光源位置坐标。

38 项检查通过（含两个新回归检查），尚未进行游戏内验证。不能据此宣称所有失效原因已解决。普通 CBC 载具炮照不到地面的根因仍待运行证据；船体活动机械部件、Colorwheel/Photon 表现待实测。仍只投射本模组炮火，不桥接火把；未加载内部区块跳过、不创建额外区块。基线未修改。

源码构建与其余配置见项目 SABLE-BRIDGE.zh-CN.md。
