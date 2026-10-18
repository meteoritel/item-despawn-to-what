# 页面层：表单、叶面板与条件树画布页（`client/ui/screen/form`）

> 层级：页面层；模块路径：`client/ui/screen/form`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/screen/form`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| `client/ui/screen/form` | `client/ui/screen/form` | §2 |

## 2. 模块：`client/ui/screen/form`

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.screen.form`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/screen/form`
- 覆盖：35 个 `.java` 文件（本包无 `package-info.java`）

### 2.2 职责

- 把 `TypeEditorDescriptor` 的字段列表渲染成可滚动的一列表单控件。
- 按 `EditorFieldType` 选择字段控件，并提供控件基类的校验、文本过滤与数值换算辅助。
- 把指针、键盘与滚轮事件分发给候选值选择框、行内目录按钮、行尾操作按钮与字段控件。
- 维持字段文本的会话内缓冲，并只把合法且发生变化的字段值经 `EditSession` 写回 `RuleDraft`。
- 提供条件树的摘要控件、条件树画布页外壳与 kit 原语的适配层。
- 提供目录候选与候选值选择框两种候选入口。
- 提供规则、条件与效果的只读自然语言摘要与消耗语义分类。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `FormView` | class | `构造`、`setDescriptor`、`addField`、`setCatalogOpener`、`setSelectionCatalogOpener`、`setOnFieldWritten`、`setSuggestionProvider`、`setConditionEditingSupport`、`setConditionContextFactory`、`setRowAction`、`setRowHeight`、`hideRowLabel`、`setRowVisible`、`setRowVisibility`、`suppressRowTooltip`、`setRowDefaultText`、`setRowDefaultValue`、`setRowSliderWindow`、`setItemCountReferences`、`useCardSpacing`、`useCatalogSelections`、`useCompactSpacing`、`setOnRejectedNotice`、`setLabelWidth`、`setOnChanged`、`reload`、`reloadWith`、`retainPendingInputs`、`commitPendingInputs`、`applyToDraft`、`pendingValue`、`issues`、`pendingInputIssue`、`revealPath`、`focusTargets`、`tick`、`isEditing`、`layout`、`preferredHeight`、`contentHeight`、`bounds`、`setBounds`、`isVisible`、`setVisible`、`setEnabled`、`onFocusScopeChanged`、`unmount`、`onHostClosed`、`draftPath`、`render`、`tooltipAt`、`openPicker`、`isPickerOpen`、`closePicker`、`mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`keyReleased`、`charTyped`、`viewportX`、`viewportY`、`viewportWidth`、`viewportHeight` | 表单视图；实现 `UiWidget`、`PickerHost`、`FormPicker.Host` |
| `FormView.CatalogOpener` | interface | `open(EditorField, boolean, Consumer<List<String>>)` | 追加式目录入口 |
| `FormView.SelectionCatalogOpener` | interface | `open(EditorField, String, boolean, List<String>, Consumer<List<String>>)` | 返回完整选择快照的目录入口 |
| `LeafPanel` | class | `构造`、`reload`、`addNote`、`contentHeight`、`bounds`、`setBounds`、`endInteractions`、`unmount`、`onHostClosed`、`setEnabled`、`focusTargets`、`focusField`、`render`、`mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`keyReleased`、`charTyped`、`canFocus`、`setFocused`、`isFocused`、`activate`、`accessibleName` | 叶面板；实现 `UiWidget`、`UiFocusTarget` |
| `ConditionTreeOverlay` | class | `构造`、`editor`、`session`、`scope`、`setHistoryActions`、`historyChanged`、`unmount`、`discardView`、`reveal`、`render`、`mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`keyReleased`、`charTyped` | 条件树画布页；实现 `UiWidget`、`UiFocusTarget` |
| `ConditionTreeOverlay.Parameters` | record | `form`、`panel`、`title` | 叶参数表单与承载面板 |
| `ConditionTreeOverlay.ParameterFactory` | interface | `create(String, ConditionNode.Leaf)`、`carriesField(String, ConditionNode.Leaf, String)` | 参数面板工厂 |
| `ConditionEditContext` | interface | `scope`、`ownerPath`、`absolutePath`、`open` | 一次条件编辑的局部目标上下文 |
| `ConditionEditContextFactory` | interface | `create(String, String)` | 条件编辑上下文工厂 |
| `ConditionEditingSupport` | interface | `typeOptions`、`leafFactory`、`registry` | 条件编辑的共享定义能力 |
| `ConditionHostCoordination` | interface | `prepareHost`、`closeView`、`discardView` | 条件编辑的宿主协调责任 |
| `CatalogSuggestions` | class | 构造、`suggestions(EditorField)` | 目录候选提供器；实现 `SuggestionProvider` |
| `SuggestionProvider` | interface | `suggestions(EditorField)` | 输入建议提供者 |
| `Suggestion` | record | `value`、`label` | 输入建议项 |
| `ConsumptionSummary` | class | `kind`、`hint`、`hintRule`、`isDuplicate`、`consumeEffectCount` | 消耗语义摘要 |
| `ConsumptionSummary.Kind` | enum | `IMPLICIT`、`EXPLICIT_SOURCE`、`EXPLICIT`、`DUPLICATE` | 消耗语义类别 |
| `NaturalSummary` | class | `rule`、`conditions`、`effects`、`effect`、`conditionLeaf`、`conditionTreeLeaf`、`conditionParams` | 规则、条件与效果的自然语言摘要 |
| `ReadOnlyTextView` | class | `构造`、`bounds`、`setBounds`、`render`、`mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`canFocus`、`setFocused`、`isFocused`、`activate`、`accessibleName` | 按宽度折行的只读详情视图；实现 `UiWidget`、`UiFocusTarget` |

包内可见类型不导出：`FormControl` 与 `FormControl` 的全部子类、`FormControlRegistry`、`FormRow`、`FormRowLayout`、`FormRowRenderer`、`FormInputRouter`、`FormDraftWriter`、`FormPicker`、`PickerHost`、`ConditionTreeChrome`。

### 2.4 依赖关系

- 依赖本模组：`client/edit`（27 个文件 import，共 100 处；`EditorField`、`RuleValue`、`EditorFieldType`、`FormIssue`、`EditSession`、`DraftPaths`）
- 依赖本模组：`client/ui/kit`（20 个文件 import，共 72 处；`UiFocusTarget`、`UiRect`、`TextScroll`、`UiInputCapture`）
- 依赖本模组：`client/ui/widget`（19 个文件 import，共 44 处；`UiButton`、`UiCheckBox`、`UiConditionGraphEditor`、`UiWidget`）
- 依赖本模组：`client/ui/theme`（12 个文件 import，共 21 处；`UiHostTheme`、`UiHostThemes`、`UiFlatSkin`）
- 依赖本模组：`client/ui/shared`（6 个文件 import，共 13 处；`FieldCatalogTypes`、`CatalogPreviewIcons`、`RuleDisplayLabels`）
- 依赖本模组：`client/net`（1 个文件 import，共 1 处；`CatalogSuggestions` 引用 `RuleEditClientWorkspace`）
- 依赖本模组：`core/api`（7 个文件 import，共 7 处；`RuleFields`、`TypeRegistry`）
- 依赖本模组：`core/model`（4 个文件 import，共 5 处；`ConditionExpression`、`ConditionNode`、`ConditionType`、`CatalystCost`）
- 依赖本模组：`core/network/protocol`（3 个文件 import，共 5 处；`RuleCatalog`、`RuleCatalogEntry`、`RuleCatalogType`）
- 依赖本模组：`core/type/effect`（1 个文件 import，共 1 处；`ConsumeSourceEffect`）
- 依赖外部：`net.minecraft`（27 个文件 import，共 71 处；`Component`、`Font`、`GuiGraphics`、`ResourceLocation`、`FormattedCharSequence`、`Minecraft`、`Screen`）
- 依赖外部：`java.util`（25 个文件 import，共 55 处）
- 依赖外部：`java.util.function`（8 个文件 import，共 14 处）
- 依赖外部：`org.jetbrains.annotations`（24 个文件 import，共 24 处；`Nullable`）
- 依赖外部：`org.lwjgl.glfw`（6 个文件 import，共 6 处；`GLFW`）
- 被依赖：`client/ui/screen`（11 个文件 import，共 24 处；`FormView`、`LeafPanel`、`NaturalSummary`、`ConsumptionSummary`、`ConditionEditingSupport`、`SuggestionProvider`）

### 2.5 关键实现位置

- `FormView#setDescriptor`：清空现有行并按描述符字段逐个调用 `addField` 装配。
- `FormView#addField`：启用目录选择时为可目录化字段装配 `CatalogSelectionControl`，其余交给 `FormControl#create`。
- `FormControl#create`：数值类型先试 `NumberControl#create`，域元数据不足时回退到 `FormControlRegistry#create`。
- `FormControlRegistry#factories`：`EditorFieldType` 到控件工厂的静态登记表；未登记类型由 `#create` 回退为 `RawJsonControl`。
- `FormControl#load`、`FormControl#store`：控件级双向搬运的抽象入口；`#rememberLoaded`、`#markEdited`、`#originalValue` 提供未编辑判定。
- `FormRowLayout#layoutRows`：按可用宽度计算标签列、控件宽度与行尾按钮换行结果，并写回 `FormRow`。
- `FormRowLayout#syncBounds`、`#maxScroll`、`#rowScreenY`：把行布局结果同步为屏幕坐标并给出滚动上限。
- `FormRowRenderer#drawLabel`、`#drawNote`、`#renderScrollbar`、`#tooltipAt`：只读行标签、说明行、滚动条与悬停提示的绘制与命中。
- `FormInputRouter#mouseClicked`：按选择框、行内目录按钮、行尾操作按钮、字段控件的顺序派发并记录按下归属。
- `FormInputRouter#keyPressed`：选择框优先；聚焦控件消费后，`Enter` 对非 `LONG_TEXT` 字段执行一次提交；未消费时处理 `PAGE_UP`/`PAGE_DOWN` 滚动。
- `FormDraftWriter#retainPendingInputs`：把与装载基线不同的控件文本写入会话输入缓冲，不修改 JSON。
- `FormDraftWriter#applyToDraft`：逐字段比较草稿值与控件值，仅对变化字段写入并记录一条撤销操作。
- `FormDraftWriter#writeField`：单字段写入入口；`fluids` 字段写入时移除同对象的 `fluid` 子字段，`catalyst_items` 写入时按引用裁剪 `item_counts`。
- `FormView#revealPath`：条件树行先向 `ConditionTreeControl#revealPath` 要精确落点，普通控件先滚动高亮再让控件处理嵌套定位。
- `FormView#pick`、`FormView#addPickers`：目录按钮的装配与选择结果回写；`catalogSelections` 为真时按钮文案使用 `pick.add`。
- `FormView#openPicker`：按 `SuggestionProvider` 的候选创建 `FormPicker`，并把当前字段值作为已选项传入。
- `FormPicker#render`、`#mouseClicked`、`#keyPressed`、`#charTyped`：候选值选择框的绘制、指针命中与键盘操作。
- `ConditionTreeControl#load`、`#store`、`#issues`：条件表达式解码、编码写回；解码失败时 `fallbackRaw` 原样保留为只读值。
- `ConditionTreeControl#revealPath`：把字段路径转换为树内路径并转交 `ConditionEditContext#open`。
- `ConditionTreeOverlay#reveal`：按草稿路径定位树内节点或参数字段；`#historyChanged` 只回填不提交。
- `ConditionTreeOverlay` 的界面组成：功能栏（撤销、重做、JSON 视图、帮助）、竖直工具列 7 格（添加条件、分组、取反、删除、上移、下移、包组）、画布与底部状态栏；右侧面板宽度上限 220、滑入动画 160ms ease-out（`ConditionGraphSidePanel#SIDE_PANEL_MAX_WIDTH`、`#SIDE_PANEL_ANIM_MS`）。
- `UiConditionGraphEditor#transform`：以 `UiTransform.ofScales(0.5, 1.0, 1.5, 2.0)` 提供四档缩放；`Ctrl+0` 适配视图、`Ctrl+Home` 复位、`Ctrl+=` 与 `Ctrl+-` 缩放、`Ctrl+A` / `Ctrl+C` / `Ctrl+V` 全选 / 复制 / 粘贴。
- `UiConditionGraphEditor#mouseClicked`：空白区左键拖拽平移；`Shift` + 拖拽框选；中键或空格 + 左键平移；内联参数框聚焦时画布快捷键让位。
- 只读 JSON 视图：功能栏同一按钮在条件树与只读 JSON 之间切换，JSON 文本可整段复制，不写入草稿。
- 叶参数面板：`ConditionTreeOverlay.ParameterFactory#create` 返回 `ConditionTreeOverlay.Parameters`（`form`、`panel`、`title`），表单由 `RuleEditorPanelFactory#leafPanel` 装配。
- `ConditionTreeChrome.Strip`：把 `UiIconStrip` 的指针上下文与焦点契约适配到 `UiWidget`/`UiFocusTarget`。
- `ConditionTreeChrome.Inline#preferredHeight`：按候选宽度对叶面板做一次测量探针后返回内容高度。
- `CatalogSuggestions#suggestions`：按字段注册表与字段类型解析目录类型，去重后最多返回 `MAX_SUGGESTIONS` 条候选。
- `CatalogSelectionControl#arrange`：按名称宽度把卡片排成多行，超过三行时启用内部滚动。
- `NumberControl` 与 `ClimateControl` 委托 kit `UiValueTextBinding` 管理滑条与精确文本同步、预览与取消快照；单位换算、气候留空端的边界映射、字段校验与草稿保存由表单控件处理。数值操作取消或无净变化时恢复原始文本，保留单端留空和原始数值表示。
- `NumberControl#setWindow`：只调整滑条常用窗口，不修改合法域、草稿值或正在输入的缓冲。
- `ItemCountsControl#setReferences`：按引用集合重建行，保留既有数量的控件状态。
- `SublistControl#createRemoveButton`：删除子项后对索引在其后的子表单执行 `FormView#rebase` 以同步输入路径。
- `ConsumptionSummary#kind`：按三类消耗效果的声明数量返回 `Kind`；`#isDuplicate` 与 `#consumeEffectCount` 复用同一计数口径。
- `NaturalSummary#conditionTreeLeaf`：条件树摘要使用「条件名（参数摘要）」格式；`#conditionParams` 对内建类型取短摘要，未注册类型按字段名兜底。

### 2.6 代码现状（可核对）

- 跨层 import：本包对 `client/net` 只有一处引用，即 `CatalogSuggestions.java` 的 `import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;`；`CatalogSuggestions` 在 `entriesOf` 中调用 `RuleEditClientWorkspace.instance()` 与 `RuleEditClientWorkspace#directory(RuleCatalogType)`。
- 缺类级 JavaDoc 的包内控件类：`BoolControl`、`ClimateControl`、`ConditionTreeControl`、`EnumControl`、`ListControl`、`NoteControl`、`RawJsonControl`、`SublistControl`、`TextControl` 均为 `final class ... extends FormControl` 的包内类型，无类级 JavaDoc；其中 8 个文件以 `// ---- ... ----` 分节注释代替类级说明，`ClimateControl` 只有一行 `//` 说明。
- 其余 26 个文件有类级 JavaDoc，包含全部 13 个公开类型；包内类型中有类级 JavaDoc 的是 `FormControl`、`FormControlRegistry`、`FormRow`、`FormRowLayout`、`FormRowRenderer`、`FormInputRouter`、`FormDraftWriter`、`FormPicker`、`CatalogSelectionControl`、`ConditionTreeChrome`、`ItemCountsControl`、`NumberControl`。
- 条件树画布页实现在本包：`ConditionTreeOverlay` 位于 `client/ui/screen/form`，不在 `client/ui/screen`；页面层的 `RuleEditorTriggerPage` import 本包的 `ConditionTreeOverlay`、`FormView`、`LeafPanel` 等类型作为画布打开入口。
- 缺类级 JavaDoc 的嵌套类型：`FormControlRegistry.Factory`、`ConsumptionSummary.Kind`、`ConditionTreeOverlay.FocusSlot`（private）、`ItemCountsControl.Entry`（private static）。
- 两套目录与选择入口：`FormPicker` 是本包内 `final class`，由 `FormView#openPicker` 构造、`FormView#closePicker` 关闭；`UiCatalogGrid` 属 `client/ui/widget`，本包没有它的 import，目录由宿主以 `FormView.CatalogOpener` 或 `FormView.SelectionCatalogOpener` 注入。
- 两套输入入口：`FormInputRouter` 是本包内 `final class`，`FormView` 的七个输入方法全部委托给它；`RuleEditorPageInput` 属 `client/ui/screen`，遍历 `RuleEditorEditPages#liveWidgets` 派发页面级输入；两者互不 import。
- 类级 JavaDoc 与实现不一致：`FormInputRouter` 的类级 JavaDoc 写派发顺序为「选择框 → 行尾按钮 → 字段控件 → 表单滚动」，`FormInputRouter#mouseClicked` 实际依次遍历 `FormRow#pickers`、`FormRow#actions`、`FormControl`，未在注释中区分前两类按钮。
- 同概念多种命名：本包条件相关类型用 `ConditionTree*`（`ConditionTreeControl`、`ConditionTreeChrome`、`ConditionTreeOverlay`）；`client/ui/widget` 的同一能力用 `UiConditionGraphEditor` 与 `UiConditionGraphEditor.InlineEditor`，摘要控件用 `UiConditionTreeSummary`。定位成员存在三种写法：`FormControl#revealPath`、`FormView#revealPath` 与 `ConditionTreeOverlay#reveal`。
- JavaDoc 含历史表述：`FormControlRegistry` 的类级 JavaDoc 写「与拆分前 switch 的 default 分支一致」。
- 全限定名使用不一致：`CatalogSuggestions#suggestions` 用 `java.util.Set<String>` 声明局部变量，同文件其余集合类型已 import；`FormView#setCatalogOpener` 与 `FormView#pick` 直接构造 `java.util.LinkedHashSet`。
- `FormView` 的 import 分组不连续：`com.meteorite.itemdespawntowhat.client.ui.widget.UiButton`、`UiButtonVariant`、`UiWidget` 三条 import 位于 `java.util.*` 之后，与文件开头的同模组依赖分组分开。
- 说明行不写 JSON 的实现分散在三处：`NoteControl#setBounds` 与 `NoteControl#render` 为空实现，`NoteControl#store` 返回 `null`；`FormView#render` 对 `EditorFieldType.NOTE` 行调用 `FormRowRenderer#drawNote`；`FormView#storeObject` 与 `FormView#issues` 都跳过 `NOTE` 字段，`FormView#pathFor` 对 `NOTE` 与 `RAW_JSON` 返回 `basePath`。
- `FormView#setCatalogOpener` 标注 `@SuppressWarnings("unused")`；它只把追加式入口包装后委托给 `#setSelectionCatalogOpener`，两者共用 `catalogOpener` 字段。
- 本包 35 个 `.java` 中不含 `package-info.java`，包级说明只出现在各类型 JavaDoc。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `BoolControl` | 布尔字段的复选框控件；未编辑时返回装载值。 | `#usesLabelColumn`、`#load`、`#store` |
| `CatalogSelectionControl` | 注册表字段的紧凑选择卡片，按名称测宽换行，支持滚动、键盘导航与逐项删除。 | `#measure`、`#load`、`#store`、`#keyPressed` |
| `CatalogSuggestions` | 目录候选提供器，把表单字段映射到服务端目录类型并产生 `Suggestion`。 | `#suggestions` |
| `ClimateControl` | 气候区间控件：匹配勾选框、区间条与 `min`/`max` 精确输入。 | `#load`、`#store`、`#issues` |
| `ConditionEditContext` | 条件编辑的局部目标上下文接口。 | `#scope`、`#ownerPath`、`#absolutePath`、`#open` |
| `ConditionEditContextFactory` | 按字段路径与所属对象创建条件上下文的工厂接口。 | `#create` |
| `ConditionEditingSupport` | 条件类型清单、新叶工厂与解码注册表的共享能力接口。 | `#typeOptions`、`#leafFactory`、`#registry` |
| `ConditionHostCoordination` | 条件宿主的打开前提交、正常关闭刷新与失效丢弃接口。 | `#prepareHost`、`#closeView`、`#discardView` |
| `ConditionTreeChrome` | kit 原语接入 `UiWidget` 分发的适配层。 | `Strip`、`Inline` |
| `ConditionTreeControl` | 条件树摘要控件与打开画布按钮；解码失败时保留只读原始 JSON。 | `#load`、`#store`、`#issues`、`#revealPath` |
| `ConditionTreeOverlay` | 条件树画布页外壳：功能栏、工具列、画布、状态栏与叶参数内联框。 | `#reveal`、`#historyChanged`、`#discardView`、`#unmount` |
| `ConsumptionSummary` | 消耗语义分类、提示与重复声明判定。 | `#kind`、`#hint`、`#hintRule`、`#isDuplicate`、`#consumeEffectCount` |
| `EnumControl` | 枚举字段的分段选择控件。 | `#load`、`#store` |
| `FormControl` | 表单控件基类、字段控件工厂与文本/数值辅助函数。 | `#create`、`#load`、`#store`、`#issues`、`#revealPath` |
| `FormControlRegistry` | `EditorFieldType` 到控件工厂的静态登记表。 | `#create` |
| `FormDraftWriter` | 表单装载、会话输入保留与逐字段回写草稿。 | `#reload`、`#reloadWith`、`#retainPendingInputs`、`#applyToDraft`、`#writeField` |
| `FormInputRouter` | 表单级输入分发与按下归属记录。 | `#mouseClicked`、`#keyPressed`、`#charTyped` |
| `FormPicker` | 候选值选择框：过滤输入框与可选列表。 | `#render`、`#keyPressed`、`#endInteraction` |
| `FormRow` | 表单行数据载体：字段、控件、可见性与布局结果。 | `#refreshButtons` |
| `FormRowLayout` | 行布局与滚动几何计算。 | `#layoutRows`、`#syncBounds`、`#maxScroll` |
| `FormRowRenderer` | 行标签、说明行、滚动条与悬停提示的绘制与命中。 | `#drawLabel`、`#drawNote`、`#renderScrollbar`、`#tooltipAt` |
| `FormView` | 表单视图：装配行、布局、输入委托、定位与目录入口。 | `#setDescriptor`、`#addField`、`#layout`、`#applyToDraft`、`#revealPath`、`#openPicker` |
| `ItemCountsControl` | 物品数量对象控件：每个引用一行滑条与精确输入。 | `#setReferences`、`#setDefaultCount`、`#load`、`#store` |
| `LeafPanel` | 叶面板：附加控件、目录按钮与表单纵向组合，维护 Tab 焦点顺序。 | `#reload`、`#addNote`、`#focusField`、`#contentHeight` |
| `ListControl` | 字符串与 id 列表控件，按字段类型选择 `UiListEditor` 模式。 | `#load`、`#store`、`#inputText`、`#restoreInputText` |
| `NaturalSummary` | 规则、条件与效果的自然语言摘要静态工具。 | `#rule`、`#conditions`、`#effects`、`#conditionTreeLeaf`、`#conditionParams` |
| `NoteControl` | 只读说明行，不写入 JSON。 | `#store`、`#note` |
| `NumberControl` | 数值控件：kit 滑杆、行内精确输入与预设按钮。 | `#load`、`#store`、`#setWindow`、`#setValueFromSuggestion` |
| `PickerHost` | 候选值选择框宿主接口，包内使用。 | `#openPicker` |
| `RawJsonControl` | 未识别字段的只读 JSON 摘要控件。 | `#load`、`#store`、`#summary` |
| `ReadOnlyTextView` | 按可用宽度折行并可滚动的只读详情视图。 | `#setBounds`、`#render` |
| `SublistControl` | 对象数组控件：每项一个子表单，提供添加与逐项删除。 | `#load`、`#store`、`#retainChildInputs`、`#finishInput` |
| `Suggestion` | 输入建议项：值写回字段，标签给玩家看。 | 组件 `value`、`label` |
| `SuggestionProvider` | 输入建议提供者接口。 | `#suggestions` |
| `TextControl` | 文本控件：单行输入或长文本区，附预设按钮与解析/格式化函数。 | `#load`、`#store`、`#setValueFromSuggestion`、`#displayDefault` |

## 4. 相关文档

- [前端文档索引](README.md)
- [页面层：屏幕与页面编排](ui-screen.md)
- [组件层：控件与条件树组件](ui-widget.md)
- [状态/数据层：编辑会话与草稿](client-edit.md)
- [基础组件层：排版、控件与绘制](ui-kit/document-and-controls.md)
- [基础组件层：输入路由](ui-kit/input-routing.md)
- [展示工具层](ui-shared.md)
- [主题层](ui-theme.md)
- [文档阅读与维护约定](../conventions.md)