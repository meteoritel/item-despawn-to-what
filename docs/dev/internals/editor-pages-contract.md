# 编辑器四页职责与输入↔触发联动契约

> 状态：当前实现（2026-10-08，阶段 2 GUI 已落地并通过串行构建）。行为依据 `docs/spec/editor-page-responsibilities-2026-10-07.md`；覆盖 `client/ui/screen/RuleEditorEditPages.java`、`client/ui/screen/RuleEditorScreen.java`、`client/ui/screen/RuleEditorP4Panels.java` 与 core 侧 `CatalystPresentCondition` / `CatalystThresholdProjection`。面向改编辑器的人：说明四页各管什么、输入页与触发页如何按**确切叶路径**关联、以及撤销粒度。

## 1. 四页职责

| 页 | 构建入口 | 负责 | 不负责 |
|---|---|---|---|
| 基本页 INFO | `buildInfo`（`RuleEditorEditPages.java:449`） | 规则身份与元信息：只读规则 ID、显示名、备注、启用开关、「恢复自动命名」按钮、优先级 | 条件、消耗、结果 |
| 输入页 INPUT | `buildInput`（`:497`） | 来源与来源消耗、催化剂固定成本开关与表单、**存在条件（催化剂/流体）的唯一入口**、消耗参数主入口 | 组合结构、结果 |
| 触发页 TRIGGER | `buildTrigger`（`:1183`） | 触发原因勾选、延迟秒数、条件树（`UiConditionTreeEditor`）：叶的**结构**在这里增删/取反/组合 | 叶的**参数**（在叶弹窗里编辑） |
| 结果页 RESULTS | `buildResults`（`:1358`） | 组合方式、方案（outcomes）与动作、效果参数 | 条件树、固定成本 |

- 页枚举 `Page`（`:87`）、派发 `switch (page)`（`:360-364`）、版面 `:1878-1882`、渲染 `:2162-2166`；页面各自保存滚动偏移（`:212` `scrollOffsets`）。
- 结果页分两层：**多方案**才有方案层（`singlePlan = candidates == 1`，`RuleEditorEditPages.java:1418`）；宽屏左列直接是动作列表（`:2122` `if (!singlePlan)` 跳过 `layoutPlans`）；窄屏单方案阶段夹取到 `[1,2]`（`:2080`），多方案仍为 `[0,2]`（stage0 方案层 / stage1 返回方案）；单方案不生成「打开方案」按钮（`:1459`），`candidateList` 创建但不注册进 `liveWidgets`（`:1451-1453`）；方案按钮可见性由 `:2101` 控制为 `plans || (singlePlan && actions)`；`combinationControl` 仅 `candidates>1` 创建。
- 结果页**不重复**消耗参数：过滤链 `:1538-1547` 配合 `isInputOwnedConsumptionField`（`:2061-2075`，`consume_source`→`count`；`consume_catalyst`→`items`/`count`/`radius`；`consume_fluid`→`fluid`，**不过滤** `require_source`——输入页不管理它），并在首个消耗动作参数前插说明行 `effect.consume_params_on_input`（`:1545`）；`chance` / `delay_ticks` / `conditions` 仍在结果页编辑。
- 术语：一个 outcome 一律称「**方案**」（`button.candidate_*` / `notice.candidate_*` / `undo.*_candidate` / `issue.candidate_*` / `rule.combination` 等文案已统一，`effect.consume_params_on_input` 为新增），只有页签名仍是「结果」（`tab.results`）；`candidate.implicit` 与 `structure.convert_hint` 已删除。
- 初始页：新建**空白**规则落在输入页（`RuleEditorScreen.createRule:1014`，`templateId == null` 时切到 `input`）；从模板新建或打开已有规则落在基本页（`openEditor:932` 置 `tab = INFO`，字段 `tab` 默认值见 `:109`）。
- 切页前先提交有效编辑：`RuleEditorScreen.switchTab:333-349` 走 `pages.blockNavigation()` 门禁后 `pages.setPage(target)`；提交路径见第 5、7 节。

## 2. 存在条件：唯一入口在输入页

- 两个开关：催化剂 / 流体，标签 `rule.presence.add_catalyst` / `rule.presence.add_fluid`（`:540-541` 调用 `buildPresenceSection`，实现 `:600-616`）。
- 勾选 = 在**规则级条件**里新增一个存在条件叶（`addRuleLevelPresence` `:673-697`）：条件为空树时直接写入叶；根已是 `all_of` 时把叶追加进 `terms`；否则把**完整旧根**与新叶一起放进新的 `all_of(旧根, 叶)`——保留旧根整体语义，不把叶插进当前选中的节点。
- 新叶 JSON：`{op: leaf, condition: {type: …, items: []}}`；催化剂叶**不预写 `count`**（`presenceLeafJson` `:757-767`，注释明确「不预写 count=1」）。通用条件工厂会给催化剂预写 `count=1`，所以这里改走同一份 JSON 解析（`presenceLeafNode` `:770-776`）。
- 类型字段的 basePath 就是**该叶的 condition 路径**：`newForm(session, leaf.conditionPath(), presenceDescriptor(leaf))`（`:620`）；字段路径 `fieldPath() = conditionPath + ".items"`（催化剂）或 `+ ".fluid"`（流体）（`PresenceLeaf` 记录 `:235-257`）。**类型不再存在 `catalyst_cost.items` 里**。
- 未勾选时不渲染类型字段（`:539-541` 与 `buildPresenceSection` 行为）。
- 逐项移除按钮挂在类型行尾（`rule.presence.remove`，`:633-634`）；删除只作用于**这一个叶**（`:663-670`：`removeLeafAt(draft, leaf.path())`）。
- 取消勾选 = 删除规则级里该类型的全部存在叶（`removeRuleLevelPresence` `:700-717`，逐个删并整理空父组；删除未生效即停手，防死循环）。
- **独立消耗配置完全不受存在条件开关影响**：规则级 `catalyst_cost`、方案内 `consume_catalyst`、`consume_fluid` 都不随勾选/取消被删改。
- 消耗参数主入口也在输入页：来源消耗（`sourceForm` / `sourceCostForm`，`:499-521`）、催化剂成本开关与表单（`catalystToggle` / `catalystForm`，`:523-538`）。当来源或催化剂由动作内消耗效果承载时，表单直接绑定该效果路径（`RuleCostBinding.Ref`）并把开关置灰，避免两处编辑同一份数据。

## 3. 同一份叶扫描：路径、编号与作用域

`scanPresenceLeaves`（`:779-827`）用同一条递归（`collectPresence` `:830-865`，路径语法与草稿一致：`terms[i]` / `term` / `condition`）扫描三条线，返回全量存在条件叶并**按固定顺序编号**：

| 顺序 | 扫描位置 | 作用域文案 |
|---|---|---|
| 1 | 规则级 `conditions` | `rule.presence.scope_rule`（「规则级」） |
| 2 | 动作局部 `effects[i].conditions` | `rule.presence.scope_action`（「动作 %s」） |
| 3 | 方案内动作 `outcomes[j].effects[k].conditions` | `rule.presence.scope_effect`（「方案 %s · 动作 %s」） |

- 编号按类型各自递增（`counters.merge(type, 1, Integer::sum)`，`:846`）；标题行 = `rule.presence.title`（「类型 编号 · 作用域」，`presenceHeader` `:1011-1014`）。
- 输入页与触发页、叶弹窗标题**共用同一份扫描结果**（`ruleLevelPresence` `:868-876`、`findPresenceLeaf` `:884-891`），因此同一类型第 N 项在两侧是同一个编号。

## 4. 输入↔触发按确切叶路径关联（契约）

**契约**：输入页与触发页之间不存在类型副本，也不按「类型相同」匹配；两侧都按**确切叶路径**指向同一份草稿 JSON。

1. 输入页只呈现**规则级**叶（`ruleLevelPresence` 过滤 `leaf.ruleLevel()`）；动作局部叶不在此列出，只在触发页条件树里编辑。
2. 触发页**不新增类型副本**：`buildTrigger`（`:1183-1204`）只建触发原因勾选框、延迟表单与条件树；叶结构未变，既有 `UiConditionTreeEditor` 直接呈现，叶的增删/取反/组合操作不变。
3. 叶参数编辑入口：`pageConditionSupport(owner)`（`:1227-1244`）→ `openLeafEditor(owner, leaf)`（`:1264-1339`）。弹窗以**发起编辑的那个表单**为 owner：`editConditionLeaf` 在 `liveForms` 里用 `findTree(form, leaf)` 找到承载该叶的表单（`:1255-1262`），路径取该树的 `selectedPath()`（`:1270-1272`）。同名同类型的多个叶各绑各的路径，**不会误绑**。
4. 弹窗标题带类型编号与作用域（`:1300-1303`）；只有催化剂叶额外加「默认：N」说明行（`:1311-1314`，见第 6 节）。
5. 确认时只写回该叶自己的路径：`form.applyToDraft()`（`:1331`）后 `owner.reload()` 刷新发起编辑的表单，再由 `host.onDraftChanged()` 通知宿主。逐项编辑**不改变组合结构**，也**不会把动作局部条件提升到规则级**。
6. 实现细节：叶弹窗内的目录选择、区间条与事件转发由 `RuleEditorP4Panels.leafPanel(...)`（`RuleEditorP4Panels.java:516`，`basePath` 即叶参数在草稿中的路径，`:508`）承载；门槛说明行经 `LeafPanel.addNote`（`:710`）注入；校验失败时 `focusField(issue.path())`（`:962`）把焦点定位到问题字段。

## 5. 类型改写与关联消耗配置重指向

- 催化剂类型（items）改写**立即落盘**，且与「关联消耗配置重指向」合并为**同一次可撤销操作**：`flushPresenceType`（`:924-954`）。写回前先校验路径仍指向**同类叶**（`:930-939`），否则不写回——防止复活已被条件树删除的叶。
- 重指向规则（`retargetCatalystItems` `:957-1003`）：只重写「items 为空」或「与旧集合相同」的 `catalyst_cost` 与动作内 `consume_catalyst`；集合语义、顺序无关（`referencesDiffer` `:1006-1008`）。类型被清空（`after` 为空）属中间态，**不连带清空消耗配置**（`:958-961`）。
- 流体类型走常规提交（`flushPresenceType` 对流体直接返回 `false`，`:925-928`）：流体没有需要同步重指向的消耗配置，这是有意简化。
- 类型缓冲不会漏落盘：`applyToDraft()` 第一件事就是 `flushPresenceTypes()`（`:2287-2298`），否则类型改写会被普通提交流程拆成两条记录。
- 类型表单**不在改类型后重建页面**（`:621-622` 注释）：目录选择可连续多次回调，重建会让后续选择写进已卸载的控件。

## 6. 有效门槛与运行期同源

- 门槛字段对外统一叫「最低触发数量」（`rule.catalyst_min_count`）：叶弹窗里把原字段重新贴标签（`relabelCatalystThreshold` `:1036-1042`、`relabelCatalystCountField` `:1045-1049`）。
- 留空时才显示「默认：N」（`rule.catalyst_min_count_effective`）：`thresholdNote`（`:1052-1058`）仅当 `form.pendingValue(countPath)` 为空才返回文案；**显式填写后不再显示**。
- N 与运行期**完全同源**：`effectiveThreshold`（`:1062-1076`）把整条草稿用 `RuleCodecs.codec(ClientTypeRegistries.effects(), ClientTypeRegistries.conditions())` 解码成 `Rule`，再调用 `core/runtime/CatalystThresholdProjection.resolveThreshold(rule, taggedItems(conditionPath), scopeEffectOf(rule, conditionPath))`。
- 草稿尚不完整（解码失败）才退化：`fallbackThreshold`（`:1119-1143`）取同作用域消耗配置的 `count`（集合匹配时才采用），否则默认门槛 1（`CatalystPresentCondition.DEFAULT_COUNT`）。
- 作用域对齐：`scopePathOf`（`:1092-1095`）从叶路径截出承载对象（规则级 → `catalyst_cost`；动作局部 → 该动作路径），`scopeEffectOf`（`:1098-1116`）据此在解码后的 `Rule` 里取出对应 `Effect`（含 `outcomes[j].effects[k]`）。
- 运行期语义（可选声明、投影规则、失效入口）见 [催化剂门槛运行投影](<../backend/systems/catalyst-threshold-projection.md>)。

## 7. 撤销与保存语义

- 所有草稿修改必须走 `EditSession.apply(opKey, change)`（`edit/EditSession.java:96-111`）：记录前深拷贝快照 → 只有真变化（含删除标记与目标级标记）才压栈 → 清空重做栈。撤销/重做是整份草稿快照置换（`:161-184`），历史上限 100（`HISTORY_LIMIT` `:24`）。
- 因此「勾选/取消存在条件」「逐项移除一个叶」「类型改写 + 重指向」各自是**一条**撤销记录（`:653-659`、`:669`、`:949-952`）。
- 撤销粒度是**字段级**：`FormView.applyToDraft()`（`ui/screen/form/FormView.java:274-297`）逐字段比较，每个真实变化的字段单独 `session.apply`。所以叶弹窗里改了多个字段 → 需要多次撤销；**弹窗确认本身不是一条记录**。
- 空父组自下而上整理（`removeLeafAt` `:720-734`、`isEmptyGroup` `:743-754`）：`all_of` / `any_of` 看 `terms`、`inverted` 看 `term`；空组连父一起删，但**不改变 `any_of` / `inverted` 的结构语义**。
- 保存走既有编辑保存协议，见 [edit-save-protocol](<../backend/flows/edit-save-protocol.md>)。

## 8. 已知限制

- 条件树**叶行本身不显示编号**：编号只在输入页存在条件标题行与叶弹窗标题（`UiConditionTreeEditor` 内没有 presence / ordinal / scope 相关代码）。
- 输入页只列**规则级**存在叶；动作局部存在叶只能在触发页的条件树里逐叶进入弹窗编辑。
- 显示名/备注一次性粘贴超过「2×码点上限」的纯补充平面字符（如 emoji）时，可能被原版 `EditBox` 的 UTF-16 单元上限静默截断到恰好上限而不弹超限提示；逐字符键入与常规粘贴不受影响（上限判定本身仍按码点，见 [ui-kit-api](<ui-kit-api.md>)）。
- 流体类型改写只重指向 `items`/`fluid` 为空或与旧集合相同的 `consume_fluid`（与催化剂同一规则，刻意不动不同的独立配置）。

## 9. 相关文档

- 行为规范：[编辑器四页职责（spec）](<../../spec/editor-page-responsibilities-2026-10-07.md>)
- 门槛运行期：[催化剂门槛运行投影](<../backend/systems/catalyst-threshold-projection.md>)
- 类型与字段：[类型系统](<../backend/modules/type-system.md>)
- UI kit 契约：[ui-kit-api](<ui-kit-api.md>)
- 玩家人工验收：[编辑器四页人工验收清单](<../../guide/editor-page-acceptance-2026-10-07.md>)
