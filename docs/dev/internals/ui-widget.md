# 控件与条件树组件（client/ui/widget）

> 层级：组件层；模块路径：`client/ui/widget`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/widget`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 控件与条件树组件 | `client/ui/widget` | 2.1–2.6、3 |

## 2. 模块：控件与条件树组件

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.widget`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/widget`
- 覆盖：30 个 `.java` 文件（含 `package-info.java`）

### 2.2 职责

- 定义 `UiWidget` 渲染与输入契约：`bounds`、`setBounds`、`render` 由实现方给出，鼠标、滚轮、键盘与字符输入以 default 方法返回 false。
- 在 kit 之上提供可直接装配的控件：按钮与按钮变体、复选框、单行与多行文本输入、数值滑杆、区间条、昼夜时间条、分段选择、类型选择面板、说明提示与旁白适配。
- 提供容器与列表：模态弹窗 `UiModal` 与弹窗栈 `UiModalStack`、可滚动列表 `UiListView`、列表编辑器 `UiListEditor`、树视图 `UiTreeView` 与树节点 `UiTreeNode`、目录选择页 `UiCatalogGrid`、底部行托管接口 `UiFooterHost`。
- 提供条件树组件：只读缩进摘要 `UiConditionTreeSummary` 与条件树编辑器 `UiConditionGraphEditor`，以及包内使用的布局、绘制、右侧面板与参数面板实现。
- 条件树的宿主注入契约与结构校验集中在 `ConditionTreeContract` 与 `ConditionTreeInspection`，摘要与画布共用同一份判定。
- 控件的配色、九宫格、字体、行高与内边距取自构造时注入的宿主主题 `UiHostTheme`。

### 2.3 对外接口与导出内容

以下两条对实现方成立：实现 `UiWidget` 的类型均提供 `bounds()`、`setBounds(int, int, int, int)`、`render(GuiGraphics, Font, int, int)`，并可覆写 `UiWidget` 的输入方法；实现 kit 的 `UiFocusTarget` 的类型均提供 `canFocus()`、`setFocused(boolean)`、`isFocused()`、`activate()`、`accessibleName()`。表中只列其余公开成员。

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `UiWidget` | interface | `bounds`、`setBounds`、`render`、`isVisible`、`mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`keyReleased`、`charTyped` | 除前三个外均为 default 方法；`isVisible` 默认 true，输入方法默认 false。 |
| `UiFooterHost` | interface | `setFooterReserved(int right, int height)` | 由弹窗按钮行托管底部行的控件实现。 |
| `UiButton` | class（`UiWidget`、`UiFocusTarget`） | `UiButton(Font, Component, UiButtonVariant, UiAction)`、`UiButton(UiHostTheme, Font, Component, UiButtonVariant, UiAction)`、`setLabel`、`setIcon`、`setChipStyle`、`setAction`、`setTooltip`、`setEnabled`、`setVisible`、`setAccessibleName`、`label`、`variant`、`isPressed`、`preferredWidth` | 状态与焦点委托 kit 的 `UiControl`。 |
| `UiButtonVariant` | enum | `PRIMARY`、`SECONDARY`、`DANGER`、`DISABLED`、`role`、`interactive` | 只表达语义；颜色由主题按 `UiControlRole` 给出。 |
| `UiCheckBox` | class（`UiWidget`、`UiFocusTarget`） | `UiCheckBox(UiHostTheme, Font, Component, boolean)`、`preferredWidth`、`label`、`setLabel`、`isChecked`、`setChecked`、`toggle`、`setOnChanged`、`setEnabled`、`isEnabled`、`setVisible` | `BOX_SIZE` 为 9、`LABEL_GAP` 为 3。`setChecked` 不触发回调，`toggle` 触发。 |
| `UiTextInput` | class（`UiWidget`、`UiFocusTarget`） | `value`、`setValue`、`clear`、`setMaxLength`、`setFilter`、`setOnOverflow`、`setOnValueChanged`、`setEditable`、`setHint`、`setOnCommit`、`setOnCancel`、`setVisible`、`selectAll`、`moveCursorToStart`、`setAccessibleName`、`endInteraction` | 封装原版 `EditBox`，长度上限按 Unicode 码点判定。 |
| `UiTextArea` | class（`UiWidget`、`UiFocusTarget`） | `value`、`setValue`、`setMaxLength`、`setEditable`、`setSelectableWhenReadOnly`、`setHint`、`setOnChanged`、`setOnOverflow`、`setOnCancel`、`setRows`、`setAccessibleName`、`setVisible`、`moveCursorToStart`、`height`、`endInteraction` | 封装原版 `MultilineTextField`；超限的这次输入整体回退。 |
| `UiSlider` | class（`UiWidget`、`UiFocusTarget`、`UiInputTarget`） | `core`、`setRange`、`value`、`min`、`max`、`step`、`setValue`、`trySetValue`、`isBackfillInvalid`、`setLabel`、`setFormatter`、`setOnChanged`、`setEnabled`、`isEnabled`、`setVisible`、`setError`、`isDragging`、`formatValue`、`endInteraction` | `TRACK_HEIGHT` 为 4、`THUMB_WIDTH` 为 3；数值域、窗口、捕获与 preview/commit/cancel 委托 `UiScalarSlider`。 |
| `UiRangeBar` | class（`UiWidget`、`UiFocusTarget`） | 静态 `baseHeight`、静态 `preferredHeight`、`core`、`setLabel`、`setEnabled`、`isEnabled`、`setVisible`、`setPreciseEditor`、`openPrecise`、`endInteraction`、`unmount`、`onFocusScopeChanged`、`onHostClosed` | 嵌套 `Listener`（`onBegan`、`onPreviewed`、`onCommitted`、`onCancelled`）与 `Texts`；交互委托 `UiRangeSlider`。 |
| `UiCyclicTimeBar` | class（`UiWidget`、`UiFocusTarget`） | `setRange`、`setPresets`、`setTicks`、`setFormatter`、`setLabel`、`setEnabled`、`isEnabled`、`setVisible`、静态 `baseHeight`、静态 `preferredHeight`、`setPreciseEditor`、`openPrecise`、`endInteraction`、`unmount`、`onFocusScopeChanged`、`onHostClosed`、`captureEnded` | 常量 `PERIOD` 为 24000.0D、`DRAG_STEP` 为 100.0D、`MIN_TICK` 为 0.0D、`MAX_TICK` 为 23999.0D；嵌套 `Listener`、`Texts`、`Preset`；交互委托 `UiCyclicRange`。 |
| `UiSegmentedControl` | class（`UiWidget`、`UiFocusTarget`） | `setOptions`、`options`、`selected`、`selectedIndex`、`setSelected`、`setOnChanged`、`setEnabled`、`isEnabled`、`setVisible`、`preferredWidth` | 嵌套 `Option(String value, Component label)`；当前选中值不在候选中时回退到第一项。 |
| `UiTypePicker` | class（`UiWidget`） | `UiTypePicker(UiHostTheme, Font, Component, Consumer<ResourceLocation>)`、`isOpen`、`open`、`close` | 嵌套 `Option(ResourceLocation id, Component label)`；`setOptions`、`setBounds`、`cancelCapture` 为包级可见。 |
| `UiDescriptionTooltip` | class | `UiDescriptionTooltip(UiHostTheme, Font)`、`render(GuiGraphics, UiRect, List<Line>, int, int)` | 嵌套 `Line(Component text, int color)`；不实现 `UiWidget` 与 `UiFocusTarget`。 |
| `UiNarration` | class | 静态 `focus(UiFocusTarget)`、`say(Component)` | 私有构造；包装 `Minecraft#getNarrator` 的播报。 |
| `UiListView<T>` | class（`UiWidget`、`UiFocusTarget`） | `setItems`、`items`、`size`、`selectedIndex`、`selectedItem`、`setSelectedIndex`、`setRowHeight`、`setEmptyMessage`、`setOnSelectionChanged`、`setActivateOnSingleClick`、`setSelectionMarkerVisible`、`setChipRows`、`setOnActivate`、`setVisible`、`ensureVisible`、`itemIndexAt`、`isDraggingScrollbar`、`rowBounds`、`visibleRowBounds`、`dragIndexAt`、`acceptsFocusAt` | 嵌套 `RowRenderer<T>`；`DEFAULT_ROW_HEIGHT` 取默认主题的 `rowHeight()`。 |
| `UiListEditor` | class（`UiWidget`、`UiFocusTarget`） | `preferredHeight`、`mode`、`registryHint`、`input`、`items`、`setItems`、`editingIndex`、`restorePendingInput`、`size`、`selectedIndex`、`setSelectedIndex`、`selectedItem`、`setOnChanged`、`setEnabled`、`isEnabled`、`setVisible`、`addItem`、`removeSelected`、`moveSelected`、`commitPendingInput` | 嵌套 `Mode`（`ID`、`TAG`、`TEXT`）；常量 `ROW_HEIGHT` 为 12、`INPUT_HEIGHT` 为 12、`VISIBLE_ROWS` 为 4、`MAX_ENTRY_LENGTH` 为 128。 |
| `UiTreeView<T>` | class（`UiWidget`、`UiFocusTarget`） | `setRoots`、`addRoot`、`roots`、`setRowHeight`、`setRowHeightProvider`、`scrollOffset`、`setScrollOffset`、`setEmptyMessage`、`setOnSelectionChanged`、`setOnActivate`、`setVisible`、`expandAll`、`collapseAll`、`selectedNode`、`setSelectedNode`、`nodeAt`、`nodeContentBounds` | 嵌套 `NodeRenderer<T>`；常量 `INDENT_WIDTH` 为 10、`MARKER_WIDTH` 为 9。 |
| `UiTreeNode<T>` | class | `UiTreeNode(T, Component)`、`addChild(UiTreeNode<T>)`、`addChild(T, Component)`、`value`、`label`、`children`、`isLeaf`、`isExpanded`、`setExpanded`、`toggleExpanded`、`expandAll`、`collapseAll` | 只承载展示数据，不参与布局与渲染。 |
| `UiCatalogGrid` | class（`UiWidget`、`UiFocusTarget`、`UiFooterHost`） | `setPageLabel`、`setStatusNote`、`setEntryPolicy`、`setEntrySource`、`setEntries`、`entries`、`size`、`selection`、`setSelection`、`setUnavailableReason`、`isSelected`、`setFilter`、`filter`、`setError`、`setLoading`、`setOnRetry`、`setTooltipSuffix`、`setPage`、`setPageLimits`、`setEnabled`、`isEnabled`、`setVisible`、`setFooterReserved` | 嵌套 `Listener`、`Scope`、`EntryPolicy`、`Entry`、`Texts`；`PAGE_SIZE` 为 200。 |
| `UiModal` | class（`UiWidget`） | `onClosed`、`onHistoryChanged`、静态 `create(UiHostTheme, Font)`、静态 `create(Font)`、`title`、`message`、`content`、`contentWidget`、`preferredWidth`、`addButton`、`confirm`、`cancel`、`inlineFooter`、`closeOnBackdrop`、`retainOnConfirm`、`onCancel`、`buttons`、`contentRect`、`setVisible`、`preferredHeight`、`layoutCentered`、`layoutAt`、`addFocusTargets` | 嵌套 `ContentRenderer`；常量 `DEFAULT_WIDTH` 为 220、`MIN_WIDTH` 为 120、`BUTTON_HEIGHT` 为 16。 |
| `UiModalStack` | class（`UiWidget`） | `setHistoryTarget`、`isEmpty`、`size`、`top`、`modals`、`focusManager`、`push`、`setOnScopeChanged`、`closeTop`、`close`、`clear`、`tick`、`isMouseOverModal`、`requestHistory` | `FADE_TICKS` 为 4；`top` 在栈空时返回 null。 |
| `UiConditionTreeSummary` | class（`UiWidget`、`UiFocusTarget`、`ConditionTreeContract.Navigation`） | `setOnOpenCanvas`、`expression`、`setExpression`、`isValid`、`nodeCount`、`leafCount`、`depth`、`issues`、`setTypeOptions`、`setLeafDetail`、`setEmptyMessage`、`setRowHeight`、`setEnabled`、`setVisible`、`expandAll`、`collapseAll`、`selectedPath`、`selectedNode`、`rowCount`、`selectPath`、`tooltipAt` | 只读摘要；点击节点通过回调请求宿主打开画布。 |
| `UiConditionGraphEditor` | class（`UiWidget`、`UiFocusTarget`、`ConditionTreeContract.Navigation`） | `tree`、`isDragging`、`setTypeOptions`、`setLeafFactory`、`setLeafSummary`、`setListener`、`setOnEditLeaf`、`endInteractions`、`lastChange`、`operationError`、`showOperationError`、`setBeforeEdit`、`setClipboardActions`、`setEmptyMessage`、`selectedSubtrees`、`expression`、`setExpression`、`isValid`、`nodeCount`、`leafCount`、`depth`、`issues`、`undoOpKey`、`selectedPath`、`selectedNode`、`selectPath`、`beginAddCondition`、`isPickerOpen`、`hasPopup`、`setEndpoints`、`isAddMode`、`isGroupKindPending`、`isWrapKindPending`、`beginAddGroup`、`beginAddGroupWithKind`、`beginWrapWithKind`、`invertSelection`、`removeSelection`、`moveSelection`、`wrapSelection`、`selectAll`、`copySelection`、`pasteSelection`、`pasteNodes`、`editSelectedLeaf`、`canEditSelectedLeaf`、`resetView`、`fitToContent`、`zoomStep`、`scalePercent`、`hint`、`setInlineEditor`、`clearInlineEditor`、`isInlineEditorOpen`、`closePicker` | 嵌套 `InlineEditor`（参数表单契约，坐标为 GUI 屏幕坐标）。画布的结构与选择基于 kit 的 `UiTreeEditor`。 |
| `ConditionTreeContract` | class | `ROOT_PATH`（等于 `RuleFields.CONDITIONS`） | 嵌套 `TypeOption(ResourceLocation, Component)`、`LeafFactory`、`Listener`、`Navigation`；私有构造的工具类。 |
| `ConditionTreeInspection` | class | `DEEP_HINT_DEPTH`（为 6）；静态 `isIncomplete`、`isLeafMissing`、`isOverflow`、`isDeep`、`nodeIssues`、`validateCandidate` | 嵌套 `IssueKind`（`INCOMPLETE_GROUP`、`MISSING_CONDITION`、`TOO_DEEP`、`TOO_MANY_NODES`、`TOO_MANY_LEAVES`）与 `Issue`；私有构造的工具类。 |

以下四个类型不带 `public` 修饰符，属包内实现，不构成本包对外契约：`ConditionGraphLayout`（含包级 `record Hotspot` 与 `record NodeInfo`）、`ConditionGraphRenderer`、`ConditionGraphSidePanel`、`ConditionGraphInlinePanel`。

### 2.4 依赖关系

依赖本模组：

- `client/ui/kit`（25 个文件 import，共 127 处）
- `client/ui/theme`（21 个文件 import，共 37 处）
- `core/model`（7 个文件 import，共 14 处）
- `client/edit`（4 个文件 import，共 8 处）
- `core/api`（2 个文件 import，共 2 处）
- `client/ui/shared`（1 个文件 import，共 1 处）

依赖外部：

- `java.util`（21 个文件，59 处）
- `net.minecraft.client.gui`（23 个文件，45 处）
- `net.minecraft.network.chat`（25 个文件，26 处）
- `java.util.function`（16 个文件，25 处）
- `org.jetbrains.annotations`（19 个文件，19 处）
- `org.lwjgl.glfw`（16 个文件，16 处）
- `net.minecraft.client.gui.screens`（6 个文件，6 处）
- `net.minecraft.resources`（5 个文件，5 处）
- `net.minecraft.util`（4 个文件，4 处）
- `net.minecraft`（`Util`，4 个文件，4 处）
- `net.minecraft.client.gui.components`（2 个文件，3 处）
- `net.minecraft.client`（`Minecraft`，1 个文件，1 处）

依赖包内：

- `ConditionGraphLayout`（4 个文件 import，共 19 处；其中 17 处为静态常量与 `isContainer`，2 处为 `Hotspot`）
- `ConditionGraphRenderer`（4 个文件 import，共 4 处；均为静态 `drawBorder`）
- `ConditionGraphSidePanel`（2 个文件，共 4 处；`SIDE_PANEL_HEADER` 与 `SIDE_PANEL_PADDING`）
- `UiConditionGraphEditor.InlineEditor`（1 个文件，1 处）

被依赖：

- `client/ui/screen`（20 个文件 import，共 75 处）
- `client/ui/screen/form`（19 个文件 import，共 44 处）
- `client/ui/prototype`（1 个文件 import，共 9 处）
- `fabric` 与 `neoforge` 的源码目录：无 import

本包不 import 页面层 `client/ui/screen` 与 `client/ui/screen/form`。

### 2.5 关键实现位置

- `UiWidget#isVisible`：默认返回 true；各输入方法默认返回 false，宿主据此继续向下分发。
- `UiModal#contentWidget`：装载内容控件；`UiModal` 在 `mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`charTyped` 中把事件转给该控件。
- `UiModal#keyPressed`：先给内容控件机会，未消费时才在 Esc 上执行取消。
- `UiModal#inlineFooter`：把按钮行计入内容区高度；布局完成后若内容控件实现 `UiFooterHost`，以 `setFooterReserved` 交出按钮占用的右侧宽度与行高。
- `UiModalStack#mouseClicked`：只投递给栈顶弹窗；点击落在弹窗外且 `closeOnBackdrop` 为真时关闭栈顶；栈非空时返回 true 吞掉本次点击。
- `UiModalStack#keyPressed`：先用 `UiHistoryDispatcher` 派发历史快捷键，再交给栈顶弹窗与焦点管理器；焦点为按钮时打开空格激活。
- `UiModalStack#requestHistory`：先请求宿主历史目标，成功后再通知所有已打开模态。
- `UiButton#apply`：把文本、主题文本色、主题样式、图标、提示行、动作与启用态写入被委托的 `UiControl`；启用态同时受 `UiButtonVariant#interactive` 约束。
- `UiButton#mouseReleased`：仅在按下由自己记录（`control.isPressed()`）时消费，指针仍在控件内才执行激活。
- `UiSlider#core`：暴露被委托的 `UiScalarSlider`；`UiSlider#setOnChanged` 桥接到 `UiValueInteraction.Listener#previewed`。
- `UiRangeBar#core`：暴露被委托的 `UiRangeSlider`；`UiRangeBar.Listener` 的 `onBegan`、`onPreviewed`、`onCommitted`、`onCancelled` 对应一次完整操作。
- `UiCyclicTimeBar` 的构造：创建 `UiCyclicRange` 并设置刻步长与滑块宽度；渲染读取 `from`、`to`、`isSinglePoint`、`crossesPeriod` 决定显示形态。
- `UiListView#rowBounds` 与 `UiTreeView#nodeContentBounds`：输出行与节点的屏幕矩形，作为宿主行内绘制与命中的统一坐标来源。
- `UiCatalogGrid#setFooterReserved`：记录按钮占用的右侧宽度与行高，布局时据此缩短底部行。
- `UiConditionGraphEditor#setExpression`：外部设置表达式时不触发变更回调。
- `ConditionGraphLayout`：维护身份索引、诊断、标签与卡片几何；`ConditionGraphRenderer` 只读取布局与选择状态完成绘制。
- `ConditionTreeInspection#validateCandidate`：结构校验入口，摘要与画布共用。
- `UiNarration#focus`：读取 `UiFocusTarget#accessibleName()` 后交给 narrator。

### 2.6 代码现状（可核对）

- 同一概念存在两种前缀。使用 `ConditionTree*` 的有 `ConditionTreeContract`、`ConditionTreeInspection`、`UiConditionTreeSummary`；使用 `ConditionGraph*` 的有 `ConditionGraphLayout`、`ConditionGraphRenderer`、`ConditionGraphSidePanel`、`ConditionGraphInlinePanel`，以及 `UiConditionGraphEditor`（Graph 位于类名中段）。两种写法并存，本文按类名引用，不合并命名。
- 组件层并非只承载通用控件：本包对 `client/edit` 有 8 处 import，分布在 4 个文件。`ConditionGraphLayout` 引用 `ConditionTreeNodes`、`OpaqueCondition`、`TypeLabels`；`ConditionGraphRenderer` 引用 `ConditionTreeNodes`；`UiConditionGraphEditor` 引用 `ConditionTreeNodes`、`EditSession`、`OpaqueCondition`；`UiConditionTreeSummary` 引用 `TypeLabels`。
- 主题依赖集中在 `UiHostTheme` 与 `UiHostThemes`：21 个文件 import `client/ui/theme`，共 37 处。不依赖主题的文件为 `ConditionGraphLayout`、`UiButtonVariant`、`UiNarration`、`UiWidget`，以及无 import 的 `UiFooterHost`。`UiButtonVariant` 只依赖 kit 的 `UiControlRole`。
- 三个滑块类都把交互委托给 kit，但暴露程度不同。`UiSlider` 委托 `UiScalarSlider` 并提供 `core()`；`UiRangeBar` 委托 `UiRangeSlider` 并提供 `core()`；`UiCyclicTimeBar` 委托 `UiCyclicRange`，其 `core` 为私有字段，没有对应的公开访问器。
- `UiDescriptionTooltip` 与 `UiNarration` 不实现 `UiWidget`；`UiTypePicker` 只实现 `UiWidget`，不实现 `UiFocusTarget`，因而不提供 `canFocus`、`activate` 与 `accessibleName`。`UiModalStack#top` 在栈空时返回 null，声明处未使用 `@Nullable`。
- `UiTypePicker` 的 `setOptions`、`setBounds`、`cancelCapture` 为包级可见，只供同包的 `UiConditionGraphEditor` 调用；其公开入口为构造、`isOpen`、`open`、`close`。
- `ConditionGraphLayout`、`ConditionGraphRenderer`、`ConditionGraphSidePanel`、`ConditionGraphInlinePanel` 的类声明均无 `public` 修饰符；四者之间的常量与 `drawBorder` 通过静态 import 复用，`ConditionGraphSidePanel` 同时被 `UiTypePicker` 复用。
- 公开方法普遍使用行内 `//` 注释而非 JavaDoc：`UiListView#setItems`、`UiTreeView#setRoots`、`UiSegmentedControl#setOptions`、`UiSlider#setValue` 的前一行均为 `//` 注释，`UiRangeBar#setLabel` 前无任何注释。带 JavaDoc 块的公开方法见 `UiConditionGraphEditor`、`UiListView`、`UiTypePicker`、`UiCatalogGrid`、`UiModal`、`UiModalStack`、`UiRangeBar`、`UiCyclicTimeBar`、`UiDescriptionTooltip`、`UiTextArea`、`UiButton`。
- `UiNarration` 为私有构造的静态工具类，仅包装 `Minecraft#getNarrator` 的播报，本包其它类型未引用它。
- `UiCheckBox` 的选中标记以字符绘制，不使用贴图；`UiCheckBox` 只有 `UiHostTheme`、`Font`、标签与初始状态四个构造参数，没有单独的选择标记样式入口。
- `UiWidget` 契约本身不包含焦点、无障碍与历史快捷键成员，这三类能力由 kit 的 `UiFocusTarget`、`UiHistoryShortcut` 等类型另行实现；本包中 `UiModal`、`UiButton`、`UiListView` 等以组合方式接入。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `ConditionGraphInlinePanel` | 条件叶参数面板：按路径维护锚点，负责动画、表单几何与输入捕获。 | `setBounds`、`mount`、`close`、`isOpen`、`validateAnchor`、`hasCapture`、`cancelCapture`、`contains`、`render`、`mouseClicked`、`mouseDragged`、`mouseReleased`、`mouseScrolled`、`keyPressed`、`charTyped` |
| `ConditionGraphLayout` | 条件画布的布局与展示数据：身份索引、诊断、标签与卡片几何。 | `record Hotspot`、`record NodeInfo`、`BORDER`、`CARD_HEIGHT`、`CARD_PADDING_X`、`CARD_PADDING_Y`、`CARD_ROW_HEIGHT`、`CARD_SEPARATOR`、`CONTENT_TOP`、`TITLE_BAND_HEIGHT`、`TITLE_BAND_TOP`、`LOGIC_CUT_CORNER`、`FLOW_HINT_KEY`、`isContainer` |
| `ConditionGraphRenderer` | 条件画布绘制：读取布局与选择状态，绘制卡片、连线和手势反馈。 | `drawDotGrid`、`drawCanvasBackground`、`drawEmptyMessage`、`drawLinks`、`drawCards`、`drawHotspots`、`drawDropMarker`、`drawDragGhost`、静态 `drawBorder` |
| `ConditionGraphSidePanel` | 右侧面板的几何、滑入动画与外观；内容由选择器或参数面板绘制。 | `SIDE_PANEL_PADDING`、`SIDE_PANEL_HEADER`、`start`、`tick`、`reset`、`bounds`、`isClosed`、`render` |
| `ConditionTreeContract` | 条件树控件家族共享的宿主注入契约与路径常量。 | `ROOT_PATH`、`record TypeOption`、`interface LeafFactory`、`interface Listener`、`interface Navigation` |
| `ConditionTreeInspection` | 条件树共用校验：限额判定、节点状态与违规项。 | `DEEP_HINT_DEPTH`、`isIncomplete`、`isLeafMissing`、`isOverflow`、`isDeep`、`nodeIssues`、`validateCandidate`、`enum IssueKind`、`record Issue` |
| `UiButton` | 按钮控件：委托 kit 控件处理状态与焦点，绘制主题立体边或胶囊底图。 | `apply`、`setLabel`、`setIcon`、`setChipStyle`、`setAction`、`setTooltip`、`variant`、`isPressed`、`preferredWidth`、`mouseClicked`、`mouseReleased`、`activate` |
| `UiButtonVariant` | 按钮变体：只表达语义与是否可交互，颜色由主题给出。 | `PRIMARY`、`SECONDARY`、`DANGER`、`DISABLED`、`role`、`interactive` |
| `UiCatalogGrid` | 目录选择页：搜索框与分列条目列表，底部行由弹窗按钮行托管。 | `PAGE_SIZE`、`setEntries`、`setEntrySource`、`setEntryPolicy`、`setSelection`、`selection`、`setPage`、`setPageLimits`、`setFooterReserved`、`interface Listener`、`enum Scope`、`interface EntryPolicy`、`record Entry`、`record Texts` |
| `UiCheckBox` | 复选框：左侧像素勾选框与右侧标签文本。 | `BOX_SIZE`、`LABEL_GAP`、`preferredWidth`、`setChecked`、`toggle`、`setOnChanged`、`isChecked` |
| `UiConditionGraphEditor` | 条件树编辑器：协调结构操作、选择、拖拽、键盘导航与宿主回调。 | `interface InlineEditor`、`tree`、`setTypeOptions`、`setLeafFactory`、`setListener`、`setExpression`、`expression`、`selectedSubtrees`、`beginAddCondition`、`beginAddGroup`、`beginWrapWithKind`、`removeSelection`、`moveSelection`、`pasteNodes`、`setInlineEditor`、`clearInlineEditor`、`resetView`、`fitToContent`、`zoomStep`、`hint` |
| `UiConditionTreeSummary` | 条件树只读缩进摘要，并作为进入画布编辑器的入口。 | `setExpression`、`expression`、`setOnOpenCanvas`、`issues`、`setTypeOptions`、`setLeafDetail`、`expandAll`、`collapseAll`、`selectedPath`、`selectPath`、`tooltipAt` |
| `UiCyclicTimeBar` | 昼夜时间条：区间刻域、跨零点与预设由宿主注入。 | `PERIOD`、`DRAG_STEP`、`MIN_TICK`、`MAX_TICK`、`setRange`、`setPresets`、`setTicks`、`setFormatter`、`setPreciseEditor`、`openPrecise`、静态 `baseHeight`、静态 `preferredHeight`、`interface Listener`、`record Texts`、`record Preset` |
| `UiDescriptionTooltip` | 条件节点与动作参数共用的悬停说明面板：按语义行色折行并自绘底色与描边。 | `record Line`、`render` |
| `UiFooterHost` | 由弹窗按钮行托管底部内容的控件接口。 | `setFooterReserved` |
| `UiListEditor` | 列表编辑器：条目列表加一行内嵌文本输入。 | `ROW_HEIGHT`、`INPUT_HEIGHT`、`VISIBLE_ROWS`、`MAX_ENTRY_LENGTH`、`enum Mode`、`input`、`setItems`、`addItem`、`removeSelected`、`moveSelected`、`commitPendingInput`、`restorePendingInput`、`preferredHeight` |
| `UiListView` | 可滚动列表控件：选中、悬停、键盘导航、滚轮与滚动条拖拽。 | `DEFAULT_ROW_HEIGHT`、`interface RowRenderer`、`setItems`、`setSelectedIndex`、`setOnSelectionChanged`、`setOnActivate`、`setChipRows`、`rowBounds`、`visibleRowBounds`、`itemIndexAt`、`ensureVisible`、`acceptsFocusAt` |
| `UiModal` | 模态弹窗：标题栏、内容区与按钮栏，内容控件的事件由弹窗转发。 | `DEFAULT_WIDTH`、`MIN_WIDTH`、`BUTTON_HEIGHT`、`interface ContentRenderer`、`contentWidget`、`inlineFooter`、`addButton`、`confirm`、`cancel`、`layoutAt`、`layoutCentered`、`preferredHeight`、`contentRect`、`addFocusTargets`、`onClosed`、`onHistoryChanged` |
| `UiModalStack` | 模态弹窗栈：遮罩与淡入淡出、居中与逐层偏移、焦点切换与输入隔离。 | `FADE_TICKS`、`push`、`top`、`modals`、`focusManager`、`closeTop`、`clear`、`tick`、`isMouseOverModal`、`requestHistory`、`setHistoryTarget` |
| `UiNarration` | 旁白适配：把可访问名称交给 Minecraft narrator 播报。 | `focus`、`say` |
| `UiRangeBar` | 区间条：像素渲染、端点缺失提示、精确输入行与一次操作一次提交的回调。 | `interface Listener`、`record Texts`、`core`、静态 `baseHeight`、静态 `preferredHeight`、`setLabel`、`setPreciseEditor`、`openPrecise`、`endInteraction`、`unmount`、`onFocusScopeChanged`、`onHostClosed` |
| `UiSegmentedControl` | 分段选择控件：把少量互斥取值渲染成一排等宽按钮。 | `record Option`、`setOptions`、`options`、`selected`、`selectedIndex`、`setSelected`、`setOnChanged`、`preferredWidth` |
| `UiSlider` | 数值滑杆：标签、当前值与像素轨道；数值域与交互委托 kit 核心。 | `TRACK_HEIGHT`、`THUMB_WIDTH`、`core`、`setRange`、`setValue`、`trySetValue`、`isBackfillInvalid`、`setFormatter`、`setOnChanged`、`setError`、`formatValue`、`endInteraction` |
| `UiTextArea` | 多行文本编辑框：换行、选择、滚动与剪贴板由原版文本模型承担。 | `value`、`setValue`、`setMaxLength`、`setEditable`、`setSelectableWhenReadOnly`、`setOnChanged`、`setOnOverflow`、`setOnCancel`、`setRows`、`height`、`moveCursorToStart`、`endInteraction` |
| `UiTextInput` | 单行文本输入框：关闭原版边框，由主题绘制内凹框。 | `value`、`setValue`、`clear`、`setMaxLength`、`setFilter`、`setOnOverflow`、`setOnValueChanged`、`setEditable`、`setHint`、`setOnCommit`、`setOnCancel`、`selectAll`、`moveCursorToStart`、`endInteraction` |
| `UiTreeNode` | 树节点：业务值、显示文本、子节点与展开状态。 | `addChild`、`value`、`label`、`children`、`isLeaf`、`isExpanded`、`setExpanded`、`toggleExpanded`、`expandAll`、`collapseAll` |
| `UiTreeView` | 可展开的树视图控件：可见行承载渲染与命中。 | `INDENT_WIDTH`、`MARKER_WIDTH`、`DEFAULT_ROW_HEIGHT`、`interface NodeRenderer`、`setRoots`、`addRoot`、`roots`、`setRowHeightProvider`、`expandAll`、`collapseAll`、`selectedNode`、`nodeAt`、`nodeContentBounds` |
| `UiTypePicker` | 条件与行为共用的右侧类型选择面板：搜索、键盘选择与滑入外观。 | `record Option`、`isOpen`、`open`、`close`、`setOptions`、`setBounds`、`cancelCapture` |
| `UiWidget` | 通用控件的渲染与输入契约。 | `bounds`、`setBounds`、`render`、`isVisible`、`mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`keyReleased`、`charTyped` |
| `package-info.java` | 包说明：本包控件遵循同一 `UiWidget` 契约。 | 无（仅包声明与包说明） |

## 4. 相关文档

- [客户端前端实现文档索引](README.md)
- [页面层：编辑器屏幕与模式页](ui-screen.md)
- [页面层：表单与叶面板](ui-screen-form.md)
- [基础组件层：UI kit 模块入口](ui-kit/README.md)
- [基础组件层：数值策略与滑块](ui-kit/value-controls.md)
- [基础组件层：输入路由与捕获](ui-kit/input-routing.md)
- [基础组件层：文本历史与选择](ui-kit/text-controls.md)
- [主题层](ui-theme.md)
- [展示工具层](ui-shared.md)
- [客户端分层职责与边界](../layers/client.md)
