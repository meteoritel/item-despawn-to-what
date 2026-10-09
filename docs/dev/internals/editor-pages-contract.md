# 编辑器四页职责与输入↔触发联动契约

> 状态：当前实现。相关规格：`docs/spec/editor-page-responsibilities-2026-10-07.md`；覆盖 `client/ui/screen/RuleEditorEditPages.java`、`client/ui/screen/RuleEditorScreen.java`、`client/ui/screen/RuleEditorP4Panels.java` 与 core 侧 `CatalystPresentCondition` / `CatalystThresholdProjection`。面向改编辑器的人：说明四页各管什么、输入页与触发页如何按**确切叶路径**关联、以及撤销粒度。
> 定位约定：本文按「类名 + 成员名」引用源码（如 `RuleEditorEditPages#buildInput`），不写行号——行号会随重构漂移，成员名才是稳定锚点。

## 1. 四页职责

| 页 | 构建入口 | 负责 | 不负责 |
|---|---|---|---|
| 基本页 INFO | `RuleEditorEditPages#buildInfo` | 规则身份与元信息：只读规则 ID、显示名、备注、启用开关、「恢复自动命名」按钮、优先级 | 条件、消耗、结果 |
| 输入页 INPUT | `RuleEditorEditPages#buildInput` | 来源与来源消耗、催化剂固定成本开关与表单、**存在条件（催化剂/流体）的唯一入口**、消耗参数主入口 | 组合结构、结果 |
| 触发页 TRIGGER | `RuleEditorEditPages#buildTrigger` | 触发原因勾选、延迟秒数、条件树（`UiConditionTreeEditor`）：叶的**结构**在这里增删/取反/组合 | 叶的**参数**（在叶弹窗里编辑） |
| 结果页 RESULTS | `RuleEditorEditPages#buildResults` | 组合方式、方案（outcomes）与动作、效果参数 | 条件树、固定成本 |

- 页枚举 `Page`、`switch (page)` 派发、版面与渲染入口、以及各页自己保存的滚动偏移表（`scrollOffsets`）都在 `RuleEditorEditPages` 内。
- 结果页分两层：**多方案**才有方案层（`singlePlan = candidates == 1`，`RuleEditorEditPages`）；宽屏左列直接是动作列表（`!singlePlan` 时跳过方案列表布局）；窄屏单方案阶段夹取到 `[1,2]`，多方案仍为 `[0,2]`（stage0 方案层 / stage1 返回方案）；单方案不生成「打开方案」按钮，`candidateList` 创建但不注册进 `liveWidgets`；方案按钮可见性由 `plans || (singlePlan && actions)` 控制；`combinationControl` 仅 `candidates>1` 创建。
- 结果页**不重复**消耗参数：过滤链配合 `isInputOwnedConsumptionField`（`consume_source`→`count`/`counts`；`consume_catalyst`→`items`/`count`/`counts`/`radius`；`consume_fluid`→`fluid`，**不过滤** `require_source`——输入页不管理它），并在首个消耗动作参数前插说明行 `effect.consume_params_on_input`；`chance` / `delay_ticks` / `conditions` 仍在结果页编辑。
- 术语：一个 outcome 一律称「**方案**」（`button.candidate_*` / `notice.candidate_*` / `undo.*_candidate` / `issue.candidate_*` / `rule.combination` 等文案已统一，`effect.consume_params_on_input` 为新增），只有页签名仍是「结果」（`tab.results`）；`candidate.implicit` 与 `structure.convert_hint` 已删除。
- 初始页：新建**空白**规则落在输入页（`RuleEditorScreen#createRule` 在 `templateId == null` 时切换页签到 `input`）；从模板新建或打开已有规则落在基本页（`RuleEditorScreen#openEditor` 置 `tab = INFO`，字段 `tab` 的默认值见该类字段声明）。
- 切页前先提交有效编辑：`RuleEditorScreen#switchTab` 走 `pages.blockNavigation()` 门禁后 `pages.setPage(target)`；提交路径见第 5、7 节。

### 条件树草稿与正式配置

- `RuleDraft` 的条件树读写通过客户端 `ConditionDraftCodec`：空 `all_of` / `any_of` 保留空 `terms`，缺项 `inverted` 保留 `op` 并省略 `term`；重新打开表单仍可继续编辑。叶参数仍交给既有正式类型 Codec，旧形状与未识别数据不被静默迁移。
- `UiConditionTreeEditor` 在空树和空组合内部显示“＋ 添加条件”；删除 NOT 唯一子项后保留 NOT，并可从内部入口补齐。添加占位不是条件节点，不计入节点数、叶数和深度限额。
- 无根表达式继续省略 `conditions`，表示无条件限制；空组合和缺项 NOT 是未完成草稿，表单及规则保存前的结构校验均拦截。服务端的正式 `RuleCodecs` 和运行时求值保持既有严格契约。
- 条件叶行复用 `NaturalSummary.conditionLeaf` 展示类型和已接受参数；摘要仅用于显示，不写入配置。截断行可悬停查看全文，叶参数确认并重载表单后同步更新。
- `UiConditionTreeEditor` 的完整树扫描独立于可见行投影，节点数、叶数、深度和结构问题不受折叠影响。树实例或摘要格式器变化才重新扫描，布局和折叠复用结果；错误子树的祖先行使用错误颜色。
- `FormView.revealPath` 将树内的节点或参数字段路径交给条件控件；定位展开祖先、选中并滚动到节点。表单问题保留具体树路径及带实际限额的提示，正式保存拦截不依赖展开状态。

## 2. 存在条件：唯一入口在输入页

- 两个开关：催化剂 / 流体，标签 `rule.presence.add_catalyst`（「启用催化剂」）/ `rule.presence.add_fluid`（「启用流体条件」）。催化剂卡片按「启用 → 物品选择与最低触发数量 → 消耗开关 → 每轮消耗数量」排列；物品与流体选择复用目录及卡片控件，卡片内可逐项移除引用。
- 勾选 = 在**规则级条件**里新增一个存在条件叶（`RuleEditorEditPages#addRuleLevelPresence`）：条件为空树时直接写入叶；根已是 `all_of` 时把叶追加进 `terms`；否则把**完整旧根**与新叶一起放进新的 `all_of(旧根, 叶)`——保留旧根整体语义，不把叶插进当前选中的节点。
- 输入页启用催化剂创建 `{op: leaf, condition: {type: …, items: [], count: 1}}`，每个引用的门槛从 1 开始，由 `counts` 对象分别编辑。触发页新建催化剂叶仍省略 `count`，保留运行期默认门槛语义；打开已有省略门槛的叶只显示有效值，不自动写回。
- 存在条件类型字段的 basePath 就是该叶的 condition 路径：`newForm(session, leaf.conditionPath(), presenceDescriptor(leaf))`；字段路径 `fieldPath() = conditionPath + ".items"`（催化剂）或 `+ ".fluids"`（流体列表，任一种命中即可）。存在叶的物品选择同步关联的消耗配置，后者仍保留运行所需的 `items`。
- 未勾选时不渲染类型字段（`RuleEditorEditPages#buildPresenceSection` 的行为）。
- 单个催化剂或流体叶不重复显示编号、作用域与整叶移除按钮；多个同类型叶保留标题和整叶移除入口。卡片上的移除只删除该引用；整叶移除只删除对应的条件叶。流体引用留空时显示「任意非空流体」，沿用后端的任意流体语义。
- 流体目录、选中卡片及标签成员共用 `FluidPreviewIcons`：从流体对应方块的模型图集取得静态材质和方块染色，绘制有明暗面的立体方块图标；流水等非源流体名称带「流动」标识。目录与文本候选过滤注册表中的空流体，流体标签预览也过滤空成员。材质每帧从当前图集读取，不保留资源重载前的 sprite。
- 取消勾选 = 删除规则级里该类型的全部存在叶（`RuleEditorEditPages#removeRuleLevelPresence`，逐个删并整理空父组；删除未生效即停手，防死循环）。
- 关闭「启用催化剂」会一并删除规则级 `catalyst_cost` 及规则中的 `consume_catalyst` 效果，关闭「消耗催化剂」只删除消耗配置并保留存在条件。流体存在开关仍不影响独立移除动作。每次开关操作与其关联删除共用一条撤销记录。
- 消耗参数主入口也在输入页：来源消耗（`sourceForm` / `sourceCostForm`）、催化剂消耗开关与表单（`catalystToggle` / `catalystForm`）。选定催化剂物品后才能首次开启消耗；开启时每轮数量初始化为当前门槛，随后可独立调整。关闭消耗前会固定省略门槛的当前有效值，避免删除消耗配置后门槛变化。
- 催化剂消耗区只显示每轮数量，不重复选择物品、不显示搜索半径。新建固定成本省略半径，由后端默认值 1 处理；已有显式半径原样保留。没有规则级存在叶的已有独立消耗规则仍显示其物品选择入口。
- 已有消耗效果的数量表单直接绑定 `RuleCostBinding.Ref` 的原效果路径，不生成额外固定成本；催化剂消耗开关可关闭这类效果，来源消耗模式仍锁定为原效果。

## 3. 同一份叶扫描：路径、编号与作用域

`RuleEditorEditPages#scanPresenceLeaves` 用同一条递归（`collectPresence`，路径语法与草稿一致：`terms[i]` / `term` / `condition`）扫描三条线，返回全量存在条件叶并**按固定顺序编号**：

| 顺序 | 扫描位置 | 作用域文案 |
|---|---|---|
| 1 | 规则级 `conditions` | `rule.presence.scope_rule`（「规则级」） |
| 2 | 动作局部 `effects[i].conditions` | `rule.presence.scope_action`（「动作 %s」） |
| 3 | 方案内动作 `outcomes[j].effects[k].conditions` | `rule.presence.scope_effect`（「方案 %s · 动作 %s」） |

- 编号按类型各自递增（`counters.merge(type, 1, Integer::sum)`）；标题行 = `rule.presence.title`（「类型 编号 · 作用域」，由 `presenceHeader` 生成）。
- 输入页与触发页、叶弹窗标题**共用同一份扫描结果**（`ruleLevelPresence`、`findPresenceLeaf`），因此同一类型第 N 项在两侧是同一个编号。

## 4. 输入↔触发按确切叶路径关联（契约）

**契约**：输入页与触发页之间不存在类型副本，也不按「类型相同」匹配；两侧都按**确切叶路径**指向同一份草稿 JSON。

1. 输入页只呈现**规则级**叶（`ruleLevelPresence` 过滤 `leaf.ruleLevel()`）；动作局部叶不在此列出，只在触发页条件树里编辑。
2. 触发页**不新增类型副本**：`buildTrigger` 只建触发原因勾选框、延迟表单与条件树；叶结构未变，既有 `UiConditionTreeEditor` 直接呈现，叶的增删/取反/组合操作不变。
3. 叶参数编辑入口：`pageConditionSupport(owner)` → `openLeafEditor(owner, leaf)`。弹窗以**发起编辑的那个表单**为 owner：`editConditionLeaf` 在 `liveForms` 里用 `findTree(form, leaf)` 找到承载该叶的表单，路径取该树的 `selectedPath()`。同名同类型的多个叶各绑各的路径，**不会误绑**。
4. 弹窗标题带类型编号与作用域；催化剂叶显示所有引用均需达标的说明，流体叶显示任一种即可匹配的说明。
5. 确认时只写回该叶自己的路径：`form.applyToDraft()` 后 `owner.reload()` 刷新发起编辑的表单，再由 `host.onDraftChanged()` 通知宿主。逐项编辑**不改变组合结构**，也**不会把动作局部条件提升到规则级**。
6. 实现细节：叶弹窗内的目录选择、区间条与事件转发由 `RuleEditorP4Panels#leafPanel(...)` 承载（`basePath` 即叶参数在草稿中的路径）；门槛说明行经 `LeafPanel#addNote` 注入；校验失败时 `focusField(issue.path())` 把焦点定位到问题字段。

## 5. 物品与数量联动

- 字段写入统一走 `FormView.writeField`，引用改写、数量对象同步和关联消耗配置重指向共用一条撤销记录。存在叶缓冲提交先核对确切叶路径，再调用同一表单写入流程。
- 源物品保留替代匹配语义。界面只提供「指定每轮消耗」按钮；开启后 `source_cost` 按当前引用写入数量 1，每个引用有独立滑条与精确输入。已有 `consume_source` 时编辑其 `counts`，避免同时生成固定成本。
- 催化剂各引用均需满足各自门槛。`counts` 以引用为键，新增引用采用默认数量，移除引用同步移除对应数量，保留仍选中的值。固定催化剂成本使用规则级存在叶的引用并集；已有独立消耗动作只在 items 为空或与原集合相同时同步。
- 开启「消耗催化剂」时，按引用复制当前门槛为每轮成本；同一引用存在多个规则级门槛时采用最大值。随后门槛和成本可各自调整；每轮全部引用付得起才扣除。
- 流体列表多选采用或关系；写入 `fluids` 时移除旧的单值 `fluid`，清空列表表示任意流体。选成单个流体时，类型为空或等于原单值的 `consume_fluid` 跟随新单值；多选不自动选择某个移除目标。
- 类型表单在连续目录回调期间保持实例，避免后续选择写进已卸载控件。数量行刷新不丢弃其它字段未提交的输入。

## 6. 数量展示与运行期同源

- `ItemCountsControl` 复用 `NumberControl`，按引用显示物品名称、数量滑条与精确输入；输入页和条件叶弹窗共用该控件。
- 催化剂门槛统一称「最低触发数量」，消耗区称「每轮消耗数量」；GUI 搜索半径使用默认 1，不提供自定义入口。
- 显式 `counts` 优先，缺省引用采用显式 `count`；门槛均缺省时，`quantityDefaults` 解码完整草稿并调用 `CatalystThresholdProjection.resolveThreshold` 按引用展示有效门槛。未编辑时保留原字段缺失状态。
- 草稿未能解码时展示显式 `count` 或默认 1；作用域按确切条件路径解析，不把不同候选结果或效果的消耗参数混用。
- 运行期投影见 [催化剂门槛运行投影](../backend/systems/catalyst-threshold-projection.md)。

## 7. 撤销与保存语义

- 所有草稿修改必须走 `EditSession.apply(opKey, change)`：记录前深拷贝快照 → 只有真变化（含删除标记与目标级标记）才压栈 → 清空重做栈。撤销/重做是整份草稿快照置换；历史上限 100（`EditSession.HISTORY_LIMIT`）。
- 因此「勾选/取消存在条件」「逐项移除一个叶」「类型改写 + 重指向」各自是**一条**撤销记录。
- 撤销粒度是**字段级**：`FormView.applyToDraft()` 逐字段比较，每个真实变化的字段单独 `session.apply`。所以叶弹窗里改了多个字段 → 需要多次撤销；**弹窗确认本身不是一条记录**。
- 空父组自下而上整理（`removeLeafAt`、`isEmptyGroup` 均在 `RuleEditorEditPages`）：`all_of` / `any_of` 看 `terms`、`inverted` 看 `term`；空组连父一起删，但**不改变 `any_of` / `inverted` 的结构语义**。
- 保存走既有编辑保存协议，见 [edit-save-protocol](../backend/flows/edit-save-protocol.md)。
- 新建、从模板新建及复制成功后清空操作提示，不显示「已创建规则」；错误与应用结果仍由原有提示区显示。

## 8. 已知限制

- 条件树**叶行本身不显示编号**：编号只在输入页存在条件标题行与叶弹窗标题（`UiConditionTreeEditor` 内没有 presence / ordinal / scope 相关代码）。
- 输入页只列**规则级**存在叶；动作局部存在叶只能在触发页的条件树里逐叶进入弹窗编辑。
- 显示名/备注一次性粘贴超过「2×码点上限」的纯补充平面字符（如 emoji）时，可能被原版 `EditBox` 的 UTF-16 单元上限静默截断到恰好上限而不弹超限提示；逐字符键入与常规粘贴不受影响（上限判定本身仍按码点，见 [ui-kit-api](ui-kit-api.md)）。
- 流体多选不会自动改写独立移除动作的目标；单选只重指向未指定类型或与原单值相同的动作。

## 9. 相关文档

- 行为规范：[编辑器四页职责（spec）](../../spec/editor-page-responsibilities-2026-10-07.md)
- 门槛运行期：[催化剂门槛运行投影](../backend/systems/catalyst-threshold-projection.md)
- 类型与字段：[类型系统](../backend/modules/type-system.md)
- UI kit 契约：[ui-kit-api](ui-kit-api.md)
- 布局与视觉规格：[编辑页卡片布局](editor-card-layout.md)

## 10. 管理页布局与条目操作

- 管理页不提供独立的选中规则预览面板或展开/收起预览按钮，规则树使用内容区的完整宽度；配方行图标与悬停说明仍在树内显示。
- 顶部按钮栏依次提供新建规则、从模板创建、编辑、启用或停用、复制、删除、更多；按钮按当前文案宽度自动换行。底部提供应用更改与变更清单。
- 新建与从模板创建的命名弹窗只填写规则 ID 的路径部分，命名空间固定为 `itemdespawntowhat` 并只读展示；名字支持小写字母、数字及 `_ - . /`，创建后的显示名可使用中文。输入时校验格式及 ID 唯一性，与已有规则或尚未应用的新建规则重复时在弹窗内显示红字并禁用确认；校验失败保留弹窗与输入。
- 编辑、启用或停用、复制、删除、更多在未选中规则或工作区冻结时禁用；选择分类不算选中规则。
- 启用开关根据当前规则体的 `enabled` 显示下一步动作：省略或为 `true` 时显示“停用”，为 `false` 时显示“启用”。切换后标签随草稿状态更新。
- “更多”提供屏蔽或取消屏蔽、恢复原始；屏蔽开关根据当前规则体的 `delete` 显示下一步动作。删除使用顶部入口并保留确认弹窗。
