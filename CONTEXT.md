# ItemDespawnToWhat 配置领域

> 当前状态（2026-10-03）：后端条件树、独占编辑协议 v2、客户端 UI kit 与编辑数据结构、选择目录数据源、规则编辑器界面与表单引擎、草稿持久化与撤销均已落地（P1–P8 代码落盘，构建与静态复核通过）；第二轮后端改造（消失方式触发、固定成本、候选结果、共享调度器、掉落物状态、结算返还）已实现并归档，双平台游戏内手动验收待执行，见 [manual-acceptance.md](docs/guide/manual-acceptance.md)。
> 权威契约：[plan-frontend-rewrite-contract.md](docs/plan/plan-frontend-rewrite-contract.md)（冻结形状）与 [plan-frontend-rewrite-forms.md](docs/plan/plan-frontend-rewrite-forms.md)（逐字段取值域）；前端决策见 [docs/adr/](docs/adr/) 的 0018–0022，第二轮后端决策见 0023–0024 与归档底稿 [docs/archive/backend-round-2/PLAN.md](docs/archive/backend-round-2/PLAN.md)（§2 行为契约）。

掉落物在自然消失前，按数据包 / config 覆盖层中的规则转化为其他内容（物品、实体、方块、经验、世界效果等）。本词汇表覆盖新链路（`core/**`）的配置与运行时词汇；已退役的历史术语标注为「旧链路」。

## 项目结构与隔离规则

| 顶层目录 | 职责 |
|---|---|
| `common/src/main/java/.../core/**` | 与平台无关的后端：模型、类型、运行时、网络协议、目录、命令 |
| `common/src/main/java/.../client/**` | 与平台无关的客户端：`client/edit`（编辑数据结构）、`client/net`（协议入口）、`client/ui`（界面）、`client/key` |
| `common/src/main/resources/` | 内置数据包（`data/itemdespawntowhat/idtw/**`）、语言文件、`META-INF/services` |
| `fabric/**`、`neoforge/**` | 平台接入：loader 事件、平台实现、网络收发落地 |

后端分层（均位于 `core/` 下）：`api`（对外接口与常量）、`model`（规则模型与 Codec）、`type`（内置条件/效果类型）、`registry`（类型注册表）、`runtime`（求值、调度、执行）、`state`（掉落物保护/冷却/禁转状态）、`service`（加载/校验/保存/编辑会话）、`network`（协议 DTO 与传输）、`catalog`（选择目录）、`command`、`load`、`debug`、`extension`（`RuleTypeProvider` SPI）。

硬性隔离规则：

1. **`common/` 不得 import `fabric/` 或 `neoforge/` 的任何类**；平台差异全部走 `api` 下的窄接口实现。
2. **`core/**` 严禁引用 `client/**`**。需要由客户端实现的动作通过**静态 sink** 注入，例如 `OpenRuleEditorPayload.installOpenEditorPayloadSink(Consumer)` 与 `RuleEditPayloadRouter.install*Sink(...)`；界面注册槽是 `EditorScreenHooks.setOpener(...)`，未注册只记日志、绝不抛异常。
3. `client/edit` 可以依赖 core 的公开类型（`core.api` / `core.model`）与原版客户端类，但**不发起网络请求、不打开界面**；`client/ui` 不得被 `client/edit` 反向依赖。
4. 没有第三条发送路径：上行协议一律经 `client/net/RuleEditClientWorkspace`。

构建：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"`（内部互斥串行化，约 40 秒）；不要裸跑 `gradlew.bat`。

## 规则 JSON 顶层形态

一条规则是一个**顶层 JSON 对象**（编辑器保存形态；顶层数组只用于只读原始文件展示）：

| 字段 | 必需 | 说明 |
|---|---|---|
| `id` | 否 | 规则稳定标识；省略时由文件路径推导，覆盖层写入时由文件名决定 |
| `enabled` | 否 | 缺省 `true`；`false` 表示停用但保留内容 |
| `priority` | 否 | 优先级，缺省 0；同一掉落物只执行优先级最高的一条命中规则 |
| `display_name` | 否 | 人类可读名（中英文可选），trim 后为空视为未设置，上限 128 码点 |
| `notes` | 否 | 备注（界面上限 1024 码点） |
| `conditions` | 否 | 规则级**条件树**；无条件的规则**省略该字段**（不得输出 `null`） |
| `source` | 是 | `{ "items": [...], "exclude": [...] }`，项为物品 id 或 `#tag` |
| `trigger_after_seconds` | 否 | 触发年龄门槛（秒），缺省 300 |
| `triggers` | 否 | 消失方式集合，取值 `natural` / `fire` / `lava` / `cactus`；缺省仅 `natural` |
| `source_cost` | 否 | 固定源成本（正整数）；缺省时按隐式消耗 1 或显式 `consume_source` 之和推算 |
| `catalyst_cost` | 否 | 催化剂固定成本**对象** `{ "items": [...], "count": 1, "radius": 1 }`（整数写法已废弃） |
| `combination` | 否 | 候选结果组合模式，`round_robin`（默认）/ `priority` |
| `outcomes` | 否 | 候选结果数组，元素为 `{ "id", "effects", "safe_spawn", "fill_origin" }`；与顶层 `effects` **二选一** |
| `effects` | 否 | 平铺效果列表；每个效果也可带自己的 `conditions`（同一条件树形状）。`effects` 与 `outcomes` **至少声明一个、且不得同时声明** |
| `schema_version` | 否 | 规则契约结构版本（正整数）；当前仅支持 `1`，其它值校验拒绝 |

覆盖控制条目只声明 `id` 加一个开关：`{ "id": "...", "disabled": true }` 停用同 id 基底规则，`{ "id": "...", "delete": true }` 删除同 id 基底规则。

**覆盖文件命名**：一文件一规则，文件名由规则 `id` 派生——把 `:` 与 `/` 换成 `_`，追加 `.json`（如 `mypack:stone_to_diamond` → `mypack_stone_to_diamond.json`）。

## 限额

| 项目 | 上限 | 强制点 |
|---|---|---|
| 单个条件树的叶数 | 128 | 校验期与解码期 |
| 单个条件树的节点数 | 256 | 同上 |
| 单个条件树的深度 | 16（根深度 = 1） | 同上 |
| 每条规则的效果数 | 32 | 校验期 |
| `source.items` + `source.exclude` 项数 | 256 | 校验期 |
| `display_name` 长度 | 128 码点 | 校验期（trim 后） |

限额**按逐表达式计**：规则级条件树与每个效果级条件树各自独立，不累加（见 `ConditionLimits`）。

## i18n 前缀分工

| 前缀 | 用途 | 例子 |
|---|---|---|
| `gui.itemdespawntowhat.edit.*` | **界面文本**：标题、按钮、字段标签、枚举值、类型显示名 | `gui.itemdespawntowhat.edit.field.y_level.min` |
| `itemdespawntowhat.edit.*` | **协议回执与校验问题的 messageCode**（服务端生成、客户端翻译） | `itemdespawntowhat.edit.status.lock_busy` |

字段标签 `gui.itemdespawntowhat.edit.field.<类型 path>.<字段>`、枚举值 `gui.itemdespawntowhat.edit.enum.<组>.<值>`、类型显示名 `gui.itemdespawntowhat.edit.<condition|effect>.<类型>`。`en_us.json` 与 `zh_cn.json` 的键集合必须完全一致（当前各 698 个键）。协议键与消息码细节见 [docs/guide/message-codes.md](docs/guide/message-codes.md)。

## 语言

**转化 (Conversion)**:
即将消失的掉落物在过期前转变为别的东西这一行为本身。
_Avoid_: 变换、转换

**模板 (Template)**:
GUI 入口卡片：一个模板对应一种效果类型，点击模板 = 新建一条只含该效果的规则。它不再是注册单元，也不进配置文件。
_Avoid_: 转化类型（后端已退役）、config category

**规则 (Rule)**:
后端**唯一的配置单元**：`id` + 源匹配 + 消失方式集合 + 条件树 + 固定成本 + 候选结果集合与组合模式（外加 `enabled` / `priority` / `display_name` / `notes` / `trigger_after_seconds` / `schema_version`）。同一掉落物只会执行**优先级最高的一条**命中规则，"一次多效果"由规则内的效果列表（顶层 `effects` 或候选结果 `outcomes` 内的效果）表达。`id` 是唯一身份，`display_name` 只影响显示、不参与排序。
_Avoid_: 转化规则（旧链路叫法）、配置项（口语可接受）

**规则 id (Rule Id)**:
规则的稳定标识（`ResourceLocation`，如 `mypack:stone_to_diamond`），是被覆盖、被删除、被日志与命令引用的依据；文件里可省略，由文件路径推导，覆盖层文件名由它派生。
_Avoid_: 内部 id、UUID

**源匹配 (Source Matcher)**:
规则中的 `source`：`items`（物品 id 或 `#tag` 列表）与 `exclude`（排除列表，优先于匹配）。不做组件级匹配。
_Avoid_: 过滤器、条件（条件另有所指）

**效果 (Effect)**:
规则被触发后要执行的一个动作，位于规则的 `effects` 有序列表中：`type` + 类型专属参数 + 通用字段 `delay_ticks` / `chance` / `conditions`。命中规则内的效果**按顺序启动、分批完成**，不做事务回滚；单个效果抛异常只记录 ERROR 并继续后续效果。
_Avoid_: 结果（旧链路叫法）、动作（口语可接受）

**效果类型 (EffectType)**:
一种已注册的效果类别，自带参数 Codec、参数校验器与服务端执行器（`{ id, codec, validator, executor }`）。内置 12 个：`spawn_item` / `spawn_entity` / `place_block` / `spawn_xp` / `loot_table` / `lightning` / `arrow_rain` / `weather` / `explosion` + 消耗类 `consume_source` / `consume_catalyst` / `consume_fluid`。
_Avoid_: 转化类型、效果种类

**消耗效果 (Consumption Effect)**:
`consume_source` / `consume_catalyst` / `consume_fluid` 三个效果类型：消耗是**效果**而不是条件的副作用。每组源成本 = 显式 `source_cost`；未声明时按隐式消耗 1 个源物品（未声明任何 `consume_*`）或显式 `consume_source.count` 之和推算；`consume_fluid` 只作实时存在条件，不换算份数。
_Avoid_: 消耗指令（旧链路叫法）

**条件 (Condition)**:
条件树叶子里的原子谓词（一个条件类型 + 参数），只负责判定"是否满足"，**不承担消耗**。叶级取反已删除，取反只能由 `inverted` 节点表达。
_Avoid_: 限制（"限制"另指效果的 `limit`）

**条件叶 (Condition Leaf)**:
条件树中的原子谓词，JSON 形状为**扁平对象**：`{"op":"leaf","condition":{"type":"itemdespawntowhat:y_level","min":0}}`——类型专属字段与 `type` 同层，没有 `params` 子对象，也**不允许 `negated` 字段**（出现即解码报错）。
_Avoid_: 条件项、条件实例

**条件树 (Condition Tree)**:
规则或单个效果上的触发谓词，由 `ConditionNode` 递归构成：`all_of`（全部）/ `any_of`（任一）/ `inverted`（取反）/ `leaf`（原子条件）。规则级与效果级使用**同一形状**；无条件时**省略 `conditions` 字段**（空 `all_of` 可构造、可序列化，但视为结构非法）。
_Avoid_: 条件表达式（旧字段名）、DNF 二维数组

**条件节点 (Condition Node)**:
条件树的一个节点，是 sealed 接口的四类实现：`AllOf(List<ConditionNode> terms)` / `AnyOf(List<ConditionNode> terms)` / `Inverted(ConditionNode term)` / `Leaf(Condition condition)`。`ConditionExpression` 只持有 `root`（可空）。
_Avoid_: 条件组（旧类 `ConditionGroup` 已删除）

**条件类型 (ConditionType)**:
一种已注册的条件类别，自带参数 Codec、参数校验器、服务端求值器与**可求值性**检查（`{ id, codec, validator, evaluator, evaluabilityCheck }`）。内置 10 个：dimension / biome / weather / outdoor / surrounding_blocks / catalyst_present / fluid_present / time_of_day / y_level / light_level。
_Avoid_: condition kind、条件种类

**求值器 (Evaluator)**:
条件类型的服务端实现，必须是**纯谓词**：只读取 `ConditionContext`，不得修改世界；返回未取反的原始结果（取反由条件树承担）。
_Avoid_: 检查器、checker（旧链路叫法）

**可求值性 (Evaluability)**:
`AVAILABLE` / `UNAVAILABLE`：条件在**当前上下文里能否判定**（例如 `surrounding_blocks` 在周围区块未加载时为 `UNAVAILABLE`）。这类门禁必须写在条件的 `evaluability(...)` 里，不得硬编码进求值器。
_Avoid_: 跳过、可用性检查

**求值结果 (ConditionResult)**:
条件求值的四态：`MATCH` / `NO_MATCH` / `UNAVAILABLE` / `ERROR`。`inverted` 只在 MATCH ↔ NO_MATCH 之间翻转，`UNAVAILABLE` / `ERROR` 原样透传（不吞）；`all_of` / `any_of` 按序短路，未定态继续累积，`UNAVAILABLE` 优先于 `ERROR`；**只有 `MATCH` 会触发效果**。
_Avoid_: 布尔结果、真假（四态不等价于布尔）

**执行器 (Executor)**:
效果类型的服务端实现，只负责"做什么"（世界操作）；延迟、概率、效果级条件、异常隔离、隐式消耗全部由运行时统一处理。
_Avoid_: 处理器

**扁平类型分发 (Flat Type Dispatch)**:
效果与条件叶的类型分发方式：类型专属字段与通用字段处于**同一个 JSON 对象**中，按 `type` 字段查注册表取对应 `MapCodec`，不产生 DFU 默认 `dispatch` 的嵌套 `value` 字段。
_Avoid_: 多态解码（口语可接受）、嵌套分发

**来源层 (Source Layer)**:
规则来源的三层作用域，优先级 **config 覆盖层 > 世界数据包 > 内置数据包**：`BUILTIN` / `WORLD` / `OVERLAY`。
_Avoid_: 配置层、优先级层

**覆盖层 (Overlay)**:
第三层作用域，`config/itemdespawntowhat/rules/**`：**唯一可写**的一层，是 GUI 与命令的写入目标；服务端权威，经一个 S2C 合并快照下发给客户端。
_Avoid_: 用户配置、本地配置

**数据包基底 (Datapack Base)**:
前两层（内置数据包 + 世界数据包）的合称：只读，由原版资源包机制加载与同步，不参与 mod 自研网络。
_Avoid_: 内置配置（会与"内置数据包"混淆）

**来源标识 (Origin)**:
一条规则最终生效的来源描述（所属层 + 数据包 id + 文件路径），用于报错定位与 `config list` 展示；协议里取值为 `overlay` / `datapack` / `mixed`。
_Avoid_: 出处、来源路径

**覆盖控制条目 (Override Control Entry)**:
覆盖层中只声明 `id` + `disabled`/`delete` 的条目：`disabled` 停用同 id 基底规则（保留内容、最终来源记为覆盖层），`delete` 删除同 id 基底规则。同一来源层内**普通条目先合并、控制条目后应用**。
_Avoid_: 停用标记、删除标记

**规则索引 (Rule Index)**:
把规则集合整理成"物品 → 候选规则"的查询结构：直接物品项建索引，标签项懒展开并缓存；缓存随 reload 整体丢弃。排序键为 优先级 desc → 条件叶数 desc → 定义序。
_Avoid_: 规则表、注册表（注册表另指类型注册表）

**到期事件 (Expiry)**:
规则触发的时机语义：各规则年龄门槛 = `min(trigger_after_seconds × 20, lifespan - 1)`，自然消失入口另保留最后一次预算检查，在物品自然 discard **之前**判定并拦截。原版没有"消失后"回调，因此拦截点在 discard 之前。
_Avoid_: 超时、过期检查（口语可接受）

**退避 (Backoff)**:
条件不满足时的重试策略：1s → 2s → 4s → 封顶 `backoff_max_ticks`，直到自然消失前停止；不再以"检查次数"计时。
_Avoid_: 重试间隔（口语可接受）

**追踪 (Tracking)**:
把一个掉落物标记为转化检查对象；只有存在候选规则的掉落物才进入追踪与调度。reload 后回扫已加载区块重建追踪。
_Avoid_: 监控

**追踪状态 (Tracked State)**:
per-level 内存追踪状态（失败次数、转化锁、下次到期刻）；任务上下文持有维度对象，维度卸载/停服即释放。已提交转化标记写入实体 tag，防止重载或区块重进重复提交；其它调度状态不跨重启保存。
_Avoid_: 缓存表、持久状态

**议题 (Issue)**:
加载/校验/转换过程中产生的一条问题记录，协议层结构为 `RuleIssue(severity, ruleId, origin, fieldPath, messageCode, messageArgs, fallbackMessage)`：`severity` = `error`/`warning`/`info`，`origin` = `overlay`/`datapack`/`runtime`/`network`，`fieldPath` 用与条件树一致的路径（如 `conditions.terms[0].condition.min`），`messageCode` 必须进中英两个语言文件。统一进 `IssueCollector`，供日志、命令与编辑界面回显。
_Avoid_: 错误日志（Issue 是结构化记录，不等于日志行）

## 事件与编辑协议

**快照 (Snapshot)**:
服务端下发给客户端的"当前生效规则全集"，协议结构 `RuleSnapshot(version, contextRevision, entries, issues)`。客户端在有界窗口内按版本与修订号判断新鲜度；超大快照分片下发（上限 4 MiB）。
_Avoid_: 配置同步包（口语可接受）

**快照条目 (Snapshot Entry)**:
`RuleSnapshotEntry(id, origin, status, editable, effective, base, overlay, issues)`：`origin` = `overlay`/`datapack`/`mixed`，`status` = `active`/`disabled`/`masked`/`invalid`，三个规则体字段中缺省者输出 JSON null。**`editable` = `base` 或 `overlay` 非空（该条目存在规则内容）**，因此**纯数据包条目同样为 `true`**（可基于它创建覆盖条目）；`editable` 不表示「能否表单编辑」——`status=invalid` 的条目也可以为 true，是否只读展示由客户端按 `status`/`issues` 决定。覆盖层条目保留**文件原始 JSON**（不丢未识别字段）；数据包条目由模型编码下发。
_Avoid_: 规则视图（旧链路叫法）

**变更集 (Change Set)**:
客户端提交的批量修改 `RuleEditChangeSet(expectedVersion, edits)`，每个 `RuleEdit(id, action, rule)` 的 `action` 取 `upsert`（必须携带完整规则 JSON）或 `delete`（只带 id）。服务端**整批接受或整批拒绝**，单条失败不半途落盘。
_Avoid_: 补丁、diff（口语可接受）

**修订号 (Context Revision)**:
覆盖层的整数上下文版本，持久化在覆盖层根目录的 `.edit_version`（非 `.json` 后缀，不会被规则读取器扫描）。客户端保存时携带自己视图的 `expectedVersion`；服务端版本不一致即**拒绝整批并回传最新快照**（乐观并发），避免覆盖他人改动。快照同时携带 `version` 与 `contextRevision`。
_Avoid_: 版本戳（旧术语）、乐观锁

**独占编辑会话 (Exclusive Edit Session)**:
**全局唯一的目标级锁**，目标 id 为 `itemdespawntowhat:rules`（不再按玩家隔离）：一个编辑目标同一时刻只有一个会话可写，取代旧链路的按玩家会话 + 版本戳并发模型。状态机 `FREE → OPENING → ACTIVE → APPLYING → FREE`；常量：OPENING 确认窗口 15 秒、心跳间隔 10 秒、租约 60 秒（按服务端活动 tick 计，暂停时不流逝）、所需权限等级 2、幂等操作记录 16 条。唯一开屏入口是 `RuleEditService.openEditor(player, tick)`，取锁失败也下发 `OpenRuleEditorPayload`（`statusCode=LOCK_BUSY` 且 `sessionId` 为空）。
_Avoid_: 编辑锁（旧链路叫法）、编辑会话（旧 per-player 语义）

**回执状态 (Save Status)**:
`RuleSaveStatus` 的 12 个取值：SUCCESS / NO_CHANGES / NO_PERMISSION / LOCK_NOT_OWNED / LOCK_BUSY / SESSION_EXPIRED / VERSION_CONFLICT / VALIDATION_FAILED / WRITE_FAILED / SAVED_NOT_RELOADED / INVALID_REQUEST / UNAVAILABLE。`id()` 为小写下划线，翻译键 `itemdespawntowhat.edit.status.<id>`；客户端**只按 statusCode 分支，禁止解析文案**，细节问题走载荷里的 `issues`。
_Avoid_: 错误码（口语可接受）、解析提示文本判断结果

**权威落盘 (Authoritative Write)**:
保存的写回依据是**磁盘上的文件内容**，按 id 逐条应用增删改（原子写 + `.bak` 备份），而不是用运行时快照整文件覆盖——因此 disabled 规则、未涉及的规则、非法 JSON 文件都不会被保存动作删除。
_Avoid_: 保存配置（口语可接受）

**规则草稿 (Rule Draft)**:
客户端工作副本 `RuleDraft`，**以 `JsonObject` 为唯一事实来源**（不与后端 record 平行建模），保留未识别字段；提供 `id()` / `toJson()` / `toOverlayJson()` / 类型化读写 / 路径读写 / `conditionsOrNull` / `setConditions` / `effects` 系列操作。
_Avoid_: 视图模型（旧链路叫法）、客户端 DTO

**编辑会话门面 (Edit Session)**:
客户端 `EditSession`：所有草稿修改都必须走 `apply(opKey, Runnable)`，修改前自动快照并入撤销栈（上限 100 步），提供 undo/redo/脏标记/监听器。`opKey` 用于撤销提示文案（`gui.itemdespawntowhat.edit.undo.*`）。
_Avoid_: 事务（口语可接受）

**类型编辑器描述符 (Type Editor Descriptor)**:
`TypeEditorDescriptor(id, label, fields, readOnly)` + `EditorField`：客户端用声明式字段描述"某种条件/效果类型的参数长什么样"，供表单引擎渲染。`EditorFieldType` 共 20 种（含 CONDITION_TREE、CLIMATE_RANGE、NOTE、RAW_JSON 等）。注册入口 `ConditionEditorRegistry` / `EffectEditorRegistry`（重复注册抛 `IllegalStateException`）；**未注册的类型自动回退为只读 RAW_JSON 描述符**，原样保留未识别字段。
_Avoid_: 声明式参数规格（旧链路叫法）、schema 表单

**类型标签 (Type Labels)**:
`TypeLabels` 负责类型显示名的翻译键查找：本模组 `gui.itemdespawntowhat.edit.<kind>.<path>`，第三方 `gui.itemdespawntowhat.edit.<kind>.<namespace>.<path>`，回退旧键再回退原始 id 字符串。
_Avoid_: i18n key 拼接散落在界面代码

## 选择目录

**选择目录 (Rule Catalog)**:
供编辑器"按名字挑 ID"的服务端目录，共 9 类 `RuleCatalogType`：ITEM / BLOCK / ENTITY / FLUID / MOB_EFFECT / LOOT_TABLE / BIOME / DIMENSION / TAG。数据来源：静态注册表（物品、方块、实体、流体、状态效果）、服务端可重载数据（战利品表）、动态注册表（群系）、已加载维度键（维度）、五注册表标签合并（标签）。客户端按名字挑 ID **只能走本目录**，不允许手输注册表 id。
_Avoid_: 注册表浏览（口语可接受）

**目录条目 (Catalog Entry)**:
`RuleCatalogEntry(id, label, subLabel, icon)`：`label` 优先本地化键（物品/方块/实体用 `getDescriptionId()`、群系用 `biome.<ns>.<path>`、维度与战利品表无原版键则用原文 id）、`subLabel` 为命名空间、`icon` 是物品/方块贴图 id 字符串（客户端解析失败走缺省渲染，不得抛异常）。
_Avoid_: 选项、候选（口语可接受）

**目录修订号 (Catalog Revision)**:
`RuleCatalogSource.revision()`：目录内容未变时稳定、变化后（数据包 reload、注册表变动）改变，服务端据此失效缓存。`RuleCatalogSources.builtin(server)` 每个服务端实例只构建一次，`all()` 结果按 id 字典序稳定排序并缓存，分页由消费方切。
_Avoid_: 目录版本（口语可接受）

**消息码 (Message Code)**:
协议回执与校验问题的**翻译键**（`itemdespawntowhat.edit.*`），与会话语义分离：状态码决定分支，消息码决定文案，`fallbackMessage` 是缺失翻译时的英文兜底。
_Avoid_: 错误文本（服务端不下发成品文案）

## 核心玩法（运行时）

**消失方式 (Trigger Kind)**:
物品被销毁（消失）的途径类别，取值 `natural` / `fire` / `lava` / `cactus`，一条规则可声明多种；未声明时按仅自然消失。自然消失沿用年龄门槛；三类环境销毁无等待、在销毁当刻判定，不额外设置转化倒计时。
_Avoid_: 转化条件（消失方式与其他匹配条件分别描述）

**固定成本 (Fixed Cost)**:
每组转化固定消耗的源物品数量：显式 `source_cost`（正整数）优先，否则按隐式 1 或显式 `consume_source.count` 之和推算。一组按固定成本整体结算、不按比例拆成半组；输入不足一组成本时不强行执行。
_Avoid_: 概率成本（源与催化剂是固定成本，不配概率/条件/延迟）

**催化剂固定成本 (Catalyst Cost)**:
规则级 `catalyst_cost` 对象 `{ items, count, radius }`，字段语义与 `consume_catalyst` 效果一致；必须足量支付，不能少扣却照常产出。与同名消耗效果互斥。
_Avoid_: 催化剂条件（判定用 `catalyst_present`，成本另指）

**转化组 (Conversion Group)**:
按固定成本消耗源物品并执行**一个候选结果**的一组转化，是结算的基本单位；组数由可用源物品与每组成本决定，同一候选内的多个产出效果共同限制完整组数。
_Avoid_: 执行批（调度分批不是结算单位）

**候选结果 (Outcome Candidate)**:
一个转化组可选的结果，形如 `{ "id", "effects", "safe_spawn", "fill_origin" }`；候选标识在规则内唯一。未声明 `outcomes` 时由顶层 `effects` 隐式映射为唯一候选 `default`。
_Avoid_: 效果（候选与其中的单个效果不是同一层级）

**组合模式 (Combination Mode)**:
从多个候选结果中选一个的方式：`round_robin`（默认，按声明顺序轮询）与 `priority`（取第一个可完成一组的候选）。每个转化组只选一个候选，容量不足的候选跳过。
_Avoid_: 效果排序（组合模式决定选哪个结果）

**结算与返还 (Settlement & Return)**:
规则命中后整堆源物品转入结算库存，按组支付成本并交付产物；已开始组不重放、不补做，失败只记录真实成功量。未开始部分对应的源物品返还，找不到落点时保存为待返还记录，重启且目标维度加载后交付。
_Avoid_: 事务回滚（结算不做整批回滚）

**新产物状态 (Drop State)**:
实体层的转化状态（`core/state`，不进 `ItemStack`）：新产物默认 2 秒环境伤害保护 + 5 秒转化冷却；返还物在本实体生命周期内永久保护且永久禁转。拾取后重丢得到普通掉落物，不继承任何状态。
_Avoid_: 物品 NBT（状态挂在实体层）

**一次性效果 (One-shot Effect)**:
对同一源最多尝试一次的世界效果（`lightning` / `explosion` / `arrow_rain` / `weather`）：不乘组数、不参与容量计算。
_Avoid_: 无上限效果（容量上限与尝试次数是两个概念）

**转化锁 (Conversion Lock)**:
一个掉落物同一时刻只允许一次转化流程在跑；进入执行前加锁，结束后释放，避免同一实体被重复转化。
_Avoid_: 互斥锁（口语可接受）

**隐式消耗 (Implicit Consumption)**:
规则未声明任何 `consume_*` 时，每组先隐式消耗 1 个源物品，按整堆展开组数；显式消耗按固定成本统一支付。源快照在消耗前保存，后续效果使用它。
_Avoid_: 默认消耗（口语可接受）

**结果上限 (Limit)**:
产物类效果自身的参数：半径内已存在同类产物的最大数量。它是**效果参数**，不是规则级字段。
_Avoid_: 堆叠上限、结果倍率

**搜索半径 (Radius)**:
产物类效果自身参数：`limit` 的统计半径 / 放置半径（按类型语义）。不同效果的默认值与区间见配置参考。
_Avoid_: 检测距离

## 已退役 / 旧链路术语

以下术语仅用于指代旧链路遗留代码，**新代码与文档不应再使用**：

| 退役术语 | 现状 |
|---|---|
| 条件组 (ConditionGroup) | 类已删除；组合语义改由 `all_of` / `any_of` 节点表达 |
| 条件表达式（二维数组 / DNF） | 旧的"外层 OR、内层 AND"形状已取消；改为任意嵌套的 `ConditionNode` 树，旧格式解码即报错（零兼容） |
| 叶级 negated / 叶级取反 | 字段与常量用途已移除；取反只能用 `inverted` 节点，叶内出现 `negated` 解码报错 |
| 转化类型 (ConversionType) | 后端注册单元已取消，前端改称「模板」 |
| 转化类型定义 (ConversionTypeDefinition) | 旧客户端编辑 UI 的绑定对象；新客户端走 `client/edit` 的 `RuleDraft` |
| 转化规则 (ConversionRule) / 已编译规则 (`CompiledConversionRule`) | 由「规则 (Rule)」+「规则索引」取代 |
| 消耗指令 (Consumption Directive) | 拆为消耗效果（真消耗）与 `*_present` 条件（只检查） |
| 结果倍率 (Result Multiple) | 落成对应效果的 `count` |
| 编辑会话锁 (Edit Session Lock) | 由**全局目标级**「独占编辑会话」取代（早期曾短暂改为 per-player 会话 + 版本戳，已废弃） |
| 视图模型 (RuleView / EffectView / ConditionView / SourceView) | 实现已删除；客户端改为 `RuleDraft`（JsonObject 事实来源）+ 描述符驱动表单 |
| 声明式参数规格 (ParamSpec / RuleFormBuilder / FormRenderer) | 已删除；改为「类型编辑器描述符」+ `EditorField` |
| 转换指令 (`/idtw config convert`) | 命令与 `RuleConvertService` 已删除（2026-10-03 随 P8 结论退役）；旧 v1.2.1 配置不再加载，改用 `/idtw config edit` 重建，见[破坏性更新说明](docs/guide/update-notes.md)。注意：`schema_version` 已于第二轮**重新引入**为现行规则字段（见上文「规则 JSON 顶层形态」） |
| 字段中心 schema (`ConfigFieldSchema`) | 由「类型编辑器描述符」取代 |

## 扩展点一览

| 想扩展 | 入口 | 文档 |
|---|---|---|
| 自定义条件类型 | 实现 `ConditionType` + 注册 `RuleTypeProvider`（`META-INF/services`） | [custom-types.md](docs/guide/custom-types.md) |
| 自定义效果类型 | 同上，`RuleTypeProvider.registerEffects(...)` | [custom-types.md](docs/guide/custom-types.md) |
| 自定义类型的编辑表单 | `ConditionEditorRegistry.register(...)` / `EffectEditorRegistry.register(...)` | [client-editor-spi.md](docs/guide/client-editor-spi.md) |
| 新增回执/校验消息码 | 枚举 + `en_us.json` / `zh_cn.json` 同步加键 | [message-codes.md](docs/guide/message-codes.md) |
| 类型显示名与字段标签 | `TypeLabels` 键约定 + 语言文件 | [client-editor-spi.md](docs/guide/client-editor-spi.md) |
