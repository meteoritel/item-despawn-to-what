# 效果（执行动作）

这一章说明规则命中后能「做什么」：每种效果写什么 `type`、有哪些参数、配出来玩家会看到什么。条件怎么写见 [03-conditions.md](03-conditions.md)，成本、候选结果与整组结算见 [05-outcomes-and-settlement.md](05-outcomes-and-settlement.md)。

## 效果是什么

规则被命中后，模组会逐个执行效果列表里的动作：给出物品、生成生物、放出经验、摆下方块、打雷、下雨、把源物品吃掉……在 JSON 里，**一个效果就是一个对象**，`type` 决定它是哪一种效果。

效果列表写在规则顶层的 `effects` 数组，或写在某个候选结果的 `effects` 数组里；一个候选结果可以包含多个效果。这两者的关系见 [05-outcomes-and-settlement.md](05-outcomes-and-settlement.md)。

## 通用字段

每个效果对象都可以写下面 4 个字段。`type` 必填，其余都可以省略。

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `type` | 这是哪一种效果 | `itemdespawntowhat:` 开头的效果 id，取值见[总览](#效果类型总览) | 无，必填 | 写错名字规则读取失败，并提示已注册的类型 |
| `delay_ticks` | 隔多久才执行这个效果 | 整数（单位：刻，20 刻 = 1 秒） | `0` | 从规则命中开始算；写负数规则读取失败，提示「delay_ticks 不能为负数」 |
| `chance` | 执行到这个效果时掷一次的成功概率 | `0.0` ～ `1.0` 的小数 | `1.0` | 超出范围规则读取失败；`1` 或 `1.0` 表示必定发生 |
| `conditions` | 只控制这一个效果做不做 | 条件树对象，写法见 [03-conditions.md](03-conditions.md) | 省略 = 无条件 | 只判定、不消耗；不满足就跳过这一个效果 |

几点必须知道的：

- **效果的专属参数和这 4 个字段写在同一层**，不要再套一层 `value` 之类的对象。
- 写了这个效果不认识的字段，规则读取会失败并指出是哪个字段。
- 引用物品、方块、实体、流体、药水效果时，既可以写具体 id（`minecraft:water`），也可以写标签（`#minecraft:logs`）；写标签时每次执行随机取标签里的一个成员。

配两个效果会发生什么：

```json
[
  { "type": "itemdespawntowhat:lightning", "delay_ticks": 20 },
  { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:diamond", "count": 3, "chance": 0.25 }
]
```

配完会发生什么：规则一命中就先启动这两个动作。闪电等 20 刻（1 秒）后落下；钻石有 25% 的概率给出 3 个，没中就这一项跳过，闪电照常发生。

## 一条规则里的多个效果是怎么执行的

- **按顺序启动**：数组里写在前面的效果先启动。每个效果各自带延迟，所以完成时间不一定和书写顺序一致（先启动的箭雨可能比随后启动的爆炸更晚结束）。
- **分批完成**：一次要产出很多个东西时不会在一刻内全部堆出来，而是分若干刻陆续完成（掉落物每批最多 16 个、实体每刻最多 8 个、放置方块每刻最多处理 128 个位置）。玩家看到的是东西「陆续出现」。
- **概率和条件是针对单个效果的**：每个效果自己掷 `chance`、自己判 `conditions`，互不干扰。一个效果概率落空，同组其它效果照常执行。
- **单个效果失败不影响其它效果**：某个效果执行时出错（例如它引用的物品在数据包更新后已经不存在），只会记录这一次失败并继续执行后面的效果；已经发生的事不会回滚，也不会把整条规则撤销。失败会记在结果里，排查方式见 [01-getting-started.md](01-getting-started.md)。
- **判定时机**：延迟到点、真正要执行的那一刻才判 `conditions` 和掷 `chance`，不是一开始就全部决定好。
- **每组各执行一次**：每开一组，这一组的效果就各被派发一次——产出物品/实体、放置方块的 `count` 就是每次产出的数量，战利品表每组开一次表。整堆被拆成几组见 [05-outcomes-and-settlement.md](05-outcomes-and-settlement.md)。
- **上限 32**：一条规则里所有效果（含各个候选结果里的效果）加起来最多 32 个，超出后规则读取失败。
- **一次性效果**：`arrow_rain`、`lightning`、`explosion`、`weather` 属于一次性效果——对同一个源只演出一场，不随组数重复；而且同一个源上最多尝试一次（概率落空、条件不满足、执行失败都算尝试过，不会重试）。

## 效果类型总览

| 效果 | `type` | 一次性 | 一句话 |
|---|---|---|---|
| 实体产出 | `itemdespawntowhat:spawn_entity` | 否 | 给出物品、生成实体或放出经验 |
| 箭雨 | `itemdespawntowhat:arrow_rain` | 是 | 从触发位置上方落下一阵箭，可带药水效果 |
| 放置方块 | `itemdespawntowhat:place_block` | 否 | 在触发位置附近摆下方块 |
| 生成战利品 | `itemdespawntowhat:loot_table` | 否 | 按战利品表产出一批掉落物 |
| 闪电 | `itemdespawntowhat:lightning` | 是 | 落下 1 ～ 16 道闪电 |
| 爆炸 | `itemdespawntowhat:explosion` | 是 | 在触发位置产生一次爆炸 |
| 天气 | `itemdespawntowhat:weather` | 是 | 让当前维度下雨或放晴 |
| 消耗源物品 | `itemdespawntowhat:consume_source` | 否 | 从这次用掉的源物品里扣掉一些 |
| 消耗催化物 | `itemdespawntowhat:consume_catalyst` | 否 | 吃掉半径内指定的掉落物 |
| 消耗流体 | `itemdespawntowhat:consume_fluid` | 否 | 吃掉触发位置的那一格流体 |

## 实体产出：spawn_entity

`type`：`itemdespawntowhat:spawn_entity`

用**产出子类**决定产出什么：`variant` 必填，取 `item`（物品）、`entity`（实体）、`experience`（经验）之一；子类自己的参数与 `variant`、`type` 写在同一层。

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `variant` | 用哪个产出子类 | `item` / `entity` / `experience` | 无，必填 | 不写会读取失败，并提示「请选择实体产出子类」 |

### 子类 `variant: "item"`：产出物品

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `item` | 产出什么物品 | 物品 id 或 `#标签` | 无，必填 | 写标签时随机取标签里的一个成员 |
| `count` | 产出件数 | 1 ～ 64 | `1` | 超过该物品的堆叠上限时自动拆成多堆 |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
```

配完会发生什么：一份腐肉落在触发位置附近，玩家可以直接捡。这些产物带新产物保护与冷却，不会立刻又被同一条规则当成源物品反复吃掉（见 [05-outcomes-and-settlement.md](05-outcomes-and-settlement.md)）。

### 子类 `variant: "entity"`：产出实体

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `entity` | 生成什么实体 | 实体类型 id 或 `#标签` | 无，必填 | 开了产物的「生成生物时挑安全位置」开关后会先在附近找能站的地方，见 [05-outcomes-and-settlement.md](05-outcomes-and-settlement.md) |
| `count` | 生成个数 | 1 ～ 64 | `1` | 每刻最多生成 8 个，多出来的接着生成 |
| `age` | 年龄 | 整数，可正可负 | `0` | 只对能长大的生物生效（家畜、村民等）；`0` 是成年，`-24000` 相当于刚出生 |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "entity", "entity": "minecraft:chicken", "count": 1, "age": -24000 }
```

配完会发生什么：在触发位置附近生成一只小鸡（幼年）。如果这个候选结果开了「生成生物时挑安全位置」，模组会在触发位置上下左右各 2 格（共 125 个格子）里挑一个站得住的地方；挑不到就退回触发位置附近。这个开关默认关闭，见 [05-outcomes-and-settlement.md](05-outcomes-and-settlement.md)。

注意：掉落物（`minecraft:item`）和经验球（`minecraft:experience_orb`）**不能**写在这里，必须用下面两个专用子类，不然规则读取会失败。

### 子类 `variant: "experience"`：产出经验

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `amount` | 经验点数 | 1 ～ 65536 | `1` | 会按面额拆成若干颗经验球 |
| `per_source_item` | 按消耗掉的源物品件数倍增 | `true` / `false` | `false` | `false` 时每组给一份 `amount`；`true` 时按本组消耗的源物品件数相乘 |

```json
{ "type": "itemdespawntowhat:spawn_entity", "variant": "experience", "amount": 100 }
```

配完会发生什么：100 点经验以经验球的形式在触发位置附近飞出（自动拆成面额合适的几颗），玩家捡起来就进账。

## 箭雨：arrow_rain

`type`：`itemdespawntowhat:arrow_rain`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `count` | 箭的数量 | 1 ～ 256 | `16` | 每 2 刻落一支 |
| `pickup` | 箭能不能被玩家捡走 | `disallowed` / `allowed` / `creative_only` | `disallowed` | 分别表示不能捡、可以捡、只有创造模式能捡 |
| `potion_effects` | 箭上带的药水效果 | 数组，可以省略 | 省略 = 不带 | 每项的字段见下表 |

`potion_effects` 里每一项：

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `effect` | 药水效果 | 药水效果 id 或 `#标签` | 无，必填 | 写标签时每次执行随机取标签里的一个成员，这一场箭雨共用同一结果 |
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

配完会发生什么：延迟 10 刻后开始，在触发位置上方 80 格处每 2 刻落下一支向下坠的箭，共 32 支，分散在触发位置周围约 5 格范围内，玩家可以把它们捡走。带药水效果时，被箭射中的实体会中对应效果。

## 放置方块：place_block

`type`：`itemdespawntowhat:place_block`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `block` | 放什么方块 | 方块 id 或 `#标签` | 省略 | 与 `use_source_block` 至少写一个 |
| `use_source_block` | 放置源物品对应的方块 | `true` / `false` | `false` | 源物品是泥土就放泥土；源物品没有对应方块时执行失败 |
| `shape` | 候选位置的形状 | `square` / `circle` / `cross` | `square` | 方形、圆形、十字（只取沿 x 轴或 z 轴的格子） |
| `count` | 最多放几个 | 1 ～ 64 | `1` | 由内向外依次尝试 |
| `radius` | 搜索半径（格） | 1 ～ 32 | `6` | 与触发位置同一高度的水平范围 |
| `limit` | 这个方块的总量上限 | 1 ～ 4096 | 省略 = 不限 | 统计半径立体范围内已有的同类方块，补到不超过它为止 |

说明：

- 两个都没写时会读取失败，提示「block 与 use_source_block 至少需要其一，否则没有可放置的方块」；两个都写时以 `block` 为准。
- 只在**触发位置的同一高度**上放置，不会往上或往下叠。
- 只替换可以被替换的位置（草丛、水之类），并且要能站稳；放不下的位置直接跳过，不会为了凑数跑到 `radius` 外面。
- 触发位置本身是否参与放置由结果方案里的填充起点开关决定，见 [05-outcomes-and-settlement.md](05-outcomes-and-settlement.md)。

```json
{ "type": "itemdespawntowhat:place_block", "use_source_block": true, "shape": "square", "count": 1, "radius": 1 }
```

配完会发生什么：把这次用掉的源物品对应的方块摆在触发位置附近 1 格范围内（最多 1 个）。

```json
{ "type": "itemdespawntowhat:place_block", "block": "minecraft:cobblestone", "shape": "circle", "count": 32, "radius": 4, "limit": 64 }
```

配完会发生什么：在触发位置周围半径 4 格的圆形范围里，由内向外补鹅卵石，最多 32 个；如果这个立体范围里的鹅卵石已经有 64 个，就一个都不放。

## 生成战利品：loot_table

`type`：`itemdespawntowhat:loot_table`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `loot_table` | 用哪张战利品表 | 战利品表 id | 无，必填 | 只能写具体 id，不支持 `#标签` |
| `luck` | 幸运值 | `-100` ～ `100` | `0` | 影响表里带幸运判定的条目，可以写负数 |

```json
{ "type": "itemdespawntowhat:loot_table", "loot_table": "minecraft:chests/simple_dungeon", "luck": 0, "chance": 0.5 }
```

配完会发生什么：有一半概率开一次「简单地牢箱子」的战利品表，抽到的东西散落在触发位置附近。开表时以触发位置为原点，并把源实体作为「当前实体」交给战利品表；口径与箱子一致，不提供击杀者信息，所以「被玩家击杀」这类只在击杀时成立的条目不会生效。

## 闪电：lightning

`type`：`itemdespawntowhat:lightning`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `count` | 闪电数量 | 1 ～ 16 | `1` | 每道间隔 8 刻（0.4 秒） |

```json
{ "type": "itemdespawntowhat:lightning", "count": 1, "delay_ticks": 20 }
```

配完会发生什么：延迟 1 秒后打下 1 道与原版雷击相同的闪电（能造成伤害、点燃方块）。写 `count: 3` 时，第 1 道精确落在触发位置，后两道在触发位置周围 5 格内随机散去，都会落到地面高度。

## 爆炸：explosion

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

配完会发生什么：只有爆炸的粒子与音效，世界和实体都不受影响——适合做「声势浩大但无害」的演出。

## 天气：weather

`type`：`itemdespawntowhat:weather`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `mode` | 下雨还是放晴 | `rain` / `clear` | 无，必填 | |
| `duration_ticks` | 持续时间（刻） | 1 ～ 24000 | `6000` | 24000 刻 = 原版一整天 |
| `thundering` | 是否雷暴 | `true` / `false` | `false` | 只在 `rain` 时有效 |

```json
{ "type": "itemdespawntowhat:weather", "mode": "rain", "duration_ticks": 6000, "thundering": true }
```

配完会发生什么：当前维度立刻转成雷暴，持续 6000 刻（5 分钟）。

几点说明：`clear` 会把天气切换成晴天；已经是目标天气时不会重复设置（不会反复刷新计时）；没有天空光照的维度（例如下界）不会生效。

## 消耗源物品：consume_source

`type`：`itemdespawntowhat:consume_source`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `count` | 扣掉多少件源物品 | 1 ～ 64 | `1` | 从这次转化用掉的源物品里扣 |
| `counts` | 按具体物品分别指定数量 | 对象，键为物品 id 或 `#标签`，值为 1 ～ 64 | 省略 | 写了就按这里的数量扣对应物品 |

```json
{ "type": "itemdespawntowhat:consume_source", "count": 1 }
```

```json
{
  "type": "itemdespawntowhat:consume_source",
  "counts": { "minecraft:bone": 2, "#minecraft:coals": 1 }
}
```

配完会发生什么：这次转化用掉的源物品会少掉指定的量；源物品不够时按实际可扣的数量收敛，不会扣成负数。整条规则完全没写任何消耗效果时的默认扣除口径见 [05-outcomes-and-settlement.md](05-outcomes-and-settlement.md)。

## 消耗催化物：consume_catalyst

`type`：`itemdespawntowhat:consume_catalyst`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `items` | 要吃掉哪些掉落物 | 数组，每项是物品 id 或 `#标签` | 无，必填且不能为空 | |
| `count` | 每个条目吃几个 | 1 ～ 64 | `1` | |
| `radius` | 搜索半径（格） | 1 ～ 8 | `1` | 搜索盒边长是 `2 × radius + 1` |
| `counts` | 按具体物品分别指定数量 | 对象，键必须是 `items` 里出现过的项 | 省略 | 引用了没在 `items` 里的物品会导致规则读取失败 |

```json
{ "type": "itemdespawntowhat:consume_catalyst", "items": ["minecraft:bone_meal"], "count": 1, "radius": 1 }
```

配完会发生什么：把触发位置附近（默认 1 格范围）最近的骨粉收走 1 个；范围里凑不齐时**整个效果跳过，一个都不扣**（不会扣一半），跳过也会记在结果里。搜索时会避开这次转化本身的源实体。

## 消耗流体：consume_fluid

`type`：`itemdespawntowhat:consume_fluid`

| 字段 | 作用 | 取值/范围 | 默认 | 说明 |
|---|---|---|---|---|
| `fluid` | 吃掉哪种流体 | 流体 id 或 `#标签` | 省略 = 任何流体 | |
| `require_source` | 是否只认真正的流体源 | `true` / `false` | `true` | `false` 时流动中的流体也算 |

```json
{ "type": "itemdespawntowhat:consume_fluid", "fluid": "minecraft:water", "require_source": true }
```

配完会发生什么：每轮吃掉触发位置的那一格水。被水淹没的方块（含水楼梯、含水台阶之类）只把水去掉、方块本身留着；普通流体方块则整格清空。

## 关于 limit 与 radius

`limit` 和 `radius` 是**效果自己的参数**，写在具体效果对象里，不是规则级字段：

- `limit`：产物数量的上限，统计范围就是 `radius`。
- `radius`：按效果语义决定用途——`place_block` 里是放置的搜索半径，`consume_catalyst` 里是要吃掉的掉落物的搜索半径；同时是 `limit` 的统计范围。

服务器层面还有一套「附近产物太多就不启动」的保护（按规则生效，与单个效果的参数无关），见 [07-server-config.md](07-server-config.md)。

