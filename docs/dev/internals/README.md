# 客户端前端实现文档（`docs/dev/internals`）

> 范围：`common/src/main/java/com/meteorite/itemdespawntowhat/client` 及其在 `fabric/`、`neoforge/` 的客户端接入。
> 本目录属于**执行层**：按代码目录与模块边界组织，只陈述可直接核对的代码事实。分层职责与硬约束见 [../layers/client.md](../layers/client.md)。
> 事实来源：各模块文档「类清单」列出的源码文件。文档与源码不一致时以源码为准，并回改文档。

## 1. 模块索引

| 层级 | 模块 | 代码路径 | 文档 | 源码文件数 |
|---|---|---|---|---|
| 入口层 | 按键入口 | `client/key` | [client-entry.md](client-entry.md) | 2 |
| 入口层 | 平台客户端接入 | `fabric/…/client`、`neoforge/…/client` | [client-entry.md](client-entry.md) | 6 |
| 接口层 | 协议工作区 | `client/net` | [client-net.md](client-net.md) | 6 |
| 状态/数据层 | 规则草稿、编辑会话与注册表 | `client/edit` | [client-edit.md](client-edit.md) | 35 |
| 状态/数据层 | 草稿持久化 | `client/edit/draft` | [client-edit.md](client-edit.md) | 5 |
| 页面层 | 编辑器屏幕与模式页 | `client/ui/screen` | [ui-screen.md](ui-screen.md) | 31 |
| 页面层 | 表单、叶面板与条件树画布页 | `client/ui/screen/form` | [ui-screen-form.md](ui-screen-form.md) | 35 |
| 组件层 | 控件与条件树组件 | `client/ui/widget` | [ui-widget.md](ui-widget.md) | 30 |
| 基础组件层 | UI kit | `client/ui/kit` | [ui-kit/README.md](ui-kit/README.md) | 68 |
| 主题层 | 主题与调色板 | `client/ui/theme` | [ui-theme.md](ui-theme.md) | 10 |
| 展示工具层 | 展示名称、预览图标与摘要 | `client/ui/shared` | [ui-shared.md](ui-shared.md) | 12 |
| 原型层 | 开发用原型界面 | `client/ui/prototype` | [ui-prototype.md](ui-prototype.md) | 2 |
| 开发工具 | NeoForge 截图源集 | `neoforge/src/guiScreenshot` | [gui-screenshot-tool.md](gui-screenshot-tool.md)、[gui-screenshot-api-findings.md](gui-screenshot-api-findings.md) | 5 |

UI kit 模块文档的分册索引：

| 分册 | 覆盖内容 |
|---|---|
| [ui-kit/README.md](ui-kit/README.md) | 模块路径、公开面、依赖边界、类清单路由 |
| [ui-kit/document-and-controls.md](ui-kit/document-and-controls.md) | 文档排版、布局、控件、样式、几何、图标、浮层、滚动、绘制 |
| [ui-kit/input-routing.md](ui-kit/input-routing.md) | 输入路由、捕获、值交互、焦点、导航、历史派发 |
| [ui-kit/value-controls.md](ui-kit/value-controls.md) | 数值策略、标量/区间/周期滑块 |
| [ui-kit/text-controls.md](ui-kit/text-controls.md) | 文本历史、文本选择、测量与滚动 |
| [ui-kit/tree-editing.md](ui-kit/tree-editing.md) | 不可变树编辑、树布局、画布原语 |
| [ui-kit/rendering-and-preview.md](ui-kit/rendering-and-preview.md) | 实体/方块/模型预览、灯箱、图片、轮播 |

## 2. 按任务定位

| 要改什么 | 先读 |
|---|---|
| 快捷键、开发环境入口、平台客户端注册与事件 | [client-entry.md](client-entry.md) |
| 编辑协议、会话状态、开屏、快照分片与目录缓存 | [client-net.md](client-net.md) |
| 规则草稿、撤销栈、输入缓冲、类型编辑器注册表、条件树编解码、草稿落盘 | [client-edit.md](client-edit.md) |
| 编辑器屏幕、模式切换、页签、保存遮罩、校验、列表页操作 | [ui-screen.md](ui-screen.md) |
| 表单字段行、字段控件、叶面板、候选值选择、条件树控件 | [ui-screen-form.md](ui-screen-form.md) |
| 按钮、输入框、列表、树、弹窗、目录选择页、条件树画布组件 | [ui-widget.md](ui-widget.md) |
| UI kit 依赖边界与公开面 | [ui-kit/README.md](ui-kit/README.md) |
| 主题、调色板、扁平皮肤 | [ui-theme.md](ui-theme.md) |
| 展示名称、目录图标、条件摘要文本 | [ui-shared.md](ui-shared.md) |
| 开发用原型界面 | [ui-prototype.md](ui-prototype.md) |
| NeoForge 截图工具 | [gui-screenshot-tool.md](gui-screenshot-tool.md) |

## 3. 层级与依赖方向

依赖方向按源码 `import` 语句统计，明细写在每个模块文档的「2.4 依赖关系」。

| 层级 | 模块 | 依赖的本模组包 |
|---|---|---|
| 入口层 | `client/key` | `client/net`；开发环境分支引用 `client/ui/prototype` |
| 入口层 | 平台客户端接入 | `client/key`、`client/net`、`client/ui/screen` |
| 接口层 | `client/net` | 无本模组客户端包 |
| 状态/数据层 | `client/edit` | `client/net`、`client/ui/kit` |
| 状态/数据层 | `client/edit/draft` | `client/edit` |
| 页面层 | `client/ui/screen` | `client/edit`、`client/edit/draft`、`client/net`、`client/ui/screen/form`、`client/ui/widget`、`client/ui/kit`、`client/ui/theme`、`client/ui/shared` |
| 页面层 | `client/ui/screen/form` | `client/edit`、`client/net`、`client/ui/widget`、`client/ui/kit`、`client/ui/theme`、`client/ui/shared` |
| 组件层 | `client/ui/widget` | `client/edit`、`client/ui/kit`、`client/ui/theme`、`client/ui/shared` |
| 基础组件层 | `client/ui/kit` | 无本模组包；由 `tools/check-ui-kit-boundaries.ps1` 校验 |
| 主题层 | `client/ui/theme` | `client/edit`（`TypeLabels`）、`client/ui/kit` |
| 展示工具层 | `client/ui/shared` | `client/edit`、`client/ui/kit`、`client/ui/theme` |
| 原型层 | `client/ui/prototype` | `client/ui/kit`、`client/ui/theme`、`client/ui/widget` |

## 4. 统一命名口径

同一概念在全目录只使用下表名称。代码本身存在多种命名时，在对应模块的「2.6 代码现状」列出两种写法，不自行合并。

| 术语 | 代码锚点 |
|---|---|
| 规则编辑器屏幕 | `RuleEditorScreen` |
| 列表模式 / 编辑模式 / 调试模式 | `RuleEditorScreen.Mode` 的 `LIST` / `EDIT` / `DEBUG` |
| 列表页 | `RuleEditorScreenListPage` |
| 编辑页 | `RuleEditorScreenEditPage` |
| 调试页 | `RuleEditorScreenDebugPage` |
| 基本页 | `RuleEditorInfoPage`（页签 `Page.INFO`） |
| 输入页 | `RuleEditorInputPage`（页签 `Page.INPUT`） |
| 触发页 | `RuleEditorTriggerPage`（页签 `Page.TRIGGER`） |
| 结果页 | `RuleEditorResultsPage`（页签 `Page.RESULTS`） |
| 条件树画布页 | `ConditionTreeOverlay` |
| 叶面板 | `LeafPanel` |
| 表单视图 | `FormView` |
| 表单控件 | `FormControl` 及其子类 |
| 目录选择页 | `UiCatalogGrid` |
| 候选值选择框 | `FormPicker` |
| 保存遮罩 | `RuleSaveOverlay` |
| 规则草稿 | `RuleDraft` |
| 编辑会话 | `EditSession` |
| 工作区 | `RuleEditClientWorkspace` |
| 工作区视图 | `EditorWorkspaceView` |
| 类型编辑器描述 | `TypeEditorDescriptor` |
| 编辑器字段 | `EditorField` |
| 条件树编辑器 | `UiConditionGraphEditor` |
| 树编辑器 | `UiTreeEditor` |
| 宿主主题 | `UiHostTheme` |
| 主题入口 | `UiHostThemes` |
| 扁平皮肤 | `UiFlatSkin` |

命名书写规则：

- 模块名写代码包路径（如 `client/ui/screen`），不使用自造模块名。
- 类型写反引号短类名（如 `RuleDraft`）；成员写 `<类>#<成员>`；不写行号。
- 源码文件写仓库相对路径（如 `common/src/main/java/com/meteorite/itemdespawntowhat/client/edit/RuleDraft.java`）。

## 5. 模块文档的固定章节

每个模块文档按下列顺序组织，标题固定：

1. 模块索引
2. 模块：<模块名>
   - 2.1 模块路径
   - 2.2 职责
   - 2.3 对外接口与导出内容
   - 2.4 依赖关系
   - 2.5 关键实现位置
   - 2.6 代码现状（可核对）
3. 类清单
4. 相关文档

一个文档覆盖多个模块时，第 2 节按模块重复，类清单与相关文档保持最后两节。UI kit 分册是同一包内的分组文档，模块路径统一写 `client/ui/kit`，并在「覆盖」行列出该分册覆盖的类。

## 6. 维护规则

- 源码增删类：同步对应模块文档的「2.3 对外接口」「2.5 关键实现位置」与「3 类清单」。
- 源码依赖变化：按 `import` 重新统计「2.4 依赖关系」，不凭记忆修改。
- 新增包或拆分模块：在本文件第 1 节登记，再新建模块文档并补齐固定章节。
- 删除包或类：删除对应文档登记与类清单条目，不留“已删除”说明。
- 命名口径变化：先改本文件第 4 节，再把全部模块文档统一改完；不得只改一处。
- 文档只写当前代码事实，不写计划、历史、版本与主观评价。
- 改动本目录后运行 `python tools/check-docs.py` 校验链接。

## 7. 相关

- 抽象层：客户端职责与边界见 [../layers/client.md](../layers/client.md)
- 后端执行层入口：[../backend/README.md](../backend/README.md)
- 文档约定：[../conventions.md](../conventions.md)
- 开发者文档索引：[../README.md](../README.md)
