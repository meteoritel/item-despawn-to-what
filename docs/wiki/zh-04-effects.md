# 效果（执行行为）

这一页说明规则命中后能做什么：每种效果写什么 `type`、有哪些参数，以及配出来玩家会看到什么。

规则命中后，模组按顺序执行 `effects` 里的每个效果对象：给出物品、生成实体、放出经验、摆下方块、打雷、下雨、消耗源物品。在 JSON 里，**一个效果就是一个对象**，`type` 决定它是哪一种效果。效果写在规则顶层的 `effects` 数组里，也可以写在某个候选结果的 `effects` 数组里；条件见[条件](zh-03-conditions)，成本与整组结算见[候选结果与结算](zh-05-outcomes-and-settlement)。

| 效果 | `type` | 一次性 | 一句话 |
|---|---|---|---|
| 实体产出 | `itemdespawntowhat:spawn_entity` | 否 | 给出物品、生成实体或放出经验 |
| 箭雨 | `itemdespawntowhat:arrow_rain` | 是 | 从触发位置上方落下一阵箭，可带药水效果 |
| 放置方块 | `itemdespawntowhat:place_block` | 否 | 在触发位置附近摆下方块 |
| 生成战利品 | `itemdespawntowhat:loot_table` | 否 | 按战利品表产出一批掉落物 |
| 闪电 | `itemdespawntowhat:lightning` | 是 | 落下 1 ～ 16 道闪电 |
| 爆炸 | `itemdespawntowhat:explosion` | 是 | 在触发位置产生一次爆炸 |
| 天气 | `itemdespawntowhat:weather` | 是 | 让当前维度下雨或放晴 |
| 消耗源物品 | `itemdespawntowhat:consume_source` | 否 | 从这次用掉的源物品里消耗一些 |
| 消耗催化剂 | `itemdespawntowhat:consume_catalyst` | 否 | 消耗触发位置附近指定的掉落物 |
| 消耗流体 | `itemdespawntowhat:consume_fluid` | 否 | 消耗触发位置那一格的流体 |

下面先讲所有效果共用的字段，再按这张表逐种说明。

## 通用字段

每个效果对象都可以写下面 4 个字段，`type` 必填，其余可以省略。效果自己的参数（例如 `item`、`count`）和这 4 个字段**写在同一层**，不要再套一层 `value` 之类的对象。

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `type` | 这是哪一种效果 | `itemdespawntowhat:` 开头的效果 id，取值见上面的效果类型总览 | 无，必填 | 写错名字的规则会被拒载，提示里会列出已注册的类型 |
| `delay_ticks` | 隔多久才执行这个效果 | 整数（单位：刻，20 刻 = 1 秒） | `0` | 从规则命中开始算；写负数会被拒载，提示「delay_ticks 不能为负数」 |
| `chance` | 执行到这个效果时掷一次的成功概率 | `0.0` ～ `1.0` | `1.0` | 超出这个范围会被拒载，提示「chance 必须在 [0,1] 内」；`1` 或 `1.0` 表示必定发生 |
| `conditions` | 只控制这一个效果做不做 | 条件树对象，写法见[条件](zh-03-conditions) | 省略 = 无条件 | 只判定、不消耗；不命中就跳过这一个效果 |

三点必须知道：

- **拒载**：这条规则的内容有问题时，模组不会加载它，并给出原因。写了效果不认识的字段同样会被拒载，提示里带字段名。
- 引用物品、方块、实体、流体、药水效果时，既可以写具体 id（`minecraft:water`），也可以写**标签**（`#` 开头的引用，例如 `#minecraft:logs`）。
- 标签的含义分两种：产出类引用（`spawn_entity` 的 `item` 和 `entity`、`place_block` 的 `block`、`potion_effects` 的 `effect`）每次执行从中**随机取一个成员**；匹配类引用（`consume_source` 的 `counts`、`consume_catalyst` 的 `items`、`consume_fluid` 的 `fluid`）表示**只要是标签里的任一成员就命中**，不会随机取。

## 多个效果怎么执行

- **按顺序启动**：数组里写在前面的效果先启动。每个效果各自带延迟，所以完成时间不一定和定义顺序一致（先启动的箭雨可能比随后启动的爆炸更晚结束）。
- **分批完成**：一次要产出很多个东西时不会在一刻内全部堆出来，而是分若干刻陆续完成（掉落物每批最多 16 个、实体每刻最多 8 个、放置方块每刻最多处理 128 个位置）。玩家看到的是东西陆续出现。
- **概率和条件只针对单个效果**：每个效果自己掷 `chance`、自己判 `conditions`，互不干扰；某个效果概率落空，同组的其它效果照常执行。
- **单个效果失败不影响其它效果**：某个效果执行时发生错误（例如它引用的物品在数据包更新后已经不存在），只会记录这一次失败并继续执行后面的效果。已经发生的事不会回滚，整条规则也不会被撤销；失败会记在结果里，排查方式见[首次使用与生效流程](zh-01-getting-started)。
- **判定时机**：延迟到点、真正要执行的那一刻才判 `conditions` 和掷 `chance`，不是一开始就全部决定好。
- **每组各执行一次**：每开一组，这一组的效果就各执行一次，`count` 就是这一次产出的数量，战利品表每组开一次表。整堆怎么拆成几组见[候选结果与结算](zh-05-outcomes-and-settlement)。
- **上限 32**：一条规则里所有效果（含各个候选结果里的 `effects`）加起来最多 32 个，超出后规则被拒载。
- **一次性效果**：`arrow_rain`、`lightning`、`explosion`、`weather` 属于一次性效果——对同一个源只演出一场，不随组数重复，也不参与候选结果的容量判定；而且同一个源上最多尝试一次（概率落空、条件不命中、执行失败都算尝试过，不会重试）。

## 实体产出（spawn_entity）

`type`：`itemdespawntowhat:spawn_entity`

用**产出子类**决定产出什么：`variant` 必填，取 `item`（物品）、`entity`（实体）、`experience`（经验）之一；子类自己的参数与 `type`、`variant` 写在同一层。

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `variant` | 用哪个产出子类 | `item` / `entity` / `experience` | 无，必填 | 不写会被拒载，提示「请选择实体产出子类」 |

### 子类 item：产出物品

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `item` | 产出什么物品 | 物品 id 或标签 | 无，必填 | 写标签时随机取标签里的一个成员 |
| `count` | 产出件数 | 1 ～ 64 | `1` | 超过该物品的堆叠上限时自动拆成多堆 |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
```

配完会发生什么：一份腐肉落在触发位置附近（水平偏移不超过 0.15 格），玩家可以直接捡走。新产出的掉落物会带上保护和转化冷却，不会马上又被规则消耗，见[候选结果与结算](zh-05-outcomes-and-settlement)。

### 子类 entity：产出实体

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `entity` | 生成什么实体 | 实体产物 id 或标签 | 无，必填 | 候选结果开了 `safe_spawn` 时先在附近找能站的位置，见[候选结果与结算](zh-05-outcomes-and-settlement) |
| `count` | 生成个数 | 1 ～ 64 | `1` | 每刻最多生成 8 个，剩下的接着生成 |
| `age` | 年龄 | 整数，可正可负 | `0` | 只对能长大的生物生效（家畜、村民等）；`0` 是成年，`-24000` 相当于刚出生 |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "entity", "entity": "minecraft:chicken", "count": 1, "age": -24000 }
```

配完会发生什么：在触发位置附近生成一只幼年鸡。开了 `safe_spawn` 时，模组会在触发位置上下左右各 2 格（5×5×5，共 125 个格子）里由近到远找一个能站的地方；挑不到就退回触发位置附近。这个开关默认关闭。

注意：掉落物（`minecraft:item`）和经验球（`minecraft:experience_orb`）不能写在这里，必须用另外两个专用子类，否则规则被拒载。

### 子类 experience：产出经验

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `amount` | 经验点数 | 1 ～ 65536 | `1` | 按单球最多 32767 点逐颗装满（不足 32767 就只有一颗），余数落在最后一颗；每批最多 16 颗，发完为止 |
| `per_source_item` | 按本组实际消耗的源物品件数倍增 | `true` / `false` | `false` | `false` 时每组给一份 `amount`；`true` 时按本组实际消耗的源物品件数相乘 |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "experience", "amount": 100 }
```

配完会发生什么：100 点经验装不满一颗球的上限，所以只飞出 **1 颗**经验球，玩家捡起来就进账。经验球在触发位置半径 0.5 格的圆盘内随机落点，落点高度抬高 0.2 格；超过单球上限才分颗，每批最多 16 颗、下一批至少等到下一个游戏刻继续发，直到发完。每个经验行为按各自的组数独立结算，不同行为、不同组的点数不合并。经验球数量本身不参与任何邻近上限判定：附近经验球再多，也不会让这一组因经验存量而开不出来。

> 提醒：经验不参与邻近上限，不代表含经验的组一定开得出——同一组里还有物品或通用实体产出时，它们各自的容量上限照旧生效。

## 箭雨（arrow_rain）

`type`：`itemdespawntowhat:arrow_rain`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `count` | 箭的数量 | 1 ～ 256 | `16` | 每 2 刻落一支 |
| `pickup` | 箭能不能被玩家捡走 | `disallowed` / `allowed` / `creative_only` | `disallowed` | 分别表示不能捡、可以捡、只有创造模式能捡 |
| `potion_effects` | 箭上带的药水效果 | 数组，可以省略 | 省略 = 不带 | 每项的字段见下表 |

`potion_effects` 里每一项：

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `effect` | 药水效果 | 药水效果 id 或标签 | 无，必填 | 写标签时随机取一个成员，这一场箭雨共用同一个结果 |
| `duration_ticks` | 持续刻数 | 1 ～ 1000000 | `100` | 20 刻 = 1 秒 |
| `amplifier` | 等级 | 0 ～ 255 | `0` | `0` 表示 I 级，`1` 表示 II 级 |

```json
{ "type": "itemdespawntowhat:arrow_rain", "count": 32, "pickup": "allowed", "delay_ticks": 10 }
```

```json
{
  "type": "itemdespawntowhat:arrow_rain",
  "count": 16,
  "pickup": "creative_only",
  "potion_effects": [
    { "effect": "minecraft:poison", "duration_ticks": 200, "amplifier": 0 }
  ]
}
```

配完会发生什么：延迟 10 刻后开始，在触发位置上方 80 格处每 2 刻落下一支向下的箭，共 32 支，散布在触发位置周围约 5 格范围内，玩家可以把它们捡走。带药水效果时，被箭射中的实体会中对应效果。

## 放置方块（place_block）

`type`：`itemdespawntowhat:place_block`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `block` | 放什么方块 | 方块 id 或标签 | 省略 | 与 `use_source_block` 至少写一个 |
| `use_source_block` | 放置源物品对应的方块 | `true` / `false` | `false` | 源物品是泥土就放泥土；源物品没有对应方块时这次执行失败 |
| `shape` | 候选位置的形状 | `square` / `circle` / `cross` | `square` | 方形、圆形、十字（只取沿 x 轴或 z 轴的格子），大小写不敏感 |
| `count` | 最多放几个 | 1 ～ 64 | `1` | 由内向外依次尝试 |
| `radius` | 搜索半径（格） | 1 ～ 32 | `6` | 与触发位置同一高度的水平范围 |
| `limit` | 这个方块的总量上限 | 1 ～ 4096 | 省略 = 不限 | 统计半径立体范围内已有的同类方块，补到不超过它为止 |

说明：

- 两个都没写时规则被拒载，提示「block 与 use_source_block 至少需要其一，否则没有可放置的方块」；两个都写时以 `block` 为准。
- 只在**触发位置的同一高度**上放置，不会往上或往下叠。
- 只替换可以被替换的位置（草丛、水之类），并且要能站稳；放不下的位置直接跳过，不会为了凑数跑到 `radius` 外面。
- 触发位置本身是否参与放置由候选结果的填充起点开关决定，见[候选结果与结算](zh-05-outcomes-and-settlement)。

```json
{ "type": "itemdespawntowhat:place_block", "use_source_block": true, "shape": "square", "count": 1, "radius": 1 }
```

配完会发生什么：把这次用掉的源物品对应的方块摆在触发位置附近 1 格范围内（最多 1 个）。

```json
{ "type": "itemdespawntowhat:place_block", "block": "minecraft:cobblestone", "shape": "circle", "count": 32, "radius": 4, "limit": 64 }
```

配完会发生什么：在触发位置周围半径 4 格的圆形范围里由内向外补鹅卵石，最多 32 个；如果这个立体范围里的鹅卵石已经有 64 个，就一个都不放。

## 生成战利品（loot_table）

`type`：`itemdespawntowhat:loot_table`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `loot_table` | 用哪张战利品表 | 战利品表 id | 无，必填 | 只能写具体 id，不支持 `#` 标签 |
| `luck` | 幸运值 | `-100` ～ `100` | `0` | 影响表里带幸运判定的条目，可以写负数 |

```json
{ "type": "itemdespawntowhat:loot_table", "loot_table": "minecraft:chests/simple_dungeon", "luck": 0, "chance": 0.5 }
```

配完会发生什么：有一半概率开一次「简单地牢箱子」的战利品表，抽到的东西散落在触发位置附近。开表时以触发位置为原点，并把源实体作为「当前实体」交给战利品表；口径与箱子一致，不提供击杀者信息，所以「被玩家击杀」这类条目不会被选中，也就不会产出。

## 闪电（lightning）

`type`：`itemdespawntowhat:lightning`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `count` | 闪电数量 | 1 ～ 16 | `1` | 每道间隔 8 刻（0.4 秒） |

```json
{ "type": "itemdespawntowhat:lightning", "count": 1, "delay_ticks": 20 }
```

配完会发生什么：延迟 1 秒后打下 1 道与原版雷击相同的闪电（能造成伤害、点燃方块）。写 `count: 3` 时，第 1 道沿触发位置的水平坐标落下，后两道在周围 5 格内随机散开，落点贴合最上层方块的碰撞表面或流体表面，包括半砖和台阶；地下触发时仍落在上方表面。虚空没有可接触表面时使用高度图高度。

## 爆炸（explosion）

`type`：`itemdespawntowhat:explosion`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `power` | 爆炸威力 | `0` ～ `16` | `3` | 参考：苦力怕约 `3`，TNT 是 `4` |
| `fire` | 是否留下火焰 | `true` / `false` | `false` | 为真时会点燃周围方块 |
| `visual_only` | 只放光效不动世界 | `true` / `false` | `false` | `true` 时只播粒子与音效，不破坏方块、不伤人 |

```json
{ "type": "itemdespawntowhat:explosion", "power": 2, "fire": true, "visual_only": false }
```

配完会发生什么：在触发位置炸出一次威力 2 的爆炸，会破坏方块并留下火。

```json
{ "type": "itemdespawntowhat:explosion", "power": 4, "visual_only": true }
```

配完会发生什么：只有爆炸的粒子与音效，世界和实体都不受影响——适合做声势浩大但无害的演出。

## 天气（weather）

`type`：`itemdespawntowhat:weather`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `mode` | 下雨还是放晴 | `rain` / `clear` | 无，必填 | 不写会被拒载 |
| `duration_ticks` | 持续时间（刻） | 1 ～ 24000 | `6000` | 6000 刻 = 5 分钟，24000 刻 = 20 分钟 |
| `thundering` | 是否雷暴 | `true` / `false` | `false` | 只在 `rain` 时有效 |

```json
{ "type": "itemdespawntowhat:weather", "mode": "rain", "duration_ticks": 6000, "thundering": true }
```

配完会发生什么：当前维度立刻转成雷暴，持续 6000 刻（5 分钟）。

几点说明：`clear` 会把天气切换成晴天；已经是目标天气时不会重复设置（不会反复刷新计时）；没有天空光照的维度（例如下界）不会生效。

## 消耗类效果

消耗类效果负责在开组时消耗材料：`consume_source` 消耗源物品、`consume_catalyst` 消耗触发位置附近的掉落物、`consume_fluid` 消耗触发位置那一格的流体。它们和规则级固定成本（`source_cost`、`catalyst_cost`）做的是同一件事，同一条规则里同类东西只能选一种写法，见[候选结果与结算](zh-05-outcomes-and-settlement)。

### 消耗源物品（consume_source）

`type`：`itemdespawntowhat:consume_source`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `count` | 消耗多少件源物品 | 1 ～ 64 | `1` | 从这次转化用掉的源物品里消耗 |
| `counts` | 按具体物品分别指定数量 | 对象，键为物品 id 或标签，值为 1 ～ 64 | 省略 | 写了就按这里的数量消耗对应物品，键必须是 `source` 里出现过的条目 |

```json
{ "type": "itemdespawntowhat:consume_source", "count": 1 }
```

```json
{
  "type": "itemdespawntowhat:consume_source",
  "counts": { "minecraft:bone": 2, "#minecraft:coals": 1 }
}
```

配完会发生什么：这次转化用掉的源物品会少掉指定的量；源物品不够时按实际可消耗的数量收敛，不会消耗成负数。整条规则完全没写任何消耗时的隐式消耗见[候选结果与结算](zh-05-outcomes-and-settlement)。

### 消耗催化剂（consume_catalyst）

`type`：`itemdespawntowhat:consume_catalyst`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `items` | 要消耗哪些掉落物 | 数组，每项是物品 id 或标签 | 无，必填且不能为空 | 在触发位置半径内凑齐 |
| `count` | 每个条目消耗几个 | 1 ～ 64 | `1` | 被 `counts` 单独指定的条目除外 |
| `radius` | 搜索半径（格） | 1 ～ 8 | `1` | 搜索盒边长是 `2 × radius + 1` |
| `counts` | 按具体物品分别指定数量 | 对象，键必须是 `items` 里出现过的项 | 省略 | 引用了没在 `items` 里的物品会导致规则被拒载 |

```json
{ "type": "itemdespawntowhat:consume_catalyst", "items": ["minecraft:bone_meal"], "count": 1, "radius": 1 }
```

配完会发生什么：消耗触发位置附近（默认 1 格范围）最近的骨粉 1 个；范围里凑不齐时**整个效果跳过，一个都不消耗**（不会只消耗一半），跳过也会记在结果里。搜索时会避开这次转化本身的源实体。

### 消耗流体（consume_fluid）

`type`：`itemdespawntowhat:consume_fluid`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `fluid` | 消耗哪种流体 | 流体 id 或标签 | 省略 = 任何流体 | 省略时不做存在性判定 |
| `require_source` | 是否只认真正的流体源 | `true` / `false` | `true` | `false` 时流动中的流体也算 |

```json
{ "type": "itemdespawntowhat:consume_fluid", "fluid": "minecraft:water", "require_source": true }
```

配完会发生什么：每轮消耗触发位置的那一格水。被水淹没的方块（含水楼梯、含水台阶之类）只把水去掉、方块本身留着；普通流体方块则整格清空。

### 与规则级固定成本的关系

- `consume_source` 对应 `source_cost`：两者写的是同一笔源物品成本，同一条规则里只能声明一种，否则规则被拒载（避免同一份东西被记两次账）。
- `consume_catalyst` 对应 `catalyst_cost`：同样是二选一。
- `consume_fluid` 没有对应的规则级字段，只能写成效果。
- 两种写法都在**开组时**发生，每组各付一次；支付不足时这一组不开、不产出，剩下的源物品最后作为返还物回到世界。

## 关于 limit 与 radius

`limit` 和 `radius` 是**效果自己的参数**，写在具体效果对象里，不是规则级字段：

- `limit`：产物数量的上限，统计范围就是 `radius`。
- `radius`：含义由效果决定——`place_block` 里是放置的搜索半径，`consume_catalyst` 里是要消耗的掉落物的搜索半径；它同时是 `limit` 的统计范围。

服务器层面还有一套「附近产物太多就不启动转化」的保护（按规则生效，与单个效果的参数无关），见[服务端配置](zh-07-server-config)。

## 相关

- [条件](zh-03-conditions)
- [候选结果与结算](zh-05-outcomes-and-settlement)
- [规则基础字段](zh-02-rule-basics)
- [服务端配置](zh-07-server-config)
