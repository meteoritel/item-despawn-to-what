# 后端开发者文档地图

> 范围：**Minecraft 1.21.1 / Fabric + NeoForge 的后端**——`common` 的 `core/**` 与两端平台接入层。
> 不含前端编辑 UI（当前为占位页，下一轮重构）。
> 目的：**知道技术路线、能找到类、知道改哪里**。所有结论都能在仓库中逐个核对。

## 1. 三层视图：同一个后端，三种问题

后端按三种视角分别成文，各回答一类问题；**类清单只在功能模块里维护**，横向与纵向系统通过链接引用，不重复抄写。

| 视图 | 目录 | 回答的问题 | 什么时候看 |
|---|---|---|---|
| **功能模块** | [modules/](modules/) | 这个模块是什么、由哪些类组成、对外契约、扩展点 | 找类、读某段逻辑、加新功能 |
| **横向系统** | [systems/](systems/) | 跨模块的机制怎么运作（注册、校验、调度、缓存、平台、配置） | 改机制、理解某条铁律为什么存在 |
| **纵向系统** | [flows/](flows/) | 端到端链路怎么走（一次加载/一次转化/一次保存） | 追调用链、排查跨层问题 |

包的定位：`core` 是**唯一的规则执行链路**（旧链路已删除，见 [归档索引](../../archive-index.md)）。`client` 只有占位页与快捷键，不在此范围内。

## 2. 目录

### 功能模块（按 `core` 包划分）

| 文档 | 覆盖的包 / 类 | 一句话 |
|---|---|---|
| [rule-model.md](modules/rule-model.md) | `core/api`、`core/model` | 契约层与规则模型：Rule / 源匹配 / DNF 条件 / 效果 + Codec 与校验入口 |
| [type-system.md](modules/type-system.md) | `core/registry`、`core/extension`、`core/type` | 类型注册表、第三方 SPI，以及 12 个内置效果 + 10 个内置条件 |
| [rule-loading.md](modules/rule-loading.md) | `core/load`、`core/service`（装配部分） | 三层来源读取 → 合并 → 解码 → 校验 → 索引 |
| [conversion-runtime.md](modules/conversion-runtime.md) | `core/runtime` | 追踪、排期、条件求值、效果派发、双队列调度 |
| [edit-protocol.md](modules/edit-protocol.md) | `core/network`、`core/service`（写入/会话） | 编辑快照、变更集、版本戳、权威落盘 |
| [command.md](modules/command.md) | `core/command` | `/idtw` 命令树与旧配置转换服务 |
| [debug.md](modules/debug.md) | `core/debug` | 开发场景、真实后端观测、性能窗口 |
| [platform.md](modules/platform.md) | `core/config`、`platform`、`fabric`、`neoforge` | 引导与生命周期、事件入口、Mixin、两端差异 |

### 横向系统

| 文档 | 一句话 |
|---|---|
| [type-registry-dispatch.md](systems/type-registry-dispatch.md) | 类型注册的冻结语义与**扁平类型分发**（字段不嵌套 `value`） |
| [issue-validation.md](systems/issue-validation.md) | Issue 问题模型、结构/参数两档校验、动态引用校验、拒载粒度 |
| [scheduling-budget.md](systems/scheduling-budget.md) | 分桶调度、检查/效果双队列、`max_checks_per_tick` 与 2ms 软预算、指数退避 |
| [caching-indexing.md](systems/caching-indexing.md) | 候选规则索引、标签/气候缓存、方块偏移预计算与失效策略 |
| [platform-abstraction.md](systems/platform-abstraction.md) | `Services`/`IPlatformHelper`、窄接口与静态 sink、平台隔离铁律 |
| [config.md](systems/config.md) | `server.json` 全字段、加载/落盘、退避算法 |

### 纵向系统

| 文档 | 一句话 |
|---|---|
| [rule-loading-flow.md](flows/rule-loading-flow.md) | 数据包/覆盖层 → 规则索引（含 reload 与失败保留旧索引） |
| [conversion-lifecycle.md](flows/conversion-lifecycle.md) | 实体加入 → 排期 → 到期 → 选规则 → 派发效果 → 清理 |
| [edit-save-protocol.md](flows/edit-save-protocol.md) | 打开编辑器 → 请求快照 → 提交变更集 → 校验 → 写盘 → 重载 |
| [debug-scenario-flow.md](flows/debug-scenario-flow.md) | `/idtw debug` 命令 → 场景准备 → 观测 → 测量 → 清理 |

相关外部文档：[Debug 实机验证与反馈指南](../debug-validation-guide.md)（在 `docs/dev/`）、[ADR 目录](../../adr/)、[领域词汇表](../../../CONTEXT.md)。

## 3. 按包 → 文档 路由

| 包 | 主文档 |
|---|---|
| `core/api` | [rule-model.md](modules/rule-model.md) + [type-registry-dispatch.md](systems/type-registry-dispatch.md) + [issue-validation.md](systems/issue-validation.md) |
| `core/model` | [rule-model.md](modules/rule-model.md) |
| `core/registry` | [type-system.md](modules/type-system.md)、[type-registry-dispatch.md](systems/type-registry-dispatch.md) |
| `core/extension` | [type-system.md](modules/type-system.md) |
| `core/type` | [type-system.md](modules/type-system.md) |
| `core/load` | [rule-loading.md](modules/rule-loading.md) |
| `core/service` | [rule-loading.md](modules/rule-loading.md)（装配）+ [edit-protocol.md](modules/edit-protocol.md)（写入/会话） |
| `core/runtime` | [conversion-runtime.md](modules/conversion-runtime.md) |
| `core/network` | [edit-protocol.md](modules/edit-protocol.md) |
| `core/command` | [command.md](modules/command.md) |
| `core/debug` | [debug.md](modules/debug.md) |
| `core/config` | [config.md](systems/config.md) + [platform.md](modules/platform.md) |
| `Constants.java`、`platform/` | [platform-abstraction.md](systems/platform-abstraction.md)、[platform.md](modules/platform.md) |
| `fabric/`、`neoforge/` | [platform.md](modules/platform.md) |

## 4. 全局铁律（改代码前先读）

1. **平台隔离**：`common/` 不 import 任何 Fabric/NeoForge 类；平台专属代码只能在 `fabric/`、`neoforge/`。见 [platform-abstraction.md](systems/platform-abstraction.md)。
2. **端隔离**：服务端不得引用客户端类；core 通过窄接口与静态 sink 与 client 解耦。
3. **模型不可变**：`Rule`/`SourceMatcher`/`ConditionExpression` 等均为 record，构造器防御性拷贝；"修改"即构造新对象。
4. **条件求值是纯谓词**：`ConditionEvaluator` 只读 `ConditionContext`，不得修改世界；副作用只在 `EffectExecutor`。
5. **注册表注册期可变、冻结后只读**：`freeze()` 之后注册抛 `RegistryFrozenException`，重复 id 抛 `DuplicateTypeException`。
6. **Codec 不得产出 null**：DFU 的 `DataResult` 内部用 `Optional.of`，null 会在解码期 NPE；可空字段用 `Optional` 承载、末步 `orElse(null)`。
7. **加载层与模型层单向依赖**：`core/load` 不 import `core/model`，模型经 `RuleDecoder<T>` 注入；`RuleLoadingService` 是唯一跨层装配点。
8. **解码器异常不吞掉**：完整堆栈进日志，Issue 保留可定位信息。
9. **失败保留旧索引**：数据包重载/写盘失败不得让异常逃逸到事件回调，必须保留上一版规则继续服务。
10. **同一掉落物只执行一条命中规则**；规则内效果**按序启动、非事务、不回滚**。

## 5. 新增功能时改哪里（防止文档膨胀）

| 你要做的事 | 改动代码 | 需要更新的文档（只动一处） |
|---|---|---|
| 新增一种效果/条件类型 | `core/type/effect|condition/**` + `Builtin*Types` 一行注册 | [type-system.md](modules/type-system.md) 的内置类型表与扩展步骤 |
| 新增规则字段 | `core/api/RuleFields` + `core/model/RuleCodecs` | [rule-model.md](modules/rule-model.md) 字段表 |
| 新增 `core` 子包 | 新包 + 装配点 | 新建 `modules/<新模块>.md` + 本文件 §2/§3 加一行 |
| 新增跨模块机制 | 机制类 | 新建/扩写 `systems/*.md` 一篇 |
| 新增端到端链路 | 链路实现 | 新建 `flows/*.md` 一篇 |
| 做出一个架构决策 | 代码 | 写一篇 `docs/adr/NNNN-*.md`；模块文档只链接，不复述 |

**维护约定**：模块文档随包走，只记录"类 → 职责 → 关键方法 → 扩展点"，不复制源码、不写实现细节；机制与链路各自独立成篇。这样新增功能时只增一小节或一篇新文件，原文不会无限膨胀。改动类名/方法名时，按上表定位唯一一篇更新。

## 6. 事实来源

每篇文档头部标注它的事实来源类。核对顺序：文档 → 源码；两者不一致时以源码为准，并回来修正文档。决策背景看 ADR，术语看 [CONTEXT.md](../../../CONTEXT.md)。
