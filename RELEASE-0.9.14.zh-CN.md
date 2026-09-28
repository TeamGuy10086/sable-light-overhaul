# 0.9.14-alpha：修掉"改配置就崩"

## 崩溃不是我们的界面

栈顶是 Create/catnip 的配置界面：

```
net.createmod.catnip.net.packets.ServerboundConfigPacket.serialize
net.createmod.catnip.config.ui.SubMenuConfigScreen.saveChanges
java.lang.IllegalArgumentException: unknown type createbigcannons:cannon_smoke{po: String
```

它在把配置值发回服务端时，**按值本身去猜"类型"**，而我们的值是 `createbigcannons:cannon_smoke{...}` 这种"看起来像资源位置"的字符串，于是被当成未知类型直接抛异常。也就是说：**触发条件是我们配置里那些"带冒号的字符串值"**，而保存时会把所有改动过的值一起序列化，所以你在 `[tankCannon]` 里改任何一项都会崩。

## 改了什么

**把配置里的字符串值全部撤掉**，粒子改成代码内置：

| 原来的配置键 | 现在 |
| --- | --- |
| `tankCannon.flame.particle` | 内置 `createbigcannons:cannon_smoke{power:8.0,size:1.6,lifetime:10,friction:0.9}` |
| `tankCannon.sparks.particle` | 内置 `createbigcannons:spark_burst{color:[1.0,0.45,0.12],deflect:true,count:24}` |
| `tankCannon.breechSmoke.particle` | 已移除（始终使用 CBCMW 自己的炮闩烟） |
| `generators.dust.particle` | 新生成器默认 `cbcmuzzlelight:ground_dust{…track:true}`（代码常量） |
| `generators.smoke.particle` | 新生成器默认 `minecraft:campfire_cosy_smoke`（代码常量） |

数值接口一个都没少：火焰/火花仍然有 `count / speed / size / plumeScale / bursts`，扬尘仍然有 `sizeMin/Max`、`stretchMin/Max`、`scatter`、`damping`、`gravity`、`alpha`、`count`、`lifetime`、`radius`、`color`，反冲仍然有 `enabled / velocity`。**这些都不是字符串，所以那个界面现在能正常保存。**

**按方块换粒子这个能力没有丢**：生成器的配置页面（右键生成器）里"粒子"一栏照旧可以填任意 `/particle` 串，那条路径走的是我们自己的网络包（不是那个配置 UI），不受影响。

## 你这边要做的

1. 换上 0.9.14；进游戏时 NeoForge 会把这些已删除的键从 TOML 里自动去掉（可能会有一条"未知键已移除"的提示，正常）。
2. 再试一次在那个界面改 `[tankCannon]` 的数值，应该不再崩。
3. 如果**改 `[colors] overrides` 也崩**，告诉一声：那个键是"字符串列表"，同样带冒号，可能是同一个 bug，我可以把它也改成不含冒号的写法（或改成"方块 id + 颜色"两个分开的字段）。
