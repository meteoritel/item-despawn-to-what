# 架构总览与数据流

> 2026-10-02 后端切换状态：`core/**` 是唯一规则执行链路，旧模型、执行器、网络和 GUI 已删除。前端目前为占位页，编辑协议保留供下一轮前端使用。

## 1. 分层与依赖

一条规则由源匹配、DNF 条件与有序效果列表组成。模型不可变；世界操作在服务端主线程执行。

| 包 | 职责与代表类 |
|---|---|
| `core/api` | 通用契约、问题收集、类型分发：`TypeRegistry`、`RuleDecoder`、`EffectContext` |
| `core/model` | 不可变规则与 Codec：`Rule`、`SourceMatcher`、`RuleCodecs`、`RuleValidation` |
| `core/load` | 三层读取、按 id 合并：`DatapackRuleReader`、`OverlayRuleReader`、`RuleLoader` |
| `core/registry` | 注册期可变、冻结后只读的类型注册表 |
| `core/extension` | 依赖模型与通用 API 的第三方 `RuleTypeProvider` SPI |
| `core/type` | 12 个内置效果、10 个条件及其执行器/求值器 |
| `core/service` | 加载装配、动态引用校验、写盘、快照与版本管理 |
| `core/runtime` | 候选索引、检查/效果双队列、追踪与有界缓存 |
| `core/config` | `server.json` 性能与运维参数 |
| `core/network` | 原版 payload、编辑变更集、分片与服务端处理流程 |
| `client` | 快捷键和 `RuleEditorPlaceholderScreen` 占位页 |
| `fabric` / `neoforge` | 平台事件、生命周期、命令与 payload 注册 |

约束：common 不引用 Fabric/NeoForge 类；core 和服务端平台路径不引用客户端；load 不依赖 model，通过 `RuleDecoder` 注入解码；service 负责装配 load 与 model。第三方 SPI 位于 extension，避免 api 反向依赖 model。

## 2. 规则加载

```text
内置/世界数据包 data/<ns>/idtw/rules/**/*.json
  → listResources 定位资源 → getResourceStack 读取全部包副本
config 覆盖层 config/itemdespawntowhat/rules/**/*.json
  → 按路径顺序只读扫描
  → RuleFileParser → RuleMerger → RuleCodecs
  → RuleValidation + RuleReferenceValidator
  → RuleIndex → replaceRules → 全维度 rescan
```

三层优先级为覆盖层 > 世界数据包 > 内置数据包；同层资源包按原版低到高顺序处理，后者覆盖前者。Fabric 按实际 mod id 识别内置包，NeoForge 按 `mod/` 包前缀识别。普通条目先合并，控制条目后应用。

单个坏文件/坏规则拒载并报告，其它条目继续加载；枚举资源或覆盖层目录失败属于加载失败，重载保留上一版索引。类型专属未知字段拒绝，规则顶层未知字段告警。引用校验包含物品、实体、方块、流体、当前维度/群系和已加载战利品表。无 MinecraftServer 的离线装配只能做静态校验。

Fabric 在数据包重载成功后加载规则；NeoForge 使用全局 `OnDatapackSyncEvent`，在新资源与标签切换后加载，忽略单玩家登录同步。成功重载推进编辑版本。

## 3. 转化生命周期

```text
实体加入/区块加载 → 候选索引（含 exclude）→ 仅有候选者追踪
  → 首次排期 min(候选规则秒数 × 20, lifespan - 1) - 当前年龄
  → 检查队列到期 → 逐规则年龄门槛 + DNF 条件 → 选一条
  → 未命中：退避 20/40/80/100 tick（默认），兼顾下一条规则到期
  → 命中：标记已提交，复制源物品快照，提交效果队列
```

自然消失入口只预留最后一次预算内检查，防止预算积压越过原版 discard。未追踪物品仍走原版消失。NeoForge 用 `ItemExpireEvent`；Fabric Mixin 仅拦截自然消失分支，业务留在 runtime。

两端仅锁玩家死亡掉落；普通生物掉落可转化。无限寿命物品与已提交转化的源实体跳过；已提交标记随实体标签保存，重载和区块重进不会让剩余源物品重复提交。源实体离开时取消检查任务，直接释放其捕获对象。

规则未声明任何 `consume_*` 时每轮默认消耗 1；有源消耗时 `rounds=max(1, available/perRound)`，实际覆盖数量以 available 收敛。没有源消耗时为 1 轮。源数量不足整轮仍执行 1 轮，催化剂与结果上限不做事务预检，详见配置参考。

## 4. 调度与性能

检查与效果使用独立 `TickScheduler`。未来任务按 tick 分桶，逾期任务进入 ready 队列；零延迟任务能在运行中继续排入当前队列。积压保留游标，不复制剩余任务。每个维度、每类队列分别执行 `max_checks_per_tick` 次访问及 2ms 软时间预算，先达到者停止。

大数量生成、战利品多轮掷取与方块遍历分批推进；查询达到限额可提前终止。方块形状偏移预计算缓存，气候按 quart 坐标缓存且最多 4096 项。标签缓存随重载清空。普通 tick 不再全量 prune，只有启动/重载回扫世界实体。

软预算无法中断一个原版原子调用：单次爆炸、单轮复杂战利品、实体 AABB 查询与催化剂排序仍可能超过预算。爆炸威力最大 16；性能指标由游戏压测验收，不能从构建结果推导。

## 5. 效果与区块

效果按列表顺序启动；delay、chance 与效果条件在实际启动时求值。分批效果可在后续 tick 交错完成，不提供整条规则事务回滚。源物品快照不因先消耗而丢失。

所有世界操作使用已加载区块门禁；生成实体还检查实际落点。未加载时任务等待 20 tick 后重试，不主动加载区块。效果绑定维度与位置，源物品消失后仍可执行。

规则重载只重建检查，不清空已提交效果；单个区块卸载保留效果。维度卸载/服务器停止释放检查、效果与缓存；未完成效果不跨重启持久化。方块 limit 为作业启动时统计的局部额度，并行作业与外部改动下不保证全局硬上限。

## 6. 保存协议

前端占位页不发编辑请求；服务端协议为后续编辑器保留：

```text
请求快照 → 权限 → 磁盘修订核对 → 打开 per-player 会话 → 原始覆盖层 JSON 快照
提交变更集 → 权限/会话 → 磁盘修订/版本 → 完整 Codec + 语义 + 动态引用校验
  → RuleOverlayWriter 整批预备、备份、逐文件替换
  → 成功重载（推进版本、重建检查）→ 回执 + 新快照
```

写盘基于磁盘原始内容，保留未编辑条目、非对象数组成员与未知字段。重复编辑、同 id 多处定义、目标被坏文件占用、路径越界或符号链接均拒绝。单条省略 id 的数组与对象按读取器相同规则推导。

编辑版本持久化在 `.edit_version`；成功 reload/convert/save 均推进。请求与保存前用 rules 目录原始字节指纹检测手工修改。会话与未完成分片定时过期，停服全部释放。快照最大 1,000,000 字节，不做 S2C 分片。

全部内容与备份预备后才提交。普通 I/O 失败尝试恢复已写文件；回滚失败明确报错、记录日志并保留 .bak。单文件原子替换不等于多文件崩溃事务；外部程序无协作锁时也不能保证全目录线性化。

## 7. 平台入口

Fabric：原生实体加入/卸载、维度与服务端事件；三个 Mixin 分别作为 spawnAtLocation 玩家死亡标记、Player.drop 死亡标记及 ItemEntity 自然消失入口。

NeoForge：原生实体加入/离开、LivingDropsEvent、ItemExpireEvent、维度与服务端事件，无新增 Mixin。lifespan 使用实体当前 `lifespan`；Fabric 使用配置兜底值，并以实际自然 discard 入口保证最终检查。

## 8. 相关文档

- [配置参考](config-reference.md)、[迁移指南](migration-guide.md)、[扩展 SPI](extension-guide.md)
- [当前切换契约 ADR-0017](../adr/0017-backend-cutover-and-budgeted-effects.md)
- [收尾记录与双平台游戏验收](../review/backend-rewrite-closeout-2026-10-02.md)
