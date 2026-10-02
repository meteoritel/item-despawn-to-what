# 配置参考：规则字段全表

> 事实来源：`core/api/RuleFields.java`、`core/model/CommonFields.java`、`core/type/effect/*`、`core/type/condition/*`、`core/config/ServerConfig.java`。
> 表中"默认值/区间"与代码常量一一对应；改代码必须同步本页。

## 1. 文件布局与三层作用域

优先级：**config 覆盖层 > 世界数据包 > 内置数据包**（同 id 后者覆盖前者）。

| 层 | 位置 | 可写 | 说明 |
|---|---|---|---|
| ① 内置数据包 | `data/<ns>/idtw/rules/**/*.json`（mod jar 内） | 否 | 默认与示例；本模组自带 6 个示例规则，全部 `enabled: false` |
| ② 世界数据包 | 存档 `datapacks/*/data/<ns>/idtw/rules/**/*.json` | 否 | 原版机制自动同步 |
| ③ config 覆盖层 | `config/itemdespawntowhat/rules/**/*.json` | **是** | GUI 保存目标；也可手工编辑 |

- 目录常量：数据包 `idtw/rules`，覆盖层 `rules`；扩展名统一 `.json`。
- 数据包层同名文件在多个包中的**副本全部参与合并**（不遮蔽）；合并顺序按 层优先级 → 包优先级 → 文件路径 → 文件内顺序。

## 2. 文件形状与 id

一个文件可以是**单条规则对象**或**规则数组**：

```json
{ "id": "mypack:stone_to_diamond", "source": { "items": ["minecraft:stone"] }, "effects": [ ... ] }
```

```json
[
  { "id": "mypack:a", "source": { "items": ["minecraft:dirt"] }, "effects": [ ... ] },
  { "id": "mypack:b", "source": { "items": ["minecraft:sand"] }, "effects": [ ... ] }
]
```

**id 推导规则**：

| 场景 | id 取值 |
|---|---|
| 规则含 `id` 字段 | 取该字段（必须是合法 `ResourceLocation`，如 `mypack:a`） |
| 文件只含 1 条规则且省略 `id` | 数据包：`<文件命名空间>:<去掉 idtw/rules/ 前缀与 .json 的相对路径>`；覆盖层：`<overlay_directory>:<相对 rules/ 的路径去扩展名>` |
| 文件含多条规则且某条省略 `id` | **该条拒载**（ERROR，多条目文件必须显式声明 id） |
| 同一文件内 id 重复 | 后者拒载（ERROR） |

## 3. 规则级字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | 字符串（ResourceLocation） | 否 | 由文件路径推导 | 稳定标识；覆盖/删除/日志/命令都以它为准 |
| `enabled` | 布尔 | 否 | `true` | `false` 的规则不进运行时索引（但可被 `config list` 读出） |
| `priority` | 整数 | 否 | `0` | 越大越优先；同优先级比条件叶数，再比定义序 |
| `notes` | 字符串 | 否 | 无 | 纯注释，不参与判定 |
| `source` | 对象 | **是** | — | 见 §4 |
| `conditions` | 二维数组（DNF） | 否 | `[]`（恒真） | 见 §5 |
| `trigger_after_seconds` | 整数（秒） | 否 | `300` | 触发时刻 = min(该值×20 刻，自然消失时刻)；负数报错 |
| `effects` | 数组 | **是** | — | 有序效果列表；为空则该规则不可运行（校验 ERROR） |
| `disabled` | 布尔 | 否 | `false` | **仅覆盖层**控制字段，见 §9 |
| `delete` | 布尔 | 否 | `false` | **仅覆盖层**控制字段，见 §9 |

## 4. `source`（源匹配）

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `items` | 字符串数组 | **是** | — | 物品 id（`minecraft:stone`）或物品标签（`#minecraft:logs`）；至少一项 |
| `exclude` | 字符串数组 | 否 | `[]` | 排除项，**优先于** `items` |

校验：`items` 内重复项、同一项同时出现在 `items` 与 `exclude` → ERROR。

```json
"source": { "items": ["#minecraft:logs", "minecraft:stick"], "exclude": ["minecraft:oak_log"] }
```

## 5. `conditions`（DNF 条件表达式）

形状：**二维数组**。外层数组是"条件组的析取（OR）"，内层数组是"条件叶的合取（AND）"；空数组 = 恒真。

```json
"conditions": [
  [ { "type": "itemdespawntowhat:outdoor" },
    { "type": "itemdespawntowhat:time_of_day", "from": 13000, "to": 23000 } ],
  [ { "type": "itemdespawntowhat:dimension", "dimensions": ["minecraft:the_nether"] } ]
]
```

校验：出现**空条件组**（`[]`）→ ERROR（空组恒真，会掩盖配置意图）。

**条件叶通用字段**：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | 字符串（ResourceLocation） | **是** | — | 已注册的条件类型 id |
| `negated` | 布尔 | 否 | `false` | 叶级取反 |

## 6. 效果级通用字段

所有 12 个效果共有，与类型专属参数处在**同一个 JSON 对象**中：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `type` | 字符串（ResourceLocation） | **是** | — | 已注册的效果类型 id |
| `delay_ticks` | 整数（刻） | 否 | `0` | 相对规则触发时刻的延迟；负数报错 |
| `chance` | 小数 | 否 | `1.0` | 执行概率，区间 `[0,1]`（解码期按 doubleRange 拦截） |
| `conditions` | 二维数组（DNF） | 否 | 无（无条件） | 效果级门槛，形状同 §5 |

**隐式消耗**：规则未声明**任何** `consume_*` 效果时，运行时会在派发声明效果前先执行一次 `consume_source(count=1)`。显式声明了任意消耗效果即按声明执行，不再隐式消耗。

## 7. 效果类型参数（12 个）

### 7.1 生效效果（9 个）

#### `spawn_item` — 生成物品

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `item` | 字符串 | **是** | — | 物品注册表 / 物品标签 | 支持 `#tag` |
| `count` | 整数 | 否 | `1` | `1..64` | 每个产物的堆叠数 |
| `limit` | 整数 | 否 | 不限制 | `1..4096` | 半径内同类产物上限 |
| `radius` | 整数（方块） | 否 | 不限制 | `1..32` | `limit` 的统计半径 |

#### `spawn_entity` — 生成实体

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `entity` | 字符串 | **是** | — | 实体类型注册表 / 标签 | 支持 `#tag` |
| `count` | 整数 | 否 | `1` | `1..64` | |
| `age` | 整数 | 否 | `0` | 无区间 | 负数表示幼体（原版 Babyable 语义） |
| `limit` | 整数 | 否 | 不限制 | `1..4096` | |
| `radius` | 整数（方块） | 否 | 不限制 | `1..32` | |

#### `place_block` — 放置方块

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `block` | 字符串 | 否 | 无 | 方块注册表 / 标签 | 与 `use_source_block` **至少其一** |
| `use_source_block` | 布尔 | 否 | `false` | — | 放置源物品对应的方块 |
| `shape` | 枚举 | 否 | `square` | `square` \| `circle` \| `cross` | 放置形状 |
| `count` | 整数 | 否 | `1` | `1..64` | |
| `radius` | 整数（方块） | 否 | `6` | `1..32` | 扩散放置半径 |
| `limit` | 整数 | 否 | 不限制 | `1..4096` | |

#### `spawn_xp` — 生成经验球

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `amount` | 整数 | 否 | `1` | `1..65536` | 经验量 |
| `per_source_item` | 布尔 | 否 | `false` | — | 为真时按源物品堆叠数量倍增 |

#### `loot_table` — 战利品表产出

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `loot_table` | 字符串（ResourceLocation） | **是** | — | — | 纯 id，**不支持 `#tag`** |
| `luck` | 小数 | 否 | `0.0` | `-100.0..100.0` | 幸运值修正 |

#### `lightning` — 召唤闪电

| 字段 | 类型 | 必填 | 默认值 | 区间 |
|---|---|---|---|---|
| `count` | 整数 | 否 | `1` | `1..16` |

#### `explosion` — 爆炸

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `power` | 小数 | 否 | `3.0` | `0.0..16.0` | 爆炸威力 |
| `fire` | 布尔 | 否 | `false` | — | 是否生成火焰 |
| `visual_only` | 布尔 | 否 | `false` | — | 只播放视觉与音效，不破坏方块、不伤害实体 |

#### `arrow_rain` — 箭雨

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `count` | 整数 | 否 | `16` | `1..256` | 箭矢数量 |
| `pickup` | 枚举 | 否 | `disallowed` | `disallowed` \| `allowed` \| `creative_only` | 拾取模式 |
| `potion_effects` | 数组 | 否 | 无 | — | 箭矢携带的药水效果，元素见下表 |

`potion_effects` 元素：

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `effect` | 字符串 | **是** | — | 药水效果注册表 / 标签 | 支持 `#tag` |
| `duration_ticks` | 整数（刻） | 否 | `100` | `1..1000000` | 持续刻数 |
| `amplifier` | 整数 | 否 | `0` | `0..255` | 等级（0 = I 级） |

#### `weather` — 切换天气

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `mode` | 枚举 | **是** | — | `rain` \| `clear` | 天气模式 |
| `duration_ticks` | 整数（刻） | 否 | `6000` | `1..24000` | 上限对齐原版一天 |
| `thundering` | 布尔 | 否 | `false` | — | 是否雷暴 |

### 7.2 消耗效果（3 个）

#### `consume_source` — 消耗源掉落物

| 字段 | 类型 | 必填 | 默认值 | 区间 |
|---|---|---|---|---|
| `count` | 整数 | 否 | `1` | `1..64` |

#### `consume_catalyst` — 消耗附近催化剂

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `items` | 字符串数组 | **是** | — | 物品注册表 / 标签 | 不得为空 |
| `count` | 整数 | 否 | `1` | `1..64` | |
| `radius` | 整数（方块） | 否 | `1` | `1..8` | 搜索半径 |

#### `consume_fluid` — 消耗流体

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `fluid` | 字符串 | 否 | 无（任意流体） | 流体注册表 / 标签 | 支持 `#tag`；`minecraft:empty` 永不匹配（WARN） |
| `require_source` | 布尔 | 否 | `true` | — | 为真时要求同位置存在源掉落物 |

**消耗效果重复**：同一规则内同一 `consume_*` 类型出现多次 → ERROR（该条拒载）。

## 8. 条件类型参数（10 个）

所有条件叶另有通用字段 `type`（必填）与 `negated`（默认 `false`）。

#### `dimension` — 维度

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dimensions` | 字符串数组 | **是** | 维度 id 列表，至少一项；命中任意一项即成立。纯 id，不支持 `#tag` |

#### `biome` — 生物群系（双模式）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `mode` | 枚举 | **是** | `exact`（按群系匹配）\| `climate`（按气候参数匹配） |
| `biomes` | 字符串数组 | `exact` 模式必填 | 群系 id 或 `#tag`，至少一项；`climate` 模式下填写会告警并忽略 |
| `temperature` | 区间对象 | `climate` 模式至少一个区间 | 温度 |
| `humidity` | 区间对象 | 同上 | 湿度 |
| `continentalness` | 区间对象 | 同上 | 大陆性 |
| `erosion` | 区间对象 | 同上 | 侵蚀度 |
| `depth` | 区间对象 | 同上 | 深度 |
| `weirdness` | 区间对象 | 同上 | 怪异度 |

区间对象形状 `{ "min": -1.0, "max": 1.0 }`：两端均可省略（省略 = 该端不限制），取值域 `[-1,1]`，`min` 不得大于 `max`。"至少一个区间有界"才算有效填写——`{ "temperature": {} }` 视为未填写。

采样点 = 掉落物所在方块位置，采样结果按位置缓存。

#### `weather` — 天气

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `weather` | 枚举 | **是** | `clear`（无雨无雷）\| `rain`（有雨无雷）\| `thunder`（只看雷暴） |

#### `outdoor` — 露天

无类型专属参数，仅 `negated`。

#### `surrounding_blocks` — 周围方块

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `up` | 字符串 | 否 | 方块 id 或 `#tag`；省略 = 该方向不限制 |
| `down` | 字符串 | 否 | 同上 |
| `north` | 字符串 | 否 | 同上 |
| `south` | 字符串 | 否 | 同上 |
| `east` | 字符串 | 否 | 同上 |
| `west` | 字符串 | 否 | 同上 |

六个方向**不得全空**（否则该条件无意义，ERROR）。

#### `catalyst_present` — 催化剂在场

| 字段 | 类型 | 必填 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|---|
| `items` | 字符串数组 | **是** | — | 物品注册表 / 标签 | 不得为空 |
| `count` | 整数 | 否 | `1` | `1..64` | 需要的最少数量 |

扫描范围为源物品所在方块格的 1×1×1，排除源物品自身与已死亡实体。

#### `fluid_present` — 流体在场

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `fluid` | 字符串 | 否 | 无（任意流体） | 流体 id 或 `#tag` |
| `require_source` | 布尔 | 否 | `true` | 只看流体源方块；为假时额外接受同族流动变体 |

#### `time_of_day` — 世界时间

| 字段 | 类型 | 必填 | 区间 | 说明 |
|---|---|---|---|---|
| `from` | 整数（刻） | **是** | `0..23999` | 区间起点 |
| `to` | 整数（刻） | **是** | `0..23999` | 区间终点；`from > to` 表示**跨零点**（合法，如 22000 → 2000） |

#### `y_level` — Y 坐标

| 字段 | 类型 | 必填 | 区间 | 说明 |
|---|---|---|---|---|
| `min` | 整数 | 否 | `-2048..2048` | 省略 = 该端不限制 |
| `max` | 整数 | 否 | `-2048..2048` | `min` 不得大于 `max` |

#### `light_level` — 光照等级

| 字段 | 类型 | 必填 | 区间 | 说明 |
|---|---|---|---|---|
| `min` | 整数 | 否 | `0..15` | 省略 = 该端不限制 |
| `max` | 整数 | 否 | `0..15` | `min` 不得大于 `max` |

口径：`LevelReader#getMaxLocalRawBrightness`（天空光按时间衰减后与方块光取较大值）。

## 9. 覆盖层控制字段（仅 config 覆盖层）

| 字段 | 类型 | 默认值 | 语义 |
|---|---|---|---|
| `disabled` | 布尔 | `false` | 停用同 id 的基底规则：保留基底内容、最终来源记为覆盖层、解码时注入 `enabled=false`；找不到同 id 基底 → WARN 并忽略 |
| `delete` | 布尔 | `false` | 删除同 id 的基底规则（幂等：基底不存在时为 no-op） |

规则：

- 两个字段**只能出现在覆盖层**；出现在数据包层 → 该条拒载（ERROR）。
- 同一条同时写 `disabled: true` 与 `delete: true` → 该条拒载（ERROR）。
- 文件加载中的控制条目只识别 `id` / `disabled` / `delete`，夹带其它字段会 WARN；网络提交则严格要求仅 id 和一个值为 true 的控制字段，否则整批拒绝。
- 想让一条**新规则**处于停用状态，用规则级 `enabled: false`，不要用 `disabled`。
- 同一来源层内**普通条目先合并、控制条目后应用**，因此控制语义与文件名排序无关。

```json
// config/itemdespawntowhat/rules/overrides.json
[
  { "id": "mypack:stone_to_diamond", "disabled": true },
  { "id": "mypack:legacy_rule", "delete": true }
]
```

## 10. 模组级配置 `config/itemdespawntowhat/server.json`

与规则配置分离；文件缺失时自动写出默认值。

| 字段 | 类型 | 默认值 | 区间 | 说明 |
|---|---|---|---|---|
| `check_interval_ticks` | 整数（刻） | `20` | `1..1200` | 失败退避基准与暂不 ticking 时的重查间隔 |
| `backoff_max_ticks` | 整数（刻） | `100` | `1..72000` | 退避上限（5 秒） |
| `max_checks_per_tick` | 整数 | `512` | `1..100000` | 每维度、每类队列的任务访问数上限，超限保留队列等待后续 tick |
| `overlay_directory` | 字符串 | `"itemdespawntowhat"` | — | 覆盖层目录名（相对 config/） |
| `fabric_lifespan_fallback_ticks` | 整数（刻） | `6000` | `1..72000` | Fabric 端 lifespan 兜底值（NeoForge 读取实体当前 `lifespan`） |
| `debug_logging` | 布尔 | `false` | — | 调试日志开关 |

退避序列：`base << n` 后按 `backoff_max_ticks` 封顶，`base = max(1, check_interval_ticks)`。

## 11. 完整示例

覆盖层文件 `config/itemdespawntowhat/rules/example/nether_quartz.json`：

```json
[
  {
    "id": "mypack:quartz_in_nether",
    "enabled": true,
    "priority": 10,
    "notes": "下界露天且是夜晚时，石头变成石英",
    "trigger_after_seconds": 60,
    "source": {
      "items": ["minecraft:stone"],
      "exclude": ["minecraft:stone_slab"]
    },
    "conditions": [
      [
        { "type": "itemdespawntowhat:dimension", "dimensions": ["minecraft:the_nether"] },
        { "type": "itemdespawntowhat:outdoor" },
        { "type": "itemdespawntowhat:time_of_day", "from": 13000, "to": 23000 }
      ]
    ],
    "effects": [
      { "type": "itemdespawntowhat:spawn_item", "item": "minecraft:quartz", "count": 2, "limit": 64, "radius": 8 },
      { "type": "itemdespawntowhat:spawn_xp", "amount": 3, "delay_ticks": 20, "chance": 0.5 }
    ]
  }
]
```

## 12. 校验与拒载规则速查

| 情况 | 结果 |
|---|---|
| 非法 JSON / 顶层非对象或数组 | 整文件拒载（ERROR） |
| 缺少 `source` 或 `effects`、`source.items` 为空、`effects` 为空 | 该条拒载（ERROR） |
| `id` 非法 / 多条目缺 `id` / 同文件 id 重复 | 该条拒载（ERROR） |
| `conditions` 含空组 | 该条拒载（ERROR） |
| `trigger_after_seconds` 为负、`delay_ticks` 为负、`chance` 越界 | 该条拒载（ERROR） |
| 未注册的 `type` | 该条拒载（ERROR） |
| 非标签引用指向未注册 id | 该条拒载（ERROR） |
| 标签引用未定义 | WARN（仅在标签数据已绑定时判定） |
| 顶层未知字段 | WARN |

## 13. 命名与单位规范

- 全小写 `snake_case`；资源引用写 `minecraft:x` 或 `#minecraft:x`。
- **时间单位必须体现在字段名里**：规则触发用**秒**（`trigger_after_seconds`），效果延迟用**刻**（`delay_ticks`）。
- 枚举取值统一小写下划线，解析**大小写不敏感**（`EnumCodecs.lowerCase`）。
- `limit` / `radius` 是**效果自身参数**（不是规则级字段），默认值与是否声明按类型语义决定。


## 后端切换后的执行与校验约定（2026-10-02）

- GUI 暂为占位入口；通过 JSON 管理规则，用 `/idtw config validate` 校验，再 `/idtw config reload` 生效。
- 每条规则按实际 age 判断自己的触发时间，秒转 tick 使用 long；无限寿命物品与带死亡/已转化标记的实体不追踪。
- 每个维度有独立的检查队列与效果队列，各自每刻最多访问 `max_checks_per_tick` 个任务（含取消条目），且各有 2ms 的软时间预算。预算不能抢占单次原版爆炸、实体查询或第三方执行器。
- 物品每批最多 16 个实体、生物每批 8 个、经验每批 4096 点、战利品每批一次开表；方块计数与放置每批访问 128 个位置。剩余数量继续排队。
- 效果按列表顺序启动；延迟或分批工作可跨刻交错完成。非事务效果、limit 收敛、概率或效果级条件失败不会回滚其它已完成效果。
- 方块 limit 在放置任务计数阶段确定，分批期间的外部世界修改可能改变实际邻域数量；它不是全局配额。实体/物品每批复核邻近余量。
- 自然寿命到期但最后判定仍在队列时暂缓 discard；队列最终决定转化或自然移除，数量/时间预算仍生效。
- reload 保留已提交效果，只重建检查、索引及标签/气候缓存。未加载目标区块等待，维度卸载或服务器停止清除该维度任务；任务不持久化。
- source 直接物品、动态 biome/dimension、战利品表存在性均校验；效果和条件顶层未知参数拒载，规则顶层未知字段告警。规则最多 32 个效果、128 个条件叶、256 个源项；每个效果级表达式最多 128 个条件叶。
- `debug stats` 分队列显示待处理量、峰值、访问任务数（含已取消条目）、本刻/最大耗时及最老就绪任务延期。毫秒/P95/P99/TPS 需实机压测。
