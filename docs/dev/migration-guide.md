# 迁移指南：旧 JSON → 新覆盖层规则

> **状态**：`/idtw config convert` 已在阶段⑤ 落地（实现见 `core/command/RuleConvertService.java`，命令树仍在收尾）。
> 本文的字段映射与命令行为都按该实现逐条核对过；命令不可用时可按 §8 手工迁移，映射关系完全一致。
>
> 本文只讲"旧配置怎么映射到新规则"。新字段的完整定义见 [config-reference.md](config-reference.md)。

## 1. 旧格式是什么样

旧链路（`config/**`）的配置是**一个转化类型一个文件**：

```
config/itemdespawntowhat/itemdespawntowhat/item_to_item.json
config/itemdespawntowhat/itemdespawntowhat/item_to_mob.json
...（9 个内置类型；第三方类型在 <ns>/ 子目录）
```

每个文件是一个**规则数组**，字段由 Gson + `@SerializedName` 绑定：

```json
[
  {
    "schema_version": 2,
    "item": "minecraft:stone",
    "result": "minecraft:diamond",
    "source_multiple": 1,
    "result_multiple": 2,
    "conversion_time": 300,
    "conditions": {
      "groups": [
        [ { "type": "itemdespawntowhat:outdoor", "params": {}, "negated": false } ]
      ]
    },
    "consumption": {
      "catalyst_items": [ { "item": "minecraft:blaze_powder", "count": 1 } ],
      "inner_fluid": { "fluid": "minecraft:water", "require_source": true }
    },
    "priority": 0,
    "enabled": true,
    "notes": "示例"
  }
]
```

旧格式有三代，**转换命令只处理 v2**（也是磁盘上最常见的形状）：

| 代 | 形状 | 说明 |
|---|---|---|
| v1 | 扁平条件/消耗字段：`dimension` / `need_outdoor` / `surrounding_blocks` / `catalyst_items` + `consume_catalyst` / `inner_fluid` + `consume_fluid` | ADR-0004 时期的早期形状 |
| v2 | `conditions.groups`（叶带 `params` 子对象）+ `consumption` 指令 | ADR-0007 起；**本文主要对照的形状** |
| 带 `schema_version` | 旧加载器按缺省推断 v1、迁移到 v2，并在**加载时写回** | 正是新链路要消灭的"读时写回"（ADR-0008，已归档） |

**v1 文件**：旧加载器读取时会先规范化成 v2（ADR-0007/0008）。转换命令直接读磁盘原始文本，因此**面对 v1 需要先让旧链路读过一次**，或按 §5 的"v1 附加映射"手工处理。

## 2. 迁移前后对照（总览）

| 维度 | 旧 | 新 |
|---|---|---|
| 组织 | 一个转化类型一个文件，文件即类型 | 任意目录下 `rules/**/*.json`，单对象或数组；类型由规则内的 `effects[].type` 表达 |
| 标识 | 无 id（内部 UUID） | `id`（`ResourceLocation`），可省略时由文件路径推导 |
| 覆盖机制 | 无 | 三层作用域 + 按 id 覆盖 + `disabled`/`delete` |
| 条件 | `conditions.groups[g].conditions[l]`，每叶带 `params` 子对象 | 条件叶**扁平**：参数直接写在叶对象里 |
| 消耗 | `consumption` 指令（配 `conditions` 里的 `*_present` 叶实现"只检查"） | 消耗是**效果**（`consume_*`）；"只检查"用 `*_present` **条件叶** |
| 结果上限 | 规则级 `result_limit` + `search_radius` | 产物类效果自身的 `limit` / `radius` |
| 版本 | `schema_version` + 加载期写回 | 无版本字段；转换是一次性显式操作 |
| 时间 | `conversion_time`（秒，但实现按检查次数） | `trigger_after_seconds`（秒，绝对存活时间，语义已修正） |

## 3. 通用字段映射

| 旧字段 | 新字段 | 说明 |
|---|---|---|
| `schema_version` | — | 删除 |
| `item` | `source.items` | 单值变数组：`"#tag" → ["#tag"]` |
| — | `source.exclude` | 旧格式无排除项，留空 |
| `priority` | `priority` | 同名同义 |
| `enabled` | `enabled` | 同名同义 |
| `notes` | `notes` | 同名同义（空白串不写） |
| `conversion_time` | `trigger_after_seconds` | 数值直接搬，且**下限钳到 1**（`max(1, conversion_time)`）；注意新语义是绝对存活时间 |
| `source_multiple` | `consume_source.count` | 转换器**总是显式写出** `consume_source`（即使为 1），以覆盖运行时的隐式消耗默认值 |
| `result_multiple` | 对应效果的 `count` | 见 §4；新模型没有"结果倍率"概念 |
| `conditions` | `conditions` | 形状改变，见 §5 |
| `consumption` | `effects` 中的 `consume_*` | 见 §6 |

## 4. 转化类型 → 效果映射（按旧类型逐个）

9 个旧类型各对应一个"生效效果"（映射表见 `RuleConvertService.TYPE_TO_EFFECT`）：

| 旧转化类型 | 新效果 `type` | 字段映射 |
|---|---|---|
| `item_to_item` | `spawn_item` | `result → item`；`result_multiple → count`；`result_limit → limit`；`search_radius → radius` |
| `item_to_mob` | `spawn_entity` | `result → entity`；`entity_age → age`（缺省 `-24000`，负值 = 幼体）；`result_multiple → count`；`result_limit → limit`；`search_radius → radius` |
| `item_to_block` | `place_block` | `block_of_item → use_source_block`；未启用时 `result → block`；`block_place_shape → shape`（转小写）；`result_multiple → count`；`radius_limit → radius`；`result_limit → limit` |
| `item_to_xp_orb` | `spawn_xp` | `amount = result_multiple × xp_per_item`；`per_source_item = false` |
| `item_to_lightning` | `lightning` | `result_multiple → count` |
| `item_to_explosion` | `explosion` | `explosion_power → power`（缺省 1.0）；`explosion_fire → fire` |
| `item_to_arrow_rain` | `arrow_rain` | `result_multiple → count`；`arrow_pickup_status → pickup`（转小写）；`arrow_potion_effects → potion_effects`（`effect` 同名、`duration → duration_ticks`、`amplifier` 同名） |
| `item_to_weather` | `weather` | `weather_mode → mode`（转小写）；`weather_duration_ticks → duration_ticks`；`is_thundering → thundering` |
| `item_to_loot` | `loot_table` | `result → loot_table`；`luck → luck` |

### 4.1 字段级损失（转换器作为 note 报告，不阻断）

| 旧字段 | 处置 |
|---|---|
| `explosion_direction_type`（`item_to_explosion`） | 新 `explosion` 无方向参数 → 丢弃并提示 |
| `result_multiple`（`item_to_explosion` / `item_to_loot`） | 新效果无数量倍率 → 丢弃并提示 |
| `result_limit`、`search_radius`（`item_to_loot`） | 新 `loot_table` 不声明 `limit` / `radius` → 丢弃并提示 |
| `visual_only`（`item_to_lightning`） | 新 `lightning` 只有 `count` → 丢弃并提示 |
| `result_limit ≤ 0`（任意类型） | 视为无效上限 → 省略 `limit` 并提示 |
| `search_radius < 1`（`spawn_item` / `spawn_entity`） | 低于新 `radius` 下限 → 按 1 转换并提示 |
| `radius_limit < 1`（`item_to_block`） | 低于新 `radius` 下限 → 按 1 转换并提示 |
| `arrow_potion_effects` 中缺 `effect` 的元素 | 跳过并提示 |

### 4.2 无法映射（转换器报告为 `unmapped`）

| 情况 | 原因 |
|---|---|
| `catalyst_items` 声明了**多于 1 组** | 新 `consume_catalyst` 只支持统一的 `items` + `count` |
| `catalyst_present` 条件的 `items` **多于 1 组** | 同上（新条件只支持统一的 `items` + `count`） |
| `item_to_xp_orb` 的 `result_multiple × xp_per_item` 超出 `[1,65536]` | 新 `spawn_xp.amount` 区间限制 |
| 条件组转换后为空 | 旧条件恒真（如 `weather: ANY`）或全部无法映射，新模型不接受空条件组 |
| 旧文件类型名不在 9 个内置类型内 | 本命令只处理 9 个内置类型 |
| 条件类型在新链路没有对应（或 `type` 非法） | 见 `RuleConvertService.CONDITIONS` 的 7 个映射 |
| `dimension` / `biome` / `weather` / `surrounding_blocks` 条件缺参数 | 无法无歧义映射 |

### 4.3 已知的**未提示**字段损失（需人工确认）

`item_to_block` 的 `search_radius`（旧 `result_limit` 的统计半径）当前**既不映射也不提示**：新 `place_block` 只有放置 `radius` 与 `limit`，没有独立的"上限统计半径"。迁移这类文件后请人工确认 `limit` 的判定范围是否符合预期。

## 5. 条件表达式映射

**形状**：旧 `{ groups: [ { conditions: [ {type, params:{...}, negated} ] } ] }` → 新 `[ [ {type, ...参数平铺, negated} ] ]`（去掉 `groups` 壳、`conditions` 层与 `params` 层）。

**条件类型与参数**（转换器逐叶按 `params` 内容重写）：

| 旧叶类型 | 旧 `params` | 新 `type` | 新字段 |
|---|---|---|---|
| `dimension` | `dimension`（单字符串） | `dimension` | `dimensions: [ <原值> ]` |
| `outdoor` | 无 | `outdoor` | 无 |
| `weather` | `weather` | `weather` | `clear` / `rain`（`RAINING`）/ `thunder`（`THUNDERING`）；**`ANY` 视为恒真并丢弃该叶** |
| `biome` | `biome`（单字符串） | `biome` | `mode: "exact"` + `biomes: [ <原值> ]`（旧格式无 climate 模式） |
| `surrounding_blocks` | `blocks: {up,down,...}` | `surrounding_blocks` | 六向字段**上提**到叶对象；六向全空则无法映射 |
| `catalyst_present` | `items: [{item,count}]` | `catalyst_present` | `items: [ <item> ]` + `count`；多于 1 组则无法映射 |
| `fluid_present` | `fluid`、`require_source` | `fluid_present` | `fluid`、`require_source`（同名） |

叶级 `negated` 原样保留。

**v1 附加映射**（早期扁平文件，需先由旧链路规范化或手工处理）：

| v1 扁平字段 | 新条件叶 |
|---|---|
| `dimension`（字符串） | `{ "type": "...:dimension", "dimensions": [ ... ] }` |
| `need_outdoor`（布尔） | `{ "type": "...:outdoor", "negated": <取反> }` |
| `surrounding_blocks` | `{ "type": "...:surrounding_blocks", up/down/... }` |
| `catalyst_items`（检而不耗的部分） | `{ "type": "...:catalyst_present", "items": [...], "count": N }` |
| `inner_fluid`（检而不耗的部分） | `{ "type": "...:fluid_present", "fluid": "...", "require_source": ... }` |

## 6. 消耗映射（谓词 / 消耗拆分）

**v2 旧格式里没有"是否真消耗"的开关**：`consumption` 指令 = 真消耗，`conditions` 里的 `*_present` 叶 = 只检查。转换器据此拆分：

| 旧位置 | 新位置 |
|---|---|
| `consumption.catalyst_items`（1 组） | `effects` 加 `{ "type": "...:consume_catalyst", "items": [<item>], "count": <count×source_multiple> }` |
| `conditions` 中的 `catalyst_present` 叶 | 保持条件叶（`effects` 里**不加**消耗） |
| `consumption.inner_fluid` | `effects` 加 `{ "type": "...:consume_fluid", "fluid": <fluid>, "require_source": <原值> }`（`fluid` 为空则省略，表示任意流体） |
| `conditions` 中的 `fluid_present` 叶 | 保持条件叶 |
| `source_multiple` | `effects` 加 `{ "type": "...:consume_source", "count": <source_multiple> }`（总是写出） |

**注意**：
- 新 `consume_catalyst` / `catalyst_present` 只有一个 `count`，旧格式每项各自带 `count`；多于 1 组时转换器**报 `unmapped` 而不是猜测合并**。
- 新模型的消耗效果**每次触发执行**；如需概率或延迟，可在效果上加 `chance` / `delay_ticks`。

## 7. `/idtw config convert`

```
/idtw config convert
```

| 项 | 实际行为 |
|---|---|
| 权限 | OP（权限等级 ≥ 2） |
| 输入 | `config/itemdespawntowhat` 下的 `*.json`（**跳过** `rules/`、`_old_chain_backup/`、`server.json`）；按文件名识别旧类型，只处理 9 个内置类型 |
| 输出 | 新覆盖层 `config/itemdespawntowhat/rules/**`（经 `RuleOverlayWriter` 按 id upsert） |
| 规则 id | `<namespace>:<旧类型名>_<文件内序号>`，如 `itemdespawntowhat:item_to_item_0` |
| 备份 | 转换前把命中的旧文件复制到 `config/itemdespawntowhat/_old_chain_backup/<相对路径>`（保留目录结构） |
| 报告 | `converted`（转换条数）/ `backedUp`（备份文件数）/ `writtenFiles`（写入文件数）/ `unmapped`（无法映射条目）/ `notes`（字段级损失提示） |
| 判定原则 | **能无歧义映射就转换，否则显式报告**，绝不静默丢弃（Q4/Q19） |
| 幂等性 | 走 `RuleOverlayWriter` 按 id upsert，重复执行不会重复追加；旧文件本身**不会被删除**（清场留给阶段⑥） |

**为什么不做自动迁移**：Q46 明确"旧配置的**自动**迁移"是非目标；旧链路的读时写回（`schema_version`）正是被淘汰的做法——它让"读配置"产生副作用，并依靠运行时快照写回，会删掉 disabled / 编译失败的规则（A1）。

## 8. 手工迁移步骤（命令不可用时）

1. **备份**：把 `config/itemdespawntowhat/` 整个复制一份。
2. **逐文件读旧 JSON**，按 §3～§6 改写；建议一个旧文件对应一个新文件，如 `rules/mypack/item_to_item.json`。
3. **给每条规则补 `id`**（命令会生成 `<ns>:<type>_<index>`；手工迁移时建议显式声明，避免依赖文件名推导）。
4. **条件改写**：删掉 `groups` / `conditions` / `params` 三层壳，参数平铺到叶对象；单值字段变数组（`dimension`、`biome`）。
5. **消耗改写**：按 §6 拆成 `consume_*` 效果与 `*_present` 条件，分别写进 `effects` / `conditions`。
6. **对照验证**：用 `/idtw config list` / `/idtw config validate` 确认规则被加载且无 ERROR；对照测试期间**只保留一侧配置**，否则同一物品会被新旧两条链路各转化一次。
7. **切换**：确认行为符合预期后，把旧文件移出 `config/itemdespawntowhat/<ns>/`（阶段⑥ 正式删除旧链路）。

### 迁移示例

**旧**（`item_to_item.json` 的一条）：

```json
{
  "schema_version": 2,
  "item": "minecraft:stone",
  "result": "minecraft:diamond",
  "source_multiple": 1,
  "result_multiple": 2,
  "conversion_time": 300,
  "conditions": {
    "groups": [
      [ { "type": "itemdespawntowhat:dimension", "params": { "dimension": "minecraft:overworld" }, "negated": false } ]
    ]
  },
  "consumption": {
    "catalyst_items": [ { "item": "minecraft:blaze_powder", "count": 1 } ],
    "inner_fluid": { "fluid": "minecraft:water", "require_source": true }
  },
  "priority": 0,
  "enabled": true,
  "notes": "示例"
}
```

**新**（`config/itemdespawntowhat/rules/mypack/stone_to_diamond.json`，等价于转换器的输出形状）：

```json
{
  "id": "mypack:stone_to_diamond",
  "enabled": true,
  "priority": 0,
  "notes": "示例",
  "trigger_after_seconds": 300,
  "source": { "items": ["minecraft:stone"] },
  "conditions": [
    [
      { "type": "itemdespawntowhat:dimension", "dimensions": ["minecraft:overworld"], "negated": false }
    ]
  ],
  "effects": [
    { "type": "itemdespawntowhat:consume_source", "count": 1 },
    { "type": "itemdespawntowhat:consume_catalyst", "items": ["minecraft:blaze_powder"], "count": 1 },
    { "type": "itemdespawntowhat:consume_fluid", "fluid": "minecraft:water", "require_source": true },
    { "type": "itemdespawntowhat:spawn_item", "item": "minecraft:diamond", "count": 2, "limit": 30, "radius": 6 }
  ]
}
```

## 9. 迁移后常见拒载原因

| 现象 | 原因 | 处置 |
|---|---|---|
| "未注册的类型/效果类型/条件类型" | `type` 拼错，或用了未注册的类型 | 核对类型 id（[config-reference.md](config-reference.md) §7/§8） |
| "多条目规则文件中的每条规则都必须显式声明 id" | 数组文件里某条省了 `id` | 补 `id`，或拆成单条文件 |
| "非法的规则 id" | id 用了大写/空格等非法字符 | 用小写 `[a-z0-9_.-]` |
| "source.items 至少需要一个物品或标签" | 只搬了 `result` 忘了 `item` | 补 `source.items` |
| "block 与 use_source_block 至少需要其一" | `block_of_item` 与 `result` 都没迁移成功 | 补其一 |
| "同一规则内重复声明消耗效果" | 多个消耗条目折叠成同类型 | 合并成一条，或改用 `*_present` 条件 |
| "disabled / delete 只能出现在 config 覆盖层" | 把控制字段写进了数据包文件 | 控制字段只放覆盖层 |
| "conditions 中存在空条件组" | 手工删叶后留下了空组 | 删掉空组或补叶 |

## 10. 相关文档

- 新字段全表：[config-reference.md](config-reference.md)
- 三层作用域与合并语义：[ADR-0014](../adr/0014-three-layer-scope-and-overlay-merge.md)
- 旧→新总表：[../plan/plan-backend-rewrite.md](../plan/plan-backend-rewrite.md) 第五节
