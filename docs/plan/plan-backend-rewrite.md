# 后端重构规划书（ItemDespawnToWhat 1.21.1）

> **当前状态更新（2026-10-02）**：后端实现已收尾，旧链路已删除；用户明确将前端改为占位页，完整前端下一轮重构，因此原 Q2/Q29/Q35 与视觉冻结验收由 [ADR-0017](../adr/0017-backend-cutover-and-budgeted-effects.md) 替代。下文原架构与①～⑤为历史规划/阶段记录，不代表当前残留代码。当前实现见 [架构说明](../dev/backend/README.md)，修复与实机验收见 [收尾记录](../review/backend-rewrite-closeout-2026-10-02.md)。
>
> **convert 相关章节已被取代（2026-10-03，P8 结论）**：下文凡涉及 `/idtw config convert` 的规划与进度记录（§2.2、§3.2、§3.3、§3.10、阶段 ⑤ / ⑥、§5 与附录 A 的 Q4 / Q19 / Q34）均已被 [v1.2.1 迁移评估](v1.2.1-migration-evaluation.md) §5 与 [plan-frontend-rewrite.md](plan-frontend-rewrite.md) §13.3 取代——转换命令与 `core/command/RuleConvertService.java` 已**整体退役**，迁移策略改为**破坏性更新**：旧 JSON 原样保留、不删除、不改写，由玩家用 `/idtw config edit` 在编辑器里手工重建（见 [更新说明](../guide/update-notes.md)）。带「已被 P8 取代」行内标记的原文保留为历史决策记录，不再代表当前实现。

> 本文档来自一次 grilling 会话（47 个决策点，台账见附录 A），回答三件事：**现状是什么、计划要达到什么目标、走哪条技术路线**。
> 执行期的更细粒度规划（每个效果的字段表、每个阶段的逐步清单）在实施时按阶段另行细分，本文档只锁定架构契约与阶段边界。
> 目标读者：项目维护者。

---

## 一、现状

### 1.1 当前架构一图

```
config/itemdespawntowhat/<ns>/<path>.json   （9 个转化类型各一个文件，Gson 手写序列化）
        │  JsonConfigRepository / ConfigJsonCodec / ConfigMigrator（读时自动写回）
        ▼
（可变）BaseConversionConfig 子类 + transient 缓存 + validate()/resolve()/initCache()
        │  ConfigService（静态单例 ConfigExtractorManager 持有）
        ▼
RuntimeConfigSnapshotBuilder → RuntimeConfigSnapshot（容器不可变、元素可变）
        │  ConversionRuleCompiler → CompiledConversionRule
        ▼
ConversionTracker（静态 Map<ServerLevel, Map<ItemEntity, ConversionState>>）
        │  每 20 tick 全量遍历
        ▼
ItemConversionProcessor（选择规则 → 条件求值 → 计时 → 执行）
        ▼
AbstractConversionExecutor 子类 → LevelTaskManager / 延迟任务 / 直接改世界

网络：C2S 4 包 + S2C 3 包，快照为整包 JSON 字符串，全局单 UUID 编辑会话锁
命令：/idtw edit | reload | inspect（inspect 内硬编码 5 个旧条件名）
客户端：client/** 直接读写后端 DTO（27 个文件 + 1 个 fabric 入口，编译期 FQN 绑定）
```

### 1.2 已核实的病灶清单

以下每一条都在本次分析中读过源码确认，不是推测。

**A. 正确性缺陷（可复现 bug）**

| # | 问题 | 证据 |
|---|---|---|
| A1 | **GUI 保存会从磁盘永久删除规则**：写回依据是运行时快照而非文件，而 disabled / 编译失败 / tag 未命中的规则不进快照 | `ConfigEditServerPayloadHandler.java:58`、`JsonConfigRepository.java:73`、`RuntimeConfigSnapshotBuilder.java:45` |
| A2 | **计时单位错误**：`conversionTime` 语义为"秒"，实现是"通过条件的检查次数"，每次 20 tick | `ItemConversionProcessor.java:20,90`；UI 默认值 `BuiltinFormDefinitions.java:258` |
| A3 | **热重载对已存在的掉落物无效**：追踪只在实体生成/加载时注册 | `ConversionTracker.java:30-49` |
| A4 | **内存泄漏**：追踪表是静态 Map 强引用 `ServerLevel`，`clear(ServerLevel)` 零调用 | `ConversionTracker.java:24,113` |
| A5 | **Fabric 锁全部生物死亡掉落，NeoForge 只锁玩家**：同一配置两端行为不同 | `LivingEntityMixin.java:19-27` vs `ItemConversionEvent.java:35-44 (N)` |
| A6 | **Fabric lifespan 硬编码 6000，NeoForge 走 `getEntityLifespan`** | `ItemConversionEvent.java:14 (F)` vs `:54 (N)` |
| A7 | **规则提升靠引用身份比较**，重载/删除后语义未定义 | `ItemConversionProcessor.java:131` |
| A8 | **结果上限走世界 AABB 扫描且每检查重复 2~3 次**，成本 ≈ O(掉落物 × 规则 × 附近实体) | `ItemConversionProcessor.java:103,119,144`、`ItemToItemExecutor.java:43-55` |
| A9 | **读取有副作用**：`schema_version` 缺失即视为 v1，加载时先写回再校验 | `JsonConfigRepository.java:102`、`ConfigMigrator.java:13` |
| A10 | **校验三套策略并存**：未知字段拒整文件 / 启动逐条丢弃 / reload 一条非法即整体失败 | `ConfigJsonCodec.java:67`、`JsonConfigRepository.java:55,66`、`ConfigService.java:61` |

**B. 结构性问题**

| # | 问题 | 证据 |
|---|---|---|
| B1 | 静态可变全局状态密集：配置服务、编辑锁、快照管理、分片累加器 | `ConfigExtractorManager.java:17`、`EditSessionLockManager.java:11`、`ConfigEditSnapshotManager.java:19`、`SaveConfigChunkAccumulator.java:19` |
| B2 | 快照并非真不可变：执行路径会删规则并全量重编译 | `ItemToMobExecutor.java:19` |
| B3 | 编辑会话锁是**全局单 UUID、跨玩家跨类型**：一人编辑，所有人、所有类型被锁 | `EditSessionLockManager.java:11` |
| B4 | 网络不对称：C2S 分片，S2C 整包，上限 4MiB | `SaveConfigChunker.java:27`、`ConfigSnapshotPayload.java:15`、`ConfigEditLimits.java:12` |
| B5 | 配置实际存在 4 份数据（磁盘 JSON / 服务端快照 / 客户端快照 / GUI 模型） | 见 1.1 链路 |
| B6 | 新增一种转化类型要改约 8 处（注册表 / Config 子类 / Executor / 客户端 FormDefinition / Presenter / i18n / 文档 / 示例） | `ConversionTypeRegistry.java:29`、`BuiltinConversionTypes.java`、`ClientConversionTypeRegistry.java` |
| B7 | 前端表单直接引用后端运行时类（`server.task.ExplosionTask`、`PlaceBlockTask.BlockPlaceShape`、`ConversionLimits`） | `BuiltinConfigPresentations.java:13-14`、`BuiltinFormDefinitions.java:6,8-9` |
| B8 | 残留硬编码：inspect 命令写死 5 个旧条件名；延迟任务间隔写死 1/8/8/2 | `ConversionInspectCommand.java:28`、`Constants.java:18-21` |

**C. 已确认健康的部分（重构时保留）**

- 端隔离成立：`server/**`、`config/**`、`network/**`、`command/**` 对 client 包引用为 **0**。
- 条件系统已落地：`ConditionTypeRegistry` 7 个内置条件、DNF（组间 OR / 组内 AND / 叶级 NOT）、谓词与消耗拆分。
- 配置路径分层与旧路径兼容读取机制（ADR-0004）已存在。

---

## 二、计划目标

### 2.1 第一性目标（Q3）

1. **扩展成本**：新增一种**效果类型**或**条件类型**的改动点从约 8 处降到 **≤3 处**，且不改框架代码。
2. **消除结构性腐化**：消灭静态全局状态、可变 DTO、`validate()/resolve()/initCache()` 三段式生命周期。
3. **消灭正确性缺陷**：A1–A10 全部闭环，其中 A1（保存删数据）、A2（计时单位）、A3（热重载失效）、A4（泄漏）为硬性验收项。
4. **运行时模型**：由"20 tick 全量轮询 + 计数清零"改为"按 tick 分桶的到期调度 + 失败退避"。
5. **网络复杂度**：数据包基底交给原版同步，自研网络只保留"覆盖层合并快照下发 + 变更集保存"。
6. **配置字段**：现有字段属早期黑历史，**全部重新设计**，不保留旧字段名。

### 2.2 非目标（Q46，全选确认）

- 掉落物以外的源（其他实体 / 方块 / 玩家的生命周期事件）。
- 脚本化扩展（KubeJS / JS 内联脚本）。
- 多 MC 版本支持与多版本抽象层。
- 自动化测试工程（JUnit / GameTest）；测试仍交用户，改用游戏内自检命令。
- 旧配置的**自动**迁移；只保留显式 `/idtw config convert`。 〔已被 P8 取代，见文首〕
- 掉落物消失以外的生命周期事件（拾取、合并、合成、熔炼、死亡掉落参与转化）。

### 2.3 硬约束

- 平台隔离：`common/` 不引用平台类；端隔离：服务端不引用客户端类（现状为 0，重构后必须仍为 0）。
- 目标版本仅 **1.21.1**，Fabric + NeoForge 双端，不做多版本抽象（Q6）。
- 前端"暂时不动"= **冻结行为与视觉**，允许编译所需的机械适配（Q2）。
- 每阶段门槛：先 IDEA 无报错/警告（忽略 markdown 格式问题），再 `./gradlew build` 通过，且构建等待不短于 120 秒。

---

## 三、技术路线

### 3.1 领域模型（核心变更）

**旧**：一个转化类型 = 一个 Config 子类 = 一个 Executor = 一个结果（N 种结果 = 2N 个类）。

**新**：**规则（Rule）** 是唯一的配置单元，**效果（Effect）** 是唯一的执行单元。

```
Rule
├─ id             : ResourceLocation（稳定标识，可被覆盖/引用/日志/命令使用）
├─ enabled        : boolean（默认 true）
├─ priority       : int（默认 0）
├─ notes          : String?（纯注释）
├─ source         : SourceMatcher（item id 列表 / #tag 列表 + 排除列表）
├─ conditions     : ConditionExpression（DNF，空 = 恒真）
└─ effects        : Effect[]（有序）

Effect（通用字段）
├─ type           : ResourceLocation（效果类型，注册表键）
├─ delay_ticks    : int（默认 0，相对规则触发时刻）
├─ chance         : double（默认 1.0，所有效果通用）
└─ conditions     : ConditionExpression?（可选，效果级门槛）
```

**执行语义（Q8/Q39/Q44）**

1. 同一掉落物**只执行优先级最高的那一条规则**（priority desc → 条件叶数 desc → 定义序）；"一次多效果"由规则内的效果列表表达，不靠多条规则叠加。
2. 命中的规则内效果**顺序执行、全部执行**，不做事务回滚。
3. 单个效果抛异常 → 捕获、记录 ERROR（规则 id + 效果类型 + 位置），**继续执行后续效果**，整体转化仍视为已发生。
4. **消耗是效果**：`consume_source` / `consume_catalyst` / `consume_fluid`；规则默认隐式包含 `consume_source`，显式声明可覆盖为不消耗。
5. 延迟效果绑定"维度 + 位置"，**不要求源实体存活**；区块卸载时任务保留在队列、加载后继续；服务器重启后丢失（不持久化，Q38）。

### 3.2 配置载体与作用域（Q5/Q10/Q11/Q43）

**三层作用域，优先级 覆盖层 > 世界数据包 > 内置**：

| 层 | 位置 | 可写 | 同步方式 |
|---|---|---|---|
| ① 内置数据包 | mod jar 内 `data/<ns>/idtw/rules/**` | 否 | 随 jar，任何世界加载，作默认与示例（示例默认 `enabled: false`） |
| ② 世界数据包 | 存档 `datapacks/*/data/<ns>/idtw/rules/**` | 否 | 原版机制自动同步到客户端 |
| ③ config 覆盖层 | `config/itemdespawntowhat/rules/**` | **是（GUI 写入目标）** | 服务端权威 + 一个 S2C 合并快照包 |

- 规则文件：一个文件可放**单条规则对象或规则数组**；`id` 优先取字段，缺省由文件名推导。
- 合并：**按 id 逐条覆盖**（同 id 后者胜）；覆盖层可声明 `disabled: true` 停用或 `delete: true` 删除基底规则；不同 id 的规则叠加。
- 规则索引在每次 reload 后完整重建；**源 tag 采用懒展开**（首次匹配到该 tag 时展开并缓存，缓存随 reload 失效，Q45）。
- 旧格式兼容：**不做加载期隐式迁移**；提供 `/idtw config convert` 显式转换（转换前自动备份，并报告无法映射条目）。 〔已被 P8 取代，见文首〕

### 3.3 序列化与 Codec（Q5/Q19/Q27）

- 数据包基底与覆盖层共用**一套 Mojang DFU Codec**（`RecordCodecBuilder` / `MapCodec` + `RegistryOps`），替换现有 61 个文件的 Gson 用法与两处独立 Gson 实例。
- 类型分发：规则/效果/条件均按 `type` 字段经注册表 dispatch；资源引用（item / block / entity / fluid / biome / loot table）走原版 Codec，直接获得注册表校验与持有人解析。
- 校验策略**统一严格**（Q19）：
  - 文件解析失败 → **整个文件拒载**并报错，错误信息含来源包 + 路径 + 字段；
  - 单条规则语义非法 → **该条拒载**并报错，其余照常；
  - 启动与 `/idtw config reload` 行为一致；
  - 不做任何隐式写回，不产生 `.bak`（转换命令除外）。 〔已被 P8 取代，见文首〕

### 3.4 字段与命名规范（Q27）

- 规则级通用字段：`id / enabled / priority / notes / source / conditions / effects`。
- 效果级通用字段：`type / delay_ticks / chance / conditions`（后三者可选）。
- 全小写 snake_case；资源引用写作 `minecraft:x` 或 `#minecraft:x`。
- 时间单位：**规则触发时间用秒**（`trigger_after_seconds`，对齐原版 300 秒直觉），**效果延迟用刻**（`delay_ticks`，精确控制）；单位后缀必须出现在字段名里。

### 3.5 类型清单

**生效效果 9 个（Q16）**：`spawn_item` / `spawn_entity` / `place_block` / `spawn_xp` / `loot_table` / `lightning` / `explosion` / `arrow_rain` / `weather`。
**消耗效果 3 个**：`consume_source` / `consume_catalyst` / `consume_fluid`。
**不提供**脚本/命令逃生舱（`run_command` 等）；确有需求时再评估。

**条件类型 10 个（Q17/Q24/Q32）**：`dimension` / `biome` / `weather` / `outdoor` / `surrounding_blocks` / `catalyst_present` / `fluid_present` / `time_of_day` / `y_level` / `light_level`。

- `biome` 支持两种子模式：`exact`（群系注册名/tag）与 `climate`（原版 6 参数 temperature / humidity / continentalness / erosion / depth / weirdness，每个参数填可选区间，未填 = 不限制）。
  - 采样入口：`ServerChunkCache.randomState().sampler()`（已核实为 public，无需 Mixin）；采样点 = 掉落物所在方块位置。
  - 新增 `/idtw debug biome` 打印当前位置 6 参数，便于玩家写区间。
  - 参考：`mc_s_ana/feature-structure/12-生物群系生成与结构地物交互.md`。
- 本轮**不加** `random_chance` 与 `nearby_count` 条件（概率已由效果级 `chance` 覆盖，计数类代价高）。

**结果上限与半径（Q26）**：`limit`（半径内已有同类产物上限）与 `radius` 作为**产物类效果自身的参数**，命名与默认值统一；不支持该概念的效果不声明这两个字段。

### 3.6 注册体系与扩展 API（Q25）

- 统一注册模式：`EffectType` / `ConditionType` 各自持有 id、参数 Codec、服务端执行器/求值器工厂、客户端表单描述（client 侧以同一 id 关联，**common 不引用 client**）。
- 对第三方承诺：**Java API 可注册新的效果类型与条件类型**；数据包只承诺"使用已注册的类型"。
- 注册单元自带客户端表单描述，使 GUI 对新类型可自动生成表单或走手写逃逸（沿用 ADR-0006 字段中心架构与 ADR-0011 条件编辑架构）。

### 3.7 运行时（Q1/Q12/Q13/Q22/Q38）

**触发语义**：到期事件，时刻 = `min(物品存活达到 trigger_after_seconds, 自然消失时刻)`。原版 1.21.1 的消失判定在 `ItemEntity.tick()` 末尾 `age >= 6000 → discard()`，无"消失后"回调，因此本方案在 discard 之前判定并拦截。

**计时**：以**绝对存活时间**为准（不再用"通过条件的检查次数"）。条件不满足 → 不转化，进入**退避重试**：1s → 2s → 4s → 封顶 5s，直到自然消失。

**调度器**：按 tick 分桶的到期队列（O(1) 入队/出队）；每 tick 检查数设上限（模组配置项），超限顺延到下一 tick。

**追踪状态**：per-level 内存索引（显式清理 + 弱引用策略），不写实体 NBT；`/idtw config reload` 后**回扫已加载区块的 ItemEntity** 重建追踪；已选规则在新快照中消失 → 清空状态重新选择（不再做引用身份比较）。

**性能验收（Q22）**：1 万个被追踪掉落物时 TPS ≥ 19.5，或检查开销 < 0.5 ms/tick（两者取可达者，由用户游戏内实测）。

### 3.8 网络与编辑协议（Q14/Q40）

- 数据包基底（① ② 层）由原版机制同步，mod **不再自研基底同步**。
- config 覆盖层（③ 层）：一个 S2C **合并快照**包下发（服务端侧已是合并结果）。
- 保存协议**服务端权威**：客户端提交**变更集**（新增/修改/删除的规则 + 期望版本戳）→ 服务端校验版本 → 写覆盖层 → reload → 回执；版本冲突则拒绝并返回差异。
- 编辑会话：由"全局单 UUID 锁"改为 **per-player 会话**（不再互相锁死）。

### 3.9 GUI 视图模型与最小实现（Q2/Q29/Q35）

- 新增 **client 侧视图模型层**：GUI 只读写视图模型，映射层负责与后端规则结构互转。这是"一次适配解决 28 个文件绑定"的关键，也保证后端再变时不再牵动 GUI。
- 前端行为与视觉冻结：第一屏仍列 9 个"模板"（原转化类型的 UI 化表达），点进去 = 新建一条只含对应效果的规则。
- **最小实现边界**：模板新建 + 单效果编辑 + 现有条件编辑子屏 + 保存/删除；**多效果规则只读展示**并提示手改 JSON。彻底重构留待后续。

### 3.10 命令与自检（Q20/Q30/Q34/Q42）

全部子命令要求权限等级 ≥2（仅 OP）：

| 命令 | 作用 |
|---|---|
| `/idtw config reload` | 重载数据包与覆盖层并重建索引、回扫追踪 |
| `/idtw config edit` | 打开配置编辑 GUI |
| `/idtw config validate` | 离线体检全部来源，输出违规条目与原因 |
| `/idtw config list` | 列出规则及其来源层（内置/世界包/覆盖层）与生效状态 |
| `/idtw config convert` | 旧 config 格式一次性转换为新覆盖层格式（转换前备份，报告不可映射条目） | 〔已被 P8 取代，见文首〕
| `/idtw rule list` / `/idtw rule show <id>` | 查询规则详情与来源文件 |
| `/idtw debug inspect` | 检查视线中掉落物的追踪与匹配状态 |
| `/idtw debug why` | 输出"为什么没转化"：规则候选、条件求值逐项结果、计时/退避状态 |
| `/idtw debug stats` | 追踪数、到期队列长度、每 tick 检查耗时 |
| `/idtw debug biome` | 打印当前位置的群系与 6 个气候参数 |

**不使用 test 文件**（沿用项目约定），验收靠上述自检命令 + 用户游戏内实测。

### 3.11 模组级配置（Q28/Q41）

统一自管文件 `config/itemdespawntowhat/server.json`（两端共用同一实现，不依赖 NeoForge ModConfig）：

`check_interval_ticks` / 退避上限 / 每 tick 检查上限 / 覆盖层目录 / Fabric lifespan 兜底值 / 调试日志开关。

### 3.12 平台层（Q6/Q21）

- 平台差异收敛到 `platform/`（现有 `Services` / `IPlatformHelper` 模式），不做多版本抽象。
- Fabric 保留 2 个 Mixin 且**只做入口**（`Entity.spawnAtLocation` 拦截、死亡掉落锁定），业务逻辑一律在普通类；NeoForge 继续用事件。
- **统一语义**：两端"只锁玩家死亡掉落"；lifespan 经 platform helper 统一（Fabric 用可配置兜底常量，NeoForge 走 `getEntityLifespan`）。

### 3.13 文档体系（Q23/Q36/Q47）

| 目录 | 内容 |
|---|---|
| `docs/dev/` | ① 架构总览 ② 扩展指南（注册效果/条件类型）③ 配置参考（字段全表）④ 迁移指南（旧→新） |
| `docs/plan/` | 本规划书 |
| `docs/archive/`（gitignore） | 已过期 ADR 归档 |

- 本次会被推翻、需归档的既有 ADR：**0004**（配置路径布局）、**0008**（schema versioning）。与之相关但概念仍有效的（0006 字段中心架构、0007 条件表达式 DNF、0011 条件编辑架构）保留。
- `CONTEXT.md` 术语更新（`ConversionType` 退役为 UI 概念"模板"，新增 Rule / Effect / Template / Overlay / 到期事件），在实施阶段执行。

---

## 四、阶段划分与验收（Q37）

**总体策略（Q15）**：新建并列包 `core/` 开发，旧链路保持可编译可运行；新链路完整后**一次性切换**并删除旧包，前端在切换时做一次性机械适配 + 视图模型接入。

每阶段门槛：**先 IDEA 无报错，再 `./gradlew build` 通过**（等待不短于 120 秒，不并发构建）。

### 阶段 ① 地基 ✅（2026-10-02 收口）

- 建 `core/` 包骨架与领域模型：Rule / SourceMatcher / ConditionExpression（沿用 DNF 概念，重写实现）/ Effect 列表。
- 规则 id、文件布局、三层作用域加载与按 id 覆盖合并（含 disabled/delete）。
- 统一 Codec 基座（RegistryOps + dispatch）与统一严格校验策略。
- **验收**：build 通过；手写样例数据包可被解析为规则列表，错误用例给出含路径与字段的报错。

#### 阶段 ① 落地结果与实现约定

**已落地**（全部位于 `common/src/main/java/com/meteorite/itemdespawntowhat/core/`，共 39 个文件：api 8 / model 13 / load 13 / registry 3 / service 2）：

| 包 | 内容 |
|---|---|
| `api` | 冻结契约：TypeDefinition / TypeRegistry / RuleFields / Issue / IssueSeverity / IssueCollector / TypeDispatch（扁平类型分发）/ RuleDecoder |
| `model` | Rule / SourceMatcher / SourceEntry / Effect / EffectType / Condition / ConditionType / ConditionExpression(DNF) / CommonFields / RuleCodecs / RuleValidation / ConsumptionDefaults |
| `load` | 三层来源（RuleSourceLayer / RuleOrigin）、包层判定注入点（PackLayerResolver）、原始条目（RawRuleEntry）、文件解析（RuleFileParser）、数据包与覆盖层读取器、覆盖合并（RuleMerger）、入口（RuleLoader） |
| `registry` | SimpleTypeRegistry（冻结语义）、DuplicateTypeException、RegistryFrozenException |
| `service` | RuleLoadContext / RuleLoadingService（唯一同时依赖 load + model 的装配层） |

旧链路未改动，全量 `./gradlew build`（common + fabric + neoforge）通过；独立复核确认依赖方向零越界（core/load、core/registry 均不依赖 core/model，四包均不引用 client/fabric/neoforge）。

**阶段① 期间确定的实现约定**（补规划书未写明的边界，后续阶段必须遵守）：

1. **可空字段一律经 Optional 中转**：DFU 的 `DataResult` 内部使用 `Optional.of`，任何 codec 产出 null 都会在解码期抛 NPE（阶段① 实测踩中过）。规则级 `notes`、效果级 `conditions` 在 codec 层以 `Optional` 承载，只在记录构造的最后一步 `orElse(null)`。
2. **加载层的 JSON 边界用 Gson**：`core/load` 以 `JsonObject` 作为注入边界（为在解码前补齐推导 id、剔除 disabled/delete 控制字段），模型与运行时仍全程 DFU Codec。这是有意的边界，不是 Gson 回流。
3. **未知字段策略**：顶层未知字段记 WARN；**类型参数级**未知字段检测随阶段② 类型注册表落地（需要各类型的字段集合），阶段① 不承诺。
4. **id 推导规则**：文件内只有一条规则时可省略 id（单对象，或长度 1 的数组）；多条必须显式声明 id，否则该条拒载。
5. **覆盖层控制条目**：同一来源层内普通条目先合并、控制条目（disabled/delete）后应用，语义不依赖文件名排序；`disabled` 命中基底后规则被停用，最终来源记为覆盖层，基底内容保留。
6. **消耗语义**：`Rule#usesImplicitSourceConsumption()` 仅在规则未声明任何 `consume_*` 效果时为真；同一规则内重复声明同一消耗效果类型属 **ERROR（该条拒载）**，此严格性由阶段① 新增，第三方与内置数据包都受约束。
7. **编码未注册类型即抛异常**：`TypeDispatch` 在 encode 阶段遇到未注册类型会抛 `IllegalStateException`（而非静默只写 type 字段）。这是编程错误，但阶段④ 的 GUI 保存路径必须 try/catch 并转成用户可读错误。

**已知未验证项**：Fabric/NeoForge 真实数据包 id 与 `PackLayerResolver.byPackIdToken` 启发式未实机验证（阶段③ 用 reload 日志或 `/idtw config list` 复核）；性能指标未测；IDEA 检查在本会话不可用，以「独立 javac 编译 + 仓库外行为探针 + 全量 gradle 构建」替代。

### 阶段 ② 注册体系与内置类型 ✅（2026-10-02 收口）

**已落地**（`core/type/` 26 个文件 + `core/service/BuiltinTypeRegistries.java`）：

| 内容 | 说明 |
|---|---|
| 生效效果 9 个 | spawn_item / spawn_entity / place_block / spawn_xp / loot_table / lightning / explosion / arrow_rain / weather |
| 消耗效果 3 个 | consume_source / consume_catalyst / consume_fluid |
| 条件 10 个 | dimension / biome（exact + climate 双模式）/ weather / outdoor / surrounding_blocks / catalyst_present / fluid_present / time_of_day / y_level / light_level |
| 共用助手 | `core/type/EnumCodecs`（小写下划线枚举编解码）、`core/type/RefChecks`（TaggedId 引用存在性校验） |
| 装配点 | `BuiltinTypeRegistries.create()`：条件类型 → 表达式 codec → 效果类型（无环） |

**阶段② 期间确定的实现约定**：

1. **静态工厂不得叫 type()**：`Condition#type()` / `Effect#type()` 是无参实例方法，同签名的静态方法在 Java 中非法。约定为 `conditionType()` 与 `effectType(expressionCodec)`。
2. **所有物品/方块/实体/流体引用统一用 `TaggedId`**（支持 `#tag`）；`loot_table.loot_table` 与 `dimension.dimensions` 用纯 `ResourceLocation`（无 tag 语义）。
3. **引用存在性校验**：非标签引用未命中 → ERROR；标签引用仅在「标签数据确已绑定」时校验，未命中记 WARN。判据为「注册表存在任一非空标签」（`getTags().anyMatch(size>0)`），因为数据包未加载时 `getTagNames()` 里全是 bootstrap 期创建的空壳标签。
4. **参数校验钩子**：`TypeDefinition#validateParams` 默认通过，内置类型各自重写；`RuleValidation` 的注册表感知重载逐效果、逐条件叶调用它，问题带 `effects[i]` / `conditions[g][l]` 路径。

**阶段② 独立复核后的修复（task-13 发现）**：

| 编号 | 问题 | 处置 |
|---|---|---|
| F-1（严重） | `RuleLoadingService.loadAndValidate` 仍调用 3 参结构校验，注册表感知的 5 参重载**全仓库零调用**，导致未注册引用/区间结构类非法参数在端到端装载路径被静默放行 | 已修：装配层改用 5 参重载 |
| F-2（中等） | `RuleValidation` 的补来源逻辑**追加**带 origin 的副本而非替换，且索引空间不一致（用 ERROR 计数索引整个列表），同一问题重复 2~7 条 | 已修：改为局部收集 + 一次性补来源合并 |
| F-4（轻微） | biome climate 模式 `{"temperature":{}}` 空区间被当作有效填写 | 已修：区间至少一端有界才算有效约束 |
| F-3（中等，部分） | codec 级错误（区间越界、类型不符）不带字段名，经 loader 后 fieldPath 为空 | 已部分修：类型分发统一前缀类型 id（"类型 xx 参数错误: ..."）；**逐字段名**需各类型给 codec 包一层命名包装，记入阶段⑤ 与 `/idtw config validate` 一并处理 |
| F-5（轻微） | `radius` 默认值不统一；`loot_table` 未声明 limit/radius | 记录为有意差异：Q26 的"统一"指**命名与取值区间**（radius 1..32、limit 1..4096），默认值按类型语义决定（place_block 默认 6 用于扩散放置，spawn_item/spawn_entity 默认不限制）；loot_table 产出为一批随机物品，邻近累积检测意义有限，暂不声明 |

**阶段② 明确未做（已记录，留给后续阶段）**：

- **biome / dimension 的引用存在性未校验**：1.21.1 没有 `BuiltInRegistries.BIOME`（群系是数据包动态注册表），维度同理，`validateParams` 拿不到 `RegistryAccess`。留待阶段③ 的 `/idtw config validate`、`debug why` 用服务端 `RegistryAccess` 复核。
- **类型参数级未知字段检测仍未实现**：需要 `TypeDefinition` 暴露字段集合（各类型已备好 `*_FIELD` 常量），属阶段④ 与 GUI/校验命令一并处理，阶段① 只承诺顶层未知字段 WARN。
- **效果执行器与条件求值器全部未实现**：本阶段只交付参数模型、Codec 与参数校验，运行时在阶段③。

- `EffectType` / `ConditionType` 注册表、内置 12 个效果与 10 个条件（含 biome 双模式）。
- 效果级 `delay_ticks` / `chance` / `conditions` 通用能力。
- **验收**：build 通过；新增一个 dummy 类型只需改 1~3 处（目标的量化验证）；`/idtw config validate` 能报出非法参数。

### 阶段 ③ 运行时 ✅（2026-10-02 收口）

**落地结果**

| 位置 | 内容 |
|---|---|
| `core/runtime/`（9 文件） | TickScheduler（分桶 + 每 tick 预算 + 异常隔离）、RuleIndex（优先级排序 + tag 懒展开缓存）、ExpressionEvaluator（DNF 短路求值）、RuntimeTagLookup / RuntimeClimateSampler（按位置/标签缓存）、RuntimeConditionContext / RuntimeEffectContext、LifespanProvider、ConversionRuntime |
| `core/config/` | ServerConfig（server.json：检查间隔/退避上限/每 tick 上限/覆盖层目录/Fabric 寿命兜底/调试开关，缺失即落盘） |
| `core/type/effect/exec/`（13 文件） | 12 个效果执行器 + EffectTargets 助手 |
| `core/type/condition/eval/`（10 文件） | 10 个条件求值器 |
| `fabric/…/runtime/`、`neoforge/…/runtime/`（各 2 文件） | RuleRuntimeHost / RuleRuntimeEvents：服务端引导、事件接入、数据包重载回扫 |

**阶段③ 实现约定**：

1. 运行时按**维度 key** 组织状态，**不持有 ServerLevel 引用**（A4 内存泄漏闭环）：维度卸载 `clear`、服务端停止 `shutdown`。
2. 触发时刻 = `min(trigger_after_seconds×20, lifespan-1)`，经调度器在到期 tick 执行；条件不满足按 1s→2s→4s→封顶**退避重试**，到自然消失前一刻停止（A2 计时单位、A3 热重载回扫闭环）。
3. 效果派发由运行时统一处理 `delay_ticks` / `chance` / 效果级 `conditions`；执行器只做世界操作；单效果异常捕获记录后**继续执行后续效果**；规则选择仅用「优先级 + 条件成立」，彻底移除引用身份比较（A7 闭环）。
4. 同一掉落物只执行优先级最高的一条命中规则；转化完成后终止追踪。
5. 标签查询与气候采样由维度级缓存实例提供，缓存随索引重建（reload）一并丢弃；`byPackIdToken` 之外的标签解析全部懒展开。
6. 平台层只做入口（引导/事件/重载钩子），业务全在 `core/**`；`server.json` 缺失即落盘由 core 负责，平台层不重复处理。

**阶段③ 独立复核后的修复（task-19 发现）**：

| 编号 | 问题 | 处置 |
|---|---|---|
| S1（严重） | 规划书 3.1-4/Q8 的"规则默认隐式消耗源物品"**未实现**：`Rule#usesImplicitSourceConsumption()` 全仓库零调用，只写 `[spawn_item]` 的规则不会消耗源物品 | 已修：`ConversionRuntime.performConversion` 在派发声明效果前，若规则未声明任何 `consume_*` 则先隐式执行一次 `consume_source`（count=1）；实例由 `BuiltinTypeRegistries.implicitSourceConsumption()` 提供，运行时只依赖 `Effect` 接口 |
| M1（中等） | `RuleIndex.candidates` 在"直接命中 vs 标签命中"并列时丢失定义序，与 3.1-1 冲突 | 已修：改为先求命中集合，再按全局有序表过滤，第三条排序键恢复有效 |
| M2（中等） | `replaceRules` 未重置 `LevelState.tags/climate`，标签与气候缓存不随 reload 失效 | 已修：重建索引时一并置空两个缓存 |
| L1 | `TickScheduler` 只捕 RuntimeException，Error 逃逸会丢弃同桶剩余任务 | 已修：VM 级错误上抛，其余 Throwable 隔离并继续 |
| L2 | 退避序列固定 2^3 上限，间隔较小时不收敛到 `backoff_max_ticks` | 已修：`base << n` 后按上限封顶，封顶成为真正的收敛值 |
| L3 | 未注册条件类型 + 叶级取反会求值为 true | 已修：未注册直接判否且不参与取反 |
| L4 | 文档写 runtime 10 文件，实为 9 | 已修 |
| 观察 | `RuntimeClimateSampler` 位置缓存无上限（长赛季内存风险） | 记入阶段⑥ 清理项：按区块失效或加容量上限 |

**阶段③ 结束时的已知限制**：

- **新旧链路并行**：同一物品若同时命中旧 `config/itemdespawntowhat/<type>.json` 与新 `rules/**`，会被两条链路各转化一次 → 对照测试必须只保留一侧配置；阶段⑥ 删除旧链路后消失。
- `PackLayerResolver.byPackIdToken` 的包 id 启发式与 `listResources("idtw/rules")` 路径前缀**未实机验证**（阶段③ 当时仓库无内置数据包，启动只走 config 覆盖层；内置数据包已在阶段⑤ 落地，验证条件已具备）。
- Fabric 端 lifespan 为常量（server.json 兜底值），不反映第三方模组改动；NeoForge 端走 `getEntityLifespan`。
- 世界写入路径（setBlock / explode / 天气 / 生成实体与掉落物）只经静态检查与原版 API 契约核对，**未实机验证**。
- 阶段③ 未接入 `/idtw` 命令（属阶段⑤），手工重载走原版 `/reload`。

- per-level 索引 + 分桶到期调度器 + 退避重试 + reload 回扫 + 弱引用/显式清理。
- 到期触发拦截（原版 discard 之前）、延迟任务（维度+位置绑定）、单条最高优先级命中、效果异常隔离。
- 模组级配置 `server.json` 接入。
- **验收**：build 通过；A2/A3/A4/A7 四类缺陷在实现层闭环；TPS 指标由用户实测。

### 阶段 ④ 网络与 GUI 视图模型 ✅（2026-10-02 收口）

**落地结果**

| 位置 | 内容 |
|---|---|
| `core/network/protocol/`（3 文件） | RuleEdit / RuleEditChangeSet / RuleSnapshot：协议 JSON 文本，网络只传字符串 |
| `core/network/transport/`（10 文件） | 5 个 payload（请求快照 / 变更集 / 分片 / 快照 / 结果）+ RuleEditPayloadRouter + RuleEditChunkAccumulator + RuleEditLimits + RuleEditServerContext + RuleEditServerHandler |
| `core/service/`（3 文件） | RuleOverlayWriter（权威落盘）、EditSessionManager（per-player 会话 + 版本戳）、RuleSnapshotAssembler |
| `client/network/`（1 文件） | RuleEditorClient：冻结的客户端门面（requestSnapshot / sendChangeSet / lastSnapshot / setSnapshotListener / setResultListener） |
| `client/ui/view/`（16）+ `screen/`（3）+ 改写 ConfigTypeSelectionScreen | 视图模型层 + 最小 GUI（9 模板入口 / 单效果编辑 / 条件子屏 / 保存删除 / 多效果只读） |
| `fabric/`、`neoforge/` | 两端 payload registrar + 客户端接收器 + RuleRuntimeHost.editContext()（NeoForge 另加 public reload） |

**阶段④ 实现约定**：

1. **保存协议（服务端权威）**：客户端提交变更集（upsert/delete + expectedVersion）→ 服务端校验版本 → `RuleOverlayWriter` 按 id 落盘 → `bumpVersion` → 重建索引 + 全维度 rescan → 回执 + 新快照；**版本冲突不落盘**。
2. **A1 闭环**：写回依据是磁盘文件内容而不是运行时快照，逐 id 应用增删改；未涉及的规则、disabled 规则、非法 JSON 文件都不会被保存动作删除（已用无头探针 18/18 验证）。
3. **编辑会话**：per-player（不再是全局单 UUID 锁），并发保护改由版本戳承担。
4. **快照条目形状** `{ "rule": {...}, "origin": "...", "editable": true|false }`：覆盖层规则用**文件原始 JSON**（不丢未识别字段）且可编辑；其它来源由模型编码、只读。
5. **网络只传字符串**：payload 记录与服务端编排全在 common（`CustomPacketPayload`/`StreamCodec` 都是原版 common 类），两端 registrar 只做薄注册；服务端包对 client 包引用为 0。
6. **视图模型只认协议 JSON**（不引用 core/model）；旧 GUI 类保留但新入口不再可达。

**阶段④ 独立复核后的修复（task-24 发现）**：

| 编号 | 问题 | 处置 |
|---|---|---|
| F-1（中等） | 单条覆盖层文件省略 id 时，快照下发的 rule JSON **不含 id** → GUI 视图 id 为空、保存得到错误 id `minecraft:`，而运行时权威 id 是 `<ns>:<path>` | 已修：装配时把推导出的 id 注入下发 JSON，与 `RuleLoader` 解码前注入的行为对齐 |
| F-2（轻微） | `disabled`/`delete` 控制条目被当作可编辑规则下发，列表里出现无 source/effects 的"规则" | 已修：装配时跳过控制条目（它们不是规则，只表达对基底规则的停用/删除意图） |
| F-3（轻微） | `RuleOverlayWriter` 每次保存都重复报告磁盘上早已存在的坏 JSON，回执长期携带旧错误 | 已修：写入路径只跳过坏文件，报错交由加载链路（OverlayRuleReader）负责 |
| F-4（中等·架构） | `core/network/transport` 反向依赖 `common/platform/Services`，与同类中 `RuleEditServerContext` 的去耦目标矛盾 | 已修：`RuleEditServerContext` 新增 `sendTo(player, payload)`，发包由两端平台实现委派；core 对 `Services` 引用归零 |

**阶段④ 已知限制**：

- **legacy GUI 及其旧 DTO 绑定仍在仓库中**（BaseConfigEditScreen / ConfigListScreen / ConfigListPanel / form/BuiltinFormDefinitions / presentation / handler、client/register/ClientConversionTypeRegistry、client/network/ConfigEditClientPayloadHandler）：新入口不可达，但代码未删 → 阶段⑥ 一并清理。
- 编辑结果回执是服务端**诊断字符串**，未走 i18n key → 阶段⑤ 统一引入 `key|参数` 编码并在客户端门面本地化。
- **专用服务端连接协商未实机验证**（两端 S2C 注册口径按 NetworkRegistry/PayloadTypeRegistry 语义推得）。
- 快照 >1MB 时只提示手改 JSON，不做 S2C 分片。
- 编辑屏字段相对旧 GUI **必然变化**（Q3 全字段重设计）；"视觉冻结"指入口、布局风格与文案 key 一致，不指字段逐一相同。
- consume_* 效果无模板入口、多效果规则只读（Q35 最小实现边界）。
- 新规则 id 由模板名 + 序号生成，跨会话可能与既有 id 撞车（表单内可手改）。

- S2C 合并快照包 + 服务端权威变更集保存协议 + per-player 会话。
- client 视图模型层 + GUI 最小实现（模板新建 / 单效果编辑 / 条件子屏 / 保存删除 / 多效果只读）。
- **验收**：build 通过；GUI 交互与视觉与现状一致；保存不再删除 disabled 规则（A1 闭环）。

### 阶段 ⑤ 命令、内置数据包与文档 ✅（2026-10-02 收口）

**实机首测反馈修复（U-1）**

| 编号 | 问题 | 处置 |
|---|---|---|
| R-1（复核发现） | 堆叠不足一轮（available < perRound）时 `coveredSourceItems` 虚报为 rounds×perRound，导致 `spawn_xp.per_source_item` 多给经验 | 已修：`covered = min(rounds × perRound, available)`，与实际扣减数量一致 |
| U-1 | 用户实测：一组物品扔出去**只转化一个**，与最初设计意图不符 | 已修：恢复旧链路 `computeActualRounds` 的语义——运行时**预先**算出整堆能支持多少轮 `rounds = 堆叠数 / 每轮源物品消耗量`（不消耗源物品的规则为 1），然后**一次性**扣减 `rounds×消耗`、产出 `rounds×结果`。`EffectContext` 新增 `rounds()` 与 `coveredSourceItems()`（= rounds × 每轮消耗，供 per_source_item 类效果精确取用）；8 个效果执行器乘 rounds、4 个一次性世界效果（闪电/爆炸/箭雨/天气）不乘；消耗一律按实际可扣数量收敛并记 debug 日志 |

**阶段⑤ 已知限制**：

- **Q3 口径补充**：新增效果类型 = 3 处（类型文件 + 执行器文件 + 容器一行）；新增**可 GUI 编辑**的条件类型 = 4 处（再加 `client/ui/view/RuleConditionInputs`）；不提供 GUI 参数编辑时条件仍为 3 处且可正常加载求值。
- **Q25 承诺的第三方 Java 扩展 SPI 未交付**：内置注册表构建后立即 freeze，没有对外插入点；扩展指南已如实标注，留阶段⑥ 补。
- `/idtw config convert` 的字段级损失与整条拒载情形见迁移指南（explosion 方向/result_multiple、lightning visual_only、loot_table 掷取次数与 limit/radius、place_block search_radius 等）。 〔已被 P8 取代，见文首〕
- 命令反馈中的布尔值仍渲染为 `true/false`（未本地化为"是/否"）。
- 内置数据包 6 个示例全部 `enabled: false`（Q31：作文档与 GUI 模板来源，不默认改变世界行为）。

- 命令树（`config` / `rule` / `debug` 三组）+ 自检命令。
- 内置数据包（示例规则默认 disabled，作 GUI 模板默认参数来源）。
- `docs/dev` 四篇 + `CONTEXT.md` 术语更新 + ADR 归档与新 ADR。
- **验收**：build 通过；`/idtw config validate` 在内置数据包上零告警。

#### 阶段 ⑤ 进度

| 交付项 | 状态 | 产出 |
|---|---|---|
| `docs/dev` 四篇 | ✅ | `architecture.md`（架构与数据流）/ `extension-guide.md`（注册效果与条件类型）/ `config-reference.md`（字段全表）/ `migration-guide.md`（旧→新 + convert 用法） |
| `CONTEXT.md` 术语更新 | ✅ | `ConversionType` 退役为 UI 概念「模板」；新增 规则 / 效果 / 效果类型 / 条件叶（语义更新）/ 覆盖层 / 到期事件 / 变更集 / 版本戳 / 视图模型 等 |
| ADR 归档与新 ADR | ✅ | 0004、0008 移入 `docs/archive/`（该目录已加入 `.gitignore`）；新增 0012（规则模型与效果列表）、0013（DFU Codec 与扁平分发）、0014（三层作用域与覆盖合并）、0015（运行时调度与追踪）、0016（编辑协议） |
| 命令树 + 平台注册 | 🚧 | task-27：`core/command/`（config/rule/debug 三组 + `RuleConvertService`）与两端注册已落地，收尾中 |
| i18n：命令与编辑回执统一 `key\|参数` | 🚧 | 随命令树交付 |
| 内置数据包 | 🚧 | 6 个示例规则已落地（`data/itemdespawntowhat/idtw/rules/`，全部 `enabled: false`），待验收 |
| 类型参数级未知字段检测（阶段② F-3 遗留） | ⏳ | 需各类型暴露字段集合，随 `/idtw config validate` 处理 |

**文档整理期间如实记录的现状（不改任何技术决策）**：

1. `/idtw config convert` 已随命令树落地（`core/command/RuleConvertService`）：输入旧 `config/itemdespawntowhat/<ns>/<type>.json`，转换前备份到 `_old_chain_backup/`，输出新覆盖层 `rules/**`，无法无歧义映射的条目报告为 `unmapped`、字段级损失报告为 `notes`。迁移指南按该实现逐条核对；另记录一处**未提示**的字段损失：`item_to_block` 的 `search_radius` 既不映射也不提示（新 `place_block` 无独立的上限统计半径），已列入待复核。 〔已被 P8 取代，见文首〕
2. 第三方 Java 扩展 SPI 尚未交付。Q25 承诺"Java API 可注册新的效果类型与条件类型"，目前只有仓库内注册路径（`BuiltinEffectTypes` / `BuiltinConditionTypes` 构建后立即 `freeze()`），扩展指南已如实标注为未交付能力。

### 阶段 ⑥ 切换与清场 ✅（实现收尾，实机验收待用户执行）

| 交付项 | 当前状态 |
|---|---|
| 旧模型/IO/注册表/执行器/网络/命令/平台事件 | 已删除，唯一入口为新 RuleRuntimeHost 与命令树 |
| 旧 GUI、新最小编辑器与 DTO 绑定 | 已删除，改为本地化占位页；后端协议保留 |
| 用户磁盘旧 JSON | 原样保留且不执行，convert 成功备份才迁移，不擅自删除 | 〔已被 P8 取代，见文首〕
| B01～B17 | 实现修复见收尾报告；性能指标和世界行为不以构建替代验收 |
| 类型未知字段与动态引用校验 | Codec keys 检测 + 服务端维度/群系/已加载战利品检查 |
| 第三方 SPI | RuleTypeProvider + ServiceLoader，冻结前分阶段注册 |
| 性能与生命周期 | 双队列预算、取消任务、分批、气候 4096 项、重载保留效果、卸载清理 |
| 文档、迁移、i18n、6 个停用示例文件 | 已同步；最终静态/构建/打包结果见收尾记录 |
| 原 Q22 性能目标 | 保留，等待用户 1 万掉落物游戏压测 |
| 双平台自然消失/死亡锁/区块边界/世界效果 | 等待用户游戏验收 |

本阶段不保留旧链路回退入口，不删除用户游戏配置，不提交 Git。前端完整重构作为下一项独立工作。

---

## 五、旧 → 新 映射说明

| 旧 | 新 |
|---|---|
| `item`（单 item 或 #tag） | `source.items`（列表 + 排除项） |
| 扁平条件字段（dimension / need_outdoor / surrounding_blocks / catalyst_items / inner_fluid） | `conditions` DNF 表达式中的对应条件叶 |
| `consumption` | `effects` 中的 `consume_*` 效果 |
| `conversion_time`（秒，实际按检查次数） | `trigger_after_seconds`（绝对存活时间，秒） |
| `priority` / `enabled` / `notes` | 同名保留 |
| 9 个转化类型文件 | 单一规则文件布局（按目录/命名空间组织） |
| `result_limit` / `search_radius` | 产物类效果各自的 `limit` / `radius` |
| `schema_version` + 迁移器 + 加载期写回 | 删除；改用 `/idtw config convert` 显式转换 | 〔已被 P8 取代，见文首〕

转换命令的判定原则（Q4/Q19）：**能无歧义映射就转换，否则显式拒载并报告**，绝不静默丢弃。 〔已被 P8 取代，见文首〕

---

## 六、风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 后端模型推倒导致 client 28 个文件编译失败 | 中间态不可编译 | 采用并列 `core/` 包策略，前端只在阶段 ⑥ 一次性机械适配；切换前旧链路始终可用 |
| Codec 化改造成面广（61 个文件用 Gson） | 工期与回归风险 | 新模型从零写 Codec，不动旧 Gson 链路，切换时整体替换 |
| 三层作用域 + 覆盖合并语义复杂 | 运行时行为难预测 | `/idtw config list` 明确标注每条规则的来源层与生效状态 |
| 分桶调度在极端密度下抖动 | 性能不达标 | 每 tick 预算 + 超限顺延 + `/idtw debug stats` 可观测 |
| 群系 climate 模糊匹配采样成本 | 每检查一次采样 | 采样结果按方块位置缓存；条件求值短路 |
| 效果异常被吞导致玩家困惑 | 效果静默失效 | 捕获即 ERROR（规则 id + 效果类型 + 位置）；`/idtw debug why` 可复核 |
| GUI 最小实现表达不了多效果 | 能力对 GUI 用户不可见 | 多效果规则只读展示 + 提示手改 JSON；阶段 ⑥ 后单独立项彻底重构 |
| 旧 ADR/README 与新语义冲突 | 误导后续开发 | 归档过期 ADR 并写新 ADR；README 在切换阶段同步更新 |

---

## 七、验收标准

1. `./gradlew build` 在 common / Fabric / NeoForge 三处通过（每阶段各验一次）。
2. A1–A10 全部闭环；其中 A1/A2/A3/A4 必须有可复现的前后对照步骤。
3. 新增一种效果类型或条件类型 ≤3 处改动，且不改框架代码。
4. 数据包（内置/世界包）+ config 覆盖层三层优先级与 disabled/delete 语义按本文档生效。
5. `/idtw config validate`、`debug why`、`debug stats`、`debug biome` 可用。
6. 1 万被追踪掉落物场景下 TPS ≥ 19.5 或检查开销 < 0.5 ms/tick（用户实测）。
7. 端隔离：服务端→客户端引用为 0；平台类不出现在 `common/`。
8. GUI 行为与视觉相对现状无可观察变化（多效果只读展示除外）。
9. i18n：新增 GUI 文案同步 `en_us.json` 与 `zh_cn.json`。

---

## 附录 A：决策台账（grilling 会话 47 项）

| # | 决策 |
|---|---|
| Q1 | 到期触发；时刻 = min(规则计时, 自然消失时刻)；效果自带消耗语义 |
| Q2 | 前端冻结行为与视觉，允许机械适配 + client 视图模型层 |
| Q3 | 动因：扩展成本 + 结构性腐化 + 正确性缺陷 + 网络复杂度 + 运行时模型；旧字段全部重设计 |
| Q4 | 保留领域概念、重写全部实现；旧 JSON 能映射则转换，否则拒载 | 〔已被 P8 取代，见文首〕
| Q5 | 数据包只读基底 + config 可写覆盖层 + 同一 Codec |
| Q6 | 仅 1.21.1，Fabric/NeoForge 双端，不做多版本抽象 |
| Q7 | 规则 = 源匹配 + 条件 + 有序效果列表 |
| Q8 | 效果顺序全执行；效果可带条件与延迟；消耗为内置效果 |
| Q9 | 后端取消 ConversionType；前端保留"模板"占位 |
| Q10 | id = ResourceLocation；文件可单条或数组；`data/<ns>/idtw/rules/**` |
| Q11 | 按 id 逐条覆盖；disabled/delete；不同 id 叠加 |
| Q12 | 绝对存活时间 + 退避重试 |
| Q13 | per-level 内存索引 + reload 回扫已加载区块 |
| Q14 | 基底原版同步 + 覆盖层 S2C 合并快照 + per-player 会话 + 乐观并发 |
| Q15 | 新并列 `core/` 包开发，最后一次性切换 |
| Q16 | 9 生效效果 + 3 消耗效果；无脚本逃生舱 |
| Q17 | 重设计现有 7 个条件并新增 |
| Q18 | 源 = 多 item/tag 列表 + 排除列表；不做组件级匹配 |
| Q19 | 统一严格校验；显式 `/idtw config convert` | 〔已被 P8 取代，见文首〕
| Q20 | 以 `/idtw` 为根按功能类群分子命令；仅 OP |
| Q21 | Fabric 保留 2 个 Mixin 仅作入口；两端统一语义；lifespan 平台 helper |
| Q22 | 按 tick 分桶调度；退避 1s→2s→4s→5s 封顶；TPS ≥19.5 或 <0.5ms/tick |
| Q23 | 新建 docs/dev、docs/plan、docs/archive（gitignore） |
| Q24 | 条件最终 10 个；biome 双模式；不加 random_chance / nearby_count |
| Q25 | 承诺 Java 扩展 API；数据包只用已注册类型 |
| Q26 | `limit` / `radius` 作为效果自身参数 |
| Q27 | 通用字段与 snake_case 命名规范；规则用秒、效果延迟用刻 |
| Q28 | 引入模组级服务端配置 |
| Q29 | client 视图模型层；GUI 先最小实现 |
| Q30 | `/idtw config|rule|debug` 三组子命令 |
| Q31 | 内置 builtin 数据包，示例默认 disabled |
| Q32 | biome `exact|climate`；6 参数区间；`debug biome` 命令 |
| Q33 | 效果级通用 `chance` |
| Q34 | `/idtw config convert` | 〔已被 P8 取代，见文首〕
| Q35 | GUI 最小实现边界：单效果编辑 + 多效果只读 |
| Q36 | docs/dev 四篇清单 |
| Q37 | 六阶段骨架 |
| Q38 | 延迟任务绑定维度+位置；卸载保留；重启不持久化 |
| Q39 | 单条最高优先级命中；快照失效则重选 |
| Q40 | 服务端权威变更集 + 版本戳 |
| Q41 | 统一 `config/itemdespawntowhat/server.json` |
| Q42 | 游戏内自检命令，不新增 test 文件 |
| Q43 | 三层作用域：内置数据包 / 世界数据包 / config 覆盖层 |
| Q44 | 效果异常捕获并继续 |
| Q45 | tag 懒展开 + 缓存随 reload 失效 |
| Q46 | 六条非目标确认 |
| Q47 | 本轮交付：规划书（现状 / 目标 / 技术路线），执行时再细分 |
