# 架构总览与数据流

> 目标读者：想读代码或做二次开发的维护者。
> 本文描述 `core/**` 新链路（阶段①～④ 已落地）。旧链路（`config/**`、`server/conversion/**`、旧网络与旧 GUI）仍在仓库中并行运行，阶段⑥ 一次性删除。

## 1. 一句话架构

**一条规则 = 源匹配 + 条件表达式 + 有序效果列表**；规则来自三层作用域（内置数据包 / 世界数据包 / config 覆盖层），按 id 覆盖合并后解码为不可变模型，运行时按"到期事件"调度，命中优先级最高的一条规则并顺序执行其全部效果。

## 2. 包分层与依赖方向

```
                        ┌──────────────────────────────┐
                        │  client/**（视图模型 + 最小 GUI） │
                        └──────────────┬───────────────┘
                                       │ 只认协议 JSON，不引用 core/model
┌──────────────┐   ┌───────────────┐   ▼
│  core/api    │◄──│  core/model   │  ┌──────────────────┐
│ 冻结契约      │   │ 领域模型+Codec │  │ core/network      │
└──────┬───────┘   └──────┬────────┘  │ protocol+transport│
       │                  │           └────────┬─────────┘
       │           ┌──────┴───────┐            │
       ├───────────│  core/load   │            │
       │           │ 三层来源+合并 │            │
       │           └──────┬───────┘            │
       │                  │                    │
       │           ┌──────▼────────────────────▼──────┐
       └──────────►│           core/service            │
                   │ 装配：加载+校验 / 覆盖层落盘 / 快照 │
                   └──────┬──────────────┬─────────────┘
                          │              │
                 ┌────────▼───────┐  ┌───▼──────────────┐
                 │  core/registry │  │  core/type        │
                 │ 类型注册表实现  │  │ 12 效果 + 10 条件  │
                 └────────────────┘  └───┬──────────────┘
                                         │
                            ┌────────────▼─────────────┐
                            │       core/runtime        │
                            │ 索引/调度/追踪/回扫/退避   │
                            └────────────┬─────────────┘
                                         │ 事件与生命周期
                            ┌────────────▼─────────────┐
                            │ fabric/** · neoforge/**  │
                            │ 平台入口（仅引导与转发）    │
                            └──────────────────────────┘
```

**依赖硬约束**（每次阶段验收复核）：

| 约束 | 含义 |
|---|---|
| 平台隔离 | `common/**` 不引用 `net.fabricmc.*` / `net.neoforged.*`；平台差异只在 `fabric/**`、`neoforge/**` |
| 端隔离 | 服务端路径对 `client/**` 引用为 0；`core/**` 整体不引用 `client/**` |
| load/model 解耦 | `core/load` 不依赖 `core/model`（解码实现经 `core/api/RuleDecoder` 注入） |
| 唯一装配层 | 只有 `core/service` 同时依赖 `core/load` 与 `core/model` |

## 3. 包职责一览

| 包 | 职责 | 代表类 |
|---|---|---|
| `core/api` | 冻结契约与通用工具：字段名常量、问题上报、扁平类型分发、参数校验助手、引用（`TaggedId`） | `RuleFields`、`IssueCollector`、`TypeDispatch`、`ParamChecks`、`RuleDecoder` |
| `core/model` | 不可变领域模型与 DFU Codec | `Rule`、`SourceMatcher`、`ConditionExpression`、`Effect`、`RuleCodecs`、`RuleValidation` |
| `core/load` | 三层来源读取、文件解析、按 id 覆盖合并 | `RuleLoader`、`DatapackRuleReader`、`OverlayRuleReader`、`RuleMerger` |
| `core/registry` | 类型注册表实现（注册期可变 → `freeze()` 后只读） | `SimpleTypeRegistry` |
| `core/type` | 内置 12 个效果类型与 10 个条件类型的参数模型、Codec、参数校验、执行器/求值器 | `BuiltinEffectTypes`、`SpawnItemEffect`、`BiomeCondition` |
| `core/service` | 装配：加载+校验、覆盖层权威落盘、编辑会话与版本戳、S2C 快照装配 | `RuleLoadingService`、`RuleOverlayWriter`、`EditSessionManager`、`RuleSnapshotAssembler` |
| `core/runtime` | 规则索引、按 tick 分桶调度、per-level 追踪、回扫、退避 | `ConversionRuntime`、`RuleIndex`、`TickScheduler` |
| `core/config` | 模组级服务端配置 `config/itemdespawntowhat/server.json` | `ServerConfig` |
| `core/network` | 编辑协议：protocol（JSON 文本形状）+ transport（payload、分片、服务端编排） | `RuleEditChangeSet`、`RuleSnapshot`、`RuleEditServerHandler` |
| `client/ui/view` | GUI 视图模型层：只解析协议 JSON | `RuleView`、`RuleTemplates`、`EffectParams` |
| `fabric`/`neoforge` | 引导、事件接入、payload 注册、数据包重载钩子 | `RuleRuntimeHost`、`RuleRuntimeEvents` |

## 4. 配置数据流（启动与 reload）

```
① 内置数据包   data/<ns>/idtw/rules/**/*.json   （mod jar 内，只读）
② 世界数据包   存档 datapacks/*/data/<ns>/idtw/rules/**/*.json （只读，原版同步）
③ config 覆盖层 config/itemdespawntowhat/rules/**/*.json     （可写，GUI 保存目标）
        │
        ├─ ①②：ResourceManager.listResources 枚举位置 → 逐位置 getResourceStack 取回**所有包**的副本
        │      （同名的不同包副本全部参与合并，不会被高优先级包遮蔽）
        └─ ③：Files.walk 覆盖层 rules 目录，按绝对路径排序
        ▼
   RuleFileParser：单对象或数组 → RawRuleEntry（id 可空 / 原始 JsonObject / 来源 / disabled / delete / fieldPath）
        ▼
   RuleMerger：跨层 覆盖层 > 世界数据包 > 内置；同层内**普通条目先合并、控制条目后应用**
        ▼
   RuleDecoder（core/model 注入）：补齐推导 id、剔除 disabled/delete、被停用条目注入 enabled=false → Rule
        ▼
   RuleValidation：结构校验 + 类型专属参数校验，非法即该条拒载（其余照常）
        ▼
   RuleIndex：剔除不可运行规则（disabled / 无效果），按 优先级 desc → 条件叶数 desc → 定义序 排序
        ▼
   ConversionRuntime.replaceRules + 各维度 rescan
```

**错误策略（统一严格，Q19）**：

| 场景 | 处理 |
|---|---|
| 文件不是合法 JSON / 顶层不是对象或数组 | 整文件拒载，ERROR 带来源与路径 |
| 单条规则 id 非法、控制字段非法、同文件 id 重复 | 该条拒载，ERROR 带来源与字段路径，其余照常 |
| 引用未注册（非标签） | 该条拒载（ERROR） |
| 标签未定义 | 仅在标签数据确已绑定时 WARN |
| 未注册的效果/条件类型 | 该条拒载（ERROR） |

## 5. 运行时数据流（到期事件）

```
ItemEntity 进入世界 / 区块加载 / reload 回扫
        │  ConversionRuntime.onItemAdded：查 RuleIndex 候选，有候选才追踪
        ▼
   计算首次到期 tick = now + min(trigger_after_seconds × 20, lifespan - 1)
        │  TickScheduler（按 tick 分桶，O(1) 入队/出队，每 tick 预算上限，超限顺延 +1 tick）
        ▼
   到期：条件求值（DNF 短路）→ 命中即执行**优先级最高的一条规则**
        │  未命中：退避重试 1s → 2s → 4s → 封顶 backoff_max_ticks，直到自然消失前停止
        ▼
   规则未声明任何 consume_* 效果 → 先执行一次隐式 consume_source(count=1)
        │
        ▼
   效果列表顺序执行（全部执行，不回滚）
        · delay_ticks：交给调度器再排期
        · chance：按概率跳过
        · 效果级 conditions：不满足则跳过该效果
        · 单个效果异常：捕获记录 ERROR，继续执行后续效果
```

**状态归属**：运行时状态按**维度 key** 组织（`Map<ResourceKey<Level>, LevelState>`），**不持有 `ServerLevel` 引用**；维度卸载 `clear`、服务端停止 `shutdown`、reload 时全维度 `rescan` 重建追踪。

**缓存**：物品标签懒展开并缓存；气候采样按方块位置缓存；两者都随 `replaceRules` 重建索引时整体丢弃。

## 6. 编辑数据流（阶段④）

```
客户端打开编辑界面
   │  C2S RequestRuleSnapshot
   ▼
服务端 RuleEditServerHandler
   · 权限校验（OP，等级 ≥ 2）
   · EditSessionManager.open(player)          ← per-player 会话，5 分钟空闲超时
   · RuleSnapshotAssembler.assemble(...)      ← 生成 { rule, origin, editable } 条目
   ▼
S2C RuleSnapshotPayload（version + 条目 + issues）
   │  客户端 RuleView 解析协议 JSON → GUI 编辑 → 构造变更集
   ▼
C2S SaveRuleChangeSet（直发或分片）  { expected_version, edits:[{id, action, rule}] }
   ▼
服务端：
   权限 → 会话有效 → 版本戳匹配 ──不匹配──► 拒绝整批，回冲突说明 + 最新快照（**不落盘**）
                                    │匹配
                                    ▼
   RuleOverlayWriter.apply：按磁盘文件内容逐 id upsert/delete（原子写 + .bak 备份）
                                    ▼
   EditSessionManager.bumpVersion → 重建索引 → 全维度 rescan → 回执 + 新快照
```

关键点：

- **写回依据是磁盘文件内容，不是运行时快照**——未涉及的规则、disabled 规则、非法 JSON 文件都不会被保存动作删除（A1 闭环）。
- **覆盖层规则以文件原始 JSON 下发**（不做模型往返，不丢未识别字段）且 `editable=true`；内置/世界数据包规则由模型编码、`editable=false`。
- **网络只传字符串**：payload 与服务端编排全在 common，两端 registrar 只做薄注册。

## 7. 平台层职责

| 平台 | 入口 |
|---|---|
| Fabric | `RuleRuntimeHost`（服务端生命周期与引导）、`RuleRuntimeEvents`（实体加入/区块加载/维度卸载/tick）、2 个 Mixin 仅作入口、`RuleEditPayloadRegistrar` / `FabricRuleEditClientRegistrar` 注册 payload |
| NeoForge | 同职责，改用事件订阅（`RegisterCommandsEvent` 等），另提供 `RuleRuntimeHost.editContext()` 与 public reload |

平台层**不含业务逻辑**：规则加载、索引、调度、执行、落盘全部在 `common/core/**`。

## 8. 当前状态与已知边界

| 项 | 状态 |
|---|---|
| `core/**` 新链路（①～④） | 已落地 |
| 旧链路 `config/**`、旧网络、旧 GUI DTO | 仍在仓库中并行运行，阶段⑥ 删除 |
| `/idtw` 命令树（`config` / `rule` / `debug`） | 阶段⑤ 代码已落地（`core/command/`），任务收尾中；旧 `ConversionConfigCommand` 仍在注册 |
| 内置数据包（示例规则） | 已落地：`data/itemdespawntowhat/idtw/rules/` 6 个示例，全部 `enabled: false` |
| 新旧并行期注意 | 同一物品若同时命中旧配置与新规则，会被两条链路各转化一次；对照测试只保留一侧配置 |
| Fabric lifespan | 走 `server.json` 的 `fabric_lifespan_fallback_ticks` 兜底值；NeoForge 走 `getEntityLifespan` |
| 实机未验证项 | `PackLayerResolver.byPackIdToken` 的真实包 id 启发式；世界写入路径 |

## 9. 相关文档

- 扩展新类型：[extension-guide.md](extension-guide.md)
- 字段全表：[config-reference.md](config-reference.md)
- 旧配置迁移：[migration-guide.md](migration-guide.md)
- 技术决策：[../plan/plan-backend-rewrite.md](../plan/plan-backend-rewrite.md) 与 [../adr/](../adr/)
