---
title: 客户端界面（client/ui 宿主层）
layer: 3
audience: 界面开发者、主题与控件适配实现者、字段控件与目录预览扩展者
authority: common/client/ui/theme、common/client/ui/widget、common/client/ui/shared、common/client/ui/screen、common/client/ui/screen/form
---

# 客户端界面

> **TL;DR**
> - 这一层把 UI kit 的原语装配成规则编辑器：主题注入、可复用控件、界面中立展示类型、规则管理页与编辑页签、条件画布页。
> - 编辑器只有一个写入口：字段与条件编辑都经 `EditSession` 落草稿，界面按类型描述符装配。
> - 条件树只有一个编辑入口：画布页；缩进树是只读摘要兼入口。

**适用对象**：改编辑器页面结构、加字段控件、加目录类型或预览图标、换主题的开发者。

## 1. 模块职责

一句话边界：在 UI kit 之上提供本项目落地的外观与控件、界面中立的展示类型，以及把规则管理、规则编辑、条件画布与调试串起来的屏幕。

子包分工：

| 子包 | 一句话职责 |
|---|---|
| `client/ui/theme` | 把灰色像素与扁平两套外观映射成 kit 的样式值，并以 `UiHostTheme` 作为唯一注入入口 |
| `client/ui/widget` | 在 kit 基础上提供可直接使用的输入框、列表、树、弹窗、按钮变体与条件树控件；滚动控件的行宽与行命中换算（`RowGeometry`）、区间宿主控件的公共外壳（`AbstractBarShell`）也收在这一层 |
| `client/ui/shared` | `screen` 与 `screen/form` 共享的纯值与展示类型：目录图标分发、展示名称、字段到目录类型的映射、条件摘要、悬停说明的语义行模型 |
| `client/ui/screen` | 规则编辑器主屏、管理页、编辑页签、调试页与页内输入分发 |
| `client/ui/screen/form` | 描述符驱动的表单视图、字段控件基类与注册表 |

编辑页签契约由 `RuleEditorPageSet.Page` 定义：INFO（基本信息）、INPUT（输入与消耗）、TRIGGER（触发条件）、RESULTS（效果与产物），`Page#of` 把键名解析成页签、未知键回落到 INFO。页签之间共用同一份 `EditSession` 草稿。

## 2. 模块边界

### 对外入口

- **编辑器入口**：`RuleEditorOpener` 注册到 `EditorScreenHooks`，服务端授权成功后由 `client/net` 调用，把 `RuleEditorScreen` 切到前台或关闭；会话的发起与结束由 `client/net` 承担，它只负责切屏与会话就绪后的刷新。链路见 [authoring](authoring.md) 与 [client-editor](client-editor.md)。
- **主题注入**：屏幕从 `UiHostThemes` 取 `UiHostTheme` 后注入自己装配的控件；控件统一经注入取色与取度量。
- **表单装配**：`FormView` + `FormControl` + `FormControlRegistry`，把类型描述符的字段列表渲染成可滚动的一列控件。
- **条件树界面**：`ConditionTreeOverlay`（画布页外壳）、`UiConditionGraphEditor`（画布编辑）、`UiConditionTreeSummary`（只读摘要兼入口）、`ConditionTreeContract`（宿主注入契约与选中路径同步）。
- **控件与弹窗契约**：`UiWidget` 统一渲染与输入转发；`UiModalStack` 负责弹窗层次、遮罩、焦点与输入独占。

### 依赖方向

箭头表示编译期依赖，方向自上而下（`client/net` 的入口类型另经 `RuleEditorOpener` 取用，图中从略）：

```mermaid
flowchart TD
    SCREEN["client/ui/screen"]
    FORM["client/ui/screen/form"]
    WIDGET["client/ui/widget"]
    SHARED["client/ui/shared"]
    THEME["client/ui/theme"]
    KIT["client/ui/kit"]
    EDIT["client/edit 草稿、撤销与描述符注册表"]
    CORE["core 契约与只读 DTO"]
    SCREEN --> FORM
    SCREEN --> WIDGET
    SCREEN --> SHARED
    SCREEN --> THEME
    FORM --> WIDGET
    FORM --> SHARED
    FORM --> THEME
    WIDGET --> SHARED
    WIDGET --> THEME
    SHARED --> THEME
    THEME --> KIT
    WIDGET --> KIT
    SHARED --> KIT
    FORM --> KIT
    SCREEN --> KIT
    THEME --> EDIT
    WIDGET --> EDIT
    WIDGET --> CORE
    SHARED --> EDIT
    FORM --> EDIT
    SCREEN --> EDIT
    SHARED --> CORE
    FORM --> CORE
    SCREEN --> CORE
```

几条必须守住的形状：

- kit 在底层：本层各子包依赖它，而它的 import 只落在原版类型与标准库白名单上（见 [ui-kit](ui-kit.md)）。
- `shared` 是 `screen` 与 `screen/form` 的公共下游：两者都依赖它，它的 import 只落在 `client/edit`、kit、theme 与 core 契约上，于是两个子包之间只有 `screen → screen/form` 一个方向。
- `theme` 只依赖 kit 与 `client/edit` 的命名空间常量；主题负责视觉，`widget` 负责交互，两者通过注入解耦。
- `screen` 与 `screen/form` 引用 `core.api`/`core.model`/`core.network.protocol` 的公开类型；调试页与本地校验另用 `core.debug`、`core.runtime`、`core.service`、`core.type.condition` 的公开入口。
- 传输通道与上行载荷收敛在 `client/net`；界面层消费它暴露的只读工作区状态与入口类型。

### 被谁依赖

`client/net` 通过 `EditorScreenHooks`/`EditorScreenOpener` 调用 `RuleEditorOpener`；客户端入口负责把 opener 注册进去。kit 与本层之间只有 kit 被依赖这一个方向。

## 3. 关键类地图

仅列对外入口、扩展点、聚合根与并发/事务敏感类，非穷举。

| 类型 | 角色 | 职责 | 协作对象 |
|---|---|---|---|
| `RuleEditorOpener` | 界面入口 | 提供 `bootstrap`/`shutdown`/`lastRequest` 静态入口，在客户端线程把编辑器切到前台或刷新 | `client/net` 的 hooks 与打开请求 |
| `RuleEditorScreen` | 屏幕聚合根 | 模式切换（管理模式、编辑模式、调试模式，取值见屏幕内枚举）、屏幕生命周期、布局编排、页内事件派发；工作区进入可编辑态才允许编辑，应用期间冻结，工作区释放时自行关屏 | 管理页、编辑页、调试页、弹窗栈、提示条 |
| `RuleEditorListPage` | 管理模式 | 搜索、状态/来源轮询筛选、分类树、规则导出与配方预览，列表底排按钮状态同步；只持有管理页控件与视图状态 | 规则会话、目录图标、`RulePackExporter`、`RuleEditorListActions` |
| `RulePackExporter` | 规则导出口 | 把列表可导出的已保存规则写成一个数据包 zip：跳过未保存草稿与有问题条目，清单格式取当前游戏的数据包版本，规则文件按 `data/<ns>/idtw/rules/<path>.json` 落位，id 强制写回 | 列表页导出弹窗、`RulePaths`、`RuleFields` |
| `RuleEditorPageSet` | 编辑页签组合 | 把页签与共享草稿绑定：所有写入经 `EditSession#apply` 提交为一条撤销记录；顶层效果按单一隐式候选展示 | 页签实现、`FormView`、`RuleEditorValidation` |
| `RuleEditorEditPage` | 编辑页与保存入口 | 页签切换、类型选择、代码视图、保存与校验入口 | `RuleEditorPageSet`、`UiModalStack` |
| `RuleEditorDebugPage` | 调试模式 | 按输入数量填充测试规则、一次性删除测试规则；只写草稿，落盘仍由管理页的“应用全部”决定 | `DebugTestRules` |
| `RuleEditorPageInput` | 页内输入分发 | 把按下、释放、拖动、滚动与键盘输入派发给当前页控件，并维护结果页动作列表的拖动排序；按下归属决定释放只发给谁 | 编辑页控件、`FormView` |
| `RuleEditorPanelFactory` | 面板装配 | 把 kit 控件与目录链路接到规则草稿上；写入一律经调用方给定的 `applyChange` | 叶子面板、`shared` 图标、数值控件 |
| `FormView` | 表单聚合根 | 按 `TypeEditorDescriptor` 渲染可滚动控件列；写入统一经 `EditSession`；支持定位高亮与内联选择 | `FormDraftWriter`、`FormInputRouter`、`FormControl` |
| `FormControl` / `FormControlRegistry` | 字段控件扩展点 | 一个字段一行，负责 JSON 值与控件值的双向搬运与本地校验；注册表把字段类型映射到控件工厂 | 描述符引擎、`EditSession` |
| `UiHostTheme` / `UiHostThemes` / `AbstractHostTheme` | 主题注入入口 | 调色板、度量、文本色、控件样式、滑块皮肤、结构绘制与图标行号；两套主题逐项相同的宿主度量与外壳原语收在 `AbstractHostTheme`，默认入口给出像素外观，扁平外观由调用方显式取用 | 全部控件与屏幕 |
| `UiWidget` / `UiModalStack` | 控件与弹窗契约 | 控件统一渲染与输入契约，坐标为 GUI 逻辑坐标；弹窗栈只把输入投给最上层，并管理焦点与历史快捷键 | 屏幕、`UiFocusManager` |
| `ConditionTreeContract` / `UiConditionGraphEditor` / `UiConditionTreeSummary` / `ConditionTreeOverlay` | 条件树界面家族 | 同一份条件数据与校验的两种展示；契约提供类型选项、叶子工厂、变更监听与选中路径同步 | 条件模型、`FormView`、`ConditionTreeChrome` |
| `UiFlatText` | 文本绘制原语 | 无阴影的居中、折行文本与悬停提示面板；GUI 不使用字体阴影与加粗，标题只靠颜色、缩进与分区间距区分层次 | kit 的 `UiRenderLayers`、各控件与页面 |
| `UiSlider` / `UiRangeBar` / `UiTextInput` | 控件适配 | 对外保持既有调用签名，内部把交互委托给 kit 的数值与文本契约；提交时机由宿主回调决定 | kit 数值/文本契约、主题 |

## 4. 关键流程

### 从授权到编辑

```mermaid
sequenceDiagram
    participant NET as client/net
    participant OP as RuleEditorOpener
    participant SCR as RuleEditorScreen
    participant WS as 客户端工作区
    NET->>OP: 服务端授权成功后的打开请求
    OP->>SCR: setScreen（客户端线程）或刷新
    SCR->>WS: 轮询工作区状态与快照
    WS-->>SCR: 快照与可编辑状态
    SCR->>SCR: 管理模式：列表、搜索、状态/来源筛选、分类树、导出
    SCR->>SCR: 进入编辑模式：页签 + 表单 + 条件摘要
    SCR->>WS: 保存/应用（由页面按钮触发）
```

### 一次字段编辑的落点

```mermaid
flowchart LR
    A["指针或键盘"] --> B["页内输入分发 / 表单输入分发"]
    B --> C["控件（UiWidget）"]
    C --> D["kit 输入路由与捕获"]
    D --> E["控件预览值"]
    E --> F{"操作结束"}
    F -->|提交| G["FormView 写入"]
    F -->|取消| H["回退预览，不写草稿"]
    G --> I["EditSession#apply：一条撤销记录"]
    I --> J["规则草稿"]
    J --> K["保存/应用经工作区上行"]
```

页内输入分发先记录“按下归属”，释放与拖动只发给同一个目标；表单输入分发按“选择框 → 行尾按钮 → 字段控件 → 表单滚动”的顺序派发，事件只消费一次。

### 列表页筛选与导出

状态与来源各用一个轮询按钮，点击依次切换取值，当前取值直接显示在按钮文字里，两者按交集过滤。状态取「全部 / 已启用 / 已停用」，来源取「全部 / 覆盖层 / 数据包」，来源为混合（覆盖层条目同时存在数据包同名规则）的条目在两个来源取值下都可见。

来源为内置数据包或世界数据包的条目在列表状态栏标注「只读」，删除与启停按钮禁用，编辑按钮改为「查看」；点击仍可进入编辑页。编辑页右上角状态标签以警告色显示「只读」，并禁用全部字段控件、保存入口与撤销/重做；页签切换、来源视图与返回保持可用。含覆盖副本（`overlay` / `mixed`）的条目继续按可编辑、可删除处理。

问题不占筛选按钮：条目带服务端校验问题或客户端解码失败时，列表树把它归入「有问题」分组，优先于「未完成」与常规分类；问题消失后回到原分组。分组无成员时自动隐藏，问题一出现就能在树上看到。

导出弹窗提供两个范围：「覆盖层」只含覆盖层来源的可导出条目，「覆盖层 + 数据包」再包含数据包条目。可导出指条目已保存、状态为已启用或已停用、有可读规则体且未标记删除；未保存草稿（「未完成」分组）、有问题条目（「有问题」分组）与屏蔽、非法条目不导出，避免把半成品或不可用规则带到别的设备。导出文件写到游戏根目录下的 `itemdespawntowhat-exports/`，zip 内是标准数据包结构，可直接作为存档数据包使用或解压后共享。

## 5. 扩展点与约束

### 扩展点

- **新增字段控件**：实现 `FormControl` 子类，并在 `FormControlRegistry` 的登记处追加一行。数值字段先走 `FormControl#create` 的“滑杆 + 行内精确输入”，域元数据不足时才落到注册表；注册表未覆盖的字段类型回落到只读的 JSON 展示控件。
- **新增界面标记图标**：标记图集是 `textures/gui/editor/marks.png`，由 `tools/image_generator/generate-editor-marks.py` 生成，字形为纯白、按调用方给定的主题色染色；在 `theme` 的 `UiMarkIcons.Mark` 登记行号与尺寸，用 `UiMarkIcons.icon(mark, color)` 取得图标后交给控件的 `setIcon` 或直接渲染。
- **新增事件图标**：事件图集是 `textures/gui/editor/events.png`，由 `tools/image_generator/generate-event-icons.py` 生成，每格 32×32 彩色成品、按 16 像素槽位缩放绘制，不做主题染色；在 `theme` 的 `UiEventIcons.Event` 登记行号，动作到事件的判定收在 `shared/EventPreviewIcons`，新增天象类动作在那里补一支分支。
- **新增目录类型或预览图标**：在 `shared` 的 `FieldCatalogTypes` 增加字段名兜底、在 `CatalogPreviewIcons` 增加分发分支；新增可预览注册表时按现有预览图标类的形态加宿主缓存类，并只在 `CatalogPreviewIcons` 接线。
- **新增条件类型**：数据与校验在 `client/edit` 的条件模型上落点（见 [client-editor](client-editor.md)），界面侧在 `ConditionTreeContract` 的类型选项与叶子工厂登记；画布与缩进摘要共用同一份数据与校验。
- **新增页签或结果页动作**：在 `RuleEditorPageSet.Page` 增加常量并实现对应页面；面板装配放 `RuleEditorPanelFactory`。页面共享的布局常量（页内留白、行高、表单与对话框的按钮高度）、`UI` 文案前缀、3 个页描述符 id 与 2 个存在条件叶类型 id 收在 `RuleEditorPageSupport`，它不持有界面状态、不装配控件；同一层的业务助手按关注点分开：`PresenceLeaves` 管存在条件叶的构造、扫描与增删，`CatalystQuantities` 管催化剂数量表对账，`RuleValueReads` 管 `RuleValue` 的只读取值与缺省回落。
- **新增主题**：实现 `UiHostTheme`（它本身就是 kit `UiStyleSource` 的宿主侧入口），在 `UiHostThemes` 暴露取用入口；控件经注入取色，新增皮肤只改主题实现。
- **新增宿主控件**：实现 `UiWidget`，由屏幕统一转发输入；需要模态行为时放进 `UiModalStack`，需要焦点顺序时登记到 `UiFocusManager`。
- **新增调试能力**：测试规则的身份前缀、分类归属与规则体在 `DebugTestRules` 统一约定，调试页只负责批量填充与按前缀删除。

### 约束

- 草稿单写入口：所有写入经 `EditSession#apply`，一次完整操作一条撤销记录；面板工厂与控件只装配、只搬运值。只读来源的编辑页先把页签组合置为禁用，`EditSession` 的只读位再在最外层拒绝写入，控件禁用不是唯一防线。
- 界面按描述符装配；JSON 的解析与构造收敛在 `client/edit`，界面消费模型或转换结果。
- 主题只表达视觉，控件只表达交互语义；控件通过注入取色，不静态读取主题类。
- 全 GUI 不使用字体阴影与加粗：需要居中、折行或悬停提示文本时统一走 `UiFlatText`，原版的 `drawCenteredString`、`drawWordWrap`、`renderTooltip` 会把暗色阴影叠在浅色底板上；信息层次由字体颜色、缩进与分区间距表达。
- 页面内容里的按钮统一用扁平胶囊样式（`UiButton#setChipStyle`），立体边只留给外壳页脚的保存行；扁平填充配 `PANEL_BORDER` 描边，让按钮与同页的标签框、分段控件、列表行处在同一层次。
- 悬停提示只补充画面上读不到的信息：规则名、组合方式、撤销说明、只读原因等；动作名、叉号、翻页箭头、分段选项、物品卡与行为卡这类已有文字或符号表达的内容不再重复解释。
- 必填字段在标签右上角加 `*` 角标；缺值时用红色标签与控件下沿的红色警示线表达。
- 条件树的结构与参数编辑只在画布页完成，缩进树保持只读并作为进入画布的入口（[ADR-0031](../adr/0031-condition-tree-single-editor-two-renders.md)）。条件编辑入口统一在触发页，结果页只编辑动作自身的参数与共用补充参数（概率、延迟），动作说明也不重复条件行。
- 画布页与画布内联编辑框使用扁平外观，且只在调用方显式注入时生效（[ADR-0028](../adr/0028-canvas-page-and-flat-skin.md)）。
- 上行载荷与传输通道收敛在 `client/net`；界面层消费只读工作区状态与协议 DTO。

### 改动后的验证

1. IDE 工作区检查 → `tools/dsh-build.ps1 -Tasks build`。
2. 界面布局按 [ADR-0022](../adr/0022-client-ui-kit-adoption.md) 的视觉基线验收：最低 320×240 GUI 逻辑坐标、页面在 GUI 缩放 1x/2x/3x 下无重叠与文本溢出，颜色线索同时给出文字或图标。
3. 改文案跑 `python tools/check-i18n.py`；改 kit 边界的引用跑 `tools/check-ui-kit-boundaries.ps1`；改文档链接跑 `python tools/check-docs.py`。

## 6. 数据与配置引用

界面只消费规则数据与配置，单一事实源在下列文档，本页只做指向：

- 规则字段语义与取值：[规则 JSON](../api/rule-json.md)。
- 表单问题、校验文案与拒载粒度：[错误与状态码](../api/errors.md)。
- 会话、分片与保存回执：[编辑协议](../api/edit-protocol.md)。
- 客户端与服务端配置项：[配置](../ops/configuration.md)。

界面文案的键前缀为 `gui.itemdespawntowhat.edit.`，字段问题另用 `gui.itemdespawntowhat.edit.issue.`（见 `FormControl` 的常量）；中英文语言文件由 `tools/check-i18n.py` 同步校验。

## 相关文档

- 模块总览：[模块索引](README.md)
- 底层原语：[UI kit](ui-kit.md)
- 草稿与类型编辑器注册表：[client-editor](client-editor.md)
- 服务端编辑会话与落盘：[authoring](authoring.md)
- 条件与效果类型注册：[type-system](type-system.md)
- 依赖规则：[依赖规则](../architecture/dependency-rules.md)
- 跨模块链路：[关键链路](../architecture/key-flows.md)
- 决策：[ADR-0022](../adr/0022-client-ui-kit-adoption.md)、[ADR-0028](../adr/0028-canvas-page-and-flat-skin.md)、[ADR-0029](../adr/0029-client-dependency-boundary-and-shared-subpackage.md)、[ADR-0031](../adr/0031-condition-tree-single-editor-two-renders.md)、[ADR-0032](../adr/0032-host-injected-ui-theme.md)
- 写作与命名约定：[代码风格](../guides/code-style.md)
- 文档体系：[文档体系](../README.md)
