# 规则基础字段

这一页说明一条规则顶层可以写哪些字段：每个字段的作用、取值、默认值，以及什么时候需要动它。

## 字段总览

| 字段 | 作用 | 取值/范围 | 默认 | 影响行为？ |
| --- | --- | --- | --- | --- |
| `id` | 规则的身份 | `命名空间:路径` 字符串 | 省略时由单条文件的路径推导 | 是（同 id 会互相覆盖） |
| `display_name` | 给人看的规则展示名 | 字符串，最多 128 个字符 | 无（可省略） | 否，只影响显示 |
| `notes` | 写给自己看的备注 | 字符串，支持换行 | 无（可省略） | 否，只影响显示 |
| `enabled` | 这条规则是否参与匹配 | `true` / `false` | `true` | 是 |
| `priority` | 同一物品命中多条规则时的先后顺序 | 整数（可为负） | `0` | 是 |
| `schema_version` | 规则结构版本 | 正整数，当前只接受 `1` | `1` | 是（其它值会被拒载） |
| `source` | 这条规则认哪些掉落物 | 对象：`items` 必填且非空，`exclude` 可选 | 无，必填 | 是 |
| `triggers` | 在哪些消失方式下触发 | 数组，取值 `natural` / `fire` / `lava` / `cactus` | 省略表示只有 `natural` | 是 |
| `trigger_after_seconds` | 自然消失时，存活多少秒后触发 | 非负整数（秒） | `300` | 是 |

另外还有只属于候选结果与结算的 `effects`、`outcomes`、`combination`、`source_cost` 与 `catalyst_cost`，写法见 [候选结果与结算](zh-05-outcomes-and-settlement)。

## id：规则的身份

- **作用**：标识一条规则。加载时按 id 合并各个来源，同一个 id 只有一条最终生效；命令、编辑器与报错信息都用它定位规则。
- **取值/范围**：`命名空间:路径` 形式的字符串，例如 `myrules:chicken_to_rotten_flesh`。命名空间与路径都只能用小写字母、数字、下划线、点与连字符，路径内可以用 `/` 分层。
- **默认值**：可以不写。仅当文件里只有一条规则时，才由文件路径推导；多条目文件里每条都必须显式写 `id`，否则该条被拒载。推导方式见 [首次使用与生效流程](zh-01-getting-started)。
- **什么时候需要动它**：给新规则起名时；想用覆盖层停用或替换一条已有规则时（必须写一模一样的 id，见 [来源层与覆盖](zh-06-layers-and-overrides)）；把一条规则从一个文件搬到另一个文件时。

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "source": { "items": ["minecraft:chicken"] },
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

## display_name 与 notes：只影响显示

- **作用**：`display_name` 是在命令输出与编辑器里代替 id 展示的规则展示名；`notes` 是随手写的备注，比如「为什么定成 30 秒」「这条和哪条规则配合」。
- **取值/范围**：都是字符串。`display_name` 最多 128 个字符（按码点算），超了会被拒载；`notes` 支持换行。
- **默认值**：都可以省略。省略时按来源与效果自动生成一个标题显示，规则本身不受影响。
- **什么时候需要动它**：规则多了以后想一眼认出某条；`notes` 在多人协作或隔很久回来看时特别有用。

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "display_name": "鸡肉腐化 / Chicken to Rotten Flesh",
  "notes": "和内置示例同样 300 秒，但只在主世界生效。",
  "source": { "items": ["minecraft:chicken"] },
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

这两个字段只影响显示：改它们不会改变规则的匹配、时机或产出。

## enabled：开关

- **作用**：写 `false` 时，这条规则仍会被读取、仍出现在 `/idtw config list` 里，但不参与匹配，等于临时关掉它。
- **取值/范围**：`true` 或 `false`。写成其它类型（比如字符串形式的 `false`）会让该条被拒载。
- **默认值**：`true`。
- **什么时候需要动它**：调试期间想先关掉某条再继续观察；保留一条还能用的配置但暂时不用它。要长期关闭内置规则时，更推荐用覆盖层停用，而不是改内置文件（内置文件只读，见 [来源层与覆盖](zh-06-layers-and-overrides)）。

```json
{
  "id": "myrules:chicken_to_rotten_flesh",
  "enabled": false,
  "source": { "items": ["minecraft:chicken"] },
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

## source：认哪些掉落物

- **作用**：决定规则适用于哪些掉落物。只有被 `source` 命中的掉落物，才会被这条规则纳入候选。
- **取值/范围**：对象。`items` 必填、必须是非空数组；`exclude` 可选，也是数组；两者加起来最多 256 项。每一项要么是物品 id（如 `minecraft:chicken`），要么是物品标签（如 `#minecraft:saplings`）。
- **默认值**：无，`source` 必填；`exclude` 省略表示不排除任何物品。

### 物品 id 与 #标签的写法差别

| 写法 | 含义 | 匹配依据 |
| --- | --- | --- |
| `minecraft:oak_sapling` | 精确的一个物品 | 只看这一个物品 |
| `#minecraft:saplings` | 物品标签，即该标签当前包含的全部物品 | 按当前数据包里的标签成员匹配 |

要点：

- 标签必须带 `#` 前缀。漏掉 `#` 会被当成一个叫这个名字的物品，而这样的物品不存在，该条会被拒载。
- 标签的成员由数据包里的标签文件决定，会随数据包变化。用标签能一次覆盖「所有树苗」这类成组物品，之后新增的物品自动包含在内。
- 写错的物品 id（物品不存在）会让该条被拒载；标签名写错不报错，只是永远匹配不到，所以排查时优先确认标签名。

### exclude 优先于 items

匹配顺序是先看 `exclude`，再看 `items`：只要掉落物命中 `exclude` 里的任意一项，这条规则就不会应用，哪怕它同时也命中 `items`。所以 `exclude` 是减法，用来在成组匹配里挖掉个别例外。

```json
{
  "id": "myrules:saplings_to_blocks",
  "display_name": "树苗落地（不含橡树苗）",
  "source": {
    "items": ["#minecraft:saplings"],
    "exclude": ["minecraft:oak_sapling"]
  },
  "trigger_after_seconds": 60,
  "effects": [
    { "type": "itemdespawntowhat:place_block", "use_source_block": true, "shape": "square", "count": 1, "radius": 1 }
  ]
}
```

这条规则匹配除橡树苗以外的全部树苗。

其它注意点：

- `exclude` 里又写了 `items` 中完全相同的某一项时，该条被拒载（写的是同一个物品 id，或同一个 `#标签`）。
- `items` 里重复写同一项也会被拒载；`exclude` 里重复写同一项不受此限制，但重复项不会带来额外效果。
- **什么时候需要动它**：默认只需要写出要变的物品；物品成组变化时改用标签；组里有少数不想被动的物品时用 `exclude`。

## priority：多条规则抢同一个物品时谁先来

- **作用**：同一个掉落物可能同时命中多条规则，但一次只执行一条。`priority` 决定先尝试哪条：数值大的排在前面，先被尝试。
- **取值/范围**：整数，可以为负数，没有额外限制。
- **默认值**：`0`。
- **什么时候需要动它**：物品被多条规则匹配到，而你想要的不是先来的那条时；给特殊规则写正数、给兜底规则写负数，是最省事的做法。

```json
{
  "id": "myrules:chicken_to_diamond",
  "priority": 10,
  "source": { "items": ["minecraft:chicken"] },
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:diamond", "count": 1 }
  ]
}
```

这条规则的 `priority` 是 10，会比同为 `0` 的鸡肉规则更先被尝试。

优先级相同时还有后续排序规则，完整的候选顺序与「只执行一条」的判定过程见 [来源层与覆盖](zh-06-layers-and-overrides)。

## triggers：在哪些消失方式下触发

- **作用**：声明这条规则对哪些消失方式生效——是自然到寿命消失，还是被火、岩浆或仙人掌销毁。
- **取值/范围**：数组，取值只能是小写下划线的 `natural` / `fire` / `lava` / `cactus`，可以同时写多个。
- **默认值**：省略 `triggers` 字段时，只按 `natural`（自然消失）处理。

| 取值 | 含义 | 是否等待 |
| --- | --- | --- |
| `natural` | 掉落物活到寿命上限的那一刻 | 等 `trigger_after_seconds` |
| `fire` | 被火销毁 | 不等，销毁当刻就判定 |
| `lava` | 被岩浆销毁 | 不等，销毁当刻就判定 |
| `cactus` | 被仙人掌销毁 | 不等，销毁当刻就判定 |

三类环境销毁的共同点与差别：

- **当刻判定、没有等待时间**：掉落物被火、岩浆或仙人掌销毁的那一瞬间，规则的条件就按当时的情况判定一次；`trigger_after_seconds` 对它们不起作用。也就是说，一个只声明了 `fire` 的规则，可能刚掉下来几秒就被火销毁——只要那一刻条件满足，它就转化。
- **火与岩浆分开算**：火只对应被直接点燃与火焰持续伤害，岩浆单独算一类，两者不会混在一起。火、岩浆、仙人掌之外的销毁方式（比如玩家拾取、爆炸销毁）都不属于这三类，不会触发。
- **不声明就没有**：`triggers` 里没写的消失方式，这条规则完全不参与。只写 `natural` 时，被火销毁的掉落物不会转化。

```json
{
  "id": "myrules:burning_arrow_rain",
  "display_name": "烧成箭雨",
  "source": { "items": ["minecraft:arrow"] },
  "triggers": ["fire", "lava"],
  "effects": [
    { "type": "itemdespawntowhat:arrow_rain", "count": 16, "pickup": "allowed" }
  ]
}
```

这条规则不写 `natural`，所以箭在自然消失时什么也不会发生；只有被火或岩浆销毁时才会降下箭雨，且降下箭雨的时机就是销毁当刻。

同时写多种也很常见：

```json
{ "triggers": ["natural", "fire", "lava", "cactus"] }
```

含义是这条规则在所有四种消失方式下都生效；`natural` 仍按 `trigger_after_seconds` 等到期，另外三种在销毁当刻判定。

编辑器要求至少勾选一项消失方式；直接改 JSON 时省略 `triggers` 字段等同于只写 `natural`。

## trigger_after_seconds：等到什么时候

- **作用**：掉落物在 `natural` 消失时，要存活多少秒才触发规则。
- **取值/范围**：非负整数，单位是秒（内部按 20 刻 = 1 秒换算）。写负数会被拒载。
- **默认值**：`300`（5 分钟），和原版掉落物的消失直觉对齐。
- **什么时候需要动它**：想让东西快点变（比如 10 秒），或想让它临消失前才变（写一个贴近自然寿命的数）。

实际触发时刻会受该掉落物的自然寿命限制：

- 触发时刻取 `trigger_after_seconds` 与自然消失前一刻中较早的那个。原版掉落物的自然寿命是 300 秒（6000 刻），所以写 `300` 及以上时，实际会在临消失前触发，而不是再多等你的秒数。
- 写 `0` 表示一进入世界就尝试触发，下一次判定即生效。
- 自然寿命由加载器提供，其它模组改过寿命时以实际值为准；被设为永不消失的掉落物不会被纳入转化。
- 条件在到期时不命中时，这条规则不会就此放弃，而是按退避重试，直到该掉落物消失。

```json
{
  "id": "myrules:chicken_to_rotten_flesh_10s",
  "source": { "items": ["minecraft:chicken"] },
  "trigger_after_seconds": 10,
  "effects": [
    { "type": "itemdespawntowhat:spawn_entity", "variant": "item", "item": "minecraft:rotten_flesh", "count": 1 }
  ]
}
```

## schema_version：规则结构版本

- **作用**：标记这条规则按哪一版规则结构书写，供模组在结构升级时识别旧文件。
- **取值/范围**：正整数，当前只接受 `1`。写其它值会被拒载，报「不支持的 schema_version」。
- **默认值**：`1`，省略即为 `1`。
- **什么时候需要动它**：基本不用动。它是给结构升级用的，日常写规则省略即可；手抄别人的配置时若看到 `"schema_version": 1`，保持原样就行。

## 一个把基础字段用全的例子

```json
{
  "id": "myrules:sapling_planting",
  "display_name": "树苗落地",
  "notes": "除橡树苗外的树苗，在火与自然消失时都会种下。",
  "enabled": true,
  "priority": 5,
  "schema_version": 1,
  "source": {
    "items": ["#minecraft:saplings"],
    "exclude": ["minecraft:oak_sapling"]
  },
  "triggers": ["natural", "fire"],
  "trigger_after_seconds": 60,
  "effects": [
    { "type": "itemdespawntowhat:place_block", "use_source_block": true, "shape": "square", "count": 1, "radius": 1 }
  ]
}
```

读法：除橡树苗以外的树苗，如果活到 60 秒（或先到自然消失前一刻），或者被火销毁，就会在当前位置种下对应的树苗方块；这条规则的优先级是 5，会排在同为 `0` 的规则之前被尝试。

## 写错会怎样

字段写错只影响这一条规则，同一文件里其它规则照常加载，其它文件也不受影响。常见错误：

| 写法 | 结果 |
| --- | --- |
| `id` 含大写字母或空格 | 该条拒载，报非法的规则 id |
| 物品 id 拼错（物品不存在） | 该条拒载，报未知源物品 |
| `#` 标签名写错 | 不报错，但永远匹配不到 |
| `enabled` 写成字符串 | 该条拒载（类型不符，只接受 `true` / `false`） |
| `trigger_after_seconds` 为负 | 该条拒载 |
| `schema_version` 不是 `1` | 该条拒载 |
| 顶层多写了不认识的字段 | 不拒载，但会作为告警提示出来 |

排查流程见 [首次使用与生效流程](zh-01-getting-started)；条件的写法见 [条件](zh-03-conditions)；效果与产出见 [效果](zh-04-effects)。

## 相关

- [首次使用与生效流程](zh-01-getting-started)：文件放哪、改完怎么应用。
- [条件](zh-03-conditions)：怎么限制规则只在特定情况下生效。
- [效果](zh-04-effects)：触发后能做哪些事。
- [候选结果与结算](zh-05-outcomes-and-settlement)：`outcomes`、`combination` 与各类成本字段。
