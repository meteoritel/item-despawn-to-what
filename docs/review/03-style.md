# UI 代码风格审查（维度三：命名 / 目录 / 文件结构 / 注释 / 样式组织 / 团队约定）

> 审查对象：`common/src/main/java/com/meteorite/itemdespawntowhat/client/`（主：`client/ui/**`；辅：`client/edit`、`client/net`、`client/key` 中直接影响 UI 的部分）。
> 定位约定：一律使用「相对路径 + 类名.成员名」，不写行号。
> 判定基线：[客户端层文档](../dev/layers/client.md)、[UI kit 入口](../dev/internals/ui-kit/README.md)、[编辑页契约](../dev/internals/editor-pages-contract.md)、[卡片布局](../dev/internals/editor-card-layout.md)、[ADR-0022](../adr/0022-client-ui-kit-adoption.md)、[ADR-0025](../adr/0025-reusable-client-ui-kit-boundary.md)、[ADR-0028](../adr/0028-canvas-page-and-flat-skin.md) 与项目根 `AGENTS.md`。

## 审查范围与方法

- 逐文件静态扫描：`client/**` 共 173 个 `.java` / 40650 行；`client/ui` 共 128 个 `.java`（124 个类 + 4 个 `package-info.java`）/ 35075 行。分包含 `package-info.java`：`kit` 60/7577、`theme` 5/683、`widget` 23/11039、`screen` 20/8956、`screen/form` 18/6418、`prototype` 2/402。
- 文件数与行数口径：文件数按 `.java` 计；行数按**文本行数**（末尾无换行的最后一行同样计为一行），本报告全部行数均用此口径。
- 机械统计项：类名后缀家族与前缀分布、`public`/`protected` 方法首词、类级 `boolean` 字段前缀、`static final` 常量命名、顶级类型数与 javadoc 覆盖率、注释语言、`0x` ARGB 字面量落点、`throw new *Exception("…")` 消息语言、内联数字字面量密度、`import` 与全限定名用法。
- 交叉比对项：`en_us.json` 与 `zh_cn.json` 键集；`client/**` 注释中出现的所有 `docs/*.md` 指针是否可解析；`client/ui/kit/package-info.java` 与 [UI kit 入口](../dev/internals/ui-kit/README.md) 的公开面清单差异；`screen` 与 `screen/form` 的相互 `import`。
- 未执行：`tools/check-ui-kit-boundaries.ps1`（其既有结论 60 文件 / 193 import / 0 违规直接引用）、Gradle 构建、任何 `.java` 修改。

---

## 结论概览（高优先级）

| # | 定位 | 一句话问题 |
|---|---|---|
| H1 | `client/**` 8 个文件的注释/包文档 | 9 处文档指针全部失效：`docs/plan/` 已被清空、`ui-kit-api.md`、`journal-ui-internals.md` 不存在 |
| H2 | `ui/screen/RuleEditorP4Panels`、`ui/screen/RuleEditorModel`、`ui/kit/UiRangeSlider` 等 6 文件 | 计划阶段码（P3/P4/P6）与任务码（task-9）残留 10 处，其中一处已进入类名 |
| H3 | `edit/TypeLabels`、`screen/RuleEditorScreen`、`screen/form/ConditionTreeOverlay` 等 7 类 | 同一「本地化键前缀」概念有 4 种常量名、4 处重复根前缀字面量（正确引用仅 3 处） |
| H4 | `widget/UiConditionGraphEditor`、`widget/UiConditionTreeSummary`、`widget/UiListEditor` | widget 层「文案由宿主注入」的既定约定（20/23 文件零硬编码）被这 3 个类打破，且前两者整份重复同一套键 |
| H5 | `Constants`、`edit/TypeLabels`、`edit/EditorChangeSet`、`screen/form/RuleTemplates`、`theme/UiTheme` | 模组命名空间字符串有 4 个常量来源 + 1 处裸字面量，客户端对权威常量 `Constants.MOD_ID` 零代码引用（仅 1 处 javadoc 提及） |
| H6 | `screen/form/CatalogSuggestions.looksLikeTranslationKey` 与 `screen/RuleEditorP4Panels.catalogLabel` | 同一判断各写一份、命名不同且边界条件不一致（`> 0` 与 `>= 0`） |
| H7 | `theme/UiPalette`+`theme/UiTheme` 与 `theme/UiFlatPalette`+`theme/UiFlatSkin` | 两套主题并行且都是静态调用（`UiTheme` 146 行／`UiPalette` 277 行、34 个文件）；同名 `drawFocusOutline` 语义不同；同族两个条件树编辑器采用不同主题 |
| H8 | `ui/screen` 与 `ui/screen/form` | `screen` 同时承载页面、模型、预览图标、纯值映射与控件；两包相互 `import` 形成双向依赖 |
| H9 | `theme/UiControlStyle`、`kit/UiLightbox`、`kit/TextScroll`、`widget/UiSlider` 等 11 处 | 注释记录「原实现 / 旧版 / 不再 / 需要改为」式沿革叙述，其中一处引用的类已不存在 |
| H10 | `kit/` 各记录类 | 异常消息语言分裂：kit 内 43 处中英文 42、中文 1；同构的 `UiTextHistory` 与 `UiNavigationHistory` 一条中文一条英文 |
| H11 | `theme/UiFlatSkin` | 主题层越界依赖 `client.edit.TypeLabels`，只为取一个命名空间字符串 |
| H12 | `client/ui/**` 12 个文件 + 20 个 import Gson 的文件 | 直接使用 `core.network.protocol` 传输载荷类型，且宿主层 Gson 用法无成文约定 |

中优先级 20 条、低优先级 8 条，合计 40 条，见下文分组。

---

## 一、注释规范与文档指针

### H1. 客户端注释中的文档指针 9 处全部失效

**定位**：`ui/kit/package-info.java`（`docs/dev/internals/ui-kit-api.md`，两处：公开行为约定段与「尚未完成」段）、`ui/prototype/package-info.java`（`docs/plan/plan-frontend-rewrite-contract.md`）、`ui/kit/UiControlStyle.java`（`docs/dev/internals/journal-ui-internals.md`）、`edit/BuiltinEditorDefaults.java` 与 `edit/BuiltinEditorDescriptors.java` 与 `ui/screen/RuleEditorScreen.java`（`docs/plan/plan-frontend-rewrite-forms.md`）、`edit/FieldNumbers.java` 与 `edit/NumericDomain.java`（`docs/plan/plan-gui-rule-update-fields.md`）。

**问题**：这 9 处引用逐个核对后均不可解析——`docs/plan/` 目录当前为空（计划文档已归档），`docs/dev/internals/` 下现存 `README.md`、`assets/`、`editor-card-layout.md`、`editor-input-state.md`、`editor-pages-contract.md`、`ui-kit/`（内含 `README.md` 等分册），没有 `ui-kit-api.md`，也没有 `journal-ui-internals.md`。活文档 [UI kit 入口](../dev/internals/ui-kit/README.md) 才是 kit 的现行入口。

**影响**：注释里的「事实来源」指向空气。新维护者按注释去找字段取值域、拆分门槛、样式分工，会先撞一次 404，再被迫从代码反推；同一约束在活文档与死指针之间会有两套说法，无从判断哪份是当前状态。

**建议**：把这 9 处改为可解析指针——kit 相关指向 [UI kit 入口](../dev/internals/ui-kit/README.md)，表单字段与数值域指向 [编辑页契约](../dev/internals/editor-pages-contract.md) 或 [卡片布局](../dev/internals/editor-card-layout.md)，样式分工指向 [ADR-0028](../adr/0028-canvas-page-and-flat-skin.md)。同时在 `tools/` 增加一条只读校验：扫描 `common/src/main/java/**` 注释中形如 `docs/**/*.md` 的路径必须存在。禁止再以 `docs/plan/` 作为长期依据。

**优先级**：高

### H2. 计划阶段码与任务码进入生产源码

**定位**：`ui/screen/RuleEditorP4Panels.java`（类名中的「P4」；类 javadoc「P4 宿主装配（主计划 §4/§7/§8）」）、`ui/screen/RuleEditorScreen.java`（分区注释「P6：草稿落盘与冲突」「P6：节流落盘…」「P6：关屏前把未应用草稿写盘…」）、`ui/screen/RuleEditorModel.java`（「草稿落盘日志（P6：…）」「// ---- P6：草稿落盘 / 恢复 / 冲突 ----」，以及类 javadoc「（契约 §5，task-9 前置）」）、`ui/screen/RuleEditorEditPages.java`（「// ---- 目录面板与结构图解（P4） ----」）、`edit/RuleIssueLocator.java`（「由 P3 负责」）、`ui/kit/UiRangeSlider.java`（「（P4 时间条/双端轨道）」）。

**问题**：阶段码与任务码共 10 处（6 文件），其中 `RuleEditorP4Panels` 把阶段号写进了公开类名。这些编号只在当时的计划文档里有定义，而 `docs/plan/` 已被清空，任何读者都无法还原 P3/P4/P6 指什么。项目根 `AGENTS.md` 明确要求只描述当前已确认状态、不记录临时阶段规划。

**影响**：类名是永久资产，`RuleEditorP4Panels`（1151 行，被其它 11 个文件引用：`screen` 6 个、`screen/form` 5 个；含自身 12 个，其中真正 `import` 的 4 个）会成为后续所有调用点的心智负担；阶段码出现在分区注释里，会诱导后来者继续沿用「按计划编号切分」的组织方式，而不是按职责切分。

**建议**：类名按职责重命名（该类的实际内容是画布页与叶面板的宿主装配，建议取表达该职责的名字，如 `ConditionCanvasHost`）；分区注释改为描述职责（如「草稿落盘与冲突」）；删除 `task-9` 之类任务引用，需要保留约束时改写为可直接验证的规则句。

**优先级**：高

### H9. 注释中的实现沿革叙述

**定位**（11 处，按强度排序）：

- `ui/kit/TextScroll.java` 类 javadoc：「旧的 `support/ScrollTextHelper` 仅作为 `String` 门面委托到此处」——`client/` 下只有 `edit`、`key`、`net`、`ui` 四个包，全仓库不存在 `ScrollTextHelper`，该句描述的类已不存在。
- `ui/kit/UiLightbox.java`：「命中只查一次：原实现先用 targetAt 判空、再交给 renderTooltip 重扫同一坐标」。
- `ui/theme/UiControlStyle.java` 类 javadoc：「与既有 `UiControl` 硬编码值逐像素相同……旧界面不会触发，因此换用本样式不改变既有画面」；同段引用的 `UiTextPalette` 也不存在。
- `ui/widget/UiSlider.java`：「公开方法签名与旧版保持一致」「step<=0 时沿用旧版键盘回退档」「保留旧的「每次变化都回调」语义」。
- `ui/screen/RuleEditorScreen.java`：「原注册名位置改为图标配方」「草稿标志与问题提示不再使用符号」。
- `ui/screen/RuleEditorEditPages.java`：「不再出现「隐式结果」这类结构术语」「不再用「本规则使用顶层 effects」这类结构说明」。
- `ui/screen/form/ConditionTreeOverlay.java` 与 `screen/form/ConditionTreeChrome.java`：「不再走浮动调板，而是……」。
- `edit/NumericDomain.java`：「（旧界面人工上限只能作窗口）」。
- `ui/kit/UiInputRouter.java`：「宿主表单/屏幕若自行提前拦截容器键，需要改为通过本类路由」。

**问题**：这些句子都在对比一个已被替换的实现，而不是描述当前可验证的规则。`TextScroll` 与 `UiControlStyle` 两处还引用了不存在的类型/类，等于把「已删除的东西」写成事实。

**影响**：沿革叙述会随下一次重构再次过期，且读者无法判断「旧版」指什么、约束是否仍然成立；真正的约束（例如「标量滑块的回填不产生回调与撤销历史」）被埋在对比句里，检索不到。

**建议**：统一改写为当前状态句式——「X 不做 Y，因为……」或「X 必须经 Y 路由」。需要表达兼容性时改为对当前公开 API 的约束（例如 `UiSlider` 的「fluent setter 返回自身」可直接保留，删掉「与旧版保持一致」）。删除对已不存在类型与类的引用。

**优先级**：高

### H10. 异常消息语言分裂

**定位**：`ui/kit/` 各记录/值类型的构造校验。英文示例：`UiSliderWindow`（`"Slider window bounds must be finite"`、`"Slider window max must be greater than min"`）、`UiNumberPolicy`（6 条）、`UiRangeValue`（5 条）、`UiNode`（5 条）、`UiTransform`、`UiTidyTreeLayout`、`UiIcon`/`UiIconSheet`、`UiLinearLayout`、`LightboxImage`、`UiTreePath`、`UiSliderStyle`、`UiCyclicRange`。中文仅 1 条：`ui/kit/UiTextHistory.java`（`"文本历史容量必须大于零"`）。

**问题**：`client` 全量统计出 `throw new *Exception(…）` 共 67 处（口径：`client/**` 全部 `.java` 中每个 `throw new *Exception(` 记 1 处，消息取该构造调用内的首个字符串字面量），其中中文 6 处、`"Utility class"` 18 处（工具类私有构造的固定写法，可视为独立约定）、其余英文参数校验消息 43 处。kit 内部 43 处（中文 1 处、英文 42 处），结构完全同构的一对是 `UiTextHistory`（中文 `"文本历史容量必须大于零"`）与 `UiNavigationHistory`（英文 `"Navigation capacity must be positive"`）——两者都是「容量必须为正」的构造校验。

**影响**：同一层同一种校验出现两种语言，与项目「注释与文案使用简体中文」的口径相悖；排查日志时要在两种语言间切换；新写一个记录类时无从判断该用哪种。

**建议**：二选一并写进 [客户端层文档](../dev/layers/client.md) §4.1：推荐统一简体中文（与既有中文用例一致），`"Utility class"` 单独保留为惯用法或一并中文化。修正 `UiNavigationHistory`、`UiNumberPolicy` 等英文消息。

**优先级**：高

### M15. 跨包复用嵌套类型 + 沿革叙述混合的类 javadoc

**定位**：`ui/widget/UiConditionGraphEditor.java` 类 javadoc：「宿主类型复用 `UiConditionTreeSummary` 的嵌套类型（`TypeOption` / `LeafFactory` / `Listener` / `Issue` / `IssueKind`）与常量（`ROOT_PATH` / `DEEP_HINT_DEPTH`），使 `ConditionSupport` 与既有调用点无需改签名」。

**问题**：一句里混了三件事——真实的类型共享关系、一个 `screen/form` 类型的名字（`ConditionSupport`，本文件并未 `import` 它）、以及「无需改签名」的沿革理由。

**影响**：读者会误以为 `UiConditionGraphEditor` 与 `ConditionSupport` 有依赖；真正需要记录的「宿主注入面复用 `UiConditionTreeSummary` 嵌套类型」这一强耦合事实反而被淡化——而该耦合正是两个编辑器无法独立演进的原因。

**建议**：拆成两句——「宿主注入面的类型与常量复用 `UiConditionTreeSummary` 的嵌套类型与常量，两个编辑器共用同一份宿主契约」；删除「无需改签名」的沿革表述与跨包类型名。

**优先级**：中

### L2. `UiMetrics` 被列为 kit 公开入口，但宿主无法写入

**定位**：`ui/kit/package-info.java` 公开入口清单中的 `UiMetrics`；`ui/kit/UiMetrics.java`（`start()` / `built()` / `laidOut()` / `rendered()` / `hit()` 均为包私有，仅读取方法为 `public`）。

**问题**：文档把它归入「第三方宿主可直接依赖」，但宿主能做的只有 `new UiMetrics(true)` 与读计数；产出计数的入口只对 kit 包内开放（`UiDocument` 持有实例）。

**影响**：公开面清单失真，第三方按文档接入会拿到一个永远为 0 的计数器。

**建议**：在 `package-info.java` 的「内部实现」段中列出 `UiMetrics`（其计时接线属 kit 内部），或补齐公开写入 API。

**优先级**：低

### L3. javadoc 引文损坏

**定位**：`ui/widget/UiStructureDiagram.java` 类 javadoc：「结构图解（宿主侧控件，主计划 §4 结果与：253 图解验收）」。

**问题**：「§4 结果与：253 图解验收」句子结构破损，且指向已清空的 `docs/plan/`；同时「主计划」一词在 `client` 中出现 14 次（13 文件），均无当前可解析的实体。

**影响**：读者无法据此核对任何验收标准。

**建议**：删除括号内的引文，只保留「宿主侧控件」与职责句；「主计划」引用随 H1 一并清理。

**优先级**：低

### L6. 美术资源 TODO 现状

**定位**：`ui/theme/UiTheme.java`（立体边与滚动条轨道/滑块贴图，2 处）、`ui/widget/UiCheckBox.java`（勾选图标与类 javadoc，2 处）、`ui/widget/UiConditionTreeSummary.java`（展开标记，1 处）。

**问题**：`client` 全部 5 处 TODO/FIXME/HACK 都是「TODO 美术资源」，没有功能性 TODO。

**影响**：如实记录现状——功能层面无待办残留；但 5 处占位实现（纯色填充、ASCII `+`/`-`）会在不同分辨率下与像素主题不一致，且没有集中清单。

**建议**：把这 5 处收敛为一份「占位资源清单」（可置于 [UI kit 入口](../dev/internals/ui-kit/README.md) 的已知限制一节），避免 TODO 长期分散在 3 个类里。

**优先级**：低

---

## 二、命名规范

### H3. 本地化键前缀常量：4 种命名 + 4 处重复根前缀字面量

**定位**：权威定义 `edit/TypeLabels.UI_PREFIX = "gui.itemdespawntowhat.edit."`。重复声明同一字面量的有 4 处：`edit/RuleRequirements.UI`、`screen/form/CatalogSelectionControl.UI`、`screen/RuleEditorScreen.UI`、`screen/SourceBlacklistPicker.UI`；正确引用 `TypeLabels.UI_PREFIX` 派生赋值的 3 处：`screen/RuleEditorEditPages.UI`、`screen/RuleEditorP4Panels.UI`、`screen/form/ConditionTreeOverlay.EDIT_UI`（其中 `EDIT_UI` 命名与其余常量的 `UI` 口径不一致，属命名问题而非重复字面量）。其他同概念不同名：`screen/form/NaturalSummary.PREFIX`、`screen/form/RuleTemplates.LABEL_PREFIX`、`screen/form/FormControl.ISSUE_PREFIX`、`screen/form/ItemCountsControl.QUANTITY_ROW_KEY`、`widget/UiConditionGraphEditor.KIND_MENU_HINT_KEY`、`edit/RuleNaming.PREFIX`、`edit/EditSession.OP_*`（直接存完整键）。

**问题**：同一概念有 `UI`、`EDIT_UI`、`UI_PREFIX`、`PREFIX` 四种常量名，且 4 个类各自复制根前缀字面量，正确引用权威常量的只有 3 处。前缀一旦调整（例如统一加版次段），编译器不会提示任何一个复制点。

**影响**：键前缀是 UI 与语言的唯一接口，重复来源会让「键是否同步」这件事从「改一处」变为「全仓库搜索」；命名不统一使新文件无法判断该新建哪种常量。

**建议**：确立单一规则——所有 `gui.itemdespawntowhat.*` 前缀常量必须由 `TypeLabels.UI_PREFIX`（或其派生的唯一子域常量）组合得到，禁止复制字面量；常量命名统一为 `<作用域>_KEY_PREFIX`（如 `TREE_KEY_PREFIX`、`ISSUE_KEY_PREFIX`、`SUMMARY_KEY_PREFIX`），单键常量用 `<用途>_KEY`。

**优先级**：高

### H4. widget 文案注入约定被 3 个类打破

**定位**：`widget/UiConditionGraphEditor.java`（46 处内联 `gui.itemdespawntowhat.*` 字面量）、`widget/UiConditionTreeSummary.java`（39 处）、`widget/UiListEditor.java`（3 处）。对照：`widget` 其余 20 个类硬编码项目键为 0，文案通过 `UiCatalogGrid.Texts` 记录、`setEmptyMessage(Component)`、`setPageLabel(Component)`、`UiCyclicTimeBar.Preset` 等方式由宿主注入。

**问题**：两个条件树编辑器内联了同一套消息键（`...edit.tree.*`、`...edit.issue.*`），键名与文本成对出现在两个文件里；`UiListEditor` 也内联了 `...edit.list.*` 三处。

**影响**：`widget` 层「可被宿主复用」的定位因此只在 20/23 的类上成立。任一键改名需要在两个 1500+ 行的文件里同步手改，且没有编译器保护；更实际的是，任何想复用条件树控件的宿主都被绑死在 `itemdespawntowhat` 的键空间上。

**建议**：为两个编辑器补齐注入面（可参照 `UiCatalogGrid.Texts`，定义承载 tree/issue 两组文案的记录，由 `screen/form` 侧装配），`UiListEditor` 同理。若短期不改造，至少在 [客户端层文档](../dev/layers/client.md) §4.2 写明「条件树与列表编辑器是项目专用控件，文案键内联属已知例外」，避免读者按通用控件理解。

**优先级**：高

### H5. 命名空间常量四源 + 裸字面量

**定位**：权威 `Constants.MOD_ID = "itemdespawntowhat"`（`client` 内零**代码**引用，仅 `ui/kit/package-info.java` 有 1 处 javadoc 引用）；平行来源 `edit/TypeLabels.OWN_NAMESPACE`（被 10 个文件引用）、`edit/EditorChangeSet.OWN_NAMESPACE`（与前者同包、同名、同值，该类另在自己的逻辑里使用）、`screen/form/RuleTemplates.NAMESPACE`（该类已 `import Constants`，仅用于 `Constants.LOG`）；裸字面量 `theme/UiTheme.java`（`ResourceLocation.fromNamespaceAndPath("itemdespawntowhat", …)`）。

**问题**：同一个字符串在客户端有 4 个常量名与 1 处直接字面量；`TypeLabels.OWN_NAMESPACE` 与 `EditorChangeSet.OWN_NAMESPACE` 同名同值却分属两个类，而项目权威常量 `Constants.MOD_ID` 在客户端没有任何代码引用。

**影响**：命名空间是资源路径、规则 id、模板目录的共同前缀，改名需要全仓库搜索；同名常量最危险——引用者以为拿到的是唯一来源，实际可能引用到另一个类。

**建议**：保留 `Constants.MOD_ID` 为唯一来源；`TypeLabels`、`EditorChangeSet`、`RuleTemplates` 三处改为引用它（kit 内不允许引用 `Constants`，需要命名空间的 kit 类应改由宿主注入字符串）。`UiTheme` 的裸字面量一并替换。

**优先级**：高

### H6. 同义启发式两份、命名与边界不一致

**定位**：`screen/form/CatalogSuggestions.looksLikeTranslationKey(String)`（判定 `label.indexOf('.') > 0`）与 `screen/RuleEditorP4Panels.catalogLabel(Component)` 内的局部变量 `looksLikeKey`（判定 `raw.indexOf('.') >= 0`）。

**问题**：两处都是「无空格、无冒号且含点号则按翻译键解析」的同一判断，但一处抽成方法、一处写成局部变量；命名 `looksLikeTranslationKey` 与 `looksLikeKey` 指同一概念；边界条件 `> 0` 与 `>= 0` 对前导点字符串（如 `.foo`）判定相反。

**影响**：目录标签的显示行为取决于「走哪个入口」，而两个入口处理的是同一类数据（一个用于候选值、一个用于目录卡片）；不一致会成为难复现的显示差异，且未来改判定规则必然漏改一处。

**建议**：抽成一个共享方法（建议置于 `screen/form` 或 `edit`，命名 `TranslationKeys.looksLikeKey(String)` 或 `isTranslationKey`），两处调用；并补一条对前导点/纯点串的判定口径。

**优先级**：高

### M1. 类名后缀家族一词多义

**定位**：

- `View`（8 个，三种含义）：kit 内容/容器 `UiImageView`、`UiScrollView`；widget 通用列表树 `UiListView`、`UiTreeView`；项目专用控件却落在 `screen` 的 `TagCarouselView`、`RuleRecipeView`（二者 `implements UiWidget`）；`screen/form` 的整表单 `FormView` 与单行只读 `ReadOnlyTextView`。
- `Control`（6 个，两种含义）：kit 基类 `UiControl`、widget `UiSegmentedControl`（可点击控件）；`screen/form` 的 `FormControl`（抽象字段行）、`NumberControl`、`ItemCountsControl`、`CatalogSelectionControl`（字段行实现，均 `extends FormControl`，与 `UiControl` 无继承关系）。
- `Editor`（三种含义）：kit `UiTreeEditor`（树编辑核心）；widget `UiConditionGraphEditor`、`UiConditionTreeSummary`、`UiListEditor`（控件）；`screen` 的 `RuleEditorScreen`、`RuleEditorEditPages`、`RuleEditorModel`、`RuleEditorP4Panels`、`RuleEditorOpener`（整个编辑器功能簇）。

**问题**：同后缀在三个包中分别表示「容器」「控件」「字段行」「整屏」；`*Control` 尤其危险——读者会去找它与 `UiControl` 的继承关系，但不存在。

**影响**：新增一个控件/字段行时，命名选择与落包选择都没有唯一答案；检索 `*View` 会混出三种抽象层级，重构时无法按后缀批量处理。

**建议**：按层固定后缀语义并写入 [客户端层文档](../dev/layers/client.md) §4：kit 用 `*View` 表示可排版容器、`*Control` 表示基类及其可点击子类；widget 的通用列表/树控件保留 `*View`；`screen/form` 的字段行改用 `*Field`（`FormControl`→`FormField`、`NumberControl`→`NumberField`、`ItemCountsControl`→`ItemCountsField`、`CatalogSelectionControl`→`CatalogSelectionField`）；`screen` 的整屏保留 `*Screen`、页面装配保留 `*Pages`。

**优先级**：中

### M2. `UiTarget` 与两个 `*Target` 接口同词不同物

**定位**：`kit/UiTarget.java`（`record UiTarget(Kind, index, payload, UiRect, tooltip, action)`——命中结果）；`kit/UiFocusTarget.java`、`kit/UiInputTarget.java`（接口）。

**问题**：三个 `*Target` 中两个是「被作用的对象」接口，一个是「命中的结果」值对象。

**影响**：读到 `UiTarget` 的调用方会先猜它是接口；命中结果与作用目标混在一组名字里，查找与补全都受影响。

**建议**：把命中结果改名为 `UiHit`（或 `UiHitResult`），保留 `UiFocusTarget` / `UiInputTarget` 语义。

**优先级**：中

### M3. `UiNode` 与 `UiTreeNode` 同名不同物且跨包

**定位**：`kit/UiNode.java`（`sealed permits Row, Gap, Divider, Frame`——文档排版节点）；`widget/UiTreeNode.java`（`public final class UiTreeNode<T>`——树控件节点）。

**问题**：两者都在 `ui` 下、都以 `Node` 结尾，语义毫无关系（排版元素 vs 树节点）。

**影响**：跨包同时使用两者时必须靠 `import` 区分；按 `UiTree*` 前缀检索会把 kit 的 `UiTreeEditor`/`UiTreePath`/`UiTreeCodec` 与 widget 的 `UiTreeNode`/`UiTreeView` 混为一谈，而前者是 kit 核心、后者是宿主控件。

**建议**：`widget/UiTreeNode` 改名为 `UiTreeItem`（与 `UiListView` 的行概念对齐）；或在 kit 侧把排版节点改名为 `UiDocNode`。同时按包固定 `UiTree*` 前缀归属。

**优先级**：中

### M4. kit 内 4/59 类缺 `Ui` 前缀，且成对类前缀不一致

**定位**：`kit/LightboxImage.java`、`kit/TextMeasurer.java`、`kit/TextScroll.java`、`kit/PixelSliderPainter.java`。对照：kit 其余 55 个类全部 `Ui*`；`theme` 4/4、`widget` 22/22、`prototype` 1/1 均为 `Ui*`。

**问题**：同一族内前缀不一致最明显的是滑块绘制：接口 `kit/UiSliderPainter.java` 与实现 `kit/PixelSliderPainter.java`；其次是灯箱：`UiLightbox` / `UiImageView` / `LightboxImage`。

**影响**：`Ui` 前缀在本项目实际承担「这是 kit 组件、可被宿主依赖」的标记功能（`kit/package-info.java` 的公开入口清单也据此组织）。4 个例外让「按前缀判断是否 kit 组件」的快速识别失效。

**建议**：二选一——把 4 个类补前缀（`UiTextMeasurer`、`UiTextScroll`、`UiPixelSliderPainter`、`UiLightboxImage`），或在 `kit/package-info.java` 写明「`Text*`/`*Painter` 家族不带 `Ui` 前缀」的例外规则。改动成本低（kit 边界脚本会同步校验引用）。

**优先级**：中

### M5. `UiSliderWindow` 的 `Window` 与真实窗口撞词

**定位**：`kit/UiSliderWindow.java`（`record UiSliderWindow(double min, double max)`——轨道显示窗口）。对照：`kit/UiFloatingPanel`、`widget/UiModal`、`widget/UiModalStack` 才是界面窗口/弹窗。

**问题**：在 UI 包里，`Window` 的自然语义是屏幕上的窗口；该记录表达的是「数值域里被铺到轨道上的一段」。

**影响**：检索 `Window` 或按名字推断职责时会把它归入窗口控件；而它实际与 [数值策略](../dev/internals/ui-kit/value-controls.md) 同组。

**建议**：改名以消除歧义，如 `UiSliderViewport` 或 `UiSliderTrackRange`；文档同步。

**优先级**：中

### M6. 阶段码类名与 `Page`/`Pages` 并用

**定位**：`screen/RuleEditorP4Panels.java`（类名含阶段码）；`screen/RuleEditorEditPages.java` 与其中嵌套枚举 `RuleEditorEditPages.Page`。

**问题**：同一概念在类名用复数 `Pages`、在枚举用单数 `Page`；`RuleEditorP4Panels` 的 `P4` 不是英文词，无法从名字读出职责。

**影响**：见 H2 的类名负担；`Pages`/`Page` 并用虽无害，但在 `RuleEditorEditPages`、`RuleEditorScreen`、`RuleEditorModel` 三者之间建立不起「谁是页面集合、谁是单页」的直觉。

**建议**：`RuleEditorP4Panels` 按职责改名（见 H2）；保留 `RuleEditorEditPages`（四页集合）与嵌套 `Page`（单页枚举），并在其中一处 javadoc 点明「一个 `Page` 是一页，`Pages` 是四页装配」。

**优先级**：中

### M7. 宿主回调类名族不统一

**定位**：同一「由宿主提供实现/数据」的概念在四个包里叫四个名字：`screen/form/PickerHost`（接口，由 `FormView` 实现）、`widget/UiFooterHost`（接口，由内容控件实现）、`screen/form/RuleTemplateHooks`（静态工具）、`net/EditorScreenHooks`（注册槽）、`screen/form/SuggestionProvider`（接口）、`kit/UiHistoryDispatcher`（派发）、`kit/UiTreeCodec`（编解码注入）。

**问题**：`Host` 与 `Hooks` 语义几乎重叠且都无统一前缀；`UiFooterHost` 有 `Ui` 而 `PickerHost` 没有；`RuleTemplateHooks` 名为 Hooks 却是工具类而非回调接口。

**影响**：新增注入点时命名无依据；读者难以从名字判断「这是我要实现的接口」还是「这是现成工具」。

**建议**：固化名词表——宿主必须实现的接口用 `*Host`（widget/kit 内加 `Ui` 前缀：`UiFooterHost`；宿主业务侧不加：`PickerHost`）；宿主可选注册的回调用 `*Hooks`；纯转换/派发工具用动词名词（`*Dispatcher`、`*Codec`、`*Provider`）。`RuleTemplateHooks` 若为工具类应改名。

**优先级**：中

### M11. 布尔字段前缀存在 4 处例外

**定位**：`client` 类级 `boolean` 字段共 156 个，无前缀 152 个（`enabled`、`visible`、`focused`、`selected`、`pressed`、`hovered`、`dragging`、`collapsed`、`dirty`、`expanded`、`available` 等），带前缀仅 4 个：`kit/UiImageView.allowUpscale`、`screen/RuleEditorEditPages.showAdvancedEffects`、`widget/UiCatalogGrid.canPrev`、`widget/UiCatalogGrid.canNext`。

**问题**：主导约定清晰（状态形容词），4 个例外中的 `canPrev`/`canNext` 是查询方法名的写法被用到了字段上。

**影响**：影响很小，但约定若不成文，例外会缓慢增多；`is*` 前缀在本项目已明确用于**方法**（`isVisible`、`isSelected` 等 114 个 `is*`、29 个 `can*`、6 个 `has*`）。

**建议**：写成规则「布尔**字段**用状态形容词，不写 `is/has/can` 前缀；布尔**方法**用 `is*`/`has*`/`can*`」，并修正上述 4 处。

**优先级**：中

### M12. 两套「控件」基类并存

**定位**：`kit/UiControl.java`（`implements UiFocusTarget`；kit 内 9 处引用，宿主仅 `widget/UiButton` 持有一个实例）；`widget/UiWidget.java`（接口，被 20+ 个 widget 与 3 个 `screen/form` 类实现）。

**问题**：两者都叫「控件」，但互不继承、接口也不同（`UiControl` 是具体类，`UiWidget` 是渲染/输入契约）。`UiControl` 在 kit 公开入口清单里，实际宿主使用面只有 `UiButton`。

**影响**：读者面对 `UiControl` 与 `UiWidget` 无法判断该继承/实现哪一个；`UiControl` 的实际定位（kit 内部可排版控件）与文档的「公开入口」定位不一致。

**建议**：在 [客户端层文档](../dev/layers/client.md) §4.1/§4.2 明确分工——`UiControl` 是 kit 内容排版的内部控件基类（宿主一般经由 `UiLightbox`/`UiFloatingPanel` 间接使用），`UiWidget` 是宿主控件的统一契约；或让 `UiWidget` 继承/委托 `UiControl` 以消除双基类。

**优先级**：中

### L1. 无出处引用

**定位**：`kit/UiSliderExamples.java`（「规格 §7」）、`screen/form/NumberControl.java`（「字段规格 §1/§4」）、`screen/form/FormControl.java`（「字段规格 §4」）、以及 `screen/RuleTriggers`、`screen/ResultStructure`、`screen/RuleCostBinding`、`screen/RuleEditorEditPages`、`widget/UiNarration`、`widget/UiRangeBar`、`widget/UiStructureDiagram`、`widget/UiCyclicTimeBar` 中的「主计划 §X」「契约 §X」「fields §X」「forms.md §X」。

**问题**：这些简称均无当前可解析的文档实体（`forms.md`/`fields`/`规格`/`主计划` 对应的文件在 `docs/` 下都不存在）。统计：`主计划` 14 处 / 13 文件，`契约 §` 20 处 / 16 文件，`forms.md` 8 处 / 5 文件，`规格` 7 处 / 7 文件，`fields` 4 处 / 3 文件。

**影响**：这些引用是注释里唯一的「为什么」来源，失效后约束退化为无根据的断言。

**建议**：与 H1 一并处理——把仍有效的约束改写到 [编辑页契约](../dev/internals/editor-pages-contract.md)、[卡片布局](../dev/internals/editor-card-layout.md)、[UI kit 入口](../dev/internals/ui-kit/README.md)，删掉无对应文档的编号引用。

**优先级**：低

### L4. 裸注册名特判

**定位**：`ui/screen/EntityPreviewIcons.java`（对 `minecraft:ender_dragon`、`minecraft:ghast`、`minecraft:squid`、`minecraft:glow_squid` 分别设定宽高与中心偏移）。

**问题**：用字符串比较识别实体类型，并为每类硬编码一组尺寸数字。

**影响**：新增需特殊适配的实体必须改这类字符串分支；拼写错误不会被编译器发现（会静默走通用分支）。

**建议**：改用 `EntityType.ENDER_DRAGON` 等原版常量或 `ResourceLocation` 常量；把每类实体的尺寸/中心抽成具名常量或小记录，并说明数值用途（适框修正）。

**优先级**：低

### L5. 嵌套枚举 `Kind` 四义

**定位**：`kit/UiTarget.Kind`（ROW/ICON）、`kit/UiTreeEditor.Kind`（CREATE/DELETE/UPDATE/MOVE）、`screen/form/ConsumptionSummary.Kind`、`screen/ResultStructure.Kind`。

**问题**：四个 `Kind` 成员集完全不同。

**影响**：有外层类型限定，风险有限；但静态导入或 `switch` 密集的文件里会出现「哪个 Kind」的歧义。

**建议**：按语义改名（`UiTarget.Kind`→`UiTarget.Origin`、`ConsumptionSummary.Kind`→`ConsumptionMode`、`ResultStructure.Kind`→`ResultShape`），保留 `UiTreeEditor.Kind`（树操作类型，语义最贴切）。

**优先级**：低

---

## 三、目录组织与文件结构

### H8. `screen` 包职责混杂，且与 `screen/form` 形成双向依赖

**定位**：`ui/screen` 直属 20 个文件 / 8956 行。按职责可分为四组：屏幕与装配（`RuleEditorScreen`、`RuleEditorEditPages`、`RuleEditorP4Panels`、`RuleEditorOpener`、`RuleEditorModel`）、**预览图标**（`BlockPreviewIcons`、`EntityPreviewIcons`、`FluidPreviewIcons`、`RulePreviewIcons`、`TagPreviewIcons`）、**纯值/映射用具**（`CatalogEntryPolicy`、`FieldCatalogChoices`、`ResultStructure`、`RuleCostBinding`、`RuleDisplayLabels`、`RuleTriggers`）、**控件与宿主回调**（`RuleNameInput`、`RuleRecipeView`、`TagCarouselView`、`SourceBlacklistPicker`）。

**问题**：四类职责共处一包。`*Icons` 一组在 [客户端层文档](../dev/layers/client.md) §4.3 的表格里被单列一行（「预览图标：物品 / 方块 / 实体 / 流体 / 标签成员 / 规则配方的预览与缓存」），但仓库里没有与之对应的包。同时，`screen/form` 有 5 个文件反向 `import` `screen` 的 4 个类型（`RuleEditorP4Panels`、`RuleDisplayLabels`、`TagPreviewIcons`、`FluidPreviewIcons`），而 `screen` 有 3 个文件 `import` `screen/form` 的 11 个类型——两包互相依赖。

**影响**：`screen/form` 无法脱离 `screen` 单独编译或复用，[客户端层文档](../dev/layers/client.md) §7「新增字段行 → `ui/screen/form`」这条扩展路径实际上要求改 `screen`；`screen` 继续膨胀会让「找页面代码」的检索成本随每一类新辅助类上升。

**建议**：按职责拆分子包并禁止反向依赖——预览与缓存类移入 `ui/screen/preview`（或 `ui/preview`）；纯值映射用具移入 `ui/screen/support`；`form` 侧对 `RuleEditorP4Panels` 的依赖是当前环路的枢纽（5 处引用），应把其中的目录/叶面板装配下沉为 `screen/form` 自己的类型，使 `form` 不再 `import` `screen`。

**优先级**：高

### M8. `package-info.java` 只覆盖 4/6 个包，缺的恰是最大的两个

**定位**：有 `package-info.java`：`ui/kit`（内容详实，含公开入口分组、依赖方向、行为约定）、`ui/theme`、`ui/widget`、`ui/prototype`。缺：`ui/screen`（20 文件 / 8956 行）、`ui/screen/form`（18 文件 / 6418 行）。

**问题**：两个最大的包没有包级说明；`screen`/`screen/form` 的职责边界与反向依赖现状只能靠读代码得出。

**影响**：新成员进入 `screen` 时没有「这里放什么、不放什么」的入口说明，这正是 H8 混杂现状得以持续的原因。

**建议**：为两包各补一份 `package-info.java`，内容对齐 [客户端层文档](../dev/layers/client.md) §4.3：`screen` 写「屏幕与页面装配、预览图标、只读值映射」及子包归属规则；`screen/form` 写「字段行与表单视图，不反向依赖 `screen`」。

**优先级**：中

### M9. kit 公开面清单两处并存且已漂移

**定位**：`ui/kit/package-info.java` 的「公开入口」分组清单 与 [UI kit 入口](../dev/internals/ui-kit/README.md) §2 的公开入口表。

**问题**：两份清单都宣称是 kit 的公开面，但 README §2 缺少 package-info 中的 10 个类：`UiTreeEditor`、`UiTreePath`、`UiTreeCodec`、`UiTreeMoveInteraction`、`UiTextHistory`、`UiHistoryShortcut`、`UiHistoryDispatcher`、`UiModelBounds`、`UiBlockPreview`、`UiCarousel`、`UiPointer`、`UiRenderLayers` 中的 10 个（`UiTreeEditor` 与 `UiTreePath` 以其它形式出现在 README 分册链接语境中）。

**影响**：kit 是可被宿主依赖的边界，公开面清单是接入契约。两份不一致时，新接入者不知道以哪份为准；第三方按 README 接入会漏掉树编辑、文本局部历史、渲染层级/指针等入口。

**建议**：确立单一权威——建议以 `ui/kit/package-info.java` 为唯一公开面清单（离代码最近、随类增删同步修改最自然），README §2 改为按分组给出指向该清单的短表 + 分册链接，不再重复枚举类名。若保留两份，则加脚本比对两侧类名集合。

**优先级**：中

### M10. 超大文件内部组织不一致

**定位**：分区注释（`// ---- 名称 ----`）使用情况：`widget/UiConditionGraphEditor`（2637 行 / 23 个分区）、`screen/RuleEditorScreen`（2194 行 / 14）、`screen/form/FormControl`（1910 行 / 11）、`screen/RuleEditorEditPages`（3352 行 / 13）、`widget/UiConditionTreeSummary`（1573 行 / 7）、`screen/form/FormView`（1378 行 / **1**，仅「PickerHost」）、`screen/RuleEditorP4Panels`（1151 行 / 4）；`kit/UiScrollView`（202 行）使用 `// ---------- 名称 ----------` 的长横线变体，与全场 `// ---- 名称 ----` 不同。

**问题**：同为 1300~2600 行量级的 6 个文件，分区数从 1 到 23 不等；`FormView` 是其中唯一几乎无分区的文件。另有一种长横线变体。 `screen/RuleEditorScreen` 的分区清单里同时出现职责名与阶段码——「管理页」「编辑页」「P6：草稿落盘与冲突」「初始化」「按钮」「渲染」「列表维护」「草稿与表单」「模式切换」「管理页动作」「效果动作」「保存 / 撤销」「焦点 / 事件 / 生命周期」「小工具」。分区标题应只允许两种形式：功能域（「草稿与表单」）或生命周期（「初始化」「保存 / 撤销」），阶段码一律清除（与 H2 同源）。

**影响**：分区注释是超大文件的主要导航手段。`FormView`（1378 行、表单引擎核心、被 3 个类引用）检索成本最高，却最缺结构标记；风格变体让「按注释标题跳转」的搜索模式失效。

**建议**：把「超过约 500 行的类必须用 `// ---- 职责名 ----` 分区，分区名描述职责而非阶段码」写进 [客户端层文档](../dev/layers/client.md)；先给 `FormView` 补分区，`UiScrollView` 统一为短横线样式。

**优先级**：中

### M13. `RuleRecipeView` 绕过调色板的裸颜色与密集内联像素常量

**定位**：`ui/screen/RuleRecipeView.java`（369 行）。

**问题**：先明确口径——`client/ui/**` 内 8 位十六进制 ARGB 字面量共 95 处 / 10 个文件，其中 `UiPalette` 39、`UiControlStyle` 16、`UiFlatPalette` 15、`UiSliderStyle` 13，集中在 theme 与 kit 的样式默认值，属正常归属；宿主层只有 `RuleRecipeView`（4 处：`0xFFFFA500` 上色、`0x224F7299` 半透明高亮、`0xFF6688AA` 下划线、`0xFFFFFFFF` 文字）与 `widget/UiModalStack`（1 处）。若把口径放宽到任意 `0x` 颜色字面量，`screen/FluidPreviewIcons.DEFAULT_WATER_TINT = 0x3F76E4` 也是宿主层的裸颜色常量，此时「screen 层唯一」不再成立。同文件还有密集内联像素常量：悬停滚动周期 `150`/`35L`/`1000`/`2000`、名称列偏移 `ICON_SIZE + 3`、缩放上限 `18.0F` 与 `0.65F`、消耗星标绘制的 8 组坐标。

**影响**：`UiPalette`/`UiTheme` 是表单页视觉的主要来源（其余 30+ 文件均从它取色），`RuleRecipeView` 绕过它后，调整主题色不会影响配方预览，导致同一屏内出现两套视觉口径；`FluidPreviewIcons` 的兜底水色同属此类。内联魔数让这些预览的尺寸调整需要逐行试错。

**建议**：`RuleRecipeView` 的 4 个颜色改为 `UiPalette` 中的语义常量（或在 `UiPalette` 新增「消耗星标/高亮」语义项）；`FluidPreviewIcons.DEFAULT_WATER_TINT` 若确为「无可用染色时的兜底值」，应在常量注释中写明该定位或移入 theme；把悬停滚动节奏与星标几何提为具名常量。

**优先级**：中

### M14. 公开面与可见性未收敛

**定位**：`ui/screen/form/RuleTemplates.java`（`public final class`——但除 `screen/form` 内部外无引用，外部引用为 0；该包内另有 `RuleTemplateHooks` 被 `RuleEditorScreen` 使用）。对照同包内 `FormControl`、`NumberControl`、`ItemCountsControl`、`CatalogSelectionControl`、`PickerHost`、`ConditionTreeChrome` 均为包私有。

**问题**：`screen/form` 内既有刻意的包私有类型，也有无外部使用者的 public 类型；可见性口径实际是「随手写的」。

**影响**：包私有类型是包边界的最小化手段，`screen` 与 `screen/form` 已有双向依赖，可见性再放松会让边界更难收敛；同时 public 类会被误当作对 `screen` 的稳定契约。

**建议**：按「有外部使用者才 public」收敛，把 `RuleTemplates` 降为包私有；把这条规则写入 `screen/form` 的 `package-info.java`（见 M8）。

**优先级**：中

### M16. `RuleNameInput` 落包与文档扩展路径不符

**定位**：`ui/screen/RuleNameInput.java`（`final class RuleNameInput implements UiWidget, UiFocusTarget`，125 行，含 `MODAL_WIDTH`、输入框、说明文案与错误预留行的组合）。

**问题**：[客户端层文档](../dev/layers/client.md) §7 规定「新增字段行 / 行尾操作 → `ui/screen/form`」；`RuleNameInput` 本质是「命名空间 + 名称输入 + 错误位」的字段行组合，却落在 `screen`。

**影响**：字段行的落点出现两个先例，后续新增字段行会二选一；`screen` 的职责进一步被稀释（与 H8 同因）。

**建议**：迁入 `ui/screen/form`；当前它只依赖 `edit/TypeLabels`、kit、theme、widget，迁移无额外成本。

**优先级**：中

### M17. `ConditionTreeOverlay` 落包与「画布页」定位不符

**定位**：`ui/screen/form/ConditionTreeOverlay.java`（829 行，`implements UiWidget, UiFocusTarget`，承载功能栏、工具列、状态栏、JSON 只读视图与内联编辑框锚定）。

**问题**：它是**整页画布外壳**，[客户端层文档](../dev/layers/client.md) §4.3 把它与 `UiConditionGraphEditor` 并列为「条件树画布页」，[编辑页契约](../dev/internals/editor-pages-contract.md) 也以「画布页 `ConditionTreeOverlay`」称呼；但按 §7 的扩展表，`screen/form` 定义为字段行层。

**影响**：包名与文档中的层名冲突，读者按包名找「画布页」会先找 `screen`；`screen/form` 因此同时容纳「字段行」与「整页画布」两种粒度。

**建议**：二选一并同步文档——把画布外壳上移到 `ui/screen`（与 `RuleEditorScreen` 同级），或把 §7 扩展表补充为「`screen/form` 承载表单视图、字段行与画布页外壳」。

**优先级**：中

### L8. `prototype` 包文档指向已归档计划

**定位**：`ui/prototype/package-info.java`：「生产入口只能由服务端授权后的指令触发（见 docs/plan/plan-frontend-rewrite-contract.md）」。

**问题**：该计划文件不存在（`docs/plan/` 为空），而句中的规则（不得注册快捷键、不得被绕过独占锁的入口打开）本身是当前有效的硬约束。

**影响**：约束的唯一出处失效；`prototype` 是「不得进入生产」的包，缺少可核对的依据会让人低估这条约束。

**建议**：改为指向 `client/key/EditorShortcut` 的实际行为说明或 [客户端层文档](../dev/layers/client.md) §6 的已知限制，删掉已归档的路径。

**优先级**：低

### L9. 无 review 索引与命名约定

**定位**：`docs/review/` 目录。

**问题**：本轮并列产出多份维度文档（`03-style.md` 等），目录内没有 `README.md`/索引，也没有文件名与编号约定说明。

**影响**：多维度审查结果之间无法交叉导航；下一轮审查的编号规则需要重新推断。

**建议**：补一份 `docs/review/README.md`，列出各文件对应维度、审查快照时间与基线文档；本文件不在自身写作用域内，故未创建。

**优先级**：低

---

## 四、样式组织方式

### H7. 主题双轨、静态调用、同名方法语义不同

**定位**：

- 轨 A：`theme/UiPalette`（39 个公共常量）+ `theme/UiTheme`（`drawWindow`/`drawPanel`/`drawCard`/`drawChip`/`drawSegment`/`drawInset`/`drawHeader`/`drawDivider`/`drawBevel`/`drawFocusOutline`/`drawSelection`/`drawSelectMarker`/`drawScrollTrack` 及 `*Style` 工厂）。引用规模（口径：`client/ui/**` 内按类名逐行统计，含注释行、不计 import 行；括号内为把 import 行也计入的出现次数）：`UiTheme` 146 行（188 次）、`UiPalette` 277 行（398 次），涉及 34 个文件，其中 widget 层 18 个文件。
- 轨 B：`theme/UiFlatPalette`（53 个常量，其中 38 处引用 `UiPalette` 取值）+ `theme/UiFlatSkin`（`drawSurface`/`drawOutline`/`drawChrome`/`drawStatusBar`/`drawHelp`/`drawFocusOutline`）。使用者（同一口径，若把 import 行计入则括号内值）：`screen/form/ConditionTreeOverlay`（`UiFlatPalette` 7 / 8 行、`UiFlatSkin` 19 / 20 行）、`widget/UiConditionGraphEditor`（`UiFlatPalette` 35 / 36 行、`UiTheme` 1 / 2 行）、`screen/form/ConditionTreeChrome`（`UiFlatSkin` 1 / 2 行）。
- 同名方法：`UiTheme.drawFocusOutline` 画「四角 + 中线」虚线感轮廓并取自 `UiPalette.FOCUS_OUTLINE`；`UiFlatSkin.drawFocusOutline` 委托 `drawOutline` 画 1 像素实框。签名相同、观感不同。
- 同族控件不同轨：`widget/UiConditionTreeSummary`（`UiTheme` 19 行 / `UiPalette` 13 行）与 `widget/UiConditionGraphEditor`（`UiFlatPalette` 35 行 / `UiTheme` 1 行）是同一「条件树编辑」家族的两个控件，却分属两套主题。

**问题**：[客户端层文档](../dev/layers/client.md) §4.2 说「`ui/theme` 把调色板映射成 kit 所需的样式对象后注入」；实际上 kit 之外的主题是**静态类**，widget 层 18 个文件直接写 `UiTheme.xxx` / `UiPalette.xxx`。同时 [ADR-0028](../adr/0028-canvas-page-and-flat-skin.md) 与 [卡片布局](../dev/internals/editor-card-layout.md) 声明「扁平面只在调用方显式注入时才生效」，但 `UiFlatPalette`/`UiFlatSkin` 也是静态调用，`UiConditionGraphEditor` 是直接使用而非被注入。

**影响**：换肤或新增第三套皮肤需要改 widget 层代码，而不是换一个注入对象——这正是「可扩展性」最直接的缺口。同一包内两个条件树控件使用不同主题来源，新写 widget 的人无法判断该取哪套；同名 `drawFocusOutline` 让人以为两者可互换。

**建议**：分两步收敛——（1）先文档化「kit 侧样式对象注入；宿主侧主题为静态常量表」这一事实，把 §4.2 的「注入」表述收窄，消除文档与实现的落差；（2）为 widget 引入样式提供者接口（例如 `ControlSkin` 记录，含卡片/边框/焦点/文本色的取用方法），使 `UiTheme` 与 `UiFlatSkin` 成为它的两个实现，widget 只依赖接口。同时把两个 `drawFocusOutline` 分名（`drawDashedFocus` / `drawSolidFocus`）。

**优先级**：高

### M15b. 两个条件树编辑器的样式、文案、结构密度差异

**定位**：`widget/UiConditionTreeSummary.java`（1573 行，`UiPalette`/`UiTheme`，39 处内联键，7 个分区）、`widget/UiConditionGraphEditor.java`（2637 行，`UiFlatPalette`，46 处内联键，23 个分区）。

**问题**：两个同类控件在主题来源、文案供给、分区密度三方面都不一致。

**影响**：二者是「同一功能的两种呈现」，用户与开发者都会假设它们共享契约；差异越多，行为分叉的风险越高。

**建议**：抽出共享的宿主文本记录（见 H4）与共享的主题取用入口（见 H7），使差异只保留在「渲染与输入」一层，与 [客户端层文档](../dev/layers/client.md) §4.3 对二者的分工描述一致。

**优先级**：中

---

## 五、i18n 与团队约定一致性

### H11. `UiFlatSkin` 越界依赖 `client.edit`

**定位**：`ui/theme/UiFlatSkin.java` 的 `import com.meteorite.itemdespawntowhat.client.edit.TypeLabels`，唯一用途是 `TypeLabels.OWN_NAMESPACE` 拼贴图路径。

**问题**：[客户端层文档](../dev/layers/client.md) §4.2 规定 `ui/theme`「只依赖 Minecraft 客户端通用类型与 kit，不依赖内核与平台 API」；`client.edit` 不在允许范围内。theme 是 `ui` 下的视觉层，却反向依赖了其上层的 `edit`。

**影响**：theme 包无法在不带 `edit` 的情况下复用，这是「样式层可独立替换」这一目标的具体障碍。同一包内 `UiPalette`、`UiFlatPalette`、`UiTheme` 均为零项目依赖，`UiFlatSkin` 是唯一例外。

**建议**：路径命名空间改为类内常量或由调用方注入；删掉对 `client.edit` 的 import。

**优先级**：高

### H12. UI 层直接使用传输载荷类型与 Gson，且缺少成文约定

**定位**：`client/ui/**` 中 `import com.meteorite.itemdespawntowhat.core.network.protocol.*` 的文件 12 个，涉及 `RuleCatalogType`、`RuleCatalog`、`RuleCatalogEntry`、`RuleSnapshot`、`RuleSnapshotEntry`、`RuleIssue`、`RuleSaveStatus`：`screen/CatalogEntryPolicy`、`screen/RuleEditorEditPages`、`screen/RuleEditorModel`、`screen/RuleEditorP4Panels`、`screen/RuleEditorScreen`、`screen/RulePreviewIcons`、`screen/RuleRecipeView`、`screen/SourceBlacklistPicker`、`screen/TagPreviewIcons`、`screen/form/CatalogSelectionControl`、`screen/form/CatalogSuggestions`、`screen/form/ItemCountsControl`；另有 `screen/RuleEditorOpener`、`screen/form/CatalogSuggestions` 引用 `client.net.*`。Gson：`client/ui` 内 21 个文件出现 `com.google.gson`，其中 20 个 `import`（`screen` 10、`screen/form` 10）。

**问题**：[客户端层文档](../dev/layers/client.md) §1 第 2 条写「界面层不得直接接触传输载荷类型」，§2 写「视图不暴露任何传输载荷类型」；`edit/EditorWorkspaceView` 与 `edit/LiveEditorWorkspace` 的 javadoc 也明确「把传输 payload 收敛在本类内，界面层因此不接触任何 payload 类型」。但 `net/RuleEditClientWorkspace` 的公开方法直接返回 `RuleSnapshot`、`RuleCatalog`、`RuleSaveResultPayload`，`edit/LiveEditorWorkspace` 也就继续暴露这些类型，UI 侧只能跟着 import。Gson 只在 kit 被脚本禁止，宿主层 20 个文件使用它没有任何文字约定。

**影响**：传输协议字段改名会波及 12 个 UI 文件；`EditorWorkspaceView` 这层「只读视图」目前没有起到隔离作用，形成「文档说有隔离、实现上没有」的落差。Gson 缺少约定意味着界面对 JSON 的直接读写边界可变，未来服务端字段调整的排查面会扩大。

**建议**：二选一并写清——（1）补齐隔离：在 `client/edit` 定义不含协议类型的视图记录（把 `RuleSnapshot`/`RuleCatalog`/`RuleIssue` 转换为 `edit` 自己的类型），UI 只依赖 `edit`；（2）认可现状：把 §1/§2 的表述改为「UI 层经 `client/edit` 的工作区视图使用协议数据，视图当前原样透出只读快照类型」，并把 `EditorWorkspaceView` 的 javadoc 同步。Gson 方面建议明确「宿主 UI 层可使用 Gson 读写草稿 JSON，kit 内禁止」并写入边界说明。

**优先级**：高

### M18. `screen/form` 的 FQN 与 import 混用（`FormView` 最集中）

**定位**：代码行中的全限定名（`com.meteorite.…`，不含 `import`/javadoc）共 46 处 / 9 文件：`screen/form/FormView` 23 处、`screen/RuleEditorEditPages` 12 处、`screen/RuleEditorScreen` 3 处、`screen/form/CatalogSuggestions` 2 处、`screen/TagCarouselView` 2 处、`prototype/UiPrototypeScreen` 1 处、`screen/form/FormControl` 1 处、`screen/form/NaturalSummary` 1 处、`screen/form/NumberControl` 1 处。

**问题**：`FormView` 自身已 `import` 了 `client.ui.widget.UiTextInput`，却在类声明处写 `implements com.meteorite.…client.ui.widget.UiWidget`，并在字段、参数、循环变量处反复写全限定名（`…client.ui.widget.UiButton`、`…client.ui.widget.UiWidget`、`…client.ui.screen.RuleEditorP4Panels`、`…core.network.protocol.RuleCatalogType`、`com.google.gson.JsonPrimitive`）。同包其他类均以短名书写。

**影响**：同一文件内两种引用风格让阅读与检索都要处理两套名称；FQN 使行宽膨胀，并遮蔽了本应由 import 清单表达的依赖关系（`FormView` 的 import 清单看不出它依赖 `UiWidget`、`UiButton`、`RuleCatalogType`、`JsonPrimitive`）。

**建议**：统一为 import + 短名，把 `FormView` 的 23 处 FQN 全部改为 import。若某处确为规避命名冲突（如两个同名内部类），应在该处加一行说明，而不是散落 FQN。

**优先级**：中

### M19. 记录/构造参数命名粒度不一致

**定位**：`kit/UiIconSheet(ResourceLocation texture, int cellWidth, int cellHeight, int columns, int rows)`、`kit/UiNumberPolicy(double min, double max, double coarseStep, double fineStep, …)`、`screen/form/FormIssue(String path, Component label, Component message, boolean blocking)` 用短名；而 `kit/UiControlStyle(int background, int hoverBackground, int pressedBackground, int selectedBackground, int disabledBackground, …)`、`kit/UiSliderStyle(…)` 用完整语义名。

**问题**：同层内既有 `min/max` 也有 `background/hoverBackground`；参数名粒度未统一。

**影响**：影响有限（IDE 参数提示可辨），但文档中的签名引用与代码审查时的可读性会不一致。

**建议**：作为低成本风格条款写入约定：记录/构造参数使用完整语义名（`background`、`hoverBackground`），域值参数可用 `min`/`max` 短名，同一记录内保持同一粒度。

**优先级**：中

---

## 六、已成体系、确实一致的风格（事实依据，不建议改动）

下列各项经全量扫描确认一致，可作为上述整改的收敛基线：

1. **顶级类型 javadoc 覆盖率 100%**：`client` 173 个文件、167 个顶级类型全部带 javadoc，无一遗漏。
2. **注释语言 100% 简体中文**：注释行中含中文 3456 行；按「≥4 个英文词且不含中文」筛选得 40 行，逐条核对后全部是 `{@link}`/`{@code}` 的全限定名或 javadoc 内的代码样例，无一行英文散文注释。
3. **常量命名零例外**：`client` 内 532 个 `static final` 常量全部符合 `UPPER_SNAKE`。
4. **布尔字段以状态形容词为主**：152/156 无前缀（`enabled`、`visible`、`focused`、`selected`、`pressed`、`hovered`、`dragging` 等），布尔方法统一用 `is*`（114 个）、`can*`（29 个）、`has*`（6 个）。
5. **一个文件一个顶级类型**：173 个文件零例「单文件多顶级类型」。
6. **i18n 双语言完全同步**：`en_us.json` 与 `zh_cn.json` 各 995 键，键集完全一致，无缺失、无空值；两份文件的键顺序不同（`en_us` 未排序、`zh_cn` 已排序），键集本身无差异。18 对同值项集中在配方符号（`recipe.arrow`、`recipe.plus`）、命令调试输出与黑名单/数量行等中性文案。
7. **UI 文案无硬编码中文**：`client` 内 29 处含中文的字符串字面量全部是日志、异常消息或断言文案，不在 `Component`/界面渲染路径上；`Component.literal` 仅 11 处，均为符号或前缀装饰（`"×"`、`"◀"`、`"# "`、`"--:--"`、`"?"`、`"-"`）。
8. **kit 边界零违规**：`tools/check-ui-kit-boundaries.ps1` 报告 60 文件 / 193 import / 0 违规；kit 对本项目其它包的 `import` 实测为 0；`kit/package-info.java` 的依赖方向与白名单描述与脚本规则逐条对应。
9. **记录组件访问器风格统一**：全 `client` 零 `get*` 方法，记录组件用同名访问器（`UiRect.x()`、`UiPointer.x()`、`UiRangeValue.low()`）。
10. **预览图标命名家族一致**：`BlockPreviewIcons`、`EntityPreviewIcons`、`FluidPreviewIcons`、`RulePreviewIcons`、`TagPreviewIcons` 五个类同缀、同包、同职责。
11. **`screen/form` 内部可见性有明确用法**：`FormControl` 及其三个字段行实现、`PickerHost`、`ConditionTreeChrome` 均为包私有，形成包内实现细节层（问题在 public 侧未同步收敛，见 M14）。
12. **widget 文案注入已成体系**：`UiCatalogGrid.Texts` 记录、`setEmptyMessage`、`setPageLabel`、`UiCyclicTimeBar.Preset` 等注入入口覆盖 20/23 个 widget 类（例外见 H4）。
13. **错误/提示键在 `screen/form` 内一致复用**：`FormControl.ISSUE_PREFIX`、`NOTICE_ISSUE` 被 `NumberControl`、`CatalogSelectionControl` 等一致复用，未出现按字段各写一套 issue 键的情况。

---

## 七、可执行的收敛规则（建议写入 [客户端层文档](../dev/layers/client.md)）

| 主题 | 规则 | 覆盖问题 |
|---|---|---|
| 命名-类前缀 | `Ui*` 表示可复用 UI 单元（kit/theme/widget/prototype 全覆盖，`Text*`/`*Painter` 例外需登记）；业务专用类型用业务前缀（`Rule*`、`Tag*`、`Catalog*`） | M4、H4 |
| 命名-后缀 | `*Screen` 整屏；`*Pages` 页面装配；`*Field` 字段行（替代 `*Control`）；`*Host` 宿主必须实现的接口；`*Hooks` 宿主可选注册的回调；`*Codec`/`*Provider`/`*Dispatcher` 纯工具 | M1、M7 |
| 命名-值类型 | 命中结果用 `UiHit`；树节点控件用 `UiTreeItem`；避免用 `Window` 表示数值窗口 | M2、M3、M5 |
| 命名-布尔 | 字段用状态形容词，方法用 `is*`/`has*`/`can*` | M11 |
| i18n-常量 | 根前缀唯一来源 `TypeLabels.UI_PREFIX`；常量名 `<作用域>_KEY_PREFIX` / `<用途>_KEY`；禁止复制前缀字面量 | H3、L1 |
| i18n-注入 | 宿主通用控件由宿主注入文案（`Texts` 记录或 setter）；项目专用控件内联键须在文档登记为已知例外 | H4、M15b |
| i18n-同步 | 新增或修改键必须同步 `en_us.json` 与 `zh_cn.json`（当前 995/995 已同步，作为持续门槛） | 既有约定 |
| 常量 | 模组命名空间唯一来源 `Constants.MOD_ID` | H5 |
| 注释-内容 | 只写当前状态；禁止阶段码（P`n`）、任务码（task-`n`）与「原实现/旧版/不再/需要改为」式沿革 | H2、H9、M20 |
| 注释-指针 | 文档指针必须可解析；禁止指向 `docs/plan/` 与已归档文件；建议加脚本校验 | H1、L1、L8 |
| 注释-语言 | 注释与消息文案统一简体中文；`Utility class` 例外需登记 | H10 |
| 注释-结构 | 超过约 500 行的类用 `// ---- 职责名 ----` 分区（不用长横线变体） | M10 |
| 样式 | 声明「kit 样式对象注入、宿主主题为静态常量表」；新 widget 统一从主题取色，禁止裸 ARGB 与裸像素魔数 | H7、M13 |
| 包结构 | `ui/screen` 只放屏幕与页面装配；预览图标、只读值映射各自子包；`ui/screen/form` 不得反向 `import` `ui/screen` | H8、M16、M17 |
| 包文档 | 每个 `ui` 子包必须有 `package-info.java`，写明职责、边界与扩展落点 | M8、M9 |
| 可见性 | 有外部使用者才 `public` | M14 |
| 引用风格 | 一律 `import` + 短名；确需 FQN 时当场注明原因 | M18 |

## 八、存疑与未覆盖

- **存疑**：`UiConditionTreeSummary` 与 `UiConditionGraphEditor` 是否应合并为一个控件的两种渲染模式，需要功能与交互层面的判断；本维度只指出二者在主题、文案、分区三方面的不一致，不下合并结论。
- **存疑**：`RuleRecipeView` 的裸颜色是否为「配方预览」有意独立于表单主题，需要产品/视觉确认；本报告按「同一屏内不应有两套取色口径」处理。
- **未覆盖**：未执行 Gradle 构建与 IDE 语言服务检查（本轮只动 Markdown）；未核对 `common` 之外的平台代码（`fabric/`、`neoforge/`）中是否存在 UI 风格分歧；未审计 i18n 文案本身的翻译质量与占位符一致性（仅核对键集同步）；未量测渲染性能与内存（属其他维度）。
- **依赖既有结论**：kit 边界脚本的 0 违规结论、`client` 文件与行数基线直接引用既有核验结果，本轮未重复执行脚本。
