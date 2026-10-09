# 开发者文档索引（`docs/dev`）

> 本目录是本项目的开发者文档根。文档面向两类读者：**人类开发者**与 **AI agent**。
> 事实来源：仓库源码。文档与源码不一致时以源码为准，并回改文档。
> 阅读入口唯一，即本文件；新增文档必须登记到下面两张目录表中。

## 1. 文档分两层

| 层 | 位置 | 写什么 | 什么时候看 |
|---|---|---|---|
| **抽象层** | 本目录根与 [`layers/`](layers/) | 分层、模块职责、接口契约、交互流程、扩展点与约束 | 建立全局认知、判断改动的落点、确认约束 |
| **实现层** | [`backend/`](backend/)、[`debug/`](debug/)、[`internals/`](internals/) | 包与类清单、方法级行为、逐项字段与常量、排查细节 | 已确定落点，需要看具体类与方法 |

抽象层文档不含代码片段、函数体、方法实现与逐项配置解释；实现层文档承担这些细节，两者不重复叙述同一事实。

## 2. 阅读顺序

**新读者 / AI agent 首次进入本项目**，按此顺序建立认知：

1. 本索引（`README.md`）
2. [overview.md](overview.md) — 项目定位、边界与领域术语
3. [architecture.md](architecture.md) — 分层模型、**架构关系图**、依赖方向与数据流
4. [layers/server-core.md](layers/server-core.md) — 服务端内核（契约 / 模型 / 类型 / 加载 / 运行时与调度）
5. [layers/authoring.md](layers/authoring.md) — 候选目录、编辑协议、命令与调试
6. [layers/client.md](layers/client.md) — 客户端编辑工作区、界面与 UI kit
7. [layers/platform.md](layers/platform.md) — 平台接入、引导与生命周期、配置
8. [flows.md](flows.md) — 四条端到端链路
9. [conventions.md](conventions.md) — 硬约束、扩展点路由、验证流程与文档维护

**按任务直达**：

| 任务 | 先读 |
|---|---|
| 理解整体架构 / 找某功能的所在层 | [architecture.md](architecture.md) → [layers/](layers/) |
| 加效果 / 条件 / 规则字段 | [conventions.md](conventions.md) §2 → [layers/server-core.md](layers/server-core.md) |
| 改编辑器界面 / 加字段行 | [layers/client.md](layers/client.md) → [internals/editor-pages-contract.md](internals/editor-pages-contract.md) |
| 改编辑协议 / 落盘策略 | [layers/authoring.md](layers/authoring.md) → [backend/flows/edit-save-protocol.md](backend/flows/edit-save-protocol.md) |
| 改调度 / 性能预算 | [layers/server-core.md](layers/server-core.md) §7 → [backend/systems/scheduling-budget.md](backend/systems/scheduling-budget.md) |
| 加平台能力 / Mixin | [layers/platform.md](layers/platform.md) → [backend/modules/platform.md](backend/modules/platform.md) |
| 做开发环境实机验证 | [debug/debug-validation-guide.md](debug/debug-validation-guide.md) |
| 提交前跑检查与构建 | [conventions.md](conventions.md) §4 |

## 3. 抽象层文档目录

| 文档 | 适用范围 |
|---|---|
| [overview.md](overview.md) | 项目定位、平台与版本环境、系统边界（做什么 / 不做什么）、领域术语表、两类读者的读法 |
| [architecture.md](architecture.md) | 五组十层的分层模型、**Mermaid 架构关系图**、依赖方向铁律、三条数据流（规则流 / 掉落物流 / 编辑流）、结构性特征 |
| [layers/server-core.md](layers/server-core.md) | 服务端内核：契约层、模型层、类型与扩展层（含第三方 SPI）、加载与装配层、运行时层（追踪 / 结算 / 实体状态）、调度与共享预算、索引与缓存、内核扩展点 |
| [layers/authoring.md](layers/authoring.md) | 创作与管控：候选目录两段式分工、编辑协议与会话（快照三视图、变更集、独占会话、分片限额、权威落盘）、命令树与权限口径、调试层与场景、该层扩展点 |
| [layers/client.md](layers/client.md) | 客户端：协议工作区与开屏入口、草稿与撤销、类型编辑器注册表与回退、UI kit 边界与输入 / 捕获 / 操作生命周期、主题与控件适配、四页界面契约、按键、已知限制 |
| [layers/platform.md](layers/platform.md) | 平台接入：三种注入方式（平台能力契约 / 窄接口 / 静态 sink）、引导与生命周期、事件与 Mixin 原则、两端差异摘要、网络与协议注册、模组级配置分组 |
| [flows.md](flows.md) | 四条端到端链路：规则加载、转化生命周期、编辑保存协议、调试场景；每条含流程图、关键契约与失败边界 |
| [conventions.md](conventions.md) | 20 条硬约束、扩展点路由表、可依赖与不可依赖的对外面、交付前验证流程、AI agent 使用约定、文档维护约定 |

## 4. 实现层文档目录

### 4.1 后端（[`backend/`](backend/)）

| 文档 | 适用范围 |
|---|---|
| [backend/README.md](backend/README.md) | 后端文档地图：功能模块 / 横向系统 / 纵向系统三种视图、包到文档的路由、全局铁律、新增功能时改哪里的定位表 |
| [backend/modules/rule-model.md](backend/modules/rule-model.md) | 契约层与模型层类清单、规则 JSON 字段全表、条件树 JSON 形状、编解码与两档校验、字段与上限常量 |
| [backend/modules/type-system.md](backend/modules/type-system.md) | 注册表与 SPI 实现、10 个内置条件与 10 个内置效果的参数全表、新增类型的步骤 |
| [backend/modules/rule-loading.md](backend/modules/rule-loading.md) | 加载流水线的类清单、三层作用域与合并语义、文件形状与 id 推导、动态引用校验范围 |
| [backend/modules/conversion-runtime.md](backend/modules/conversion-runtime.md) | 运行时类清单、追踪与触发、到期判定、完整组结算与数量账目、持久化与恢复、实体状态 |
| [backend/modules/edit-protocol.md](backend/modules/edit-protocol.md) | 协议数据模型与全部载荷清单、快照形状、独占会话、服务端处理流程、限额常量、权威落盘 |
| [backend/modules/command.md](backend/modules/command.md) | `/idtw` 命令树与权限、子命令到后端调用的映射、已退役命令的历史记录 |
| [backend/modules/debug.md](backend/modules/debug.md) | 调试层类清单、场景如何复用真实链路、命令清单、生命周期与清理、约束与踩坑 |
| [backend/modules/platform.md](backend/modules/platform.md) | 平台隔离层、生命周期持有者方法表、两端事件映射表、Mixin 注入清单、网络与客户端差异、掉落物状态适配 |
| [backend/systems/type-registry-dispatch.md](backend/systems/type-registry-dispatch.md) | 注册表冻结语义、装配顺序、扁平类型分发与递归条件树编解码、严格度差异 |
| [backend/systems/issue-validation.md](backend/systems/issue-validation.md) | 问题模型、拒载粒度、两档校验、字段路径约定 |
| [backend/systems/scheduling-budget.md](backend/systems/scheduling-budget.md) | 任务模型、每 tick 双上限、公平轮转与到期搬运、取消原因、观测面 |
| [backend/systems/caching-indexing.md](backend/systems/caching-indexing.md) | 候选索引排序键、缓存整体失效、标签与气候缓存、静态预计算、候选目录两段式、有界性总览 |
| [backend/systems/platform-abstraction.md](backend/systems/platform-abstraction.md) | 三条隔离铁律、平台能力契约、窄接口、静态 sink、事件即调用时机 |
| [backend/systems/config.md](backend/systems/config.md) | 模组级配置文件的加载与落盘、全部字段表、派生换算、退避算法 |
| [backend/systems/catalyst-threshold-projection.md](backend/systems/catalyst-threshold-projection.md) | 催化剂门槛的声明与有效数量、缺省门槛解析、投影时机、效果条件重建 |
| [backend/flows/rule-loading-flow.md](backend/flows/rule-loading-flow.md) | 规则加载链路的触发时机、主流程、分步证据与失败边界 |
| [backend/flows/conversion-lifecycle.md](backend/flows/conversion-lifecycle.md) | 转化生命周期的分步细节、数量账目、一个可核对的例子 |
| [backend/flows/edit-save-protocol.md](backend/flows/edit-save-protocol.md) | 打开编辑器、确认与快照、目录分页、提交变更集、关键不变量、版本戳推进时机 |
| [backend/flows/debug-scenario-flow.md](backend/flows/debug-scenario-flow.md) | 调试场景链路的全景、分步、隔离机制与结果清理语义 |

### 4.2 调试与 UI（[`debug/`](debug/) · [`internals/`](internals/)）

| 文档 | 适用范围 |
|---|---|
| [debug/debug-validation-guide.md](debug/debug-validation-guide.md) | 在 IDE 中开始调试、一条命令创建功能场景、场景控制与清理验收、实时日志校对、性能优化前后对比方案 |
| [debug/debug-test-pipeline.md](debug/debug-test-pipeline.md) | 开发环境实机测试流水线：一键运行与 P0~P4 各阶段的目标、命令与判定 |
| [debug/debug-scenario-extension.md](debug/debug-scenario-extension.md) | 调试场景统一技术路线与扩展方式：新增普通场景、参数与动作、统一断言、性能与生命周期边界、新增自动阶段 |
| [debug/debug-settlement-examples.md](debug/debug-settlement-examples.md) | 开发环境结算示例与日志读法、与内置数据包的边界 |
| [internals/ui-kit-api.md](internals/ui-kit-api.md) | UI kit 的公开面、依赖边界与检查脚本、输入路由与捕获生命周期、数值策略与滑块接入、样式注入、实机预览 API、鼠标释放归属、码点长度上限 |
| [internals/editor-pages-contract.md](internals/editor-pages-contract.md) | 编辑器四页职责、存在条件的唯一入口、同一份叶扫描与编号、输入与触发按确切叶路径关联、物品与数量联动、撤销与保存语义、管理页布局 |
| [internals/editor-card-layout.md](internals/editor-card-layout.md) | 四阶段编辑页的卡片布局与视觉规格：卡片分组、间距与高度、目录控件、黑名单选择、数量控件与预览尺寸 |

## 5. 权威性与维护

- **分层的权威范围**：架构、职责与约束以抽象层文档为准；类名、方法行为、字段与常量的逐项事实以实现层文档为准；两者冲突时以源码为准。
- **单一来源**：类清单只在实现层维护；抽象层只描述契约与职责，不复述类清单。
- **决策背景**：设计取舍与替代方案见 [`docs/adr/`](../adr/)；本目录文档只链接、不复述。
- **术语**：领域术语见 [overview.md](overview.md) §4；项目级词汇记录见仓库根目录的 `CONTEXT.md`。
- **新增文档**：先判断属于抽象层还是实现层，再扩写现有文档或新开文件，并登记到本文档第 3、4 节。
