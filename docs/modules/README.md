---
title: 模块文档索引
layer: 3
audience: 需要定位实现位置或修改某个子系统的开发者
authority: settings.gradle、common/core/**、common/client/**、common/platform/**、fabric/**、neoforge/**
---

# 模块文档索引

## TL;DR

- 层 3 的“模块”是按职责划分的逻辑模块，与 Gradle 子项目不是一一对应。
- 每个模块一篇 `<module>.md`，只回答边界、关键类、核心流程与扩展点；默认读者是人类开发者，agent 在任务命中该模块时读。
- 跨模块链路看 [关键链路](../architecture/key-flows.md)，契约细节看 [docs/api](../api/README.md)。

## 适用对象

已经读完 [架构总览](../architecture/overview.md)，准备深入某个子系统、新增条件或效果、修改编辑协议或界面的开发者。

## 逻辑模块与代码位置

Gradle 侧只有 `common`、`fabric`、`neoforge` 三个子项目；层 3 的模块是 `common` 内部的职责切分，加上两端的平台接入。一个包只归属一个逻辑模块，跨模块协作通过 [core-contract](core-contract.md) 中的契约完成。

| 模块 | 一句话职责 | 覆盖的包与目录 |
|---|---|---|
| [platform](platform.md) | 两端加载器接入、生命周期、平台能力注入与开发工具 | `common/platform`、`common/core/host`、`common/core/config`、`fabric/`、`neoforge/` |
| [core-contract](core-contract.md) | 跨模块共享的契约：条件与效果执行、类型注册与分发、Issue、预算与观测 | `common/core/api`、`common/core/api/scheduler` |
| [rule-model](rule-model.md) | 规则的数据形态、不可变约束、Codec 与语义校验 | `common/core/model` |
| [type-system](type-system.md) | 注册与冻结、第三方 SPI、内置条件与效果的落点 | `common/core/registry`、`common/core/extension`、`common/core/type` |
| [rule-loading](rule-loading.md) | 三层来源读取与合并、拒载、候选索引与催化剂投影 | `common/core/load`、`common/core/service` 的加载装配部分 |
| [conversion-runtime](conversion-runtime.md) | 追踪与触发、预检查、完整组结算、恢复与共享预算 | `common/core/runtime`、`common/core/runtime/scheduler`、`common/core/state` |
| [authoring](authoring.md) | 候选目录、编辑会话与落盘、命令与调试场景 | `common/core/catalog`、`common/core/network`、`common/core/service` 的编辑落盘部分、`common/core/command`、`common/src/dev`、`neoforge/src/probe` |
| [client-editor](client-editor.md) | 客户端协议工作区、草稿与撤销、类型编辑器注册表 | `common/client/net`、`common/client/edit` |
| [ui-kit](ui-kit.md) | 声明式 UI kit 的公开面与依赖边界 | `common/client/ui/kit` |
| [client-ui](client-ui.md) | 编辑器界面、主题与控件适配 | `common/client/ui/theme`、`common/client/ui/widget`、`common/client/ui/shared`、`common/client/ui/screen`、`common/client/ui/screen/form` |

`RuleIndex` 与 `CatalystThresholdProjection` 的实现在 `common/core/runtime`：rule-loading 负责它们的构建入口与顺序，conversion-runtime 负责持有索引与查询，逐包归属见 [包职责](../architecture/package-responsibility.md)。

## 怎么读

模块文档在任务落在该模块内部实现时读对应一篇；判断落点先回[架构总览](../architecture/overview.md)与[模块地图](../architecture/module-map.md)。

1. 改契约或跨模块共享类型：读 [core-contract](core-contract.md)，再看使用它的模块。
2. 改规则字段或校验：读 [rule-model](rule-model.md)，字段语义查 [规则 JSON](../api/rule-json.md)。
3. 新增条件或效果：读 [type-system](type-system.md)。
4. 改转化行为：读 [conversion-runtime](conversion-runtime.md)，跨模块顺序查 [关键链路](../architecture/key-flows.md)。
5. 改编辑器：按 [authoring](authoring.md) → [client-editor](client-editor.md) → [ui-kit](ui-kit.md) → [client-ui](client-ui.md) 的顺序读。

模块文档的固定骨架与写作规则见 [文档体系](../README.md)。

## 相关文档

- [架构总览](../architecture/overview.md)
- [模块地图](../architecture/module-map.md)
- [包职责](../architecture/package-responsibility.md)
- [关键链路](../architecture/key-flows.md)
- [文档体系](../README.md)
