# 条件（conditions）

本页说明 `conditions` 字段怎么写，以及模组在什么时候判定它是否命中。

规则的其它字段见 [规则基础字段](zh-02-rule-basics)；效果与材料消耗见 [效果](zh-04-effects) 与 [候选结果与结算](zh-05-outcomes-and-settlement)。

## 条件负责什么

- 条件只负责判断，不消耗任何物品或流体；真正消耗材料的是消耗类效果。
- 整个 `conditions` 字段省略表示无条件，任何时刻都命中。
- 只有命中时规则才会被选中并触发转化；不命中、不可用、错误都不会触发。
- 条件长期不命中不是错误，只是这条规则很少生效。
- 条件写法不合法是另一回事：结构错误或参数越界会让整条规则拒载，加载日志与 `/idtw config validate` 会给出原因。

## 条件树的四种节点

整棵条件树只有一个根节点。每个节点都是一个 JSON 对象，用 `op` 说明它是哪种节点：

| `op` | 含义 | 子节点字段 | 该层允许出现的字段 |
|---|---|---|---|
| `all_of` | 全部子节点都命中才命中（并且） | `terms` | `op`、`terms` |
| `any_of` | 任一子节点命中即命中（或者） | `terms` | `op`、`terms` |
| `inverted` | 把子节点的命中与不命中互换（取反） | `term` | `op`、`term` |
| `leaf` | 一个具体条件 | `condition` | `op`、`condition` |

三条容易踩错的地方：

- `terms` 是数组（`all_of`、`any_of` 用），`term` 是单个节点（`inverted` 用），`condition` 是条件对象（`leaf` 用）；写错字段会让整条规则拒载。
- 条件对象是扁平的：`type` 与这个类型自己的参数写在同一个对象里，不需要再包一层。
- 每个节点只允许出现上表列出的字段，多写一个字段（例如在 `leaf` 里写 `terms`）会被拒绝。`all_of`、`any_of` 的 `terms` 不能是空数组；无条件请直接省略 `conditions` 字段。

### 条件叶（leaf）

`leaf` 的 `condition` 是一个由 `type` 和该类型自己的参数组成的对象，`type` 一律带命名空间前缀 `idtw:`：

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:dimension",
    "dimensions": ["minecraft:overworld"]
  }
}
```

### 组合节点（all_of、any_of、inverted）

```json
{
  "op": "all_of",
  "terms": [
    { "op": "leaf", "condition": { "type": "idtw:outdoor" } },
    {
      "op": "inverted",
      "term": {
        "op": "leaf",
        "condition": { "type": "idtw:weather", "weather": "thunder" }
      }
    }
  ]
}
```

这个例子的含义是「露天，并且不是雷暴天」。

### 把条件树放进规则

`conditions` 是规则的一个顶层字段，值就是上面那棵树，整条规则中只有这一个根节点。下面只列出与条件有关的字段，其余必填字段见 [规则基础字段](zh-02-rule-basics)：

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
          "type": "idtw:biome",
          "mode": "exact",
          "biomes": ["#minecraft:is_forest"]
        }
      },
      {
        "op": "leaf",
        "condition": { "type": "idtw:time_of_day", "from": 13000, "to": 23000 }
      }
    ]
  },
  "trigger_after_seconds": 300
}
```

配完会发生什么：只有落在森林类群系、且世界时间处于夜晚（13000～23000 刻）时，苹果才会在这条规则到点时被转化；其它情况下苹果照常待在原地，到期后按原版机制消失。

无条件时把 `conditions` 这一行整个删掉即可，不要写 `"conditions": {}` 或 `"conditions": []`。

### 旧写法不再支持

下面这些写法都会被明确报错，不会静默兼容；遇到报错请按下表改写：

| 旧写法 | 现在怎么写 |
|---|---|
| `conditions` 直接写成 JSON 数组 `[ {...} ]` | 用 `all_of`、`any_of` 节点包起来 |
| `conditions` 下的 `groups` 条件分组 | 用 `all_of`、`any_of` 节点包起来 |
| 条件叶里写 `negated` | 用 `{ "op": "inverted", "term": ... }` |
| `conditions` 下直接内联条件对象（直接写 `type`） | 包一层 `{ "op": "leaf", "condition": { ... } }` |

## 条件规模上限

| 项目 | 上限 | 计数方式 |
|---|---|---|
| 条件叶 | 128 | 每个 `leaf` 节点计 1 |
| 条件节点 | 256 | `all_of`、`any_of`、`inverted`、`leaf` 全都要算 |
| 条件树深度 | 16 | 根节点深度为 1，每往下一层加 1 |

超过任意一项的后果是整条规则拒载（这条规则不生效），加载日志与 `/idtw config validate` 会给出条件叶、节点或深度超出上限的错误；同一个文件里的其它规则照常加载。`all_of`、`any_of` 的 `terms` 是空数组，或 `inverted` 没有 `term`，同样按结构非法处理，规则被拒载。

对配置的实际影响：把上百个条件平铺成一层 `all_of` 会超出条件叶上限；超过 16 层的嵌套（例如并且里再套并且）会被直接拒绝，需要改成更平的结构。

## 条件的判定结果

条件树的判定有四种结果，不是只有命中与不命中两种：

| 结果 | 含义 | 会发生什么 |
|---|---|---|
| 命中 | 条件判断为真 | 规则参与选中，选中后触发转化 |
| 不命中 | 条件判断为假 | 这条规则本轮不会命中，会在之后的检查里重试 |
| 不可用 | 现在判不了，例如周围区块还没加载完 | 这条规则本轮不会命中，会在之后的检查里重试，不会当成命中 |
| 错误 | 判定过程中出现异常 | 规则被跳过，并在日志里记录错误 |

只有命中才会触发转化，这一点对 `inverted` 也很重要：

- `inverted` 只把命中与不命中互换，不可用与错误原样保留。
- 所以取反一个暂时判不了的条件不会意外变成命中，也不会白白消耗材料。
- 组合节点的结论规则：`all_of` 遇到子节点不命中就立刻判为不命中；`any_of` 遇到子节点命中就立刻判为命中；其余情况按不可用优先于错误的顺序收尾，两者都不会让规则命中。

实际会遇到不可用的典型场景是 `surrounding_blocks`（周围方块）：相邻区块还没加载时判不了，等区块加载后下一次检查就能给出结论。

### 条件不命中会自动重试

条件不命中时，模组不会放弃这条规则，而是延后重试：

- 退避从检查间隔开始，每失败一次翻一倍，直到封顶。默认检查间隔 20 刻、退避上限 100 刻，也就是 1 秒 → 2 秒 → 4 秒 → 最多 5 秒；这两个数值由服务端配置的 `check_interval_ticks` 与 `backoff_max_ticks` 决定，见 [服务端配置](zh-07-server-config)。
- 重试一直持续到掉落物自然消失前一刻；到自然消失时刻仍未命中任何规则，掉落物按原版机制消失，不再转化。

把条件写成「只在夜晚生效」这类有窗口的规则是正常用法，掉落物会在窗口到来时被转化，不需要把 `trigger_after_seconds` 调得很小。条件越严格，命中前的等待越久，掉落物被捡走或提前消失的机会越大；这是配置取舍，不是故障。

条件永远不可能命中的规则不会有任何提示，掉落物只是普通地消失。想确认写法，用 `/idtw config validate` 检查语法与参数，或用 `/idtw config edit` 在编辑器里查看这条规则。

## 条件类型速查

`type` 一律带命名空间前缀 `idtw:`。引用类参数（物品、方块、流体、群系）除 `dimension` 外都支持 `#` 开头的标签写法，例如 `"#minecraft:logs"`。

| `type` | 中文名 | 作用 | 参数 |
|---|---|---|---|
| `idtw:dimension` | 维度 | 掉落物在指定维度之一 | `dimensions` |
| `idtw:biome` | 生物群系 | 群系匹配，或气候参数落在区间内 | `mode`、`biomes`、六个气候区间 |
| `idtw:time_of_day` | 时间 | 世界时间落在区间内 | `from`、`to` |
| `idtw:weather` | 天气 | 当前天气等于指定值 | `weather` |
| `idtw:light_level` | 光照等级 | 所在位置的光照等级落在区间内 | `min`、`max` |
| `idtw:y_level` | Y 高度 | 所在高度落在区间内 | `min`、`max` |
| `idtw:outdoor` | 露天 | 上方没有遮挡、能看到天空 | 无 |
| `idtw:surrounding_blocks` | 周围方块 | 上下前后左右六个相邻方块匹配 | `up`、`down`、`north`、`south`、`east`、`west` |
| `idtw:fluid_present` | 存在流体 | 所在位置有指定流体 | `fluid`、`fluids` |
| `idtw:catalyst_present` | 存在催化剂 | 共享半径内有足够催化剂 | `items`、`count`、`counts` |

## 逐个条件类型

### dimension（维度）

掉落物所在维度在 `dimensions` 里有任意一项对得上，就算命中。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `dimensions` | 维度列表 | 维度 id 字符串数组，至少 1 项 | 必填 |

只接受维度 id，不支持 `#` 标签写法。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:dimension",
    "dimensions": ["minecraft:overworld", "minecraft:the_end"]
  }
}
```

典型场景：让某条规则只在下界生效；或配合 `inverted` 表示「不在主世界才生效」。

### biome（生物群系）

按掉落物所在方块位置的生物群系匹配，`mode` 决定用哪种方式。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `mode` | 匹配方式 | `exact` 或 `climate` | 必填 |
| `biomes` | 群系引用列表 | 群系 id 或 `#` 标签数组，`mode=exact` 时至少 1 项 | 空 |
| `temperature` | 温度区间 | `{"min": 数值, "max": 数值}`，取值 -1～1 | 不限制该参数 |
| `humidity` | 湿度区间 | 同上 | 不限制该参数 |
| `continentalness` | 大陆性区间 | 同上 | 不限制该参数 |
| `erosion` | 侵蚀度区间 | 同上 | 不限制该参数 |
| `depth` | 深度区间 | 同上 | 不限制该参数 |
| `weirdness` | 怪异度区间 | 同上 | 不限制该参数 |

- `exact`：取该位置的生物群系 id 逐个比对，任意一项对得上就算命中；可以写标签（例如 `#minecraft:is_forest`）一次覆盖多个群系。
- `climate`：读取该位置的原版气候参数，只有写了的参数全部落在各自区间内才命中；此时至少要有一个参数的一端有界，`{ "temperature": {} }` 这种两端都空的区间等于没写。
- 区间两端都可以少写一端：只写 `min` 表示只卡下界，只写 `max` 表示只卡上界。
- `mode=exact` 时写了气候区间，或 `mode=climate` 时写了 `biomes`，这些多余内容会被忽略并给出告警：不报错，但也不起作用。
- 末地这类不是多噪声群系源的位置在 `climate` 模式下取不到气候参数，结果按不命中处理，不是不可用。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:biome",
    "mode": "exact",
    "biomes": ["#minecraft:is_forest"]
  }
}
```

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:biome",
    "mode": "climate",
    "temperature": { "min": 0.2 },
    "humidity": { "max": -0.2 }
  }
}
```

典型场景：`exact` 用于只在这几个群系，`climate` 用于只在这种气候带（例如炎热干燥的地方），不必逐个列群系。

### time_of_day（时间）

按世界时间（一天 24000 刻）的区间匹配。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `from` | 起始刻 | 整数 0～23999 | 必填 |
| `to` | 结束刻 | 整数 0～23999 | 必填 |

- `from` 不大于 `to` 时是普通区间（含两端）；`from` 大于 `to` 表示跨零点区间，例如 22000～2000，这不是错误。
- 参照时间：0 刻约为清晨 6 点，6000 刻为正午，12000 刻为傍晚，13000 刻起进入夜晚，18000 刻为午夜。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:time_of_day",
    "from": 13000,
    "to": 23000
  }
}
```

典型场景：只在夜晚转化；用 22000～2000 表示后半夜那一段。

### weather（天气）

按掉落物所在维度当前的天气匹配。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `weather` | 目标天气 | `clear`、`rain` 或 `thunder` | 必填 |

三种取值的确切含义：

- `clear`：既没有下雨，也没有雷暴。
- `rain`：正在下雨，且不是雷暴天。
- `thunder`：正在雷暴，此时通常也在下雨。

也就是说，雷暴天不会命中 `rain`。想让下雨或雷暴都算，用 `any_of` 把 `rain` 与 `thunder` 并列。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:weather",
    "weather": "thunder"
  }
}
```

典型场景：雷雨天才会发生的转化；也可以与 `outdoor` 搭配，避免在室内被雨淋到。

### light_level（光照等级）

按掉落物所在位置的光照等级（0～15）区间匹配。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `min` | 下限 | 整数 0～15 | 不限制 |
| `max` | 上限 | 整数 0～15 | 不限制 |

光照口径是天空光经过时间衰减后与方块光取较大值，与实体、刷怪使用的光照等级一致；白天露天的地方数值高，夜里同一位置会变低。`min` 不得大于 `max`。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:light_level",
    "max": 7
  }
}
```

典型场景：只在昏暗处转化（`max` 取 7 左右）；与 `outdoor` 配合可以区分白天露天与洞里点灯。

### y_level（Y 高度）

按掉落物所在方块的 Y 坐标区间匹配。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `min` | 下限 | 整数，不小于 -2048 | 不限制 |
| `max` | 上限 | 整数，不小于 -2048 | 不限制 |

高度参数不设上限，可以填写超过 512 或 2048 的值，以兼容其它模组扩展的世界高度；参数仍使用 32 位有符号整数。`min` 不得大于 `max`。

两端都省略时条件恒命中（写法合法，但没有意义）；只写一端就是某高度以下或以上。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:y_level",
    "max": 0
  }
}
```

典型场景：地下的转化（`max` 为 0 或更低）；高处才生效的特殊规则（`min` 为 100）。

### outdoor（露天）

判断掉落物上方是否暴露在天空下。这个条件没有专属参数。

判定方式是取该柱「阻挡移动但不含树叶」的高度图最高面，不高于物品所在高度 +1 就算露天。因此树下仍可能算露天，而屋顶下与洞里不算。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:outdoor"
  }
}
```

典型场景：只在户外转化；需要室内才生效时，用 `inverted` 包住它。

### surrounding_blocks（周围方块）

按掉落物上、下、北、南、东、西六个相邻方块匹配。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `up` | 上方方块 | 方块 id 或 `#` 标签 | 不限制该方向 |
| `down` | 下方方块 | 同上 | 不限制该方向 |
| `north` | 北侧方块 | 同上 | 不限制该方向 |
| `south` | 南侧方块 | 同上 | 不限制该方向 |
| `east` | 东侧方块 | 同上 | 不限制该方向 |
| `west` | 西侧方块 | 同上 | 不限制该方向 |

- 只检查填写的方向，填了的方向必须全部命中（方向之间是并且）。
- 六个方向全都不写属于非法配置，规则会被拒载。
- 相邻区块未加载时判不了，结果是不可用而不是不命中；等区块加载后重试会给出结论。这一点对使用 `inverted` 的写法尤其重要：判不了就是判不了，不会被取反成命中。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:surrounding_blocks",
    "down": "#minecraft:logs"
  }
}
```

典型场景：放在原木上、被石头包住、贴着某类方块等等。

### fluid_present（存在流体）

判断掉落物所在方块位置的流体。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `fluid` | 单个流体引用 | 流体 id 或 `#` 标签 | 空 |
| `fluids` | 流体引用列表 | 流体 id 或 `#` 标签数组，命中任意一项即可 | 空 |

- `fluid` 与 `fluids` 都省略时表示任意流体，但所在位置必须有流体，否则仍不命中；两者同时写时以 `fluids` 为准。
- 源流体与流动流体使用同一存在判定；`minecraft:water` 接受同族流动变体。
- 写 `minecraft:empty` 不会匹配任何实际流体，加载时会给出告警。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:fluid_present",
    "fluid": "minecraft:water"
  }
}
```

典型场景：泡在水里才转化的规则；与存在催化剂条件组合，做出水加骨粉这类配方（模组自带的内置规则 `idtw:builtin_catalyst_and_fluid` 就用了这个组合）。

### catalyst_present（存在催化剂）

判断规则共享半径内是否存在足够数量的指定催化剂物品。这是在场检查，不会消耗任何东西。

| 字段 | 作用 | 取值 | 默认值 |
|---|---|---|---|
| `items` | 催化剂引用列表 | 物品 id 或 `#` 标签数组，至少 1 项 | 必填 |
| `count` | 数量门槛 | 整数 1～64 | 留空则采用规则 `catalyst_cost.counts`；未配置引用时为 1 |
| `counts` | 按引用分别设门槛 | 对象，键必须是 `items` 里出现过的引用，值 1～64 | 空 |

- `items` 里的每一项都要分别达标。
- 统计范围使用规则 `catalyst_cost.radius`，缺省为 1；排除源实体，范围内同类多堆会累加。
- `count` 留空时采用规则门槛，实际消耗量独立配置；解析值不反写声明。
- `counts` 里出现没写进 `items` 的引用会报错。

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:catalyst_present",
    "items": ["minecraft:bone_meal"],
    "count": 1
  }
}
```

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:catalyst_present",
    "items": ["minecraft:redstone", "minecraft:glowstone_dust"],
    "counts": { "minecraft:redstone": 4, "minecraft:glowstone_dust": 1 }
  }
}
```

典型场景：材料在场才转化的配方式规则。条件只负责判定，实际消耗由规则 `catalyst_cost.consume_counts` 控制，见[候选结果与结算](zh-05-outcomes-and-settlement)。

## 常用组合示例

每段都只给 `conditions` 的值，直接放进规则的 `conditions` 字段即可。

夜晚或下雨才转化（两个条件任一命中）——注意雷暴天不算 `rain`，所以把 `thunder` 也列上：

```json
{
  "op": "any_of",
  "terms": [
    {
      "op": "leaf",
      "condition": { "type": "idtw:time_of_day", "from": 13000, "to": 23000 }
    },
    { "op": "leaf", "condition": { "type": "idtw:weather", "weather": "rain" } },
    { "op": "leaf", "condition": { "type": "idtw:weather", "weather": "thunder" } }
  ]
}
```

只在特定生物群系（一次覆盖整类群系）：

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:biome",
    "mode": "exact",
    "biomes": ["#minecraft:is_taiga", "minecraft:swamp"]
  }
}
```

只在某高度以下、而且是暗处：

```json
{
  "op": "all_of",
  "terms": [
    { "op": "leaf", "condition": { "type": "idtw:y_level", "max": 20 } },
    { "op": "leaf", "condition": { "type": "idtw:light_level", "max": 7 } }
  ]
}
```

不在主世界，而且是在室内（取反节点的用法）：

```json
{
  "op": "all_of",
  "terms": [
    {
      "op": "inverted",
      "term": {
        "op": "leaf",
        "condition": { "type": "idtw:dimension", "dimensions": ["minecraft:overworld"] }
      }
    },
    { "op": "inverted", "term": { "op": "leaf", "condition": { "type": "idtw:outdoor" } } }
  ]
}
```

流动水里、旁边有骨粉（模组自带的内置规则 `idtw:builtin_catalyst_and_fluid` 的 `conditions` 就是这么写的，可以直接照抄）：

```json
{
  "op": "all_of",
  "terms": [
    {
      "op": "leaf",
      "condition": {
        "type": "idtw:fluid_present",
        "fluid": "minecraft:water"
      }
    },
    {
      "op": "leaf",
      "condition": {
        "type": "idtw:catalyst_present",
        "items": ["minecraft:bone_meal"],
        "count": 1
      }
    }
  ]
}
```

六面都被原木包住（填了的方向必须全部命中）：

```json
{
  "op": "leaf",
  "condition": {
    "type": "idtw:surrounding_blocks",
    "up": "#minecraft:logs",
    "down": "#minecraft:logs",
    "north": "#minecraft:logs",
    "south": "#minecraft:logs",
    "east": "#minecraft:logs",
    "west": "#minecraft:logs"
  }
}
```

## 相关

- 规则顶层字段与来源写法：[规则基础字段](zh-02-rule-basics)
- 效果与材料消耗：[效果](zh-04-effects)
- 转化结果与返还流程：[候选结果与结算](zh-05-outcomes-and-settlement)
