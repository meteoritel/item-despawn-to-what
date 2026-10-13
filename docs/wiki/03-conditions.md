# 条件（conditions）

这份文档说明规则的 `conditions` 字段怎么写：条件树由哪些节点组成、能放哪些条件、每个条件有哪些参数，以及条件不满足时会发生什么。

规则本身的其它字段（`id`、`source`、`triggers`、`trigger_after_seconds` 等）见 [规则基础字段](02-rule-basics.md)；效果与成本见 [效果](04-effects.md)、[候选结果与结算](05-outcomes-and-settlement.md)。

## 条件负责什么

- 条件只判定「是否满足」，**不消耗任何物品或流体**。真正扣除材料的是消耗类效果。
- `conditions` 字段**整体省略**表示无条件，任何时刻都成立。
- 只有条件**命中**时，规则才会被选中并触发转化；不命中、无法判定、出错都不会触发。
- 条件符合预期与否不影响规则能否加载：条件长期不成立不是错误，只是这条规则很少生效。

## 条件树：四个 op

整棵条件树只有一个根节点。每个节点都是一个 JSON 对象，用 `op` 说明它是什么节点：

| `op` | 含义 | 子节点字段 | 该层允许出现的字段 |
|---|---|---|---|
| `all_of` | 全部子节点都成立才成立（并且） | `terms` | `op`、`terms` |
| `any_of` | 任一子节点成立即成立（或者） | `terms` | `op`、`terms` |
| `inverted` | 取反 | `term` | `op`、`term` |
| `leaf` | 一个具体条件 | `condition` | `op`、`condition` |

三条容易踩的规则：

- `terms` 是**数组**（`all_of` / `any_of` 用），`term` 是**单个节点**（`inverted` 用），`condition` 是**条件对象**（`leaf` 用）。写错字段就读不出来。
- 条件对象是扁平的：`type` 与类型专属参数写在**同一个对象**里，不再多包一层。
- 每个节点只认上表中的字段，多写一个字段（例如 `leaf` 里写 `terms`）会被直接拒绝。`all_of` / `any_of` 的 `terms` 不能为空数组；空条件请直接省略 `conditions` 字段。

### 条件叶（leaf）

叶节点里 `condition` 是一个「`type` + 专属参数」的对象：

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:dimension",
    "dimensions": ["minecraft:overworld"]
  }
}
```

### 组合节点（all_of / any_of / inverted）

```json
{
  "op": "all_of",
  "terms": [
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:outdoor" } },
    {
      "op": "inverted",
      "term": {
        "op": "leaf",
        "condition": { "type": "itemdespawntowhat:weather", "weather": "thunder" }
      }
    }
  ]
}
```

这个例子的含义是「露天、并且不是雷暴天」。

### 放进规则里

`conditions` 是规则的一个顶层字段，值就是上面那棵树；整条规则中只有它一个根。下面只列出与条件相关的字段，`effects` 等其余必填字段见 [规则基础字段](02-rule-basics.md) 与 [效果](04-effects.md)：

```json
{
  "id": "example:only_in_forest_at_night",
  "source": { "items": ["minecraft:apple"] },
  "conditions": {
    "op": "all_of",
    "terms": [
      {
        "op": "leaf",
        "condition": {
          "type": "itemdespawntowhat:biome",
          "mode": "exact",
          "biomes": ["#minecraft:is_forest"]
        }
      },
      {
        "op": "leaf",
        "condition": { "type": "itemdespawntowhat:time_of_day", "from": 13000, "to": 23000 }
      }
    ]
  },
  "trigger_after_seconds": 300
}
```

配完会发生什么：只有落在森林类群系、且世界时间处于夜晚（13000~23000 刻）时，苹果才会在该规则到点时被转化；其它情况下苹果照常呆着，到期后按原版机制消失。

无条件时，把 `conditions` 这一行整个删掉即可，不要写 `"conditions": {}` 或 `"conditions": []`。

### 旧写法已经不支持

下面这些写法都会被**明确报错**（不会静默兼容），遇到报错请按下表改写：

| 旧写法 | 现在怎么写 |
|---|---|
| `conditions` 直接是条件数组 `[ {...} ]` | 用 `all_of` / `any_of` 节点包起来 |
| `conditions.groups` 条件分组 | 用 `all_of` / `any_of` 节点包起来 |
| 条件叶里写 `negated` | 用 `{ "op": "inverted", "term": ... }` |
| `conditions` 下直接内联条件对象（直接写 `type`） | 包一层 `{ "op": "leaf", "condition": { ... } }` |

## 规模上限

| 项目 | 上限 | 计数方式 |
|---|---|---|
| 条件叶 | 128 | 每个 `leaf` 节点计 1 |
| 条件节点 | 256 | `all_of` / `any_of` / `inverted` / `leaf` 全都要算 |
| 条件树深度 | 16 | 根节点深度为 1，每往下一层加 1 |

超过任意一项的后果：**整条规则被拒载**（这条规则不生效），加载日志与 `/idtw config validate` 会给出「条件叶 / 节点 / 深度超出上限」的错误；同一个文件里的其它规则照常加载。`all_of` / `any_of` 的 `terms` 为空数组、`inverted` 没有 `term`，同样按结构非法处理、规则被拒载。

对配置的实际影响：把上百个条件平铺成一层 `all_of` 会撞上叶数上限；超过 16 层的嵌套（例如「并且」里再套「并且」）会被直接拒绝，需要改成更平的结构。

## 条件是怎么被判定的

条件树求值有四种结果，不是简单的「是 / 否」：

| 结果 | 含义 | 会发生什么 |
|---|---|---|
| 命中 | 条件确实成立 | 该规则参与选中；选中后触发转化 |
| 不命中 | 条件确实不成立 | 该规则被跳过 |
| 无法判定 | 当前情况不足以判断（例如周围区块未加载） | 该规则被跳过，**不会**当成成立 |
| 出错 | 判定过程中出现异常 | 该规则被跳过，并在日志里记录错误 |

只有**命中**才会触发转化。这一条对 `inverted` 也很重要：

- `inverted` **只把「命中」和「不命中」互换**，「无法判定」和「出错」两种结果原样保留。
- 所以「取反一个暂时判不了的条件」不会意外变成成立，也不会因此白白消耗掉材料。
- 组合节点：`all_of` 遇到子节点「不命中」立刻判为不命中；`any_of` 遇到子节点「命中」立刻判为命中；无论如何，只要最后没有确定结论，都是「无法判定」优先于「出错」，两者都不会让规则成立。

实际会看到「无法判定」的典型场景是 `surrounding_blocks`（六个相邻方块）：相邻区块还没加载时判不了，等区块加载后下一次检查就能给出结论。

### 条件不满足会自动重试

条件不满足时，模组不会放弃这条规则，而是**延后重试**：

- 重试间隔从「检查间隔」开始，每失败一次翻一倍，直到封顶。默认检查间隔 20 刻、退避上限 100 刻，也就是 1 秒 → 2 秒 → 4 秒 → 最多 5 秒。这两个数值由服务端配置的 `check_interval_ticks` 与 `backoff_max_ticks` 控制，见 [服务端配置](07-server-config.md)。
- 重试会一直持续到掉落物自然消失前一刻；到了自然消失时刻还是没有命中任何规则，掉落物就按原版机制消失，不再转化。

对配置的意义：

- 条件写成「晚上才生效」这类有窗口的规则是正常用法，掉落物会在窗口到来时被转化，不需要把 `trigger_after_seconds` 调得很小。
- 条件永远不可能成立的规则（例如维度写错）不会有任何提示，掉落物只是普通地消失；要确认条件写法是否正确，用 `/idtw config validate` 检查语法与参数，用 `/idtw config edit` 打开编辑器查看。
- 条件越严格，命中前的等待越久、丢失（掉落物被捡走或提前消失）的机会越大；这是配置取舍，不是故障。

## 条件类型速查

`type` 一律带命名空间前缀 `itemdespawntowhat:`。引用类参数（物品、方块、流体、群系）除 `dimension` 外都支持 `#` 开头的标签写法，例如 `"#minecraft:logs"`。

| `type` | 中文名 | 一句话作用 | 参数 |
|---|---|---|---|
| `itemdespawntowhat:dimension` | 维度 | 掉落物在指定维度之一 | `dimensions` |
| `itemdespawntowhat:biome` | 生物群系 | 群系匹配或气候参数落在区间内 | `mode`、`biomes`、六个气候区间 |
| `itemdespawntowhat:time_of_day` | 时间 | 世界时间落在区间内 | `from`、`to` |
| `itemdespawntowhat:weather` | 天气 | 当前天气等于指定值 | `weather` |
| `itemdespawntowhat:light_level` | 光照等级 | 所在位置光照等级落在区间内 | `min`、`max` |
| `itemdespawntowhat:y_level` | Y 高度 | 所在高度落在区间内 | `min`、`max` |
| `itemdespawntowhat:outdoor` | 露天 | 上方没有遮挡、能看到天空 | 无 |
| `itemdespawntowhat:surrounding_blocks` | 周围方块 | 相邻六个方向的方块匹配 | `up`、`down`、`north`、`south`、`east`、`west` |
| `itemdespawntowhat:fluid_present` | 存在流体 | 所在位置是某种流体 | `fluid`、`fluids`、`require_source` |
| `itemdespawntowhat:catalyst_present` | 存在催化物 | 掉落物所在方块格内有足够催化剂 | `items`、`count`、`counts` |

## 逐个条件类型

### dimension（维度）

掉落物所在维度命中 `dimensions` 列表中的任意一项即成立。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `dimensions` | 维度列表 | 维度 id 字符串数组，至少 1 项 | 无（必填） | 只接受维度 id，**不支持标签** |

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:dimension",
    "dimensions": ["minecraft:overworld", "minecraft:the_end"]
  }
}
```

典型场景：让某条规则只在下界生效；或配合 `inverted` 表示「不在主世界才生效」。

### biome（生物群系）

按掉落物所在方块位置的生物群系匹配，`mode` 决定用哪种方式。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `mode` | 匹配方式 | `exact` / `climate` | 无（必填） | |
| `biomes` | 群系引用列表 | 群系 id 或 `#` 标签数组 | 空 | `mode=exact` 时至少 1 项 |
| `temperature` | 温度区间 | `{"min": 数值, "max": 数值}`，取值 [-1, 1] | 不限制该参数 | 仅 `mode=climate` 有效 |
| `humidity` | 湿度区间 | 同上 | 不限制该参数 | 仅 `mode=climate` 有效 |
| `continentalness` | 大陆性区间 | 同上 | 不限制该参数 | 仅 `mode=climate` 有效 |
| `erosion` | 侵蚀度区间 | 同上 | 不限制该参数 | 仅 `mode=climate` 有效 |
| `depth` | 深度区间 | 同上 | 不限制该参数 | 仅 `mode=climate` 有效 |
| `weirdness` | 怪异度区间 | 同上 | 不限制该参数 | 仅 `mode=climate` 有效 |

- `exact`：取该位置的群系 id 逐个比对，命中任意一项即成立；可以写标签（如 `#minecraft:is_forest`）一次覆盖多个群系。
- `climate`：读取该位置的原版气候参数，**写了的参数必须全部落在各自区间内**才算成立；`mode=climate` 时至少要让一个参数的一端有界，`{ "temperature": {} }` 这种两端都空的区间等于没写。
- 区间两端都可以少写一端：只写 `min` 表示只卡下界，只写 `max` 表示只卡上界。
- `mode=exact` 时写了气候区间、或 `mode=climate` 时写了 `biomes`，这些多余内容会被忽略并给出告警——不报错，但也不起作用。
- 末地这类不是多噪声群系源的位置，`climate` 模式取不到气候参数，结果按「不命中」处理（不是「无法判定」）。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:biome",
    "mode": "exact",
    "biomes": ["#minecraft:is_forest"]
  }
}
```

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:biome",
    "mode": "climate",
    "temperature": { "min": 0.2 },
    "humidity": { "max": -0.2 }
  }
}
```

典型场景：`exact` 用于「只在这几个群系」；`climate` 用于「只在这种气候带」（例如炎热干燥的地方），不必逐个列群系。

### time_of_day（时间）

按世界时间（一天 24000 刻）的区间匹配。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `from` | 起始刻 | 整数 [0, 23999] | 无（必填） | |
| `to` | 结束刻 | 整数 [0, 23999] | 无（必填） | |

- `from` 不大于 `to` 时是普通区间（含两端）；`from` 大于 `to` 表示**跨零点**区间，例如 22000 → 2000，这不是错误。
- 参照时间：0 刻约为清晨 6:00，6000 刻为正午，12000 刻为傍晚，13000 刻起进入夜晚，18000 刻为午夜。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:time_of_day",
    "from": 13000,
    "to": 23000
  }
}
```

典型场景：只在夜晚转化；用 22000 → 2000 表示「后半夜那一段」。

### weather（天气）

按掉落物所在维度当前的天气匹配。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `weather` | 目标天气 | `clear` / `rain` / `thunder` | 无（必填） | 大小写不敏感，统一按小写读取 |

三种取值的确切含义：

- `clear`：既没有下雨、也没有雷暴。
- `rain`：正在下雨，且**不是**雷暴天。
- `thunder`：正在雷暴（此时通常也在下雨）。

也就是说，雷暴天不会命中 `rain`。想要「下雨或雷暴都算」，用 `any_of` 把 `rain` 和 `thunder` 并列。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:weather",
    "weather": "thunder"
  }
}
```

典型场景：雷雨天才会发生的转化；也会与 `outdoor` 搭配，避免在室内「被雨淋到」。

### light_level（光照等级）

按掉落物所在位置的光照等级（0~15）区间匹配。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `min` | 下限 | 整数 [0, 15] | 不限制 | 不得大于 `max` |
| `max` | 上限 | 整数 [0, 15] | 不限制 | |

光照口径是「天空光经过时间衰减后与方块光取较大值」，与实体、刷怪使用的光照等级一致；白天露天的地方数值高，夜里同一位置会变低。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:light_level",
    "max": 7
  }
}
```

典型场景：只在昏暗处转化（`max` 取 7 左右）；与 `outdoor` 配合可区分「白天露天」与「洞里点灯」。

### y_level（Y 高度）

按掉落物所在方块的 Y 坐标区间匹配。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `min` | 下限 | 整数，≥ -2048 | 不限制 | 不得大于 `max` |
| `max` | 上限 | 整数，≥ -2048 | 不限制 | |

高度参数不按世界限高设置上限，可填写超过 512 或 2048 的值，以兼容其它模组扩展的世界高度；参数仍使用 32 位有符号整数。GUI 滑条的 −64～512 仅是常用显示窗口。

两端都省略时条件恒成立（写法合法但没有意义）；只写一端就是「某高度以下 / 以上」。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:y_level",
    "max": 0
  }
}
```

典型场景：地下（`max` 为 0 或更低）才会发生的转化；高处（如 `min` 为 100）才生效的特殊规则。

### outdoor（露天）

判断掉落物上方是否暴露在天空下。**没有专属参数**。

判定方式：取该柱「阻挡移动但不含树叶」的最高面高度，不高于物品所在高度 +1 就算露天。因此树下仍可能算露天，而屋顶下、洞里不算。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:outdoor"
  }
}
```

典型场景：只在户外转化；需要「室内才生效」时用 `inverted` 包住它。

### surrounding_blocks（周围方块）

按掉落物上 / 下 / 北 / 南 / 东 / 西六个相邻方块匹配。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `up` | 上方方块 | 方块 id 或 `#` 标签 | 不限制该方向 | |
| `down` | 下方方块 | 同上 | 不限制该方向 | |
| `north` | 北侧方块 | 同上 | 不限制该方向 | |
| `south` | 南侧方块 | 同上 | 不限制该方向 | |
| `east` | 东侧方块 | 同上 | 不限制该方向 | |
| `west` | 西侧方块 | 同上 | 不限制该方向 | |

- 只检查填写的方向，**填了的方向必须全部命中**（方向之间是「并且」）。
- 六个方向全都不写属于非法配置，规则会被拒载。
- 相邻区块未加载时无法判定，结果不是「不命中」；等区块加载后重试会给出结论。这一点对使用 `inverted` 的写法尤其重要：判不了就是判不了，不会被取反成成立。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:surrounding_blocks",
    "down": "#minecraft:logs"
  }
}
```

典型场景：放在原木上、被石头包住、贴着某类方块等等。

### fluid_present（存在流体）

判断掉落物所在方块位置的流体。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `fluid` | 单个流体引用 | 流体 id 或 `#` 标签 | 空 | 与 `fluids` 同时写时以 `fluids` 为准 |
| `fluids` | 流体引用列表 | 流体 id 或 `#` 标签数组 | 空 | 命中任意一项即成立 |
| `require_source` | 是否只认源方块 | `true` / `false` | `true` | |

- `fluid` 与 `fluids` 都省略时表示「任意流体」，但所在位置必须有流体，否则仍不成立。
- 默认 `require_source` 为 `true`：只匹配流体源方块。想连流动的水一起匹配，把它设为 `false`；此时写 `minecraft:water` 也能匹配流动的水（同一流体的流动变体）。
- 写 `minecraft:empty` 不会匹配任何实际流体，加载时会给出告警。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:fluid_present",
    "fluid": "minecraft:water",
    "require_source": false
  }
}
```

典型场景：泡在水里才转化的规则；与催化剂条件组合，做出「水 + 骨粉」这类配方（可参考内置示例 `builtin_conditions.json`）。

### catalyst_present（存在催化物）

判断掉落物**所在方块格内**是否存在足够数量的指定催化剂物品。这是「在场检查」，不会消耗任何东西。

| 字段 | 作用 | 取值 / 范围 | 默认 | 说明 |
|---|---|---|---|---|
| `items` | 催化剂引用列表 | 物品 id 或 `#` 标签数组，至少 1 项 | 无（必填） | 每一项都要分别达标 |
| `count` | 数量门槛 | 整数 [1, 64] | 留空则跟随该规则里这种催化剂的消耗量；没有对应消耗配置时按 1 | |
| `counts` | 按引用分别设门槛 | 对象，键必须是 `items` 里出现过的引用，值 [1, 64] | 空 | 只覆盖这里列出的引用 |

- 统计范围是掉落物所在方块的 1×1×1 格内，**不包含掉落物自己**，也不统计已经死亡的实体；同一格内同一物品的多堆会累加。
- `count` 留空是推荐的写法：它会自动跟随规则里该催化剂的消耗量（规则级 `catalyst_cost` 或效果级 `consume_catalyst`）；两者都没有时才退到 1。留空不会写回文件，你自己的声明不会被改动。
- `counts` 里出现没写进 `items` 的引用会报错。

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:catalyst_present",
    "items": ["minecraft:bone_meal"],
    "count": 1
  }
}
```

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:catalyst_present",
    "items": ["minecraft:redstone", "minecraft:glowstone_dust"],
    "counts": { "minecraft:redstone": 4, "minecraft:glowstone_dust": 1 }
  }
}
```

典型场景：材料在场才转化的配方式规则。条件只负责判定，要真的扣掉催化剂，还需要配套的消耗效果（见 [效果](04-effects.md)）。

## 常用组合示例

每段都只给 `conditions` 的值，直接放进规则的 `conditions` 字段即可。

**夜晚或下雨才转化**（两个条件任一成立）——注意雷暴天不算 `rain`，所以把 `thunder` 也列上：

```json
{
  "op": "any_of",
  "terms": [
    {
      "op": "leaf",
      "condition": { "type": "itemdespawntowhat:time_of_day", "from": 13000, "to": 23000 }
    },
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:weather", "weather": "rain" } },
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:weather", "weather": "thunder" } }
  ]
}
```

**只在特定生物群系**（一次覆盖整类群系）：

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:biome",
    "mode": "exact",
    "biomes": ["#minecraft:is_taiga", "minecraft:swamp"]
  }
}
```

**只在某高度以下、而且是暗处**：

```json
{
  "op": "all_of",
  "terms": [
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:y_level", "max": 20 } },
    { "op": "leaf", "condition": { "type": "itemdespawntowhat:light_level", "max": 7 } }
  ]
}
```

**不在主世界，而且是在室内**（取反节点的用法）：

```json
{
  "op": "all_of",
  "terms": [
    {
      "op": "inverted",
      "term": {
        "op": "leaf",
        "condition": { "type": "itemdespawntowhat:dimension", "dimensions": ["minecraft:overworld"] }
      }
    },
    { "op": "inverted", "term": { "op": "leaf", "condition": { "type": "itemdespawntowhat:outdoor" } } }
  ]
}
```

**流动水里、旁边有骨粉**（模组自带的内置规则 `builtin_conditions.json` 就是这么写的，可以直接照着抄）：

```json
{
  "op": "all_of",
  "terms": [
    {
      "op": "leaf",
      "condition": {
        "type": "itemdespawntowhat:fluid_present",
        "fluid": "minecraft:water",
        "require_source": false
      }
    },
    {
      "op": "leaf",
      "condition": {
        "type": "itemdespawntowhat:catalyst_present",
        "items": ["minecraft:bone_meal"],
        "count": 1
      }
    }
  ]
}
```

**六面都被原木包住**（填了的方向必须全部命中）：

```json
{
  "op": "leaf",
  "condition": {
    "type": "itemdespawntowhat:surrounding_blocks",
    "up": "#minecraft:logs",
    "down": "#minecraft:logs",
    "north": "#minecraft:logs",
    "south": "#minecraft:logs",
    "east": "#minecraft:logs",
    "west": "#minecraft:logs"
  }
}
```
