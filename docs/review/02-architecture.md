# 客户端 UI 代码架构审查

> 维度：组件分层、模块职责划分、状态管理方式、组件复用机制、依赖关系与耦合度、扩展点成本。
> 结论均可回到源码；定位采用「相对路径 + 类名.成员名」，不使用行号。
> 基线文档：[架构总览](../dev/architecture.md)、[客户端层](../dev/layers/client.md)、[UI kit 边界与公开面](../dev/internals/ui-kit/README.md)、[编辑器四页契约](../dev/internals/editor-pages-contract.md)、[编辑页卡片布局](../dev/internals/editor-card-layout.md)、[ADR-0022](../adr/0022-client-ui-kit-adoption.md)、[ADR-0025](../adr/0025-reusable-client-ui-kit-boundary.md)、[ADR-0028](../adr/0028-canvas-page-and-flat-skin.md)。
> 本轮只审查 Markdown，不修改任何 Java 源码。

## 1. 审查范围与方法

**范围**：`client/ui/**`（kit／theme／widget／screen／screen/form／prototype）为主，`client/edit`、`client/net`、`client/key` 仅在直接影响 UI 时纳入。

**实测规模**（按文件行数统计，与 Lead 基线一致）：

| 包 | 文件数 | 行数 |
| --- | --- | --- |
| `client/ui/kit` | 60 | 7577 |
| `client/ui/theme` | 5 | 683 |
| `client/ui/widget` | 23 | 11039 |
| `client/ui/screen` | 20 | 8956 |
| `client/ui/screen/form` | 18 | 6418 |
| `client/ui/prototype` | 2 | 402 |
| `client/edit` | 32 | 4352 |
| `client/edit/draft` | 5 | 458 |
| `client/net` | 6 | 694 |
| `client/key` | 2 | 71 |
| `client` 合计 | 173 | 40650 |

**计数口径**：文件数按 `.java` 计数；行数按**文本行数**（末尾无换行的最后一行同样计为一行）；各行只统计该包自身的文件（不含子包，screen 20 / edit 32 / net 6 / key 2）；引用计数区分「`import` 条数」与「包／类型字符串出现次数」，正文中凡出现计数均注明口径。

**方法**：

1. 用 `import` 与同包引用构建类级依赖图，统计包级边、类级环、扇入／扇出，与 [architecture.md §3](../dev/architecture.md)、[client.md §1](../dev/layers/client.md) 的依赖铁律逐条比对；并用 `tools/check-ui-kit-boundaries.ps1` 已有的 60 文件／193 import／0 违规结论复核 kit 边界。
2. 按行数、字段数、public 方法数、嵌套类型数排序定位上帝类与职责混杂，并核对「渲染 + 输入 + 状态 + 业务装配」是否同处一类。
3. 逐一核对状态载体（`RuleEditClientWorkspace`／`RuleEditorModel`／`EditSession`／`RuleDraft`／`FormView`／各 widget 内部状态），判定「草稿真源 vs 视图状态」边界与访问路径是否唯一。
4. 以 `UiWidget`／`UiControl`／`FormControl` 三个控件契约与宿主 `instanceof` 分派为准，评估复用机制与新增控件的成本。
5. 对「新增字段类型／新增编辑页／新增条目类型预览／新增条件或效果类型」四个扩展点做改动清单实测。

**已确认的良好设计**（本节只列结论，后文不再重复）：

- kit 真实零反向依赖：kit 未 import 本模组任何其它包，`theme → kit`、`widget → theme/kit`、`screen → widget/kit` 方向明确，边界脚本可强制。
- 类型编辑器注册表的「未注册类型只读回退」是真实机制（`TypeEditorRegistry#descriptorFor`），第三方类型不会因客户端不认识而丢失字段。
- `RuleEditorEditPages.Host` 接口把四个页面与 `RuleEditorScreen` 解耦，页面不反向持有屏幕类型；页签列表由 `Page.values()` 驱动，新增页不会漏页签。
- `ConditionLimits` 作为限额唯一常量来源被客户端复用，未在 UI 内另立一套限额常量。
- `UiModalStack`／`UiFocusManager` 把模态与焦点集中托管，避免每个控件各自维护栈。

## 2. 结论概览（高优先级清单）

| 编号 | 结论 | 优先级 |
| --- | --- | --- |
| A-01 | 界面层直接依赖 `core.network.protocol` 协议 DTO（12 文件 20 条 import；含全限定共 13 文件 22 处），与「界面层不接触传输载荷类型」冲突，且承担该约束的 `EditorWorkspaceView` 自身就暴露 DTO | 高 |
| A-02 | `client/edit → client/ui` 反向依赖（`EditSession`→`FormIssue`；`ConditionTreeNodes`／`ConditionTreeJsonCodec` 实现 kit 接口），与层内单向相反 | 高 |
| A-03 | `ui/screen ↔ ui/screen/form` 双向依赖并存在类级相互引用，与层内单向不一致 | 高 |
| A-04 | `RuleEditorEditPages` 3352 行／52 字段把四页的构建、布局、渲染、输入与存在条件业务逻辑全部并入一类 | 高 |
| A-05 | `RuleEditorScreen` 2194 行把管理页与编辑页两种模式并入一类，并复制服务端校验子集 | 高 |
| A-06 | `FormControl` 1910 行 = 工厂 switch + 9 个嵌套控件实现，是新增字段控件的单点瓶颈 | 高 |
| A-07 | `RuleEditorP4Panels` 1151 行、含自身出现于 12 个文件（其它文件 11 个、真正 import 4 个），混合文案包／目录映射／JSON 业务判定／尺寸助手／弹窗控件 | 高 |
| A-08 | 两套条件树控件并存：`UiConditionTreeSummary` 已降级为只读展示却保留整套编辑机器，结构操作语义与 `UiConditionGraphEditor` 各写一遍 | 高 |
| A-09 | 客户端复制了服务端 `RuleValidation` 的结构／参数校验子集，同一规则两处维护 | 高 |
| A-10 | `CatalogSuggestions` 绕过 `EditorWorkspaceView` 直接取全局 `RuleEditClientWorkspace.instance()`，服务端状态出现第二条读取路径 | 高 |
| A-11 | 消耗类效果 id 在 3 个类里各自定义一份，另有 2 处字符串 switch 硬编码效果类型 | 高 |
| A-18 | 用 i18n key 字符串（`FieldCatalogChoices#isGenericProduct`）与字段名子串启发式（`RuleEditorP4Panels#catalogTypeOf`）决定功能行为 | 高 |

中优先级 13 条（A-12 ~ A-17、A-19 ~ A-25）、低优先级 6 条（A-26 ~ A-31）见后文分组。

## 3. 分层与依赖方向

### A-01 界面层直接依赖协议 DTO，且「只读视图」本身就是 DTO 出口

- **定位**：`RuleEditorModel#refresh`／`RuleEditorModel#issuesFor`（`client/ui/screen/RuleEditorModel.java`）、`RuleEditorScreen#pollWorkspace`（`client/ui/screen/RuleEditorScreen.java`）、`RuleEditorP4Panels#catalogModal`、`CatalogEntryPolicy`、`FormView#writeField`；接口侧 `EditorWorkspaceView#snapshot`／`#catalog`／`#lastIssues`（`client/edit/EditorWorkspaceView.java`）。
- **问题**：`client/ui` 共 12 个文件、20 条 `import com.meteorite.itemdespawntowhat.core.network.protocol.*`（计口径：import 语句；若按该包字符串出现次数计为 13 个文件、22 处，另 2 处为 `FormView` 中的全限定用法）（含 `RuleIssue`、`RuleSnapshot`、`RuleSnapshotEntry`、`RuleSaveStatus`、`RuleCatalog`、`RuleCatalogEntry`、`RuleCatalogType`）。其中 `RuleEditorModel` 直接调用 `RuleSnapshotEntry#effective/#overlay/#base/#origin`，`RuleEditorScreen` 直接判 `RuleSnapshotEntry.STATUS_ACTIVE`／`ORIGIN_OVERLAY`，`RuleEditorP4Panels.CatalogMapper#map` 直接遍历 `RuleCatalog#entries()`。而 [client.md §1](../dev/layers/client.md) 写「界面层不得直接接触传输载荷类型」，§2 写「视图**不暴露任何传输载荷类型**」；`LiveEditorWorkspace` 的 javadoc 也声称「把传输 payload 收敛在本类内，界面层因此不接触任何 payload 类型」。
- **影响**：协议 DTO 一旦增删字段或调整形状，改动会穿透到 12 个 import 该包的文件（含全限定用法共 13 个），其中 3 个是 1000–3400 行的大文件；界面无法在协议演进时独立改动；「只读视图」这层抽象没有起到隔离作用，反而让调用方误以为已隔离。
- **建议**：先做概念裁决——若「传输载荷类型」仅指 `core.network.transport.*`（现状已满足），则在 [client.md §1/§2](../dev/layers/client.md) 明确写出「`core.network.protocol` 的只读 DTO 视为客户端可用契约」；若确要隔离，则在 `client/edit` 增加视图模型（如 `RuleCardView`／`IssueView`／`CatalogView`），由 `EditorWorkspaceView` 返回视图模型而非 DTO。两条路必须二选一，不能维持「文档说隔离、接口却导出 DTO」的现状。
- **优先级**：高（**与现行文档不一致**，含改代码／改文档取舍）

### A-02 `client/edit` 反向依赖 UI 包与 kit

- **定位**：`EditSession` 的 `PendingInput`（`client/edit/EditSession.java`，import `client.ui.screen.form.FormIssue`）；`ConditionTreeNodes`（`implements UiTreeEditor.Adapter<ConditionNode>`）；`ConditionTreeJsonCodec`（`implements UiTreeCodec<ConditionNode, JsonElement>`）。
- **问题**：[client.md §1](../dev/layers/client.md) 规定层内单向 `client/net → client/edit → client/ui`；实际存在 `client/edit → client/ui.screen.form`（1 处）与 `client/edit → client.ui.kit`（3 处）。草稿层把「未完成输入的校验问题」类型 `FormIssue` 作为历史条目的一部分持有，并让业务节点类去实现 UI kit 的通用树接口。
- **影响**：草稿与撤销栈无法脱离 UI 包单独使用（例如无头测试或未来复用），`FormIssue` 字段变化会波及 `EditSession.apply/undo/redo` 与整个历史快照结构；kit 的 `UiTreeEditor.Adapter` 成为 `client/edit` 的强制契约，kit 若独立发布必须连带发布该适配契约，与 [ADR-0025](../adr/0025-reusable-client-ui-kit-boundary.md)「只替代机械改写约束」的意图有张力。
- **建议**：把 `FormIssue`（纯值对象：path／label／message／blocking）下沉到 `client/edit`（或新建 `client/edit/issue`），UI 反向 import；若确认 kit 是「可被任意宿主层依赖的公共边界」，则在 [ui-kit/README.md §1](../dev/internals/ui-kit/README.md) 显式声明「kit 可被 `client/edit` 等非 UI 包依赖」，同时把树适配的实现类移出 `client/edit` 业务包。
- **优先级**：高（**与现行文档不一致**）

### A-03 `screen ↔ screen/form` 双向依赖

- **定位**：`screen/form → screen` 的 9 处 import——`CatalogSelectionControl`、`ConditionTreeChrome`、`ConditionTreeOverlay`、`ItemCountsControl`、`NaturalSummary` 引用 `RuleEditorP4Panels`／`RuleDisplayLabels`／`TagPreviewIcons`／`FluidPreviewIcons`；`screen → screen/form` 的 17 处 import。
- **问题**：包级双向依赖成立，且存在**类级相互引用**：`CatalogSelectionControl → RuleEditorP4Panels → FormView → CatalogSelectionControl`（最后一跳是 `FormView#addField` 中的同包 `new CatalogSelectionControl(...)`，因此不体现在 import 图上，但真实存在）。[client.md §1](../dev/layers/client.md) 的层内单向描述没有覆盖这两个子包的关系。
- **影响**：表单控件的改动可能牵动 screen 的面板工具类，反之亦然；两个包无法独立演进或独立测试；`screen/form` 实际不能脱离 `ui/screen` 编译。`RuleEditorP4Panels` 作为枢纽被 11 个其它文件提及（其中 4 个直接 import），任何修改的影响面都被放大。
- **建议**：把 `UiCatalogGrid` 装配、`catalogTypeOf`、`iconFor`、弹窗尺寸助手抽到中立包（如 `client/ui/screen/catalog` 或 `client/ui/host`），`screen/form` 只依赖该中立包与 kit／widget；`RuleDisplayLabels` 等纯标签解析移到 `client/edit`。或者，在 [client.md §1](../dev/layers/client.md) 显式承认 `screen` 与 `screen/form` 为同一层的两个子包并允许双向——但需给出可核查的收敛条件。
- **优先级**：高（**与现行文档不一致**）

### A-04 `RuleEditorEditPages`：四页合并的上帝类

- **定位**：`RuleEditorEditPages`（3352 行、8 个嵌套类型）：`#buildInfo`／`#buildInput`／`#buildTrigger`／`#buildResults`、`#layoutPage` 的 `switch (page)`、`#render` 的 `switch (page)`、`#mouseClicked`／`#keyPressed`、`#scanPresenceLeaves`／`#addRuleLevelPresence`／`#removeRuleLevelPresence`、`#retargetFluidActions`／`#retargetCatalystItems`。
- **问题**：四页各自的「装配 + 布局 + 渲染 + 输入」与**跨页业务语义**（存在条件叶扫描与增删、消耗动作重指向、数量对象同步）同处一个类；字段区按页排布（`infoForm`／`sourceForm`／`catalystForm`／`effectForm` …共 20 余个 `@Nullable` 字段），页间状态共享。
- **影响**：新增一个页签或调整某一页，必须在 3000+ 行文件内同时改构建、布局、渲染、输入四处；四页共享字段使跨页误改风险上升；`PresenceLeaf`／`PresenceRow` 等存在条件语义无法被触发页或结果页单独复用。
- **建议**：按页拆类（`InfoPage`／`InputPage`／`TriggerPage`／`ResultsPage`）实现统一的页面接口（`build/layout/render/input/applyToDraft`），由 `RuleEditorEditPages` 退化为页注册与调度；把存在条件扫描与增删抽成 `PresenceConditions`（纯草稿操作），把消耗动作重指向抽成 `ConsumptionRetarget`（纯草稿操作），两者都放 `client/edit` 并单独可测。
- **优先级**：高

### A-05 `RuleEditorScreen`：管理页 + 编辑页 + 校验器

- **定位**：`RuleEditorScreen`（2194 行、3 个嵌套类型）：`Mode.LIST` 的管理逻辑（`#refreshList`、`#promptNewRule`、`#promptDuplicate`、`#deleteSelected`、`#maskSelected`、`#toggleSelectedEnabled`、`#showRuleDetails`）与 `Mode.EDIT` 的编辑逻辑（`#rebuildEdit`、`#switchTab`、`#save`、`#undo`／`#redo`、`#promptConflicts`）并存；`#localIssues` 与 `#collectIssues`。
- **问题**：单个类同时是「规则列表页」「四页编辑器宿主」「保存协议驱动」「草稿本地校验器」；`localIssues` 自行遍历 `effects`／`outcomes`／`catalyst_cost`／`source_cost` 做结构判定并拼接问题。
- **影响**：管理功能与编辑功能互相牵制，改任一侧都要动同一个 2000+ 行类；本地校验与服务端校验形成两份事实，规则变更漏改会表现为「本地放行、服务端拒绝」或反之。
- **建议**：拆成管理列表与编辑器两个 Screen（或一个 Screen + 两个 Controller）；本地校验只保留「未完成输入／结构无法解析」类硬阻塞，其余问题直接消费服务端下发的 `RuleIssue`（协议已具备）；若必须保留本地预校验，把规则收敛到 `client/edit` 的一处实现并注明与服务端校验的对应关系。
- **优先级**：高（与 A-09 关联）

### A-06 `FormControl`：字段控件工厂 + 全部控件实现

- **定位**：`FormControl`（1910 行）与 `#create` 的 `switch (field.type())`；9 个嵌套控件 `TextControl`／`BoolControl`／`EnumControl`／`ListControl`／`ClimateControl`／`ConditionTreeControl`／`RawJsonControl`／`NoteControl`／`SublistControl`。
- **问题**：新增一个 `EditorFieldType` 必须同时改枚举、`FormControl#create` 的 switch、并在这个 1910 行文件里追加一个嵌套控件；数值／目录／行内布局相关类型还要改 `FormView`、`NumberControl`、`CatalogSuggestions`、`CatalogSelectionControl`（`EditorFieldType.` 限定引用分布在 8 个文件、按行计 73 行次）。
- **影响**：字段类型是 UI 最常见的扩展方向，却以「改最大文件里的 switch + 嵌套类」为代价；`FormControl` 同时是抽象基类、工具函数集合（`idChars`／`literalValue`／`textOf`／`trimDouble`）和 9 个实现，读改成本高。
- **建议**：以注册表替代 switch（`Map<EditorFieldType, ControlFactory>`，默认项回落 `RawJsonControl`）；每个控件一个文件；纯文本／数值转换函数移入 `FieldNumbers` 或新建 `FormValues`。
- **优先级**：高

### A-07 `RuleEditorP4Panels`：跨 11 个其它文件的混合枢纽

- **定位**：`RuleEditorP4Panels`（1151 行、2 个嵌套类型）：`#clockText`／`#dayTicks`／`#cyclicTexts`／`#rangeTexts`／`#catalogTexts`／`#catalogLabel`（文案包）、`#catalogTypeOf`／`#registryType`／`#iconFor`／`CatalogMapper`（目录映射）、`#climateAllUnbounded`／`#diagramEdges`（读规则 JSON 的业务判定）、`#pickerWidth`／`#pickerBodyHeight`（尺寸）、`LeafPanel`（控件实现）。
- **问题**：一个类同时是文案常量表、目录类型推断器、规则 JSON 检查器、弹窗尺寸助手和可复用面板；被其它 11 个文件提及（含自身共 12 个文件；其中真正 `import` 的 4 个），`ui/screen` 与 `ui/screen/form` 两边都有，是 A-03 反向依赖的直接来源。
- **影响**：任何一处改动都要评估这 11 个其它调用点（其中 4 个直接 import）；`catalogTypeOf` 的字段名启发式被 `FormView`、`CatalogSelectionControl`、`FieldCatalogChoices`、`ItemCountsControl` 共同依赖，形成隐式「字符串协议」。
- **建议**：拆为 `CatalogSupport`（类型映射 + 图标 + 策略）、`RuleInspectors`（`climateAllUnbounded`／`diagramEdges` 等只读派生）、`LeafPanel`（控件）、`EditorTexts`（文案常量）；其中 `CatalogSupport`／`RuleInspectors` 放中立位置供 `screen/form` 依赖。
- **优先级**：高

### A-08 两套条件树控件并存，结构操作语义重复

- **定位**：`UiConditionTreeSummary`（1573 行、60 个 public 成员）与 `UiConditionGraphEditor`（2637 行、68 个 public 成员）；两者的结构操作：`addGroup`／`addNode`／`wrapSelection`／`deleteSelected`（对应 `removeSelection`）／`moveSelected`（对应 `moveSelection`）／`toggleSelectedGroupKind`／`pickerIndexAt`／`pickerKeyPressed`。
- **问题**：`UiConditionTreeSummary` 唯一实例化点是 `FormControl.ConditionTreeControl`，且构造后立即 `editor.setEnabled(false)`，即生产中只作只读展示；交互编辑全部由 `UiConditionGraphEditor` 承担。但缩进树仍保留完整的结构编辑、拖动与类型选择浮层实现，且缺陷扫描由 `UiConditionGraphEditor` 重写（仅复用 `UiConditionTreeSummary.Issue`／`IssueKind` 两个类型）。
- **影响**：条件树的增删／包裹／移动语义存在两份实现，行为修复必须同步两处，漏改会出现「画布页可用但树行不可用」之类的不一致；只读用途却携带 1500 行可写逻辑，增加阅读与回归成本。
- **建议**：把「对 `ConditionNode` 的纯结构操作」抽成 `ConditionTreeOps`（或在 `client/edit` 的 `ConditionTreeNodes` 上扩展），两个控件共用；`UiConditionTreeSummary` 若确定只读，删除编辑分支并重命名为「树摘要视图」，或直接由 `UiConditionGraphEditor` 的只读投影替代。
- **优先级**：高

### A-09 客户端复制服务端校验规则

- **定位**：`RuleEditorScreen#localIssues`／`#validateConditionTree`／`#countConsumption`／`#invalidItemCounts`（`client/ui/screen/RuleEditorScreen.java`）；`RuleEditorEditPages#issues`／`#addPageIssues`。
- **问题**：客户端自行判定 `source` 非空、`effects`／`outcomes` 互斥、`catalyst_cost` 合法、消耗效果不重复、`display_name`／`notes` 码点上限、`schema_version` 受支持、条件树结构损坏等，与 [问题模型与校验](../dev/backend/systems/issue-validation.md) 中 `RuleValidation` 的「结构档」高度重叠；限额常量复用了 `ConditionLimits`（这一点是正确的），但判定逻辑本身是第二份。
- **影响**：校验规则变更需要双处修改；两侧判据漂移会产生「本地通过、服务端拒绝」或「本地阻塞、服务端允许」的体验与排查成本；且 UI 层被迫理解 `RuleFields` 的结构细节。
- **建议**：把「未完成输入／无法解析」保留为客户端硬阻塞，其余校验结果统一取服务端 `RuleIssue`；若为离线即时反馈必须保留本地预校验，则把实现下沉到 `client/edit` 并与服务端 `RuleValidation` 建立同一份判据来源（至少共享常量与谓词）。
- **优先级**：高

## 4. 状态管理

### A-10 服务端状态存在两条读取路径，其中一条是全局单例

- **定位**：`CatalogSuggestions#suggest`（`client/ui/screen/form/CatalogSuggestions.java` 直接 `RuleEditClientWorkspace.instance()`）；对照 `RuleEditorModel#workspace` 与 `RuleEditorEditPages.Host#workspace` 走 `EditorWorkspaceView`。
- **问题**：同一份服务端目录状态，`RuleEditorScreen` 经 `RuleEditorModel → EditorWorkspaceView` 读取，`RuleEditorEditPages` 经 `Host#workspace()` 读取，而 `screen/form` 的 `CatalogSuggestions` 直接取全局单例。后者完全绕过视图抽象与注入点，也不感知 `RuleEditorModel` 的缓存。
- **影响**：无法用假工作区测试表单建议；工作区实现替换或多会话场景下该路径会静默使用错误实例；目录缓存的失效时机出现第三个事实来源。
- **建议**：把 `EditorWorkspaceView`（或只读目录访问器）经 `FormView`／`ConditionSupport` 注入到 `CatalogSuggestions`，移除对 `.instance()` 的直接依赖。
- **优先级**：高

### A-11 `RuleEditorModel` 承担六类状态生命周期

- **定位**：`RuleEditorModel`（518 行、13 字段、26 个 public 方法）：快照消费（`#refresh`、`seenSnapshot`／`snapshotVersion`／`loaded`）、会话表（`sessions`／`createdIds`／`restoreIds`）、基线（`baselines`）、变更集装配（`#buildChangeSetJson`）、草稿落盘与冲突（`journal`／`restored`／`conflicts`／`#resolveConflict`／`#discardAllDrafts`）、问题查询（`#issuesFor`／`#hasError`）。
- **问题**：一个「编辑页数据模型」同时是快照缓存、会话生命周期、变更集构造器、落盘控制器与问题视图。
- **影响**：新增保存流程、草稿冲突策略或会话恢复方式都改这一个类，且它的状态无法单独构造，测试必须搭起全部六类状态。
- **建议**：拆为 `RuleSessionStore`（会话与标记）、`DraftPersistence`（journal／conflict／baseline）、`ChangeSetBuilder`、`IssueView`；`RuleEditorModel` 退化为门面。
- **优先级**：中

### A-12 视图状态与草稿真源的同步依赖轮询式 revision 比较

- **定位**：`FormView#tick`／`#retainPendingInputs`／`#applyToDraft`（`loadedRevision` 与 `EditSession#revision` 比较）；`RuleEditorScreen#tick` 的 `listNeedsRefresh`／`lastEditable`；`RuleEditorEditPages#tick`／`#pruneLeafPanels`；各 widget 自身的滚动与选中状态。
- **问题**：草稿真源是 `EditSession.draft()`，但视图层至少叠了四层状态——`RuleEditorEditPages` 的页字段与 `scrollOffsets`、`FormView` 的行与未提交文本、`FormControl` 子类的 `edited`／`loadedText`、widget 内部滚动与选中。层间同步靠每 tick 的 `revision` 比较与显式 `reload()` 调用，`FormView#reload`／`#reloadWith`／`#rebase` 三个入口由不同调用方触发。
- **影响**：新增一个视图状态字段时，必须同时判断它属于哪一层、由谁在哪个时机刷新；漏掉一次 `reload()` 就会表现为「草稿已改但界面未刷新」，这类缺陷不易定位。
- **建议**：为 `FormView` 明确定义唯一刷新入口（把 `reloadWith`／`rebase` 收敛为一个「结构变更后重建」入口），并在 [editor-input-state.md](../dev/internals/editor-input-state.md) 补一张「状态归属 × 刷新触发」表；把 per-widget 的滚动／选中统一登记到 `RuleEditorEditPages` 的视图状态表，避免散落。
- **优先级**：中

### A-13 存在条件开关状态由草稿派生，但仍保留可变的界面标志

- **定位**：`RuleEditorEditPages` 的 `catalystPresenceToggle`／`fluidPresenceToggle`／`catalystToggle`／`enabled` 字段与 `#restoreSwitches`。
- **问题**：开关的语义真源是草稿中的存在条件叶（`catalystPresenceToggle` 由 `hasRuleLevelPresence` 派生），但界面同时持有可变勾选态，靠 `restoreSwitches()` 在重建时回填。`RuleEditorEditPages#enabled` 与 `RuleEditorScreen#lastEditable`／`workspace.active()` 也构成同一事实的两份缓存。
- **影响**：一旦某条路径漏调 `restoreSwitches()`／`applyEnabled()`，界面勾选态就会与草稿不一致；「是否可编辑」的判定分散在两个类里。
- **建议**：开关的选中态只由草稿派生、不缓存（或缓存但标注为纯派生且在每次重建后必须刷新）；把「可编辑」统一为 `Host#rejectWhenFrozen` 一条判定路径，界面不另存副本。
- **优先级**：中

### A-14 `RuleEditorScreen` 与 `RuleEditorEditPages` 各自维护滚动与命中状态

- **定位**：`RuleEditorScreen` 的 `contentArea`／`lastEditArea`；`RuleEditorEditPages` 的 `pageScroll`／`scrollOffsets`／`lastArea`／`pressedWidget`／`dragKind`；`FormView` 的 `scrollOffset`。
- **问题**：滚动条存在三层（管理列表、页面容器、表单内部），命中与拖拽归属也分别记录。
- **影响**：新增滚动容器或调整布局嵌套时，裁剪与命中坐标需要同时满足三层的绝对／相对坐标约定（[editor-card-layout.md](../dev/internals/editor-card-layout.md) 已说明卡片矩形与页面控件共用绝对滚动坐标），容易产生「可见但点不中」类问题。
- **建议**：由 `UiScrollView` 统一承担滚动状态与坐标换算，页面层只提供内容高度；把「按下的控件归属」收敛到 `UiInputRouter`／`UiInputCapture`（kit 已有该契约），不在页面层另存 `pressedWidget`。
- **优先级**：中

## 5. 组件复用机制

### A-15 三套并行控件契约

- **定位**：kit `UiControl`（`final class`，实现 `UiFocusTarget`）、widget `UiWidget`（接口：`bounds/setBounds/render` + 6 个输入回调）、screen/form `FormControl`（`abstract class`：`height()`／`setBounds(int,int,int)`／`render(GuiGraphics,Font,int,int)`／`load(JsonElement)`）。
- **问题**：三者的事件面与几何约定互不相同（`FormControl` 无高度参数透传、无 `isVisible` 语义以外的统一可见性切换），且 `FormControl` 不实现 `UiWidget`。新增控件必须先决定落在哪一层，再手工写适配。
- **影响**：跨层复用要靠手写转发；无法用统一容器装载三类控件（`RuleEditorEditPages#liveWidgets` 只能装 `UiWidget`，`FormControl` 必须由 `FormView` 包一层）。
- **建议**：在 [ui-kit/README.md §2](../dev/internals/ui-kit/README.md) 用一张表固定三者分工（kit 原语 / widget 可复用宿主控件 / form 字段适配器），并让 `FormControl` 实现 `UiWidget`（高度通过 `setBounds` 的 height 或 `layoutHeight()` 统一），减少适配层。
- **优先级**：中

### A-16 `UiWidget` 生命周期不完整，宿主靠 `instanceof` 兜底

- **定位**：`UiWidget` 未声明 `setVisible(boolean)`／`tick()`／`endInteractions()`／`focusTargets()`；对应兜底：`RuleEditorEditPages#show` 对 `UiButton`／`UiListView`／`FormView`／`UiSegmentedControl`／`UiCheckBox` 五种具体类型分派；`#tick` 用 `widget instanceof FormView`；`FormView` 对 `FormControl.TextControl`／`ItemCountsControl`／`RawJsonControl` 分派；`RuleEditorP4Panels.LeafPanel` 对 `UiRangeBar`／`UiCyclicTimeBar` 重复 4 段 if/else。
- **问题**：UI 包共 150 处 `instanceof`（22 个文件；计口径：正则 `\binstanceof\b`），其中相当一部分不是业务判别，而是**契约缺口**：接口没提供能力，宿主只能按具体类型分支。
- **影响**：新增一个需要「可隐藏／需 tick／需结束交互」的控件，必须回到上述宿主类逐一加分支；遗漏就表现为不可见性失效或捕获未结束。
- **建议**：把 `setVisible(boolean)`／`tick()`／`endInteractions(EndReason)`／`focusTargets()` 提升到 `UiWidget`（默认空实现），或引入能力接口（`Visible`／`Tickable`／`EndInteractions`）由宿主按能力查询；`FormControl` 的按类型分支改为在 `FormControl` 上声明对应的虚方法。
- **优先级**：中

### A-17 kit 公开面缺少继承与策略扩展点

- **定位**：`UiControl` 为 `final class`；kit 中可直接定制的公开点仅有 `UiControlStyle`／`UiSliderStyle`／`UiSliderPainter`／`UiNumberPolicy`／`UiTreeCodec` 等少数策略／适配接口。
- **问题**：[ADR-0028](../adr/0028-canvas-page-and-flat-skin.md) 已确认 kit 公开面会持续扩张，但扩张方式是「加类」而非「留策略缝」；`UiControl` 这类核心控件不可替换行为。
- **影响**：宿主想改按钮／列表的命中或绘制细节时只能改 kit（破坏可回流边界），或在 widget 层重写一个同类控件（产生重复实现）。
- **建议**：在 [ui-kit/README.md](../dev/internals/ui-kit/README.md) 明确 kit 的扩展策略（组合优先，公开类默认 `final`），并为高频定制点（命中判定、文字裁剪、焦点轮廓）提供策略接口，与既有 `UiSliderPainter` 模式保持一致。
- **优先级**：中

### A-18 目录类型推断依赖字段名字符串启发式

- **定位**：`RuleEditorP4Panels#catalogTypeOf`（字段名 `switch` + `registryType` 的 `contains`／`endsWith` 判定）、`RuleEditorP4Panels#catalogLabel`（用「无空格无冒号含点」判断是否为翻译 key）、`CatalogSuggestions`（注册表名与别名的 `switch`）、`FieldCatalogChoices#isGenericProduct`（比较 i18n key 字符串）。
- **问题**：目录类型由「字段名像什么／注册表名包含什么」推断，且至少两处（`FieldCatalogChoices` 与 `RuleEditorP4Panels`）各自实现；`FieldCatalogChoices#isGenericProduct` 直接把 `gui.itemdespawntowhat.edit.field.spawn_entity.entity` 这个**文案 key** 当语义判别符。
- **影响**：重命名字段、调整文案 key、或新增同义的注册表名，都会静默改变目录行为（不报错，只是列表内容变了），排障困难；与项目规则「禁止滥用魔法字符串与魔法数字」的取向不符。
- **建议**：在 `EditorField` 上增加结构化元数据（`catalogType` 与「是否通用产物」标志），由描述符声明、UI 直接读取；文案 key 不再参与行为判定。
- **优先级**：高（对应概览 A-18）

## 6. 耦合与重复

### A-19 消耗类效果 id 与判定散落多处

- **定位**：`RuleCostBinding#CONSUME_SOURCE`／`#CONSUME_CATALYST`（`client/ui/screen/RuleCostBinding.java`）、`RuleEditorEditPages` 的 `CONSUME_SOURCE_ID`／`CONSUME_CATALYST_ID`／`CONSUME_FLUID_ID`、`ConsumptionSummary` 的同名三常量；以及 `RulePreviewIcons` 对 `spawn_entity`／`place_block`／`loot_table`／`lightning`／`explosion`／`arrow_rain` 的字符串 `switch`、`RuleNaming` 对同名效果与 `experience`／`weather` 的 `switch`。
- **问题**：同一批业务类型标识在 UI 层至少有 3 处独立常量定义、2 处字符串 `switch`；`RuleEditorEditPages#isInputOwnedConsumptionField`／`RuleEditorScreen#countConsumption` 等再各自复述消耗语义。
- **影响**：新增或改名一个消耗类效果需要扫改 4–5 个文件；漏改会造成「输入页不显示该参数」「结果页重复显示」「保存前校验漏判」等分散缺陷。
- **建议**：把内置效果／条件 id 收敛到 `core/type` 的既有类型常量（`BuiltinEffectTypes` 已持有类型），UI 一律引用类型常量而非字符串；消耗语义（哪些字段归输入页、哪些算一次消耗）集中到一处声明。
- **优先级**：高

### A-20 `client/edit` 生成界面文案

- **定位**：`ConditionTreeJsonCodec#decode` 返回 `Component.translatable("gui...tree.source_invalid")`；`ConditionTreeClipboard` 拼接 `Component.translatable` 问题；`RuleNaming`、`TypeLabels`、`JsonSummary#compact`、`EditSession.OP_*` 常量。
- **问题**：草稿／编解码层持有并构造面向界面的本地化文本（`client/edit` 内 79 处 `gui.itemdespawntowhat.*`／`Component.translatable`，分布在 13 个文件）。[client.md §4.4](../dev/layers/client.md) 要求主题与文案由宿主注入（该约束对 kit 成立），但 edit 层承担文案表达使「数据层」依赖 MC 文本类型。
- **影响**：草稿层无法无头复用；文案归属分散在 edit 与 ui 两层，i18n key 的新增／改名需要跨层搜索；未来若换展示形态（非 GUI 客户端）需要改 edit。
- **建议**：`client/edit` 只返回消息码与参数（如 `DecodeResult(code, args)`），由 UI 翻译；撤销操作标签保留为 key（当前已是 key，成本低）；或至少在 [client.md §3](../dev/layers/client.md) 明确「类型显示名与撤销标签由 `client/edit` 持有，其余界面文案归 `ui`」并据此收敛。
- **优先级**：中（**与现行文档不一致**，含改代码／改文档取舍）

### A-21 UI 值载体绑定 Gson

- **定位**：`client/ui` 20 个文件 `import com.google.gson`（计口径：import 行；使用最集中的是 `RuleEditorEditPages`、`FormControl`、`RuleEditorScreen`、`FormView`、`RuleEditorP4Panels`）。kit 明令禁止 Gson（边界正确，无违规）。
- **问题**：控件值以 `JsonElement`／`JsonObject` 为载体，UI 自行做深拷贝、`instanceof JsonObject` 判定与路径拼接；`FormView#writeField`／`RuleEditorEditPages` 直接操作草稿 JSON 结构。
- **影响**：草稿的内部表示一旦改变（例如改为强类型 value object 或换 codec），需要改 20 个 UI 文件；UI 层被迫理解规则 JSON 的字段形状，与前文 A-09 的校验重复同源。
- **建议**：短期在 `client/edit` 暴露一组只读访问助手（取值／判型／路径拼接），UI 不直接 `instanceof`；长期评估用 `Optional`／值对象门面隔离 JSON，保留 JSON 仅在 `client/edit` 内部。
- **优先级**：中

### A-22 类型编辑器注册时机绑定在 Screen，且输入页绕过注册表

- **定位**：`RuleEditorScreen#init` 调用 `BuiltinEditorDescriptors.bootstrap()`；`RuleEditorEditPages#buildInput` 直接调用 `BuiltinEditorDescriptors.sourceDescriptor()`／`#sourceCostDescriptor()`／`#catalystCostDescriptor()`，`#buildResults` 直接调用 `#entityDescriptor(variant)`。
- **问题**：注册表本应是扩展点（[client.md §7](../dev/layers/client.md) 声明「新增类型只需注册编辑器描述」），但注册由某个 Screen 的 `init()` 触发；同时输入页与结果页对 source／catalyst／entity 三类描述符直接走静态方法，未经过 `TypeEditorRegistry`。
- **影响**：任何不经 `RuleEditorScreen` 的入口（未来的其它界面、测试、未来在客户端引导阶段预取的目录／标签）都会拿到空注册表；第三方无法替换这三类描述符。
- **建议**：注册移到客户端引导阶段（`bootstrap()` 变幂等，调用点移到 `client/key` 或平台客户端初始化）；输入页的 source／catalyst 与结果页的 entity 描述符改为经注册表或宿主注入取得。
- **优先级**：中

### A-23 `client/key` 承担开屏编排

- **定位**：`EditorShortcut#activate`（`client/key/EditorShortcut.java`）同时依赖 `client.net` 的 `RuleEditClientWorkspace`／`EditorScreenHooks`／`EditorScreenOpener`／`EditorOpenRequest`／`RuleEditClientState`；`EditorShortcut#prototypeScreen` 打开 `UiPrototypeScreen`。
- **问题**：按键层不只是「输入 → 意图」，还负责判断会话状态、复用开屏请求、直接 `setScreen`，与 `RuleEditorOpener#open` 的职责部分重叠（两处都能切屏）。
- **影响**：开屏路径有两条，修改切屏行为需要同时检查两处；`client/key` 无形中成为「第三个开屏入口」。
- **建议**：把「重开已有会话界面」收敛为 `client/net` 的一个入口（例如 `EditorScreenOpener#reopen(request)`），`client/key` 只做「有会话则请求重开、否则提示」。
- **优先级**：中

### A-24 纯 JSON 派生的业务类被放在 UI 包

- **定位**：`RuleCostBinding`、`ResultStructure`、`RuleTriggers`、`CatalogEntryPolicy` 位于 `client/ui/screen`，均不渲染、只读规则 JSON 并回答「结构是什么」；同类职责的 `RuleCategories` 实际位于 `client/edit`。
- **问题**：这些类的职责是「规则草稿的只读派生视图」，属于 draft 语义而非界面语义，但因依赖 `RuleFields` 与 JSON 而落在 UI 包，进而被 `screen/form` 反向 import（A-03 的一部分）。
- **影响**：UI 包持续吸收本应属于 `client/edit` 的逻辑，包体量与反向依赖同时增长；派生逻辑无法独立测试。
- **建议**：在 `client/edit` 下建立 `derive`（或 `ruleview`）子包承载这些只读派生类，UI 只消费其结果。
- **优先级**：中

### A-25 `FormControl` 内嵌的数个具体控件与 `FormView` 互相 `instanceof`

- **定位**：`FormView#setRowDefaultText` 判 `FormControl.TextControl`、`#setItemCountReferences` 判 `ItemCountsControl`、`#reload` 判 `RawJsonControl`、`#applyToDraft` 对 `ItemCountsControl` 特判；`RuleEditorEditPages` 亦对 `ItemCountsControl` 特判。
- **问题**：`FormView` 作为通用表单容器，却按具体控件类型提供专属能力（默认文本、引用集合、只读 JSON），这些能力没有在 `FormControl` 上以虚方法声明。
- **影响**：新增一个需要「默认文本展示」或「结构随兄弟字段变化」的控件，必须回到 `FormView` 加分支，容器与控件耦合。
- **建议**：把 `displayDefault`／`setReferences`／`revealPath`／`hasFallbackRaw` 等提升为 `FormControl` 的默认空实现，`FormView` 只调接口。
- **优先级**：中

## 7. 扩展点实测（改动清单）

以下按「新增 X 需要改哪些类」列出实测结果，用于判断是否存在散弹式修改。

### 7.1 新增一个 `EditorFieldType` 字段控件

必改：

1. `EditorFieldType`（`client/edit`，加枚举值）；
2. `FormControl#create` 的 `switch (field.type())`＋ 在 1910 行文件内新增嵌套控件。

按类型可能需要：

3. `FormView`（若需要新的行内布局／选择器／可见性规则，11 处 `EditorFieldType` 引用）；
4. `NumberControl`（数值类）；
5. `CatalogSuggestions`／`CatalogSelectionControl`（目录类）；
6. `RuleEditorEditPages`／`RuleEditorP4Panels`（若该字段出现在输入页或结果页的特判清单中）；
7. `en_us.json` / `zh_cn.json` 同步新增标签 key。

结论：**2 个必改点中有一个是 1910 行的共享文件**，且随字段种类可能扩展到 8 个引用文件。主要成本来自 `FormControl` 的集中式工厂，而非枚举本身。

### 7.2 新增一个编辑页

必改：

1. `RuleEditorEditPages.Page`（枚举，加值；页签列表由 `Page.values()` 自动跟随，无需改 `RuleEditorScreen#tabOptions`）；
2. `RuleEditorEditPages#rebuild` 的 `switch (page)`；
3. `RuleEditorEditPages#layoutPage` 的 `switch (page)`；
4. `RuleEditorEditPages#render` 的 `switch (page)`；
5. 在同一个 3352 行文件内新增该页的 build／layout／render／input 四组方法及专属字段；
6. `en_us.json` / `zh_cn.json` 的 `tab.<值>` key；
7. [editor-card-layout.md](../dev/internals/editor-card-layout.md) 文档同步。

结论：**改动集中在 1 个类，但该类为全项目最大文件**；三处 `switch` 分散在文件首、中、尾，容易出现「加了页签但漏了布局或渲染」的不一致。

### 7.3 新增一个条目类型预览（新目录类型）

必改：

1. 新建 `XxxPreviewIcons`（参照 `BlockPreviewIcons`／`EntityPreviewIcons`／`FluidPreviewIcons`／`TagPreviewIcons` 的既有写法——四者无共同基类或接口）；
2. `RuleEditorP4Panels#iconFor` 增加分支；
3. `RuleEditorP4Panels#registryType`／`#catalogTypeOf` 增加注册表名与字段名映射；
4. `CatalogSuggestions` 的注册表名 `switch`；
5. `CatalogEntryPolicy#hidden` 增加排除规则；
6. `FieldCatalogChoices#tags` 增加服务端标签来源；
7. 若涉及新目录协议类型：`core.network.protocol.RuleCatalogType` 及其服务端映射与 i18n。

结论：**至少 6 个 UI 文件**，其中 `RuleEditorP4Panels` 与 `CatalogSuggestions` 各持一份注册表名映射。预览能力没有统一的「按条目类型注册渲染器」机制，属于典型散弹式修改。

### 7.4 新增一个条件／效果类型

- **通用情况（无特殊界面需求）**：只需改 `client/edit` 的 `BuiltinEditorDescriptors`（注册描述符）+ `BuiltinEditorDefaults`（默认参数），必要时 `ClientTypeRegistries`；未注册类型自动走只读回退。**这一路径与 [client.md §7](../dev/layers/client.md) 的声明一致，是好设计。**
- **需要特殊界面时**：若新类型需要预览图标、需要把参数归到输入页、或需要新的目录类型，则必须改 `RulePreviewIcons` 的字符串 `switch`、`RuleEditorEditPages#isInputOwnedConsumptionField`／`#isCommonActionField`／`#ownFieldsOnly`、`RuleCostBinding`、`RuleEditorScreen#countConsumption` 等。此时实际改动为 **5–7 个文件，其中 2 个超过 2000 行**。

结论：文档声明的扩展点只覆盖「通用类型」；一旦涉及界面语义（预览、归属页、消耗），就会退化为跨文件散弹式修改。建议把「预览渲染器」「字段归属页」「目录类型」都变成描述符上的声明式元数据。

## 8. 与现行文档不一致汇总与取舍

| 编号 | 文档位置 | 文档写法 | 代码现状 | 取舍建议 |
| --- | --- | --- | --- | --- |
| A-01 | [client.md §1／§2](../dev/layers/client.md) | 界面层不接触传输载荷类型；视图不暴露任何传输载荷类型 | 12 个文件 20 条 import `core.network.protocol`（含全限定用法共 13 文件 22 处）；`EditorWorkspaceView` 直接返回 `RuleSnapshot`／`RuleCatalog`／`RuleIssue` | 二选一：改文档澄清「protocol 只读 DTO 可用」，或改代码引入视图模型。推荐先改文档（成本低、语义可裁定），并把「transport 载荷」作为唯一禁令 |
| A-02 | [client.md §1](../dev/layers/client.md) | 层内单向 net → edit → ui | `client/edit → client/ui`（`FormIssue`）与 `client/edit → client/ui/kit`（`UiTreeEditor`／`UiTreeCodec`） | 推荐改代码：`FormIssue` 下沉 edit，树适配实现移出业务包；若维持 kit 为公共边界，需在 [ui-kit/README.md](../dev/internals/ui-kit/README.md) 显式声明 |
| A-03 | [client.md §1](../dev/layers/client.md) | 层内单向；四页职责 | `screen ↔ screen/form` 双向 import（17／9 处）并有类级相互引用 | 推荐改代码：抽中立 `catalog`／`host` 包承载双方共享物；退路是改文档承认同层子包双向 |
| A-20 | [client.md §4.4](../dev/layers/client.md) | 主题与文案由宿主注入（针对 kit） | `client/edit` 内 79 处界面文案／i18n key | 推荐改文档明确边界（类型显示名与撤销标签归 edit），其余文案逐步上移 |
| A-24 | [client.md §3](../dev/layers/client.md) | `client/edit` 是草稿与注册表 | 规则派生逻辑（`RuleCostBinding`／`ResultStructure`／`RuleTriggers` 等）位于 `client/ui/screen` | 推荐改代码：派生类下沉 `client/edit` |
| A-23 | [client.md §6](../dev/layers/client.md) | `ui/prototype` 不得注册快捷键，也不得被绕过独占锁的入口打开 | `client/key/EditorShortcut#prototypeScreen` 在 `DebugMode.ENABLED` 下打开原型屏（生产环境返回 null，不构成绕过独占锁的入口，但字面表述与实现不符） | 推荐改文档：写明「生产环境不注册快捷键；开发环境可经 DebugMode 打开原型屏」 |

## 9. 其余问题（低优先级）

### A-26 `RuleEditorEditPages` 与 `RuleEditorScreen` 内联匿名实现过大

- **定位**：`RuleEditorScreen` 的 `pagesHost` 匿名 `RuleEditorEditPages.Host` 实现（约 75 行）；`RuleEditorEditPages#pageConditionSupport` 返回的匿名 `ConditionSupport`。
- **问题**：宿主适配逻辑以匿名类内联在大文件里，`Host` 接口的 12 个无注解方法（另有带 `@Nullable` 注解的 `session()`）没有独立命名与复用点。
- **影响**：调整宿主能力时必须在屏幕类内部定位匿名块；同一接口无法被第二个宿主复用。
- **建议**：抽成具名内部类或独立文件 `RuleEditorPagesHost`。
- **优先级**：低

### A-27 kit 内部 13 类强耦合

- **定位**：同包内形成互引簇的 13 个类——`TextScroll`／`UiControl`／`UiControlGroup`／`UiControlStyle`／`UiDocument`／`UiFocusManager`／`UiFocusTarget`／`UiIcon`／`UiNode`／`UiRect`（扇入 59）／`UiScrollView`／`UiTarget`／`UiTransform`（按同包引用统计）。
- **问题**：kit 作为整体边界清晰（0 违规、无反向依赖），但内部形成紧密互引簇，单独取用一个类需要带上大半 kit。
- **影响**：未来拆包或裁剪 kit 体积时难以按需拆分；上游同步（[ADR-0022](../adr/0022-client-ui-kit-adoption.md) 的来源回流）需整包比对。
- **建议**：在 [ui-kit/README.md §1](../dev/internals/ui-kit/README.md) 标注核心簇与外围类的依赖方向，为未来的 `api/` / `internal/` 拆分预留清单。
- **优先级**：低

### A-28 示例类与原型类的归属

- **定位**：`UiSliderExamples`（kit，89 行，无宿主引用）、`UiPrototypeScreen`（prototype，395 行，仅 `EditorShortcut` 在开发环境引用）。
- **问题**：示例类位于 kit 公开包内，原型类位于 `client/ui` 下但只在开发环境可达；二者的保留／删除标准没有记录。
- **影响**：后续维护者无法判断其是否为必要公开面（`UiSliderExamples` 是 [拆包就绪门槛](../dev/internals/ui-kit/README.md) 的「真实宿主示例」证据，属有意保留）。
- **建议**：在 kit 文档中明确 `UiSliderExamples` 是接入示例而非生产代码；原型类在 [client.md §6](../dev/layers/client.md) 已有定位，补一句「仅 `DebugMode` 可达」即可。
- **优先级**：低

### A-29 命名空间常量存在两种写法

- **定位**：`UiTheme#surface` 直接用字面量 `"itemdespawntowhat"` 拼纹理路径；`UiFlatSkin` 使用 `TypeLabels.OWN_NAMESPACE`。
- **问题**：同一主题层内命名空间常量两种来源，且其一为裸字面量。
- **影响**：命名空间变更（或主题复用）时需逐处搜索；也是 `ui/theme → client/edit` 依赖的唯一来源（A-02 的附带项）。
- **建议**：把命名空间作为主题构造参数注入，或统一走一个 theme 自身的常量，消除 theme 对 edit 的依赖。
- **优先级**：低

### A-30 javadoc 指向已不存在的 `docs/plan` 文档

- **定位**：`common/src/main/java` 下 8 个文件的注释引用 `docs/plan/plan-*.md`，而该目录当前不存在：`client/edit/BuiltinEditorDefaults.java`、`client/edit/BuiltinEditorDescriptors.java`、`client/edit/FieldNumbers.java`、`client/edit/NumericDomain.java`、`client/ui/screen/RuleEditorScreen.java`、`client/ui/prototype/package-info.java`、`core/command/RuleConfigCommands.java`、`core/model/ConditionExpression.java`（`RuleEditorP4Panels` 实测 0 处引用）。
- **问题**：注释中的「事实来源」指向已归档或已删除的计划文档，违反 [conventions.md](../dev/conventions.md) 关于文档只描述当前状态的取向。
- **影响**：维护者按图索骥找不到依据；也解释了当前文档与实现漂移的成因。
- **建议**：改指对应的 `docs/dev` 页面（如 [editor-pages-contract.md](../dev/internals/editor-pages-contract.md)）或删除引用。
- **优先级**：低

### A-31 `EditorField` 作为值对象偏大

- **定位**：`EditorField`（353 行；全部字段类型的静态工厂集中在本类）。
- **问题**：一个描述符值对象承载了所有字段类型的构造入口与取值域表达，与 `EditorFieldType` 的 21 种取值一一对应。
- **影响**：新增字段类型既要在 `EditorFieldType` 加值、又要在 `EditorField` 加工厂，两处需同步。
- **建议**：工厂按字段族分组（文本族／数值族／引用族／结构族），或让 `EditorFieldType` 自己持有「构造默认值域」的策略对象。
- **优先级**：低

## 10. 审查局限

- 本轮为静态审查，未运行游戏与 Gradle，未覆盖实际渲染、布局与交互时序的正确性；结论限于代码结构与依赖关系。
- 行数按文本行数口径统计（末尾无换行的最后一行同样计为一行，与核验报告一致）；字段数与方法数因正则口径不同区间较大，正文已不再给出无口径的精确数字；`import` 与 `instanceof` 的计数均标注了口径。
- `instanceof` 与 `import` 计数包含正常的多态与工具用法，报告中只采纳能对应到「契约缺口／宿主兜底」的具体位置。
- `core.network.protocol` 是否属于文档所称「传输载荷类型」存在语义空间，A-01 已按「需文档裁定」处理，未预设结论。
- 未评估 UI 视觉、i18n 键集合一致性与性能（帧内扫描、缓存失效），这些属于其它审查维度。
