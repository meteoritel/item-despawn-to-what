# 状态/数据层：规则草稿与编辑会话（`client/edit`、`client/edit/draft`）

> 层级：状态/数据层；模块路径：`client/edit`、`client/edit/draft`；源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/edit`、`common/src/main/java/com/meteorite/itemdespawntowhat/client/edit/draft`。
> 事实来源：本文件「类清单」列出的源码文件。文档与源码不一致时以源码为准。

## 1. 模块索引

| 模块 | 代码路径 | 本文章节 |
|---|---|---|
| 规则草稿与编辑会话 | `client/edit` | 2 |
| 草稿持久化 | `client/edit/draft` | 3 |

## 2. 模块：规则草稿与编辑会话

### 2.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.edit`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/edit`
- 覆盖：35 个 `.java` 文件（含 `package-info.java`）

### 2.2 职责

- 承载规则草稿 `RuleDraft`：保留服务端基线与工作副本，以 JSON 作为唯一事实来源。
- 承载编辑会话 `EditSession`：所有草稿修改经统一入口写入完整快照历史，并独立保留未完成输入文本。
- 提供条件树与草稿 JSON 的编解码适配，未识别的第三方条件叶原样保留。
- 提供类型编辑器注册表与内置条件/效果描述符表，供表单按描述渲染。
- 提供规则读写的纯函数与无状态服务：自动命名、必填项清单、问题定位、分类、身份工厂、变更集装配、数值与 JSON 摘要。
- 提供页面层使用的只读工作区视图 `EditorWorkspaceView` 及其实机实现 `LiveEditorWorkspace`，其中来源视图以「就绪 / 在途 / 失败」三态查询，并由界面按需触发请求与重试。
- 不发起网络请求，不打开界面；网络收发由 `client/net` 负责，界面由 `client/ui` 负责。

### 2.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `BuiltinEditorDefaults` | class | `ruleBody(String)`； `copyOf(String, RuleValue)`； `effectBody(ResourceLocation)`； `effectiveValue(RuleValue, String)`； `conditionLeafJson(ResourceLocation)` | 内置类型的新建初值与省略字段的展示默认值 |
| `BuiltinEditorDescriptors` | class | `Y_LEVEL_SLIDER_MIN`； `Y_LEVEL_SLIDER_MAX`； `DEFAULT_TRIGGER_WINDOW_SECONDS`； `RULE_DESCRIPTOR`； `SOURCE_DESCRIPTOR`； `SOURCE_COST_DESCRIPTOR`； `CATALYST_COST_DESCRIPTOR`； `bootstrap()`； `isBootstrapped()`； `entityDescriptor(String)`； `ruleDescriptor()`； `sourceCostDescriptor()`； `catalystCostDescriptor()`； `sourceDescriptor()` | 10 个条件与 10 个效果各注册一条类型编辑器描述 |
| `ClientTypeRegistries` | class | `conditions()`； `effects()`； `leafNode(ResourceLocation)` | 内置条件/效果类型注册表持有者，惰性初始化 |
| `ConditionDraftCodec` | class | `decode(JsonElement)`； `encode(ConditionExpression)` | 客户端条件草稿编解码，已知叶走正式 Codec |
| `ConditionEditorRegistry` | class | `register(TypeEditorDescriptor)`； `isRegistered(ResourceLocation)`； `find(ResourceLocation)`； `descriptorFor(ResourceLocation)`； `all()`； `ids()` | 条件类型编辑器注册表，静态方法委托给 `TypeEditorRegistry` |
| `ConditionTreeClipboard` | class | `read(String)`； `ReadResult(List<ConditionNode>, Component)` | 剪贴板子树列表的读取与限额检查 |
| `ConditionTreeJsonCodec` | class | `decode(RuleValue)`； `encode(ConditionNode)` | 实现 `UiTreeCodec<ConditionNode, RuleValue>` |
| `ConditionTreeNodes` | class | `INSTANCE`； `children(ConditionNode)`； `withChildren(ConditionNode, List<ConditionNode>)`； `childCapacity(ConditionNode)`； `path(String)`； `jsonPath(ConditionNode, UiTreePath, String)`； `remapInputs(...)`（2 个重载） | 实现 `UiTreeEditor.Adapter<ConditionNode>` |
| `DebugTestRules` | class | `CATEGORY`； `ID_PREFIX`； `QUALIFIED_ID_PREFIX`； `MIN_COUNT`； `MAX_COUNT`； `DEFAULT_COUNT`； `SOURCE_ITEM`； `COST_COUNT`； `VARIANT_FIELD`； `VARIANT_EXPERIENCE`； `AMOUNT_FIELD`； `AMOUNT`； `ruleId(int)`； `isTestRule(String)`； `ruleBody(String)` | 调试用测试规则的 id 前缀、分类与最小合法规则体 |
| `DraftListInputs` | class | `capture(JsonObject)`； `capture(RuleDraft)`； `remap(EditSession)` | 候选与效果列表结构变化时的输入路径快照 |
| `DraftPaths` | class | `Segment(String, int)`； `parse(String)`； `render(List<Segment>)`； `child(String, String)`； `indexPath(String, int)`； `read(JsonElement, List<Segment>)`； `readAt(JsonElement, String)`； `readStep(JsonElement, Segment)`； `write(JsonObject, List<Segment>, JsonElement)`； `remove(JsonObject, List<Segment>)` | 草稿路径语法与按路径读写删 |
| `EditSession` | class | `HISTORY_LIMIT` 与 20 个 `OP_*` 操作标签； `PendingInput`； `revision()`； `targetId()`； `draft()`； `load(RuleDraft)`； `isDirty()`； `apply(String, Runnable)`； `setFlagBridge(BooleanSupplier, Consumer<Boolean>)`； `canUndo()`； `canRedo()`； `undoDepth()`； `redoDepth()`； `undoOpKey()`； `redoOpKey()`； `undo()`； `redo()`； `clearHistory()`； `snapshot()`； `addListener(Runnable)`； 输入缓冲 `retainPendingInput`/`pendingInput`/`clearPendingInput`/`pendingInputIssues`/`hasPendingInput`/`clearPendingInputs`/`remapPendingInputs`/`recordInputStructure` | 编辑会话：快照历史与未完成输入缓冲 |
| `EditorChangeSet` | class | `OWN_NAMESPACE`； `expectedVersion()`； `isEmpty()`； `size()`； `upsert(RuleDraft)`； `upsert(String, JsonObject)`； `delete(String)`； `serialize()`； `ruleId(String)` | 客户端变更集装配器 |
| `EditorFactories` | class | `RULE_ID_PREFIX`； `CANDIDATE_ID_PREFIX`； `newRuleId()`； `newCandidateId()`； `blankRule()`； `blankRule(String)`； `candidateBody(String)`； `copyRule(String, JsonObject)`； `reassignIdentity(String, JsonObject)`； `uniqueCandidateId(Set<String>)`； `candidateIds(JsonObject)` | 规则与候选结果的身份分配与默认体构造 |
| `EditorField` | record | 组件 `name`/`labelKey`/`type`/`domain`/`nullable`/`required`/`registry`/`enumGroup`/`hintKey`/`subFields`/`presets`/`numbers`/`displayPrecision`； 29 个静态工厂（`text` 至 `rawJson`）； `optional()`； `asRequired()`； `withHint(String)`； `withPresets(EditorPreset...)`； `withNumbers(NumericDomain)`； `withDisplayPrecision(int)`； `enumValues()`； `intMin`/`intMax`/`doubleMin`/`doubleMax`/`hasNumericDomain`/`backendMin`/`backendMax`/`sliderMin`/`sliderMax` | 编辑器字段描述 |
| `EditorFieldType` | enum | `TEXT`； `LONG_TEXT`； `INTEGER`； `ITEM_COUNTS`； `DECIMAL`； `BOOLEAN`； `ENUM`； `RESOURCE_LOCATION`； `REGISTRY_ID`； `TAG`； `TAG_LIST`； `RL_LIST`； `STRING_LIST`； `CLIMATE_RANGE`； `PERCENT`； `AMPLIFIER`； `TICKS`； `SUBLIST`； `CONDITION_TREE`； `NOTE`； `RAW_JSON` | 21 个字段类型常量 |
| `EditorPreset` | record | `value`； `labelKey` | 字段预设值 |
| `EditorWorkspaceView` | interface | `state()`； `active()`； `sessionId()`； `contextRevision()`； `openRequest()`； `snapshot()`； `catalog(RuleCatalogType)`； `directory(RuleCatalogType)`； `refreshDirectory(RuleCatalogType)`； `directoryFailed(RuleCatalogType)`； `retryDirectory(RuleCatalogType)`； `invalidateDirectories()`； `requestCatalog(RuleCatalogType, String, int, int)`； `requestSourceView(String)`； `sourceView(String)`； `sourceViewLoading(String)`； `sourceViewFailed(String)`； `retrySourceView(String)`； `referenceLifespanTicks()`； `save(String, String)`； `saveProgress()`； `cancelRequested()`； `cancelSave()`； `newOperationId()`； `close(String)`； `resultSeq()`； `lastStatus()`； `lastMessageCode()`； `lastMessageArgs()`； `lastResultVersion()`； `lastWrittenToDisk()`； `lastReloaded()`； `lastIssues()` | 页面层使用的编辑工作区只读视图 |
| `EffectEditorRegistry` | class | `register(TypeEditorDescriptor)`； `isRegistered(ResourceLocation)`； `find(ResourceLocation)`； `descriptorFor(ResourceLocation)`； `all()`； `ids()` | 效果类型编辑器注册表，与条件注册表同构 |
| `FieldNumbers` | class | `TICKS_PER_SECOND`； `MIN_SECONDS_STEP`； `MAX_AMPLIFIER`； `MIN_LEVEL`； `MAX_LEVEL`； `percentText(double)`； `parsePercent(String)`； `ticksToSecondsText(int)`； `parseSecondsToTicks(String)`； `parseSecondsDirect(String)`； `levelOf(int)`； `amplifierOf(String)`； `text(double, int)` | 数值字段的文本与 JSON 值互转 |
| `FormIssue` | record | `path`； `label`； `message`； `blocking`； `error(String, Component, Component)`； `warning(String, Component, Component)`； `matches(String)` | 表单校验问题 |
| `JsonSummary` | class | `compact(RuleValue)`； `compact(RuleValue, int)`； `describe(RuleValue, int)`； `compact(JsonElement)`； `compact(JsonElement, int)`； `describe(JsonElement, int)` | 只读回退的一行 JSON 摘要 |
| `LiveEditorWorkspace` | class | `instance()`； `state()`； `sessionId()`； `contextRevision()`； `openRequest()`； `snapshot()`； `catalog(...)`； `directory(...)`； `refreshDirectory(...)`； `directoryFailed(...)`； `retryDirectory(...)`； `invalidateDirectories()`； `requestCatalog(...)`； `requestSourceView(...)`； `sourceView(...)`； `sourceViewLoading(...)`； `sourceViewFailed(...)`； `retrySourceView(...)`； `referenceLifespanTicks()`； `save(...)`； `saveProgress()`； `cancelRequested()`； `cancelSave()`； `newOperationId()`； `close(...)`； `resultSeq()`； `lastStatus()`； `lastMessageCode()`； `lastMessageArgs()`； `lastResultVersion()`； `lastWrittenToDisk()`； `lastReloaded()`； `lastIssues()` | `EditorWorkspaceView` 的实机实现，单例 |
| `NumericDomain` | record | 组件 `backendMin`/`backendMax`/`windowMin`/`windowMax`/`step`/`shiftStep`/`displayPrecision`/`integer`； `integers(int, int)`； `integers(int, int, int, int)`； `decimals(double, double, double, double)`； `decimals(double, double, double, double, int)`； `withWindow(double, double)`； `withSteps(double, double)`； `domainText()`； `sliderMin(double)`； `sliderMax(double)` | 数值字段的域元数据 |
| `OpaqueCondition` | class | `type()`； `rawNode()`； `equals(Object)`； `hashCode()` | 实现 `Condition`，只保留类型标识与原始节点 JSON |
| `RuleCategorySkeleton` | class | `LABEL_PREFIX`； `EXTENSION`； `roots()`； `Category(id, children)`（`labelKey()`） | 已知规则分类的层级、顺序与本地化显示名 |
| `RuleDraft` | class | `of(JsonObject)`； `of(RuleValue)`； `id()`； `original()`； `toJson()`； `toOverlayJson()`； `isDeleted()`； `setDeleted(boolean)`； `isDirty()`； `revert()`； `root()`； `value(String)`； `set(String, RuleValue)`； `has`/`remove`/`get*`/`set*`； `valueAt`； `setAt`； `mergeAt`； `removeAt`； `textAt`/`integerAt`/`decimalAt`/`flagAt`/`valuesAt`； `conditionsValue`； `conditionsValueAt`； `conditionsOrNull`； `setConditions`； `conditionsAt`； `setConditionsAt`； `encodeConditionsValue`； `decodeConditions`； `replaceWith`（2 个重载）； `effectCount`； `moveEffect`； `candidateEffectsPath`； `effectCountAt`； `effectValuesAt`； `moveInList`； `hasFlatEffects`； `hasOutcomes`； `hasConflictingStructures`； `outcomeCount`； `outcomeValues`； `outcomeValueAt`； `moveOutcome`； `candidateIds`； `candidateIdExists`； `convertToOutcomes` | 规则草稿 |
| `RuleIssueLocator` | class | `Tab`（`SOURCE`/`CONDITIONS`/`RESULTS`/`INFO`）； `AnchorStep`； `ResolvedStep`； `IssueAnchor`（`resolve`/`location`）； `Location`（`known`/`hasCandidate`/`hasEffect`）； `anchor(String, String, JsonObject)`； `anchorAll(String, Iterable<String>, JsonObject)`； `tabOf(String)` | 服务端问题路径到编辑器稳定身份的映射 |
| `RuleNaming` | class | `PREFIX`； `NameSource`（`label`/`NONE`）； `ruleTitle(RuleDraft, NameSource)`； `ruleTitle(RuleValue, NameSource)`； `sourceTitle`； `resultTitle`； `candidateTitle`； `effectListTitle`； `effectTitle` | 只读自动命名与短摘要 |
| `RuleRequirements` | class | `Missing(String, String)`； `find(RuleDraft)`； `find(RuleValue)` | 缺少必填项的导航清单 |
| `RuleValue` | class | `Kind`（`NULL`/`TEXT`/`NUMBER`/`FLAG`/`MAPPING`/`LIST`）； `Mapping`； `Sequence`； `nil()`； `text(String)`； `integer(long)`； `decimal(double)`； `flag(boolean)`； `mapping()`； `mapping(RuleValue)`； `list()`； `list(RuleValue)`； `textList(List<String>)`； `parse(String)`； `parse(Reader)`； `kind()`； `isNull`/`isMapping`/`isList`/`isText`/`isNumber`/`isFlag`/`isScalar`/`isEmpty`； `has`/`child`/`childOr`/`keys`/`entries`/`size`/`items`/`item`； `text`/`integer`/`decimal`/`flag`； `asText`/`asInteger`/`asDecimal`/`asFlag`/`textItems`； `toJson`/`toPrettyJson`/`deepCopy`/`decode(Codec)` | 状态/数据层与页面层之间的动态值中介 |
| `TypeEditorDescriptor` | record | `id`； `label`； `fields`； `readOnly`； `of(ResourceLocation, Component, List<EditorField>)`； `of(ResourceLocation, Component, EditorField...)`； `readOnly(ResourceLocation, Component)`； `hasField(String)`； `field(String)`； `fieldCount()` | 类型编辑器描述 |
| `TypeEditorRegistry` | class | `TypeEditorRegistry(String)`； `register(TypeEditorDescriptor)`； `isRegistered(ResourceLocation)`； `find(ResourceLocation)`； `descriptorFor(ResourceLocation)`； `all()`； `ids()`； `size()` | 类型 id 到类型编辑器描述的映射 |
| `TypeLabels` | class | `UI_PREFIX`； `OWN_NAMESPACE`； `conditionLabel(ResourceLocation)`； `effectLabel(ResourceLocation)`； `displayKey(String, ResourceLocation)`； `key(String, ResourceLocation)`； `legacyKey(String, ResourceLocation)`； `typeSegment(ResourceLocation)` | 条件/效果类型标签与本地化 key |
| `package-info` | 包声明 | — | 声明本包职责与依赖约束 |

### 2.4 依赖关系

- 依赖本模组：`core.model`（11 个文件 import，共 24 处；`ConditionExpression`、`ConditionNode`、`RuleCodecs`、`CatalystCost` 等）。
- 依赖本模组：`core.api`（15 个文件 import，共 18 处；`RuleFields`、`TypeRegistry`）。
- 依赖本模组：`core.network.protocol`（3 个文件 import；`RuleEdit`、`RuleEditChangeSet`、`RuleCatalog`、`RuleIssue`、`RuleSaveStatus`、`RuleSnapshot`、`RuleSourceView`）。
- 依赖本模组：`core.type.effect`（3 个文件，3 处；`PlaceBlockEffect`、`SpawnEntityEffect`）。
- 依赖本模组：`core.type`（1 个文件，2 处；`BuiltinConditionTypes`、`BuiltinEffectTypes`）。
- 依赖本模组：`core.config`（1 个文件，1 处；`ServerConfig`）、`core.service`（1 个文件，1 处；`RuleEditSessionLimits`）、`core.type.condition`（1 个文件，1 处；`YLevelCondition`）、`core.network.transport`（1 个文件，1 处；`RuleSaveResultPayload`）。
- 依赖本模组：`client.net`（2 个文件 import，共 5 处；`EditorOpenRequest`、`RuleEditClientState`、`RuleEditClientWorkspace`）。
- 依赖本模组：`client.ui.kit`（2 个文件 import，共 3 处；`UiTreeCodec`、`UiTreeEditor`、`UiTreePath`）。
- 依赖外部：`java.util`（25 个文件，74 处）、`com.google.gson`（15 个文件，44 处）、`org.jetbrains.annotations`（24 个文件，24 处）、`net.minecraft.resources`（12 个文件，12 处）、`net.minecraft.network.chat`（9 个文件，9 处）、`com.mojang.serialization`（2 个文件，4 处）、`java.math`（2 个文件，4 处）、`java.util.function`（1 个文件，2 处）、`net.minecraft.locale`（1 个文件，1 处）、`java.io`（1 个文件，1 处）。
- 被依赖：`client.ui.screen`（27 个文件 import，共 126 处）。
- 被依赖：`client.ui.screen.form`（27 个文件 import，共 100 处）。
- 被依赖：`client.ui.shared`（6 个文件 import，共 13 处）、`client.ui.widget`（4 个文件 import，共 8 处）、`client.ui.theme`（1 个文件 import，共 1 处；`TypeLabels`）。
- 被依赖：`dev.guiscreenshot`（1 个文件 import，共 1 处；`EditorWorkspaceView`）。
- 被依赖：`client.edit.draft`（1 个文件 import，共 2 处；`RuleDraft`、`RuleValue`）。

### 2.5 关键实现位置

- `EditSession#HISTORY_LIMIT`：撤销/重做历史上限 `100`。
- `EditSession#apply`：修改前深拷贝工作副本、输入缓冲、删除标记与目标级布尔标记；工作副本、删除标记或目标级标记三者任一变化才入栈。入栈后按 `HISTORY_LIMIT` 从栈底丢弃最旧条目，清空重做栈并自增 `revision`。
- `EditSession#retainPendingInput`：同一路径复用已有 `PendingInput#identity`，否则分配新序号；`text` 与 `issues` 在记录处做不可变拷贝。
- `EditSession#remapPendingInputs(Map)`：按最长匹配的作用域前缀迁移缓冲路径，并同步迁移缓冲内 `FormIssue#path`；交换兄弟节点时不会互相覆盖。
- `EditSession#restorePendingInputs`：历史恢复先按 `identity` 找回缓冲；字段类操作（`OP_SET_FIELD`、`OP_SET_SOURCE`）保留原位置，树操作按叶对象唯一匹配定位，无唯一匹配时不绑定到同类型节点。
- `EditSession#load`：换草稿时清空撤销栈、重做栈与输入缓冲，并自增 `revision`。
- `RuleDraft#of`：`original` 与 `working` 都是入参的深拷贝；`isDirty` 为删除标记或 `original` 与 `working` 不相等。
- `RuleDraft#toOverlayJson`：删除标记以 `delete=true` 附加到输出副本，不写入工作副本。
- `RuleDraft#setConditionsAt`：空表达式删除 `conditions` 字段而不是写入 JSON null。
- `RuleDraft#moveInList`：`to` 为移动后的最终下标；区间内容完全相同时返回 `false`。
- `RuleDraft#convertToOutcomes`：已声明 `outcomes` 或候选 id 为空时返回 `false`；否则删除顶层 `effects` 并写入单个候选。
- `DraftPaths#parse`：以 `.` 分隔字段、`[i]` 表示下标；下标不是整数时该段按无下标处理，不抛异常。
- `DraftPaths#write`：空路径返回 `false`；中间数组用空对象补洞；带下标的段遇到非数组元素时整体替换为新数组。
- `DraftPaths#remove`：按下标删除会压缩数组，其后的元素下标整体前移。
- `DraftListInputs#capture` 与 `DraftListInputs#remap`：以 `JsonObject` 对象身份（`IdentityHashMap`）记录顶层 `effects`、`outcomes` 与候选内 `effects` 的路径；同一路径出现新对象且旧对象已消失时清除该作用域的输入。
- `TypeEditorRegistry#register`：重复注册同一 id 抛 `IllegalStateException`，不静默覆盖。
- `TypeEditorRegistry#descriptorFor`：未注册类型返回 `TypeEditorDescriptor#readOnly`，标签为 `Component#translatableWithFallback(回退 key, id 文本)`。
- `BuiltinEditorDescriptors#bootstrap`：双检锁幂等注册；条件侧 10 条、效果侧 10 条；效果公共字段 `delay_ticks`、`chance`、`conditions` 由注册方法统一追加。
- `BuiltinEditorDescriptors#entityDescriptor`：按 `variant`（`item`/`entity`/`experience`）生成参数，再从注册表描述中追加 `delay_ticks`、`chance`、`conditions`。
- `BuiltinEditorDefaults#effectiveValue`：本模组命名空间的效果类型按描述符默认补齐；效果 `chance` 为 1.0、`delay_ticks` 为 0；规则级 `enabled` 为 true、`priority` 为 0、`trigger_after_seconds` 取 `RuleCodecs.DEFAULT_TRIGGER_AFTER_SECONDS`。
- `BuiltinEditorDefaults#fillDescriptorDefaults`：只补齐必填字段，枚举取首个取值、布尔取 `false`、整数类取域下界、小数类取域下界；文本与列表类不猜值。
- `ConditionDraftCodec#decodeLeaf`：注册表命中的类型走正式 `Codec`；未命中的类型包装为 `OpaqueCondition` 并保留完整节点 JSON。
- `ConditionDraftCodec#encode`：空表达式返回 `null`；`OpaqueCondition` 直接回填 `rawNode()`。
- `ConditionTreeJsonCodec#encode`：根节点与解码时接受的值相等时返回解码时保存的原文副本，保留未识别字段。
- `ConditionTreeClipboard#read`：先按 `ConditionLimits#MAX_NODES`/`MAX_DEPTH`/`MAX_LEAVES` 做表达式边界检查，再逐项解码；失败时返回本地化错误文本。
- `ConditionTreeNodes#remapInputs(EditSession, UiTreeEditor.Change, String)`：显式路径变更用变更前后的树各自渲染 JSON 路径；结构新增/删除登记到 `EditSession#recordInputStructure`。
- `ConditionTreeNodes#remapInputs(EditSession, ConditionNode, ConditionNode, String)`：无显式路径变更时按节点对象身份（`IdentityHashMap`）迁移输入。
- `EditorField#percent` 与 `EditorField#amplifier`：两者 `displayPrecision` 为 `-1`（最短往返）；等级域文本为 `1..256`，滑块后端域为 `0..FieldNumbers#MAX_AMPLIFIER`。
- `NumericDomain#domainText`：后端域两端齐全才输出 `min..max`；整数不带小数点，小数用最短往返表示。
- `FieldNumbers#parseSecondsToTicks`：秒 × `TICKS_PER_SECOND`（20）不是整数时返回 `null`，不静默取整。
- `EditorFactories#blankRule`：只写 `id`、`source.items` 空数组、固定源成本 1、`schema_version` 与一个初始候选。
- `EditorChangeSet#upsert`：同一规则 id 只保留最后一次编辑，后写覆盖先写。
- `RuleNaming#ruleTitleJson`：非空 `display_name` → 自动标题模板 → id 文本兜底；运行期异常回落到 `safeFallback`。
- `RuleNaming#ruleTitleJson` 的多候选分支：候选数大于 1 时按 `combination` 选择 `multi_result_priority` 或 `multi_result_cycle` 模板。
- `RuleIssueLocator#anchor` 与 `IssueAnchor#resolve`：数组元素身份依次取 `id:`、`type:`、`op:` 前缀；身份找不到时回落原下标。
- 规则分类由服务端随快照下发并已按显式声明或产出派生；客户端不再按效果类型字符串判定，未知分类标识按原文成组。
- `LiveEditorWorkspace#pollResult`：发现新的 `RuleSaveResultPayload` 时自增 `resultSeq`；所有回执读取方法都先经该入口。
- `LiveEditorWorkspace#sourceView` 与 `#sourceViewLoading`/`#sourceViewFailed`：直接转发 `RuleEditClientWorkspace` 的来源视图缓存三态；界面不持有请求标识，只按状态渲染来源对照。

### 2.6 代码现状（可核对）

- 跨层 import（`client/edit` → `client/net`）：`EditorWorkspaceView.java` import `EditorOpenRequest`、`RuleEditClientState`；`LiveEditorWorkspace.java` import `EditorOpenRequest`、`RuleEditClientState`、`RuleEditClientWorkspace`（2 个文件，5 处）。
- 跨层 import（`client/edit` → `client/ui/kit`）：`ConditionTreeJsonCodec.java` import `UiTreeCodec`；`ConditionTreeNodes.java` import `UiTreeEditor`、`UiTreePath`（2 个文件，3 处）。`package-info.java` 只声明「网络收发由 client/net 负责，界面由 client/ui 负责」，未列出这两条跨层依赖。
- 同概念多命名（条件树 / 条件图）：本包使用 `ConditionTreeNodes`、`ConditionTreeJsonCodec`、`ConditionTreeClipboard`、`ConditionDraftCodec`；基础组件层对应控件名为 `UiConditionGraphEditor`。
- 同值常量重复定义：`EditorChangeSet#OWN_NAMESPACE`（`itemdespawntowhat`）与 `TypeLabels#OWN_NAMESPACE` 各定义一份。
- 效果类型字符串分散维护：`RuleRequirements` 以内联字符串定义 `itemdespawntowhat:consume_source` 等消耗类型并在分支中内联其余类型名；`RuleNaming` 用私有常量 `CONSUME_SOURCE`/`CONSUME_CATALYST`。
- 边界：`RuleRequirements#inspect` 的「是否存在产出」以 `!actions.isEmpty()` 初始化；列表仅含消耗类效果时该方法仍返回 `true`，`RuleRequirements#find` 因此不追加 `required.results`。
- 缺 JavaDoc 的公开嵌套类型：`RuleValue.Kind`、`ConditionTreeClipboard.ReadResult`；两者的含义只由行注释或类型名表达。
- 注释风格不一致：`EditSession`、`RuleDraft`、`RuleValue` 的公开成员使用 `//` 行注释；`DraftPaths`、`FieldNumbers` 的公开方法带 JavaDoc 与 `@param`/`@return`。
- 同一包内对 `TypeRegistry` 的引用方式不一致：`ClientTypeRegistries` 未 import 该类型，方法签名中写全限定名 `com.meteorite.itemdespawntowhat.core.api.TypeRegistry<...>`；其余类 import `TypeRegistry` 后使用短名。

## 3. 模块：草稿持久化

### 3.1 模块路径

- 包：`com.meteorite.itemdespawntowhat.client.edit.draft`
- 源码目录：`common/src/main/java/com/meteorite/itemdespawntowhat/client/edit/draft`
- 覆盖：5 个 `.java` 文件（含 `package-info.java`）

### 3.2 职责

- 把未应用的草稿落盘到客户端本地目录，一个编辑目标一份 JSON 文件。
- 按时间窗口合并写盘请求，并在关键时刻立即写盘。
- 记录建立草稿时的服务端基线与修订号，支持恢复与整条规则级别的冲突判定。
- 目录缺失、文件损坏、解析失败时跳过并记 WARN，不抛异常。
- 不写服务端目录与覆盖层目录；草稿只是本机未提交的编辑中间态。

### 3.3 对外接口与导出内容

| 类型 | 种类 | 公开成员 | 说明 |
|---|---|---|---|
| `DraftConflict` | record | `targetId`； `reason`； `Reason`（`TARGET_MISSING`/`TARGET_EXISTS`/`REMOTE_CHANGED`）； `messageSuffix()` | 草稿与目标不一致的冲突描述 |
| `DraftJournal` | class | `SAVE_DELAY_MS`； `store()`； `restore()`； `has(String)`； `get(String)`； `trackedIds()`； `markDirty(PersistedDraft)`； `consumeFailures()`； `writeNow(PersistedDraft)`； `flushDue(long)`； `flushAll()`； `drop(String)`； `dropAll()` | 草稿落盘节流器 |
| `DraftStore` | class | `DIRECTORY`； `DraftStore()`； `DraftStore(Path)`； `directory()`； `save(PersistedDraft)`； `loadAll()`； `delete(String)`； `clear()`； `fileName(String)` | 草稿文件仓库 |
| `PersistedDraft` | record | 组件 `targetId`/`created`/`restore`/`deleted`/`baseline`/`draft`/`baseVersion`/`baseContextRevision`/`savedAt`； `FORMAT_VERSION`； `baselineValue()`； `draftValue()`； `toJson()`； `fromJson(JsonObject)` | 未应用草稿的落盘载体 |
| `package-info` | 包声明 | — | 声明本包职责与依赖约束 |

### 3.4 依赖关系

- 依赖本模组：`client.edit`（1 个文件 import，共 2 处；`RuleDraft`、`RuleValue`）。
- 依赖本模组：`com.meteorite.itemdespawntowhat`（1 个文件 import，共 1 处；`Constants`）。
- 依赖外部：`java.util`（2 个文件，8 处）、`com.google.gson`（2 个文件，6 处）、`java.nio.file`（1 个文件，5 处）、`org.jetbrains.annotations`（3 个文件，3 处）、`java.io`（1 个文件，1 处）、`java.nio.charset`（1 个文件，1 处）、`java.util.stream`（1 个文件，1 处）。
- 被依赖：`client.ui.screen`（2 个文件 import，共 5 处；`DraftConflict`、`DraftJournal`、`DraftStore`、`PersistedDraft`）。

### 3.5 关键实现位置

- `DraftStore#DIRECTORY`：`config/itemdespawntowhat/client/editor-drafts`，相对游戏根目录。
- `DraftStore#fileName`：输出 `sanitize(targetId) + "-" + shortHash(targetId) + ".json"`。
- `DraftStore#sanitize`：只保留 ASCII 字母、数字与 `.`、`_`、`-`，其余字符替换为 `_`；结果为空时输出 `_`。
- `DraftStore#shortHash`：FNV-1a 32 位，固定 8 位小写十六进制。
- `DraftStore#save`：先写同目录 `.tmp` 文件，再经私有 `moveInto` 以 `ATOMIC_MOVE` 替换；文件系统不支持时退化为普通替换。
- `DraftStore#loadAll`：只读取 `*.json`，按文件名排序；单个文件损坏时记 WARN 并跳过；读取成功后调用私有 `migrate` 处理旧文件名。
- `DraftStore#clear`：只删除目录中的 `*.json` 与 `*.json.tmp`。
- `DraftJournal#SAVE_DELAY_MS`：节流窗口 `2000L` 毫秒。
- `DraftJournal#markDirty`：记录最新内容并把写盘时间点排到 `now + SAVE_DELAY_MS`。
- `DraftJournal#persist`：写盘成功清除待写标记；失败把该目标重排到 `now + SAVE_DELAY_MS` 并在 `failing` 由 false 变 true 时自增失败计数。
- `DraftJournal#restore`：同一 `targetId` 只保留 `savedAt` 较大的一份。
- `PersistedDraft#toJson`：写出 `format`、`target_id`、`created`、`restore`、`deleted`、`base_version`、`base_context_revision`、`saved_at`、`draft`，以及可缺省的 `baseline`。
- `PersistedDraft#fromJson`：缺少 `target_id` 或 `draft`、或 `draft` 不是对象时返回 `null`；布尔/整型/长整型字段类型不符时取缺省值。
- `DraftConflict#messageSuffix`：三个冲突类型分别映射到 `target_missing`、`target_exists`、`remote_changed`。

### 3.6 代码现状（可核对）

- 反向依赖：`PersistedDraft.java` import `com.meteorite.itemdespawntowhat.client.edit.RuleDraft` 与 `RuleValue`（1 个文件，2 处）。
- `package-info.java` 只声明「允许依赖 core 与原版客户端类型」，未声明对 `client/edit` 的依赖。
- 边界：落盘层的 `PersistedDraft` 以 `JsonObject` 组件 `baseline`/`draft` 承载内容，再由 `baselineValue()`/`draftValue()` 包装成 `RuleValue`；`client/edit` 对界面隐藏 Gson 类型的约定不延伸到本层。
- 缺 JavaDoc 的公开嵌套类型：`DraftConflict.Reason`；三个常量只有行注释。
- 边界：`PersistedDraft#fromJson` 不读取 `format` 字段；`FORMAT_VERSION` 只由 `toJson` 写出。

## 4. 类清单

### 4.1 `client/edit`

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `BuiltinEditorDefaults` | 内置类型的新建初值与省略字段的展示默认值 | `ruleBody`； `copyOf`； `effectBody`； `effectiveValue`； `conditionLeafJson` |
| `BuiltinEditorDescriptors` | 内置条件/效果编辑器描述符表 | `bootstrap`； `entityDescriptor`； `ruleDescriptor`； `sourceDescriptor`； `sourceCostDescriptor`； `catalystCostDescriptor` |
| `ClientTypeRegistries` | 客户端内置类型注册表的惰性持有者 | `conditions`； `effects`； `leafNode` |
| `ConditionDraftCodec` | 条件草稿的宽松编解码 | `decode`； `encode` |
| `ConditionEditorRegistry` | 条件类型编辑器注册表的静态入口 | `register`； `find`； `descriptorFor`； `all`； `ids` |
| `ConditionTreeClipboard` | 剪贴板条件子树的读取与限额检查 | `read`； `ReadResult` |
| `ConditionTreeJsonCodec` | 条件树与 JSON 之间的宿主适配 | `decode`； `encode` |
| `ConditionTreeNodes` | 条件节点与通用树编辑器的适配 | `INSTANCE`； `children`； `withChildren`； `path`； `jsonPath`； `remapInputs` |
| `DebugTestRules` | 调试测试规则的 id 前缀、分类与最小规则体 | `ruleId`； `isTestRule`； `ruleBody` |
| `DraftListInputs` | 列表结构变化的输入路径快照 | `capture`； `remap` |
| `DraftPaths` | 草稿路径语法与按路径读写删 | `parse`； `render`； `child`； `indexPath`； `read`； `write`； `remove` |
| `EditSession` | 编辑会话：快照历史与输入缓冲 | `apply`； `undo`； `redo`； `snapshot`； `retainPendingInput`； `remapPendingInputs` |
| `EditorChangeSet` | 客户端变更集装配 | `upsert`； `delete`； `serialize`； `ruleId` |
| `EditorFactories` | 规则与候选结果的身份分配与默认体 | `newRuleId`； `newCandidateId`； `blankRule`； `candidateBody`； `copyRule`； `reassignIdentity` |
| `EditorField` | 编辑器字段描述与构造工厂 | `text` 至 `rawJson` 的静态工厂； `withNumbers`； `enumValues`； `sliderMin`； `sliderMax` |
| `EditorFieldType` | 字段类型枚举 | 21 个字段类型常量 |
| `EditorPreset` | 字段预设值 | `value`； `labelKey` |
| `EditorWorkspaceView` | 页面层使用的编辑工作区只读视图 | `state`； `snapshot`； `save`； `lastStatus`； `lastIssues` |
| `EffectEditorRegistry` | 效果类型编辑器注册表的静态入口 | `register`； `find`； `descriptorFor`； `all`； `ids` |
| `FieldNumbers` | 数值字段的文本与 JSON 值互转 | `percentText`； `parsePercent`； `parseSecondsToTicks`； `levelOf`； `amplifierOf`； `text` |
| `FormIssue` | 表单校验问题 | `error`； `warning`； `matches` |
| `JsonSummary` | 只读回退的 JSON 摘要 | `compact`； `describe` |
| `LiveEditorWorkspace` | `EditorWorkspaceView` 的实机实现 | `instance`； `pollResult`； 全部视图方法 |
| `NumericDomain` | 数值字段域元数据 | `integers`； `decimals`； `withWindow`； `withSteps`； `domainText` |
| `OpaqueCondition` | 未识别条件叶的原文保留载体 | `type`； `rawNode` |
| `RuleCategorySkeleton` | 已知规则分类的静态骨架 | `roots`； `EXTENSION`； `Category` |
| `RuleDraft` | 规则草稿 | `of`； `isDirty`； `toOverlayJson`； `setAt`； `mergeAt`； `setConditionsAt`； `moveInList`； `convertToOutcomes` |
| `RuleIssueLocator` | 问题路径到编辑器稳定身份的映射 | `anchor`； `anchorAll`； `tabOf`； `IssueAnchor#resolve`； `Location` |
| `RuleNaming` | 只读自动命名与短摘要 | `ruleTitle`； `sourceTitle`； `resultTitle`； `candidateTitle`； `effectTitle` |
| `RuleRequirements` | 缺少必填项的导航清单 | `find`（2 个重载）； `Missing` |
| `RuleValue` | 状态/数据层与页面层之间的动态值中介 | `nil`； `text`； `integer`； `decimal`； `flag`； `mapping`； `list`； `parse`； `child`； `asText`； `deepCopy`； `decode` |
| `TypeEditorDescriptor` | 类型编辑器描述 | `of`； `readOnly`； `field`； `fieldCount` |
| `TypeEditorRegistry` | 类型 id 到描述的映射与只读回退 | `register`； `find`； `descriptorFor`； `all`； `ids`； `size` |
| `TypeLabels` | 条件/效果类型标签与本地化 key | `conditionLabel`； `effectLabel`； `displayKey`； `legacyKey`； `typeSegment` |
| `package-info` | 包职责与依赖约束声明 | — |

### 4.2 `client/edit/draft`

| 类型 | 职责 | 关键成员 |
|---|---|---|
| `DraftConflict` | 草稿与目标不一致的冲突描述 | `Reason`； `messageSuffix` |
| `DraftJournal` | 草稿落盘节流器 | `markDirty`； `flushDue`； `flushAll`； `writeNow`； `restore`； `consumeFailures` |
| `DraftStore` | 草稿文件仓库 | `DIRECTORY`； `save`； `loadAll`； `delete`； `clear`； `fileName` |
| `PersistedDraft` | 未应用草稿的落盘载体 | `toJson`； `fromJson`； `baselineValue`； `draftValue`； `FORMAT_VERSION` |
| `package-info` | 包职责与依赖约束声明 | — |

## 5. 相关文档

- [开发者文档索引](../README.md)
- [客户端层](../layers/client.md)
- [接口层：客户端网络与工作区](client-net.md)
- [页面层：规则编辑器屏幕](ui-screen.md)
- [页面层：表单视图与表单控件](ui-screen-form.md)
- [组件层：控件与条件树组件](ui-widget.md)
- [基础组件层：模块入口](ui-kit/README.md)
- [类型系统](../backend/modules/type-system.md)
- [问题校验](../backend/systems/issue-validation.md)
- [编辑保存协议](../backend/flows/edit-save-protocol.md)
