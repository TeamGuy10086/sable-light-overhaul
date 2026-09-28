# 生成器配置页面（Synaxis 风格）方案对比

你的要求：两个生成器都能打开**Synaxis 风格的配置页面**，参数让玩家自己填。我去把 Synaxis 的资产和类挖了一遍，结论如下。

---

## 1. 我查到的事实

| 发现 | 位置 |
|---|---|
| Synaxis 的页面**不是自己画的**，底层是 **LDLib2**（`ldlib2-neoforge-1.21.1-2.2.17-all.jar`，6.4 MB，已在你包里） | `com.lowdragmc.lowdraglib2.*` |
| 打开页面的机制是 LDLib2 的标准设施：方块实现 `BlockUIMenuType.BlockUI`，服务端调 `BlockUIMenuType.openUI(player, pos)` | `BlockUIMenuType.openUI(ServerPlayer, BlockPos)` / `BlockUI#createUI(BlockUIHolder)` → `ModularUI` |
| Synaxis 在 LDLib2 之上叠了一层**声明式页面**：`UiSchema.builder().page(...).group(...).text(key).number(key).toggle(key).option(key).vector3(key).action(id)` | `foundation/ui/UiSchema`、`UiPage`、`UiControlType` |
| 可用控件类型就 7 种：`READONLY / TEXT / TOGGLE / NUMBER / OPTION / VECTOR3 / ACTION` | `UiControlType` |
| 页面还带**状态同步层**：`StateKey` / `StateStore` / `BlockEntitySync` / `UiSessionManager` / `NetworkBlockEntityAccess` | `foundation/state/*`、`foundation/ui/session/*` |
| 需要复杂页面时 Synaxis 也会**直接用 LDLib2 控件手搓**（`Label`/`TextField`/`Toggle`/`Selector`/`Button`/`ScrollerView`/`ItemSlot` + taffy 布局） | `RedstoneSettingsUiFactory` |
| GUI 贴图：`synaxis:textures/gui/simple_background_{one_line,5_6,large}.png`、`tab_bar.png`、`icons10x10.png`；LDLib2 自己那套是 `background/bordered_background/button/icon/*` | 两个 jar 的 assets |

**最容易踩的坑**：`BlockUI` 必须由**方块类**实现，而我们的方块类要是静态 `implements BlockUI`，那么**没有 LDLib2 的整合包会在注册方块时直接崩**（类加载失败）。所以下面的 A 方案里我用"条件式子类"来绕。

---

## 2. 三种做法

### A. 直连 LDLib2（推荐）

- 方块在 LDLib2 存在时用**子类**注册：`GeneratorUiBlock extends GeneratorBlock implements BlockUI`；注册 lambda 里 `ModList.isLoaded("ldlib2") ? new GeneratorUiBlock(...) : new GeneratorBlock(...)`。`new GeneratorUiBlock(...)` 这条指令只有走那个分支才会被 JVM 解析 → **没装 LDLib2 就不会加载那个类，不会崩**（沿用我们 Iris/Lamb/CBCAT 的软依赖思路）。
- 页面用 LDLib2 控件手搓（`Label` + `TextField` + `Toggle` + `Selector` + `Button` + taffy 布局），**观感与 Synaxis 页面完全同源**（同一个库、同一套底色/边框/按钮）。
- 依赖：**LDLib2（软）**。需要把 `ldlib2-*.jar` 放进 `libs/` 编译（`prepare-libs.ps1` 加一项哈希校验）。
- 成本：中。LDLib2 是 Kotlin 写的，Java 侧要用它的构造器/`*Spec` 构建器；Synaxis 的 `RedstoneSettingsUiFactory` 正好是一份**可照抄的 Java 参考实现**。
- 风险：LDLib2 版本更新可能改 API → 因为门控在，最坏情况是"页面不开"，不会崩。

### B. 直接吃 Synaxis 的 `UiSchema` + 状态同步

- 我们的方块实体实现 `NetworkBlockEntityAccess` + `NetworkBlockEntitySupport`，声明若干 `StateKey`，然后用 `UiSchema.builder()...` 描述字段；Synaxis 负责渲染、分页、标签、权限、状态同步。
- 代码量最小（字段是声明式的），观感 100% 一致。
- 依赖：**Synaxis（软/硬）+ LDLib2**。
- 风险最大：这是 Synaxis 的**内部框架**（它的 `api` 包里只有控制椅，没有 UI 扩展点），而且 `NetworkBlockEntitySupport` 可能依赖它自己的包注册/会话注册；Synaxis 一更新就可能碎，而且我们会被迫跟着它的状态模型走。

### C. 纯原版自绘（零依赖兜底，也能当主方案）

- 自己做 `AbstractContainerMenu` + `Screen`：面板用 `GuiGraphics.fill` 画（**颜色直接从 Synaxis 的 GUI 贴图取色**），标签页/复选框/滑块自己实现，粒子字符串用原版 `EditBox`。
- 依赖：**无**。任何整合包都能用，永远不会因为别人更新而碎。
- 成本：中偏高（要自己写控件），观感约 90% 接近（Synaxis/LDLib 本身就是"平底色 + 细边框"，很好仿）。
- 适合当 A 的**兜底**：LDLib2 不在时自动退回这个简单版。

---

## 3. 我的建议（已定稿）

**A 为主 + C 为兜底**：装了 LDLib2（也就是装了 Synaxis 的包，比如你现在这个）就开 Synaxis 同款页面；没装就退回一个简版原版页面，功能不缺。B 已排除——反编译证实 Synaxis 的同步层是它自己的框架（`StateStore`/`BlockEntitySync`/`SynaxisPackets`），复用等于绑死在内部 API 上。

> **实施细节见 `LDLIB2-NOTES.zh-CN.md`（已核实、照做即可）**。两个关键更正：
> 1. 方块类**不需要**实现 `BlockUI`：自注册一个 `MenuType` + `ModularUIContainerMenu` + `IContainerUIHolder`，同步管线完整保留，LDLib2 仍是纯可选依赖（原方案里的"条件式子类"不用了）。
> 2. 构建先要解决一个坑：`taffy`/`yoga` 是 LDLib2 的 **jar-in-jar**，必须像 flywheel 那样从 `META-INF/jarjar/` 抽出成独立 `libs/*.jar` 才能编译。

---

## 4. 页面里放什么（我打算这么列，你增删）

参数**从 TOML 挪进方块实体**（每个生成器各自一份，用配置项当新方块的默认值）：

**扬尘生成器**
| 字段 | 控件 | 说明 |
|---|---|---|
| 粒子 | TEXT | 直接粘 `/particle` 里那段 SNBT，如 `minecraft:dust{color:[0.55,0.5,0.42],scale:1.4}` |
| 启用 | TOGGLE | |
| 出尘率 `rate` | NUMBER | 与水平速度相乘 |
| 单次上限 `maxPerScan` | NUMBER | |
| 搜索深度 `searchDepth` | NUMBER | 向下找地面的最大格数 |
| 最小速度 `minSpeed` | NUMBER | 低于它不出尘 |
| 寿命 min/max | NUMBER ×2 | |
| 尺寸 `size` | NUMBER | |
| 颜色取自地面 | TOGGLE | 仅对 `minecraft:dust` 有意义 |
| 偏移 | VECTOR3（或只读显示 + 提示用扳手） | plot 局部的 X/Z |

**引擎烟生成器**
| 字段 | 控件 | 说明 |
|---|---|---|
| 粒子 | TEXT | 同上（默认给个不亮的，如 `minecraft:large_smoke`） |
| 启用 | TOGGLE | |
| 红石 15 时的间隔 / 1 时的间隔 | NUMBER ×2 | 决定浓淡曲线 |
| 红石 15 时的数量 / 1 时的数量 | NUMBER ×2 | |
| 上升速度 `riseSpeed` | NUMBER | |
| 继承速度 `inertia` | NUMBER | 车开快了烟被甩在后面 |
| 寿命 / 尺寸 | NUMBER ×2 | |
| 偏移 | VECTOR3 | |

可选再加一个 **ACTION 按钮"试喷一次"**（立刻在炮口/履带位置喷一发，方便调参）——你说过"先确定功能、不加额外东西"，要不要加你定。

---

## 5. 实现顺序（选定后）

1. 先把 A 的**最小闭环**做出来：一个方块 + 一个页面，页面上只放一个文本框和一个按钮，实机确认"扳手/空手右键能开页面、改完能存进方块实体"。
2. 再补字段与控件、粒子字符串解析与校验（复用 `/particle` 的 `TagParser` 路径）、数字范围钳制。
3. 最后做 C 的兜底页面（LDLib2 缺失时）。
4. 测试 + 文档（含"哪些字段只对某些粒子有意义"）。
