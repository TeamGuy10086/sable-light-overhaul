# 坦克炮粒子试看命令表

用途：在游戏里把这些候选粒子一个个调出来看，决定"复用哪个 / 要画哪个"。全部命令已按 1.21.1 的 `/particle` 真实解析逻辑核对过（源码 `ParticleArgument` + `ClientPacketListener#handleParticleEvent`）。

## 三条语法规则（很重要，决定命令对不对）

| 规则 | 说明 |
|---|---|
| **`count = 0`** | 只生成**一个**粒子，位置精确、速度 = `(dx,dy,dz) * speed`。**方向驱动的粒子（plume、spark_burst、trail）必须用 0**，否则方向会被随机化 |
| **`count > 0`** | 生成 count 个：位置 = 基准点 ± `gaussian * (dx,dy,dz)`，速度 = `gaussian * speed`。**喷散类（dust/smoke/flame）用这个**，`delta` 当散布范围 |
| **参数** | 带参数的粒子写成 `id{字段:值,...}`，字段名必须与模组 codec 完全一致（下表已核对）。**面包军火库的 111 个粒子全是无参类型**，直接写 id 即可 |

前提：需要 op / 单人开启作弊。建议先把视角对着一面墙或地面，距离 3–5 格看细节，再退到 20 格看整体。

---

## 一、CBC（`createbigcannons:`）—— 坦克炮真正在用的那套

### 1.1 炮口烟本体（关键：`power` 控制"亮→暗"）

CBC 的 `cannon_smoke` 出生时**自发光**（光照贴图 +240，按 `(1-age/lifetime*1.5)^4` 衰减到环境光），透明度按 `1-progress²` 淡出，贴图沿 11 帧序列推进。`power` 通过着色器梯度纹理决定"火焰色 → 烟色"。**这就是你记得的"会变色的炮口焰"**，也是我们复用的核心。

```
# 明亮（火焰期）：大 power + 中等尺寸 + 短寿命
/particle createbigcannons:cannon_smoke{power:8.0,size:1.6,lifetime:10,friction:0.9} ~ ~1 ~ 0 0 0 0.15 3

# 中等（过渡）
/particle createbigcannons:cannon_smoke{power:4.0,size:2.0,lifetime:20,friction:0.95} ~ ~1 ~ 0 0 0 0.2 4

# 暗而薄（我们效果 2 想要的"不亮大范围薄烟"）：小 power + 大 size + 长寿命
/particle createbigcannons:cannon_smoke{power:0.2,size:3.0,lifetime:60,friction:0.99} ~ ~0.5 ~ 3 0.5 3 0.25 20
```

> 看什么：`power` 从 8 调到 0.2，同一个粒子应该从"白亮"变成"灰暗"；`size` 决定单片多大；`lifetime` 决定挂多久。**如果这一条在你机器上不像"火焰"**，多半是 CBC 客户端配置 `useShaderCompatibleGraphics = true` 把它换成了 `fallback_cannon_smoke`（见四处开关那节）。

### 1.2 炮口羽流（发射器：本体只活 10 tick，但持续吐烟）

```
# 长管坦克炮的真实参数：size = power = 装药/1.5（9 格炮管 ≈ 6.0）
/particle createbigcannons:big_cannon_plume{size:6.0,power:6.0,lifetime:10} ~ ~1 ~ 0 1 0 3 0

# 短管（3 格 ≈ 2.0）对比
/particle createbigcannons:big_cannon_plume{size:2.0,power:2.0,lifetime:10} ~ ~1 ~ 0 1 0 3 0

# 机炮羽流（对比用）
/particle createbigcannons:autocannon_plume{scale:2.0} ~ ~1 ~ 0 1 0 3 0
```

> 看什么：这个粒子**自带**火焰期（前 5 tick 喷 vanilla `flame`）、开炮瞬间的白色 `cloud` 团、以及持续 1.5–3 秒的灰烟。也就是说**光是把 size/power 放大，就已经是"闪光+快速扩散的烟"的雏形**。

### 1.3 火星（自带反弹！）

```
# 注意：CBC 的 spark 字段名就叫 "scale"，但值是颜色向量（源码如此，别写成 color）
/particle createbigcannons:spark{scale:[1.0,0.5,0.2]} ~ ~1 ~ 0 1 0 2 0

# 火星蓬：color + deflect(反弹) + count
/particle createbigcannons:spark_burst{color:[1.0,0.45,0.12],deflect:true,count:30} ~ ~1 ~ 0 1 0 2.5 0

# 不反弹版对比
/particle createbigcannons:spark_burst{color:[1.0,0.45,0.12],deflect:false,count:30} ~ ~1 ~ 0 1 0 2.5 0

# 亮黄白（做"白热"点缀）
/particle createbigcannons:spark_burst{color:[1.0,0.9,0.6],deflect:true,count:15} ~ ~1 ~ 0 1 0 3 0
```

> 看什么：`deflect:true` 时火星打到地面/方块会弹开（**这就是你问的"经济的一次反弹"——CBC 自己实现了，我们只是传参，零成本**）。数量会按客户端粒子设置自动缩放。

### 1.4 其它可能用得上的 CBC 粒子

```
/particle createbigcannons:quick_firing_breech_smoke ~ ~1 ~ 0 1 0 0.3 3          # 效果4的烟（开闩），拿来当参照
/particle createbigcannons:trail_smoke{lifetime:100} ~ ~1 ~ 0 1 0 2 0            # 弹丸拖尾
/particle createbigcannons:debris_smoke_burst ~ ~1 ~ 0 1 0 1 0                   # 冲击碎屑烟
/particle createbigcannons:flak_smoke{lifetime:80,scale:2.0} ~ ~1 ~ 0 0 0 0.2 5   # 高炮烟团
/particle createbigcannons:gas_cloud{scale:3.0,color:[0.7,0.7,0.6]} ~ ~1 ~ 0 0 0 0.15 3
/particle createbigcannons:smoke_shell_smoke{scale:3.0} ~ ~1 ~ 0 0 0 0.15 3
/particle createbigcannons:fallback_cannon_smoke{power:8.0,size:1.6,lifetime:10,friction:0.9} ~ ~1 ~ 0 0 0 0.15 3
```
最后一条是"开 `useShaderCompatibleGraphics` 之后`cannon_smoke` 会变成的样子"——**直接对比这两条就能判断你在光影下的炮口烟会不会丢掉火焰色**。

---

## 二、面包军火库（`mianbaos_modernwarfare:`）—— 全是无参粒子，命令最好写

它是个 MCreator 模组，111 个粒子全部是 `SimpleParticleType`（不需要 `{}`）。

```
/particle mianbaos_modernwarfare:whiteflash ~ ~1 ~ 0 0 0 0 1          # ★ 白光闪（效果1的"闪光"核心候选）
/particle mianbaos_modernwarfare:spark ~ ~1 ~ 0 1 0 2 0               # ★ 火星（方向驱动）
/particle mianbaos_modernwarfare:smoke ~ ~1 ~ 0 0 0 0.2 6             # ★ 基础烟
/particle mianbaos_modernwarfare:newsmoke_1 ~ ~1 ~ 0 0 0 0.2 6        # ★ 新版烟 1–6，逐个试
/particle mianbaos_modernwarfare:newsmoke_2 ~ ~1 ~ 0 0 0 0.2 6
/particle mianbaos_modernwarfare:newsmoke_3 ~ ~1 ~ 0 0 0 0.2 6
/particle mianbaos_modernwarfare:newsmoke_4 ~ ~1 ~ 0 0 0 0.2 6
/particle mianbaos_modernwarfare:newsmoke_5 ~ ~1 ~ 0 0 0 0.2 6
/particle mianbaos_modernwarfare:newsmoke_6 ~ ~1 ~ 0 0 0 0.2 6
/particle mianbaos_modernwarfare:explosionsmall ~ ~1 ~ 0 0 0 0 1
/particle mianbaos_modernwarfare:explosionsmall_2 ~ ~1 ~ 0 0 0 0 1
/particle mianbaos_modernwarfare:explosionlarge ~ ~1 ~ 0 0 0 0 1
/particle mianbaos_modernwarfare:explosionblast ~ ~1 ~ 0 0 0 0 1
/particle mianbaos_modernwarfare:fireballtanshetrailsmoke ~ ~1 ~ 0 1 0 2 0   # 火球弹射拖尾烟
/particle mianbaos_modernwarfare:ballistic_launch_smoke ~ ~1 ~ 0 1 0 3 0     # 弹道发射烟
/particle mianbaos_modernwarfare:gassmoke_1 ~ ~1 ~ 0 0 0 0.2 6
/particle mianbaos_modernwarfare:scorchedstonesmokeparticle ~ ~1 ~ 0 0 0 0.2 6
/particle mianbaos_modernwarfare:shellrackexplodepraticleorange ~ ~1 ~ 0 0 0 0 1
/particle mianbaos_modernwarfare:shellrackexplodepraticleyellowsmall ~ ~1 ~ 0 0 0 0 1
```
> 重点看 `whiteflash`（有没有可能直接当"闪光"）、`newsmoke_*`（哪种烟的边缘最柔、最适合做贴地薄烟）、`explosionsmall*`（是不是那种快速扩张的亮团）。

---

## 三、vanilla 对照组（做"薄烟 / 地尘"，零新贴图）

```
# 尘土：颜色 = 落点方块的地图色（草 0.35,0.55,0.25 / 沙 0.86,0.82,0.65 / 石 0.5,0.5,0.5）
/particle minecraft:dust{color:[0.55,0.5,0.42],scale:2.5} ~ ~0.2 ~ 2 0.3 2 0.3 30
/particle minecraft:dust{color:[0.86,0.82,0.65],scale:3.5} ~ ~0.2 ~ 2.5 0.3 2.5 0.25 40

# 白→灰的过渡色（模拟"闪光余烬变烟"）
/particle minecraft:dust_color_transition{from_color:[1.0,0.85,0.5],to_color:[0.3,0.3,0.3],scale:2.0} ~ ~1 ~ 0 0 0 0.15 3

# 沙子/雪被吹起
/particle minecraft:falling_dust{block_state:"minecraft:sand"} ~ ~0.5 ~ 2 0.2 2 0.2 25

# 火焰与余烬
/particle minecraft:small_flame ~ ~1 ~ 0 1 0 2.5 4
/particle minecraft:flame ~ ~1 ~ 0 1 0 2 3
/particle minecraft:lava ~ ~1 ~ 0 1 0 2 3
/particle minecraft:electric_spark ~ ~1 ~ 0 1 0 2.5 3
/particle minecraft:flash ~ ~1 ~ 0 0 0 0 1

# 烟/团
/particle minecraft:large_smoke ~ ~1 ~ 0 0 0 0.2 6
/particle minecraft:white_smoke ~ ~1 ~ 0 0 0 0.2 6
/particle minecraft:poof ~ ~1 ~ 0 0 0 0.2 6
/particle minecraft:campfire_cosy_smoke ~ ~1 ~ 0 0 0 0.1 4
```
> 看什么：`dust` + `scale` 是**唯一能"任意颜色 + 任意大小 + 长时间慢飘"的无资产方案**，地面扬尘建议就用它（颜色取方块地图色）。缺点是边缘偏硬、没有卷曲细节 —— 如果你觉得不够"土"，那才需要画一张软边烟尘贴图。

---

## 四、CBC 的四个客户端开关（决定你看到的是哪一套）

在 `config/createbigcannons-client.toml` 里（默认值已标注）：

| 开关 | 默认 | 作用 |
|---|---|---|
| `bigCannonBlast.showBigCannonPlumes` | `true` | 炮口羽流总开关，关掉就没有任何炮口烟 |
| `bigCannonBlast.showExtraSmoke` | `true` | 开炮瞬间那团白色 `cloud` |
| `bigCannonBlast.showExtraFlames` | `true` | **羽流前 5 tick 的火焰**（你记得的那个炮口焰） |
| `useShaderCompatibleGraphics` | `false` | **开光影时应该关注这个**：打开后 CBC 会把 `cannon_smoke` 换成 `fallback_cannon_smoke`（普通半透明烟，**没有火焰色梯度**） |

建议的对照实验：
1. 三条全开 + `useShaderCompatibleGraphics=false`，用 §1.2 的命令看炮口羽流 → 这是 CBC 的"完整形态"。
2. 改成 `true` 再看一遍 → 你会看到光影下实际会变成什么样。**这决定我们效果 1 到底能不能"靠 CBC 拼出来"，还是必须自己加一层。**
3. 参考量为零成本：`/particle createbigcannons:big_cannon_plume{size:6,power:6,lifetime:10} ~ ~1 ~ 0 1 0 3 0` 与坦克炮实际参数一致（`size=power=装药/1.5`）。

---

## 五、一次"模拟坦克开炮"的组合（把上面选好的拼起来看）

对着地面，朝 +X 方向打一发（把 `~ ~1 ~` 换成炮口位置、`1 0 0` 换成炮口方向）：

```
/particle createbigcannons:big_cannon_plume{size:6.0,power:6.0,lifetime:10} ~ ~1 ~ 1 0 0 3 0
/particle createbigcannons:cannon_smoke{power:8.0,size:1.6,lifetime:8,friction:0.9} ~ ~1 ~ 0 0 0 0.15 3
/particle mianbaos_modernwarfare:whiteflash ~ ~1 ~ 0 0 0 0 1
/particle createbigcannons:spark_burst{color:[1.0,0.45,0.12],deflect:true,count:30} ~ ~1 ~ 1 0 0 2.5 0
/particle createbigcannons:cannon_smoke{power:0.2,size:3.0,lifetime:60,friction:0.99} ~ ~0.5 ~ 2 1 2 0.25 20
/particle minecraft:dust{color:[0.55,0.5,0.42],scale:3.0} ~ ~0.2 ~ 2 0.3 2 0.3 35
```
（6 条可以做成一个按键宏/循环命令方块，连续看几遍就能判断哪条该留、哪条该删。）
