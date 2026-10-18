# 页面层：规则编辑器屏幕（client/ui/screen）

> 层级：页面层；模块路径：`client/ui/screen`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/screen`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| `client/ui/screen` | `common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/screen` | §2 |

## 2. 模块：`client/ui/screen`

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.ui.screen`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/screen`
- 覆盖：31 个 `.java` 文件（无 `package-info.java`）

### 2.2 职责

- 承载规则编辑器屏幕 `RuleEditorScreen`，并按内建模式切换列表页、编辑页与调试页。
- 组织编辑页的四个页签：基本页、输入页、触发页、结果页。
- 把服务端快照映射为按规则 id 的草稿会话，并维护落盘、恢复、冲突与脏规则集合。
- 提供列表页的搜索、筛选、分类规则树与配方预览。
- 提供编辑页的页签容器、底排按钮、保存流程与问题定位。
- 提供保存遮罩、本地校验、目录选择页装配与叶面板装配。
- 产出列表与变更清单共用的只读展示快照。
- 提供规则编辑器屏幕的打开与关闭适配入口。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `RuleEditorScreen` | class | `RuleEditorScreen#setNotice(Component, NoticeLevel)`、`RuleEditorScreen#noticeColor(NoticeLevel)`、`RuleEditorScreen#refreshFromWorkspace()`、`RuleEditorScreen#onClose()`、`RuleEditorScreen#isPauseScreen()`；其余为 `Screen` 生命周期与输入覆盖方法 | 规则编辑器屏幕，`extends Screen`；模式枚举 `Mode` 为 private |
| `RuleEditorScreen.NoticeLevel` | enum | `RuleEditorScreen.NoticeLevel#INFO`、`RuleEditorScreen.NoticeLevel#WARNING`、`RuleEditorScreen.NoticeLevel#DANGER` | 底部状态提示级别 |
| `RuleEditorModel` | class | `RuleEditorModel#workspace()`、`RuleEditorModel#refresh()`、`RuleEditorModel#isLoaded()`、`RuleEditorModel#ruleIds()`、`RuleEditorModel#entry(String)`、`RuleEditorModel#session(String)`、`RuleEditorModel#openSession(String)`、`RuleEditorModel#createSession(String, RuleValue)`、`RuleEditorModel#dropSession(String)`、`RuleEditorModel#dirtyRuleIds()`、`RuleEditorModel#buildChangeSetJson()`、`RuleEditorModel#tickPersistence()`、`RuleEditorModel#persistNow()`、`RuleEditorModel#conflicts()`、`RuleEditorModel#resolveConflict(String, boolean)`、`RuleEditorModel#discardAllDrafts()`、`RuleEditorModel#entryBody(String)` 等 | 按规则 id 的草稿会话模型 |
| `RuleEditorEditPages` | class | `RuleEditorEditPages#page()`、`RuleEditorEditPages#setPage(Page)`、`RuleEditorEditPages#rebuild()`、`RuleEditorEditPages#layout(UiRect)`、`RuleEditorEditPages#render(GuiGraphics, Font, int, int)`、`RuleEditorEditPages#applyToDraft()`、`RuleEditorEditPages#issues()`、`RuleEditorEditPages#reveal(String)`、`RuleEditorEditPages#blockNavigation()`、`RuleEditorEditPages#setEnabled(boolean)`、`RuleEditorEditPages#focusTargets()`、`RuleEditorEditPages#endInteractions()`、`RuleEditorEditPages#unmount()` 等，输入覆盖方法仅委托 | 四个页签的组合视图 |
| `RuleEditorEditPages.Page` | enum | `RuleEditorEditPages.Page#INFO`、`RuleEditorEditPages.Page#INPUT`、`RuleEditorEditPages.Page#TRIGGER`、`RuleEditorEditPages.Page#RESULTS`；`RuleEditorEditPages.Page#value()`、`RuleEditorEditPages.Page#of(String)` | 页签标识，取值依次为 `info`、`input`、`trigger`、`results` |
| `RuleEditorEditPages.Host` | interface | `RuleEditorEditPages.Host#font()`、`#session()`、`#focus()`、`#modals()`、`#onDraftChanged()`、`#onControlsChanged()`、`#notice(Component, int)`、`#suggestions()`、`#conditionEditingSupport()`、`#workspace()`、`#rejectWhenFrozen()`、`#screenWidth()`、`#screenHeight()` | 页签容器向屏幕获取能力的宿主接口 |
| `RuleEditorPanelFactory` | class | `RuleEditorPanelFactory#clockText(double)`、`RuleEditorPanelFactory#dayTicks()`、`RuleEditorPanelFactory#cyclicTexts()`、`RuleEditorPanelFactory#rangeTexts()`、`RuleEditorPanelFactory#dailyBar(Font, UiCyclicTimeBar.Listener)`、`RuleEditorPanelFactory#catalogTexts()`、`RuleEditorPanelFactory#catalogLabel(String)`、`RuleEditorPanelFactory#catalogModal(...)` 三个重载、`RuleEditorPanelFactory#pickerWidth(int)`、`RuleEditorPanelFactory#pickerBodyHeight(int)`、`RuleEditorPanelFactory#climateAllUnbounded(RuleValue)`、`RuleEditorPanelFactory#leafPanel(Font, FormView, EditSession, List<EditorField>, String, Consumer<Runnable>, Runnable)` | 目录选择页、时间/高度区间条与叶面板的装配 |
| `RuleEditorOpener` | class | `RuleEditorOpener#bootstrap()`、`RuleEditorOpener#shutdown()`、`RuleEditorOpener#lastRequest()`、`RuleEditorOpener#open(EditorOpenRequest)`、`RuleEditorOpener#close()` | 编辑器界面实现，`implements EditorScreenOpener` |
| `CatalogEntryPolicy` | class | `CatalogEntryPolicy#fixed(RuleCatalogType)`、`CatalogEntryPolicy#scoped(RuleCatalogType)`、`CatalogEntryPolicy#hidden(String)`、`CatalogEntryPolicy#scoped()`、`CatalogEntryPolicy#scope()`、`CatalogEntryPolicy#setScope(UiCatalogGrid.Scope)` | 目录条目默认排除策略，`implements UiCatalogGrid.EntryPolicy` |
| `ResultStructure` | class | `ResultStructure#kind(RuleValue)`、`ResultStructure#hasFlat(RuleValue)`、`ResultStructure#hasOutcomes(RuleValue)`、`ResultStructure#candidateCount(RuleValue)`、`ResultStructure#candidateAt(RuleValue, int)`、`ResultStructure#listPath(RuleValue, int)`、`ResultStructure#effectsAt(RuleValue, int)`、`ResultStructure#effectCount(RuleValue, int)`、`ResultStructure#totalEffectCount(RuleValue)` | 顶层 `effects` 与 `outcomes` 的只读结构判定 |
| `ResultStructure.Kind` | enum | `ResultStructure.Kind#EMPTY`、`ResultStructure.Kind#FLAT`、`ResultStructure.Kind#OUTCOMES`、`ResultStructure.Kind#MIXED` | 结果结构类型 |
| `RuleTriggers` | class | `RuleTriggers#NATURAL`、`RuleTriggers#FIRE`、`RuleTriggers#LAVA`、`RuleTriggers#CACTUS`、`RuleTriggers#known()`、`RuleTriggers#isExplicit(RuleValue)`、`RuleTriggers#isExplicitlyEmpty(RuleValue)`、`RuleTriggers#effective(RuleValue)`、`RuleTriggers#allowsDelay(RuleValue)`、`RuleTriggers#toArray(List<String>)` | `triggers` 字段的只读解析与写入 |
| `RulePreviewIcons` | class | `RulePreviewIcons#clear()`、`RulePreviewIcons#action(RuleValue)`、`RulePreviewIcons#rule(RuleValue)`、`RulePreviewIcons#unavailable(RuleValue)` | 列表页与动作导航共用的产出图标缓存 |
| `RuleRecipeView` | class | `RuleRecipeView#RuleRecipeView(RuleValue)`、`RuleRecipeView#summary()`、`RuleRecipeView#tooltip()`、`RuleRecipeView#render(GuiGraphics, Font, UiRect, int, boolean)`、`RuleRecipeView#tagAt(Font, UiRect, double, double)` | 配方行的只读展示快照；包级静态方法 `RuleRecipeView#renderRow`、`RuleRecipeView#recipeBounds` 供列表页与变更清单复用 |
| `TagCarouselView` | class | `TagCarouselView#TagCarouselView(Font, TagPreviewIcons.Tag)`；`UiWidget` 的 `bounds`、`setBounds`、`render`、`mouseClicked`、`mouseDragged`、`mouseReleased`、`mouseScrolled`、`keyPressed`、`charTyped` 覆盖方法 | 标签成员的轮播与列表容器 |

其余 21 个类型为包级可见：`ActionDescription`、`RuleListDisplayCache`、`RuleListDisplaySnapshot`、`RuleChangesView`、`RuleEditorInfoPage`、`RuleEditorInputPage`、`RuleEditorPageInput`、`RuleEditorPageSupport`、`RuleEditorResultsPage`、`RuleEditorScreenButtonBar`、`RuleEditorScreenDebugPage`、`RuleEditorScreenEditPage`、`RuleEditorScreenListActions`、`RuleEditorScreenListPage`、`RuleEditorTriggerPage`、`RuleEditorValidation`、`RuleNameInput`、`RuleSaveOverlay`、`RuleTriggerView`、`SourceBlacklistPicker`、`SourceCatalystSelectionPolicy`。

包级嵌套类型另有 `RuleEditorEditPages.CardHeader`（record）与 `RuleEditorEditPages.PageCard`（static final class）；页面内部还有 `RuleEditorInputPage.PresenceRow`、`RuleEditorPageSupport.PresenceLeaf`、`RuleEditorResultsPage.PlanPager`、`RuleEditorScreenListPage.MenuAction`、`RuleChangesView.Change`、`SourceBlacklistPicker.Group`、`SourceBlacklistPicker.Page`、`SourceBlacklistPicker.Pages`。

### 2.4 依赖关系

- 依赖本模组：`client/edit`（27 个文件 import，共 126 处）
- 依赖本模组：`client/edit/draft`（2 个文件 import，共 5 处，含 `DraftConflict`、`DraftJournal`、`DraftStore`、`PersistedDraft`）
- 依赖本模组：`client/net`（3 个文件 import，共 5 处，含 `EditorOpenRequest`、`EditorScreenHooks`、`EditorScreenOpener`、`RuleEditClientState`）
- 依赖本模组：`client/ui/kit`（20 个文件 import，共 61 处）
- 依赖本模组：`client/ui/screen/form`（11 个文件 import，共 24 处）
- 依赖本模组：`client/ui/shared`（11 个文件 import，共 28 处）
- 依赖本模组：`client/ui/theme`（17 个文件 import，共 30 处）
- 依赖本模组：`client/ui/widget`（20 个文件 import，共 75 处）
- 依赖外部：`com.meteorite.itemdespawntowhat.core.*`（`core.api`、`core.model`、`core.network.protocol`、`core.service`、`core.type.condition`、`core.debug`）
- 依赖外部：`net.minecraft.*`（`Screen`、`Font`、`GuiGraphics`、`Component`、`Minecraft`、`ItemStack`、`Items`、`ChatFormatting` 等）
- 依赖外部：`com.mojang.blaze3d.systems.RenderSystem`、`org.jetbrains.annotations`、`org.lwjgl.glfw.GLFW`、`java.util`、`java.util.function`
- 被依赖：`client`（1 个文件 import，共 1 处，`RuleEditorOpener`）
- 被依赖：`client/register`（1 个文件 import，共 1 处，`RuleEditorOpener`）
- 被依赖：`dev/guiscreenshot`（1 个文件 import，共 1 处，`RuleEditorScreen`）

### 2.5 关键实现位置

- `RuleEditorScreen#render`：按模式绘制窗口、页头、页签、内容视口、底部状态提示与底排；保存中改绘保存遮罩并跳过页面输入。
- `RuleEditorScreen#drawHeaderText`：绘制标题与右端状态条；调试模式标题取 `debug.title`，编辑模式取 `header.editing`，状态取 `state.freezing` / `state.applied` / `state.unapplied` / `state.active`。
- `RuleEditorScreen#keyPressed`：在 debug 模式开启、停在列表页且无弹层与类型选择器时响应 Ctrl+F8 进入调试页；F1–F12 一律返回 false 放行；Esc 先交给聚焦控件再逐级回退；其余按模式派发。
- `RuleEditorScreen#openEditor` / `RuleEditorScreen#backToList` / `RuleEditorScreen#enterDebug`：三个模式的切换入口。
- `RuleEditorScreen#closeByUser` / `RuleEditorScreen#onClose`：关屏前提交表单、卸载编辑页并写盘未应用草稿；有脏规则时先弹确认框并丢弃落盘草稿。
- `RuleEditorScreen#setNotice` / `RuleEditorScreen#noticeColor` / `RuleEditorScreen#clearStickyNotice`：提示级别与色值的取色集中点；INFO 使用 `NOTICE_INFO_TTL_MS` 计时。
- `RuleEditorScreen` 构造：为列表页、编辑页、调试页各创建一个 `Host` 匿名实现注入屏幕能力；`RuleEditorScreen#tick` 每帧调用 `RuleEditorModel#refresh`。
- `RuleEditorEditPages#rebuild`：`switch (page)` 分派到 `RuleEditorInfoPage#buildInfo`、`RuleEditorInputPage#buildInput`、`RuleEditorTriggerPage#buildTrigger`、`RuleEditorResultsPage#buildResults`。
- `RuleEditorEditPages#setPage`：切页前记录滚动偏移并结束进行中的交互，切页后清除提示并重建页面。
- `RuleEditorEditPages#apply`：页面表单动作的 `EditSession#apply` 封装，提交后用 `DraftListInputs#capture` 与 `DraftListInputs#remap` 处理列表路径重排；`RuleEditorModel`、`RuleEditorScreenListActions`、`RuleEditorTriggerPage` 另有直接调用 `EditSession#apply` 的入口。
- `RuleEditorEditPages#applyStructure`：结构类编辑（增删效果、改产出子类、调整顺序、删除方案）的同一入口，在结构写入后按 `RuleCategoryAssignment#follow` 同步分类；分类写入与结构改动同处一条撤销记录，撤销该次编辑时分类一并回退。
- `RuleEditorEditPages#reveal`：按草稿路径前缀切到对应页签、候选与效果，并返回待聚焦控件。
- `RuleEditorPageInput`：页面级输入路由，记录按下归属并只把释放与拖动派发给同一控件；结果页动作列表的拖动排序结果交给 `RuleEditorResultsPage#moveEffect`。
- `RuleEditorScreenEditPage#save`：提交可见表单后执行 `RuleEditorValidation#collectIssues`，有阻塞问题则定位，否则把变更集交给工作区保存，回执由 `RuleEditorScreenEditPage#pollWorkspace` 轮询。
- `RuleEditorScreenEditPage#pollWorkspace`：轮询保存回执与草稿冲突。
- `RuleEditorInfoPage#categoryText`：分类行只读展示当前分类与来源（显式 / 自动），分类名按 `RuleCategorySkeleton#knownLabelKey` 取本地化文本，未知分类按原文显示。
- `RuleEditorInfoPage#sourceSummary`：来源摘要只读取已取回的来源视图，按就绪 / 在途 / 失败三态呈现；请求失败只影响来源对照，不阻断本页表单编辑。
- `RuleEditorScreenListPage#refreshList`：先按规则 id 取列表展示快照（`RuleListDisplayCache`），再按快照里的标题、状态与搜索文本做过滤与分组，重建分类规则树；已知分类的层级与顺序来自 `RuleCategorySkeleton`，只消费快照下发的分类，未知分类按原文成组并挂在扩展分组下，已知与未知的子分类统一显示计数。配方预览与条件提示不在刷新时构建：行首次可见时由渲染回调向快照索取预览，悬停时生成条件提示，两者都缓存在快照上。首载分段观测开启时输出 `CLIENT_LIST_REFRESH`（过滤、取体、建树、setRoots、`body_fetches`、`reused` 与总耗时，`first=true` 表示首份快照后的首次刷新）。
- `RuleListDisplayCache#snapshot`：按「快照版本 + 草稿修订 + 新建 / 恢复标记」判定展示快照是否失效；命中时标题、状态、分类、预览与条件提示直接复用，因此搜索逐键刷新只重算过滤与分组。
- `RuleListDisplaySnapshot#titleOf`：列表行、分类树节点与页头标题共用同一份标题文本。
- `RuleEditorScreen#tick`：每帧消费一次新快照；首载分段观测开启时消费成功会输出 `CLIENT_APPLY`（快照进入模型的 `apply_ms`）。
- `RuleEditorScreenListActions` 的条目动作：`RuleEditorScreenListActions#promptNewRule`、`RuleEditorScreenListActions#promptDuplicate`、`RuleEditorScreenListActions#toggleSelectedEnabled`、`RuleEditorScreenListActions#maskSelected`、`RuleEditorScreenListActions#restoreSelected`、`RuleEditorScreenListActions#deleteSelected`。
- `RuleEditorScreenListActions#promptNewRule`：默认名由 `#nextDefaultRuleName` 生成，`new_rule` 已被快照条目或本次新建占用时依次尝试 `new_rule_1`、`new_rule_2`……取最小空闲序号。
- `RuleNameInput#parseId`：输入含一个冒号且两端非空时按 `<namespace>:<path>` 原样解析，否则按 `<path>` 补本模组命名空间；含多个冒号或空段返回 `null`。新建与复制共用该解析。
- `RuleEditorModel#refresh`：消费新快照，绑定磁盘草稿与内存会话。
- `RuleEditorModel#buildChangeSetJson` / `RuleEditorModel#acceptSaved`：脏规则提交与保存后确认。
- `RuleEditorModel#tickPersistence` / `RuleEditorModel#persistNow`：草稿的节流落盘与立即落盘；磁盘草稿的读取在构造与 `RuleEditorModel#refresh` 中完成。
- `RuleEditorValidation#collectIssues`：汇总当前编辑会话、其余脏规则与页面级问题。
- `RuleEditorPanelFactory#catalogModal` / `RuleEditorPanelFactory#leafPanel`：目录选择页与叶面板的装配入口。
- `RuleEditorScreenDebugPage#fillTestRules` / `RuleEditorScreenDebugPage#deleteTestRules`：测试规则草稿的批量填充与批量删除。
- `RuleSaveOverlay#render` / `RuleSaveOverlay#requestCancel`：保存遮罩绘制与取消信号。
- `CatalogEntryPolicy#hidden`：物品与流体的默认排除判定。
- `RuleTriggers#effective`：字段省略时返回自然触发，显式数组保留未知取值。
- `RulePreviewIcons#action` / `RulePreviewIcons#rule`：产出图标的缓存查询入口。
- `RuleRecipeView#renderRow`：列表页与变更清单共用的两行条目绘制。
- `RuleTriggerView#render`：配方箭头的触发信息绘制。
- `RuleEditorPageSupport` 的静态方法：存在条件叶扫描（`RuleEditorPageSupport#scanPresenceLeaves`）、叶子增删（`RuleEditorPageSupport#addRuleLevelPresence`、`RuleEditorPageSupport#removeRuleLevelPresence`）、引用重指向（`RuleEditorPageSupport#retargetCatalystItems`、`RuleEditorPageSupport#retargetFluidActions`）与页面级问题追加（`RuleEditorPageSupport#addPageIssues`）。
- `RuleEditorInputPage#buildPresenceSection`：只列规则级存在叶；数据来自 `RuleEditorPageSupport#ruleLevelPresence`，`RuleEditorPageSupport.PresenceLeaf#ruleLevel()` 以 `effectPath == null` 判定。行为局部存在叶不进入输入页。
- `RuleEditorPageSupport.PresenceLeaf#conditionPath` 与 `#fieldPath`：前者是条件叶的草稿路径，后者由前者与 `#fieldName()` 拼出类型字段路径；输入页的存在条件表单以该路径作为表单 `basePath`。
- `RuleEditorInputPage#buildPresenceSection` 的开关：勾选时调用 `RuleEditorPageSupport#addRuleLevelPresence` 新增规则级叶，取消时调用 `RuleEditorPageSupport#removeRuleLevelPresence` 删除该类型的规则级叶。
- `RuleEditorTriggerPage#openConditionTree`：接收 `scope`、`ownerPath` 与可选 `fieldPath` 打开条件树画布页；输入页与触发页之间不保存类型副本，两侧都按草稿路径指向同一份条件 JSON。

### 2.6 代码现状（可核对）

- 该包无 `package-info.java`；33 个类型均有类级 JavaDoc。
- `RuleEditorModel` 与 `RuleEditorEditPages` 名称分别含 Model 与 Pages，职责不同：前者是数据模型（`LinkedHashMap<String, EditSession>`、`DraftJournal`、`DraftStore`），后者是四个页签的界面组合视图（`Page`、`PageCard`、`UiScrollView`、`FormView` 列表）。
- 输入路由有两套：`RuleEditorPageInput` 负责页面级派发（当前页控件、页面滚动、结果页动作拖动排序），`client/ui/screen/form` 的 `FormInputRouter` 负责表单内派发（选择框 → 行尾按钮 → 字段控件 → 表单滚动）；`RuleEditorEditPages` 的 `mouseClicked`、`mouseReleased`、`mouseDragged`、`mouseScrolled`、`keyPressed`、`charTyped` 只委托给 `RuleEditorPageInput`。
- `client/ui/screen` 与 `client/ui/screen/form` 存在职责交叉点：`RuleEditorPageInput#mouseClicked` 直接调用 `RuleEditorEditPages.ruleEditorResultsPage` 的 `RuleEditorResultsPage#clickRemove`，`RuleEditorPageInput#mouseReleased` 直接调用 `RuleEditorResultsPage#moveEffect`；`RuleEditorEditPages` 持有 `FormView` 列表并暴露 `RuleEditorEditPages.Host#suggestions`、`RuleEditorEditPages.Host#conditionEditingSupport` 给表单控件；`RuleEditorPanelFactory` 装配 `LeafPanel`。依赖方向为 `client/ui/screen` → `client/ui/screen/form`，包级依赖统计中无反向边。
- 同一概念存在多种命名：结果候选的 JSON 字段为 `outcomes`（`RuleFields.OUTCOMES`），`RuleEditorResultsPage` 的方法与字段用 `candidate`（`RuleEditorResultsPage#addCandidate`、`RuleEditorResultsPage#duplicateCandidate`、`RuleEditorResultsPage#candidateIndex`），界面键用 `results.plan_*` 与 `button.candidate_*`，`ResultStructure.Kind` 又用 `OUTCOMES`。
- 主题获取方式有两套：`RuleEditorEditPages.THEME` 为包级共享常量，`RuleEditorInfoPage`、`RuleEditorInputPage`、`RuleEditorTriggerPage`、`RuleEditorResultsPage` 以 `private static final UiHostTheme THEME = RuleEditorEditPages.THEME` 复用；`RuleEditorScreen`、`RuleEditorScreenListPage`、`RuleEditorScreenDebugPage`、`RuleEditorScreenEditPage`、`RuleEditorScreenListActions`、`RuleEditorPanelFactory`、`RuleChangesView`、`RuleNameInput`、`SourceBlacklistPicker`、`TagCarouselView` 各自调用 `UiHostThemes.defaultTheme()`。
- 公开成员多数只有行注释、没有 JavaDoc：`RuleEditorModel#refresh`、`RuleEditorModel#session`、`RuleEditorScreen#init`、`RuleEditorScreen#tick`、`RuleEditorPanelFactory#clockText` 等以 `//` 说明；带 JavaDoc 的公开成员包括 `RuleEditorScreen#setNotice`、`RuleEditorScreen#noticeColor`、`RuleEditorEditPages#renderDescriptionTooltip`、`RuleEditorEditPages#reveal`。
- 页面容器字段可见性不一：`RuleEditorEditPages#ruleEditorInfoPage`、`#ruleEditorInputPage`、`#ruleEditorTriggerPage`、`#ruleEditorResultsPage`、`#liveWidgets`、`#liveForms`、`#buttons`、`#pageScroll` 为包级字段，被 `RuleEditorPageInput`、`RuleEditorValidation` 与页面类直接读写；`RuleEditorScreen` 的 `listPage`、`editPage`、`debugPage`、`model`、`focus`、`modals` 为 private 字段。
- 提示入口有两套签名：`RuleEditorScreen#setNotice(Component, NoticeLevel)` 是公开语义入口，私有 `RuleEditorScreen#setNotice(Component, int)` 把调色板色值折回级别；`RuleEditorEditPages.Host#notice(Component, int)`、`RuleEditorScreenListPage.Host#setNotice(Component, int)`、`RuleEditorScreenEditPage.Host#setNotice(Component, int)`、`RuleEditorScreenDebugPage.Host#setNotice(Component, int)` 均以 int 色值桥接。
- 命名相近而职责不同：`RuleTriggerView`（包级，绘制配方箭头触发信息）与 `RuleTriggers`（public，读写 `triggers` 字段）；`RuleRecipeView`（public，配方行快照）与 `RuleChangesView`（包级，变更清单，复用 `RuleRecipeView` 行）。
- `ConsumptionSummary` 不在本包，位于 `client/ui/screen/form`，由 `RuleEditorScreen` 以 `client.ui.screen.form.ConsumptionSummary` 导入并用于页头悬停摘要。
- `RuleEditorScreen.Mode` 为 private 枚举，`RuleEditorScreen.NoticeLevel` 为 public 枚举；`RuleEditorEditPages.CardHeader` 与 `RuleEditorEditPages.PageCard` 为包级嵌套类型。
- 四个页面类的构造签名统一为 `页面类(RuleEditorEditPages pages)`，由 `RuleEditorEditPages` 构造时按固定顺序创建。
- `RuleEditorScreenListPage.Host#selectInputTab` 由 `RuleEditorScreenListActions#createRule` 在新建规则后调用，用于把页签切到输入页。

## 3. 类清单

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `ActionDescription` | 动作的玩家说明：按已注册字段语义展示有效值 | `ActionDescription#lines`、`ActionDescription#describe`、`ActionDescription#reference`、`ActionDescription#itemLabel` |
| `CatalogEntryPolicy` | 目录条目策略：物品与流体的默认排除、范围状态 | `CatalogEntryPolicy#fixed`、`CatalogEntryPolicy#scoped`、`CatalogEntryPolicy#hidden`、`CatalogEntryPolicy#scope`、`CatalogEntryPolicy#setScope` |
| `ResultStructure` | 顶层 `effects` 与 `outcomes` 的只读结构模型 | `ResultStructure#kind`、`ResultStructure#hasFlat`、`ResultStructure#hasOutcomes`、`ResultStructure#candidateAt`、`ResultStructure#effectsAt`、`ResultStructure#totalEffectCount` |
| `ResultStructure.Kind` | 结果结构类型 | `ResultStructure.Kind#EMPTY`、`ResultStructure.Kind#FLAT`、`ResultStructure.Kind#OUTCOMES`、`ResultStructure.Kind#MIXED` |
| `RuleChangesView` | 变更清单的只读条目快照，复用配方行布局 | `RuleChangesView#preferredHeight`、`RuleChangesView#render`、`RuleChangesView#accessibleName` |
| `RuleEditorEditPages` | 四个页签的组合视图与共享状态 | `RuleEditorEditPages#page`、`RuleEditorEditPages#setPage`、`RuleEditorEditPages#rebuild`、`RuleEditorEditPages#applyToDraft`、`RuleEditorEditPages#issues`、`RuleEditorEditPages#reveal`、`RuleEditorEditPages#apply`、`RuleEditorEditPages#applyStructure` |
| `RuleEditorInfoPage` | 基本页：只读规则 ID、分类（含来源）与来源摘要，并编辑显示名、启用、优先级与备注 | `RuleEditorInfoPage#buildInfo`、`RuleEditorInfoPage#layoutInfo`、`RuleEditorInfoPage#renderInfo`、`RuleEditorInfoPage#idTextLines`、`RuleEditorInfoPage#categoryTextLines`、`RuleEditorInfoPage#sourceTextLines` |
| `RuleEditorInputPage` | 输入页：来源与消耗、催化剂与流体的存在条件、消耗参数与门槛 | `RuleEditorInputPage#buildInput`、`RuleEditorInputPage#buildPresenceSection`、`RuleEditorInputPage#onPresenceToggle`、`RuleEditorInputPage#specifySourceCost`、`RuleEditorInputPage#PresenceRow` |
| `RuleEditorModel` | 按规则 id 的草稿会话模型与持久化 | `RuleEditorModel#refresh`、`RuleEditorModel#openSession`、`RuleEditorModel#dirtyRuleIds`、`RuleEditorModel#buildChangeSetJson`、`RuleEditorModel#tickPersistence`、`RuleEditorModel#resolveConflict` |
| `RuleEditorOpener` | 编辑器界面实现：打开或关闭规则编辑器屏幕 | `RuleEditorOpener#bootstrap`、`RuleEditorOpener#shutdown`、`RuleEditorOpener#open`、`RuleEditorOpener#close`、`RuleEditorOpener#lastRequest` |
| `RuleEditorPageInput` | 页面级输入派发与结果页动作拖动排序 | `RuleEditorPageInput#mouseClicked`、`RuleEditorPageInput#mouseReleased`、`RuleEditorPageInput#mouseDragged`、`RuleEditorPageInput#mouseScrolled`、`RuleEditorPageInput#keyPressed` |
| `RuleEditorPageSupport` | 四页共享的常量、描述符 id、存在条件数据操作与纯函数工具 | `RuleEditorPageSupport#scanPresenceLeaves`、`RuleEditorPageSupport#addRuleLevelPresence`、`RuleEditorPageSupport#retargetCatalystItems`、`RuleEditorPageSupport#addPageIssues`、`RuleEditorPageSupport#PresenceLeaf` |
| `RuleEditorPanelFactory` | 目录选择页、时间/高度区间条与叶面板装配 | `RuleEditorPanelFactory#catalogModal`、`RuleEditorPanelFactory#fieldCatalogModal`、`RuleEditorPanelFactory#leafPanel`、`RuleEditorPanelFactory#dailyBar`、`RuleEditorPanelFactory#catalogTexts` |
| `RuleEditorResultsPage` | 结果页：方案工具条、动作列表、动作参数与窄屏导航 | `RuleEditorResultsPage#buildResults`、`RuleEditorResultsPage#addCandidate`、`RuleEditorResultsPage#duplicateCandidate`、`RuleEditorResultsPage#addEffectOfType`、`RuleEditorResultsPage#enterResultStage`、`RuleEditorResultsPage#PlanPager` |
| `RuleEditorScreen` | 规则编辑器屏幕：模式切换、屏幕生命周期、布局编排与事件派发 | `RuleEditorScreen#render`、`RuleEditorScreen#keyPressed`、`RuleEditorScreen#openEditor`、`RuleEditorScreen#backToList`、`RuleEditorScreen#setNotice`、`RuleEditorScreen#noticeColor` |
| `RuleEditorScreen.NoticeLevel` | 底部状态提示级别 | `RuleEditorScreen.NoticeLevel#INFO`、`RuleEditorScreen.NoticeLevel#WARNING`、`RuleEditorScreen.NoticeLevel#DANGER` |
| `RuleEditorScreenButtonBar` | 按钮排：按可用宽度换行、左对齐排布并转发渲染与输入 | `RuleEditorScreenButtonBar#set`、`RuleEditorScreenButtonBar#preferredHeight`、`RuleEditorScreenButtonBar#layout`、`RuleEditorScreenButtonBar#render` |
| `RuleEditorScreenDebugPage` | 调试页：批量填充与批量删除测试规则草稿 | `RuleEditorScreenDebugPage#init`、`RuleEditorScreenDebugPage#fillTestRules`、`RuleEditorScreenDebugPage#deleteTestRules`、`RuleEditorScreenDebugPage.Host` |
| `RuleEditorScreenEditPage` | 编辑页：页签、底排按钮、保存、冲突与问题定位 | `RuleEditorScreenEditPage#rebuildEdit`、`RuleEditorScreenEditPage#save`、`RuleEditorScreenEditPage#pollWorkspace`、`RuleEditorScreenEditPage#revealIssue`、`RuleEditorScreenEditPage#restoreHistory`、`RuleEditorScreenEditPage.Host` |
| `RuleEditorScreenListActions` | 列表页条目动作与规则 ID 命名、弹窗流程 | `RuleEditorScreenListActions#promptNewRule`、`RuleEditorScreenListActions#promptDuplicate`、`RuleEditorScreenListActions#toggleSelectedEnabled`、`RuleEditorScreenListActions#deleteSelected` |
| `RuleEditorScreenListPage` | 列表页：搜索、筛选、分类规则树、配方预览与底排状态 | `RuleEditorScreenListPage#init`、`RuleEditorScreenListPage#refreshList`、`RuleEditorScreenListPage#render`、`RuleEditorScreenListPage#renderFooter`、`RuleEditorScreenListPage.Host` |
| `RuleEditorTriggerPage` | 触发页：触发来源、触发时间、条件树摘要与画布页入口 | `RuleEditorTriggerPage#buildTrigger`、`RuleEditorTriggerPage#updateLifespanWindow`、`RuleEditorTriggerPage#sharedConditionSupport`、`RuleEditorTriggerPage#openConditionTree` |
| `RuleEditorValidation` | 客户端保存前校验：收敛可本地判定的问题 | `RuleEditorValidation#collectIssues`、`RuleEditorValidation#issueRuleId`、`RuleEditorValidation.Host` |
| `RuleListDisplayCache` | 列表展示快照缓存：按内容版本复用条目的标题、状态、预览与提示，一次刷新只取一次体 | `RuleListDisplayCache#snapshot`、`RuleListDisplayCache#retain`、`RuleListDisplayCache#bodyFetches` |
| `RuleListDisplaySnapshot` | 一条规则的列表展示快照：规则体、标题、分类、状态与按需构建的预览、条件提示、搜索文本 | `RuleListDisplaySnapshot#titleOf`、`RuleListDisplaySnapshot#preview`、`RuleListDisplaySnapshot#conditionTooltip`、`RuleListDisplaySnapshot#searchText` |
| `RuleNameInput` | 规则名输入区：`<namespace>:<path>` / `<path>` 解析、名称限制提示与校验错误位 | `RuleNameInput#parseId`、`RuleNameInput#allowsCharacters`、`RuleNameInput#setProblem`、`RuleNameInput#preferredHeight` |
| `RulePreviewIcons` | 列表页与动作导航共用的产出图标缓存 | `RulePreviewIcons#clear`、`RulePreviewIcons#action`、`RulePreviewIcons#rule`、`RulePreviewIcons#unavailable` |
| `RuleRecipeView` | 配方行的只读展示快照 | `RuleRecipeView#summary`、`RuleRecipeView#tooltip`、`RuleRecipeView#render`、`RuleRecipeView#renderRow`、`RuleRecipeView#tagAt` |
| `RuleSaveOverlay` | 保存遮罩的宿主适配 | `RuleSaveOverlay#render`、`RuleSaveOverlay#mouseClicked`、`RuleSaveOverlay#keyPressed`、`RuleSaveOverlay#requestCancel` |
| `RuleTriggerView` | 配方箭头的触发信息快照与绘制 | `RuleTriggerView#tooltip`、`RuleTriggerView#width`、`RuleTriggerView#render`、`RuleTriggerView#HEIGHT` |
| `RuleTriggers` | `triggers` 字段的只读解析与写入 | `RuleTriggers#NATURAL`、`RuleTriggers#FIRE`、`RuleTriggers#LAVA`、`RuleTriggers#CACTUS`、`RuleTriggers#effective`、`RuleTriggers#toArray` |
| `SourceBlacklistPicker` | 源标签成员的黑名单选择与分页 | `SourceBlacklistPicker#sourceTags`、`SourceBlacklistPicker#modal`、`SourceBlacklistPicker.Group`、`SourceBlacklistPicker.Page`、`SourceBlacklistPicker.Pages` |
| `SourceCatalystSelectionPolicy` | 源物品与催化剂选择的互斥快照 | `SourceCatalystSelectionPolicy#forField`、`SourceCatalystSelectionPolicy#apply` |
| `TagCarouselView` | 标签展开容器：成员轮播与成员列表 | `TagCarouselView#render`、`TagCarouselView#setBounds`、`TagCarouselView#mouseClicked` |

## 4. 相关文档

- [页面层：表单视图与控件](ui-screen-form.md)
- [组件层](ui-widget.md)
- [基础组件层索引](ui-kit/README.md)
- [状态/数据层](client-edit.md)
- [前端文档索引](README.md)
- [架构与依赖方向](../architecture.md)
