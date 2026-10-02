# ItemDespawnToWhat 配置领域

> 当前状态（2026-10-02）：后端切换、旧链路删除；GUI 是占位页，前端模型与表单术语只用于下一轮设计。当前执行与保存契约见 [ADR-0017](docs/adr/0017-backend-cutover-and-budgeted-effects.md)。

掉落物在自然消失前，按数据包 / config 覆盖层中的规则转化为其他内容（物品、实体、方块、经验、世界效果等）。本词汇表覆盖新链路（`core/**`）的配置与运行时词汇；已退役的历史术语标注为「旧链路」。

## 语言

**转化 (Conversion)**:
即将消失的掉落物在过期前转变为别的东西这一行为本身。
_Avoid_: 变换、转换

**模板 (Template)**:
下一轮 GUI 的候选入口卡片，是**原"转化类型"退役后的 UI 概念**：一个模板对应一种效果类型，点击模板 = 新建一条只含该效果的规则。它不再是注册单元，也不进配置文件。
_Avoid_: 转化类型（后端已退役）、config category

**规则 (Rule)**:
后端**唯一的配置单元**：`id` + 源匹配 + 条件表达式 + 有序效果列表（外加 `enabled` / `priority` / `notes` / `trigger_after_seconds`）。同一掉落物只会执行**优先级最高的一条**命中规则，"一次多效果"由规则内的效果列表表达。
_Avoid_: 转化规则（旧链路叫法）、配置项（口语可接受）

**规则 id (Rule Id)**:
规则的稳定标识（`ResourceLocation`，如 `mypack:stone_to_diamond`），是被覆盖、被删除、被日志与命令引用的依据；文件里可省略，由文件路径推导。
_Avoid_: 内部 id、UUID

**源匹配 (Source Matcher)**:
规则中的 `source`：`items`（物品 id 或 `#tag` 列表）与 `exclude`（排除列表，优先于匹配）。不做组件级匹配。
_Avoid_: 过滤器、条件（条件另有所指）

**效果 (Effect)**:
规则被触发后要执行的一个动作，位于规则的 `effects` 有序列表中：`type` + 类型专属参数 + 通用字段 `delay_ticks` / `chance` / `conditions`。命中规则内的效果**按顺序启动、分批完成**，不做事务回滚；单个效果抛异常只记录 ERROR 并继续后续效果。
_Avoid_: 结果（旧链路叫法）、动作（口语可接受）

**效果类型 (EffectType)**:
一种已注册的效果类别，自带参数 Codec、参数校验器与服务端执行器（`{ id, codec, validator, executor }`）。内置 12 个：9 个生效效果 + 3 个消耗效果。
_Avoid_: 转化类型、效果种类

**消耗效果 (Consumption Effect)**:
`consume_source` / `consume_catalyst` / `consume_fluid` 三个效果类型：消耗是**效果**而不是条件的副作用。规则未声明任何 `consume_*` 时每轮隐式消耗 1 个源物品，按整堆展开轮次。
_Avoid_: 消耗指令（旧链路叫法）

**条件 (Condition)**:
条件表达式中的原子谓词（一个条件类型 + 参数，可叶级取反），只负责判定"是否满足"，**不承担消耗**。
_Avoid_: 限制（"限制"另指效果的 `limit`）

**条件叶 (Condition Leaf)**:
条件组中的原子谓词。**JSON 形状已扁平化**：旧链路的 `{ type, params: {...}, negated }` 变为 `{ type, <参数直接平铺>, negated }`，不再有 `params` 子对象。
_Avoid_: 条件项、条件实例

**条件表达式 (Condition Expression)**:
规则（或单个效果）上的触发谓词，采用析取范式（DNF）：**二维数组**——外层是条件组的析取（OR），内层是条件叶的合取（AND）。空数组 = 恒真；出现空条件组视为非法。
_Avoid_: 条件树（本项目采用 DNF 而非任意嵌套树）

**条件组 (Condition Group)**:
条件表达式中的一个合取子句：组内若干条件叶全部为真则该组为真。组按顺序求值，首个为真的组即匹配组（求值短路）。
_Avoid_: 场景（口语可接受）

**条件类型 (ConditionType)**:
一种已注册的条件类别，自带参数 Codec、参数校验器与服务端求值器（`{ id, codec, validator, evaluator }`）。内置 10 个：dimension / biome / weather / outdoor / surrounding_blocks / catalyst_present / fluid_present / time_of_day / y_level / light_level。
_Avoid_: condition kind、条件种类

**求值器 (Evaluator)**:
条件类型的服务端实现，必须是**纯谓词**：只读取 `ConditionContext`，不得修改世界；返回未取反的原始结果。
_Avoid_: 检查器、checker（旧链路叫法）

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
一条规则最终生效的来源描述（所属层 + 数据包 id + 文件路径），用于报错定位与 `config list` 展示。
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
加载/校验/转换过程中产生的一条问题记录（ERROR / WARN + 信息 + 来源标识 + 字段路径），统一进 `IssueCollector`，供日志、命令与编辑界面回显。
_Avoid_: 错误日志（Issue 是结构化记录，不等于日志行）

## 事件与编辑协议

**快照 (Snapshot)**:
服务端下发给客户端的"当前生效规则全集"：条目形状 `{ rule, origin, editable }`。覆盖层规则用**文件原始 JSON**（不丢未识别字段）且 `editable=true`；内置/世界数据包规则由模型编码、只读。
_Avoid_: 配置同步包（口语可接受）

**变更集 (Change Set)**:
客户端提交的批量修改：`expected_version` + 若干 `RuleEdit`（`upsert` 携带完整规则 JSON / `delete` 只带 id）。服务端**整批接受或整批拒绝**，单条失败不半途落盘。
_Avoid_: 补丁、diff（口语可接受）

**版本戳 (Version Stamp)**:
覆盖层的整数版本号，持久化在覆盖层根目录的 `.edit_version`。客户端保存时携带自己视图的版本；服务端版本不一致即**拒绝整批并回传最新快照**（乐观并发），避免覆盖他人改动。
_Avoid_: 乐观锁、修订号（口语可接受）

**编辑会话 (Edit Session)**:
per-player 的编辑会话（空闲 5 分钟超时），取代旧链路的"全局单 UUID 锁"：一个玩家编配置不再锁死其他玩家，并发保护由**版本戳**承担。
_Avoid_: 编辑锁（旧链路叫法）

**权威落盘 (Authoritative Write)**:
保存的写回依据是**磁盘上的文件内容**，按 id 逐条应用增删改（原子写 + `.bak` 备份），而不是用运行时快照整文件覆盖——因此 disabled 规则、未涉及的规则、非法 JSON 文件都不会被保存动作删除。
_Avoid_: 保存配置（口语可接受）

**视图模型 (View Model)**:
下一轮客户端封装协议 JSON 的模型层。当前 RuleView、EffectView、ConditionView、SourceView 等实现已删除，占位屏不编辑规则；后续前端应只读写协议视图，不引用 core/model 服务端模型。
_Avoid_: 客户端 DTO（旧链路叫法）、渲染模型

**模板目录 (Template Catalog)**:
下一轮 GUI 第一屏的模板清单（9 个生效效果各一个），顺序与旧第一屏一致。
_Avoid_: 类型列表

**声明式参数规格 (Param Spec)**:
前端重构可采用的表单设计：字段名、i18n key、控件种类、默认值与必填标记。当前 ParamSpec、RuleFormBuilder、FormRenderer 已删除，不作为现存 API。
_Avoid_: schema 表单（旧链路叫法）

## 核心玩法（运行时）

**转化锁 (Conversion Lock)**:
一个掉落物同一时刻只允许一次转化流程在跑；进入执行前加锁，结束后释放，避免同一实体被重复转化。
_Avoid_: 互斥锁（口语可接受）

**隐式消耗 (Implicit Consumption)**:
规则未声明任何 consume_* 时，每轮先隐式消耗 1 个源物品，按整堆展开轮次；显式消耗按效果顺序启动。源快照在消费前保存，后续效果使用它。
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
| 转化类型 (ConversionType) | 后端注册单元已取消，前端改称「模板」 |
| 转化类型定义 (ConversionTypeDefinition) | 旧客户端编辑 UI 的绑定对象；新 GUI 走视图模型层 |
| 转化规则 (ConversionRule) / 已编译规则 (`CompiledConversionRule`) | 由「规则 (Rule)」+「规则索引」取代 |
| 消耗指令 (Consumption Directive) | 拆为消耗效果（真消耗）与 `*_present` 条件（只检查） |
| 结果倍率 (Result Multiple) | 落成对应效果的 `count` |
| 编辑会话锁 (Edit Session Lock) | 由 per-player「编辑会话」+「版本戳」取代 |
| schema 版本 (schema_version) | 删除；旧配置改用 `/idtw config convert` 一次性显式转换 |
| 字段中心 schema (`ConfigFieldSchema`) | 由「声明式参数规格」取代 |
