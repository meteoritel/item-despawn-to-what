# UI 代码简洁性审查（维度一：重复/冗余/长函数/魔法值）

审查对象：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/**`（kit / theme / widget / screen / screen/form / prototype），旁及 `client/edit`、`client/net`、`client/key` 中直接影响 UI 的部分。定位一律用「相对路径 + 类名.成员名」，不写行号。

## 结论概览（高优先级）

| ID | 定位 | 一句话结论 |
| --- | --- | --- |
| H1 | `RuleEditorScreen.ButtonBar` / `RuleEditorEditPages` / `FormView` / `FormControl` / `RuleEditorP4Panels` | 「按宽度换行的流式排布」同一算法在 5 个类里手写了 8 份，且测量与命中已存在数值漂移 |
| H2 | `kit/UiCyclicRange` 与 `kit/UiRangeSlider` | 同层两个滑块核心大段同构：140 行逐行相同、8 段连续 ≥10 行，最长 35 行 |
| H3 | `widget/UiCyclicTimeBar` 与 `widget/UiRangeBar` | 同层两个区间条 175 行逐行相同（相似度 0.663），最长连续段 55 行 |
| H4 | `RuleEditorEditPages` 全类 | 会话取用前奏 `host.session()` 32 次、null 守卫 18 次、`session.draft()` 21 次重复，无统一入口 |
| H5 | `RuleEditorEditPages.buildResults` | 156 行单体方法，且候选列表与效果列表的接线块近乎逐行重复 |
| H6 | `kit/UiFloatingPanel`、`kit/UiLinearLayout`、`kit/UiNavigationHistory`、`kit/UiSliderExamples` | 4 个 kit 类共 705 行，在生产 Java 代码中零引用（仅 package-info 与 dev 文档提及） |
| H7 | `screen/form/ConditionTreeOverlay` | 自建 4 槽焦点循环（FocusSlot/cycleFocus/focusOrder/applyFocus），未复用 kit 既有 `UiFocusManager` |
| H8 | `kit/UiScalarSlider.formatValue` 与 `widget/UiRangeBar.formatValue` | 同一「整数直出 / 否则两位小数」规则两套实现，且对非有限值处理不一致 |
| H9 | `screen/form/FormControl` 与 `screen/form/NumberControl` | 同包内两套数值解析（parseDouble/boundedInt/boundedDouble 对 parseFinite/parseExactInt/withinDomain） |
| H10 | `RuleEditorScreen.localIssues` | 137 行校验方法，内部含 3 组同形重复校验块，且与 `RuleEditorEditPages.issues()` 职责交叠 |

共 37 条：高 10 条、中 18 条、低 9 条。完整清单见下文分组。

## 数据基线（本次实测）

| 指标 | 数值 |
| --- | --- |
| UI 目录 | 128 个 .java |
| 单文件最长方法 | 156 行（`RuleEditorEditPages.buildResults`） |
| ≥100 行的方法 | 5 个 |
| ≥45 行的方法 | 40 个 |
| 方法总数（配平度量） | 2380 |
| 最大嵌套深度 | 5 层（`FormView.layoutRows`、`CatalogSelectionControl.keyPressed`） |
| 相似度最高的同层文件对 | `UiCyclicTimeBar`/`UiRangeBar` 0.663；`UiCyclicRange`/`UiRangeSlider` 0.581 |
| 未使用 private 成员 | 0（启发式扫描全量 UI 文件） |

## 一、重复逻辑（DRY）

### H1 流式换行布局算法 8 份手写实现

**定位**
- `screen/RuleEditorEditPages.layoutTrigger`
- `screen/RuleEditorEditPages.layoutButtonGroup`
- `screen/RuleEditorP4Panels` 内部类 `LeafPanel.layoutButtons`
- `screen/RuleEditorScreen` 内部类 `ButtonBar.rows`、`ButtonBar.layout`
- `screen/form/FormControl.ConditionTreeControl.layoutToolbar`
- `screen/form/FormView.layoutRows` 与 `FormView.syncBounds`

**问题**：8 处都在实现同一算法——游标累加、`cursor + width > 右边界` 时回行首并把 y 下推一个「行高 + 间距」。差异只在度量函数、间距常量和是否均分剩余宽度，全部靠复制粘贴维持。

**影响**：[editor-card-layout.md](../dev/internals/editor-card-layout.md) 要求「测量与命中矩形共用同一组按钮」，但现在 `FormView.layoutRows` 用 `controlWidth` 夹取按钮宽，`syncBounds` 用 `viewportWidth - row.controlRelX` 夹取，同一行按钮在测量与命中下可能得出不同的行数；`ButtonBar.rows` 与 `ButtonBar.layout` 同样各自算一遍换行。任何一处调整行高、间距或最小宽都可能只改到一半，扩展新面板时还要再抄第 9 份。

**建议**：在 kit 增加一个无状态工具（例如 `UiFlowLayout.wrap(items, x, y, maxWidth, measureFn, lineHeight, gap)`，返回每项矩形或行数），`ButtonBar`/`FormView`/`FormControl`/`RuleEditorP4Panels`/`RuleEditorEditPages` 全部改为调用它，测量与排布共用一个入口。注意 `kit/UiLinearLayout` 明确声明「不做自动换行」，可直接作为该工具的基座改造，而不是并排再加第三个 kit 布局类。

**优先级**：高

### H2 `kit/UiCyclicRange` 与 `kit/UiRangeSlider` 同层大段同构

**定位**：`client/ui/kit/UiCyclicRange.java`（555 行）与 `client/ui/kit/UiRangeSlider.java`（606 行）。典型重合：`mouseReleased`、`keyReleased`、`keyPressed` 的 Left/Down/Right/Up/Home/End/Escape 分支。

**问题**：两者同为 kit 层「区间交互核心」，SequenceMatcher 度量相似度 0.581、逐行相同 140 行、连续 ≥10 行的同构段 8 个（最长 35 行），集中在输入生命周期上：`UiInputCapture` 持有与释放、`dragging`/`heldKey` 状态机、key-up 合并提交、Esc/Home/End 语义。二者不构成继承关系，也没有共享基类。

**影响**：鼠标释放归属契约、按住方向键只在 key-up 合并提交、取消回退——这些是全局输入契约，现在有两份实现。改一处契约要么漏改另一处，要么两个控件对同一次按键给出不同结果；新增第三种区间控件（例如周期标量）时仍会从其中一份再复制。

**建议**：抽出 kit 内的抽象基类（例如 `AbstractRangeCore`），承载 `UiInputCapture`、`dragging`/`heldKey`/`visible`/`enabled`、`mouseReleased`、`keyReleased`、`endInteraction` 骨架与「一次操作 = 一次提交」的模板方法；`UiCyclicRange` 与 `UiRangeSlider` 只保留各自的值域与吸附规则。kit 层内部抽基类不违反任何依赖方向。

**优先级**：高

### H3 `widget/UiCyclicTimeBar` 与 `widget/UiRangeBar` 同层大段同构

**定位**：`client/ui/widget/UiCyclicTimeBar.java`（499 行）与 `client/ui/widget/UiRangeBar.java`（433 行）。典型重合：`mouseClicked` 的「精确输入优先 → 边界命中 → 转发 core.mousePressed」三段、`mouseDragged` 的 core/precise 二选一转发、以及 `drawPresets` 的预设条绘制与端点提示居中文本。

**问题**：两类的交互语义确实分别委托给 `UiCyclicRange` / `UiRangeSlider`（这一点是正确设计），但宿主侧的转发胶水与绘制细节仍是复制：相似度 0.663、逐行相同 175 行、最长连续段 55 行。两者还各自独立声明了 `LABEL_HEIGHT = 10`、`TRACK_ROW_HEIGHT = 12` 等同类常量。

**影响**：新增区间类控件（高度条、温度带等）时，`preciseActive` 判定顺序、`closePrecise()` 时机、`context(x, y, button)` 构造这些细节要再抄一遍；任一处调整（例如「精确输入框失焦时是否提交」）容易只改一个控件，导致两个控件的输入手感不一致。

**建议**：抽出宿主侧共享基类或组合式 `RangeBarChrome`（持有 `UiTextInput precise` + `preciseActive` + `closePrecise()` + `context(...)` + 共享尺寸常量），两个控件只提供各自的 core、文本与绘制钩子。

**优先级**：高

### H4 `RuleEditorEditPages` 会话取用前奏重复 32/18/21 次

**定位**：`screen/RuleEditorEditPages` 全类。`EditSession session = host.session();` 32 次、`if (session == null) { return; }` 18 次、`RuleDraft draft = session.draft();` 21 次、`ResultStructure.listPath(draft.view(), candidateIndex)` 5 次。例：`addEffectOfType`、`duplicateEffect`、`removeEffect`、`moveEffectBy`、`moveEffect`。

**问题**：每个编辑动作都重复同一段四行前奏，且都以「静默 return」处理假值分支。这段前奏是「当前会话 + 当前草稿 + 当前候选列表路径」的同一业务含义，却在 30 余处各写一遍。

**影响**：新增一个编辑动作要先抄前奏，漏写 null 守卫会直接 NPE（`host.session()` 在非编辑态确实可为 null）；若将来草稿获取方式变化（例如多会话），需要改 30 余处；方法实际业务逻辑被前奏稀释，可读性下降。

**建议**：在类内增加集中入口，例如 `private @Nullable EditSession session()`、`private @Nullable RuleDraft draft()`、`private String listPath(RuleDraft draft)`，并用一个 `private boolean withDraft(Consumer<RuleDraft> action)` 包裹「取会话—取草稿—执行」三态，把 null 分支收敛到一处。

**优先级**：高

### H5 `RuleEditorEditPages.buildResults`：156 行单体 + 列表接线块重复

**定位**：`screen/RuleEditorEditPages.buildResults`。重复块：候选列表接线与效果列表接线。

**问题**：方法在一个方法体内完成候选数量夹取、单方案判定、候选列表装配、策略勾选框（安全生成/原位填充）、组合模式分段控件、效果列表装配、动作描述符解析与主/高级表单拆分、按钮组追加共 8 类职责，嵌套 4 层。其中「建列表 → setRowHeight → setEmptyMessage → 建索引列表 → setItems → rebuilding 抑制 → setSelectedIndex → setOnSelectionChanged（内含 rebuilding 复位 + blockNavigation 回退） → setOnActivate → 登记 liveWidgets」这一段在两个列表上几乎逐行相同（`rebuilding = true;` 在本文件出现 4 次）。

**影响**：编辑结果页的任何一处（例如新增一种产物策略）都要在 156 行里定位，回归面大；两个列表接线继续各自演进，很容易出现「候选列表能回退选择、效果列表忘了回退」这类不对称缺陷。

**建议**：先抽出 `private <T> UiListView<T> buildList(rowRenderer, emptyMessage, rowHeight, items, selected, onSelected, onActivate)`，把 rebuilding 抑制与 blockNavigation 回退封装进去；再把 `buildResults` 拆为 `buildCandidatePlan(...)`（方案层）、`buildEffectDetail(...)`（动作详情层）、`buildStrategyControls(...)`（策略勾选与组合模式）三段，主方法只做顺序编排。

**优先级**：高

### H6 kit 中 4 个零引用类共 705 行

**定位**：`kit/UiFloatingPanel.java`（360 行）、`kit/UiLinearLayout.java`（212 行）、`kit/UiNavigationHistory.java`（44 行）、`kit/UiSliderExamples.java`（89 行）。

**问题**：在本仓库全部 417 个 Java 文件（common + fabric + neoforge）中检索类名，这 4 个类除了被 `kit/package-info.java` 的 javadoc 提及外，没有任何代码引用；对应的 dev 文档（`docs/dev/internals/ui-kit/*`）仍把它们列为 kit 组成。`UiSliderExamples` 从命名到内容都是演示样例；`UiFloatingPanel` 是已经不再使用的浮动调板（当前内联参数框走 `ConditionTreeChrome.Inline`）。

**影响**：kit 是「公共契约层」，其文件数与 API 面直接影响后续扩展时的检索成本与维护承诺；705 行无消费者的代码会持续产生「这个类是不是该用」的判断负担，并且与 H1 中 8 份手写换行布局形成直接冲突——库里躺着一个未被使用的布局类，业务侧却在重复实现布局。

**建议**：按 [ui-kit README](../dev/internals/ui-kit/README.md) 与 ADR [0025](../adr/0025-reusable-client-ui-kit-boundary.md) 的边界标准逐类判定：无保留理由（无宿主接入计划、无契约价值）即删除并同步更新 README 与 `package-info`；若 `UiLinearLayout` 确有计划承载 H1 的流式换行，则应在本次 H1 改造中落地，而不是继续悬空。

**优先级**：高

### H7 `ConditionTreeOverlay` 自建焦点循环，未复用 kit `UiFocusManager`

**定位**：`screen/form/ConditionTreeOverlay`：`FocusSlot` 枚举、`setFocusSlot`、`applyFocus`、`cycleFocus`、`focusOrder`，以及 `keyPressed` 的 Tab 分支。

**问题**：kit 已提供 `UiFocusManager`（`kit/UiFocusManager.java`），负责「按视觉顺序持有焦点目标、唯一决定当前焦点、Tab/Shift+Tab 循环、清除失效焦点」，`RuleEditorScreen` 正在使用它。`ConditionTreeOverlay` 却在类内又实现了一遍等价逻辑：手写 `focusOrder()` 过滤不可聚焦项、`cycleFocus()` 取模回绕、`applyFocus()` 逐目标写焦点。

**影响**：屏幕层与画布页出现两套焦点规则，Tab 顺序、焦点清除时机（目标失效、视图切到 JSON）、以及未来「焦点变化时滚动到可见」这类横切需求都要改两处；`ConditionTreeOverlay` 作为单个 `UiFocusTarget` 交给上层管理器，两个层级之间没有共享状态，容易在嵌套焦点下产生「上层以为是 A、下层以为是 B」的不一致。

**建议**：在 `ConditionTreeOverlay` 内持有一个私有 `UiFocusManager` 实例（kit 类，screen/form 已依赖 kit，无新增依赖），把 `functionBar`/`toolColumn`/`source`/`editor` 作为目标登记，Tab 直接委托给它的 `keyPressed`；删除 `FocusSlot` 与 5 个私有方法。

**优先级**：高

### H8 数值格式化两套实现

**定位**：`kit/UiScalarSlider.formatValue`、`widget/UiRangeBar.formatValue(double)`。

**问题**：两者都是「整数则输出整数字符串、否则 `String.format(Locale.ROOT, "%.2f", value)`」，但整数判定与边界处理不同：前者用 `Math.abs(value - Math.rint(value)) < 1.0E-6D` 且不处理非有限值，后者用 `Math.rint(value) == value` 且把非有限值显示为 `-`。`widget/UiSlider.formatValue` 则是转调 `core.formatValue()`。

**影响**：同一份主题里，标量滑条与区间条对 `1.0000001`、`Double.NaN` 的显示结果不一致；这是最容易在截图比对中被发现、也最难解释的一类差异。数值格式是「显示策略」，本质应只有一处定义。

**建议**：把该规则下沉为 kit 的纯函数（例如挂在 `UiNumberPolicy` 上或新增 `UiNumberFormat.text(double)`），`UiScalarSlider`、`UiRangeBar`、`RuleEditorP4Panels.clockText` 等调用同一入口；非有限值的显示文案作为参数由宿主传入（宿主负责 i18n）。

**优先级**：高

### H9 同包内两套数值解析

**定位**：`screen/form/FormControl.parseDouble`、`boundedInt`、`boundedDouble` 与 `screen/form/NumberControl.parseText`、`parseExactInt`、`parseFinite`、`withinDomain`。

**问题**：`FormControl.parseDouble` 与 `NumberControl.parseFinite` 是同一算法（trim → 空判 → `Double.parseDouble` → `isFinite` 过滤 → null）；`boundedInt` 与 `parseExactInt + withinDomain` 覆盖同一语义（含 int 溢出拒绝）。此外 edit 层已有 `FieldNumbers` 承担百分比、amplifier、精度等换算，`FormControl` 却在它旁边另起一套通用解析。

**影响**：同一字段类型可能被两条解析路径处理（数值字段优先走 `NumberControl`，回退走 `TextControl` + `boundedXxx`），两条路径对极值、空白、溢出的处理一旦分叉，就会出现「同一输入在滑条入口合法、在文本入口非法」的行为差异。扩展新的数值域要同时照顾两处。

**建议**：把「文本 → 有限 double / 精确 int」与「域夹取」统一到 edit 层的 `FieldNumbers`（或 `screen/form` 内的单一 `NumericText` 工具），`FormControl` 与 `NumberControl` 都改为委托调用；删除重复的私有静态方法。

**优先级**：高

### H10 `RuleEditorScreen.localIssues` 137 行且含三组同形校验块

**定位**：`screen/RuleEditorScreen.localIssues`。

**问题**：方法把「必填项」「effects/outcomes 互斥」「规则级与效果级条件树」「候选 id 唯一性与候选效果非空」「名称/备注的 Unicode 码点上限」「触发秒数非负」「源成本与催化剂成本」「结构版本」「消耗效果重复记账」共 9 类校验串成一条 137 行的直线；其中：
- 名称与备注的码点上限判定重复两次，仅字段、上限与 key 不同；
- 催化剂 count 与 radius 的区间判定重复两次，仅字段与上下界不同；
- 候选内效果条件树与 effects 内效果条件树的路径拼接是同一「索引 → 路径 → validateConditionTree」形状。

**影响**：本地校验是服务端校验的镜像，必须在新增字段时同步扩展；137 行直线里找插点容易漏项，且重复块的阈值（128 / 1024）与字段分离后无法集中审阅。

**建议**：抽出三个小助手——`checkCodePointLimit(value, path, limit, issueKey)`、`checkIntRange(owner, field, min, max, path, issueKey)`、`validateEffectConditions(effects, parentPath, draft, issues)`；主方法改为按校验主题分段调用，目标压到 40 行以内。

**优先级**：高

### M1 `UiConditionGraphEditor` 与 `UiConditionTreeSummary` 的同构输入与生命周期骨架

**定位**：`widget/UiConditionGraphEditor`（2637 行）与 `widget/UiConditionTreeSummary`（1573 行）。重合示例：构造器中 `UiTreeEditor` + `MoveErrors` 文案装配、`setEnabled`/`setVisible` + `endInteractions` + `closePicker`、`issues()`/`selectedNode()`/`undoOpKey()` 一类只读访问器。

**问题**：两者共享同一棵 `UiTreeEditor` 适配、同一套校验文案（`tree.invalid_path` / `tree.invalid_leaf` 等 key 在两文件重复出现）、同一套「禁用/隐藏时结束交互并关浮层」语义、同一套选择与撤销操作 key。相似度度量 0.172，但有 8 段连续 ≥10 行的逐行相同块。

**影响**：画布页与列表页是同一编辑能力的两套视图，契约（校验规则、禁用语义、撤销 key 命名）必须一致；现在每加一条校验或一个生命周期钩子都要改两处，且没有任何机制提醒漏改。

**建议**：把共同部分上提为 widget 层的共享基类或组合对象（例如 `ConditionTreeController`：持有 `UiTreeEditor`、文案、校验、选择与撤销 key），两个编辑器只负责各自的排布、命中与绘制；文案 key 集中到一处常量，避免同一 key 在两类里各写一遍。

**优先级**：中

### M2 `UiListView` 与 `UiTreeView` 的滚动/选择/命中同构

**定位**：`widget/UiListView.java`（403 行）与 `widget/UiTreeView.java`（585 行）。

**问题**：相似度 0.534、逐行相同 104 行、连续 ≥10 行块 6 个（最长 29 行），集中在滚动窗口计算、选中行绘制、滚轮与拖拽命中、空态提示上。

**影响**：两者是 widget 层并列的基础列表控件，滚动条几何与选中绘制一旦需要统一调整（例如主题改描边），要改两处；扩展「多选」「悬停行」等能力也会各做一遍。

**建议**：抽出共享的「行窗口 + 滚动条 + 选中/悬停绘制」组合对象供两者使用；行的内容渲染继续各自注入（`RowRenderer` / `NodeRenderer` 已经是这个形状，可沿用）。

**优先级**：中

### M3 `Component.translatable(UI + "…")` 244 处，无统一取词入口

**定位**：`RuleEditorEditPages` 81 处、`RuleEditorScreen` 81 处、`RuleEditorP4Panels` 34 处、`ConditionTreeOverlay` 24 处、`RuleRecipeView` 13 处、`SourceBlacklistPicker` 5 处等。

**问题**：每个屏幕层类各自声明私有前缀常量 `UI`，然后在调用点做字符串拼接。同一 key 会在不同类里各写一遍字面量（例如 `button.cancel` 在 4 个类出现 12 次、`button.confirm` 7 次、`issue.candidate_limit` 单文件 5 次）。

**影响**：本地化 key 是魔法字符串最集中的区域。拼接使 key 无法被静态检查，改名时靠全文替换，漏改会静默回退到显示原始 key；同一 UI 短语在中英两份 json 中的存在性也无法在调用点核对。

**建议**：为屏幕层引入集中取词入口（例如在 edit 层已有的 `TypeLabels.UI_PREFIX` 之上再加一个 `RuleTexts.of(String suffix)`，或在每个类内提供 `private static Component t(String suffix)`），把 `UI + "button.cancel"` 收敛为 `t("button.cancel")`；进一步可把按钮/提示类 key 转为枚举或常量表，使「新增文案必须同步 en_us 与 zh_cn」有落点。

**优先级**：中

### M4 `if (blockNavigation()) return;` 重复 19 次

**定位**：`RuleEditorEditPages` 6 次（另有 `blockNavigation()` 调用共 12 次）、`RuleEditorScreen` 7 次。

**问题**：导航拦截是「存在未提交输入时不允许离开当前页」的同一业务判定，在每个入口（打开弹窗、切页、删除、重排、返回）都被复制成同一个 3 行守卫。

**影响**：新增入口时容易漏写守卫，导致某条路径可以在未提交状态离开；守卫内部若增加「先确认再拦截」等行为，需要改 19 处。

**建议**：改为返回布尔的高阶入口，例如 `private boolean guarded(Runnable action)`（拦截时直接返回 false 并提示），调用点写成 `guarded(this::addCandidate)`。

**优先级**：中

### M5 `UiModal` 装配六连重复 14 处

**定位**：`UiModal.create(font)` 赋值点 14 处（`RuleEditorScreen` 7、`RuleEditorEditPages` 5、`RuleEditorP4Panels` 1、`SourceBlacklistPicker` 1），随后固定为 `title(...)` / `contentWidget(...)` / `confirm(...)` / `cancel(...)` / `layoutCentered(host.screenWidth(), host.screenHeight())` / `host.modals().push(modal)`；`layoutCentered(host.screenWidth(), host.screenHeight())` 在 `RuleEditorEditPages` 单文件 6 次，`button.cancel` 文案拼接 12 次。

**问题**：每个弹窗都重复「建 → 标题 → 内容 → 确认/取消 → 居中 → 入栈」六步，且居中尺寸与取消文案每次都从宿主重新取。

**影响**：新增弹窗要抄 6 行样板；若弹窗栈改为支持层级动画或统一尺寸策略，需要逐个改；取消文案/居中规则不集中，容易出现个别弹窗尺寸不一致。

**建议**：在 `RuleEditorEditPages.Host` 上提供一个 `UiModal modal(Component title)`（内部完成 create + 取消文案 + 居中 + 入栈），或做成小 builder（`ModalSpec.title(t).content(w, h).confirm(label, action).show()`）。

**优先级**：中

### M6 `RuleEditorP4Panels.catalogModal` 三版重载共 8/9/10 个参数

**定位**：`screen/RuleEditorP4Panels.catalogModal`。

**问题**：同类方法有三个重载（8 / 9 / 10 个参数），8 参与 9 参版本各自逐级转发到参数更全的一版；最全的参数列表为 `font, workspace, type, multiSelect, title, screenWidth, screenHeight, onPicked, field, combined`。`font` / `screenWidth` / `screenHeight` 三者始终成组来自同一个宿主，`workspace + type + multiSelect + field + combined` 描述「选什么」，`title + onPicked` 描述「怎么用」。

**影响**：调用点必须严格记住最多 10 个位置参数（其中 3 个是同类 `Component`/`int`，顺序写错不会编译报错）；扩展一个开关就要再加一个重载或参数。

**建议**：引入请求值对象，例如 `CatalogPickerRequest(workspace, type, multiSelect, field, combined)` 与 `CatalogPickerHost(font, screenWidth, screenHeight)`，方法签名降为 `catalogModal(host, request, title, onPicked)`；调用点用具名构造，顺序错误在编译期暴露。

**优先级**：中

### M7 `FormControl.create` 76 行工厂

**定位**：`screen/form/FormControl.create`。

**问题**：一是方法体 76 行、承担「数值优先分流 + 18 个 switch 分支」两层调度；二是 `RESOURCE_LOCATION` 与 `REGISTRY_ID` 两个分支的 3 行分支体逐字相同，可直接合并为一个 case 标签组；三是传给 `TextControl` 的过滤器参数 `textFilter()` 恒返回 `true`，是无效参数。

**影响**：工厂是所有字段类型的唯一入口，长度决定了新增字段类型的成本；恒真过滤器让调用者以为存在输入限制，实际限制在别处（输入框长度/解析器），语义误导。

**建议**：合并两个同体 case；删除 `textFilter()` 参数或改为 `null` 表示「无过滤器」并在 `TextControl` 内判空；把 switch 的每类分支下沉为小型 `static FormControl createXxx(...)`，`create` 只保留数值优先分流与分派。

**优先级**：中

### M8 `UiFlatPalette` 大量 1:1 别名

**定位**：`theme/UiFlatPalette.java`。示例：`STATUS_TEXT`/`PANEL_TEXT`/`CANVAS_TEXT`/`HELP_TEXT`/`CANVAS_PICKER_TEXT` 五个名字都等于 `UiPalette.TEXT_PRIMARY`；四个 `*_TEXT_MUTED` 都等于 `UiPalette.TEXT_SECONDARY`；`CANVAS_ACCENT` = `UiPalette.ACCENT`、`FOCUS_OUTLINE` = `UiPalette.FOCUS_OUTLINE` 等。

**问题**：类头注释写明「语义相同的基础色直接引用 UiPalette，避免同一含义维护两份字面量」——字面量确实只有一份，但同一语义出现了 4–5 个别名常量，别名本身不携带新语义（既不改变值，也不表达状态）。

**影响**：新增画布配色时无法判断该加别名还是直接用 `UiPalette`；阅读方需要来回跳转确认两个名字是否同值；未来 `UiPalette` 改色时别名会「正确跟随」，但「扁平主题是否仍应跟随」这一设计问题被别名掩盖。

**建议**：只保留画布页确实需要区分的扁平专属色（`0xFF…` 字面量那批与 `CANVAS_*` 中改变明度层次者）；纯转引用的别名改为直接用 `UiPalette` 成员。

**优先级**：中

### M9 `UiFlatSkin.drawFocusOutline` 与 `UiTheme.drawFocusOutline` 语义重复

**定位**：`theme/UiFlatSkin.drawFocusOutline`（转 `drawOutline`）与 `theme/UiTheme.drawFocusOutline`。

**问题**：两者都表达「焦点轮廓」，但一个画 1 像素实心同色框、另一个画 4 条边（注释称「虚线感」）。两套主题并列是 ADR [0028](../adr/0028-canvas-page-and-flat-skin.md) 的既定设计，但焦点轮廓的几何构造仍可共享，只是颜色与线型不同。

**影响**：焦点提示是可达性相关表现，两套实现各自演化会让键盘用户在两套主题下看到不同的焦点反馈范围（一个内缩 1 像素、一个贴边）。

**建议**：把「画 1 像素四边框」下沉为 kit 的绘制原语（`UiRenderLayers` 已是绘制原语归属地），两套主题只提供颜色与是否虚线感。

**优先级**：低

## 二、过长函数与过深嵌套

### M10 `UiConditionGraphEditor` 与 `UiConditionTreeSummary` 的输入方法过长

**定位**：`UiConditionGraphEditor.mouseClicked` 100 行、`keyPressed` 100 行；`UiConditionTreeSummary.mouseClicked` 76 行、`keyPressed` 76 行。

**问题**：`mouseClicked` 依次处理可见性、浮层（picker / kindMenu）、内联框命中的坐标换算转发、平移手势（中键或空格+左键）、添加模式热区、框选、节点命中、双击激活、拖动预备，共 9 类职责；`keyPressed` 依次处理可见性、两类浮层、拖动中取消、内联框优先、Ctrl 快捷键 7 个分支、空格、Esc、空树新增、方向键、Enter、Delete、A/G/N，共 7 类职责。此外 `keyPressed` 中「空树分支」的 `KEY_A/ENTER → beginAddCondition` 与 `KEY_G → beginAddGroup` 与后面的同名单键分支逐字重复，仅前置条件不同。

**影响**：输入方法是画布页最容易出回归的地方（浮层与内联框的优先级、拖动中按键、双击与拖动的区分）。100 行直线虽嵌套仅 2–3 层，但任一分支插入都可能改变前面分支的吞键行为；重复的 A/G 分支让「空树时按 A」与「非空树时按 A」存在两条维护路径。

**建议**：按职责切分并让主方法只做优先级编排——`handleOverlays(mouseX, mouseY, button)`、`handlePanGesture(...)`、`handleNodePress(...)`、`handleEmptyArea(...)`；`keyPressed` 抽出 `handleControlShortcuts(keyCode)`、`handleCanvasCommand(keyCode, shift)`，并合并重复的 A/G 分支为单一「新增条件/新增分组」处理（是否空树只影响后续选中行为）。

**优先级**：中

### M11 `FormView.layoutRows` 嵌套 5 层且与 `syncBounds` 重复计算换行

**定位**：`screen/form/FormView.layoutRows`（47 行、最大嵌套 5 层）与 `FormView.syncBounds`。

**问题**：`layoutRows` 在 `for (Row) { … for (button) { if (…) { … } } }` 中完成标签列宽、控件宽、按钮预留、是否下移、行内容高与光标推进，最深 5 层；按钮换行在 `layoutRows` 里用 `controlWidth` 计数、在 `syncBounds` 里用 `viewportWidth - row.controlRelX` 再算一遍。

**影响**：两处夹取上限不一致，测量出的行数与实际放置的行数可能不同，表现为按钮溢出视口或行高不足（契约「测量与命中矩形共用同一组按钮」的数值前提被破坏）；嵌套 5 层使「按钮下移」条件难以单独测试。

**建议**：先按 H1 抽出换行工具并在两处共用同一结果结构（行数 + 每项矩形），再拆 `layoutRows` 为 `measureLabelColumn`、`measureControl`、`measureButtonBlock` 三段。

**优先级**：中

### M12 `RuleEditorScreen.render` 80 行混合卡片绘制与页面分派

**定位**：`screen/RuleEditorScreen.render`。

**问题**：方法内同时做指针门控（`UiPointer.gated`）、scissor 开合、逐卡片绘制卡片底 + 标题短标记 + 加粗标题 + 分隔线、四页 render 分派、活动控件渲染、拖拽预览、滚动条绘制。卡片装饰绘制（底、标记、加粗标题、分隔线）在 `RuleEditorEditPages.render` 中又一次出现，形状相同。

**影响**：卡片外观调整需改两处；`render` 承担的分派与装饰耦合，使「跳过某页绘制」或「仅重绘卡片」这类优化无法局部进行。

**建议**：抽出 `drawPageCard(graphics, PageCard)`（底 + 标记 + 标题 + 分隔线）供两个 render 共用，主 render 只保留 scissor 与分派。

**优先级**：中

### M13 `RuleEditorP4Panels.CatalogMapper.map` 70 行

**定位**：`screen/RuleEditorP4Panels` 内部类 `CatalogMapper.map`。

**问题**：一个方法内完成「资源/语言代际失效判定 → 目录与标签取用 → 范围与条目映射（含 4 类特殊排除）→ 标签预览缓存 → 过滤（含 `#` 前缀语义）→ 分页切分 → 状态字段回写」。`language` 与 `resources` 两个字段被声明为 `Object`，连注释都没有，读者无法判断比较的是哪种身份。

**影响**：目录映射是输入页的核心链路，70 行里混合了缓存失效与业务映射两类关注点，修改分页逻辑必须通读缓存判定；`Object` 字段让「比较的是 Language 实例还是 generation 计数」只能靠猜，是潜在的缓存永不失效/过度失效来源。

**建议**：拆为 `refreshIfStale(workspace, type)`（返回是否需重建）、`rebuildEntries(...)`、`applyFilterAndPage()` 三段；把 `language`/`resources` 改为具体类型，必要时用一个小 record 表达「目录代际」。

**优先级**：中

## 三、冗余、可合并与无用代码

### M14 `CatalogMapper` 弱类型字段与空语句块

**定位**：`screen/RuleEditorP4Panels.CatalogMapper.language` / `resources`（`Object` 类型）；`catalogModal` 内 `onSearch` / `onPageChange` 的空白尾随块。

**问题**：`Object` 字段无法从声明读出语义；两个 listener 方法在赋值后各留一个空行级空白块，形似未完成的代码。

**影响**：弱类型字段降低可读性并有类型混淆风险；空白块会被误读为遗漏逻辑。

**建议**：字段改为具体类型并加一行说明其身份；删除空白块。

**优先级**：低

### M15 构造器未使用参数

**定位**：`widget/UiListView` 构造器（`@SuppressWarnings("unused") Font font`）、`widget/UiTreeView` 构造器、`widget/UiConditionTreeSummary` 构造器。

**问题**：三个控件的公开构造器都接收 `Font` 但从不使用，靠 `SuppressWarnings` 压住警告。`UiConditionGraphEditor` 则确实使用了 `font`。

**影响**：调用方（`RuleEditorEditPages`、`RuleEditorScreen`、`ConditionTreeOverlay`）传递了一个被忽略的参数，形成「字体可以配」的错误预期。

**建议**：确认无需字体后从签名移除（同步改调用点），或在 javadoc 中说明保留原因。

**优先级**：中

### L1 `RuleEditorScreen` 冗余全限定名

**定位**：`screen/RuleEditorScreen.recipeViews`；同类字段级全限定声明另有 `screen/RuleEditorP4Panels.tagPreviews`、`screen/RulePreviewIcons.ICONS`、`kit/UiLightbox.icons`。

**问题**：`recipeViews` 用 `java.util.Map` / `java.util.LinkedHashMap` 全限定名声明，而该文件已 import 这两个类型，属与 import 清单重复的冗余写法；另外三处（`RuleEditorP4Panels`、`RulePreviewIcons`、`UiLightbox` 均未 import `java.util.Map`）是字段级的集合类型全限定声明，`UiLightbox.setIcons` 的参数同样如此。同一「集合类型写全限定名」的写法在本目录至少 4 处。

**影响**：声明风格不统一，读者需逐处判断全限定是「同名冲突所需」还是「随手写法」；`RuleEditorScreen` 那处与 import 清单直接重复，属应清理的残留。

**建议**：4 处统一改为 import + 短名；若确有同名类型冲突，在声明处加一行注释说明保留全限定的原因。

**优先级**：低

### L2 `UiTheme.drawInset` 重复填充

**定位**：`theme/UiTheme.drawInset`：两次 `graphics.fill` 使用同一个 `UiPalette.SLOT_FILL`。

**问题**：第一次填充整矩形、第二次填充内缩 1 像素矩形，颜色完全相同，第二次为冗余绘制（不产生任何视觉差异）。

**影响**：每帧多一次无效 fill 调用；读者会以为两次填充颜色不同而去找差异。

**建议**：删除第二次 fill（若原意是「内凹阴影」应使用不同色值或 `drawBevel`）。

**优先级**：低

### L3 `FormView.rowButtons` 纯转发

**定位**：`screen/form/FormView.rowButtons`：`return row.buttons;`。

**问题**：方法体只有一次字段返回，没有任何变换或缓存语义。

**影响**：读者会预期这里存在过滤（如「仅可见按钮」）而逐处确认；增加一层无信息的间接。

**建议**：内联为 `row.buttons`，或在确有过滤需求时实现该过滤并让调用点受益。

**优先级**：低

### L4 `UiTheme` 中重复的尺寸守卫

**定位**：`drawWindow`、`drawPanel`、`drawInset`、`drawHeader`、`drawBevel` 的 `if (rect.width() < 2 || rect.height() < 2) return;`；`drawSurface`、`drawSelection`、`drawScrollTrack` 的类似守卫。

**问题**：同一「太小则不画」判定在 8 个方法里各写一遍，阈值有 2 与 4 两种，且与 `UiFlatSkin.drawSurface`/`drawOutline` 的同类守卫重复。

**影响**：阈值不统一时，同一个矩形在不同绘制方法下可能一个画一个不画（例如 width=3 时 `drawBevel` 画、`drawFocusOutline` 不画），边框出现缺口。

**建议**：把最小可视尺寸定义为常量并有意识地统一（或明确记录各方法不同的原因）。

**优先级**：低

## 四、魔法字符串与魔法数字

### M16 常量已定义却仍使用字面量 `min` / `max`

**定位**
- `screen/RuleEditorP4Panels`：已定义 `RANGE_MIN_FIELD = "min"`、`RANGE_MAX_FIELD = "max"`，但 `isNumber(bounds.get("min"))`、`field(fields, "min")` / `field(fields, "max")`、`body.add("min", …)` / `body.add("max", …)`、`readRange(body.get("min"), body.get("max"))` 仍用字面量。
- `screen/form/FormControl`：已定义 `MIN_FIELD = "min"`、`MAX_FIELD = "max"`，但 `object.has("min")` / `object.get("min")` 等与 `object.addProperty("min", …)` 仍用字面量。

**问题**：这两个字符串是规则 JSON 的字段名（`RuleFields` 域），常量已存在却被绕过使用。

**影响**：字段改名时全文替换会漏掉字面量点，导致读写路径不一致（读到一个 key、写到另一个 key），且失败是静默的。

**建议**：统一改用常量；若 `min`/`max` 属于 `RuleFields` 的通用子字段，应下沉到 edit/core 层的常量，避免 `screen` 与 `screen/form` 各定义一份同名常量。

**优先级**：中

### M17 按钮度量的魔法数 `24` / `6` / `4` / `2`

**定位**：`RuleEditorScreen.ButtonBar`：`Math.clamp(button.preferredWidth(6), Math.min(24, width), width)` 同一表达式出现 3 次；`RuleEditorEditPages.layoutButtonGroup` 用 `preferredWidth(PAD)` + `Math.min(24, width)`；`FormControl.ConditionTreeControl.layoutToolbar` 用 `preferredWidth(4)` + `Math.min(24, limit)`；`RuleEditorP4Panels.LeafPanel.layoutButtons` 用 `preferredWidth(4)`。

**问题**：`6`/`4` 是「按钮文本左右内边距」，`24` 是「按钮最小宽度」，两个值在 4 个类中各写字面量，含义只存在于阅读者脑中；同一常量在同文件内还出现 3 次。

**影响**：主题或字体变化导致按钮观感不统一时，需要全文搜索数字；不同类用了 4 与 6 两种内边距，同一界面的按钮宽度其实不一致。

**建议**：把「按钮最小宽」「文本内边距」「按钮间距」提到 theme 层（`UiTheme` 已有 `ROW_HEIGHT`/`PADDING` 等尺寸常量），4 个类统一引用；结合 H1 的换行工具一并消除重复表达式。

**优先级**：中

### M18 时间域常量在 kit 与 screen 各定义一份

**定位**：`widget/UiCyclicTimeBar`：`PERIOD = 24000.0D`、`MIN_TICK = 0.0D`、`MAX_TICK = 23999.0D`、`DRAG_STEP = 100.0D`。`screen/RuleEditorP4Panels`：`DAY_TICKS = 24000.0D`、`TIME_MIN = 0`、`TIME_MAX = 23999`、`DEFAULT_TIME_RANGE_END = 12000`，并在 `dayTicks()` 中使用 `1000.0D`、`24`、`3`，在 `clockText()` 中使用 `1440`、`24000`、`60`。

**问题**：同一「一天 24000 刻、合法刻域 0..23999」的事实有两份常量。`RuleEditorP4Panels` 已经 import `UiCyclicTimeBar`，复用其 `PERIOD`/`MIN_TICK`/`MAX_TICK` 不存在依赖方向问题。

**影响**：修改日长（例如支持自定义周期）时必须同时改两处，漏改会让宿主传入的刻域与控件内部周期不一致；`1000.0D`（每 3 小时刻数）、`1440`（一天分钟数）等换算中间量散落在方法体内，无法集中校核 `0 刻 = 06:00` 的换算。

**建议**：`RuleEditorP4Panels` 的时间常量改为引用 `UiCyclicTimeBar` 的公开常量；把「刻 → 时钟文本」所需的 `TICKS_PER_DAY / MINUTES_PER_DAY / DAY_OFFSET_MINUTES` 提为具名常量并加注释。

**优先级**：中

### M19 `UiNumberPolicy` 工厂中重复的字面量 `0.25D`

**定位**：`kit/UiNumberPolicy.of(double,double,double)`、`of(double,double,double,double)`、`integer(int,int,int)`、`integer(int,int,int,int)` 四处传入 `0.25D` 作为细调拖动系数。

**问题**：`0.25D` 是「细调拖动灵敏度」的默认值，出现在 4 个工厂方法的参数表里，没有名字。

**影响**：调整默认灵敏度需改 4 处；读者无法从 `0.25D` 得知其含义与合理范围（构造函数校验里该参数必须落在 `(0, 1]`）。

**建议**：提为具名常量 `DEFAULT_FINE_DRAG_FACTOR` 并在 javadoc 中说明含义。

**优先级**：低

### L5 `RuleEditorP4Panels.CLIMATE_FIELDS` 用字符串字面量数组

**定位**：`screen/RuleEditorP4Panels.CLIMATE_FIELDS`。

**问题**：六个气候维度字段名以 `String[]` 字面量列出，未复用规则模型层的字段常量。

**影响**：字段改名时此数组不在编译期受保护；后续扩展维度容易遗漏。

**建议**：若 `RuleFields` 已有对应常量则改为引用；若确实尚无，在 edit/core 层补一组常量并让 UI 引用。

**优先级**：低

## 五、参数过多与可提取公共方法

### M20 `RuleEditorEditPages` 三个行渲染器各 8 个参数

**定位**：`renderEffectRow`、`renderCandidateRow`、`renderTypeRow`；三者签名都是 `(GuiGraphics graphics, Font renderFont, T item, int index, UiRect row, boolean selected, boolean hovered, boolean focused)`。

**问题**：这三个参数组是「列表行渲染上下文」，其中 `graphics`/`renderFont`/`row`/`selected`/`hovered`/`focused` 六项完全同构；`font` 与 `graphics` 还可能与类字段重复。

**影响**：新增行渲染器必须逐字复刻 8 个参数；调整行渲染签名要同时改 3 处签名与 3 处调用；参数顺序（两个 boolean 相邻）容易写反。

**建议**：定义 `record RowRender(GuiGraphics graphics, Font font, UiRect bounds, int index, boolean selected, boolean hovered, boolean focused)`，渲染器签名降为 `(RowRender ctx, T item)`；若 `UiListView` 的 `RowRenderer` 契约允许，可把该 record 直接作为其参数。

**优先级**：中

### M21 `UiFocusTarget` 实现样板：`isFocused` 18 份、`isVisible` 19 份、`setFocused` 8 份、`activate` 6 份

**定位**：全 UI 目录。`isFocused() { return focused; }` 18 个文件（另有 2 处写作 `return this.focused;`）（UiControl、UiCyclicRange、UiIconStrip、UiRangeSlider、UiScalarSlider、UiCatalogGrid、UiCheckBox、UiConditionGraphEditor、UiConditionTreeSummary、UiCyclicTimeBar、UiListView、UiRangeBar、UiSegmentedControl、UiStructureDiagram、UiTextArea、UiTreeView 等）；`isVisible() { return visible; }` 19 个（另有 2 处写作 `return this.visible;`）；`setFocused(boolean f) { this.focused = f; }` 8 个；`activate()` 恒返回 `false` 6 个；`RuleEditorP4Panels`、`CatalogSelectionControl` 的 `canFocus()` 同为 `enabled && visible`。

**问题**：`kit/UiFocusTarget` 的 6 个方法全为抽象，而其中 `activate()`（Enter/Space 语义）在多数实现里固定返回 `false`，`isFocused()`/`setFocused()` 在每个实现里都退化为对一个布尔字段的读写。

**影响**：每新增一个控件都要抄 3–4 个样板方法；`activate()` 的默认语义分散在 6 处，若将来改为默认 `true`（点击即可激活）将无法一处生效。

**建议**：至少把 `activate()` 改为接口 `default` 返回 `false`（`UiWidget` 的输入方法已是这种风格，契约一致）；对 `focused` 字段的三件套考虑在 kit 提供一个小型 `UiFocusState` 组合对象或抽象基类。

**优先级**：中

### L6 `FormView` 中重复的按钮间距字面量

**定位**：`screen/form/FormView`（多处 `+ 2` 间距）。

**问题**：按钮之间、控件与按钮之间的间距在 `FormView` 里以字面量 `2` 反复出现，而同类间距在其他类是具名常量。

**影响**：表单密度调整（契约中的 4/6/20 行间距体系）在这几处不受常量控制，容易与主题不一致。

**建议**：把 `FormView` 的按钮相关间距提为具名常量（如 `BUTTON_GAP`），与既有的最小高度/行间距常量并列。

**优先级**：低

## 六、判定为「有意为之」的重复（不作为问题）

以下重复由架构边界或既有 ADR 明确要求，审查中已确认不构成可合并项：

1. `kit/UiInputTarget` 与 `widget/UiWidget` 的输入方法签名并列：`kit/UiInputTarget` 的类注释明确「kit 核心只依赖本接口，宿主控件通过适配方法把旧签名转发进来」，`kit` 不得依赖 `widget`。两套契约的方法集合（mousePressed/Dragged/Released/Scrolled、keyPressed/Released、charTyped）确有重叠，但这是依赖方向决定的，合并会让 kit 反向依赖宿主。符合 [0025](../adr/0025-reusable-client-ui-kit-boundary.md) 与 [client.md](../dev/layers/client.md)。
2. `screen/form/ConditionTreeChrome` 对 kit 原语的转发薄层：类注释说明「kit 不允许反向依赖 widget 包」，并约定「kit 签名调整时只需改这一个文件」。`Strip` 对 `UiIconStrip` 的 6 个方法转发属于适配成本，不是可消除的重复。
3. `theme/UiFlatPalette` / `UiFlatSkin` 与 `UiPalette` / `UiTheme` 并列：[0028](../adr/0028-canvas-page-and-flat-skin.md) 与 [editor-card-layout.md](../dev/internals/editor-card-layout.md) 的「画布页视觉规格」要求画布页独立配色、表单页不动。并列本身是有意设计（M8 只针对其中的 1:1 别名部分）。
4. `theme/UiTheme` 把 `UiPalette` 映射为 kit 的 `UiControlStyle` / `UiSliderStyle`：kit 不读主题，主题单向注入，属于契约要求的转译，不算重复。
5. `screen` 与 `screen/form` 的双向引用（`RuleEditorP4Panels`、`RuleDisplayLabels`、`TagPreviewIcons`、`FluidPreviewIcons`）：该双向关系已在基线中确认存在且被契约接受，本轮未发现由此产生的同一逻辑多份实现，故不列为简洁性问题。
6. `prototype/UiPrototypeScreen`（395 行）：仅由 `client/key/EditorShortcut` 在 `DebugMode.ENABLED` 下实例化，属受控调试入口，不计入死代码；其 `layout`（62 行）与 `kit/UiDocument.layout`、`kit/UiLightbox.layout` 形状相近但宿主不同，未确认共享收益，本轮不作为建议项。

## 七、审查范围、方法与未覆盖说明

**方法**：口径——文件数按 `.java` 计数，行数按原始行数（文件末尾换行不计为一行）；源文件编码经字节级校验确认为合法 UTF-8。对 `client/ui/**` 全量 128 个文件做三类机械度量，再对度量命中的文件逐一精读复核：

1. 去注释/去字符串后的花括号配平计数，取方法体行数与最大嵌套深度（已排除 record / class / enum 声明被误判为方法）；
2. 规范化（去注释、压缩空白、忽略纯括号行）后的 8 行滑动窗口哈希 + difflib.SequenceMatcher 对候选文件对做相似度与最长同构段量化；
3. 字符串/数字字面量频次、指令式惯用片段（会话前奏、守卫、换行、模态装配等）计数、私有成员引用计数、类名跨 417 个 Java 文件的引用检索。

精读覆盖：`RuleEditorEditPages`、`RuleEditorScreen`、`UiConditionGraphEditor`、`UiConditionTreeSummary`、`FormControl`、`NumberControl`、`FormView`、`RuleEditorP4Panels`、`UiCatalogGrid`、`ConditionTreeOverlay`、`ConditionTreeChrome`；kit 中 `UiCyclicRange`/`UiRangeSlider`/`UiScalarSlider`/`UiNumberPolicy`/`UiRangeValue`/`UiFocusManager`/`UiFocusTarget`/`UiInputTarget`/`UiWidget`/`UiLinearLayout`；theme 全 5 文件；widget 中 `UiCyclicTimeBar`/`UiRangeBar`/`UiListView`/`UiTreeView`/`UiButton`/`UiModal`。

**未覆盖与存疑**
- 未做运行时验证（本轮不编译、不运行），所有「无引用」结论基于全仓库源码文本检索；若存在反射、资源注册或构建期代码生成引用这些类名，需另行确认（本次未发现此类机制）。
- `client/net` 与 `client/key` 仅按「是否影响 UI」抽样（`EditorShortcut` 的调试入口），未做同等级别的简洁性度量。
- `prototype` 目录仅确认其受调试开关控制，未评估其内部 402 行的重复情况。
- H2/H3 的合并建议涉及输入契约（鼠标释放归属、一次操作一次提交），落地方案应与 [input-routing.md](../dev/internals/ui-kit/input-routing.md) 一并确认后实施。
