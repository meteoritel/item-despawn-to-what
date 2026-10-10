# 客户端与编辑器文档地图（`docs/dev/internals`）

> 范围：客户端编辑工作区、编辑器界面、输入状态与客户端 UI kit。
> 本目录属于**执行层**（面向开发者的模块查阅手册）：按需单篇查阅，不要把本目录整体加载。抽象层的分层与职责见 [../layers/client.md](../layers/client.md)。

## 1. 按问题 → 文档

| 问题 | 文档 |
|---|---|
| 编辑器四页各负责什么、输入页与触发页怎么联动、管理页怎么布局 | [editor-pages-contract.md](editor-pages-contract.md) |
| 字段输入缓冲、切页/卸载时的提交、树结构操作后输入路径怎么迁移 | [editor-input-state.md](editor-input-state.md) |
| 每页卡片分组、间距、控件尺寸与视觉规格 | [editor-card-layout.md](editor-card-layout.md) |
| UI kit 能依赖什么、公开面有哪些、输入/数值/文本/树怎么接入 | [ui-kit/README.md](ui-kit/README.md) |

## 2. 客户端包 → 文档

| 包 | 主文档 |
|---|---|
| `client/edit` | [editor-input-state.md](editor-input-state.md)、[editor-pages-contract.md](editor-pages-contract.md) |
| `client/ui/screen` | [editor-pages-contract.md](editor-pages-contract.md)、[editor-card-layout.md](editor-card-layout.md) |
| `client/ui/widget`、`client/ui/theme` | [editor-card-layout.md](editor-card-layout.md)、[ui-kit/README.md](ui-kit/README.md) |
| `client/ui/kit` | [ui-kit/README.md](ui-kit/README.md) |

## 3. 事实来源

每篇文档头部标注它的事实来源类。核对顺序：文档 → 源码；两者不一致时以源码为准，并回来修正文档。
