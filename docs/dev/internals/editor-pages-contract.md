# 编辑器四页职责与输入↔触发联动契约

> 事实来源：`client/ui/screen/` 的 `RuleEditorEditPages.java`、`RuleEditorScreen.java`、`RuleEditorScreenEditPage.java`、`RuleEditorScreenListPage.java`、`RuleEditorScreenListActions.java`、`RuleEditorScreenButtonBar.java`、`RuleEditorPageInput.java`、`RuleEditorValidation.java`、`RuleEditorPanelFactory.java`，以及 core 侧 `CatalystPresentCondition` / `CatalystThresholdProjection`。面向改编辑器的人：说明四页各管什么、输入页与触发页如何按**确切叶路径**关联、以及撤销粒度。
> 定位约定：本文按「类名 + 成员名」引用源码（如 `RuleEditorInputPage#buildInput`），不写行号——行号会随重构漂移，成员名才是稳定锚点。

## 1. 四页职责

| 页 | 构建入口 | 负责 | 不负责 |
|---|---|---|---|
| 基本页 INFO | `RuleEditorInfoPage#buildInfo` | 规则身份与元信息：只读规则 ID、显示名、备注、启用开关、优先级 | 条件、消耗、结果 |
| 输入页 INPUT | `RuleEditorInputPage#buildInput` | 来源与来源消耗、催化剂固定成本开关与表单、**存在条件（催化剂/流体）的唯一入口**、消耗参数主入口 | 组合结构、结果 |
| 触发页 TRIGGER | `RuleEditorTriggerPage#buildTrigger` | 触发原因勾选、触发时间（秒）、条件树卡片与画布页 `ConditionTreeOverlay`：结构增删/取反/组合及叶参数就地编辑框 | 输入页的来源与固定消耗 |
| 结果页 RESULTS | `RuleEditorResultsPage#buildResults` | 组合方式、方案（outcomes）与行为、效果参数 | 条件树、固定成本 |

- 触发方式未声明时默认勾选自然消失；显式空数组保留全部不勾选的草稿状态，触发卡片以警告色显示“请至少勾选一项”。重新勾选任一方式即移除警告；`RuleEditorValidation` 保存前检查全部脏规则的空触发数组，切页、切换规则或返回管理页不绕过检查。
- 页枚举 `Page` 与 `switch (page)` 派发仍在 `RuleEditorEditPages`；页内的按下 / 释放 / 拖动 / 滚动与键盘派发由 `RuleEditorPageInput` 承担，`RuleEditorEditPages` 的 `mouseClicked` / `mouseReleased` 只做委托。
- 结果页由 `RuleEditorResultsPage` 提供顶部方案工具条及行为／参数工作区：左右箭头按单击或键盘激活直接切换方案，中间页码只读，单方案与多方案均直接进入行为层；方案操作使用胶囊按钮；组合方式只在多方案时显示，单击轮换枚举值，其后依次为新建、复制、删除、上移与下移，直接执行操作。删除与移动按钮按方案数量和位置禁用。行为通过胶囊叉号删除，保留列表拖动排序。窄屏仅有行为列表（stage 1）和行为参数（stage 2）两层，顶部方案管理在两层均可用。
- 结果页**不重复**消耗参数：过滤链配合 `isInputOwnedConsumptionField`（`consume_source`→`count`/`counts`；`consume_catalyst`→`items`/`count`/`counts`/`radius`；`consume_fluid`→`fluid`，**不过滤** `require_source`——输入页不管理它），并在首个消耗行为参数前插说明行 `effect.consume_params_on_input`；`chance` / `delay_ticks` / `conditions` 仍在结果页编辑。
- 术语：一个 outcome 一律称「**方案**」（`button.candidate_*` / `notice.candidate_*` / `undo.*_candidate` / `issue.candidate_*` / `rule.combination` / `effect.consume_params_on_input` 等文案统一使用该词），只有页签名仍是「结果」（`tab.results`）。
- 初始页：新建**空白**规则落在输入页（`RuleEditorScreenListActions#createRule` 调用 `selectInputTab`）；打开已有规则落在基本页（`RuleEditorScreen#openEditor` 置 `tab = INFO`，字段 `tab` 的默认值见该类字段声明）。
- 管理列表在 `RuleEditorModel#isLoaded` 为 false 时显示“正在加载规则…”；收到首份有效快照后，列表或筛选结果为空才显示空列表提示。`RuleEditorScreenListPage#refreshList` 随快照载入更新文案。
- 切页前先提交合法字段，非法文本保留在当前会话输入缓冲：`RuleEditorScreenEditPage#switchTab` 走 `pages.blockNavigation()` 门禁后 `pages.setPage(target)`；提交路径见第 5、7 节。
- 页名与页内提示同名：输入页一律称「**输入**」（`tab.input`），页内提示、文档与代码注释不得再用其他名字指代该页。
- 页头右侧状态条只表达状态且不可点击：`RuleEditorScreen#drawHeaderText` 以 `header.editing`（「当前编辑 · %s」）作标题，状态条取 `state.applied` / `state.unapplied`（「待应用」，与列表页 `recipe.status.pending` 同词）/ `state.active` / `state.freezing`，右端面板不参与命中判定。
- 界面用词口径：条件树运算符一律取 `tree.operator.*`（「且」/「或」/「非」），画布卡片标题与树摘要的取反行共用该键，撤销标签与移动提示同样只用「非」；组合名 `tree.node.all`「全部满足」/ `tree.node.any`「任一满足」只用于「包成组」菜单与组行。消耗提示与校验提示不得出现 JSON 字段名、英文枚举或全限定 ID，字段名一律换成界面用词（`catalyst_cost.items` → 「催化剂成本的物品」，`schema_version` → 「规则文件版本」）。

### 条件树草稿与正式配置

- `RuleDraft` 的条件树读写通过客户端 `ConditionDraftCodec`：空 `all_of` / `any_of` 保留空 `terms`，缺项 `inverted` 保留 `op` 并省略 `term`；重新打开表单仍可继续编辑。叶参数仍交给既有正式类型 Codec，旧形状与未识别数据不被静默迁移。
- 画布 `UiConditionGraphEditor` 在空树和空组合内部显示“＋ 添加条件”；删除 NOT 唯一子项后保留 NOT，并可从内部入口补齐。添加占位不是条件节点，不计入节点数、叶数和深度限额。
- 无根表达式继续省略 `conditions`，表示无条件限制；空组合和缺项 NOT 是未完成草稿，表单及规则保存前的结构校验均拦截。服务端的正式 `RuleCodecs` 和运行时求值保持既有严格契约。
- 条件叶行按「条件名（参数摘要）」展示：条件名取宿主注入的类型选项标签，缺省回退 `TypeLabels.conditionLabel`，参数摘要复用 `NaturalSummary.conditionParams`；摘要仅用于显示，不写入配置。截断行可悬停查看全文，叶参数确认并重载表单后同步更新。行首展开 / 收起标记是 1 像素扫描线拼出的像素三角（▼ 已展开、▶ 已收起，与 `UiTreeView` 的树标记同形），层级用 1 像素竖线引导；缩进宽度、行高与标记命中范围由 `UiConditionTreeSummary` 的常量固定。
- 条件树的完整树扫描由 `ConditionTreeInspection` 承担，独立于可见行投影，节点数、叶数、深度和结构问题不受折叠影响。树实例或摘要格式器变化才重新扫描，布局和折叠复用结果；错误子树的祖先行使用错误颜色。
- `FormView.revealPath` 将树内的节点或参数字段路径交给条件控件；定位展开祖先、选中并滚动到节点。问题定位链路按现状为 `FormView.revealPath` → `FormControl.revealPath(path, basePath)` → `ConditionSupport.openTree(tree, fieldPath)` → `RuleEditorTriggerPage.openConditionTree(owner, fieldPath)` → `ConditionTreeOverlay.reveal(fieldPath)`，摘要节点点击也携带节点路径，经所属表单补齐作用域后进入画布并展开已知叶的参数框。已知条件的参数字段问题通过 `ConditionSupport.revealLeaf` 打开叶参数弹窗并聚焦字段；未知叶保留局部只读。表单问题保留具体树路径及带实际限额的提示，正式保存拦截不依赖展开状态。

## 2. 存在条件：唯一入口在输入页

- 两个开关：催化剂 / 流体，标签 `rule.presence.add_catalyst`（「启用催化剂」）/ `rule.presence.add_fluid`（「启用流体条件」）。催化剂卡片按「启用 → 物品选择与最低触发数量 → 消耗开关 → 每轮消耗数量」排列；物品与流体选择复用目录及卡片控件，卡片内可逐项移除引用。
- 勾选 = 在**规则级条件**里新增一个存在条件叶（`RuleEditorInputPage#addRuleLevelPresence`）：条件为空树时直接写入叶；根已是 `all_of` 时把叶追加进 `terms`；否则把**完整旧根**与新叶一起放进新的 `all_of(旧根, 叶)`——保留旧根整体语义，不把叶插进当前选中的节点。
- 输入页启用催化剂创建 `{op: leaf, condition: {type: …, items: [], count: 1}}`，每个引用的门槛从 1 开始，由 `counts` 对象分别编辑。触发页新建催化剂叶仍省略 `count`，保留运行期默认门槛语义；打开已有省略门槛的叶只显示有效值，不自动写回。
- 存在条件类型字段的 basePath 就是该叶的 condition 路径：`newForm(session, leaf.conditionPath(), presenceDescriptor(leaf))`；字段路径 `fieldPath() = conditionPath + ".items"`（催化剂）或 `+ ".fluids"`（流体列表，任一种命中即可）。存在叶的物品选择同步关联的消耗配置，后者仍保留运行所需的 `items`。
- 未勾选时不渲染类型字段（`RuleEditorInputPage#buildPresenceSection` 的行为）。
- 单个催化剂或流体叶不重复显示编号、作用域与整叶移除按钮；多个同类型叶保留标题和整叶移除入口。卡片上的移除只删除该引用；整叶移除只删除对应的条件叶。流体引用留空时显示「任意非空流体」，沿用后端的任意流体语义。
- 流体目录、选中卡片及标签成员共用 `FluidPreviewIcons`：从流体对应方块的模型图集取得静态材质和方块染色，绘制有明暗面的立体方块图标；流水等非源流体名称带「流动」标识。目录与文本候选过滤注册表中的空流体，流体标签预览也过滤空成员。材质每帧从当前图集读取，不保留资源重载前的 sprite。
- 取消勾选 = 删除规则级里该类型的全部存在叶（`RuleEditorInputPage#removeRuleLevelPresence`，逐个删并整理空父组；删除未生效即停手，防死循环）。
- 关闭「启用催化剂」会一并删除规则级 `catalyst_cost` 及规则中的 `consume_catalyst` 效果，关闭「消耗催化剂」只删除消耗配置并保留存在条件。流体存在开关仍不影响独立移除行为。每次开关操作与其关联删除共用一条撤销记录。
- 消耗参数主入口也在输入页：来源消耗（`sourceForm` / `sourceCostForm`）、催化剂消耗开关与表单（`catalystToggle` / `catalystForm`）。选定催化剂物品后才能首次开启消耗；开启时每轮数量初始化为当前门槛，随后可独立调整。关闭消耗前会固定省略门槛的当前有效值，避免删除消耗配置后门槛变化。
- 催化剂消耗区只显示每轮数量，不重复选择物品、不显示搜索半径。新建固定成本省略半径，由后端默认值 1 处理；已有显式半径原样保留。没有规则级存在叶的已有独立消耗规则仍显示其物品选择入口。
- 已有消耗效果的数量表单直接绑定 `RuleCostBinding.Ref` 的原效果路径，不生成额外固定成本；催化剂消耗开关可关闭这类效果，来源消耗模式仍锁定为原效果。

触发时间表示掉落后的年龄门槛。`RuleEditorTriggerPage.updateLifespanWindow` 经工作区读取服务端缓存的统一参考寿命，滑条常用上限按寿命向上取整到秒；切换源匹配不重新查询。NeoForge 启动时构造一个未加入世界的普通石头物品实体并读取 `lifespan`；Fabric 的 `ItemLifespanReferenceMixin` 在首次服务端物品 tick 中观察自然寿命比较阈值并原样返回，缓存由 `FabricLifespanReference` 持有。参考值保留到停服，客户端保留到断线；Fabric 尚未采样时 GUI 使用 300 秒窗口，服务端采样完成后补发一次应答。参考上限不参与 JSON 合法性校验，不钳制已有合法值，精确输入仍可超过窗口；实际转化仍受当前物品实际寿命来源限制；Fabric 寿命来源优先采用采样阈值，采样前使用配置兜底。Mixin 观察采用较低优先级与可链式表达式注入；其它模组整体替换自然消失判断或更晚覆写表达式时不能保证采样，未命中时继续使用默认窗口。

## 3. 同一份叶扫描：路径、编号与作用域

`RuleEditorInputPage#scanPresenceLeaves` 用同一条递归（`collectPresence`，路径语法与草稿一致：`terms[i]` / `term` / `condition`）扫描三条线，返回全量存在条件叶并**按固定顺序编号**：

| 顺序 | 扫描位置 | 作用域文案 |
|---|---|---|
| 1 | 规则级 `conditions` | `rule.presence.scope_rule`（「规则级」） |
| 2 | 行为局部 `effects[i].conditions` | `rule.presence.scope_action`（「行为 %s」） |
| 3 | 方案内行为 `outcomes[j].effects[k].conditions` | `rule.presence.scope_effect`（「方案 %s · 行为 %s」） |

- 编号按类型各自递增（`counters.merge(type, 1, Integer::sum)`）；标题行 = `rule.presence.title`（「类型 编号 · 作用域」，由 `presenceHeader` 生成）。
- 输入页与触发页、叶弹窗标题**共用同一份扫描结果**（`ruleLevelPresence`、`findPresenceLeaf`），因此同一类型第 N 项在两侧是同一个编号。

## 4. 输入↔触发按确切叶路径关联（契约）

**契约**：输入页与触发页之间不存在类型副本，也不按「类型相同」匹配；两侧都按**确切叶路径**指向同一份草稿 JSON。

1. 输入页只呈现**规则级**叶（`ruleLevelPresence` 过滤 `leaf.ruleLevel()`）；行为局部叶不在此列出，只在触发页条件树里编辑。
2. 触发页**不新增类型副本**：`buildTrigger` 只建触发原因勾选框、触发时间表单与条件树；叶结构未变，新增与结构编辑经画布 `UiConditionGraphEditor`，卡片内的 `UiConditionTreeSummary` 只呈现树干与叶条件名，叶的增删/取反/组合操作不变。
3. 叶参数编辑入口：`pageConditionSupport(owner)` → `openLeafEditor(owner, leaf)`。弹窗以**发起编辑的那个表单**为 owner：`editConditionLeaf` 在 `liveForms` 里用 `findTree(form, leaf)` 找到承载该叶的表单，路径取该树的 `selectedPath()`。同名同类型的多个叶各绑各的路径，**不会误绑**。
4. 弹窗标题带类型编号与作用域；催化剂叶显示所有引用均需达标的说明，流体叶显示任一种即可匹配的说明。
5. 确认时只写回该叶自己的路径：`form.applyToDraft()` 后 `owner.reload()` 刷新发起编辑的表单，再由 `host.onDraftChanged()` 通知宿主。逐项编辑**不改变组合结构**，也**不会把行为局部条件提升到规则级**。
6. 实现细节：叶弹窗内的目录选择、区间条与事件转发由 `RuleEditorPanelFactory#leafPanel(...)` 承载（`basePath` 即叶参数在草稿中的路径）；门槛说明行经 `LeafPanel#addNote` 注入；校验失败时 `focusField(issue.path())` 把焦点定位到问题字段。

### 4.1 生物群系参数与范围控件

- `RuleEditorTriggerPage#configureBiomeFields` 根据 `mode` 绑定 `FormView#setRowVisibility`：注册表模式显示 `biomes` 的 ID / 标签目录选择，气候模式显示六个气候参数。模式入口位于表单首行，参数随表单滚动；历史回填同步可见行。隐藏字段保留值和文本缓冲，不参与表单写入或本地输入错误检查。
- `ClimateControl` 将每个参数的独立匹配开关、双端范围条和最小 / 最大精确输入放在同一表单行。开启时初始化范围为 `[-1,1]`；关闭时删除对应参数字段，`BiomeEvaluator#testClimate` 跳过缺失参数。气候模式至少需要启用一个有效范围，由 `BiomeCondition#validateParams` 校验。
- 单端范围装载时，滑条将缺失端显示在数值域边界；仅查看或切换模式保留原 JSON，拖动后提交两端范围。非法文本留在表单缓冲并提供范围与顺序校验，关闭参数会清除该参数的文本缓冲。开关状态与两端文本一起保留，拖动、方向键释放和精确输入共用字段写入与撤销流程。
- 高度 / 光照附加范围条以各自滑条窗口边界预览缺失端，倒置范围由精确输入校验，预览装载不构造非法区间。`UiRangeBar` 绘制两个端点手柄和范围文本；普通范围与昼夜范围点击轨道后从预览位置继续拖动，精确提交只触发一次变更。`LeafPanel` 只向聚焦附加控件转发按键和字符，避免数值输入同时调整其它滑条。

## 5. 物品与数量联动

- 字段写入统一走 `FormView.writeField`，引用改写、数量对象同步和关联消耗配置重指向共用一条撤销记录。存在叶缓冲提交先核对确切叶路径，再调用同一表单写入流程。
- 源物品保留替代匹配语义。界面只提供「指定每轮消耗」按钮；开启后 `source_cost` 按当前引用写入数量 1，每个引用有独立滑条与精确输入。已有 `consume_source` 时编辑其 `counts`，避免同时生成固定成本。
- 催化剂各引用均需满足各自门槛。`counts` 以引用为键，新增引用采用默认数量，移除引用同步移除对应数量，保留仍选中的值。固定催化剂成本使用规则级存在叶的引用并集；已有独立消耗行为只在 items 为空或与原集合相同时同步。
- 开启「消耗催化剂」时，按引用复制当前门槛为每轮成本；同一引用存在多个规则级门槛时采用最大值。随后门槛和成本可各自调整；每轮全部引用付得起才扣除。
- 流体列表多选采用或关系；写入 `fluids` 时移除旧的单值 `fluid`，清空列表表示任意流体。选成单个流体时，类型为空或等于原单值的 `consume_fluid` 跟随新单值；多选不自动选择某个移除目标。
- 类型表单在连续目录回调期间保持实例，避免后续选择写进已卸载控件。数量行刷新不丢弃其它字段未提交的输入。

## 6. 数量展示与运行期同源

- `ItemCountsControl` 复用 `NumberControl`，按引用逐行显示物品图标与名称、数量滑条与精确输入；行首图标列与名称列按全场最宽名称统一宽度，滑条起止上下对齐；输入页和条件叶弹窗共用该控件。
- 催化剂门槛统一称「最低触发数量」，消耗区称「每轮消耗数量」；GUI 搜索半径使用默认 1，不提供自定义入口。
- 显式 `counts` 优先，缺省引用采用显式 `count`；门槛均缺省时，`quantityDefaults` 解码完整草稿并调用 `CatalystThresholdProjection.resolveThreshold` 按引用展示有效门槛。未编辑时保留原字段缺失状态。
- 草稿未能解码时展示显式 `count` 或默认 1；作用域按确切条件路径解析，不把不同候选结果或效果的消耗参数混用。
- 运行期投影见 [催化剂门槛运行投影](../backend/systems/catalyst-threshold-projection.md)。

## 7. 撤销与保存语义

- 所有草稿修改必须走 `EditSession.apply(opKey, change)`：记录前深拷贝快照 → 只有真变化（含删除标记与目标级标记）才压栈 → 清空重做栈。撤销/重做是整份草稿快照置换；历史上限 100（`EditSession.HISTORY_LIMIT`）。
- 因此「勾选/取消存在条件」「逐项移除一个叶」「类型改写 + 重指向」各自是**一条**撤销记录。
- 输入缓冲独立于已接受的规则 JSON，切页、重载和返回保留未完成文本；非法或缺失必需输入阻止保存，具体生命周期与结构操作路径同步见 [编辑输入状态](editor-input-state.md)。
- 撤销粒度是**字段级**：`FormView.applyToDraft()` 逐字段比较，每个真实变化的字段单独 `session.apply`。所以叶弹窗里改了多个字段 → 需要多次撤销；**弹窗确认本身不是一条记录**。
- 空父组自下而上整理（`removeLeafAt` 在 `RuleEditorInputPage`，`isEmptyGroup` 在 `RuleEditorPageSupport`）：`all_of` / `any_of` 看 `terms`、`inverted` 看 `term`；空组连父一起删，但**不改变 `any_of` / `inverted` 的结构语义**。
- 保存走既有编辑保存协议，见 [edit-save-protocol](../backend/flows/edit-save-protocol.md)。编辑页在保存前调用客户端校验 `RuleEditorValidation.collectIssues()`；命中问题时按 `issueRuleId` 先打开所属规则、再按原字段路径定位，服务端校验仍是权威。
- 新建及复制成功后清空操作提示，不显示「已创建规则」；错误与应用结果仍由原有提示区显示。

## 8. 已知限制

- 条件树**叶行本身不显示编号**：编号只在输入页存在条件标题行与叶弹窗标题（`UiConditionTreeSummary` 内没有 presence / ordinal / scope 相关代码）。
- 输入页只列**规则级**存在叶；行为局部存在叶只能在触发页的条件树里逐叶进入弹窗编辑。
- 显示名/备注一次性粘贴超过「2×码点上限」的纯补充平面字符（如 emoji）时，可能被原版 `EditBox` 的 UTF-16 单元上限静默截断到恰好上限而不弹超限提示；逐字符键入与常规粘贴不受影响（上限判定本身仍按码点，见 [文本控件](ui-kit/text-controls.md)）。
- 流体多选不会自动改写独立移除行为的目标；单选只重指向未指定类型或与原单值相同的行为。
- 缩进摘要点击节点会打开画布、定位该节点，并自动展开已知条件叶的参数框；未知叶保持只读，容器不展开参数。工具栏入口打开整树默认视图。
- `ConditionTreeOverlay`（1017 行）与 `RuleEditorResultsPage`（935 行）仍是超大类，未拆分，登记为后续单独立项。

## 9. 相关文档

- 门槛运行期：[催化剂门槛运行投影](../backend/systems/catalyst-threshold-projection.md)
- 类型与字段：[类型系统](../backend/modules/type-system.md)
- UI kit 契约：[ui-kit/README.md](ui-kit/README.md)
- 布局与视觉规格：[编辑页卡片布局](editor-card-layout.md)

## 10. 管理页布局与条目操作

- 管理页列表模式由 `RuleEditorScreenListPage` 承担（规则树、筛选框、条目判定与刷新）；新建、复制、启用或停用、屏蔽或取消屏蔽、恢复原始、删除由 `RuleEditorScreenListActions` 承担；列表与编辑页共用的按钮排由 `RuleEditorScreenButtonBar` 提供。
- 管理页不提供独立的选中规则预览面板或展开/收起预览按钮，规则树使用内容区的完整宽度；配方行图标与悬停说明仍在树内显示。
- 管理页没有提示条时，规则树与底部按钮排之间预留 8 个 GUI 像素；显示提示条时按提示条高度额外预留空间。
- 分类名称旁显示当前搜索与筛选结果中的规则数；实体父分类汇总掉落物、通用实体、经验和组合产出子分类的规则数，折叠状态不影响计数。
- 管理页非启用条目的文字、数量角标与消耗标记使用禁用色；输入、产出和触发图标统一降低亮度。图标绘制后恢复调色状态，相邻启用条目正常显示。
- 管理列表与变更清单共用 `RuleRecipeView.renderRow` 的两行布局：首行显示名称及状态或变更类型，次行显示源物品、催化剂、流体浸润与产出图标关系，内容高 28。转换箭头由 `RuleTriggerView` 展示触发信息：上方以 0.65 倍字幅显示自然触发秒数（缺省 300），下方依次显示已启用的火焰、岩浆与仙人掌图标（每个 8 像素）；仅自然触发时图标区留空，非自然触发不显示时间，混合触发同时展示数字和图标。火焰与岩浆使用当前原版图集材质，仙人掌使用物品图标；空集合与未知触发使用占位图标并在悬停说明中注明。箭头按时间与图标的最大宽度量宽，命中与横向滚动沿用配方的同一份几何，悬停箭头单独说明完整触发方式和时间单位。变更清单由 `RuleChangesView` 提供滚动列表，名称及配方取当前展示草稿；删除标志不清空规则体，删除条目仍展示待删除内容，恢复条目沿用当前展示草稿并标记恢复类型。变更清单悬停可查看完整输入与产出说明；管理页规则条目悬停显示规则级条件树摘要，由 `ConditionTreeTextSummary` 以空格缩进表达组合层级，分组节点取「全部满足（%s 项）」/「任一满足（%s 项）」，取反节点取「非」，叶节点由 `NaturalSummary.conditionTreeLeaf` 给出「条件名（参数摘要）」。摘要随列表刷新缓存，行数按屏幕高度预留标题和状态空间，超长或超深树明确提示在触发页查看；无条件显示任意条件，未完成节点保留提示，标签悬停仍显示标签说明。
- 配方行扫描规则级与行为级条件中的正向催化剂、流体存在叶，取反叶不作为输入要求，双重取反恢复展示。流体列表优先于单个 `fluid` 引用，二者均空显示“任意非空流体”；流体不显示数量角标。流体消耗行为使用与催化剂相同的橙色星号，明确引用保留自身范围，任意流体消耗标记全部已列出的流体；符号表示配置了消耗，实际匹配与执行仍由条件和方案决定。
- 顶部按钮栏依次提供新建规则、编辑、启用或停用、复制、删除、更多；顶部与底部按钮均复用 `UiButton` 的主题胶囊样式，按当前文案宽度自动换行。底部提供应用更改与变更清单。
- 新建与复制共用命名弹窗，只填写规则 ID 的路径部分，命名空间固定为 `itemdespawntowhat`；弹窗仅提示名称支持小写字母、数字和下划线，输入过滤与提交校验采用同一字符限制。复制建议名将源路径的不支持字符替换为下划线，追加复制后缀并避开重复 ID。基本页的显示名由用户手动填写，未设置时输入框为空；列表及页头仍使用既有名称展示兜底。输入时校验格式及 ID 唯一性，与已有规则或尚未应用的新建规则重复时在弹窗内显示红字并禁用确认；校验失败保留弹窗与输入。
- 编辑、启用或停用、复制、删除、更多在未选中规则或工作区冻结时禁用；选择分类不算选中规则。
- 启用开关根据当前规则体的 `enabled` 显示下一步动作：省略或为 `true` 时显示“停用”，为 `false` 时显示“启用”。切换后标签随草稿状态更新。
- “更多”提供屏蔽或取消屏蔽、恢复原始；屏蔽开关根据当前规则体的 `delete` 显示下一步动作。删除使用顶部入口并保留确认弹窗。

## 11. 条件树画布页

条件卡片显示当前条件摘要，通过「编辑条件树」进入**整页画布页**（`ConditionTreeOverlay`，由 `UiModal` 承载）。`ConditionSupport.openTree` 由 `RuleEditorTriggerPage` 提供，范围来自表单的 `draftPath(conditions)`，规则级与行为局部共用同一条路径；条件编辑不要求整条规则已填完整。

**实现职责**：画布组件均位于 `client.ui.widget`；宿主仍通过 `UiConditionGraphEditor` 与其 `InlineEditor` 契约接入。

| 组件 | 当前职责 |
|---|---|
| `UiConditionGraphEditor` | 协调 Kit 树操作、选择、拖拽、键盘导航、分组菜单、历史操作标识与宿主回调；确认类型后创建并插入条件叶 |
| `ConditionGraphLayout` | 重建节点身份索引、路径反查、诊断、参数摘要与文本测量缓存；计算条件树和左右端点几何，提供节点及热区命中查询 |
| `ConditionGraphRenderer` | 读取布局与选择状态，绘制点阵、端点、卡片、实线与执行虚线、添加热区和拖拽反馈，不提交树修改 |
| `UiTypePicker` | 条件与行为共用的类型选择器，管理过滤、高亮、悬停、滚轮、滚动条捕获和键盘确认；通过类型 ID 回调交回宿主 |
| `ConditionGraphInlinePanel` | 管理参数表单装载、路径锚点校验、关闭动画、屏幕坐标布局与输入捕获；参数提交仍由宿主表单处理 |
| `ConditionGraphSidePanel` | 为类型选择器与参数面板共用右侧几何、160ms ease-out 动画、独立绘制层、背景、标题和边框 |

布局只在表达式、类型标签、参数摘要或端点摘要更新时重建；绘制读取缓存，不重新扫描诊断或计算树布局。节点与路径映射封装在布局组件内，树序与热区仅提供只读视图；路径锚点不按条件节点对象身份判断，参数提交产生新对象时不会误关表单。

**版面**：整页是条件树画布（`UiConditionGraphEditor`），四周是外壳——左上角水平图标功能栏（撤销 / 重做 / JSON 轮询 / 帮助）、左缘竖直工具列共 7 格（添加条件 / 分组 / 取反 / 删除 / 上移 / 下移 / 包组）、底部状态栏（错误、未完成输入、条件 / 叶 / 深度统计与缩放比例）。外壳分区紧贴画布，之间只用 1 像素分隔线划分，内容区外围描一圈 1 像素外框，外壳与承载它的模态面板同底不再各自成板；功能栏底板只铺到最后一个图标格之后，右侧空白交还画布，底板收窄不影响格子的悬停与点击命中。画布始终展示整棵树，「源物品」框位于所有条件左侧，「结果」框位于所有条件右侧，两个端点与根卡片水平对齐，子条件在根下方展开。源物品以实线向右连接根；真实父子关系以实线和向下箭头连接；根到结果的执行路径以向右虚线及线段上方的「整棵条件树满足后执行」文字表达，不从各叶节点汇线到结果。结果位置同时避让子树宽度与说明文字，适配视图包括两个端点、整棵树、虚线和说明文字。源物品与结果未填写时保留标题与空内容框；两个端点只读，不参与条件 JSON、结构操作或条件统计。源物品显示规则输入摘要，结果显示当前条件作用域所属的规则结果或行为摘要。空树在两端点之间保留添加热区，默认视图与适配视图覆盖整条链路。

**手势**：空白区左键拖拽 = 平移画布，**Shift + 拖拽 = 框选**，中键或空格 + 左键也是平移；滚轮以鼠标位置为锚点按 50 / 100 / 150 / 200 四档缩放；`Ctrl+0` 适配视图、`Ctrl+Home` 回到根。点阵网格随内容平移，步长随档位变化。键盘支持方向键导航、`Ctrl+A`、`Ctrl+C/V`、`Del` 与 `Esc` 取消当前手势；文本框聚焦时画布快捷键让位。

**添加条件**：点工具列「添加条件」打开**可搜索的类型列表**，面板从右侧滑入并吸附画布右边界。鼠标悬停切换高亮类型，左键或 Enter 确认后才生成默认条件叶，取消不修改草稿；方向键与 Home/End 切换高亮并将目标行带入可见范围，静止指针不覆盖当前高亮。列表行高为 18 GUI 像素，按可用高度裁剪；内容溢出时显示右侧滚动条，支持拖动滑块与点击轨道。列表上的滚轮只滚动内容，不改变高亮类型，也不缩放画布；筛选后重置滚动位置并显示当前高亮。选中容器时追加到该容器；选中叶时追加到最近的父容器；无选中节点时取最深叶所在容器，同深度取树序最后一叶。取反容器只有一个子节点，追加时在其子节点处以全部满足包组。无可用容器时以全部满足包住现有根和新叶，空树直接创建根叶。新增叶默认只显示卡片，不打开参数编辑框。整棵树为空时，两端点之间常驻一个可点的大 `[+]`，点击同样打开类型列表。「分组」与「包组」工具先在热区处（包组在选区附近）弹出 ∧ / ∨ 小菜单（全部满足 / 任一满足），选定后才插入容器或包裹选区。

**参数编辑**：首次单击条件叶只选中，双击或再次点击已单独选中的叶并在同一卡片释放时，参数编辑框从右侧滑入并吸附画布右边界，`Esc` 向右收起。类型列表与参数框共用最大宽度 220 GUI 像素、画布全高、约 160ms ease-out 动画及背景、标题和边框；表单按 GUI 屏幕坐标绘制与接收输入，不随画布平移或缩放。面板独立绘制层盖住树图，内容裁剪在面板内，面板上的点击和滚轮不穿透到画布。新增、选择同步与历史回填不自动打开参数；拖动叶节点或 Ctrl / Shift 多选不触发编辑。摘要叶节点跳转、键盘激活叶节点与参数问题定位也可打开编辑框。容器节点（全部满足 / 任一满足 / 取反）没有参数，不展开。表单由 `createLeafParameters` 装配，描述符、数量默认值、目录 / 建议、区间 / 时间条与存在条件关联写入复用既有机制，`ConditionTreeChrome.Inline` 适配 `LeafPanel` + `FormView`。

**草稿与历史**：树操作经 Kit 绑定回调，在同一事务中写当前条件 JSON、迁移所有结构共享节点的输入路径，并清除删除叶的输入；判断使用节点对象身份，内容相同的叶不按类型或 record 相等性混同。参数编辑只在字段完成时写一条配置历史；参数框切换节点前提交合法值，非法文本保留在 EditSession。`UiModal.onHistoryChanged` 回填当前条件并收起参数框；行为局部树遇到外层行为 / 方案结构恢复、归属类型序列变化或范围删除时关闭，只卸载旧视图，不提交旧参数到新的索引归属。正常返回的 `onClosed` 完成合法字段、保留非法文本并卸载控件。参数字段问题可打开画布页、定位到对应节点并展开右侧编辑框。模态键盘遵守文本优先与当前规则独立历史。

**只读 JSON**：功能栏的轮询按钮在树与只读 JSON 之间切换，默认树视图。JSON 只展示当前规则级或行为局部的 conditions 表达式，按缩进排版，可选择复制或整段复制；空表达式显示字段省略提示。非法文本留在会话缓冲，JSON 保留最近已接受值并显示提示。视图切换不新增配置历史，源码不接受直接修改。

**视觉**：画布页与参数框使用扁平皮肤（`UiFlatPalette` / `UiFlatSkin`），无立体凸凹与阴影。条件叶使用普通矩形；AND 使用蓝色圆角框、OR 使用绿色胶囊框、NOT 使用紫色切角框。卡片内部统一为居中标题带、1 像素分隔线与固定两行内容区：容器标题带显示运算符（`且` / `或` / `非`，容器语义只由运算符表达、内容行不再重复），内容首行为分支计数、次行为结构标记；条件叶标题带为类型名，内容为参数摘要与结构标记。描边常态为 1 像素卡片描边色，悬停未选中为 1 像素强调色，选中为 2 像素强调色，结构问题使用错误描边和文字，优先级为 选中 > 结构问题 > 悬停；强调色只作用于描边，不覆盖运算符盖色。卡片悬停提示由画布自绘面板绘制（底色与描边取浮层配色，层级高于画布内容与侧栏），不再交给原版 tooltip；功能栏、工具列与内联参数框字段仍用原版 tooltip。形状按连续扫描行合并绘制，布局与命中保持原矩形。其它编辑页继续使用 `UiPalette` / `UiTheme`。

## 12. 外层结构变化与保存回显

结果页的候选与行为重排保持目标最终下标、列表长度和所有 JSON 元素。`DraftListInputs` 在页面操作的同一 `EditSession.apply` 事务内按原 JSON 对象身份迁移候选 / 行为作用域输入：删除对象清除其缓冲，存活对象生成完整路径映射，包含当时没有缓冲的对象；映射进入历史，供操作之后新增输入的撤销与重做使用。不会按相同类型或相同 JSON 内容猜测对象身份。

顶层 effects 转成 `outcomes[0].effects` 时原效果数组与对象被保留，输入前缀同步迁移。输入页移除催化剂消耗行为引发的行为下标变化同样迁移。产物子类等同路径对象替换属于字段编辑，保留该作用域的输入，不当作结构删除。

主编辑器保存前检查所有脏规则的未完成输入与条件结构。服务端仍执行完整解码、语义和引用校验，校验拒绝不清除草稿；服务端字段问题先打开其 ruleId 所属规则，再按原字段路径定位，避免将其它规则或行为局部条件问题套到当前规则树。

`RuleSaveOverlay` 在工作区 APPLYING 时接管主屏输入，立即绘制“保存中...”与 `UiProgressBar`。确定总数时显示当前步骤完成计数与平滑动画，未知总数时显示往返区段；不展示估算的整体百分比或剩余时间。高级信息默认收起，展开显示本地化阶段、规则 id／相对文件名或整批范围。Tab、Enter 与 Space 可操作高级信息和取消按钮；关闭、Esc、返回及底层快捷键被拦截。取消按钮只请求终止，回滚期间禁用重复取消，最终 `CANCELLED` 保留草稿；提交确认后取消禁用。主屏继续不暂停单人服务端。

## 13. i18n 键分工

| 前缀 | 用途 | 例子 |
|---|---|---|
| `gui.itemdespawntowhat.edit.*` | 界面文本：标题、按钮、字段标签、枚举值、类型显示名 | `gui.itemdespawntowhat.edit.field.y_level.min` |
| `itemdespawntowhat.edit.*` | 协议回执与校验问题的消息码（服务端生成、客户端翻译） | `itemdespawntowhat.edit.status.lock_busy` |

字段标签 `gui.itemdespawntowhat.edit.field.<类型 path>.<字段>`、枚举值 `gui.itemdespawntowhat.edit.enum.<组>.<值>`、类型显示名 `gui.itemdespawntowhat.edit.<condition|effect>.<类型>`。`en_us.json` 与 `zh_cn.json` 的键集合必须完全一致，由 `python tools/check-i18n.py` 校验。协议回执状态码见 [编辑协议](../backend/modules/edit-protocol.md) §4.3。

## 14. 目录选择页样板

物品、标签、流体等注册表目录共用同一个选择页：页面控件是 `client/ui/widget/UiCatalogGrid`，弹窗由 `RuleEditorPanelFactory#catalogModal` 装配，黑名单入口 `SourceBlacklistPicker` 复用同一尺寸助手与同一套文案。

标签展示名统一由 `RuleDisplayLabels#tagLabel` 返回仅带一个前置 `#` 的完整标签名（例如 `#minecraft:logs`），选择目录、已选卡片、数量行与规则预览共用该名称；`TagPreviewIcons.Tag#label` 委托此方法。长名称按控件宽度裁剪，完整名称和匹配范围可悬停查看；成员图标轮播与展开浏览共用标签快照。

- 注册表字段的已选内容由 `CatalogSelectionControl` 显示为可逐项移除的卡片：物品、流体、方块、实体、维度、群系、战利品表和状态效果共用此控件；没有图标的目录按文字卡片布局。`RuleDisplayLabels.catalogLabel` 按确切目录类型取名称，缺少本地化或未知资源时保留原 ID，未编辑值原样写回。`FormView` 为有目录映射的 TAG / TAG_LIST / RESOURCE_LOCATION / REGISTRY_ID / RL_LIST 字段选择卡片控件，主编辑表单、条件叶参数与子表单均启用；自由字符串列表及无目录映射的字段保留文本输入。

- `UiCatalogGrid` 只知道「条目 + 图标 + 本地化文本」：条目、图标与全部文本由宿主注入，搜索、翻页与范围切换只发出意图回调；面板不校验也不解析注册表 id。
- 分页依据：目录由 `RuleEditClientWorkspace#directory` 按类型**一次拉全**（每请求 200 条，`lastPage` 为真即完成，按 id 去重合并；`refreshDirectory` 只重探第 0 页修订号，未变化即复用整份缓存）。选择页在本地做搜索过滤与切片：`total` = 命中条数，`pageCount` = `ceil(total / 200)`（`UiCatalogGrid.PAGE_SIZE`），翻页不产生网络请求。
- 底部行由 `UiModal#inlineFooter()` 与 `UiFooterHost` 托管：弹窗把按钮行算进内容区高度，页面让出右侧后把翻页（只画 `<`/`>`，完整名称进悬停说明）、页码、分组标签／总数／已选与「清空已选」排在按钮左边，不再为翻页单独占一行。
- 悬停提示只给条目信息：显示名与注册名各占一行，宿主附加说明（标签成员范围、实体预览不可用）另起一行；渲染前按 240 像素拆行——原版单个 Component 的 `renderTooltip` 重载不处理换行。
- 条目策略由宿主实现 `UiCatalogGrid.EntryPolicy` 注入：`CatalogEntryPolicy` 把物品与流体的默认排除写在同一处——物品排除空气与仅命令可得的技术性条目，流体只保留可放置的源流体（空流体与流动变体一律排除）；规则固定生效、不提供界面开关。宿主在**分页前**过滤，所以页码、总数与可见条目始终基于同一份事实。
- 同时存在物品与同注册表标签的目录页在搜索框右侧出现一个范围轮询按钮：显示当前范围的符号（`◆` 只物品 / `#` 只标签 / `≡` 一起），点击按该顺序切到下一个；符号代替文案，完整文案（含「点击切换」提示）只进悬停说明。范围由页面回写、宿主按 `EntryPolicy#scope()` 决定取物品还是标签，切换后映射缓存立即失效。
- 列表滚动条可拖动：按住滑块拖动即连续滚动，点击轨道即把滑块跳到该处；抓取范围比 4 像素的视觉宽度略宽，内容未超出可见行时不响应。
- 选择状态由页面持有：多选页再次点击即取消，单选页替换旧选择，`selection()` 按点击顺序返回 id 列表；确认按钮把该列表交给调用方的 `onPicked`。跨条目去重由调用方按 id 处理。
- 键盘与鼠标等价：方向键移动光标、Home/End 跳首尾、空格切换选择、Enter 激活、PageUp/PageDown 翻屏；滚轮一格滚动多行。列表高度小于一页时按可见行滚动，不改变宿主页码。
