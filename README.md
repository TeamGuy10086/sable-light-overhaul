# Sable Light Overhaul

给 **Create: Big Cannons** 及其附属的动态光照、粒子与阴影。前身叫 *CBC Muzzle Light*。

> **实验性。** 当前 `0.1.0`。测试期只递增第三位（`0.1.1`、`0.1.2` …），
> **在明确设基线并 release 之前不会升到 `0.2`**。绝大多数功能**尚未实机验收** ——
> 状态与待测清单见 [`HANDOFF.zh-CN.md`](HANDOFF.zh-CN.md) §9。

## 做什么

重点是让炮火、射灯和随身光源**真正参与光影包自己的光照阶段**，而不是浮在画面上的一层叠加。

- 炮口闪光 / 曳光弹的**动态光照**与**尾迹**
- 爆炸：闪光 + 冲击波（服务端测量 → 客户端绘制）
- 射灯、随身手电筒（含 48 格内**他人手电筒**同步）、登记式静态光源
- AP 弹命中与跳弹火花、扬尘、迪斯科灯球
- **阴影**：屏幕空间射线；名额按「灯的衰减球 ∩ 视野锥」分配（不是按距离），
  软/硬边与采样次数**每盏灯可配**，且客户端有最终否决权
- 计划中：翼面涡流

## 四档光照模式（客户端 `lightingMode`）

| 档位 | 实现 | 需要光影补丁 | 网格重建 |
|---|---|---|---|
| `Legacy` | 原版方块光传播 | ❌ | 有 |
| `Compatible` | Lamb 区域光 | ❌ | 有 |
| `DynamicOnly` | 屏幕空间叠加，任意光影通用 | ❌ | 无 |
| `Advanced` | 注入光影包的光照阶段，**出颜色、有阴影** | ✅ | 无 |

`Advanced` 档要求光影包带上补丁，见 [`patch/README.zh-CN.md`](patch/README.zh-CN.md)。
**`Advanced` 但补丁没装 = 完全不发光**，请退回 `DynamicOnly`。

## 构建

> **前提：本仓库不含任何第三方 jar。** `.gitignore` 忽略 `libs/**/*.jar`，所以
> **克隆下来不能直接构建** —— 先把依赖从游戏实例里抽出来。

```powershell
# 1) 从游戏实例的 mods\ 抽依赖 jar（只需一次；依赖版本变化时重跑）
#    它逐个校验 SHA-256 并与被检视过的那几份构建比对，对不上会直接报错
.\prepare-libs.ps1 -CbcJar       '<实例>\mods\createbigcannons-...jar' `
                   -SableJar     '<实例>\mods\sable-neoforge-...jar' `
                   -LambJar      '<实例>\mods\lambdynamiclights-...jar' `
                   -CreateJar    '<实例>\mods\create-...jar' `
                   -YuushyaJar   '<实例>\mods\yuushya-...jar' `
                   -CopycatsJar  '<实例>\mods\copycats-...jar' `
                   -CopyfoxiesJar '<实例>\mods\copyfoxies-...jar'
                   # -CbcatJar 可选

# 2) 构建：Java 21 / NeoForge 21.1.228 / Minecraft 1.21.1
.\gradlew.bat build      # → build/libs/sable-light-overhaul-<version>.jar
.\gradlew.bat test       # 133 个测试
```

> **已知限制**：`prepare-libs.ps1` 要你手动传 8 个 jar 路径，且**逐个比对 SHA-256**，
> 所以这份代码实际上**只能在那台装了特定整合包的机器上构建**。
> 让它像 `patch/deploy-to-instance.ps1` 那样自动探测实例，是待办项。

## 部署到游戏实例

```powershell
& .\patch\deploy-to-instance.ps1 -Check                             # 只看不改，退出码 0 = 一致
& .\patch\deploy-to-instance.ps1 -Instance '<版本目录>'              # 真正部署
```

它把 jar 与光影补丁都对齐到仓库产物，并**清掉旧档名的 jar** —— 同 mod id 留两份，游戏起不来。

> **改了 GLSL 必须让 Iris 重新加载光影包**（在列表里重选该包，或重启游戏）。
> **F3+R 不重载 shader。**

## 文档

| 文件 | 内容 |
|---|---|
| **[`HANDOFF.zh-CN.md`](HANDOFF.zh-CN.md)** | **主文档，读这一份就够**：架构、第三方 API 逆向事实卡、配置地图、诊断日志、踩过的坑、待办队列 |
| [`patch/README.zh-CN.md`](patch/README.zh-CN.md) | Photon 光影补丁：安装、uniform 协议、阴影参数、局限 |
| `RELEASE-*` / `*-PLAN` / `GPU-VERIFY` / `SPOTLIGHT-*` 等 | **历史文档**，可能已过时，保留作记录 |

## 命名

项目叫 **`sable-light-overhaul`**，但 **mod id 仍是 `cbcmuzzlelight`**
（Java 包 `dev.cbcmuzzlelight`、资源命名空间、配置文件 `cbcmuzzlelight-*.toml`、`SavedData` 键同理）。

这是**故意的**：改它们会让现有存档里的方块与物品全部失效。计划在设基线时统一改名，
并附带配置与存档数据的迁移。

## 许可

MIT，见 [`LICENSE`](LICENSE)。
