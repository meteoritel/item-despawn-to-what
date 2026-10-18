---
title: 文档体系
layer: 1
audience: 所有维护本仓库文档的开发者
authority: tools/check-docs.py
---

# 文档体系

## TL;DR

- 文档按 5 层组织，每层只回答自己那一类问题。
- 同一事实只有一个权威落点，其他文档只链接。
- 新增功能时更新对应事实源，不做全仓库改文档。

## 适用对象

需要查文档、写文档或评审文档的开发者。仓库根的 [README.md](../README.md) 是项目 README（面向使用者），本文是开发者文档体系的入口与维护约定。

## 分层与入口

| 层 | 位置 | 回答的问题 |
|---|---|---|
| 1 | [README.md](../README.md) | 项目定位与使用说明（项目 README），开发者从这里进入文档体系 |
| 2 | [architecture/](architecture/overview.md) | 系统上下文、模块划分、包职责、依赖规则、跨模块链路 |
| 3 | [modules/](modules/README.md) | 每个逻辑模块的边界、关键类、核心流程、扩展点 |
| 4 | 源码注释 | 公开类型与方法的契约，用 IDE 查看 |
| 5 | [api/](api/README.md)、[ops/](ops/configuration.md)、[guides/](guides/code-style.md)、[adr/](adr/README.md)、[agents/](agents/domain.md) | 对外契约、运行配置与故障排查、编码约定、架构决策、agent 工作方式 |

## 阅读路径

```mermaid
flowchart LR
    A[README] --> B[architecture/overview]
    B --> C[architecture/module-map]
    C --> D["modules 下的模块文档"]
    D --> E[api/ 与 ops/]
    B --> F[architecture/key-flows]
```

- 首次接手：README → 架构总览 → 模块地图 → 你负责的模块。
- 改规则字段：api/rule-json → modules/rule-model。
- 新增条件或效果：modules/core-contract → modules/type-system。
- 改编辑器：modules/authoring → modules/client-editor → modules/ui-kit → modules/client-ui。
- 排查线上问题：ops/troubleshooting → ops/configuration。

## 单一事实源

| 事实 | 唯一落点 | 其他文档 |
|---|---|---|
| 规则 JSON 形状、来源层、字段扩展方式 | [api/rule-json.md](api/rule-json.md) | 只链接 |
| 编辑协议载荷、分片、幂等与版本戳 | [api/edit-protocol.md](api/edit-protocol.md) | 只链接 |
| Issue、错误码、保存状态与文案 key | [api/errors.md](api/errors.md) | 只链接 |
| server.json 的定位、分组与派生换算 | [ops/configuration.md](ops/configuration.md) | 只链接 |
| 症状与排查步骤 | [ops/troubleshooting.md](ops/troubleshooting.md) | 只链接 |
| 类与方法契约 | 源码注释 | 不复制签名或字段列表 |
| 架构取舍与替代方案 | [adr/](adr/README.md) | 只链接 |
| 构建、启动与目录职责 | [README.md](../README.md) | 不重复 |
| 跨模块链路 | [architecture/key-flows.md](architecture/key-flows.md) | 模块文档只画本模块 |
| agent 工作方式 | [agents/](agents/domain.md) | 不重复 |

## 写作约定

本文档体系内的每篇文档以 front matter 开头（仓库根的项目 README 不在此列）：

```yaml
---
title: 文档标题
layer: 1 | 2 | 3 | 5
audience: 目标读者
authority: 该文档结论所依据的源码包、类或文件
---
```

正文先给 TL;DR（不超过 3 行）和“适用对象”，再进入内容；结尾用“相关文档”列出继续阅读的入口。图使用 Mermaid，链接使用相对路径，正文用中文写作，技术名词保留英文。

模块文档固定六节：模块职责、模块边界、关键类地图、关键流程、扩展点与约束、数据与配置引用；文末“相关文档”不占骨架章节。关键类地图只列对外入口、扩展点、聚合根与并发或事务敏感类，并注明“非穷举”。

## 硬规则

1. 只写当前存在的代码事实；不写占位功能、计划中功能、已删除机制、旧版本行为。
2. 边界用正面表述写清适用范围，不写“不支持 X”式否定清单。
3. 不写数量统计：文件数、类数、方法数、配置项数。
4. 不写穷举清单与全量目录树；枚举型内容指向代码枚举或常量，并说明新增方式。
5. 同一事实只维护一处，其他文档链接过去。
6. 不记录修改历史、日期、版本注记；与源码不一致时以源码为准并回改文档。
7. 类级契约在源码注释里，Markdown 不复制方法签名与字段列表。

## 校验

文档改动后运行：

```powershell
python tools/check-docs.py
```

脚本检查相对链接是否可解析、是否出现归档路径、是否出现反向指针。退出码非 0 即存在违规，修复后重跑。

## 相关文档

- [README.md](../README.md)
- [架构总览](architecture/overview.md)
- [模块文档索引](modules/README.md)
- [ADR 索引](adr/README.md)
