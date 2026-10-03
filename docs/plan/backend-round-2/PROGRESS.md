# 第二轮后端改造进度记录

> 本文件是阶段 0–7 的持续进度载体。状态区分三档，不得混写：
> **实现完成**（代码已写完）/ **工程验证通过**（IDEA MCP + Gradle build 通过）/ **用户游戏验收通过**（用户实机确认）。

## 0. 目标与基线

- 目标：按 `docs/plan/backend-round-2/PLAN.md` 完成阶段 0–7，实现玩法扩展（销毁转化、候选组合、固定成本与返还结算）、架构调整（规则契约、所有权与账目）与性能优化（公共共享 tick 预算），通过工程验证后交用户实机验收。
- 工作目录：`D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult`
- 分支：`1.21.1`；起始 HEAD：`2096019`（docs：同步文档）
- 本轮起始时 Git 工作区仅有未跟踪目录 `docs/plan/backend-round-2/`（规划与 ADR），无代码改动。
- 规划依据：`docs/plan/backend-round-2/PLAN.md`、`docs/plan/backend-round-2/CONTEXT.md`、`docs/plan/backend-round-2/docs/adr/0001-conversion-commitment.md`、`docs/plan/backend-round-2/docs/adr/0002-shared-server-budget.md`。
- 游戏内验收矩阵：`PLAN.md:304-336`（25 行场景/预期），由用户实机执行，不新增 test 文件。

## 1. 阶段状态总表

| 阶段 | 内容 | 实现 | 工程验证 | 用户验收 |
| --- | --- | --- | --- | --- |
| 0 | 基线、API 与入口核实 | **完成**（`notes/stage0-platform.md` 231 行、`notes/stage0-runtime.md` 446 行、`notes/stage0-domain.md` 314 行；决策 D1–D16 已裁决） | 不适用（只读调研）；起始点构建基线已记录 | 不适用 |
| 1 | 领域模型、规则契约与 GUI API | **完成**（31 文件 +517/-79，rev4） | **通过**：`tools/dsh-build.ps1 -Tasks "build"` exit=0 / BUILD SUCCESSFUL in 30s / 30 tasks: 20 executed（IDEA MCP 不可用，见 3.3） | 未开始 |
| 2 | 公共调度器基础 | **完成**（`core/runtime/scheduler/` 16 新文件；改 12 删 1，含平台推进点） | **通过**：三次串行构建均 exit=0（点 1 28s / 点 2 28s / 点 3 28s，覆盖迁移与平台侧） | 未开始 |
| 3 | 环境销毁入口与掉落物状态 | **完成**（task-6：新增 5 文件 + 修改 13 文件，rev3） | **通过**：两次串行构建均 exit=0（点 1 28s / 点 2 27s，覆盖两端 Mixin 与打包）；IDEA MCP 不可用（见 3.3） | 未开始（按 D21 待阶段 5 后统一验收） |
| 4 | 预检查、结果选择与成本结算 | **完成**（task-7：新增 5 文件 + 修改 21 文件，rev3） | **通过**：构建点 3 exit=0 / `BUILD SUCCESSFUL in 30s`（17 executed）；修正后构建点 4 exit=0 / `BUILD SUCCESSFUL in 28s`（14 executed）；IDEA MCP 不可用（见 3.3） | 未开始（按 D21 与阶段 5 一起） |
| 5 | 安全生成、方块放置与返还位置 | **完成**（task-8：新增 1 文件 + 修改 8 文件，rev3；`ReturnItemSpawner.java` 220 行） | **通过**：构建点 5 `exit=0` / `BUILD SUCCESSFUL in 30s`（17 executed）；IDEA MCP 不可用（见 3.3） | 未开始（按 D21 待阶段 6 后统一交用户） |
| 6 | 正常中断、持久返还与恢复 | **完成**（task-9：新增 `SettlementRecovery.java` 141 行 + 修改 4 文件；含阶段 4 遗留任务的收尾） | **通过**：构建点 7 `exit=0` / `BUILD SUCCESSFUL in 29s`（17 executed）；IDEA MCP 不可用（见 3.3） | 未开始（按 D21 待阶段 7 后统一交用户） |
| 7 | 削峰调优与整体验证 | **完成**（task-10 completed：新增 `DebugDistribution.java` + 修改 8 文件；交付面=观测补齐 + 判据表 + 用户实机清单，本环境无游戏、无法产出实测数字） | **通过**：构建点 8 `exit=0` / `BUILD SUCCESSFUL in 29s`（17 executed，观测 6 文件）、构建点 9 `exit=0` / `BUILD SUCCESSFUL in 29s`（17 executed，配置键 3 文件）、最终验收构建点 10 `exit=0` / `BUILD SUCCESSFUL in 8s`（1 executed / 29 up-to-date）；另全仓资源 21 个 JSON 解析通过；IDEA MCP 不可用（见 3.3） | **未开始**（需用户实机执行 `PLAN.md:304-336`；本阶段不得预先宣称毫秒收益） |
| IDE 静态检查 | IDEA MCP `lint_files` 覆盖本轮 57 个改动 `.java` | **完成**（修复 4 条 ERROR 级 Mixin 注入描述符 + 6 条参数名 weak warning，见 §20） | **通过**：修复后 lint 复跑 **0 ERROR**（92 WARNING / 4 WEAK WARNING 已逐条分级为误报/预留 API/死代码/风格）；构建点 19 `exit=0` / `BUILD SUCCESSFUL in 37s`（10 executed） | 不适用 |
| 补漏 | 规则级催化剂固定成本（PLAN 2.3 / 4.3 / 第 6 阶段） | **完成**（task-12 契约形状 completed rev4，7 文件；task-13 执行/预留/记账 completed rev4，新增 `CatalystReservations.java` + 改 4 文件；D31–D33 见 §4.10/§4.11） | **通过**：构建点 11–16 全绿（11 30s/20 executed、12 29s/17、13 29s/17、14 29s/14、15 28s/14、16 28s/14）；21 个 JSON 全部 parse 通过；IDEA MCP 不可用（见 3.3） | 未开始（随阶段 0–7 一并交用户） |

## 2. 团队与分工（agent team）

| 成员 | 负责 | 写入范围（advisory） |
| --- | --- | --- |
| lead | PROGRESS.md、决策裁决、跨阶段整合审查、构建与最终验证 | `docs/plan/backend-round-2/PROGRESS.md` |
| platform-scout | task-1 阶段0-A（完成）；阶段 3 平台入口与掉落物实体状态（完成，task-6）；阶段 5 安全生成/方块放置/返还位置（完成，task-8）；现待命 | `core/state/**`、`platform/**`、`fabric/**`、`neoforge/**`、阶段 5 的 `core/type/effect/exec/**` |
| runtime-scheduler | task-2 阶段0-B（完成）；阶段 2 共享预算调度器（完成，task-5）；阶段 4 完整组结算与真实账目（完成，task-7）；task-11 结构版本稳定化（完成，构建点 6 green）；阶段 6 中断与持久返还（完成，task-9，构建点 7 green）；**阶段 7 削峰调优与观测收敛（完成，task-10，构建点 8/9 green）**；现待命 | `core/runtime/**`、`core/config/**`、`core/debug/**` |
| domain-contract | task-3 阶段0-C（完成）；**阶段 1 规则契约与数据包（完成，task-4）**；后续按需承接契约类改动 | `core/model/**`、`core/type/**`、`core/service/**`、数据包 |

约束：构建（`tools/dsh-build.ps1`）串行互斥，任何时刻只允许一个成员启动构建；阶段 0 不构建（起始点基线构建除外，已完成）。

## 3. 阶段 0 记录

### 3.1 调研任务与产出

| 任务 | owner | 状态 | 产出 |
| --- | --- | --- | --- |
| task-1 阶段0-A 平台销毁入口与实体状态 | platform-scout | completed（rev3） | `docs/plan/backend-round-2/notes/stage0-platform.md`（231 行） |
| task-2 阶段0-B 性能基线与调度 | runtime-scheduler | completed（rev3） | `docs/plan/backend-round-2/notes/stage0-runtime.md`（446 行） |
| task-3 阶段0-C 规则契约现状 | domain-contract | completed（rev3） | `docs/plan/backend-round-2/notes/stage0-domain.md`（314 行） |

### 3.2 lead 已核实的现状（代码事实）

- `core/runtime/ConversionRuntime.java`（490 行）：维度级 `LevelState` 各持两个独立 `TickScheduler`（检查/效果），预算不跨维度共享（`ConversionRuntime.java:54-65`、`:139-144`）。
- `performConversion` 现状：`rounds = perRound>0 ? max(1, available/perRound) : 1`、`covered = min(rounds*perRound, available)`，产出不足不回减消耗、无真实成功量回执；`item.addTag(Constants.CONVERTED_TAG)` 为永久 converted 标记。
- `core/runtime/TickScheduler.java`（97 行）：`SOFT_BUDGET_NANOS = 2_000_000`（硬编码）、`TickScheduler(int maxTasksPerTick)`、`TreeMap` 到期桶 + `ready` 队列；`runDue` 内层搬运（`:59-61`）无 deadline/工作量检查，存在预算外全量 drain。
- `core/api/EffectExecutor.java`：`void execute(P effect, EffectContext context)`，无完成量/失败/中断回执（阶段 4 扩展）。
- `core/api/EffectContext.java`（50 行）：`level/source/sourceStack/position/random/ruleId/rounds/coveredSourceItems/schedule` 现状；无候选、组编号、冻结条件字段。
- `core/config/ServerConfig.java`（115 行）：`check_interval_ticks`(20)、`backoff_max_ticks`(100)、`max_checks_per_tick`(512，语义为每维度每队列)、`overlay_directory`、`fabric_lifespan_fallback_ticks`(6000)、`debug_logging`；无任何预算/平滑窗口字段。
- `core/model/Rule.java`（74 行）：`id, enabled, priority, displayName, notes, source, conditions, triggerAfterSeconds, effects`；无消失方式集合、无候选层级、无固定成本字段（阶段 1 改造面）。
- `core/runtime/LoadedChunks.java`：`contains` / `containsArea` 无副作用查询，返还与放置搜索复用。
- 平台文件清单：`fabric/` 13 个 Java（`mixin/ItemEntityMixin.java` 19 行、`mixin/EntityMixin.java` 23 行、`runtime/RuleRuntimeHost.java` 331 行）；`neoforge/` 9 个 Java（`runtime/RuleRuntimeHost.java` 343 行、`runtime/RuleRuntimeEvents.java` 122 行）。
- 全仓无岩浆/仙人掌/火焰销毁检测代码；现有销毁/到期入口仅 `onItemRemoved`（`ConversionRuntime.java:465`）与 `deferNaturalExpiry`（`:474`）。

### 3.3 验证工具状态（实际检查结果）

- **IDEA MCP 当前不可用**：本会话对 `mcp__srv-e3b0c44298fc1c14__get_file_problems` / `lint_files` / `get_project_modules` 的调用全部返回 `Error POSTing to endpoint: Streamable HTTP session not found`（重试 4 次后放弃）。端口 64342 处于 LISTEN（idea64 PID 33376），但 DSH 侧 MCP 会话缺失。结论：**不能声称已通过 IDEA MCP 检查**；需用户侧重连。
- 构建脚本 `tools/dsh-build.ps1`（24 行）：命名互斥 `Global\idtw_dsh_gradle_build`，串行执行 `gradlew.bat <tasks> --console=plain`，默认 900 秒等待，参数 `-Tasks`/`-TimeoutSeconds`。
- 起始点构建基线（lead 实测）：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` 未改动任何源码时返回 `exit=0`、`BUILD SUCCESSFUL in 9s`、`30 actionable tasks: 1 executed, 29 up-to-date`。说明工具链可用、起始代码可编译，后续构建失败可归因于本轮改动。
- 备用 API 查询通道 `mc-developing-mcp` 可用；lead 已用它复核 vanilla `ItemEntity.hurt` 致死分支，与 task-1 笔记一致（4 道提前 return；`health<=0` 后 `getItem().onDestroyed(this)` → `discard()`）。

## 4. 已确定的接口与决策（lead 裁决）

### 4.1 术语基线（与 `CONTEXT.md` 对齐，代码标识符须对应）

- 销毁转化 / 即时转化 / 消失方式 / 环境保护 / 转化冷却 / 返还掉落物 / 转化组 / 已开始组 / 候选结果 / 组合模式 / 一次性效果 / 转化禁用 / 安全生成位置 / 起点填充。
- 代码命名对应：`DropState`（实体状态）、`TriggerKind`（消失方式）、`Candidate`（候选结果）、`CombinationMode`（组合模式）。

### 4.2 伤害分类决策（D1）

- 火 = `DamageTypes.ON_FIRE` + `DamageTypes.IN_FIRE`；岩浆 = `DamageTypes.LAVA`；仙人掌 = `DamageTypes.CACTUS`。判定顺序：**先 LAVA，再火，再 CACTUS**。
- **不使用** `DamageTypeTags.IS_FIRE`（含火球/爆炸火球/营地火/热地板，属错误归因）。
- **不把** `CAMPFIRE` / `HOT_FLOOR` 归为火：PLAN 4.4 要求不把未知伤害自动归火，验收矩阵用火方块点燃/岩浆/仙人掌即可覆盖。集合以 common 内单一常量表定义；若用户要扩展，仅改该处并补验收。
- 归因只看最终致死伤害（`hurt` 内 `health<=0` 分支），不在 `hurt` HEAD 用“是否接触危险源”判断。

### 4.3 实体状态决策（D2–D8）

- **D2 保护粒度**：恒为「三类伤害全防」，不存掩码；状态内预留 `v` 版本字段以便迁移。
- **D3 合并兼容口径**：以永久标志一致性判定。双方永久保护、永久禁转标志完全一致 → 兼容，允许原版合并，且合并后临时保护到期刻与临时冷却到期刻各自取 `max(dest, origin)`（0 视为未设置）；任一永久标志不一致 → 不兼容，注入 `tryToMerge(Lnet/minecraft/world/entity/item/ItemEntity;)V` HEAD cancellable 禁止合并（临时状态并集注入 4 参 `merge(...)V` TAIL）。理由：验收矩阵要求「不污染其它物品状态」，永久状态无法用取大/取小合并。
- **D4 拾取后重丢**：状态挂在实体层，拾取即实体消亡 → 临时保护、临时冷却、永久保护、永久禁转全部不再继承，重丢为普通掉落物；物品栈不写任何状态字段。
- **D5 状态归属**：common 定义状态 record 与存取接口；`platform/services/IPlatformHelper.java` 增加默认抛 `UnsupportedOperationException` 的方法，两平台实现（NeoForge 用 `Entity#getPersistentData()`；Fabric 用 Data Attachment `AttachmentRegistry.createPersistent`）。属规划范围内改动。
- **D6 NeoForge 启用 Mixin：批准**。理由：PLAN 4.4 要求两平台各自最小 Mixin 接 `ItemEntity.hurt` 致死分支（NeoForge 无等价事件），合并禁止与临时状态并集也需 Mixin。阶段 3 需新增 `neoforge` 的 mixins.json 与元数据/构建声明，并以构建通过为验收；描述符疑点（`onDestroyed` 的 INVOKE owner）以 Mixin 应用/编译报错为准校正。
- **D7 状态键与版本**：Attachment id / NBT 键 `itemdespawntowhat:state`，内部 `v:1`。
- **D8 永久保护与自然消失**：**不豁免**。返还物在保护状态下仍按原版时间自然消失且不触发转化，`deferNaturalExpiry` 不为永久保护加例外。
- 计时基准：`ServerLevel#getGameTime()`（持久于 level.dat `Time`；卸载不暂停、停服不推进、重启恢复）。

### 4.4 调度与预算决策（D9–D12，依据 task-2 笔记）

- **D9 语义变更**：`max_checks_per_tick` 由「每维度每队列」改为「每 tick 全局工作量上限」；新增配置键 `server_budget_us`(2000)、`max_work_units_per_tick`(512)、`effects_work_units_per_tick`(64)、`check_spread_window_ticks`(20)、`dispatch_batch_size`(64)；旧键保留兼容读取作为新全局上限的缺省回退，变更写入交付说明。
- **D10 平滑窗口采用方案 B**：保留原始 `dueTick`，用 `dispatch_batch_size` 分片搬运铺开工作量，不改写到期时间（符合 ADR-0002「到期任务保留原始时间」）。
- **D11 默认值**：采用 `notes/stage0-runtime.md` §4.2 候选表（2ms / 512 / 64 / 20 / 64 / 1），标注为「代码推导候选，阶段 7 依实测收敛」，不承诺毫秒收益。
- **D12 阶段 2 接口基线**：新包 `core/runtime/scheduler/`（`ServerScheduler`/`ServerTickBudget`/`ServerTask`/`StepResult`/`RealmQueues`/`TaskDrainer`/`ScheduledTask`/`LegacyTaskAdapter` 等）按 task-2 §5.3 草案实施；本阶段**不改** `core/api/EffectContext` 与 `core/api/EffectExecutor` 签名。推进入口迁到两端已有的 `ServerTickEvent.Post` / `END_SERVER_TICK`。验收按 `PLAN.md:260` 四条。
- 规则重载只清 `scheduler` 未清 `effects`（`ConversionRuntime.java:94-106,147-162`）属既有不一致，阶段 2 引入 `CancelReason.RULE_RELOAD` 时一并修正。

### 4.5 阶段 1 契约决策（D13–D16，依据 `notes/stage0-domain.md`）

- **D13 字段归属**：`triggers`、`source_cost`、`catalyst_cost`、`combination`、`outcomes`、`schema_version` 一律为**规则级**；`safe_spawn`（默认 `false`）与 `fill_origin`（默认 `true`）为**候选级**（`outcomes[i]` 内）。理由：安全生成/起点填充描述的是「该候选产物如何落位」，绑定候选才能让同一规则的不同候选各自决定；规则级将来若确有必要可追加（additive，不破坏候选级）。此归属只影响 JSON 位置，不改变 PLAN 的阶段 5 行为验收。
- **D14 固定成本与效果式消耗并存口径**：规则级 `source_cost`/`catalyst_cost` 是本轮唯一「固定成本」表述；效果式 `consume_source`/`consume_catalyst` 保留解析与执行兼容（旧数据包/用户覆盖层仍可用，按 `BuiltinTypeRegistries.perRoundSourceConsumption` 现有语义推算）。同一规则**同时**声明规则级成本与同类效果式消耗 → 拒绝并给出明确文案（避免双账目）。规则级成本字段结构上不可能携带 `chance`/`conditions`/`delay_ticks`；效果式消耗保持现状字段集（不新增拒绝条件，避免破坏既有数据包）。
- **D15 `outcomes` 缺失的兼容**：**不报错**，按「现有 `effects` 即唯一候选」隐式映射（候选 id 固定为 `default`），并产生 WARN 级 issue 说明已按旧形状解释。理由：用户可能已有自制覆盖层规则，阶段 1 直接拒绝会破坏既有存档；PLAN 验收「候选结构完整往返」以显式声明 `outcomes` 的规则为准。`outcomes` 与顶层 `effects` **同时**声明 → 拒绝（形状歧义）。
- **D16 未知/未接线字段策略**：**先接线再登记**。只有在 `codec()` 中真正解码的键才写入 `KNOWN_RULE_FIELDS`；未接线的键维持现状只 WARN，绝不「先登记白名单后接线」。理由：登记后 codec 未接字段的行为（DFU 是否报错）在本环境无法用 IDEA MCP 实测，采用不依赖该未知行为的实现策略即可免除实测依赖。阶段 1 实现完成后由 lead 用一次构建 + 静态复核确认。
- **D17 `schema_version`**：缺省 `1`；`>1` 或非整数 → 拒绝「不支持的结构版本: N」。本轮只支持 `1`。
- 阶段 1 附带要求（来自笔记 4.4）：`client/edit/BuiltinEditorDefaults.ruleBody` 的新建规则默认体必须补新字段；`client/ui/screen/RuleEditorScreen.localIssues` 补本地拦截（源成本 >0、候选非空、候选 id 唯一）；`data/` 与 `assets/` 下 8+8 个 JSON 必须保持逐字节同源（现为 SHA256 相同的副本）。
- 阶段 1 不改执行账目语义（阶段 4 范围）；`core/runtime/ConversionRuntime.java` 只按 `triggers`/`outcomes`/`source_cost` 读取，行为契约不变。

### 4.6 阶段 3 决策（D18–D21，2026-10-03 裁决）

- **D18 保护/冷却的施加点**：采用「显式 API 调用」，禁止在 `onItemAdded`/实体加入世界处做宽泛启发式。`core/state` 提供 `DropStateStore.grantNewProduct(ItemEntity)`（临时保护 + 临时冷却）与 `grantPermanentReturn(ItemEntity)`（永久保护 + 永久禁转）；阶段 3 即把 `grantNewProduct` 接到本轮转化会产生掉落物的效果执行点（`SpawnItemExecutor` 及同类 `spawn_*` 掉落物效果），非转化来源掉落物绝不授予。理由：环境致死路径在阶段 3 已能触发转化并产出掉落物，产物必须先带保护/冷却才符合 `PLAN.md:81-94`；宽泛启发式会污染无关掉落物。**不给源实体写冷却**（源实体随即被原版 `discard()`，写入无意义）。
- **D19 致死请求后的源实体生命周期**：接受「环境致死请求转化后源实体被原版 `discard()`」为既定事实。允许效果在实体移除后按位置/维度继续执行；**禁止**通过源实体再入世界（返还交付必须用触发时快照 `sourceStack` 新建掉落物，阶段 4/5 落地）。效果执行不得把「`source()` 已移除」记为失败；若某效果确实需要活实体，须在回报中逐条列出。
- **D20 阶段 3 写入面**：`core/state/**`（新建：`DropState` / `DropStateStore` / `DamageClassification`）、`core/type/effect/**` 中掉落物生成点的 `grantNewProduct` 调用、`core/runtime/**` 仅 DropState 读取与保护判定调用点（不得动 `core/runtime/scheduler/**`）、`core/config/ServerConfig.java`、`platform/**`、`fabric/**`、`neoforge/**`。仍禁改 `core/model/**`、`core/api/**`、`core/service/**`、数据包与 `lang`。
- **D21 验收可得性口径**：阶段 3 用户暂时无法完整验收「产品保护计时」（需阶段 5 的产物/返还路径齐备）。阶段 3 必须以代码级证据保证五点：保护判定发生在原版扣血前、仅三类伤害生效、合并兼容口径、拾取重丢不继承、自然过期不被保护阻止；阶段 5 再补游戏内验收，阶段 3 的状态表不得写「用户验收通过」。
- 复用既有 `core/model/TriggerKind.java`（NATURAL/FIRE/LAVA/CACTUS + CODEC），不新建同类枚举。

### 4.7 阶段 5 决策（D22–D25，2026-10-03 裁决）

- **D22 `EffectContext` 追加默认方法（保留）**：`safeSpawn()` 默认 `false`（是否启用有界最近安全搜索）、`fillOrigin()` 默认 `true`（起点填充是否含触发位置本身）、`positionSearchChecksPerTick()` 默认 16，语义写作「**单次推进的候选位置检查上限，仅用于位置搜索类执行器**」。理由：配置化分步额度优于硬编码常量，且三者均为追加默认方法，不破坏阶段 4 已冻结的签名与语义（阶段 4 已固定的 `rounds()`/`coveredSourceItems()`/`reportProgress` 未动）。
- **D23 返还位置排除危险方块**：`ReturnItemSpawner.isFree` 拒绝脚部/头部为火、岩浆、仙人掌（含岩浆流体）的位置；但**必须以 stage2「限高以上」兜底**，绝不因找不到安全点而放弃交付。理由：返还物虽永久免三类伤害，落进危险方块仍属异常行为；兜底保证账目守恒（返还只延迟，不丢失）。
- **D24 两处搜索口径**：`safe_spawn` 与返还位置搜索**各自独立**（前者是「实体安全生成」、后者是「掉落物交付」），但两处排序必须是同一条公式 `dx²+dz²+4·dy² → dy → x → z`；stage2 的 y 必须取 `level.getMaxBuildHeight()`，不得写死常量（下界 128 / 主世界 320 不同）。
- **D25 越权写入备案（责任在 lead）**：为贯通 `position_search_checks_per_tick`，lead 直接改了 `core/runtime/scheduler/SchedulerConfig.java`（记录头第 5 组件、`Math.max(1, …)` clamp、`from(ServerConfig)` 末参）与 `core/debug/DebugLog.java:52`。根因：该键由 platform-scout 加在 `ServerConfig`，而 `core/runtime/scheduler/**` 属其禁改区，无人补位，导致 `:common:compileJava` 报 `RuntimeEffectContext.java:96: 找不到符号 方法 positionSearchChecksPerTick() 位置: 类 SchedulerConfig`、`BUILD FAILED in 8s`。教训：跨层配置键必须在任务里明确「谁负责贯穿到使用点」，不能靠禁改区边界自动闭合。

### 4.8 阶段 6 决策（D26–D28，2026-10-03 裁决）

- **D26 中断只落记录、不做世界写入**：`ConversionSettlement.onCancelled(reason)` 的处理顺序固定为 `record.syncPendingDelivery(held.getCount())` → `record.interrupt(reason)` → `ledger.put(record)`（SavedData 标脏），**然后**才由 scheduler 释放队列（`ConversionRuntime.java:114` RULE_RELOAD / `:162` rescan / `:182` DIMENSION_UNLOAD / `:191` SERVER_STOP / `:501`/`:514` ENTITY_REMOVED）。`onCancelled` 必须幂等：同一记录被重复取消不得重复记账或二次扣减待交付量。理由：停机/卸载瞬间的 `addFreshEntity` 没有后续 tick 与区块写入保障，易产生「生成了但没保存」→ 下次启动重复返还。
- **D27 停机与维度卸载走「下次启动交付」（平台选项 A）**：停服/卸载时只保存结算记录，未开始组的返还库存在服务端重新 tick 且**目标维度已加载**后由新建的 `core/runtime/SettlementRecovery.java`（REBATE 车道 ServerTask）交付。三条硬要求：①恢复交付必须走公共预算 `budget.charge` 且受 `position_search_checks_per_tick` 约束、失败沿用 `retry(1,20)`，不得在启动阶段一次性倾泻；②目标维度未加载的记录保持不动（低频告警计数，不每 tick 刷屏）②语义差异必须在注释与交付说明中写明「**停机时的未开始源库存于下次启动交付**」，不得对外声称「停服瞬间已返还」。对 PLAN.md §6 矩阵 L332「延迟效果尚未开始时停服/卸载 → 记录并返还未开始库存」的实现口径即此。
- **D28 交付只认 `needsRecovery()`**：`SettlementRecord.needsRecovery()` = `status != COMPLETED && pendingDelivery() > 0 && 源栈非空`，且该判定必须在 Tag 往返（`save`/`load`）后由加载出的字段重新计算，不得依赖运行时缓存。`pendingUnits`（及新增的 `pendingUnitsTotal()` 调试计数）**只用于观测，绝不参与交付量计算**——`PlaceBlockExecutor` 的 `deferred(planned)` 在 limit 封顶时会长期留下 `pendingUnits > 0`，若误用会导致多返还。`SettlementRecord` 新增的位置字段与 `needsRecovery` 相关字段必须全部进 `save`/`load`。

### 4.9 阶段 7 决策（D29–D30，2026-10-03 裁决）

- **D29 观测补齐口径**：同意 runtime-scheduler 检查点①的落点 —— `KindStats` 扩 `done`/`yielded`/`retried`/`work_units`/`last_micros` 且保旧名（`requeued = yielded + retried` 语义不变）；启用原空置 counters[2] 作 YIELD 计数；`SchedulerStats` 加 `last_max_ready_delay_ticks`/`last_drained_tasks`/`drained_ticks`/`max_drained_in_tick`；`DebugPerformanceWindow` 增加预算使用率与就绪延迟分位数、`exhaustion_reason` 占比、`kinds.*` 跨维度段、`cancelled_by_reason`、积压清空与平滑分散字段；`ConversionRuntime` 只加只读 `queueStats(ServerLevel, ServerTaskKind)` 重载。硬条件：①纯观测，不得改变 takeDue/countDue/每步 ≥1 工作单位等既有语义；②`DebugPerformanceWindow` 是**单 ServerLevel 绑定**窗口，凡跨维度汇总字段必须在键名或注释上标明级别，回报需给「字段名 → server/level」对照；③`MAX_SAMPLES = 12_000` 与 nearest-rank 分位数算法（`DebugMeasurements.java:29-45`）不变，不引入新库。**明确不做**：位置搜索「候选遍历 vs 选择决策」两级耗时（需改阶段 5 冻结的 `ReturnItemSpawner`，改用 lane 步骤耗时 + 每份返还 charge(2) 差分近似，须标注「近似口径，非直接测量」）、(维度×kind) 二维分位数（样本量不足）。
- **D30 场景准备预算配置化**：`core/debug/DebugScenarioRun.java` 的 `2_000_000L`（2ms）与每 tick 128 源两个字面量改为配置键 `debug_scenario_prepare_budget_us`（默认 2000）与 `debug_scenario_prepare_batch_size`（默认 128），落 `core/config/ServerConfig.java` 并在 `DebugLog.config` 输出；硬条件 = 默认值必须与现行为逐位等价、只影响场景准备、不参与 `server_budget_us`（该字面量与调度预算无关、不污染测量窗口，此结论采信检查点⑤的事实说明）。**口径更正**：`max_work_units_per_step` **不是**配置键，而是 `StepResult` 紧凑构造里的实现硬化（`workUnits = max(1, …)`）；配置判据表按「6 个真实配置键 + 1 条实现硬化」书写。

### 4.10 阶段 7 后补漏决策（D31：规则级催化剂固定成本，2026-10-03 裁决）

- **发现的缺口（证据，lead 全仓核实）**：`catalyst_cost` 在阶段 1 被实现为规则级**整数**（`core/model/Rule.java:33` `@Nullable Integer catalystCost`、`core/model/RuleCodecs.java:151-152` `Codec.INT.optionalFieldOf`、`core/model/RuleValidation.java:65-66` 只校负、`core/api/RuleFields.java:24`、客户端 `client/ui/screen/RuleEditorScreen.java:1521-1523` 与 `:1547-1549`），全仓 `catalystCost()` 读取点只有 codec / 校验 / 客户端；`core/runtime/**` 与 `core/type/**` 内**没有任何 catalyst 引用**（`grep catalyst` 在 `core/runtime` 命中 0）→ 声明 `catalyst_cost` 的规则会「零催化剂消耗照常产出」，违反 PLAN.md:48「催化剂必须足量支付，不能少扣却照常产出」、PLAN.md:52「催化剂不足…在开始该组之前处理」、PLAN.md:184/186 催化剂预留与释放、PLAN.md:290「释放未消费的催化剂 / 容量预留」。阶段 0 领域笔记本就建议对象形状（`notes/stage0-domain.md:253`：`{items:[TaggedId], count:int}`），阶段 1 定稿时简化为整数，导致该固定成本在运行时**不可执行**。
- **裁决 D31**：
  1. 形状：`catalyst_cost` 由整数改为对象 `{"items":[TaggedId...],"count":int,"radius":int?}`，字段名与取值域与 `consume_catalyst` 效果一致（`items` 必填非空、物品 id 或 `#tag` 混合；`count` 必填 1..64；`radius` 可选默认 1、范围 1..8）。整数写法不再接受，解码报错并给出明确文案（不做静默兼容；内置数据包与 8 个模板均未使用该字段，无数据迁移）。
  1. 语义：规则级固定成本，与 `source_cost` 同层；结构上不可能携带 `chance`/`conditions`/`delay_ticks`（沿用未知字段检查拒绝）；与 `consume_catalyst` 效果同时声明仍按 D14 拒绝。
  1. 计划期（PLAN.md:52）：可支付组数上界 = `min(floor(源 N / c), 候选容量上界, floor(可用催化剂件数 / count))`；催化剂可用量按触发位置 `radius` 盒内统计（复用 `EffectTargets.blockBox`，排除源实体，物品/标签命中口径与 `ConsumeCatalystExecutor.matchesAny` 一致），由近及远排序保证与遍历顺序无关。
  1. 预留（PLAN.md:184/186）：同维度维护「已预留未支付」催化剂实体 UUID→件数表，防止多个结算任务重复使用同一批催化剂；组开始时把该组预留转为已支付（真实扣减 `ItemStack.shrink` + 空栈 `discard()`），未开始部分在取消 / 中断时**释放预留**，不扣件数。
  1. 记账：`SettlementRecord` 记录每组催化剂成本与已消耗催化剂总数，字段进 `save`/`load`；**守恒公式仍只针对源物品**（`N = C + 已交付返还 + 尚待交付返还`），催化剂单独计数，不破坏阶段 4 已验收的守恒语义。
  1. 效果式 `consume_catalyst` 保持现状（D14 兼容既有数据包），不纳入固定成本预留；其「不足时不补偿」行为沿用现状并在 §18.4 记为已知限制。

### 4.11 D32–D33（催化剂执行侧，task-13 方案回报后裁决）

- **D32 口径单一真源（可见性例外）**：批准 runtime-scheduler 的选项 B —— `core/type/effect/exec/EffectTargets.java` 的 `blockBox(...)` 改为 `public static`，并在同一文件新增 `public static boolean matchesAny(List<TaggedId> items, ItemStack stack)` 承载物品/标签匹配；`core/type/effect/exec/ConsumeCatalystExecutor.java` 的私有 `matchesAny` 改为委托调用（删除复刻体）。仅这两个文件、仅可见性与委托改动，`core/type/**` 其它内容仍冻结。理由：选项 A（在 `core/runtime` 逐字复刻 AABB 与标签匹配）会让「同口径」变成两份会漂移的代码，而 D31 的催化剂可用量统计与效果式消耗必须同口径，漂移会造成账目不可解释。改动后 `core/type` 属本轮新增越权面，记入 §18 与最终交付说明。
- **D33 支付不足的原子口径**：`payGroup` 必须**先校验后扣减**：按预留的实体 UUID 重读件数求和，每个实体读取计 `budget.charge(1)`；①预算耗尽 → 未扣任何件数，返回「需重试」（dispatch 走 `yield(1,1)`，下刻重试，**不得半支付**）；②总可用件数 < 本组需 `count` → 不扣任何件数，返回「不足」；③足量 → 逐实体 `shrink` + 空栈 `discard`，返回实付件数。dispatch 的 `!groupOpen` 分支顺序固定为：**先 payGroup（校验+支付）→ 成功后**再 `held.setCount(-cost)` → `record.groupStarted(cost)` → `record.catalystGroupPaid(n)` → `ledger.put(record)`（先支付再写盘，延续既有理由）。payGroup 报「不足」时：不开该组（不扣源成本、不派发效果），把 `groupCount` 截断为 `groupsStarted`、`releaseOwner` 释放剩余预留、发 `CATALYST_SHORT`、剩余源库存走正常返还路径。理由：PLAN.md:48「不能少扣却照常产出」。
- **D32 备注（第二处越权面备案）**：`core/model/RuleValidation.java` 新增 `import com.meteorite.itemdespawntowhat.core.type.RefChecks`（全仓 `core/model → core/type` 唯一一处；RefChecks 自身只依赖 `core/api`），用于让催化剂 `items` 的引用存在性校验与 `consume_catalyst` 效果侧完全同口径（非标签未注册 ERROR、标签缺失 WARN）。lead 裁决保留：另写一份 containsKey 检查会制造第二份会漂移的口径。
- codec 区间校验（`Codec.intRange(1,64)` / `(1,8)`）保留，不降级为 `Codec.INT`：越界在解码期即拒绝，`RuleValidation.ParamChecks.inRange` 继续覆盖程序化构造路径，两者不冲突。
- 预留时机确认：选定候选且 `groupCount` 定稿的同一 tick 内、`record.plan` 之前 `reserve(...)`，期间无 yield 点，因此不存在半预留。
- 预留表的运行期属性（停服即失，不补偿）由 §18.4 记录；三条释放路径（组开始支付、取消/中断、恢复任务取消）与 `ConversionRuntime.shutdown()` 兜底顺序（`scheduler.clear(SERVER_STOP)` 先触发 `onCancelled` 释放，再 `reservations.clear()`）已由 runtime-scheduler 回报确认，lead 采纳。

## 5. 阻塞与待办

- 阻塞（工具）：IDEA MCP `Streamable HTTP session not found`，影响验收顺序第 1 步（先检查 git 工作区错误/警告）。等待用户处理；期间以串行 Gradle 构建输出 + JSON 校验为工程证据，并在交付说明中如实标注。
- 代码任务：阶段 0（笔记）→1（task-4）→2（task-5）→3（task-6）→4（task-7 + task-11）→5（task-8）→6（task-9）→7（task-10）均已 `completed`，代码与配置改动全部落盘且每次改动后都有绿构建记录（构建点 1–10）。**阶段 7 后新增补漏任务**：task-12（契约形状）/ task-13（执行侧），见 §18。
- **补漏缺口已修复（lead 复核发现，非用户报告）**：规则级 `catalyst_cost` 只解析与校验、运行时零消费者 → 声明它的规则「零催化剂消耗照常产出」，违反 PLAN.md:48 / :52 / :184 / :290。修复为 D31（§4.10）+ §18。**这是当前唯一未完成的代码工作**，完成后才恢复「阶段 0–7 + 补漏全部实现完成」的口径。
- **唯一未完成项 = 用户实机验收**：`PLAN.md:304-336` 的 36 行验收矩阵 + 阶段 7 的性能对照（§17.7 的 8 步执行清单 + 阶段 0 基线命令 `/idtw debug bench baseline 60` / `convert 10000 60` / `normal 10000 10` / `retry 10000 60`）。本环境无游戏，无法代替执行。
- **补漏档（task-12/13）已完成并关闭**：契约形状改正（D31）+ 执行侧容量上界/预留/支付/截断/三条释放路径/异常闭合（D33 + 检查点⑤），构建点 11–16 全绿、21 个 JSON 校验通过。当前**不再有任何未完成的代码工作**，唯一未完成项就是用户实机验收。
- 阶段 7 的性能结论全部为「待实测」：7 个配置键的默认值本轮**未改**，收敛方向见 §17.4；执行饥饿（搬运优先消耗预算）是第一待实测风险（§17.5）。
- 阶段 6 的崩溃窗口（`addFreshEntity` 成功后、`ledger.put` 之前崩溃 → 最坏重复 1 批返还，≤ `dispatch_batch_size` 个堆叠）为有意取舍，已记入 §16.5；若用户要求零重复需引入在途状态机并在恢复时反查世界，成本较高。
- 旧存档（阶段 4/5 期间产生的结算记录）没有位置字段，恢复时退回该维度全局出生点；仅影响开发期记录的返还落点，不影响账目。
- 旧 GUI（客户端编辑器）本轮未扩展新字段的编辑能力（本轮范围只做数据包/JSON 配置）：旧 GUI 不会静默抹掉新字段（阶段 1 已验证并加本地拦截），但也不提供新字段的可视化编辑；客户端 GUI 留到下一轮。

## 6. 下一步动作

1. 阶段 0–7 + 补漏档（§18）的代码与文档交付已全部完成，工程验证通过（构建点 1–16 全绿 + 21 个 JSON 解析通过）；**下一步就是用户实机验收**。
2. 交用户执行：`PLAN.md:304-336` 验收矩阵（行为类，逐行对照）+ §17.7 的 8 步执行清单（性能类）。建议顺序：先 1–3 步建立基线，再跑行为矩阵，最后 4–8 步（多维度、环境集中销毁、返还与恢复、中断）。
3. 回收用户实测数据后由 lead 收敛 §17.4 的配置默认值（只按数据调整，不预先宣称毫秒收益），必要时开新任务修正并复跑用例。
4. 客户端 GUI（新字段可视化编辑）与「阶段 7 未做的 4 项观测」为已知的下一轮候选，本轮不做。
5. 每轮改动仍由 lead 串行执行一次 `tools/dsh-build.ps1 -Tasks "build"`；构建互斥，任何时刻只允许一个成员启动。

## 7. 阶段 1 契约改动面（lead 预扫，供 task-4 使用）

服务端读写面：
- `core/model/RuleCodecs.java`（编解码）、`core/model/Rule.java`（记录定义）
- `core/load/RuleFileParser.java`（数据包/文件解析）
- `core/service/RuleOverlayWriter.java`（覆盖写入 = 旧 GUI 写回路径，**丢字段风险点**）
- `core/service/RuleSubmissionValidator.java`（提交校验）
- `core/service/RuleSnapshotAssembler.java` + `core/network/protocol/RuleSnapshotEntry.java` / `RuleSnapshot.java` / `RuleCatalog.java` / `RuleEdit.java` / `RuleEditChangeSet.java`（快照与编辑协议）

客户端草稿面（位于 common 的 `client/`，**服务端代码不得引用**）：
- `client/edit/RuleDraft.java`（JSON 相关命中 41 处，草稿模型）、`client/edit/draft/PersistedDraft.java`、`client/edit/BuiltinEditorDefaults.java`、`client/edit/EditorChangeSet.java`
- `client/ui/screen/RuleEditorScreen.java`、`client/ui/screen/RuleEditorModel.java`、`client/ui/screen/form/NaturalSummary.java`、`RuleTemplates.java`、`FormControl.java`

结论：阶段 1 只要保证「解析→内存→写回」链路对新增字段无损（尤其 `RuleOverlayWriter` 与 `RuleDraft`/`PersistedDraft`），即可满足 PLAN.md:250「旧 GUI 不能无声抹掉新字段」。

## 8. 阶段 1 记录（rev4，实现完成 + 工程验证通过）

### 8.1 交付面（31 文件 +517/-79）

- 新增：`core/model/TriggerKind.java`（NATURAL/FIRE/LAVA/CACTUS + CODEC，未知值报错）、`core/model/CombinationMode.java`（ROUND_ROBIN/PRIORITY）、`core/model/OutcomeCandidate.java`（id/effects/safe_spawn/fill_origin，IMPLICIT_ID=`default`，SAFE_SPAWN=false，FILL_ORIGIN=true）。
- 修改：`core/model/Rule.java`（15 组件 + `effectiveTriggers()`/`effectiveOutcomes()`/`allEffects()`/`declaresConsumption()`）、`core/api/RuleFields.java`（新增 6 个顶层键 + 候选级 4 键，旧常量未改名）、`core/model/RuleCodecs.java`（15 字段 group、`DEFAULT_SCHEMA_VERSION=1`、`OUTCOME_FIELDS` 白名单 + `checkOutcomeFields`；`effects` 由必填改可选；`conditionExpressionCodec`/`effectCodec` 签名未动）、`core/model/RuleValidation.java`（新校验，见 8.2）、`core/model/EffectType.java`（`default oneShot()`）、`core/model/SimpleEffectType.java`（第 5 组件 + 4 参兼容构造）、`core/service/BuiltinTypeRegistries.java`、`core/service/RuleReferenceValidator.java`、`client/edit/BuiltinEditorDefaults.java`、`client/ui/screen/RuleEditorScreen.java`（本地拦截 +99 行）、`WeatherEffect`/`LightningEffect`/`ExplosionEffect`（`oneShot=true`）。
- 数据包 8+8：`data/itemdespawntowhat/idtw/rules/*` 与 `assets/itemdespawntowhat/idtw/templates/*` 逐对 SHA256 相同（8/8）；每条新增 `triggers:["natural"]`、`combination:"round_robin"`、`schema_version:1`；仅「完全未声明任何 consume_*」的 6 条加 `source_cost:1`（`builtin_multi_effect`/`builtin_catalyst_and_fluid`/`builtin_thunder_condensation` 不加，避免双重记账）。lang 双语各 +7 键（现各 639 行）。
- 写回口径：未声明即不写（DFU `optionalFieldOf` 的默认值 encode 时省略）；候选效果路径统一 `outcomes[i].effects[j]`。

### 8.2 校验点（阶段 1 验收对照）

- `RuleValidation.java:62-63` `source_cost` 必须为正数（0/负数 → error）；`:65-66` `catalyst_cost` 不得为负。
- `RuleValidation.java:68-73` 规则级成本与同名 consume_* 效果互斥（避免重复记账）。
- `RuleValidation.java:45-55` `effects`/`outcomes` 至少一个且不得同时声明；仅 `outcomes` 时 WARN「当前版本不会执行」。
- `RuleValidation.java:57-60` `schema_version != 1` → error；`:220-238` 候选 id 非空唯一、候选 effects 非空、候选数 ≤ `MAX_EFFECTS`（32）。
- 客户端本地拦截 `RuleEditorScreen.localIssues`（`:1518-1523`、`:1544-1549`）同步拦 `source_cost<=0` 与重复记账。

### 8.3 工程验证

- `powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → `exit=0`、`BUILD SUCCESSFUL in 30s`、`30 actionable tasks: 20 executed, 10 up-to-date`，:common/:fabric/:neoforge 编译、javadoc、jar 全通过。
- IDEA MCP 仍不可用（见 3.3），本轮**未**经过 IDE 检查；构建是本阶段唯一的工程验证证据（如实标注）。

### 8.4 阶段 1 已知限制与裁决（Q1/Q2）

- **Q1（裁决：留阶段 4）**：`core/runtime/ConversionRuntime.java:361-378` 未改。`perRoundSourceConsumption(rule)` 已支持 `source_cost`（`BuiltinTypeRegistries.java:36-38`），但真实扣费仍走 `implicitSourceConsumption()`（count 恒 1，`ConversionRuntime.java:374`）→ 声明 `source_cost>1` 且不声明 `consume_source` 的规则会出现「按每轮 >1 估算、实际只扣 1」。理由：PLAN 把成本结算归阶段 4（L270-276），阶段 1 只建立可校验契约；内置包只用 `source_cost:1` 回避。**阶段 4 必须修正隐式消耗读取 `source_cost`。**
- **Q2（裁决：留阶段 4）**：`Rule.isRunnable()`（`Rule.java:64-66`）仍要求顶层 `effects` 非空，`RuleIndex.java:55` 是唯一使用点 → 仅声明 `outcomes` 的规则当前不会触发（`RuleValidation:53-55` 已 WARN，非静默）。阶段 4 候选执行落地后改为按 `effectiveOutcomes()` 判定；内置包因此保持 `effects` 形态。
- 阶段 1 未改 `core/runtime/**`、`fabric/**`、`neoforge/**`；`RuleSubmissionValidator.java` 未改（新校验挂在 `RuleCodecs.decoder` 与 `RuleValidation` 上，保存链路自动覆盖）。

## 9. 阶段 3 预扫（lead 只读核实）

- 平台实体状态接口：`common/src/main/java/com/meteorite/itemdespawntowhat/platform/services/IPlatformHelper.java`（27 行）现有 `getPlatformName/isModLoaded/isDevelopmentEnvironment/getConfigDir/getEnvironmentName`；新增能力按既有 `sendToPlayer` 的 `default` + `throw new UnsupportedOperationException`（第 24-26 行先例）模式，由两平台 override。实现类：`fabric/src/main/java/com/meteorite/itemdespawntowhat/platform/FabricPlatformHelper.java`、`neoforge/src/main/java/com/meteorite/itemdespawntowhat/platform/NeoForgePlatformHelper.java`。
- Fabric Mixin 实际路径/包名为 `fabric/src/main/java/com/meteorite/itemdespawntowhat/mixin/`（`ItemEntityMixin.java` 19 行、`EntityMixin.java`、`PlayerMixin.java`），配置文件 `fabric/src/main/resources/itemdespawntowhat.mixins.json`（`required:true`、package `com.meteorite.itemdespawntowhat.mixin`、`compatibilityLevel JAVA_21`、`injectors.defaultRequire=1`）。**新增 Fabric Mixin 必须同时登记进该 json。**
- NeoForge Mixin 现状：`neoforge/src/main/java/...` 下没有 mixin 包，`neoforge/src/main/resources/META-INF/neoforge.mods.toml`（27 行）也无任何 mixin 声明 → D6 的新增项确认成立。构建插件为 `net.neoforged.moddev`（`neoforge/build.gradle:3`）；NeoForge 1.21 声明 Mixin 配置的形式（`neoforge.mods.toml` 的 `[[mixins]] config=...`）**须由阶段 3 以构建结果验证，不得只凭记忆**。`fabric/build.gradle:20-22` 侧有 `mixin { defaultRefmapName }`；NeoForge 端通常不需要 refmap。
- 构建体系：`buildSrc/src/main/groovy/multiloader-loader.gradle` 把 `:common` 的 Java/资源注入各 loader 模块（`compileJava` 追加 `commonJava` 源码、`processResources` 复制 `commonResources`），因此 common 新类自动参与两端编译；**common 的公共签名改动同时影响 fabric+neoforge**，风险最高。

## 10. 阶段 3/4 交接关键结论

- D1 伤害分类所用常量：`DamageTypes.IN_FIRE`、`DamageTypes.ON_FIRE`、`DamageTypes.LAVA`、`DamageTypes.CACTUS`；火 = `{IN_FIRE, ON_FIRE}`，禁用 `is_fire` 标签（含 lava/hot_floor/campfire）。判定顺序先 LAVA 再火再 CACTUS。
- Fabric 致死入口：`@Inject(method="hurt", at=@At(value="INVOKE", target="Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;)V"))`；保护判定在 `hurt` 方法头（HEAD，`cancellable=true` 且 `setReturnValue(false)`）。
- NeoForge：自然消失继续用 `ItemExpireEvent`、保护用 `EntityInvulnerabilityCheckEvent`（阶段 0 已确认无 `ItemDestroyedEvent`），致死分支仍需 Mixin。

## 11. 阶段 2 记录：公共共享 tick 预算调度器

### 11.1 交付面

- 新增 `common/src/main/java/com/meteorite/itemdespawntowhat/core/runtime/scheduler/`（16 文件）：`ServerScheduler.java`（322 行）、`TaskLane.java`（195 行）、`ServerTickBudget.java`（119 行）、`RealmQueues.java`（103 行）、`ScheduledTask.java`（102 行）、`SchedulerConfig.java`（33 行）、`ServerTask/ServerTaskKind/StepOutcome/StepResult/CancelReason/SchedulerStats/KindStats/QueueStats/ServerTickBudgetSnapshot/RunnableTask`。
- 修改 12 文件：`core/runtime/ConversionRuntime.java`（onLevelTick→onServerTick、单实例、cancel 带原因）、`core/runtime/RuntimeEffectContext.java`（效果走公共 EFFECT 种类 + realmKey，构造 +2 参数，**`core/api/EffectContext` 接口未动**）、`core/config/ServerConfig.java`（+42/-4，5 个预算键）、`core/debug/{DebugScenarioManager,DebugScenarioRun,DebugPerformanceWindow,DebugLog}.java`、`fabric/src/main/java/com/meteorite/itemdespawntowhat/runtime/{RuleRuntimeEvents,RuleRuntimeHost}.java`、`neoforge/**` 同名两文件。
- 删除 `core/runtime/TickScheduler.java`（-97 行）。累计工作区 diff（含阶段 1；相对 HEAD）：`43 files changed, 788 insertions(+), 260 deletions(-)`。

### 11.2 四条验收的代码级证据（PLAN.md:260）

- **多维度共用一个预算**：`ConversionRuntime.java:60` 全服务器唯一 `ServerScheduler`；`:93` 构造；`LevelState`（`:65-69`）已无任何独立预算，realm 仅作 lane 分组（`:255`）；每 tick 只推一次 `ConversionRuntime.java:153-154` → `ServerScheduler.java:88-93`（`gameTime<=lastGameTime` 去重 + `budget.begin` 一次）。平台各自只剩一处推进：`fabric/.../RuleRuntimeEvents.java:64`、`neoforge/.../RuleRuntimeEvents.java:92`（按维度的 `LevelTickEvent`/`END_WORLD_TICK` 已删）。
- **同 tick 大量到期不做预算外全量处理**：`ServerScheduler.java:109-112`（每 tick 每 lane 只统计一次额度）、`:125` `takeDue(...,allowance)`、`:147-153` `allowance=min(dispatch_batch_size, ceil(countDue(≤batch×window)/window))`；`TaskLane.java:58-84` 一次只搬 1 条并在 `:81` `chargeDrain()` 记 1 工作单位，`:59` 达额度立即停。执行双上限 `ServerTickBudget.java:24-29`（时间+工作量）、`:81-91`（`exhausted`），每访问一条 lane 前检查 `ServerScheduler.java:117`。旧的无约束搬运动作随 `TickScheduler.java` 删除，全仓仅剩 `RunnableTask.java:5`、`QueueStats.java:5` 两条注释提及旧名。
- **延后任务不丢**：`TaskLane.java:182-194` `clear(reason)` 逐条 `handle.cancel(reason)`；`ScheduledTask.java:52` terminal、`:57` cancel 幂等。生命周期清理全部带原因：`ConversionRuntime.java:114`（规则重载）、`:162`（rescan）、`:182`（DIMENSION_UNLOAD）、`:191`（SERVER_STOP）、`:501`/`:514`（ENTITY_REMOVED），场景停止为 SCENARIO_STOP；预算耗尽只是 `return`，任务留在队列（`ServerScheduler.java:117`），`SchedulerStats.dropped` 恒 0。
- **延迟不提前、推进不重复**：`ConversionRuntime.java:262-267` `scheduleCheck` 用 `level.getGameTime()+delay` 得绝对 `dueTick` → `scheduleAt`；`TaskLane.java:64` `entry.getKey() > gameTime` 直接返回，窗口只分片搬运、从不改写已有句柄的 `dueTick`（D10 方案 B 落地）；`TaskLane.java:99-110` `pollReady` 出队即 `markRunning`，只有 YIELD/RETRY 且非 terminal 才 requeue。

### 11.3 观测能力（新增）

- `DebugPerformanceWindow.java:76` 起含 `max_oldest_ready_delay_ticks` 与 scheduler 段：预算使用率均值/峰值、`ticks_exhausted_by_time` / `by_work_units`、搬运量、steps/released/failed/drained/cancelled/dropped、`cancelled_by_reason`、per_kind、`last_backlog_clear_ticks`（跨维度合并差值视图）。
- `DebugScenarioRun.java:382` 输出 `scheduler_pending_after_cleanup`，FRAME 新增 12 个预算/计数字段，`:418-419` 保留「输出刻−转化刻 ≥100」的延迟断言。`DebugLog.java:47` 输出 `server_budget_us`，`:53` 保留旧名 `queue_soft_budget_us` 取同值（仅字段兼容）。

### 11.4 lead 复核结论与遗留观测项

- 结论：符合 ADR-0002 与 D9–D12；2ms 硬编码随 `TickScheduler` 删除，预算唯一来源 `ServerConfig.java:83` `server_budget_us`（默认 2000）→ `SchedulerConfig.java:24` → `ServerScheduler.java:93`。`DebugScenarioRun.java:161` 另有一处 `2_000_000` 字面量，属「场景准备批处理截止」，与调度预算无关（是否配置化留阶段 7 决定）。
- 遗留观测项（阶段 7 必须复测，不在阶段 2 结论里预判）：①搬运每条扣 1 工作单位并计入总预算，深积压时先耗预算搬运、执行被推后，需观测是否造成执行饥饿；②YIELD/RETRY 最短延后 1 tick（`ServerScheduler.java:187`）；③`countDue` 统计上限 `batch×window`（默认 1280）且每 tick 每 lane 只统计一次，**统计本身不计费**，属潜在峰值转移点；④`KindStats` 的 `counters[2]` 目前未被使用（无功能影响）。
- 工程验证：三次串行构建全部 `exit=0`（点 1 覆盖新包+ServerConfig，点 2 覆盖迁移与平台推进点，点 3 覆盖 `drainAllowance` 热路径修正），均 `BUILD SUCCESSFUL in 28s`。IDEA MCP 仍不可用，未经 IDE 检查。
- 用户实机验收：**未开始**（需 bench：多维度预算合并、10000 同龄到期的清空时长、延迟/重载断言、积压清空时间收敛）。
## 12. 阶段 3 记录：四类触发、保护与冷却、合并兼容

### 12.1 交付面（task-6，owner platform-scout）

- 新增 5：`common/src/main/java/com/meteorite/itemdespawntowhat/core/state/DropState.java`、`core/state/DamageClassification.java`、`core/state/DropStateStore.java`、`neoforge/src/main/java/com/meteorite/itemdespawntowhat/mixin/ItemEntityMixin.java`、`neoforge/src/main/resources/itemdespawntowhat.mixins.json`。
- 修改 12：`common/.../platform/services/IPlatformHelper.java`、`common/.../core/config/ServerConfig.java`、`common/.../core/type/effect/exec/{EffectTargets,SpawnItemExecutor,LootTableExecutor}.java`、`common/.../core/runtime/ConversionRuntime.java`、`fabric/.../platform/FabricPlatformHelper.java`、`fabric/.../mixin/ItemEntityMixin.java`、`fabric/.../runtime/RuleRuntimeHost.java`、`neoforge/.../platform/NeoForgePlatformHelper.java`、`neoforge/.../runtime/{RuleRuntimeHost,RuleRuntimeEvents}.java`、`neoforge/src/main/resources/META-INF/neoforge.mods.toml`。
- 未触碰：`core/model/**`、`core/api/**`、`core/service/**`、`core/runtime/scheduler/**`、数据包与 lang（阶段 1 冻结契约保持）。

### 12.2 D21 五点代码级证据（lead 已逐一读码复核）

- ①保护在原版扣血前：`fabric/.../mixin/ItemEntityMixin.java:32-41`（`hurt` HEAD、`cancellable=true`、命中三类伤害时 `cir.setReturnValue(false)`）；`neoforge/.../runtime/RuleRuntimeEvents.java:94-103`（`EntityInvulnerabilityCheckEvent` → `setInvulnerable(true)`，带 `instanceof ItemEntity` 守卫避免污染其它实体）。阶段 0 已确认两端 `ItemEntity` 均未重写 `isInvulnerableTo`，故事件路径对掉落物有效。
- ②仅三类伤害：`core/state/DamageClassification.java:20/22/24` = FIRE `{IN_FIRE, ON_FIRE}` / LAVA / CACTUS，判定顺序 LAVA→火→CACTUS，明确不用 `is_fire` 标签；消费点在 `core/state/DropStateStore.java:63-68`。
- ③合并兼容口径 D3：`fabric/.../mixin/ItemEntityMixin.java:54-60`（`tryToMerge` HEAD cancellable，`permanentFlagsMatch` 不一致即 `ci.cancel()`）、`:63-68`（4 参 `merge` TAIL `unionTemporary`：写目标、清来源）；`neoforge/.../mixin/ItemEntityMixin.java:33-39`/`:42-47` 同构。
- ④拾取重丢不继承：状态只挂实体层（Fabric `AttachmentRegistry.createPersistent`；NeoForge `Entity#getPersistentData` 键 `itemdespawntowhat:state`），`ItemStack` 无任何状态字段；`DropState` 全仓 71 处引用只落在 `core/state`、产物 exec 入口、`ConversionRuntime`、`platform`、两端 Host/Events/Mixin，未触及 `PlayerMixin`/`EntityMixin`/`spawnAtLocation`。
- ⑤自然过期不被保护阻止：Fabric `ItemEntityMixin.java:24-29` 的 `tick` 内自然到期 `@Redirect`（ordinal=1 → `deferNaturalExpiry`）与 NeoForge `ItemExpireEvent`（`RuleRuntimeEvents.java:79-87`）逻辑未改；保护只挂在 `hurt` / `isInvulnerableTo` 路径。

### 12.3 触发路径接线

- 环境致死：Fabric 用 `hurt` 内 `@Inject(INVOKE, ItemStack.onDestroyed(ItemEntity)V)`（1 参）、NeoForge 用 2 参 `onDestroyed(ItemEntity, DamageSource)` INVOKE；两端描述符均以 `javap` 逐字节核对（NEO 偏移 110 后接 114 `discard`，VAN 偏移 109 后接 113 `discard`）。
- `ConversionRuntime` 新增 `requestEnvironmentalConversion(level, item, source)` → `DamageClassification.classify` → `requestConversion(level, item, kind)`：`excluded` 过滤、`tracked` 缺失时补 `onItemAdded`、`tracked.locked` 去重、DebugMode 下 observe `ENV_DESTROY_REQUEST`、再 `attempt(level, uuid, kind)`。
- `attempt` 门禁：`permanentConversionBan` → 移除追踪（永久禁转）；`cooldownRemaining>0` → 重新入队到冷却结束（转化冷却）；`select(..., TriggerKind)` 按 `rule.effectiveTriggers().contains(kind)` 过滤，年龄门槛只在 `NATURAL` 生效。
- 产物保护授予：`core/type/effect/exec/EffectTargets.java` 的 `addConversionProduct` 由 `SpawnItemExecutor`/`LootTableExecutor` 调用；环境致死请求不禁止效果继续（效果按位置/维度执行，`base.schedule` 排队）。
- `ServerConfig` 新键：`new_product_protection_seconds`（0..3600，默认 2）、`conversion_cooldown_seconds`（0..3600，默认 5），派生 `newProductProtectionTicks()`/`conversionCooldownTicks()`。

### 12.4 工程验证

- 构建点 1（新包 + 两个平台接线 + ServerConfig）：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → exit=0 / `BUILD SUCCESSFUL in 28s` / 30 actionable tasks: 18 executed, 12 up-to-date；`:common`/`:fabric`/`:neoforge` 的 compileJava + javadoc + jar 全通过。
- 打包复核：`neoforge/build/libs/itemdespawntowhat-neoforge-1.21.1-1.2.1.jar` 内含 `itemdespawntowhat.mixins.json` 与 `com/meteorite/itemdespawntowhat/mixin/ItemEntityMixin.class`；jar 内 `META-INF/neoforge.mods.toml` 的 `${mod_id}` 已展开为 `config="itemdespawntowhat.mixins.json"`；无 refmap 属 mojmap 环境正常（Mixin 运行期是否真正应用只能游戏内验证）。
- 构建点 2（裁决 a 的 `SpawnEntityExecutor` 接线路 + `removeAttached` 显式移除 + 两处同步执行注释）：`tools/dsh-build.ps1 -Tasks "build"` → exit=0 / `BUILD SUCCESSFUL in 27s` / 30 actionable tasks: 14 executed, 16 up-to-date。
- IDEA MCP 仍不可用（Streamable HTTP session not found），本阶段未经 IDE 检查。

### 12.5 稳健性与已知限制

- 环境致死路径里「条件检查 + 规则选择」在 `hurt` 调用栈内同步执行（单实体一次）；效果一律经 `ServerScheduler` 排队并在预算内执行（`ConversionRuntime.java:474-479`）。环境集中销毁时峰值是否转移到这里，留阶段 7 观测。
- 阶段 3 无法完整游戏内验收保护/冷却计时与返还物行为（需阶段 5 的产物与返还生成面），状态表不写用户验收通过（D21）。
- NeoForge Mixin 为首次启用：构建与打包通过，运行期注入结果未验证。

## 13. 阶段 4 记录：完整组结算与真实账目（task-7，owner runtime-scheduler）

### 13.1 交付面

- 新增 5：`core/api/EffectResult.java`；`core/runtime/SettlementRecord.java`（213 行）、`SettlementLedger.java`（127 行）、`RoundRobinCursors.java`（90 行）、`ConversionSettlement.java`（459 行）。
- 修改：`core/api/EffectContext.java`、`EffectExecutor.java`；`core/runtime/RuntimeEffectContext.java`、`ConversionRuntime.java`；`core/model/Rule.java`（仅 `isRunnable()` 单点）；`core/type/effect/ArrowRainEffect.java`；`core/type/effect/exec/` 下 12 个执行器改为返回 `EffectResult`；`WeatherEffect`/`LightningEffect`/`ExplosionEffect` 传 `oneShot=true`；`core/debug/{DebugScenarioManager,DebugScenarioRun,DebugPerformanceWindow,DebugLog}.java`。
- 未触碰：`core/service/**`、`core/state/**`、数据包与 lang、`fabric/**`、`neoforge/**`。

### 13.2 新契约（阶段 5/6/7 需遵守）

- `public record EffectResult(Outcome outcome, int appliedUnits, int pendingUnits, boolean oneShot, String detail)`，`enum Outcome { APPLIED, DEFERRED, SKIPPED, FAILED }`；`deferred(units)` 的语义是 **appliedUnits=0 / pendingUnits=units**（不是「完成 units」），异步批次用 `reportProgress` 收敛：`SettlementRecord.addProgress` = `appliedUnits += done; pendingUnits -= done`（`SettlementRecord.java:83-87`），因此不存在重复计数。
- `EffectContext` 新增 `outcomeId()` / `groupIndex()` / `groupCount()` / `groupSourceCost()` / `reportProgress(int)`；`rounds()` 语义变为「本组轮数」恒为 1；`coveredSourceItems()` = **本组真实扣减的源物品数**（`ConversionSettlement.java:338` 传 `sourceCostPerGroup`，为 0 时取 1；`SpawnXpExecutor.java:26` 的 per_source_item 倍率依赖此语义）。
- 账目守恒（ADR-0001）：`N = consumedSources + pendingDelivery + deliveredReturns`，`consumedSources` 只在已开始的组记账（`SettlementRecord.groupStarted`），返还交付成功后才从 `pendingDelivery` 扣减。

### 13.3 验收证据（PLAN.md:272-276）

- ① 10 源 / c=2 / 候选每组 3 生物 / 余量 4：`ConversionSettlement.groupsFor` = `min(10/2=5, floor(4/3)=1)` = 1 → 扣 cost=2（C=2）、`held` 余 8 → `deliver()` 生成 8 件返还掉落物并 `grantPermanentReturn` → 3 生物 + 8 源，记录 10 = 2 + 8 + 0。
- ② 候选内多产出共同限制：`capacityGroups`（`ConversionSettlement.java:207-240`）对每个 `spawn_item`/`spawn_entity` 取 `room/per` 后 `min`。
- ③ 轮询跨实体接续并持久化：`RoundRobinCursors extends SavedData`（`DATA_ID = "itemdespawntowhat_round_robin"`，键 `ruleId@dimension`，`record Cursor(int structureVersion, int nextIndex)`），`structureVersion` = 候选 id + 效果 hashCode，结构变化即归零；**⚠ 该实现跨 JVM 重启不稳定（枚举组件），见 13.6 末条与 task-11**。
- ④ 概率落空仍付费：`ConversionRuntime.java:494-498` 组成本已在派发时扣除，落空只记 `EffectResult.skipped("chance_missed")`，不返还、不重试。
- ⑤ 一次性效果：`EffectType.oneShot()`（weather/explosion/lightning/arrow_rain）不参与容量计算，`capacityGroups` 末尾 `groups = min(groups, 1)`；仍按配置扣一组源成本。
- 整堆返还：全候选容量不足 → `record.plan("", 0)` + `OUTCOME_SKIPPED` + 全额返还、零成本（`ConversionSettlement.java:180-188`）。

### 13.4 lead 复核与修正（构建点 4 的来源）

- 死代码：`ConversionSettlement.java:148` 原条件含 `&& false`，已清理（现 `:151`），语义 = 无候选 / 无库存 / 单组成本超过库存 → 整堆返还零成本。
- 账本无界增长：`SettlementLedger.prune(gameTime)`（`:70`）与 `unsettledCount()`（`:59`）改前**零调用点**，`MAX_RECORDS=512` / `RETAIN_COMPLETED_TICKS=6000` 形同虚设。现接线：`ConversionRuntime.java:63` `SETTLEMENT_PRUNE_INTERVAL_TICKS = 1200L` + `:196-203` `onServerTick` 每 1200 tick 调 `SettlementLedger.get(server.overworld()).prune(gameTime)`；`prune` 只裁 `COMPLETED` 且 `pendingDelivery<=0` 且已过 6000 tick 的记录，无可裁对象时 `:86` 打 WARN 并保留全部未结清记录（宁可超上限也不丢阶段 6 的恢复依据）。
- 观测：`DebugPerformanceWindow.java:147-150` 新增 `ledger_unsettled`（未结清条数）与 `ledger_records`（总条数）；`DebugLog` 未加同名键。
- 取消守恒算例（N=10、c=2、计划 g=3）：派发 1 组后取消 → 10 = 2 + 8 + 0；若先交付 3 再取消 → 10 = 2 + 5 + 3。取消路径不做世界写入，记录状态 `INTERRUPTED`，留给阶段 6。

### 13.5 工程验证

- 构建点 3（阶段 4 主体）：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → exit=0 / `BUILD SUCCESSFUL in 30s` / 30 actionable tasks: 17 executed, 13 up-to-date。
- 构建点 4（prune 接线 + 死代码清理 + 观测字段）：同命令 → exit=0 / `BUILD SUCCESSFUL in 28s` / 30 actionable tasks: 14 executed, 16 up-to-date。
- 两次均含 `:common`/`:fabric`/`:neoforge` 的 compileJava + javadoc + jar + `:fabric:remapJar`。IDEA MCP 仍不可用（`Streamable HTTP session not found`），本阶段未经 IDE 检查。

### 13.6 已知限制与遗留项

- 跨重启只恢复到计划期快照（`SettlementRecord` 落盘字段），完整恢复与「不重复交付」留阶段 6。
- 每组成本为 0（例如只声明 `consume_fluid`）时 `coveredSourceItems` 取 1，避免 `spawn_xp` 的 per_source_item 倍率归零。
- `spawn_entity` 的 tag 容量按 tag 全体近似统计（执行器按随机解析出的单一类型统计），偏保守。
- 阶段切换 `PLANNING → DISPATCHING → DELIVERING` 各占至少 1 tick，产物比旧实现晚约 1–2 tick（验收只看数量与 age 下限）。
- 容量搜索的世界查询每次计 `CAPACITY_QUERY_UNITS = 2` 预算单位，属阶段 7 的峰值观测点。
- 未结清/中断记录不参与裁剪，极端情况（>512 条未结清）账本会超上限，属有意取舍。
- 用户游戏验收未开始：阶段 4 验收矩阵需实机执行，按 D21 与阶段 5 一起交用户。
- **结构版本跨重启不稳定（lead 核实，修复任务 task-11）**：`ConversionSettlement.java:383-388` `structureVersion(candidates)` = `hash * 31 + entry.id().hashCode()` 与 `hash * 31 + effect.hashCode()`。Effect/条件实现虽全为 `record`（12 个 Effect、11 个 Condition、`ConditionNode` 的 AllOf/AnyOf/Inverted/Leaf 均为 record，见 `core/model/ConditionNode.java:16/26/36/42`），record 的 hashCode 会逐层展开组件 hashCode，而 **`java.lang.Enum.hashCode()` 是 `Object` 身份哈希且为 final**：`WeatherEffect.Mode`/`PlaceBlockEffect.Shape`/`ArrowRainEffect.Pickup`/`BiomeCondition.Mode`/`WeatherCondition.Kind` 等枚举组件会让版本号每次 JVM 重启都变 → `RoundRobinCursors.java:43` 判定结构变化 → 游标归零，于是「轮询跨实体接续并重启保存」（`PLAN.md:276` 验收③）在含枚举参数的规则上不成立。修法：改用稳定内容摘要（`RuleCodecs.codec(effectTypes, conditionTypes)` 已有 JsonOps+RegistryOps 编码路径，见 `RuleCodecs.java:129`/`:171-172`，把候选效果编码成 JSON 文本再取 SHA-256 或 `String.hashCode`），并在 debug 事件输出结构版本供阶段 7 观测。因 `ConversionSettlement.java` 正由阶段 5（task-8）修改，修复排在其后（task-11）。**该缺陷已修复并工程验证通过，见 §15。**
## 14. 阶段 5 记录：安全生成、方块放置与返还位置（task-8，owner platform-scout）

### 14.1 交付面（新增 1 + 修改 8）

- 新增：`common/src/main/java/com/meteorite/itemdespawntowhat/core/type/effect/exec/ReturnItemSpawner.java`（220 行）= 产物/返还统一生成入口 + 三级返还位置搜索游标。
- 修改：`core/type/effect/exec/PlaceBlockExecutor.java`（137 行）、`core/type/effect/exec/SpawnEntityExecutor.java`（231 行）、`core/type/effect/exec/EffectTargets.java`（122 行）、`core/runtime/ConversionSettlement.java`（463 行）、`core/runtime/RuntimeEffectContext.java`（182 行）、`core/api/EffectContext.java`（89 行，仅追加三个 default）、`core/config/ServerConfig.java`（184 行，新键 `position_search_checks_per_tick`）、`core/model/OutcomeCandidate.java`（仅 `:11` 注释「阶段 4」→「阶段 5」）。
- 未触碰：`core/service/**`、`core/runtime/scheduler/**`、`core/api/EffectResult.java`、`core/model` 其它文件、数据包与 `lang`、`fabric/**`、`neoforge/**`、`docs/**`。

### 14.2 验收证据（PLAN.md:280-284）

- **① 水生/陆生安全检查正确**：`SpawnEntityExecutor.java:90-104` `isSafeSpawnPosition` = worldborder(`:91`) → `SpawnPlacements.isSpawnPositionOk(type, level, pos)`(`:95`) → `type.getSpawnAABB(x+0.5D, y, z+0.5D)` + `level.noCollision(box)`(`:99-101`) → 脚/头非危险方块(`:103`)。**不使用 `checkSpawnRules`**（光照/难度/结构门禁会把地下与夜晚误判为不安全）。反编译依据：`SpawnPlacements.java:63-65` → `getPlacementType()`；`SpawnPlacementTypes.java:13-20`（IN_WATER）、`:24-49`（ON_GROUND = 下方 `isValidSpawn` + 本格与上方 `NaturalSpawner.isValidEmptySpawnBlock`，`NaturalSpawner.java:329-339`）；未登记类型回退 NO_RESTRICTIONS（`SpawnPlacements.java:58-61`）。
- **② 找不到安全点回原点且不否决规则**：`SpawnEntityExecutor.java:170-178` 候选耗尽且 `target==null` → `fallbackOrigin=true`；`:195` `spawnOne(target=null, fallback)` → `:210-214` 沿用阶段 4 原点抖动（±0.25）；`:186` 观测 `fallback_origin`。不放弃生成、不否决规则。
- **③ 起点填充不多产、不破坏不可替换方块**：`PlaceBlockExecutor.java:30`/`:50-56` 候选级 `fillOrigin`（`false` 时只剔除触发位置本身）；`:33`/`:91` `planned = min(count×rounds, candidates.size())` 上限封顶；`:112` 逐候选、`:118` 失败也 `cursor++` 继续下一个；`:119-120` 仅 `canBeReplaced() && canSurvive() && setBlock()` 才 `placed++`；`:123` 每个成功计 1 回执。反编译依据：`BlockBehaviour.java:840-842` `canBeReplaced()` → `replaceable` 字段(`:488`)；`Blocks.java:339-353`(WATER)/`:354-370`(LAVA) 均为 replaceable；`BlockItem.java:146-151` 放置占用检查。
- **④ 有限半径不足不强行扩张**：候选上限来自 radius 对应的 offsets（`PlaceBlockExecutor.java:50-59`，CIRCLE 用 `hypot` 剔除半径外），`planned` 再受 `candidates.size()` 封顶；游标只在候选内推进，不越界补位；`limit` 收敛 `target = max(0, min(target, limit - existing))`(`:103-104`)；放不满是允许结果。
- **⑤ 返还不被方块埋住、满高柱用限高以上坐标**：`ReturnItemSpawner.PositionSearch` 三级（`:146-188`）——stage0 起点附近 45 候选（dx,dz∈[-2,2]×dy∈[-1,1]，`:109-121`，起点 (0,0,0) 第一）→ stage1 从 `originBlock.y+1` 上扫到 `maxBuildHeight-1`(`:169-179`) → stage2 `(origin.x, maxBuildHeight, origin.z)`(`:183`)；`:85-97` `isFree` 要求区块已加载 + worldborder + `EntityType.ITEM.getSpawnAABB` 无碰撞 + 脚/头非危险方块；被新放置方块埋住的位置因 `noCollision` 失败被跳过。stage2 的 y 取 `level.getMaxBuildHeight()`（`:142` 取字段、`:183` 使用），无写死常量。

### 14.3 lead 读码复核与裁决

- 逐行读 `ReturnItemSpawner.java`（220 行）与 `SpawnEntityExecutor.java:86-231`：三级搜索、每候选 `budget.charge(1)`、`decided` 缓存（同一份返还物只决定一次）、`reset()` 后下一份从起点附近重搜；`safeSpawn=false` 时 `:169` 整段搜索短路（不调用 `isSafeSpawnPosition`、不额外读世界），区块检查 `area=1`、容量 `allowedCount` 每批重算、位置原点抖动、每实体一次 `reportProgress(1)` ——与阶段 4 行为等价。
- 三条裁决（D22/D23/D24）已由 platform-scout 落地：第三个 default 语义按裁定文案写入（`EffectContext.java:85-88`）；返还位置排除危险方块且 stage2 兜底（`ReturnItemSpawner.java:100-106`、`:183`）；两处 Comparator 链同公式（`ReturnItemSpawner.java:118`、`SpawnEntityExecutor.java:125-127`），搜索空间各自独立。
- 越权写入见 D25（lead 修 `SchedulerConfig.java` + `DebugLog.java:52`），platform-scout 已认领根因（新键只加到 `ServerConfig`）。

### 14.4 工程验证

- 构建点 5：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → `exit=0` / `BUILD SUCCESSFUL in 30s` / `30 actionable tasks: 17 executed, 13 up-to-date`；`:common`/`:fabric`/`:neoforge` 的 compileJava + javadoc + jar + `:fabric:remapJar` 全通过。
- 点 5 之前有一次失败（已修复并复验）：`:common:compileJava` 报 `RuntimeEffectContext.java:96: 错误: 找不到符号 符号: 方法 positionSearchChecksPerTick() 位置: 类 SchedulerConfig`，`BUILD FAILED in 8s`；修法与责任见 D25。
- IDEA MCP 仍不可用（`Streamable HTTP session not found`），本阶段未经 IDE 检查；构建是本阶段唯一的工程验证证据。

### 14.5 已知限制与遗留观察项（阶段 7 复核）

- 无游戏内运行证据：`safe_spawn=true`、返还三级搜索、限高兜底均为代码级证据 + 编译通过，行为需实机或阶段 7 复核。
- `PlaceBlockExecutor` 的 `deferred(planned)`（`planned = min(count×rounds, 候选数)`）在 `limit` 封顶使实际放置少于 `planned` 时，结算记录 `pendingUnits` 会长期 >0；按阶段 4 语义 `pending` = 「已受理未交付」，阶段 6 做中断结算时必须确认不会被误判成「待交付返还」。
- `safe_spawn` 下每个实体都重新从最近候选搜索（`SpawnEntityExecutor.java:197`），8 个实体最多 8 轮搜索，每轮受 `position_search_checks_per_tick` 限制 → 阶段 7 峰值观测点。
- 返还 stage1 上扫最多约 320 次候选检查/份返还物（16/刻 → 约 20 tick）；满高柱场景是返还需时观测点。
- 返还 stage1 的起点是「触发位置上方 (y+1)」而非显式查询柱顶；放置效果若延迟到交付之后执行，搜索不会重扫最新柱顶（阶段 7 如需精确需在交付时重读柱顶）。
- 搜索不加载区块：起点区块未加载 → `CHUNK_UNLOADED` → `retry(1, 20)`（沿用阶段 4 重试语义）。`ReturnItemSpawner.deliver` 把 `addFreshEntity` 失败与区块未加载都归为 `CHUNK_UNLOADED`（统一可重试语义，命名后续可收敛）。
- 预算计量：每候选 `budget.charge(1)`（`:161`/`:174`），每成功交付一份返还另 `charge(2)`（沿用阶段 4）；`position_search_checks_per_tick` 为 `intRange(1, 4096)`（不允许 0，避免搜索停摆），`ServerConfig` 与 `SchedulerConfig:19` 均做 ≥1 clamp。
- 阶段 7 建议观测字段：`SPAWN_POS_SEARCH`（candidates_checked / hit / fallback_origin = 安全生成回退率与深度）、`REBATE_POS_SEARCH`（stage / above_limit = 限高兜底比例）、`PLACE_BLOCK_RESULT`（candidates vs placed vs target = 半径不足缺口），以及搜索类预算占 `server_budget_us` 的比例。
- 用户游戏验收未开始：按 D21，阶段 3/4/5 的计时、账目与位置类验收统一在阶段 6 完成后交用户执行。


## 15. task-11 记录：轮询游标结构版本稳定化（owner runtime-scheduler，构建点 6 green）

### 15.1 缺陷与修法

- 缺陷：`ConversionSettlement.structureVersion(candidates)` 原用 `hash*31 + id.hashCode() + effect.hashCode()`；Effect/条件实现全是 `record`，但枚举组件（`WeatherEffect.Mode`/`PlaceBlockEffect.Shape`/`ArrowRainEffect.Pickup`/`BiomeCondition.Mode`/`WeatherCondition.Kind` 等）的 `hashCode` 是 `Object` 身份哈希，值随 JVM 实现、哈希模式与分配状态变化 → 重启后版本号变化 → `RoundRobinCursors.cursor` 判定结构变化 → 游标归零，破坏 `PLAN.md:276` 验收③。
- 新实现（`ConversionSettlement.java:396-409` `structureVersion` + `:411-424` `digestVersion`）：`OutcomeCandidate.codec(RuleCodecs.effectCodec(runtime.types().effectTypes())).listOf()` 把候选（id、safe_spawn/fill_origin、效果顺序与全部参数）编码成规范 JSON 文本，`RegistryOps.create(JsonOps.INSTANCE, level.registryAccess())` 提供注册表上下文，再取 **SHA-256 前 8 字节**；编码失败走 `resultOrPartial` → `LOGGER.warn` 并返回 0（语义 = 游标归零 + 告警，不静默）；`NoSuchAlgorithmException` 兜底 `String.hashCode()`（仍与对象身份无关）。
- 稳定性论证：输入只含 id、布尔、数值与枚举名（枚举走 `core/type/EnumCodecs.java:21-26` 的 `name().toLowerCase(Locale.ROOT)`）；`TypeDispatch.encode`（`core/api/TypeDispatch.java:57-68`）按 MapCodec 字段声明序编码，无 HashMap 迭代；`OutcomeCandidate.codec` 固定 id/effects/safe_spawn/fill_origin 顺序。runtime-scheduler 另用临时程序实测：同一 JSON 文本 4 次运行摘要恒为 `1751165854`；对照的身份哈希在 `-XX:hashCode=2/4` 或不同 JVM 实例间会变（默认模式下两次新 JVM 恰好相同，说明「默认模式看起来稳定」是假象，修法必须是内容摘要）。
- 旧存档兼容：按旧算法写入的版本号与新摘要不同 → 首次加载一次性归零，不做迁移（可接受，已记入已知限制）。
- 微调：`ConversionRuntime.java:536-539` 新增包内访问器 `BuiltinTypeRegistries types()`（只读复用阶段 1 的类型注册表）；`RoundRobinCursors.java` 未改（版本判据本就由调用方传入）；`OUTCOME_PLANNED` 观测（`:187-191`）新增 `structure_version` / `cursor_start` / `candidates`，仅在 `DebugMode.ENABLED` 分支取值。
- 注释口径修正（runtime-scheduler 主动）：原注释写「枚举身份哈希每次 JVM 启动都不同」过于绝对，改为「其值不受内容约束，随 JVM、哈希模式与分配状态变化」（`ConversionSettlement.java:396-398`）。**PROGRESS 与交付说明沿用后者口径。**

### 15.2 第一次构建失败与修复（lead 记录的教训）

- 构建点 6 首次：`:common:compileJava` 报 `ConversionSettlement.java:400: 错误: 不兼容的类型: BuiltinTypeRegistries无法转换为TypeRegistry<EffectType<?>>`、`BUILD FAILED in 7s`。根因：`RuleCodecs.effectCodec(TypeRegistry<EffectType<?>>)`（`RuleCodecs.java:124`）要注册表本体，而新访问器返回的是记录。修法：调用点改 `runtime.types().effectTypes()`（不动 `effectCodec` 签名）。

### 15.3 工程验证

- 构建点 6 复验：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → `exit=0` / `BUILD SUCCESSFUL in 28s` / `30 actionable tasks: 14 executed, 16 up-to-date`；`:common`/`:fabric`/`:neoforge` 的 compileJava + javadoc + jar + `:fabric:remapJar` 全通过。
- IDEA MCP 仍不可用（`Streamable HTTP session not found`），未经 IDE 检查。


### 15.4 未决项

- 结构摘要每次转化在 `ConversionSettlement` 构造时编码一次（候选少、微秒级，未计入 tick 预算）。「按规则对象缓存」已裁决暂不做：避免引入以对象身份为键的缓存，反过来成为跨重启不一致的新来源；阶段 7 若实测编码开销明显再议。
- 旧存档首次归零不做迁移（可接受）。

## 16. 阶段 6 记录：正常中断、持久返还与恢复（task-9，owner runtime-scheduler，构建点 7 green）

### 16.1 交付面

- 新增：`core/runtime/SettlementRecovery.java`（141 行，REBATE 车道的 `ServerTask`，只交付 `pendingDelivery`，位置载体是未加入世界的 `ItemEntity` 锚点）。
- 修改：`core/runtime/SettlementRecord.java`（242 行：位置字段 `has_position`/`pos_x`/`pos_y`/`pos_z`、`needsRecovery()`、`save`/`load` 全字段）、`SettlementLedger.java`（169 行：`unsettled()`、`unsettled(dim)`、`pendingDeliveryTotal()`、`pendingUnitsTotal()`）、`ConversionRuntime.java`（恢复扫描与入队、`activeRecoveries` 幂等位、`onRecoveryFinished`、`activeRecoveryCount()`、`recoverySkippedDimensions()`）、`ConversionSettlement.java`（`onCancelled` 幂等守卫 + `groupStarted` 后立即落盘）。
- 未触碰：`core/type/**`、`core/api/**`、`core/model/**`、数据包与 `lang`、`platform/**`、`fabric/**`、`neoforge/**`、`docs/**`。`ServerConfig` 未新增键。

### 16.2 四条验收证据（PLAN.md:288-292）

- **① 延迟执行前停服 → 返还全部未开始库存**：`ConversionSettlement.java:139-161` `onCancelled` 顺序 = `syncPendingDelivery(held.getCount())` → `interrupt(reason.name(), level.getGameTime())` → `ledger.put(record)`，调度器在此之后才把句柄置 `CANCELLED` 并从队列移除（顺序证据写在 `:144-145` 注释）；`ConversionSettlement.java:263-275` `dispatch` 在 `groupStarted(cost)` 后立即 `ledger.put`，保证「已支付成本的组」重启后不会被当作未开始库存重复返还；释放点 `ConversionRuntime.java:114`/`:126`(RULE_RELOAD)、`:247`(DIMENSION_UNLOAD)、`:256`(SERVER_STOP)、`:501`/`:514`(ENTITY_REMOVED)，SCENARIO_STOP 由 `DebugScenarioRun` 取消；停服事件早于 `flush → saveAllChunks → 维度 close`，故这次 `put` 会被最终存档保存（Fabric `@At("HEAD")` 已取证，NeoForge 为高置信推断）。
- **② 部分执行后只返未开始组**：守恒 `N = consumedSources + pendingDelivery + deliveredReturns`；算例（N=10、c=2、计划 g=3）：计划后派发前停服 → `10 = 0 + 10 + 0`；派发 1 组后停服 → `10 = 2 + 8 + 0`；派发 1 组并交付 3 个返还后停服 → `2 + 5 + 3 = 10`。
- **③ 重启不重放、不多发**：`SettlementRecord.java:164` `needsRecovery() = status != COMPLETED && pendingDelivery > 0 && !source.isEmpty()`（`load` 后由字段重算，不依赖运行时缓存）；恢复入口 `ConversionRuntime.java:205-218`（首个 tick 全局扫描）、`:254-262`（维度加载/rescan 补扫，插在 `cancelRealm` 之后、空规则库也会恢复）、`:267-277`（位置取记录字段）→ `SettlementRecovery` 只调 `ReturnItemSpawner.deliver(Kind.PERMANENT_RETURN)`，**从不重建 `ConversionSettlement`**（旧效果队列不回放）；幂等位 `activeRecoveries`（`ConversionRuntime.java:226`/`:257` 入队前查、`:273` 加入、`:280-282` 收尾释放）；交付成功后才写盘（`SettlementRecovery.java:82-89`：`ADDED → record.delivered(stack.getCount()) → ledger.put(record)`）；共享轮询进度 `RoundRobinCursors.java:55` `moveTo` 已 `setDirty()`（lead 核实，无需改动）。
- **④ 目标区块不可用记录不丢**：`ReturnItemSpawner.java:69-71` 要求调用方位置区块已加载；`SettlementRecovery.java:93-97` → `budget.charge(1)` + `StepResult.retry(1, retryDelayTicks())`，退避 20/40/80/160/320/640/1200 tick（上限 1 分钟），记录与剩余 `pendingDelivery` 原样留在账本（早已 `put`），不丢弃、不每刻重试。

### 16.3 决策落地（D26–D28 见 §4.8）

- D26 顺序与幂等：`ConversionSettlement.onCancelled`（`:140`）与 `SettlementRecovery.onCancelled`（`:112`）均有 `if (cancelled) return;` 守卫。
- D27 停机/卸载走「下次启动交付」（平台选项 A）：口径注释在 `ConversionRuntime.java:264-266`；未加载维度的记录保持不动并计数（`:230-233`），仅在 `debugLogging` 开启时低频提示，不空转。
- D28 交付只认 `needsRecovery()`；`pendingUnits`/`pendingUnitsTotal()` 仅供调试观测，绝不参与交付量计算。

### 16.4 工程验证

- 构建点 7：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → `exit=0` / `BUILD SUCCESSFUL in 29s` / `30 actionable tasks: 17 executed, 13 up-to-date`；`:common`/`:fabric`/`:neoforge` 的 compileJava + javadoc + jar + `:fabric:remapJar` 全通过。
- IDEA MCP 仍不可用（`Streamable HTTP session not found`），本阶段未经 IDE 检查；构建是唯一工程验证证据。

### 16.5 已知限制与遗留项

- **崩溃窗口（有意取舍）**：`addFreshEntity` 成功但 `ledger.put` 之前进程崩溃 → 重启后该批返还重复交付一次，最坏 1 批（≤ `dispatch_batch_size` 个堆叠）。未采用「交付前先落 `DELIVERING` 状态 + 恢复时反查世界」的在途状态机，理由是代价高于收益。
- 旧存档（阶段 4/5 期间产生的记录）没有位置字段，恢复落点退回该维度全局出生点；仅影响开发期记录的落点，不影响账目。
- 停机与维度卸载时的未开始库存于「下次启动、目标维度加载后」交付，**不是停服瞬间返还**（D27）。
- `activeRecoveryCount()`/`recoverySkippedDimensions()` 尚未接入 `DebugPerformanceWindow`（`core/debug` 不在阶段 6 写入范围），属阶段 7 待接观测项。
- 用户游戏验收未开始：按 D21 待阶段 7 后统一交用户执行 `PLAN.md:304-336`。

## 17. 阶段 7 记录：削峰调优与整体验证（task-10，owner runtime-scheduler，构建点 8/9/10 green，已关闭）

口径前提：本会话只有构建环境、没有可启动的游戏，阶段 7 的交付面是「补齐观测能力 + 对照方法与阈值 + 默认配置收敛判据 + 用户实机执行清单」。**所有性能结论均为待实测**，不得预先宣称毫秒收益（PLAN.md:296-300）。

### 17.1 交付面

- 新增：`core/debug/DebugDistribution.java`（有界整数样本分布；`MAX_SAMPLES = DebugMeasurements.MAX_SAMPLES = 12_000`；nearest-rank `percentile`，键名 `samples/mean/max/p50/p95/p99` 不带 `_us`，单位由调用方决定）。
- 观测修改：`core/runtime/scheduler/KindStats.java`（+`done`/`yielded`/`retried`/`workUnits`/`lastMicros`；`requeued` 仍 = `yielded + retried`）、`SchedulerStats.java`（+`lastMaxReadyDelayTicks`/`lastDrainedTasks`/`drainedTicks`/`maxDrainedInTick`）、`ServerScheduler.java`（counters `long[4]` → `long[6]`：`[0]steps [1]failed [2]YIELD [3]RETRY [4]workUnits [5]done`；`runStep` 记录本 tick 各 kind 微秒；`finishTick` 采集搬运量与就绪延迟；**takeDue/countDue/每步 ≥1 工作单位/预算语义逐字未改**）、`core/runtime/ConversionRuntime.java`（只加只读 `queueStats(ServerLevel, ServerTaskKind)`）、`core/debug/DebugPerformanceWindow.java`（全部新增观测，旧键逐字保留）、`core/debug/DebugScenarioRun.java`（FRAME 增 2 个即时字段）。
- 配置修改：`core/config/ServerConfig.java`（第 15/16 组件 `debugScenarioPrepareBatchSize` / `debugScenarioPrepareBudgetUs`，默认常量 128 / 2000，CODEC 两个 `intRange(1,100000)` 可选键）、`core/debug/DebugLog.java`（`config()` 输出两键）、`DebugScenarioRun.java`（`prepare(RuleCommandContext)` 从既有 `context.serverConfig()`（可空）读取、null 回退 `ServerConfig.DEFAULT`）。
- 未触碰：`core/type/**`、`core/api/**`、`core/model/**`、`core/state/**`、`core/service/**`、数据包与 lang、`platform/**`、`fabric/**`、`neoforge/**`、`docs/**`（除本文件由 lead 维护）。

### 17.2 观测字段清单（按级别区分，D29 要求）

- **server 级（跨维度合并）**：`server_budget.{samples, usage_ratio_ppm.{samples,mean,max,p50,p95,p99}, ticks_exhausted_by_time, ticks_exhausted_by_work_units, ticks_unexhausted}`、`server_ready_delay_ticks.{samples,mean,max,p50,p95,p99}`（单位 tick，**只在存在就绪任务的 tick 采样**，否则恒 0 会稀释分位数）、`server_drain_per_tick.{samples,mean,max,p50,p95,p99, ticks_with_drain_in_window, max_drained_in_tick}`、`server_backlog.{cleared_count_in_window,last_clear_ticks,max_clear_ticks}`、`server_kind_step_micros.<kind>.*`、`server_recovery.{ledger_unsettled, ledger_records, ledger_pending_delivery_total, ledger_pending_units_total_debug_only, active_recoveries, recovery_skipped_dimensions}`、`scheduler.*`（旧字段 + `last_max_ready_delay_ticks`/`last_drained_tasks`/`drained_ticks`/`max_drained_in_tick`、`cancelled_by_reason`、`per_kind.<kind>.*`）、扁平 `ledger_unsettled`/`ledger_records`（旧键保留）。
- **level 级（绑定场景 ServerLevel）**：`checks.*` / `effects.*`（旧键字符不变）、`level_kinds.<kind>.*`（samples/mean_us/max_us/p50_us/p95_us/p99_us + visited_in_window/counter_resets/mean_pending_at_tick_end/max_pending_at_tick_end/final_pending/max_oldest_ready_delay_ticks）、`queue_scope_dimension`、`sampled_seconds`、`sampled_ticks`、`world_ticks_advanced`、`observed_ticks_per_second`、`max_tick_interval_ms`、`sample_limit_reached`、`index_changes`、`server_tick_cost`。
- **FRAME 即时字段**：`scheduler_last_max_ready_delay_ticks`、`scheduler_drained_in_tick`。
- **近似口径标注**：`server_kind_step_micros.*` 与 `level_kinds.*.p95_us` 是 lane 步骤总耗时（含搬运后执行），**不是**位置搜索「候选遍历 / 选择决策」两级直接测量（拆分需改阶段 5 冻结的 `ReturnItemSpawner`）；`server_ready_delay_ticks` 只统计有就绪任务的 tick。

### 17.3 阶段 0 §2.3 十项指标缺口逐项结论

- 1 预算使用率：**已覆盖 + 补分位数**（旧 mean/max 保留，新增 `server_budget.usage_ratio_ppm.*`）。
- 2 耗尽原因三态：**本次补上 `none`**（`ticks_unexhausted`），与 `by_time`/`by_work_units` 三态齐备。
- 3 分步耗时：**部分覆盖** —— drain 只有量（条/tick）无耗时；条件检查/效果执行走 `server_kind_step_micros.<kind>`（近似口径）；**位置搜索两级与返还单列不做**（需改冻结区）。
- 4 按种类计数：**本次补上**（`per_kind` 现含 steps/failed/requeued/done/yielded/retried/work_units/last_micros + pending/deferred）；**per-kind cancelled 不做**（取消按原因在 server 级 `cancelled_by_reason` 已足够）。
- 5 延迟分布：**本次补上**（`server_ready_delay_ticks` 全维度合并分位数；`level_kinds.*.max_oldest_ready_delay_ticks` 保留）。
- 6 积压清空时间：**已覆盖 + 补分布特征**（`server_backlog.*`）。
- 7 取消/丢弃计数：**已覆盖**（阶段 2 落地，`cancelled_by_reason` 全枚举，`clear()` 带原因不再静默）。
- 8 跨维度合并视图：**本次补上**（`server_*` 段与 `scheduler.*` 均为 server 级）。
- 9 返还类指标：**部分覆盖**（`server_recovery.*` 六字段；**返还独立耗时/次数不做**，理由同第 3 项）。
- 10 平滑窗口生效证据：**本次补上（间接）**（`server_drain_per_tick` 分布 + `ticks_with_drain_in_window`/`max_drained_in_tick`；**严格「同一 dueTick 桶最早~最晚执行差」不做**，需改冻结区统计结构且与 allowance 机制重复）。

### 17.4 默认配置判据表（6 个真实键 + 2 个 debug-only 键 + 1 条实现硬化）

| 键 | 当前默认 | 判据 | 收敛场景 | 若实测 X 则调 Y |
| --- | --- | --- | --- | --- |
| `server_budget_us` | 2000 | 代码推导（自阶段 2 之前硬编码 2_000_000ns 逐位迁移） | S0 基线 + S1 `convert 10000 60` | `server_tick_cost.p99` 逼近可承受上限或 `ticks_exhausted_by_time` >30% → 下调 1500/1000；`ticks_unexhausted` ≈100% 且清空 >1000 tick → 上调 3000 |
| `max_work_units_per_tick` | **未配置**（`ServerConfig.effectiveMaxWorkUnitsPerTick()` 回退 `max_checks_per_tick`=512） | 代码推导（旧 key 语义迁移为全局工作量上限） | S1/S2 | work_units 先于时间耗尽且 ready 延迟持续上升（搬运饿死执行）→ 上调 768/1024；从未先触发 → 保持或下调 |
| `effects_work_units_per_tick` | 64 | **设计预留值，未标定** | S1（效果占比最高） | EFFECT ready 延迟显著高于 check 且 work_units 长期触顶 → 上调 96/128；EFFECT 挤占 check → 下调 32 |
| `check_spread_window_ticks` | 20 | 代码推导（= `check_interval_ticks` 默认 20，同批到期在一个检查周期内铺开） | S1/S2 | 同龄到期长期触顶且就绪延迟 >1 秒 → 上调 40（代价：单任务最坏多等 40 tick）；清空 <1 秒 → 可下调 10 |
| `dispatch_batch_size` | 64 | 代码推导（每 lane 每 tick 搬运上限；allowance = `min(batch, ceil(countDue(≤batch×window)/window))`） | S1 | 搬运长期触顶且 ready 延迟上升 → 上调 128；单 tick 尖峰过大（max ≫ mean）→ 下调 32 |
| `position_search_checks_per_tick` | 16（范围 1..4096，不允许 0） | **待实测/未标定**（阶段 5 取值） | S1 返还与安全生成 + `/idtw debug run expiry`（含满高柱） | EFFECT 微秒被搜索拉高并挤占其它 kind → 下调 8；`ledger_pending_delivery_total` 长时间不降 → 上调 32 |
| `debug_scenario_prepare_batch_size` / `debug_scenario_prepare_budget_us` | 128 / 2000 | 代码推导（与旧硬编码逐位等价：128、`(long)2000 × 1000L = 2_000_000`ns） | 不需收敛（只影响场景准备，不参与 `server_budget_us`） | 若准备期掉刻干扰「集中到期」起始对齐 → batch 下调（如 32）或预算上调（如 5000） |

- 实现硬化（**非配置键**）：`max_work_units_per_step = 1` —— `StepResult` 紧凑构造 `workUnits = max(1, …)`，保证每步至少记 1 工作单位、预算单调递减、不会 0 计费空转；要配置化必须动冻结语义（D30 口径更正）。
- **本轮未改任何默认值**（D29/D30 硬条件：无实测数据不得调整）。

### 17.5 峰值转移复核结论

- §11.4 ①「搬运每条扣 1 工作单位、深积压时先耗预算搬运」→ **执行饥饿仍是第一待实测风险**，代码级无法排除；判据 = `server_drain_per_tick` 是否长期 = allowance + `server_ready_delay_ticks.p95/p99` 是否单调上升 + `per_kind.*.deferred` 是否堆积。
- §11.4 ②「YIELD/RETRY 最短延后 1 tick」→ 现可量化（`per_kind.yielded/retried/steps`、`server_backlog.max_clear_ticks`）；不制造新峰值，只拉长清空时间。
- §11.4 ③「`countDue` 统计上限 `batch×window`（默认 1280）且不计费」→ 确认为**观测盲区而非处理盲区**（超出部分既不计费也不搬运）；量化需改冻结区 `TaskLane.countDue`，**不做**，替代判据 = `server_backlog.max_clear_ticks` + ready 延迟。
- §11.4 ④「`counters[2]` 未使用」→ **已解决**（现为 YIELD 计数，`per_kind.yielded`）。
- §14.5 `PlaceBlockExecutor.deferred(planned)` 使 `pendingUnits` 长期 >0 → **已由阶段 6 语义隔离**（交付只认 `pendingDelivery`）；字段名 `ledger_pending_units_total_debug_only` + 注释防误读。
- §14.5 `safe_spawn` 每实体重搜 → **不会形成未受控峰值**（每候选 `budget.charge(1)`，搜索在 EFFECT kind 内，受全局时间/工作量与 `effects_work_units_per_tick` 双重封顶），但会变成 EFFECT kind 内的时间占用与饥饿源；给搜索类单独限额**不做**（需改冻结区）。
- §14.5 返还 stage1 上扫（≈320 候选/份、16/刻 ≈20 tick）→ 同上受 kind 上限封顶；长尾风险 = 交付时长，看 `server_recovery.ledger_pending_delivery_total` 是否单调下降（返还侧必看指标）。
- §14.5 stage1 起点为 `originBlock.y+1` 而非柱顶查询 → 阶段 7 不改（精度问题非峰值）。

### 17.6 工程验证

- 构建点 8（观测 6 文件）：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → `exit=0` / `BUILD SUCCESSFUL in 29s` / `30 actionable tasks: 17 executed, 13 up-to-date`。
- 构建点 9（配置键 3 文件）：`exit=0` / `BUILD SUCCESSFUL in 29s` / `30 actionable tasks: 17 executed, 13 up-to-date`。
- 构建点 10（**最终验收构建**，所有写入任务关闭后由 lead 执行）：`exit=0` / `BUILD SUCCESSFUL in 8s` / `30 actionable tasks: 1 executed, 29 up-to-date`（无待编译改动，确认工作区与构建产物一致）。
- JSON 校验（lead 执行）：`common` + `fabric` + `neoforge` 资源目录下 21 个 `.json` 全部 `JSON.parse` 通过（含 8 规则、8 模板、`lang/en_us.json`、`lang/zh_cn.json`、`itemdespawntowhat.mixins.json`、`fabric.mod.json`）。
- IDEA MCP 仍不可用（`Streamable HTTP session not found`）；阶段 7 的工程验证证据同样只有串行 Gradle 构建 + JSON 校验。

### 17.7 用户实机执行清单（交给用户执行；按 PLAN.md:304-336）

0. 前置：开发环境（`/idtw debug` 仅在 dev env 注册）、单人世界或权限 ≥2、先备份存档；记录 `config/itemdespawntowhat/server.json` 全部键 + seed / simulation-distance / 已加载区块；同一时刻只跑一个场景（busy）。
1. 基线：`/idtw debug bench baseline 60` → 保存 START、每秒 FRAME、summary。
2. 纯检查噪声：`/idtw debug bench normal 10000 10` → 看 `checks` 与 `server_tick_cost`。
3. 主场景：`/idtw debug bench convert 10000 60` → 重点抄 `server_budget.*`、`server_ready_delay_ticks.*`、`server_drain_per_tick.*`、`server_backlog.*`、`scheduler.per_kind.*`、`server_kind_step_micros.*`、`server_recovery.*`、`level_kinds.*.max_oldest_ready_delay_ticks`、`verdict`。
4. 多维度合并：两名玩家分别在主世界与另一维度各跑 `convert 5000 60`（尽量对齐启动）→ 验证 `server_*` 为合并值、`scheduler.realms ≥ 2`、仍是单一预算。
5. 环境集中销毁：自建岩浆池 / 仙人掌阵列投入 1000 个同类物品，观察 FRAME 的 tracked/队列与 verdict。
6. 返还与恢复：`/idtw debug run expiry`（含满高柱位置）→ 记录 `REBATE_POS_SEARCH` 与返还生成；随后保存退出→重进，确认 `ledger_pending_delivery_total` 下降且产物不重复。
7. 中断验收：延迟执行前停服 → 全部未开始源返还；部分组已开始后停服 → 只返未开始组。
8. 收尾：每个场景保留原始日志与当时的 `server.json`；异常记下世界时间与日志行。

阅读提醒：`server_ready_delay_ticks` 只统计有就绪任务的 tick；`server_kind_step_micros` / `level_kinds.*.p95_us` 为近似口径（lane 步骤总耗时）。

### 17.8 已知限制

- **全部性能结论待实测**：本轮没有任何游戏内数字，配置默认值一律未改（D29/D30）。
- 4 项观测明确未做（位置搜索两级耗时、返还单列耗时、per-kind cancelled、严格同桶铺开跨度），理由均为「需改阶段 2/5 冻结区」或收益低，已在 17.3 逐项记录。
- 执行饥饿（搬运优先消耗预算）是第一待实测风险，判据字段已就绪但无法在无游戏环境下证实或排除。
- IDEA MCP 不可用，阶段 1–7 的「工程验证通过」全部只以串行 Gradle 构建（+ 本轮 JSON 校验）为证据。


## 18. 阶段 7 后补漏：规则级催化剂固定成本（task-12 契约 + task-13 执行）

### 18.1 缺口与证据（lead 全仓复核）

- `catalyst_cost` 在阶段 1 落成规则级**整数**：`core/model/Rule.java:33`（`@Nullable Integer catalystCost`）、`core/model/RuleCodecs.java:151-152`（`Codec.INT.optionalFieldOf`）、`core/model/RuleValidation.java:65-66`（只校负）、`core/api/RuleFields.java:24`、客户端 `client/ui/screen/RuleEditorScreen.java:1521-1523` 与 `:1547-1549`。
- 全仓 `catalystCost|CATALYST_COST|catalyst_cost` 命中点仅：codec / 校验 / 客户端 / 文档；`core/runtime/**` 内 `catalyst` 命中 **0**，`core/type/**` 仅效果式 `consume_catalyst`（`ConsumeCatalystEffect` + `ConsumeCatalystExecutor`，与规则级字段无关）。
- 后果：数据包写 `catalyst_cost: 2` 时既不检查催化剂数量也不扣减，直接按源成本产出 —— 违反 PLAN.md:48「催化剂必须足量支付，不能少扣却照常产出」、PLAN.md:52「催化剂不足…在开始该组之前处理」、PLAN.md:184/186 预留与释放、PLAN.md:290「释放未消费的催化剂 / 容量预留」。
- 形状层面：阶段 0 领域笔记已提出对象形状（`notes/stage0-domain.md:253`：`catalyst_cost` 应为 `{items:[TaggedId], count:int}`），阶段 1 简化成整数后**该固定成本结构上不可执行**（没有物品来源就无法判定消耗什么）。

### 18.2 裁决（D31，全文见 §4.10）

- 形状改为对象 `{"items":[TaggedId...],"count":int,"radius":int?}`（items 必填非空、物品 id 或 `#tag`；count 1..64；radius 可选默认 1、1..8），整数写法解码报错、不做静默兼容；内置规则与 8 个模板均未使用该字段，无数据迁移。
- 计划期组数上界加入 `floor(可用催化剂件数 / count)`；预留表按维度记录「已预留未支付」催化剂实体 UUID→件数；组开始转已支付（真实扣减），取消/中断释放预留不扣件数；`SettlementRecord` 增加催化剂字段并进 save/load；**源守恒公式不变**（催化剂单独计数）。

### 18.3 分工与依赖

- **task-12**（owner domain-contract，in_progress，writeScopes = `core/model/**`、`core/api/RuleFields.java`、`client/**`）：新类型 + Codec + 校验 + 客户端本地拦截 + lang 同步（如需）。
- **task-13**（owner runtime-scheduler，pending，blocked_by task-12，writeScopes = `core/runtime/**`）：计划期催化剂上界、预留表、组开始支付、取消释放、记账字段、观测事件。
- 依赖：task-12 完成并构建通过后才唤醒 task-13（当前 runtime-scheduler 只做只读方案回报，不写码）。

### 18.4 本轮保留的已知限制

- 效果式 `consume_catalyst`（既有数据包兼容路径，D14 保留）不纳入固定成本的计划期预留：它在组内按 `count × rounds` 实时扣附近物品，不足时只记录 `short_of_catalyst` 且不补偿。规则级 `catalyst_cost` 与它互斥声明（`RuleValidation.java:71-73`），所以同一规则不会同时走两条路径。
- 预留表为运行期结构：停服/卸载只落结算记录（D27 选项 A），催化剂预留随运行期消失，重启不重放、不补偿；未开始组的源返还仍按 §16 路径交付。
- 催化剂匹配按物品/标签命中，不按上游模组自定义物品语义；tag 命中按件数统计（与 `ConsumeCatalystExecutor.matchesAny` 相同的近似口径）。


### 18.5 补漏实施记录（构建点 11 起）

- task-12 检查点①（domain-contract）：新增 `core/model/CatalystCost.java`（当时记 52 行，检查点②实测更正为 **46 行**；`items`/`count`/`radius` + DEFAULT_COUNT=1/MIN_COUNT=1/MAX_COUNT=64/DEFAULT_RADIUS=1/MIN_RADIUS=1/MAX_RADIUS=8，紧凑构造器 `List.copyOf`）；`core/api/RuleFields.java` 新增 `CATALYST_ITEMS/CATALYST_COUNT/CATALYST_RADIUS`；`core/model/Rule.java:33` 组件类型改为 `@Nullable CatalystCost`；`RuleCodecs` 挂 `CATALYST_COST_FIELDS` 白名单（非对象即 error「整数写法已废弃」、对象内未知键 error，故 chance/conditions/delay_ticks 结构上被拒）；`RuleValidation:271-282 validateCatalystCost`（notEmpty + RefChecks.checkAll + inRange，路径 `catalyst_cost.items/.count/.radius`）；客户端 `RuleEditorScreen:1522-1548` 四个新拦截键；lang 双语各 642 行（删旧键 `catalyst_cost_negative`、加 4 键）。
- **构建点 11**：`tools/dsh-build.ps1 -Tasks "build"` → `exit=0` / `BUILD SUCCESSFUL in 30s` / `30 actionable tasks: 20 executed, 10 up-to-date`（:common/:fabric/:neoforge 编译 + javadoc + jar 全通过）。
- **构建点 12**：`core/type` 例外（`EffectTargets` 类改 public + `blockBox` 改 public static + 新增 public `matchesAny`，`ConsumeCatalystExecutor.matchesAny` 改委托）落盘后复验 → `exit=0` / `BUILD SUCCESSFUL in 29s` / `17 executed, 13 up-to-date`。
- **task-12 检查点②（domain-contract）**：探针在 %TEMP%\idtw_probe_catalyst（仓库内零新增文件）用真实 classpath 验证：对象往返等价（`{"items":[...],"count":2,"radius":3}` decode→encode 一致）、默认值不写回（`encode={"items":["minecraft:stone"]}`）、边界四条解码期报错（整数 → `Not a JSON object: 3`；缺 items → `No key items in MapLike[...]`；count=100 → `Value 100 outside of range [1:64]`；radius=9 → `Value 9 outside of range [1:8]`）；8 个数据包文件 10 条规则解码 10/10 通过、encode→decode 10/10 等价、data 与 assets 逐对 SHA256 相同（8/8）。
- 旧整数写法实测文案（RuleCodecs.java:224 分支）：`[ERROR] catalyst_cost: catalyst_cost 必须是对象 {"items":[...],"count":1,"radius":1}，整数写法已废弃`；未知子字段：`[ERROR] catalyst_cost.chance: catalyst_cost 存在未知字段: chance`。
- 诚实标注：`RuleValidation` 整链（`validateCatalystCost` 触碰 `BuiltInRegistries.ITEM`）在游戏外 JVM 无法 bootstrap（补 lang 桩后仍 StackOverflowError），改为直连 `ParamChecks` 实测 3 条 error（`catalyst_cost.items` 空 / `count=100` / `radius=9`，文案与路径实测）；`validateCatalystCost` 与 `:75` 互斥 error 为静态读码确认。
- task-12 最终改动面 = 7 文件：新增 `core/model/CatalystCost.java`（**46 行**，检查点①的 52 行为误报已更正）；改 `core/api/RuleFields.java`(86)、`core/model/Rule.java`(119)、`core/model/RuleCodecs.java`(388)、`core/model/RuleValidation.java`(329)、`client/ui/screen/RuleEditorScreen.java`(2221)、lang 双语（各 642 行）。**task-12 已关闭（completed）**，task-13 已 reassign 给 runtime-scheduler。
- **构建点 13**（task-13 检查点①：`CatalystReservations.java` 新增 + `SettlementRecord` 催化剂三字段与 save/load）→ `exit=0` / `BUILD SUCCESSFUL in 29s` / `17 executed, 13 up-to-date`。
- **构建点 14**（task-13 检查点②：`ConversionSettlement` 催化剂上界与预留时机 + `ConversionRuntime` 运行期持有与 shutdown 清空）→ `exit=0` / `BUILD SUCCESSFUL in 29s` / `14 executed, 16 up-to-date`。
- **task-13 检查点③④（runtime-scheduler）**：`ConversionSettlement.dispatch:340-376` 固定顺序 = `payGroup` → RETRY（:345-348 一件不扣、不开组、`yield(1,1)` 下刻重试）→ SHORT（:349-363 不开组 → `shortGroups = groupCount - groupsStarted` → `groupCount = groupsStarted` → `record.plan(candidateId, groupCount)` → `releaseOwner` → `ledger.put` → CATALYST_SHORT → `break`，剩余源走 :421-426 `syncPendingDelivery` → DELIVERING 正常返还）→ PAID（:364 `paidCatalyst` → :366-370 源成本 → :371 `groupStarted(cost)` → :373 `catalystGroupPaid(paid)` → :375 `ledger.put`）。释放路径：`onCancelled:150`、`deliver` 完成 :431-432 防御性释放、`SettlementRecovery:116-117`、`ConversionRuntime.shutdown:338-339 clear()`。观测三处：CATALYST_PLANNED(:217-219)、CATALYST_RELEASED(:165-167)、CATALYST_SHORT(:357)，全在 `DebugMode.ENABLED` 内。
- **lead 复核确认**：`ConversionSettlement.java:175-232` 里 tryReserve 只在最终选定候选调用一次（成功路径 :212-232 直接 `return StepResult.yield`，无 continue）；`CatalystReservations.java:81-84` 新增「同 owner 已有预留即拒绝」守卫（`byOwner.containsKey` → 返回 null），杜绝叠加预留；tryReserve(:208) 到 `record.plan`(:216) 之间无 yield/return/continue/throw；`collectCatalystItems:283-291` 只在 `blockBox(source.blockPosition(), catalyst.radius())` 内查询并排除源实体与已移除实体。
- **构建点 15**（检查点③④）→ `exit=0` / `BUILD SUCCESSFUL in 28s` / `14 executed, 16 up-to-date`。
- **检查点⑤（残留预留闭合，lead 裁决后追加）**：`ConversionSettlement.step:129-148` 把 switch 包进 `try`，`catch (RuntimeException failure)` → `abort("exception: " + failure.getClass().getSimpleName(), true)` 后 rethrow（调度器 FAILED 转换未改，scheduler 包零改动）；`onCancelled:150-158` 改为 `if (!abort(reason.name(), false)) return;`；新增私有 `boolean abort(String reason, boolean failed)` :160-194，顺序 = `cancelled` 幂等位 → `record.completed()` 守卫 → `record.syncPendingDelivery(held.getCount())` → `failed ? record.fail(...) : record.interrupt(...)` → `reservations().releaseOwner(record.id())` → `ledger.put`（失败只 warn）→ `SETTLEMENT_CANCELLED` + `CATALYST_RELEASED` 观测；`SettlementRecord.completed()` :186 为新增守卫（既有 `fail` 无 COMPLETED 早退，裸调会把已完成记录改写成 FAILED）。
- **lead 读码复核**：`ConversionSettlement.java:126-194` 与回报逐条一致；`abort` 无世界写入，幂等（第二次调用返回 false）。异常算例（N=10、c=2、g=3、催化剂每组 2 件、盒内可用 6 件，第 3 组派发时抛异常）→ consumedSources 4 + pendingDelivery 6 + deliveredReturns 0 = 10；重启后 recovery 只交付 6 件 → 4 + 0 + 6 = 10，组 1/2 效果不重放。
- **构建点 16** → `exit=0` / `BUILD SUCCESSFUL in 28s` / `14 executed, 16 up-to-date`。
- **task-13 已关闭为 completed（rev4）**；task-12/13 全部收尾，补漏档完成。
- **构建点 17（所有写入任务停止后的最终验收构建）** → `exit=0` / `BUILD SUCCESSFUL in 8s` / `30 actionable tasks: 1 executed, 29 up-to-date`。
- 最终静态复核：`common/src/main/java` 内 `&& false` 命中 0；`core/**` 内 TODO/FIXME/XXX 命中 0；`core/debug` 内 `catalyst` 命中 0（严格遵守边界）。
- 最终工作区：`git status --porcelain` **82 条**（64 个 tracked 修改 + 1 删除 + 17 个未跟踪目录/文件集）；`git diff --stat` = **64 files changed, 1927 insertions(+), 413 deletions(-)**。未提交、未推送。
- **实机验收附加观察（催化剂链，建议加进 §17.7 的第 3 步日志抄录）**：`CATALYST_PLANNED` / `CATALYST_RELEASED` / `CATALYST_SHORT` 三个调试事件（`DebugMode.ENABLED` 下），并专门确认「催化剂可用量不足时只减少组数、绝不少扣催化剂却照常产出」。
- JSON 校验（补漏后复跑）：`common/`+`fabric/`+`neoforge/` 下 **21 个 .json 全部 JSON.parse 通过**；`zh_cn.json`/`en_us.json` 各 **640 键**（domain-contract 报的 642 行为行数口径差异，键数 640 为准）。
- 实施顺序留痕：task-12 检查点①（`CatalystCost` + `RuleFields` + `RuleCodecs`，lead 构建点 11）→ 检查点②（`Rule.java` 组件 + `RuleValidation` + `RuleEditorScreen` + lang，构建点 11/12 覆盖）→ 关闭 task-12 → task-13 第一批（`core/type` 可见性 + 委托，构建点 12）→ 第二批（`CatalystReservations` + `SettlementRecord` 三字段，构建点 13）→ 第三批（`ConversionSettlement` 上界与预留时机 + `ConversionRuntime` 持有/clear，构建点 14）→ 第四批（dispatch 支付与截断、释放路径、观测，构建点 15）→ 第五批（`abort` 异常闭合，构建点 16）。

## 19. 交付确认（goal round 3 复核）

本轮以「交付确认」为目的重新核对：先读规划书全文与状态表，再逐条抽查实现，最后跑一次独立构建。结论：**PLAN 阶段 0–7 与后续补漏档的实现侧内容已全部落地，工程验证通过；唯一未完成项是用户游戏验收（PLAN.md:304-336 矩阵 + §17.7 八步清单），本环境无游戏客户端、不可代替执行。**

### 19.1 本轮新增验证证据

- **构建点 18（本轮独立验收构建，foreground 取明确退出码）** → `exitCode=0` / `BUILD SUCCESSFUL in 8s` / `30 actionable tasks: 1 executed, 29 up-to-date`；`:common` / `:fabric` / `:neoforge` 的 compileJava + javadoc + jar + remapJar 全通过。执行方式：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"`（未裸跑 gradlew、未并发）。
- **JSON 校验复跑** → `json_ok=21 bad=0`（`common` / `fabric` / `neoforge` 的 `src/main/resources` 下全部 `.json`）。
- **IDEA MCP 本轮再试一次** → `lint_files` 仍返回 `Error POSTing to endpoint: Streamable HTTP session not found`，因此**本轮同样没有 IDE 检查证据**；按「工具不可用时如实记录，不能声称检查通过」处理，不记为通过。
- **工作区状态** → `git status --porcelain` 82 条；`git diff --stat` = 64 files changed, 1927 insertions(+), 413 deletions(-)；**未提交、未推送**（提交与推送需用户另行授权）。
- **写入方冻结确认** → task-1…task-13 全部 completed；platform-scout / runtime-scheduler / domain-contract 三名 teammate 均 inactive，构建期间无并发写入。

### 19.2 规划书覆盖抽查（lead 逐节读码核对）

- §2.1 四类触发并列 + 按最终致死伤害归因：`core/model/TriggerKind.java` + 阶段 3 两平台最小入口（Fabric `@Inject hurt INVOKE ItemStack.onDestroyed`、NeoForge `ItemEntityMixin` + `ItemExpired`/保护事件）。
- §2.3 固定成本与完整转化组：`Rule.sourceCost()` 必须为正、`CatalystCost` 固定数量成本（补漏档），组数 `g = min(floor(N/c), 候选容量, floor(催化剂可用/每组用量))`，不足一组不强制产出；`SettlementRecord` 守恒 `N = consumedSources + pendingDelivery + deliveredReturns`。
- §2.4 组合模式：默认 `ROUND_ROBIN`（`Rule.effectiveOutcomes()` + `RoundRobinCursors`，结构版本改为候选 JSON 的 SHA-256 前 8 字节，见 §15）；一次性效果 `EffectType.oneShot()` 封顶 1 组。
- §2.6 状态与计时：`core/state/DropState.java`（临时保护 / 临时冷却 / 永久保护 / 永久禁转，`v:1`）、`DropStateStore`、`ServerLevel#getGameTime()` 计时、合并兼容 `tryToMerge` HEAD + 4 参 `merge` TAIL 并集。
- §2.7 位置：`SpawnEntityExecutor` 安全点搜索（水平 5×5、上下各 2 格、最近优先、找不到回原点）、`PlaceBlockExecutor` 只替换 `canBeReplaced`、`ReturnItemSpawner` 三段搜索（起点附近 45 候选 → 向上至限高 → 限高以上坐标）。
- §4.6 公共预算：`core/runtime/scheduler/`（单实例、按维度 lane、公平轮转、逐条搬运计入预算、到期保留原时刻、每 tick 一次入口）。
- §5 阶段验收四条/五条逐条在 §8–§17 有对应代码证据段落；§6 验收矩阵 27 行全部为「待用户实机」，本轮未勾选任何一行。

### 19.3 交接状态

- **可直接交给用户的部分**：编译产物（`common` / `fabric` / `neoforge` 的 jar 与 remapJar）、内置数据包新字段、配置键（`config/itemdespawntowhat/server.json`）、`/idtw debug bench|run` 场景命令、`docs/plan/backend-round-2/` 下的规划书 + 决策 + 验收清单。
- **不能由本环境完成的部分**：正式版/开发版游戏内实机动作、性能采样（真实 tick P95/P99、积压清空时间）、IDE 静态检查（当时 IDEA MCP 不可用；用户重连后已于 §20 补做，并修复 4 条 ERROR 级问题）。
- **下一步**：用户按 §17.7 执行并从 §6 矩阵逐行勾选；若发现缺陷，回报场景 + 日志（含 `config/itemdespawntowhat/server.json`）由 lead 定位到具体阶段修复。

## 20. IDEA 静态检查（用户重连 IDEA MCP 后补做，m01227）

### 20.1 工具与范围

- IDEA MCP 恢复可用：`mcp__srv-e3b0c44298fc1c14__lint_files`（`min_severity=warning`）全程正常，16 个文件约 4.6s、26 个文件约 9s；`projectPath` 必须传绝对路径 `D:/system_default/desktop/mc_mod_project/itemDespawn-1.21.1-mult`。
- 检查范围：`git status --porcelain` 中全部**存在**的改动 `.java`，共 **57 个**（83 条改动里 1 条是删除的 `TickScheduler.java`，其余为 md/json/资源）。
- 限制：IDEA 的 `read_file` 只能读项目内文件（读 `.gradle` 下的 jar 报 `Unable to determine the target project`）→ 判定 Mixin 规范所需的源码先解压到 `%TEMP%\mixin-src-085`（`mixin-0.8.5-sources.jar`，431 个 java）后离线阅读。

### 20.2 修复前：4 条 ERROR（阻塞级，全部在 `ItemEntityMixin`）

| 文件:行 | 注入 handler | 目标方法 | IDEA 文案 |
| --- | --- | --- | --- |
| `fabric/.../mixin/ItemEntityMixin.java:46` | `itemdespawntowhat$onEnvironmentDestroy(DamageSource, float, CallbackInfo)` | `hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z`（`@Inject` + INVOKE） | Method signature does not match expected signature for Inject |
| `neoforge/.../mixin/ItemEntityMixin.java:25` | 同上 | 同上 | 同上 |
| `fabric/.../mixin/ItemEntityMixin.java:64` | `itemdespawntowhat$unionTemporaryState(ItemEntity, ItemStack, ItemEntity, ItemStack)` | `merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V`（TAIL） | 同上 |
| `neoforge/.../mixin/ItemEntityMixin.java:43` | 同上 | 同上 | 同上 |

- **根因（Mixin 0.8.5 源码判定，非 IDEA 误报）**：`CallbackInfo.java:121-123 getCallInfoClassName(Type)` 规定 **void 目标用 `CallbackInfo`、非 void 目标用 `CallbackInfoReturnable`**；`CallbackInjector.java:500-519` 在描述符不匹配且 `!canCaptureLocals` 时把 handler 里的 `CallbackInfo;` 替换为 `CallbackInfoReturnable;` 重试，命中即抛 `InvalidInjectionException`（`Invalid descriptor on ...! CallbackInfoReturnable is required!`），未命中则抛 `Expected ... but found ...`；`Inject.java:209 locals()` 默认 `LocalCapture.NO_CAPTURE` → `canCaptureLocals` 恒 false，所以该分支必然生效，且**void 目标的 handler 描述符尾部必须带 `CallbackInfo`**。
- **失败后果**：两个 `mixins.json` 均为 `required: true` + `injectors.defaultRequire: 1`，Mixin 侧 `MixinProcessor.java:603` 取 `ErrorAction action = config.isRequired() ? ERROR : WARN`，`:636` 抛 `MixinApplyError` → 属必需注入失败；这 4 处正好承载「环境致死触发转化」与「合并临时状态并集」两条核心行为，必须先修才可交用户实机验收。

### 20.3 修复内容（D34，仅两个文件、7 行）

- `fabric/.../mixin/ItemEntityMixin.java`：`:46` handler 尾参 `CallbackInfo ci` → `CallbackInfoReturnable<Boolean> cir`；`:64` `unionTemporaryState(...)` 末尾补 `CallbackInfo ci`。
- `neoforge/.../mixin/ItemEntityMixin.java`：`:14` 补 `import ...callback.CallbackInfoReturnable;`；`:26` 同 fabric 的返回值类型修正；`:44` 补尾部 `CallbackInfo ci`。
- 顺带对齐目标方法参数名（消除 6 条 weak warning）：`tryToMerge(ItemEntity itemEntity)` 的 `other` → `itemEntity`；4 参 `merge(ItemEntity destinationEntity, ItemStack destinationStack, ItemEntity originEntity, ItemStack originStack)` 的 `destination` → `destinationEntity`、`origin` → `originEntity`。
- 复核：两个 mixin 文件单独 `lint_files` 复跑返回**空**（0 问题）。

### 20.4 注入点合法性交叉验证（javap 反汇编，不只看源码）

- Fabric 侧目标为原版签名：`common/build/moddev/artifacts/vanilla-1.21.1-20240808.144430-minecraft-merged.jar` 的 `ItemEntity.hurt` 内 `invokevirtual ItemStack.onDestroyed:(Lnet/minecraft/world/entity/item/ItemEntity;)V`（偏移 109）→ 与 `@At` 的 INVOKE 目标一致。
- NeoForge 侧目标为平台补丁后的 2 参签名：`neoforge/build/moddev/artifacts/neoforge-21.1.218-minecraft-merged.jar` 的 `ItemEntity.hurt` 内 `invokevirtual ItemStack.onDestroyed:(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/damagesource/DamageSource;)V`（偏移 110）；`javap` 确认 `ItemStack` 类本体只声明 1 参 `onDestroyed(ItemEntity)`，2 参来自扩展接口（与 D6 记录一致）。
- 结论：两处 `@At` 的 owner 与描述符都正确，问题只在 handler 签名本身。

### 20.5 其余 92 条 WARNING 的复核分级（按 D34 不修改）

- **误报（IDEA 无法证明守卫）**：约 15 处「在没有 try-with-resources 语句的情况下使用 ServerLevel」（`Level` 实现 `AutoCloseable` 的通用误报，如 `ArrowRainExecutor.java:54`、`PlaceBlockExecutor.java:109/119/120`、`SpawnEntityExecutor.java:84`）；`DebugScenarioRun.java:168` 拆箱可能 NPE（组件是 `int`，codec 用 `optionalFieldOf(..., DEFAULT)`）；`ConversionSettlement.java:562/:587` 拆箱 `effect.limit()`（调用点 `:339`/`:344` 已判 `limit() != null`）；`SettlementRecord.java:245` `tag.get("source_stack")`（`:244` 有 `contains` 守卫）。
- **真实死代码 / 死形参（本轮不清理）**：`core/service/BuiltinTypeRegistries.java:53 implicitSourceConsumption()` 阶段 4 后无调用点；`core/type/effect/exec/SpawnEntityExecutor.java:203` 形参 `fallback` 未使用；`core/type/effect/exec/EffectTargets.java:119` 三参重载的 `batchSize` 调用处恒为 1；`core/model/RuleValidation.java:312/:320` 私有方法 `warnLargeSource`/`describe` 未使用。
- **预留 API（非缺陷）**：`core/api/EffectContext.java:50/:53/:59` 的 `outcomeId()/groupIndex()/groupSourceCost()`、`ServerConfig.DEFAULT_MAX_WORK_UNITS_PER_TICK`（`:55`）与 `serverBudgetNanos()`（`:137`）、scheduler 与 `SettlementRecord` 的多个只读访问器。
- **风格建议**：`Math.clamp()`（`ServerConfig.java:194`、`PlaceBlockExecutor.java:104`、`ConsumeSourceExecutor.java:49`、`SpawnEntityExecutor.java:86`）、记录模式（`RuleCodecs.java:268/272/276/280`）、`getFirst()`（`BuiltinEditorDefaults.java:166`）、冗余 cast 与显式类型实参（`RuleCodecs.java:243/312/327/347`）、`if` 换 `switch`（`RuleReferenceValidator.java:54`）、`BuiltinEditorDefaults.java:153` 形参恒为 `RuleFields.TYPE`、`RuleEditorScreen.java:308` 未注解形参。
- **决策（D34）**：只修 4 条 ERROR 与 6 条参数名 weak warning，其余一律保留（改风格会扩大回归面、无功能收益），记为本轮已知静态噪声，留待下一轮统一清理。

### 20.6 工程验证

- **构建点 19（修复后）**：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → `exit code 0` / `BUILD SUCCESSFUL in 37s` / `30 actionable tasks: 10 executed, 20 up-to-date`；`:common`/`:fabric`/`:neoforge` 的 compileJava + javadoc + jar + `:fabric:remapJar` 全通过。
- **lint 复跑**：57 个改动 `.java`（`min_severity=warning`）→ **0 ERROR**、92 WARNING、4 WEAK WARNING；其中两个 `ItemEntityMixin` 单独复跑返回空。
- 本轮不涉及数据包与 JSON 变更，故未复跑 JSON 校验。
- 用户实机验收仍未开始：`PLAN.md:304-336` 矩阵与 §17.7 八步清单均未勾选，「用户游戏验收通过」一档依旧全空。
