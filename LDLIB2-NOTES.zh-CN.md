# LDLib2 配置页实现笔记（已核实，照做即可）

来源：反编译 `synaxis-1.4.3.jar` + `ldlib2-neoforge-1.21.1-2.2.17-all.jar`（工作副本在 `D:\DSH\McMOD\work\synaxis-src`、`ldlib-probe`）。
**结论先说**：Synaxis 的页面只用了 LDLib2 的**控件与贴图**，同步完全是它自己的框架（`StateStore`/`BlockEntitySync`/`SynaxisPackets`）。我们**不要抄它的同步层**，直接用 LDLib2 自带的绑定/RPC，短得多。

---

## 1. 依赖与构建（有一个必须先解决的坑）

`dev.vfyjxf.taffy.*`（布局）和 `org.appliedenergistics.yoga.*`（被公开签名引用）**不在 LDLib2 顶层**，而是 jar-in-jar：

```
ldlib2-neoforge-1.21.1-2.2.17-all.jar
  └─ META-INF/jarjar/taffy-1.1.4.jar
  └─ META-INF/jarjar/yoga-1.0.0.jar
```

所以要像 `prepare-libs.ps1` 抽取 flywheel/sable-companion 那样抽出三个 jar 放进 `libs/`：

```
libs/ldlib2.jar   ← ldlib2-neoforge-1.21.1-2.2.17-all.jar 本体
libs/taffy.jar    ← 从它内部 META-INF/jarjar/taffy-1.1.4.jar 抽出
libs/yoga.jar     ← 从它内部 META-INF/jarjar/yoga-1.0.0.jar 抽出
```
`build.gradle`：`compileOnly files('libs/ldlib2.jar','libs/taffy.jar','libs/yoga.jar')`
`neoforge.mods.toml`：`modId="ldlib2"`、`type="optional"`、`versionRange="[2.2.17,)"`、`ordering="AFTER"`、`side="BOTH"`。
纯 Java 用法**不需要** kotlin-stdlib；只有碰 `*Spec`/`*_dslKt` 才需要。

**不需要**为了页面让方块类实现 `BlockUI`（这是重点，见下）。

---

## 2. 打开页面：自注册 MenuType（可选依赖的正路）

LDLib2 的 `BlockUIMenuType` 是**硬 instanceof 判定**（`blockstate.getBlock() instanceof BlockUI`，`gui/factory/BlockUIMenuType.java`），没有 provider 间接层。要让它作为**可选依赖**，用这条路（已验证同步管线完整保留）：

1. 自建 holder：`implements MenuProvider, IContainerUIHolder`（`createUI(Player)`、`isStillValid(Player)`、`createMenu(...)`、`writeClientSideData(...)`）。
   - `createUI` 里构造页面并 `return ModularUI.of(ui, player)` —— **player 不能是 null**，初始值靠它推送。
2. 注册 `MenuType`：`IMenuTypeExtension.create((id,inv,data) -> new ModularUIContainerMenu(MY_MENU.get(), id, inv, myHolder))`。
   - `ModularUIContainerMenu` 接受的是 `IContainerUIHolder`（不是 `BlockUIHolder`），它是 `IModularUIHolder`，所以 `writeInitialData/readInitialData` 与 20Hz 的 `syncManager.tick()` 全部照常工作。
3. 客户端注册界面：`RegisterMenuScreensEvent` 里 `event.register(MY_MENU.get(), ModularUIContainerScreen::new)`。
4. 打开：`player.openMenu(holder)`（走原版 MenuProvider 路径）。

（备选：`PlayerUIMenuType.openUI(player, id)` 零注册，但工厂只拿得到 Player、拿不到 BlockPos，得自己映射；不推荐。条件式子类/mixin 注入接口都更脆，不用。）

---

## 3. 页面结构与尺寸（Synaxis 实测常量）

| 元素 | 尺寸/布局 |
|---|---|
| 根 | `242 x 220`，`FlexDirection.COLUMN`，gap 0 |
| 标签栏 | `242 x 30`，贴图 `tab_bar`；页签按钮 `18x18`，绝对定位 `left=38+22*i, top=4` |
| 背景 | `242 x 210`，`simple_background_5_6` 取 `(0,0,166,133)` |
| 内容区 | 绝对定位 `left=22, top=4`，`196 x 178`，COLUMN，gap 4 → **页面按 196 宽设计** |
| 一行 | 高 13；列表行 28；列表面板高 84；标签列 96；控件区 96；文本框 `96x10`；图标按钮 `13x13` |

- 面板底色两种写法：`SpriteTexture.of(RL).setSprite(x,y,w,h)`（默认**拉伸**，不设 border 就不是九宫格）或 `SDFRectTexture.of(fill).setBorderColor(border).setRadius(2f).setStroke(1f)` —— 后者正是 Synaxis 的 `inputBackground/selectorBackground/displayBackground`。不想用它的 PNG 就用 LDLib2 自带的 `Sprites.BORDER / RECT_RD / RECT_DARK`。
- 一"页"就是一个普通 `UIElement`（`widthPercent100`、`heightAuto`、COLUMN、gap 4）。**Synaxis 不做页面滚动**，用 `setDisplay(boolean)` 切页；超出 178 高就把整页塞进 `ScrollerView`。

---

## 4. 字段绑定（每行一句）

LDLib2 自带双向绑定，值会以 **1 tick 延迟**自动上行（编辑 → 下一个 tick 推到服务端；不是即时）：

```java
textField.bind(DataBindingBuilder.string(be::particle, be::setParticle).build());
toggle.bind(DataBindingBuilder.bool(be::forceEmit, be::setForceEmit).build());
selector.bind(DataBindingBuilder.enumVal(Mode.class, be::mode, be::setMode).build());
label.bind(DataBindingBuilder.componentS2C(be::statusText).build());   // 只服务端→客户端
```

- 服务端侧绑定的 getter 读方块实体、listener 写方块实体；客户端侧把控件本身当远端数据源。
- **坑**：`build()` 在 getter 返回 null 且未指定 syncType 时会 NPE（用带类型的静态方法就不会）。
- 想要"改完点应用"的显式草稿（Synaxis 的观感）就用 RPC：`button.addRPCEvent(RPCEventBuilder.simple(CompoundTag.class, draft -> {...}))` + `setOnClick(e -> e.currentElement.sendEvent(rpc, tag))`，**在服务端校验后写入**。`setOnServerClick(...)` 触发的是 `mouseDown` 不是 `click`，注意。

---

## 5. 校验（与我们已定的行为一致）

- `textField.setTextValidator(Predicate<String>)`：不合法 → `isError=true`、**已提交的文本保持旧值**、原始输入仍在。
- 错误视觉只有**文字变红**：`textFieldStyle(s -> s.errorColor(0xFFFF5555))`；红**底**要自己在 responder 里换 `backgroundTexture`。
- `getRawText()` 是当前输入，`getValue()/getText()` 是上次提交值。
- **没有 commit-on-blur 钩子**（`onBlur` 只收选区）；需要就自己 `addEventListener("blur"/"focusOut", ...)`。

我们的既有设计正好对上：**失焦/应用时校验，不合法标红并保留原文、不写入方块实体**。

---

## 6. 风险清单（写在前面，踩了别怪）

1. **同步 ID 是位置序**（按 `UIElement` 深度优先注册顺序分配）。客户端/服务端**树形或构造顺序不一致会静默错位**，不报错。→ 页面必须**静态构造**：所有控件无条件创建，用 `setDisplay()` 切换显隐，绝不在两侧走不同分支。
2. 服务端初始值推送依赖一个 **lambda 名 mixin**（`ServerPlayerMixin` 指向 `lambda$openMenu$15`），MC 更新可能悄悄失效。
3. **2.2.17 里没有 Slider、没有 ScalarText**。数字输入 = `TextField` 数字模式（+ `setWheelDur`）；要滑块得自己写。
4. 构造器默认值有实际尺寸含义（Selector/Button/Toggle 默认 14 高、Label 9 高、`ScrollerView` 视野 paddingAll(5)），**一律显式设置宽高**。
5. `setText(v)` / `setSelected(v,true)` / `setValue(v,true)` 会触发自己的监听器；回显服务端状态时**必须用 `false` 的不通知重载**。
6. 每个字段用 `bind()` 或手写 responder **二选一**，不要两者都上。

---

## 7. 我们的页面内容（已定稿）

共享区：粒子(SNBT 文本框 + 校验)、启用(开关)、**持续喷射（调试开关，开着就一直喷）**、偏移 X/Z(数字)。
扬尘额外：出尘率、单次上限、找地深度、最低速度、寿命 min/max、尺寸、颜色取自地面(开关)。
引擎烟额外：红石 15/1 时的间隔与数量、上升速度、继承载具速度、寿命、尺寸。

字段表由 `GeneratorFields` 提供（id/类型/范围/标签键），页面只按类型画控件、不懂语义 —— 所以两个方块共用一套页面代码。
