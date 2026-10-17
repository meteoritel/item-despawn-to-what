# 开发者文档索引（`docs/dev`）

> 本目录是开发者文档根，按**两个层级**组织：抽象层回答「整体架构是什么」，执行层回答「这个模块具体怎么运作」。
> 事实来源：仓库源码。文档与源码不一致时以源码为准，并回改文档。
> 入口唯一，即本文件；新增文档必须登记到第 3、4 节的目录表。

## 1. 两个层级

| 层 | 位置 | 读者 | 写什么 | 怎么读 |
|---|---|---|---|---|
| **抽象层** | 本目录根与 [`layers/`](layers/) | 开发者、AI agent | 架构分层、模块职责与边界、核心概念、组件关系、依赖方向、关键流程与硬约束 | 可以一次读完；用于建立全局认知、判断改动落点 |
| **执行层** | [`backend/`](backend/)、[`internals/`](internals/) | 开发者 | 包与类清单、方法级行为、字段与常量、配置项、逐项契约与操作步骤 | **按需单篇查阅**：先用路由表定位一篇，禁止一次性全量读取 |

抽象层不含类清单、方法实现、逐项字段与配置解释；执行层承担这些细节。同一事实只在执行层维护，抽象层只引用不复述。

## 2. 阅读协议

### 2.1 建立架构认知（读抽象层）

按序读完第 3 节的抽象层文档即可建立全局认知，不需要读源码：

[README.md](README.md) → [overview.md](overview.md) → [architecture.md](architecture.md) → [layers/server-core.md](layers/server-core.md) → [layers/authoring.md](layers/authoring.md) → [layers/client.md](layers/client.md) → [layers/platform.md](layers/platform.md) → [flows.md](flows.md) → [conventions.md](conventions.md) → [code-style.md](code-style.md)

### 2.2 执行改动（按需读执行层）

1. 用下面的路由表定位**一篇**目标文档，只打开那一篇；
2. 需要相邻主题时沿文末「相关」继续，不要按目录批量打开；
3. 后端类与方法：先读 [backend/README.md](backend/README.md) 的「包 → 文档」路由；
4. 客户端前端：先读 [internals/README.md](internals/README.md) 的模块索引与「任务 → 文档」路由。

### 2.3 任务 → 文档路由

| 任务 | 先读 |
|---|---|
| 理解整体架构 / 找某功能的所在层 | [architecture.md](architecture.md) → [layers/](layers/) |
| 加效果 / 条件 / 规则字段 | [layers/server-core.md](layers/server-core.md) → [backend/modules/type-system.md](backend/modules/type-system.md) 或 [backend/modules/rule-model.md](backend/modules/rule-model.md) |
| 改规则加载 / 来源合并 | [backend/modules/rule-loading.md](backend/modules/rule-loading.md) |
| 改转化运行时 / 结算 | [backend/modules/conversion-runtime.md](backend/modules/conversion-runtime.md) → [backend/flows/conversion-lifecycle.md](backend/flows/conversion-lifecycle.md) |
| 改编辑器界面 / 加字段行 | [layers/client.md](layers/client.md) → [internals/ui-screen.md](internals/ui-screen.md) → [internals/ui-screen-form.md](internals/ui-screen-form.md) |
| 改客户端草稿 / 撤销 / 类型编辑器注册表 | [internals/client-edit.md](internals/client-edit.md) |
| 改编辑协议工作区 / 开屏与快照 | [internals/client-net.md](internals/client-net.md) |
| 改编辑协议 / 落盘策略 | [layers/authoring.md](layers/authoring.md) → [backend/flows/edit-save-protocol.md](backend/flows/edit-save-protocol.md) |
| 改调度 / 性能预算 | [layers/server-core.md](layers/server-core.md) §7 → [backend/systems/scheduling-budget.md](backend/systems/scheduling-budget.md) |
| 改模组级配置 | [backend/systems/config.md](backend/systems/config.md) |
| 加平台能力 / Mixin | [layers/platform.md](layers/platform.md) → [backend/modules/platform.md](backend/modules/platform.md) |
| 加 / 改命令 | [backend/modules/command.md](backend/modules/command.md) |
| 调试命令与开发场景 | [backend/modules/debug.md](backend/modules/debug.md) |
| 使用 / 改造 UI kit | [internals/ui-kit/README.md](internals/ui-kit/README.md) |
| 改平台客户端接入 / 客户端 tick 心跳 | [internals/client-entry.md](internals/client-entry.md) |
| 改主题 / 展示名称 / 目录图标 | [internals/ui-theme.md](internals/ui-theme.md)、[internals/ui-shared.md](internals/ui-shared.md) |
| 提交前跑检查与构建 | [conventions.md](conventions.md) §4（校验脚本与退出码）；构建用 `tools/dsh-build.ps1` |

## 3. 抽象层文档目录

| 文档 | 适用范围 |
|---|---|
| [overview.md](overview.md) | 项目定位、平台与版本环境、系统边界（做什么 / 不做什么）、术语入口（指向根 `GLOSSARY.md`） |
| [architecture.md](architecture.md) | 分层模型、架构关系图、依赖方向铁律、三条数据流、结构性特征 |
| [layers/server-core.md](layers/server-core.md) | 服务端内核：契约层、模型层、类型与扩展层（含第三方 SPI）、加载与装配层、运行时层（追踪 / 结算 / 实体状态）、调度与共享预算、索引与缓存 |
| [layers/authoring.md](layers/authoring.md) | 创作与管控：候选目录两段式分工、编辑协议与会话（快照生效视图与按需来源视图、变更集、独占会话、分片限额、权威落盘）、命令树与权限口径、调试层与场景 |
| [layers/client.md](layers/client.md) | 客户端：协议工作区与开屏入口、草稿与撤销、类型编辑器注册表与回退、UI kit 边界与输入生命周期、主题与控件适配、四页界面契约、已知限制 |
| [layers/platform.md](layers/platform.md) | 平台接入：三种注入方式、引导与生命周期、事件与 Mixin 原则、两端差异摘要、网络与协议注册、模组级配置分组 |
| [flows.md](flows.md) | 四条端到端链路概览：规则加载、转化生命周期、编辑保存协议、调试场景 |
| [conventions.md](conventions.md) | 文档层级与职责、阅读协议、文档维护约定、校验脚本 |
| [code-style.md](code-style.md) | 代码风格：注释与 JavaDoc 的判定标准、魔法字符串与魔法数字的提取口径 |

## 4. 执行层文档目录

### 4.1 后端（[`backend/`](backend/)）

先读 [backend/README.md](backend/README.md)：它提供功能模块 / 横向系统 / 纵向系统三种视图与「包 → 文档」路由。

| 文档 | 适用范围 |
|---|---|
| [backend/modules/rule-model.md](backend/modules/rule-model.md) | 契约层与模型层类清单、规则 JSON 字段全表、条件树 JSON 形状、编解码与两档校验、字段与上限常量 |
| [backend/modules/type-system.md](backend/modules/type-system.md) | 注册表与 SPI 实现、内置条件与效果参数全表、新增类型的步骤 |
| [backend/modules/rule-loading.md](backend/modules/rule-loading.md) | 加载流水线类清单、三层作用域与合并语义、文件形状与 id 推导、动态引用校验范围 |
| [backend/modules/conversion-runtime.md](backend/modules/conversion-runtime.md) | 运行时类清单、追踪与触发、到期判定、完整组结算与数量账目、持久化与恢复、实体状态 |
| [backend/modules/edit-protocol.md](backend/modules/edit-protocol.md) | 协议数据模型与载荷清单、快照形状、独占会话、服务端处理流程、限额常量、权威落盘 |
| [backend/modules/command.md](backend/modules/command.md) | `/idtw` 命令树与权限、子命令到后端调用的映射、扩展点 |
| [backend/modules/debug.md](backend/modules/debug.md) | 调试层类清单、场景如何复用真实链路、命令清单、生命周期与清理、约束与踩坑 |
| [backend/modules/platform.md](backend/modules/platform.md) | 平台隔离层、生命周期持有者方法表、两端事件映射表、Mixin 注入清单、网络与客户端差异、掉落物状态适配 |
| [backend/systems/type-registry-dispatch.md](backend/systems/type-registry-dispatch.md) | 注册表冻结语义、装配顺序、扁平类型分发与递归条件树编解码、严格度差异 |
| [backend/systems/issue-validation.md](backend/systems/issue-validation.md) | 问题模型、拒载粒度、两档校验、字段路径约定、提交前校验 |
| [backend/systems/scheduling-budget.md](backend/systems/scheduling-budget.md) | 任务模型、每 tick 双上限、公平轮转与到期搬运、取消原因、观测面 |
| [backend/systems/caching-indexing.md](backend/systems/caching-indexing.md) | 候选索引排序键、缓存整体失效、标签与气候缓存、静态预计算、候选目录两段式、有界性总览 |
| [backend/systems/platform-abstraction.md](backend/systems/platform-abstraction.md) | 三条隔离铁律、平台能力契约、窄接口、静态 sink、事件即调用时机 |
| [backend/systems/config.md](backend/systems/config.md) | 模组级配置文件的加载与落盘、全部字段表、派生换算、退避算法 |
| [backend/systems/catalyst-threshold-projection.md](backend/systems/catalyst-threshold-projection.md) | 催化剂门槛的声明与有效数量、缺省门槛解析、投影时机、效果条件重建 |
| [backend/flows/rule-loading-flow.md](backend/flows/rule-loading-flow.md) | 规则加载链路的触发时机、主流程、分步证据与失败边界 |
| [backend/flows/conversion-lifecycle.md](backend/flows/conversion-lifecycle.md) | 转化生命周期的分步细节、数量账目、一个可核对的例子 |
| [backend/flows/edit-save-protocol.md](backend/flows/edit-save-protocol.md) | 打开编辑器、确认与快照、目录分页、提交变更集、关键不变量、版本戳推进时机 |
| [backend/flows/debug-scenario-flow.md](backend/flows/debug-scenario-flow.md) | 调试场景链路的全景、分步、隔离机制与结果清理语义 |

### 4.2 客户端前端（[`internals/`](internals/)）

先读 [internals/README.md](internals/README.md)：它提供层级、模块索引、任务路由与统一命名口径。

| 文档 | 适用范围 |
|---|---|
| [internals/client-entry.md](internals/client-entry.md) | 入口层：Fabric/NeoForge 客户端接入、客户端 tick 心跳与接收器注册 |
| [internals/client-net.md](internals/client-net.md) | 接口层：协议工作区状态机、上行载荷、下行缓存、快照分片、界面注册槽 |
| [internals/client-edit.md](internals/client-edit.md) | 状态/数据层：规则草稿与撤销栈、输入缓冲、类型编辑器注册表、条件树编解码、草稿持久化 |
| [internals/ui-screen.md](internals/ui-screen.md) | 页面层：规则编辑器屏幕、列表/编辑/调试模式页、四页页签、保存遮罩、校验、展示视图 |
| [internals/ui-screen-form.md](internals/ui-screen-form.md) | 页面层：表单视图、字段控件、候选值选择、叶面板、条件树控件与条件树画布页 |
| [internals/ui-widget.md](internals/ui-widget.md) | 组件层：按钮、输入框、列表、树、弹窗、目录选择页、条件树画布组件 |
| [internals/ui-theme.md](internals/ui-theme.md) | 主题层：调色板、像素主题、扁平皮肤、宿主主题入口 |
| [internals/ui-shared.md](internals/ui-shared.md) | 展示工具层：展示名称、目录图标、字段目录映射、条件摘要文本 |
| [internals/ui-prototype.md](internals/ui-prototype.md) | 原型层：开发用原型界面与开发环境入口 |
| [internals/gui-screenshot-api-findings.md](internals/gui-screenshot-api-findings.md) | GUI 截图相关 API 核查：无世界渲染、资源加载遮罩、截图写盘时序、构建接入与现有 Screen 依赖 |
| [internals/gui-screenshot-tool.md](internals/gui-screenshot-tool.md) | NeoForge 开发截图工具：隔离 Source Set、启动参数、fixture、PNG 与结果文件、运行限制 |
| [internals/ui-kit/README.md](internals/ui-kit/README.md) | UI kit 模块入口：依赖边界与检查脚本、公开面、分册索引、类清单路由 |
| [internals/ui-kit/document-and-controls.md](internals/ui-kit/document-and-controls.md) | 文档排版、布局、控件、样式、几何、图标、浮层、滚动、绘制层级 |
| [internals/ui-kit/input-routing.md](internals/ui-kit/input-routing.md) | 输入路由顺序、输入上下文与目标契约、捕获与单值操作生命周期、焦点与导航、历史派发 |
| [internals/ui-kit/value-controls.md](internals/ui-kit/value-controls.md) | 数值策略与显示窗口、标量/区间/周期滑块核心、样式注入 |
| [internals/ui-kit/text-controls.md](internals/ui-kit/text-controls.md) | 文本局部历史、文本选择、历史快捷键、文本测量与悬停滚动 |
| [internals/ui-kit/tree-editing.md](internals/ui-kit/tree-editing.md) | 不可变树编辑、路径与编解码、移动捕获、树布局与画布原语 |
| [internals/ui-kit/rendering-and-preview.md](internals/ui-kit/rendering-and-preview.md) | 实体与方块预览、模型适框、灯箱与图片视图、加载动画、图标轮播 |

## 5. 权威性与维护

- **入口唯一**：新增文档必须登记到第 3、4 节。
- 文档层级与职责、权威范围、单一来源、链接规则与校验方式见 [conventions.md](conventions.md)。
