# 项目概述

> 本文是抽象层文档的起点，只交代项目定位、边界与术语，不涉及类与方法。
> 架构与模块分层见 [architecture.md](architecture.md)；文档阅读协议见 [README.md](README.md) §2。

## 1. 一句话定位

ItemDespawnToWhat（mod id `itemdespawntowhat`）是一个 Minecraft 1.21.1 的 Fabric / NeoForge 双端服务端模组：
让**即将自然消失**、或被**火 / 岩浆 / 仙人掌销毁**的掉落物，按声明式**规则**转化为其他物品、实体、方块、经验或世界效果。

模组的核心不是"硬编码若干转化"，而是提供一套**规则引擎 + 规则编辑能力**：
规则可来自数据包与 config，可在游戏内通过独占编辑会话用图形界面维护，也可直接用命令与 JSON 管理。

## 2. 基础环境

| 项 | 取值 |
|---|---|
| Minecraft | 1.21.1（版本区间 `[1.21.1, 1.22)`） |
| 加载器 | Fabric（Loader 0.16.9、Fabric API 0.109.0+1.21.1）；NeoForge 构建版本为 21.1.218，元数据声明的依赖区间为 `[21.1.210,)` |
| 语言与工具链 | Java 21；Gradle 多模块工程 |
| 工程模块 | `common`（平台无关实现）、`fabric`、`neoforge`（平台接入） |
| 映射 | Mojang 官方映射（构建期附加 Parchment 参数名） |
| 规则命名空间 | 内置与默认覆盖层使用 `itemdespawntowhat`，第三方类型与规则可使用自有命名空间 |

## 3. 系统边界

**系统提供的**：规则的数据形态与校验、三层规则来源、规则执行运行时（追踪 / 排期 / 条件求值 / 完整组结算）、
规则编辑协议与图形编辑器、命令入口、开发环境调试场景与性能观测。

**系统当前明确不提供的**（避免把边界当缺陷）：

- 不自动迁移旧版本配置：旧格式一律**明确报错、不静默兼容**。
- 不为规则执行**主动加载区块**；区块不可用时退避重试，不生成新区块。
- 条件求值**不修改世界**：副作用只发生在效果执行阶段。
- 不提供跨加载器统一的 Mixin 方案：两端各自使用各自的原生事件与最小 Mixin。
- 不提供图形编辑器以外的规则配置入口保证：命令与 JSON 是稳定入口，但界面承担主要交互。

**对外稳定面**（第三方与集成方可以稳定依赖）：

| 面 | 说明 |
|---|---|
| 规则数据格式 | 数据包内的规则目录与 config 覆盖层目录；字段契约见 [layers/server-core.md](layers/server-core.md) 与 [backend/modules/rule-model.md](backend/modules/rule-model.md) |
| 类型注册 SPI | 以服务发现登记的规则类型提供者；约束见 [layers/server-core.md](layers/server-core.md) §4 |
| 命令 | `/idtw` 命令树与其权限口径 |
| 模组级配置 | 固定的模组级配置文件位置与分组语义 |
| 客户端 UI kit | 声明的公开边界（尚未拆包发布；公开面变更须同步 `internals/ui-kit/` 与边界检查脚本） |
| 候选目录协议 | 类别枚举与条目字段约定的冻结形状 |

**不构成对外面**（随实现变动）：内核内部包结构与非契约类型、窄接口的具体实现、平台接入层的内部细节、客户端控件层与屏幕层的内部结构、UI kit 的「内部实现」成员。

## 4. 领域术语

领域术语统一维护在仓库根目录的 [GLOSSARY.md](../../GLOSSARY.md)；本节不再另行列表，避免同一术语出现两处定义。


## 5. 两类读者

- **人类开发者**：按 [README.md](README.md) 的阅读顺序建立全局认知，再按任务跳转。
- **AI agent**：先读本文件 + [architecture.md](architecture.md)，再按 [README.md](README.md) 的「任务 → 文档」路由读
  [layers/](layers/) 与 [flows.md](flows.md)；改代码前先核对 [backend/README.md](backend/README.md) §4 的全局铁律。

## 6. 子系统地图

| 子系统 | 主要职责 | 抽象层文档 |
|---|---|---|
| 契约与模型 | 规则数据形态、条件树、编解码组装、语义校验的对外契约 | [layers/server-core.md](layers/server-core.md) |
| 类型与扩展 | 效果 / 条件类型的注册、冻结、分发与内置实现；第三方 SPI | [layers/server-core.md](layers/server-core.md) |
| 加载与装配 | 三层来源读取、合并、解码、校验、候选索引构建 | [layers/server-core.md](layers/server-core.md) |
| 运行时与调度 | 追踪、排期、条件求值、完整组结算与返还、共享预算调度、实体状态 | [layers/server-core.md](layers/server-core.md) |
| 候选目录与编辑协议 | 候选枚举、编辑快照、变更集、独占会话、权威落盘 | [layers/authoring.md](layers/authoring.md) |
| 命令与调试 | 命令树与权限口径；开发环境场景、流水线、性能观测 | [layers/authoring.md](layers/authoring.md) |
| 客户端编辑 | 协议工作区、草稿与撤销、类型编辑器注册表、四页界面 | [layers/client.md](layers/client.md) |
| 平台接入 | 引导与生命周期、平台抽象、事件与 Mixin、配置分类 | [layers/platform.md](layers/platform.md) |
