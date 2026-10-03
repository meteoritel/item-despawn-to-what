# P2 条件与效果表单规格（P5 客户端编辑器实现依据）

> 后续规格（2026-10-03）：新 GUI 的字段域、单位与控件映射以已冻结的 [新字段规格](plan-gui-rule-update-fields.md)为准；本文保留原实施基线，现行条件树与扩展约束继续按新主计划指定范围沿用。
>
> 生成者：tree-dev（P2 条件系统重构负责人），生成日期：2026-10-02。
> 事实来源：`common/src/main/java/com/meteorite/itemdespawntowhat/core/type/{condition,effect}/**` 的 record 组件与 RecordCodecBuilder 字段名，以及 `core/model/CommonFields.java`、`core/api/RuleFields.java`、`core/model/ConditionLimits.java`、`core/model/RuleValidation.java`、`core/runtime/ExpressionTreeEvaluator.java`。
> 冻结契约：`docs/plan/plan-frontend-rewrite-contract.md` 第 2 节（条件树）、第 5 节（客户端 UI）。本文件只做表单规格，不改任何 Java / JSON 资源。
> **所有字段名与代码里的 JSON 键逐字一致**（表单能被后端接受的前提）；第 10 节的 10 项产品/跨模块问题已由 lead 于 2026-10-02 全部裁决（同时写入契约 §5.1 / §5.2），本文件已按裁决更新，不再是待定项。

## 1. 条件树：编辑器必须生成的 JSON 形状

四种节点（`op` 是节点类型字段，未知取值会被明确拒绝并列出允许值）：

    { "op": "all_of",   "terms": [ <node>, ... ] }   // 组内 AND，terms 至少 1 个
    { "op": "any_of",   "terms": [ <node>, ... ] }   // 组内 OR，terms 至少 1 个
    { "op": "inverted", "term": <node> }             // 取反，恰好 1 个子节点
    { "op": "leaf",     "condition": { <条件对象> } } // 叶；condition 内 type 与类型专属字段同层（扁平）

完整示例（规则级 `conditions`；效果级 `conditions` 形状完全相同，只是嵌在效果对象内部）：

    "conditions": {
      "op": "all_of",
      "terms": [
        { "op": "leaf", "condition": { "type": "itemdespawntowhat:outdoor" } },
        { "op": "inverted", "term": { "op": "leaf",
            "condition": { "type": "itemdespawntowhat:weather", "weather": "clear" } } },
        { "op": "any_of", "terms": [ <node>, <node> ] }
      ]
    }

叶节点就是「条件对象」本身扁平写进 `condition`：`type` + 该类型的专属字段，例如
`{ "op": "leaf", "condition": { "type": "itemdespawntowhat:y_level", "min": 60, "max": 320 } }`。

### 1.1 硬性约定（UI 必须遵守，否则保存被拒）

| # | 约定 | 后端行为 / 文案 |
| --- | --- | --- |
| 1 | 规则/效果没有任何条件时**省略** `conditions` 字段 | 不要写 `null`、不要写 `[]`；后端解码到空数组会报「空条件组请直接省略 conditions 字段」 |
| 2 | 空 `all_of` / `any_of` 只作编辑中间态 | 内存里可存在（便于分步编辑），保存前必须补足 terms；解码期 `terms: []` 报「op=X 的 terms 不能为空」 |
| 3 | 叶级 `negated` 已彻底删除 | 叶内出现 `negated` 立即解码报错并提示改用 `inverted`；UI 不要再提供叶级「取反」勾选框 |
| 4 | 取反只能用 `inverted` 节点 | 恰好 1 个 `term`；空或缺失报错 |
| 5 | 旧格式一律拒绝 | `conditions` 为二维数组、含 `groups` 字段、叶级 `negated` 都明确报错，解码层不静默兼容 |
| 6 | 未知字段报错 | 节点级未知字段（如 `all_of` 里写 `term`）、叶内未知字段（叶只允许 `type` + 专属字段）都报错 |
| 7 | 深度从 1 起算 | 单叶深度 1，空表达式深度 0；超过 16 报超限 |
| 8 | 节点上限 | 叶 ≤ 128、节点 ≤ 256、深度 ≤ 16（详见 1.2） |
| 9 | 深度 > 6 的提示 | 不额外硬限（后端上限仍是 16）；深度 > 6 时节点旁显示「层级较深」即可（裁决 10.7） |

### 1.2 用量上限（core/model/ConditionLimits.java，保存前必须拦截）

| 常量 | 值 | 含义 |
| --- | --- | --- |
| MAX_EFFECTS | 32 | 一条规则的 effects 条数 |
| MAX_LEAVES | 128 | **一个**条件表达式的叶节点数 |
| MAX_NODES | 256 | **一个**条件表达式的节点总数（含组合与 inverted） |
| MAX_DEPTH | 16 | **一个**条件表达式的最大深度（根深度 = 1） |
| MAX_SOURCE_ENTRIES | 256 | source.items + source.exclude 条目总数 |
| MAX_DISPLAY_NAME_CODEPOINTS | 128 | display_name 的码点数（按 code point，不是字节数） |

上限按**逐表达式**计（裁决 10.6）：规则级条件树与每个效果级条件树各自受限，不跨表达式累加。

### 1.3 求值四态（只有 MATCH 触发效果）

| 结果 | 含义 | UI 建议提示 |
| --- | --- | --- |
| MATCH | 条件成立 | — |
| NO_MATCH | 条件不成立 | — |
| UNAVAILABLE | 当前场景无法判定（如区块未加载） | 「当前场景无法判定该条件，规则不会触发」 |
| ERROR | 求值时抛异常（后端已记日志） | 「该条件求值出错，规则不会触发」 |

`inverted` 只互换 MATCH 与 NO_MATCH；UNAVAILABLE / ERROR 原样保留——「判不了」不会被取反成「成立」。

## 2. 规则级表单

| 字段 | JSON 键 | 类型 | 必填 / 默认 | 取值域 | 推荐控件 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 规则 id | id | ResourceLocation | 必填 | 命名空间须为 `itemdespawntowhat` | 文本框 + 自动生成 | 非法 id 直接拒绝 |
| 是否启用 | enabled | boolean | 默认 true | — | 复选框 | enabled=false 不参与匹配 |
| 优先级 | priority | int | 默认 0 | 整数，无范围限制 | 数字框 | 越大越优先；同优先级按条件叶数（complexity）多者优先 |
| 展示名 | display_name | string 或 null | 可空 | 规范化后 ≤ 128 码点 | 文本框 | strip 后为空的写入即归一为 null |
| 备注 | notes | string 或 null | 可空 | UI 上限 1024 码点（后端不校验，裁决 10.5） | 多行文本框 | 仅展示；超限本地拦截 |
| 源匹配 | source | 对象 | 必填 | 见 2.1 | 对象表单 | — |
| 条件 | conditions | 条件树 或 省略 | 可省略 | 见第 1 节 | 条件树编辑器 | 省略 = 恒真 |
| 触发时间 | trigger_after_seconds | int | 默认 300 | ≥ 0 | 数字框 | 单位：秒 |
| 效果 | effects | 效果数组 | 必填，≥ 1 | ≤ 32 项 | 可排序列表 | **数组顺序 = 执行顺序** |

保存形态（裁决 10.8）：编辑器保存的是**顶层 JSON 对象**，一个文件一条规则；文件名由规则 id 生成（把 `:` 与 `/` 替换为 `_`，追加 `.json`）。顶层数组形态只用于只读的数据包原始文件。

### 2.1 source 对象

| 字段 | JSON 键 | 类型 | 必填 / 默认 | 取值域 | 推荐控件 |
| --- | --- | --- | --- | --- | --- |
| 匹配项 | source.items | TaggedId 数组 | 必填且非空 | items + exclude 合计 ≤ 256 | 双模式选择器 + 列表（见第 4 节） |
| 排除项 | source.exclude | TaggedId 数组 | 可空，默认空 | 同上 | 同上 |

加载/保存校验：items 内不得重复；exclude 内不得重复；同一项不得同时出现在 items 与 exclude（后端报「source.items 中存在重复项」等错误）。

### 2.2 规则级 i18n（建议键）

**前缀分工（裁决 10.1，已写入契约 §5.1）**：界面文本（字段标签、枚举名、类型显示名）一律用 `gui.itemdespawntowhat.edit.*`；只有走网络的协议回执与校验错误 messageCode 用 `itemdespawntowhat.edit.*`（与现有 49 个 key 一致）。下表及本文件其余建议键均已按 `gui.` 前缀命名。

| key | en | zh |
| --- | --- | --- |
| gui.itemdespawntowhat.edit.rule.id | Rule ID | 规则 ID |
| gui.itemdespawntowhat.edit.rule.enabled | Enabled | 启用 |
| gui.itemdespawntowhat.edit.rule.priority | Priority | 优先级 |
| gui.itemdespawntowhat.edit.rule.display_name | Display name | 展示名 |
| gui.itemdespawntowhat.edit.rule.notes | Notes | 备注 |
| gui.itemdespawntowhat.edit.rule.source | Source matching | 源匹配 |
| gui.itemdespawntowhat.edit.rule.source.items | Matched items / tags | 匹配物品 / 标签 |
| gui.itemdespawntowhat.edit.rule.source.exclude | Excluded items / tags | 排除物品 / 标签 |
| gui.itemdespawntowhat.edit.rule.conditions | Conditions (omitted = always true) | 条件（省略即恒真） |
| gui.itemdespawntowhat.edit.rule.trigger_after_seconds | Trigger after (seconds) | 触发时间（秒） |
| gui.itemdespawntowhat.edit.rule.effects | Effects (executed in order) | 效果（按顺序执行） |

## 3. 效果级公共字段（12 个效果都有）

| 字段 | JSON 键 | 类型 | 必填 / 默认 | 取值域 | 推荐控件 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| 延迟 | delay_ticks | int | 默认 0 | ≥ 0（后端无上限） | 数字框 | 单位：刻；UI 范围 0 .. 72000（1 游戏小时，裁决 10.2），值为 0 时显示「立即」 |
| 概率 | chance | double | 默认 1.0 | [0, 1] | 滑杆 + 数字框 | 未命中则不产出该效果 |
| 附加条件 | conditions | 条件树 或 省略 | 可省略 | 见第 1 节 | 条件树编辑器 | 与规则级同一契约；省略 = 恒真 |

i18n：gui.itemdespawntowhat.edit.field.common.delay_ticks = en "Delay (ticks)" / zh "延迟（刻）"；gui.itemdespawntowhat.edit.field.common.chance = en "Chance" / zh "概率"；gui.itemdespawntowhat.edit.field.common.conditions = en "Extra conditions" / zh "附加条件"。

## 4. ID 双模式选择器（具体 ID / 标签）

后端有两种 ID 类型，控件必须按字段类型区分，**不要**给纯 ResourceLocation 字段提供标签模式：

| 类型 | 字符串形态 | 允许 # 前缀 | 说明 |
| --- | --- | --- | --- |
| TaggedId | "`minecraft:bone_meal`" 或 "`#minecraft:is_forest`" | 是 | # 表示物品/方块/流体等标签 |
| ResourceLocation | "`minecraft:overworld`" | 否 | 纯注册名 |

类型分布：

| 类型 | 字段 |
| --- | --- |
| TaggedId | source.items、source.exclude、biome.biomes、catalyst_present.items、fluid_present.fluid、surrounding_blocks.{up,down,north,south,east,west}、spawn_item.item、spawn_entity.entity、place_block.block、consume_catalyst.items、consume_fluid.fluid、arrow_rain.potion_effects[].effect |
| ResourceLocation | dimension.dimensions、loot_table.loot_table |

推荐控件：双段切换「具体 ID / 标签」+ 可搜索下拉（用注册表填充）+ 允许手填；切到「标签」自动补 `#` 前缀，切回时去掉；列表型字段提供增删。纯 ResourceLocation 字段只给搜索下拉 + 手填，不给切换。

加载期还会做引用存在性校验（维度、群系、物品、流体、实体、战利品表、药水效果）；下拉只列已注册项可避免保存后被拒载。

## 5. 10 个条件逐个规格

下文 `<name>` 指 `itemdespawntowhat:<name>`；i18n 前缀统一为 gui.itemdespawntowhat.edit.field.<name>.（类型显示名用 gui.itemdespawntowhat.edit.condition.<name>），表格里只写 key 后缀。

### 5.1 dimension（itemdespawntowhat:dimension）

后端 record：`DimensionCondition(List<ResourceLocation> dimensions)`。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 维度列表 | dimensions | ResourceLocation 数组 | 否 | 非空数组 | **是**（codec 缺省为空数组，但 validateParams 要求非空） | 维度多选（下拉 + 多选，无标签模式） |

i18n 前缀 gui.itemdespawntowhat.edit.field.dimension.

| key 后缀 | en | zh |
| --- | --- | --- |
| dimensions | Dimensions | 维度 |
| dimensions.add | Add dimension | 添加维度 |

evaluability：恒 AVAILABLE（未注册的维度 id 在加载期被引用校验拒载，不会进入运行时）。

### 5.2 biome（itemdespawntowhat:biome）

后端 record：`BiomeCondition(Mode mode, List<TaggedId> biomes, ClimateRange temperature, ClimateRange humidity, ClimateRange continentalness, ClimateRange erosion, ClimateRange depth, ClimateRange weirdness)`。
`Mode` = exact（按群系 id/标签精确匹配）/ climate（按气候区间匹配）。ClimateRange 是嵌套对象 `{ "min": <double?>, "max": <double?> }`，两端均可空，取值域 [-1, 1]。**单维两端全空 = 该维度不构成约束**（允许，裁决 10.9），UI 提示「留空 = 不约束」；但 `mode=climate` 时 6 个维度全空仍按校验失败拦截。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 匹配方式 | mode | 枚举 Mode | 否 | exact / climate | **是** | 分段单选 |
| 群系列表 | biomes | TaggedId 数组 | 默认空数组 | mode=exact 时非空 | mode=exact 时必填 | 双模式选择器 + 列表 |
| 温度 | temperature | ClimateRange 对象 | 是 | min/max ∈ [-1,1]，min ≤ max | mode=climate 时 6 个区间至少 1 个 | 两个数字框 |
| 湿度 | humidity | ClimateRange 对象 | 是 | 同上 | 同上 | 两个数字框 |
| 大陆性 | continentalness | ClimateRange 对象 | 是 | 同上 | 同上 | 两个数字框 |
| 侵蚀度 | erosion | ClimateRange 对象 | 是 | 同上 | 同上 | 两个数字框 |
| 深度 | depth | ClimateRange 对象 | 是 | 同上 | 同上 | 两个数字框 |
| 怪异度 | weirdness | ClimateRange 对象 | 是 | 同上 | 同上 | 两个数字框 |

互斥提示（后端只给警告、不阻断）：mode=exact 时气候区间被忽略；mode=climate 时 biomes 被忽略。建议按 mode 把无关字段置灰。

i18n 前缀 gui.itemdespawntowhat.edit.field.biome.

| key 后缀 | en | zh |
| --- | --- | --- |
| mode | Match mode | 匹配方式 |
| mode.exact | Exact biome | 精准群系 |
| mode.climate | Climate ranges | 气候区间 |
| biomes | Biomes / tags | 群系 / 标签 |
| temperature | Temperature | 温度 |
| humidity | Humidity | 湿度 |
| continentalness | Continentalness | 大陆性 |
| erosion | Erosion | 侵蚀度 |
| depth | Depth | 深度 |
| weirdness | Weirdness | 怪异度 |
| range.min | Min | 最小值 |
| range.max | Max | 最大值 |

evaluability：恒 AVAILABLE。

### 5.3 weather（itemdespawntowhat:weather）

后端 record：`WeatherCondition(Kind weather)`；`Kind` = CLEAR / RAIN / THUNDER。用途：判断当前天气（与 weather **效果**改天气区分）。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 天气 | weather | 枚举 Kind | 否 | clear / rain / thunder | **是** | 三选一（分段单选） |

i18n 前缀 gui.itemdespawntowhat.edit.field.weather.（枚举文案见第 8 节）：key 后缀 weather = en "Weather" / zh "天气"。

evaluability：恒 AVAILABLE。

### 5.4 outdoor（itemdespawntowhat:outdoor）

后端 record：`OutdoorCondition()`——**无参条件**，JSON 里只有 `type`（`MapCodec.unit`）。
旧版靠叶级 negated 表达「非露天」，现在必须写成 `inverted` 包一个 outdoor 叶。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| （无参数） | — | — | — | — | — | 只显示说明文字 |

i18n：gui.itemdespawntowhat.edit.field.outdoor.note = en "No parameters (wrap in an inverted node to negate)" / zh "无参数（取反请包一层 inverted 节点）"。

evaluability：恒 AVAILABLE。

### 5.5 surrounding_blocks（itemdespawntowhat:surrounding_blocks）

后端 record：`SurroundingBlocksCondition(TaggedId up, TaggedId down, TaggedId north, TaggedId south, TaggedId east, TaggedId west)`；六向均可空，但**至少一个非空**（全空报错）。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 上 | up | TaggedId | 是 | 方块 id 或 #标签 | 六向至少 1 个 | 双模式选择器（每方向一行） |
| 下 | down | TaggedId | 是 | 同上 | 同上 | 同上 |
| 北 | north | TaggedId | 是 | 同上 | 同上 | 同上 |
| 南 | south | TaggedId | 是 | 同上 | 同上 | 同上 |
| 东 | east | TaggedId | 是 | 同上 | 同上 | 同上 |
| 西 | west | TaggedId | 是 | 同上 | 同上 | 同上 |

i18n 前缀 gui.itemdespawntowhat.edit.field.surrounding_blocks.：up/down/north/south/east/west = en "Up/Down/North/South/East/West" / zh "上/下/北/南/东/西"。

evaluability：**可能 UNAVAILABLE**。后端门禁为 `LoadedChunks.containsArea(level, pos, 1)`：源掉落物所在位置 ±1 范围内的区块只要没全部加载，该条件就返回 UNAVAILABLE。
UI 提示：「周围区块未加载时该条件无法判定（UNAVAILABLE），规则不会触发，且 inverted 也不会因此放行」。

### 5.6 catalyst_present（itemdespawntowhat:catalyst_present）

后端 record：`CatalystPresentCondition(List<TaggedId> items, int count)`。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 催化剂列表 | items | TaggedId 数组 | 默认空数组 | 非空数组 | **是**（validateParams 要求非空） | 双模式选择器 + 列表 |
| 需要数量 | count | int | 否 | 1 .. 64，默认 1 | 否 | 数字框（带步进） |

i18n 前缀 gui.itemdespawntowhat.edit.field.catalyst_present.：items = en "Catalyst items / tags" / zh "催化剂物品 / 标签"；count = en "Required count" / zh "需要数量"。

evaluability：恒 AVAILABLE。

### 5.7 fluid_present（itemdespawntowhat:fluid_present）

后端 record：`FluidPresentCondition(TaggedId fluid, boolean requireSource)`。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 流体 | fluid | TaggedId | 是（省略 = 任意流体） | 流体 id 或 #标签 | 否 | 双模式选择器 |
| 必须源方块 | require_source | boolean | 否 | 默认 true | 否 | 复选框 |

提示：fluid 明确写 `minecraft:empty` 时后端给警告「不会匹配任何流体」；省略 `fluid` 才是「任意流体」。

i18n 前缀 gui.itemdespawntowhat.edit.field.fluid_present.：fluid = en "Fluid / tag" / zh "流体 / 标签"；require_source = en "Must be a source block" / zh "必须是源方块"。

evaluability：恒 AVAILABLE。

### 5.8 time_of_day（itemdespawntowhat:time_of_day）

后端 record：`TimeOfDayCondition(int from, int to)`；取值域 0 .. 23999（游戏内刻），支持跨零点（from > to 表示跨夜区间）。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 起始刻 | from | int | 否 | 0 .. 23999 | **是** | 滑杆 + 数字框（可显示换算后的时刻） |
| 结束刻 | to | int | 否 | 0 .. 23999 | **是** | 同上 |

i18n 前缀 gui.itemdespawntowhat.edit.field.time_of_day.：from = en "From tick" / zh "起始刻"；to = en "To tick" / zh "结束刻"。

evaluability：恒 AVAILABLE。

### 5.9 y_level（itemdespawntowhat:y_level）

后端 record：`YLevelCondition(Integer min, Integer max)`；两端可空，取值域 -2048 .. 2048，非空时要求 min ≤ max。
注意（裁决 10.3）：后端**不强制**至少填一端，两端全空等价恒真，**UI 也不强制填写**；两端全空时编辑器显示黄色提示「未设置任何边界」。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 最低 Y | min | Integer | 是 | -2048 .. 2048 | 否（两端全空 = 恒真，黄色提示） | 数字框（可清空） |
| 最高 Y | max | Integer | 是 | -2048 .. 2048 | 否（两端全空 = 恒真，黄色提示） | 数字框（可清空） |

i18n 前缀 gui.itemdespawntowhat.edit.field.y_level.：min = en "Min Y" / zh "最低 Y"；max = en "Max Y" / zh "最高 Y"。

evaluability：恒 AVAILABLE。

### 5.10 light_level（itemdespawntowhat:light_level）

后端 record：`LightLevelCondition(Integer min, Integer max)`；两端可空，取值域 0 .. 15，非空时 min ≤ max；同样**允许两端全空 = 恒真**（裁决 10.3），两端全空时给黄色提示「未设置任何边界」。

| 字段 | JSON 键 | 类型 | 可空 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 最低光照 | min | Integer | 是 | 0 .. 15 | 否（两端全空 = 恒真，黄色提示） | 数字框（可清空） |
| 最高光照 | max | Integer | 是 | 0 .. 15 | 否（两端全空 = 恒真，黄色提示） | 数字框（可清空） |

i18n 前缀 gui.itemdespawntowhat.edit.field.light_level.：min = en "Min light" / zh "最低光照"；max = en "Max light" / zh "最高光照"。

evaluability：恒 AVAILABLE。

### 5.11 evaluability 汇总

10 个条件里只有 surrounding_blocks 覆盖了 `evaluability(...)`，其余 9 个恒 AVAILABLE；运行期遇到未注册条件类型按 UNAVAILABLE 兜底（加载期本应已拒载），绝不会被当成「不成立」。

## 6. 12 个效果逐个规格

每个效果的对象结构 = `type` + 类型专属字段 + 第 3 节的三个公共字段（delay_ticks / chance / conditions）。
下文 `<name>` 指 `itemdespawntowhat:<name>`；i18n 前缀统一为 gui.itemdespawntowhat.edit.field.<name>.（效果类型显示名用 gui.itemdespawntowhat.edit.effect.<name>）。

### 6.1 spawn_item（itemdespawntowhat:spawn_item）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 产物 | item | TaggedId | 否 | 物品 id 或 #标签 | **是** | 双模式选择器 |
| 数量 | count | int | 默认 1 | 1 .. 64 | 否 | 数字框 |
| 上限 | limit | Integer | 可空（= 不限） | 1 .. 4096 | 否 | 数字框（可清空） |
| 半径 | radius | Integer | 可空（执行器默认 6） | 1 .. 32 | 否 | 数字框（可清空） |

i18n 后缀：item = en "Item / tag" / zh "物品 / 标签"；count = en "Count" / zh "数量"；limit = en "Limit" / zh "上限"；radius = en "Radius" / zh "半径"。

### 6.2 spawn_entity（itemdespawntowhat:spawn_entity）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 实体 | entity | TaggedId | 否 | 实体 id 或 #标签 | **是** | 双模式选择器 |
| 数量 | count | int | 默认 1 | 1 .. 64 | 否 | 数字框 |
| 年龄 | age | int | 默认 0 | UI 范围 -24000 .. 24000（后端无范围校验，裁决 10.4） | 否 | 数字框 + 「幼年」(-24000) / 「成年」(0) 两个预设按钮 |
| 上限 | limit | Integer | 可空 | 1 .. 4096 | 否 | 数字框（可清空） |
| 半径 | radius | Integer | 可空（默认 6） | 1 .. 32 | 否 | 数字框（可清空） |

i18n 后缀：entity = "Entity / tag" / "实体 / 标签"；count = "Count" / "数量"；age = "Age (negative = baby)" / "年龄（负值 = 幼年）"；limit = "Limit" / "上限"；radius = "Radius" / "半径"。取值域按裁决 10.4：UI 限制 -24000 .. 24000，默认 0，提供「幼年」-24000 与「成年」0 两个预设按钮。

### 6.3 place_block（itemdespawntowhat:place_block）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 方块 | block | TaggedId | 可空 | 方块 id 或 #标签 | 与 use_source_block **至少一个** | 双模式选择器 |
| 用源方块 | use_source_block | boolean | 默认 false | — | 同上 | 复选框 |
| 形状 | shape | 枚举 Shape | 默认 square | square / circle / cross | 否 | 分段单选 |
| 数量 | count | int | 默认 1 | 1 .. 64 | 否 | 数字框 |
| 半径 | radius | int | 默认 6 | 1 .. 32 | 否 | 数字框 |
| 上限 | limit | Integer | 可空 | 1 .. 4096 | 否 | 数字框（可清空） |

硬约束：`block` 与 `use_source_block` 不能同时缺省（后端 validateParams 报错）。UI 应在保存前拦截并高亮这两个字段。

i18n 后缀：block = "Block / tag" / "方块 / 标签"；use_source_block = "Use source block" / "使用源方块"；shape = "Shape" / "形状"；count = "Count" / "数量"；radius = "Radius" / "半径"；limit = "Limit" / "上限"。

### 6.4 spawn_xp（itemdespawntowhat:spawn_xp）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 经验量 | amount | int | 默认 1 | 1 .. 65536 | 否 | 数字框 |
| 按源物品数 | per_source_item | boolean | 默认 false | — | 否 | 复选框 |

i18n 后缀：amount = "XP amount" / "经验量"；per_source_item = "Multiply by source count" / "按源物品数量倍增"。

### 6.5 loot_table（itemdespawntowhat:loot_table）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 战利品表 | loot_table | ResourceLocation | 否 | 已加载的战利品表 id（**不支持标签**） | **是** | 搜索下拉 + 手填 |
| 幸运值 | luck | float | 默认 0 | -100 .. 100 | 否 | 数字框 |

加载期会校验战利品表是否已加载，UI 下拉只列已加载项可避免拒载。

i18n 后缀：loot_table = "Loot table" / "战利品表"；luck = "Luck" / "幸运值"。

### 6.6 lightning（itemdespawntowhat:lightning）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 数量 | count | int | 默认 1 | 1 .. 16 | 否 | 数字框 |

i18n 后缀：count = "Strike count" / "落雷次数"。

### 6.7 arrow_rain（itemdespawntowhat:arrow_rain）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 数量 | count | int | 默认 16 | 1 .. 256 | 否 | 数字框 |
| 拾取方式 | pickup | 枚举 Pickup | 默认 disallowed | disallowed / allowed / creative_only | 否 | 分段单选 |
| 药水效果 | potion_effects | PotionEffectSpec 数组 | 可空（默认空） | 每项见下 | 否 | 可增删的子表单列表 |
| └ 药水效果 id | potion_effects[].effect | TaggedId | 否 | MOB_EFFECT 注册表 | **是** | 搜索下拉 + 手填 |
| └ 持续刻数 | potion_effects[].duration_ticks | int | 否 | 1 .. 1000000 | **是** | 数字框 |
| └ 等级 | potion_effects[].amplifier | int | 否 | 后端 0 .. 255；UI 输入 1 .. 255 | **是** | 数字框（UI 显示 = amplifier + 1 的罗马数字，从 I 级起；写回时减 1，裁决 10.10） |

i18n 后缀：count = "Arrow count" / "箭矢数量"；pickup = "Pickup" / "拾取方式"；potion_effects = "Potion effects" / "药水效果"；potion_effects.effect = "Effect" / "效果"；potion_effects.duration_ticks = "Duration (ticks)" / "持续（刻）"；potion_effects.amplifier = "Amplifier (UI shows I, II, ...)" / "等级（UI 从 I 级起）"。

### 6.8 weather（itemdespawntowhat:weather，改天气）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 天气 | mode | 枚举 Mode | 否 | rain / clear | **是** | 分段单选 |
| 持续刻数 | duration_ticks | int | 否 | 1 .. 24000 | **是** | 数字框（可显示换算秒/天） |
| 是否雷暴 | thundering | boolean | 默认 false | — | 否 | 复选框（mode=clear 时置灰） |

i18n 后缀：mode = "Weather" / "天气"；duration_ticks = "Duration (ticks)" / "持续（刻）"；thundering = "Thundering" / "伴随雷暴"。

### 6.9 consume_source（itemdespawntowhat:consume_source）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 消耗数量 | count | int | 默认 1 | 1 .. 64 | 否 | 数字框 |

属于**消耗类效果**，受第 7 节约束（一条规则内最多 1 次）。
i18n 后缀：count = "Consume count" / "消耗数量"。

### 6.10 consume_catalyst（itemdespawntowhat:consume_catalyst）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 催化剂 | items | TaggedId 数组 | 否 | 非空数组 | **是** | 双模式选择器 + 列表 |
| 数量 | count | int | 默认 1 | 1 .. 64 | 否 | 数字框 |
| 半径 | radius | int | 默认 1 | 1 .. 8 | 否 | 数字框 |

属于**消耗类效果**，受第 7 节约束。
i18n 后缀：items = "Catalyst items / tags" / "催化剂物品 / 标签"；count = "Consume count" / "消耗数量"；radius = "Search radius" / "搜索半径"。

### 6.11 consume_fluid（itemdespawntowhat:consume_fluid）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 流体 | fluid | TaggedId | 可空（省略 = 任意流体） | 流体 id 或 #标签 | 否 | 双模式选择器 |
| 必须源方块 | require_source | boolean | 默认 true | — | 否 | 复选框 |

属于**消耗类效果**，受第 7 节约束。
i18n 后缀：fluid = "Fluid / tag" / "流体 / 标签"；require_source = "Must be a source block" / "必须是源方块"。

### 6.12 explosion（itemdespawntowhat:explosion）

| 字段 | JSON 键 | 类型 | 可空 / 默认 | 取值域 | 必填 | 推荐控件 |
| --- | --- | --- | --- | --- | --- | --- |
| 威力 | power | float | 默认 3.0 | 0 .. 16 | 否 | 滑杆 + 数字框 |
| 生成火焰 | fire | boolean | 默认 false | — | 否 | 复选框 |
| 仅视觉效果 | visual_only | boolean | 默认 false | — | 否 | 复选框 |

i18n 后缀：power = "Power" / "威力"；fire = "Create fire" / "生成火焰"；visual_only = "Visual only (no damage)" / "仅视觉效果（无伤害）"。

## 7. 消耗语义（三种消耗效果的特殊约束）

消耗类效果共 3 个：`itemdespawntowhat:consume_source`、`itemdespawntowhat:consume_catalyst`、`itemdespawntowhat:consume_fluid`。

1. **同类型最多 1 次**：一条规则内同一消耗效果类型只能出现一次；重复时后端报「同一规则内重复声明消耗效果 X 共 N 次」。UI 应在「添加效果」列表里把已声明的消耗类型标灰。
2. **隐式源消耗**：规则没有声明任何消耗类效果时，运行时隐式追加一次「消耗 1 个源物品」（等价 `consume_source` count=1、delay=0、chance=1.0、无条件）。即使用户没写消耗效果，源物品默认也会被消耗。
3. **显式声明即接管**：只要规则里出现了**任意一个**消耗类效果（哪怕只是 consume_catalyst / consume_fluid），隐式源消耗就会被抑制——源物品不再被自动消耗。这是最容易误配的一点，UI 必须显示提示。

提示文案（建议 i18n 键）：

| key | en | zh |
| --- | --- | --- |
| gui.itemdespawntowhat.edit.consumption.hint.implicit | No consumption effect declared: 1 source item is consumed by default. | 未声明任何消耗效果：默认消耗 1 个源物品。 |
| gui.itemdespawntowhat.edit.consumption.hint.explicit | A consumption effect is declared: the implicit source item consumption is disabled. | 已声明消耗效果：隐式源物品消耗已被关闭。 |
| gui.itemdespawntowhat.edit.consumption.hint.explicit_source | consume_source declared: source items are consumed explicitly (see delay/chance/conditions on this effect). | 已显式声明 consume_source：源物品按该效果的延迟 / 概率 / 附加条件消耗。 |
| gui.itemdespawntowhat.edit.consumption.hint.duplicate | Each consumption type may appear at most once per rule. | 每种消耗效果在一条规则中只能出现一次。 |

## 8. 枚举取值表

| 枚举 | 所属字段 | JSON 取值 | en | zh | 默认 |
| --- | --- | --- | --- | --- | --- |
| Kind | 条件 weather.weather | clear / rain / thunder | Clear / Rain / Thunder | 晴朗 / 下雨 / 雷暴 | 无（必填） |
| Mode | 条件 biome.mode | exact / climate | Exact / Climate | 精准群系 / 气候区间 | 无（必填） |
| Shape | 效果 place_block.shape | square / circle / cross | Square / Circle / Cross | 方形 / 圆形 / 十字 | square |
| Pickup | 效果 arrow_rain.pickup | disallowed / allowed / creative_only | Disallowed / Allowed / Creative only | 不可拾取 / 可拾取 / 仅创造可拾取 | disallowed |
| Mode | 效果 weather.mode | rain / clear | Rain / Clear | 下雨 / 放晴 | 无（必填） |

JSON 里的枚举值一律小写；编辑器不要输出大写或本地化文本。

建议 i18n 键：gui.itemdespawntowhat.edit.enum.<枚举>.<取值>，例如 gui.itemdespawntowhat.edit.enum.weather_kind.thunder = en "Thunder" / zh "雷暴"；gui.itemdespawntowhat.edit.enum.biome_mode.climate = en "Climate ranges" / zh "气候区间"；gui.itemdespawntowhat.edit.enum.place_block_shape.cross = en "Cross" / zh "十字"；gui.itemdespawntowhat.edit.enum.arrow_rain_pickup.creative_only = en "Creative only" / zh "仅创造可拾取"；gui.itemdespawntowhat.edit.enum.weather_effect_mode.clear = en "Clear" / zh "放晴"。

## 9. 保存前必须拦截的错误清单

编辑器应在提交前本地校验（后端仍会二次校验，但本地拦能给出字段级定位）：

| # | 情况 | 后端文案要点 |
| --- | --- | --- |
| 1 | terms 为空的 all_of / any_of | 「op=X 的 terms 不能为空：空条件组请直接省略 conditions 字段」 |
| 2 | inverted 缺 term，或出现多个 term | inverted 恰好 1 个 term |
| 3 | 叶内出现 negated | 旧格式报错并提示改用 inverted |
| 4 | conditions 为数组 / 含 groups 字段 | 旧格式已废弃，禁止静默兼容 |
| 5 | 未知 op | 列出允许值 all_of / any_of / inverted / leaf |
| 6 | 节点或叶出现未知字段 | 「类型 X 存在未知字段: k」 |
| 7 | 超限：effects > 32、叶 > 128、节点 > 256、深度 > 16、source 项 > 256 | 「规则超出工作量上限：...」 |
| 8 | display_name 码点 > 128 | 「display_name 超过 128 个码点」 |
| 9 | delay_ticks < 0 | 「delay_ticks 不能为负数」 |
| 10 | chance 不在 [0,1] | codec 直接拒绝（doubleRange） |
| 11 | dimension.dimensions 为空 | 「dimensions 不能为空」 |
| 12 | biome：缺 mode；mode=exact 而 biomes 空；mode=climate 而 6 个区间全空；区间 min > max | 各自报参数错误 |
| 13 | surrounding_blocks 六向全空 | 至少需要一个方向 |
| 14 | catalyst_present.items 为空 | 「items 不能为空」 |
| 15 | y_level / light_level 超出取值域或 min > max | inRange / orderedRange 报错 |
| 16 | place_block 同时缺 block 与 use_source_block | 「block 与 use_source_block 至少需要其一」 |
| 17 | weather 效果缺 mode / duration_ticks 超出 1..24000 | 必填与范围报错 |
| 18 | arrow_rain.potion_effects 项缺 effect / duration_ticks / amplifier 或超范围 | 子字段报错 |
| 19 | 同一规则重复声明同一消耗效果 | 「同一规则内重复声明消耗效果 X 共 N 次」 |
| 20 | source.items / exclude 内部重复或交叉重复 | 「source.items 中存在重复项」等 |
| 21 | effects 为空 | 规则至少要有一个效果 |

## 10. 产品/跨模块裁决（lead 2026-10-02 全部裁决，已同步进契约 §5.1 / §5.2）

| # | 事项 | 裁决结论 |
| --- | --- | --- |
| 10.1 | i18n 键前缀 | 界面文本（字段标签 / 枚举名 / 类型显示名）一律 `gui.itemdespawntowhat.edit.*`；协议回执与校验错误的 messageCode 用 `itemdespawntowhat.edit.*`（与现有 49 个 key 一致）。字段标签 `gui.itemdespawntowhat.edit.field.<condition 或 effect>.<field>`；枚举 `gui.itemdespawntowhat.edit.enum.<enum>.<value>`；类型显示名 `gui.itemdespawntowhat.edit.condition.<type>` / `gui.itemdespawntowhat.edit.effect.<type>` |
| 10.2 | delay_ticks UI 上限 | UI 范围 0 .. 72000（1 游戏小时），值为 0 显示「立即」；后端不加限制 |
| 10.3 | y_level / light_level 是否强制至少一端 | 允许两端全空 = 恒真，不强制填写；编辑器给黄色提示「未设置任何边界」 |
| 10.4 | spawn_entity.age UI 取值域 | UI 范围 -24000 .. 24000，默认 0；提供「幼年」-24000 与「成年」0 两个预设按钮 |
| 10.5 | notes 长度上限 | UI 上限 1024 个 code point（后端不校验），超限本地拦截 |
| 10.6 | 效果级条件是否计入规则级总量 | 确认按**逐表达式**：规则级与每个效果级条件树各自受 128 / 256 / 16 限制，不跨表达式累加（即当前后端实现，规划书 §5.3 原意） |
| 10.7 | 条件树编辑器层级 UX 上限 | 不额外硬限；深度 > 6 时节点旁显示「层级较深」提示即可 |
| 10.8 | 规则文件顶层形态 | 编辑器保存 = 顶层 JSON **对象**，一个文件一条规则；文件名由规则 id 生成（`:` 与 `/` 替换为 `_`，加 `.json`）；顶层数组形态只用于只读的数据包原始文件 |
| 10.9 | ClimateRange 两端全空 | 单维两端全空 = 该维度不构成约束（允许）；`mode=climate` 而 6 维全空 → 校验失败；UI 提示「留空 = 不约束」 |
| 10.10 | amplifier 显示 | 后端 0 起算；UI 显示 = amplifier + 1（罗马数字 I 起），输入范围 1 .. 255，写回时减 1 |

**注意**：第 10.1 条意味着本文件第 2.2 / 3 / 5 / 6 / 7 / 8 节的建议键前缀已统一为 `gui.`；协议回执与校验错误 messageCode 仍用 `itemdespawntowhat.edit.*`，两者不要混用。

---

生成者备注：本文件由 P2（task-2）负责人在完成条件树重构后产出，字段名与取值范围均逐一核对过 `core/type/**` 的 record 组件与 codec；第 10 节原为待核查项，已于 2026-10-02 由 lead 全部裁决并同步进契约 §5.1 / §5.2，本文件已按裁决更新。
