# 破坏性更新说明：v1.2.1 旧 config 不再加载

> 适用对象：使用过 v1.2.1（及更早）旧版配置的玩家与整合包作者。
> 权威依据：[v1.2.1-migration-evaluation.md](../plan/v1.2.1-migration-evaluation.md)（P8 迁移评估）与 [plan-frontend-rewrite.md](../plan/plan-frontend-rewrite.md) §13.3。

## 一句话

旧版五类 config 文件**不再被加载**，也**没有自动迁移**；你的旧 JSON 仍原样留在磁盘上（不删除、不改写），需要时在编辑器里手工重建规则。

## 1. 受影响的对象

下列文件（均为顶层 JSON 数组，位于 `config/itemdespawntowhat/<命名空间>/`）已不再被读取、不再参与转化，也不会被自动转换：

| 旧文件 | 旧内容 |
| --- | --- |
| `item_to_item.json` | 掉落物 → 掉落物 |
| `item_to_mob.json` | 掉落物 → 实体 |
| `item_to_block.json` | 掉落物 → 方块 |
| `item_to_xp_orb.json` | 掉落物 → 经验球 |
| `item_to_world_effect.json` | 掉落物 → 天气 / 闪电 / 爆炸 / 箭雨等世界现象 |

- 文件**不会被删除或改写**：可以随时打开对照、备份或分享。
- 加载期不会报错刷屏，只是这些规则不再生效（规则来源改为数据包与覆盖层 `config/itemdespawntowhat/rules/`）。
- 旧的自动备份目录 `_old_chain_backup/` 也随之退役，不会再产生新备份。

## 2. 为什么不自动迁移

P8 评估的结论是**破坏性更新**：旧五类配置与最终模型之间是**执行模型差异**，不是字段改名；且有多项行为在最终模型里**没有对应字段**（结构性缺失）。转换器只能「拒绝这些规则」或「静默降级」，两种结果都会让你在不知情的情况下丢失行为，因此本项目选择不提供转换器，而是把差异**全部公开列出**：

- 逐项映射表（五组检查项，逐字段对照）：[v1.2.1-migration-evaluation.md](../plan/v1.2.1-migration-evaluation.md) 的 §4。
- 权威损失清单（无法保留 / 需人工确认的 7 项）：同文件 §5，以及本说明第 4 节。

## 3. 重建方式（不需要手写 JSON）

1. 服务端执行 `/idtw config edit`（需要权限等级 2；单人存档直接放行），打开规则编辑界面。
2. 界面分四个页签：**基本信息 / 源物品 / 触发条件 / 效果**。
3. 源物品、条件与效果的多数资源引用（物品 / 方块 / 实体 / 流体 / 战利品表 / 群系 / 维度 / 标签 / 状态效果）都能**按名字搜索挑选**，不必记技术 id，也不必拼 JSON。
4. 条件支持任意嵌套的 与（all_of）/ 或（any_of）/ 非（inverted），覆盖全部 10 种条件；效果覆盖全部 12 种效果。
5. 保存后规则立即重建索引并对已加载区块回扫，与 `/idtw config reload` 等效。
6. 想从现成配置起步时，可以从内置样本（见第 5 节）或编辑器模板创建新规则再改。

常用命令：`/idtw config reload`（重载）、`/idtw config validate`（体检全部来源）、`/idtw config list`（列出规则与来源层）。

## 4. 无法保留的 7 项（旧行为 → 现在的做法）

| # | 旧行为 | 现在的做法 |
| --- | --- | --- |
| 1 | 爆炸方向 `explosion_direction_type`（默认 `FLAT`） | 新爆炸效果没有方向概念，只有`威力 / 起火 / 纯视觉`三个参数；有方向要求的表现请用多个效果组合实现 |
| 2 | 闪电的「纯视觉」用法（`side_effect=lightning` + `visual_only=true`） | 新闪电总是真实落雷（可能伤害实体、点燃方块）；只想看效果请用爆炸的「纯视觉」或箭雨 |
| 3 | 现象间隔（`lightningInterval` / `explosionInterval` / `arrowInterval`）与爆炸的「次数」（旧实现 = 轮数） | 新实现固定节奏：闪电每 8 刻一次、箭雨每 2 刻一次、爆炸只炸一次；需要多份请调效果自身的 `count` |
| 4 | 方块形状 `CUBE` / `SPHERE` | 新 `place_block` 只有 `SQUARE`（方形）、`CIRCLE`（圆形）、`CROSS`（十字） |
| 5 | 经验球的倍率组合（轮数 × `result_multiple` × `xp_per_item`） | 新 `spawn_xp` 只有一个`经验量`参数，按 `per_source_item` 决定「每轮」还是「每个源物品」 |
| 6 | 催化剂「逐条目规定数量、各自都要满足、按量守恒消耗」 | 新 `catalyst_present` 判定为「命中任意条目的**合计数量** ≥ count」，`consume_catalyst` 按总量由近及远扣除 |
| 7 | 旧轮数压缩（催化剂存量与结果容量会压缩源物品的总消耗） | 新的轮数只由源物品的每轮消耗量决定（`堆叠数 / 每轮消耗`） |

## 5. 能保留的旧语义（摘要）

| 旧字段 | 新写法 |
| --- | --- |
| `item` / `result`（支持 `#标签`） | 规则`源物品`与产物效果的条目参数 |
| `source_multiple` | `consume_source` 的 `count` |
| `result_multiple` | `spawn_item` / `spawn_entity` / `place_block` 的 `count` |
| `result_limit` / `radius_limit` | 对应效果的 `limit` / `radius` |
| `conversion_time`（秒） | 规则的 `trigger_after_seconds` |
| `need_outdoor` / `dimension` / `surrounding_blocks` / `catalyst_items` / `inner_fluid` | 条件树中的 `outdoor` / `dimension` / `surrounding_blocks` / `catalyst_present` / `fluid_present` 条件 |
| `consume_catalyst` / `consume_fluid` / `require_source` | `consume_catalyst` / `consume_fluid` 效果与 `fluid_present.require_source` |
| `weather_duration_ticks` / `is_thundering` / `explosion_power` / `explosion_fire` | `weather` 效果的 `duration_ticks` / `thundering` 与 `explosion` 效果的 `power` / `fire` |
| `arrow_pickup_status` / `arrow_potion_effects` | `arrow_rain` 效果的 `pickup` / `potion_effects` |
| `priority` / `enabled` / `notes` | 同名保留（并新增 `display_name` 显示名） |

逐字段的权威对照请以上述 §4 映射表为准。

## 6. 内置样本与参考

可直接参考的 8 个内置样本（数据包规则，位于 `common/src/main/resources/data/itemdespawntowhat/idtw/rules/`）：

- `builtin_item_to_item.json`、`builtin_item_to_entity.json`、`builtin_item_to_block.json`
- `builtin_multi_effect.json`、`builtin_loot_and_chance.json`、`builtin_conditions.json`
- `builtin_weather_and_light.json`、`builtin_arrow_rain.json`

这些样本合起来覆盖全部 10 种条件与 12 种效果，并已启用若干条便于直接观察效果。

其他参考：

- 迁移评估逐项映射与权威损失清单：[v1.2.1-migration-evaluation.md](../plan/v1.2.1-migration-evaluation.md) §4 / §5。
- 结论记录：[plan-frontend-rewrite.md](../plan/plan-frontend-rewrite.md) §13.3。
- 第三方类型扩展：[custom-types.md](custom-types.md)；验收清单：[manual-acceptance.md](manual-acceptance.md)。

## 7. 转换指令已退役

`/idtw config convert` 及其实现（`core/command/RuleConvertService.java`）已删除，配置子命令现有 `reload / edit / validate / list / edit-lock status|release`。若你的脚本或说明文档里调用过 convert，请改用本说明第 3 节的手工重建流程。

## 8. 第二轮规则契约变更（在同一新链路内，破坏性）

第二轮后端改造（提交 `63eea8b`）在新链路内部再次调整了规则 JSON 契约。旧格式**一律明确报错、不做静默兼容**；命中下列字段的既有规则需按新写法改写（见 [manual-acceptance.md](manual-acceptance.md) 4.3.5）。

| # | 变更 | 旧写法 → 新写法 |
| --- | --- | --- |
| 1 | 消失方式触发 | 新增 `triggers`（`natural` / `fire` / `lava` / `cactus` 数组）；**缺省仍仅自然消失**，声明环境方式后可在火/岩浆/仙人掌销毁时触发 |
| 2 | 固定源成本 | 新增 `source_cost`（**必须为正整数**）；取消「源成本为 0 关闭消耗」的行为——只想消耗催化剂/流体的输入应作为催化剂，不作为源物品。`source_cost` 与 `consume_source` 效果互斥 |
| 3 | 催化剂固定成本写法 | `catalyst_cost` 由**整数**改为**对象** `{ "items": [...], "count": 1, "radius": 1 }`；整数写法解码即报错。与 `consume_catalyst` 效果互斥 |
| 4 | 候选结果 | 新增 `outcomes`（元素为 `{ "id", "effects", "safe_spawn", "fill_origin" }`）与 `combination`（`round_robin` 默认 / `priority`）。`effects` 与 `outcomes` **至少声明一个、且不得同时声明**；未声明 `outcomes` 时顶层 `effects` 隐式映射为唯一候选 `default`（旧规则行为不变） |
| 5 | 契约版本 | 新增 `schema_version`（正整数，**当前仅支持 `1`**），声明其它值校验拒绝 |
| 6 | 条件表达式形状 | 由「外层 OR / 内层 AND」的 **DNF 二维数组**改为**递归条件树**：`{"op":"all_of","terms":[...]}` / `{"op":"any_of","terms":[...]}` / `{"op":"inverted","term":{...}}` / `{"op":"leaf","condition":{...}}`。旧数组格式、`conditions.groups`、叶级 `negated` 一律解码报错；取反只能用 `inverted` 节点 |
| 7 | 旧转换入口 | `/idtw config convert` 与 `RuleConvertService` 继续不可用（见第 7 节） |

说明：第 6 条的条件树形状在更早的前端重写（ADR-0018）中已落地，此处一并列出以保证破坏性清单完整。内置数据包样本已同步更新为上述新写法，可作为改写参照（见第 6 节）。
