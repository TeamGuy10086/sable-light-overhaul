# Sable Light Overhaul

[English](README.md) | **简体中文**

面向 Minecraft 1.21.1 / NeoForge，为 Create: Big Cannons 提供动态光照、火炮特效和远距补绘。

SLO 包含可配置的炮口烟雾、扬尘、曳光、爆炸、照明弹和灯具，提供专用光影适配及无光影光照后端。通过 **F9** 管理特效、装置和预算；界面、物品名称和描述支持中英文。

**当前开发构建 0.1.716。** 公开发行编号与游戏显示的内部版本可以不同。

## 主要功能

- **炮口烟雾和扬尘**：交错的热烟与冷烟、逐渐扩散和边缘侵蚀、按装药缩放的大炮烟、坦克炮口烟与地面扬尘、轻量机炮薄烟。大炮和机炮拥有独立可调烟雾预算，超额时淘汰最早的效果。
- **曳光和爆炸**：支持远距补绘的短亮线，在适配管线中接受场景、云和烟雾遮挡，处理命中终止与网络平滑；高射炮爆点使用自定义烟雾、火花和冲击波。
- **照明弹、信号弹和高度引信**：CBC 照明炮弹与 RGB 信号炮弹，燃烧时间和亮度可逐弹保存；燃烧时缓降并具有远距辉光。
- **照明装置**：可转动灯头的射灯、手电筒和隐藏点光源，可配置光束、辉光和偏移；支持的船载路径使用船体参照变换。
- **Sable Hostile 集成**：逐炮型特效配置、远距炮弹和曳光补绘、炮声及炮塔转动音效。兼容的 Sable Healthbar 可提供数学弹道和原生实体接力。
- **远景生物标记**：专用服务器上，客户端视距之外的生物可显示为受遮挡的分类线框、名字或可取得的头像，并可显示血量。范围受服务端视距及请求距离限制，不额外加载地形，也不是完整实体模拟。
- **全局风场**：固定或缓慢变化的风向、风速，作用于已接入风场的粒子效果。

## 爆炸特效

- **地爆**包含由小到大、上升并逐渐卷散的火球、贴地冲击尘和可配置的外张尘柱。**地下爆炸**按实际出口喷发，浅埋火球较小。**空爆**使用更小的火球，细火花向全方向抛射。导弹拦截有独立的特效路径。
- 自定义弹着粒子从威力 6 起触发。MW 中炮 HE 的方块爆炸威力是 6，CBC 普通 HE 是 8。原版 TNT 使用自己的粒子，也可以绘制 SLO 网格冲击波。
- **F9 → CBC 高爆视觉**可以调地爆火球的上升与膨胀、尘柱形态，以及是否按实际移除方块数缩放尘柱高度。无光影和已打补丁的 Photon Advanced 共用炮口烟质量滑槽的调节方式。
- 本构建的 Photon 补丁包含缓存冷烟受光和爆炸烟雾材质的更新，请与模组配套使用。Photon 的薄片云偶尔会透过烟雾，目前仍缺少稳定的重现场景。

## 环境与安装

- Minecraft **1.21.1**、Java **21**、NeoForge **21.1.228 或更新版本**、Create: Big Cannons **5.11.7** 及其必需依赖。
- F9 配置界面需要 **LDLib2 2.2.17 或更新版本**。Voxy、Iris、Sable Hostile、Sable Healthbar 等集成需使用兼容版本，本项目不捆绑这些模组。
- 将 SLO JAR 放入 `mods` 并替换旧版，不要同时保留多个 SLO 版本。
- 使用服务端同步功能时，专用服务器与客户端都应安装匹配的 SLO 版本及依赖。
- 在 **F9 → 光源 → 光照后端** 选择模式。

| 后端 | 使用场景 |
| --- | --- |
| **SLO Only** | 不开启光影包，保留 SLO 光照、特效及支持的 Voxy 远景补光与遮挡。禁用体积光，折射默认关闭；材质补光仍是近似，并非完整重建原版光照贴图。 |
| **屏幕空间近似** | 包括未适配光影在内的近似补光回退，兼容性和遮挡质量取决于当前管线。 |
| **Advanced** | 使用已适配且打过匹配 SLO 补丁的光影包。 |
| **Off** | 排查问题时关闭光照后端。 |

## 光影适配

请从原作者处下载原包，再使用[发行页面](https://github.com/TeamGuy10086/sable-light-overhaul/releases)的对应 **Windows x64 离线补丁工具**。SLO 不提供完整光影包下载。

| 补丁工具 | 支持的原包 |
| --- | --- |
| Photon | [voxy-support 固定快照 ba11ae2](https://github.com/sixthsurge/photon/archive/ba11ae2ad9d506c3a2fd9ca10c317be259b94d69.zip)，作者 [SixthSurge](https://modrinth.com/shader/photon-shader) |
| Complementary | [Unbound r5.8.1](https://www.complementary.dev/shaders/)，也支持通过 Euphoria 自身安装器生成的 [Euphoria Patches 1.9.3](https://www.euphoriapatches.com/) 版本 |
| Mellow | [v3.5](https://modrinth.com/shader/mellow/version/3.5)，作者 TheCMK |

选择原版 ZIP 或解压目录，指定新的输出 ZIP。工具校验源码指纹，拒绝不支持或已修改的输入，并校验输出；修改文件名不能绕过版本检查，不覆盖原包或已有输出。命令行使用及重新构建见[补丁工具说明](tools/patcher/README.md)。

在 Iris 中选择生成的包并启用 **Advanced**。补丁成功表示文件校验通过，不代表显卡已通过 GLSL 编译。这些适配并非上游官方版本；Mellow 的 SLO 光源阴影因残余伪影默认关闭。Photon 与 Complementary 分开调校，不应直接套用参数。

## 从源码构建

使用 Java 21 和 Gradle Wrapper。第三方模组二进制不随仓库分发；请按 [libs/README.txt](libs/README.txt) 准备编译和测试依赖。测试也会检查可选集成的二进制契约。

部分着色器契约测试需要本机准备 Photon 原包及生成文件，无需将上游源码提交到仓库：

```powershell
python tools/prepare-shader-tests.py path/to/photon-original.zip
./gradlew.bat test jar
```

Windows 下执行 `build` 时，只有显式设置 `SLO_INSTANCE`，或在被忽略的 `local-deploy.json` 中设置 `instance` 路径，才会自动部署到测试实例；游戏运行时会跳过。本仓库不提供作者个人实例配置。

内部研究记录、维护文档、部署记录、光影原包及生成的完整包保留在本地，不纳入公开仓库。

## 问题反馈

请使用 [GitHub Issues](https://github.com/TeamGuy10086/sable-light-overhaul/issues)，提供 SLO 内部版本、Minecraft/NeoForge/Iris/Voxy 版本、光影名称与准确版本、光照后端、复现步骤及相关日志或截图。说明无光影是否复现，以及单人还是专用服务器环境；发布日志前去除凭据和私人服务器地址。

## 许可与署名

SLO 模组与 patcher 的自有代码采用 [MIT](LICENSE)。第三方光影、衍生补丁和运行时组件保留各自协议，参见[补丁许可说明](tools/patcher/licenses)。光影下载归原作者提供，patcher 不赋予任意分发完整修改包的许可。
