# P6 复核报告：草稿持久化 / 撤销重做 / 统一应用

- 复核人：session-dev（task-15，只读复核；未修改任何 Java / JSON / lang 文件）
- 快照时间：2026-10-03 01:26（git HEAD `d56132c`）
- 复核对象：kit-dev 的 task-9（P6）交付 —— `client/edit/draft/**`、`client/edit/EditSession.java`、`client/edit/RuleEditorModel.java`、`client/ui/screen/RuleEditorScreen.java`（1947 行）、lang（各 632 键）
- 对照基线：`docs/plan/plan-frontend-rewrite-contract.md` §5.3 / §6、`docs/review/p5-editor-review.md`（快照 2026-10-03 01:05，当时 RuleEditorScreen 1890 行）、`docs/guide/manual-acceptance.md`
- 构建复跑：`powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"` → **BUILD SUCCESSFUL（exit=0）**
- 行号均为上述快照下的行号，定位以**方法名**为准（文件仍在演进时行号会漂移）。

## 结论摘要

10 条必查项中 **9 条通过（含 2 条带保留意见）**，发现 **11 个缺陷/风险：高 1、中 4、低 6**。整体判断：**P6 的核心机制（草稿落盘隔离、恢复/冲突判定、撤销重做、单一变更集、服务端权威性、门面纪律）实现正确、无绕过服务端锁与校验的捷径**；但存在 **1 个阻断级集成缺陷**——快照到达不会驱动界面刷新，导致首次打开编辑器时规则列表为空、磁盘草稿恢复与冲突弹窗都不会触发；该缺陷同时解释了「恢复提示」与「列表可用」两项在实机上不可用。此外 APPLYING 冻结只覆盖表单区、未覆盖列表页与效果条按钮，存在静默不一致风险。

结论口径：**F1 已修复且修复正确**（规则级 + 每个 `effects[i].conditions`，路径语义与 `RuleDraft.rawConditionsAt` 一致，单叶/缺省 conditions 不误杀）；**F3 已正确接线**（每帧调用、弹窗/选择器时跳过、无 NPE）；**F2 部分修复**（保存前通知已含字段名 + 问题文本，字段级问题可通过悬停提示定位；仍无「跳转/聚焦到出错字段」）。

## 逐条核对表

| # | 必查项 | 结论 | 关键证据 |
|---|---|---|---|
| 1 | 落盘位置与隔离、文件名安全 | 通过（附 1 个低风险） | `client/edit/draft/DraftStore.java:30` 目录 `config/itemdespawntowhat/client/editor-drafts`，与覆盖层 `config/itemdespawntowhat/rules/` 不重叠（`OverlayRuleReader` 只扫 `rules/` 下 `.json`）；`resolve(:142-144)` 无路径穿越；`fileName(:126-140)` 只保留 `[A-Za-z0-9._-]`。风险：`:` 与 `/` 都归一成 `_`，`idtw:foo/bar` 与 `idtw:foo_bar` 会落到同一文件（见 F-H） |
| 2 | 原子性与失败处理 | 部分通过 | 磁盘失败只 `Constants.LOG.warn` + `return false`、不崩溃、不丢内存草稿（`DraftStore.java:52-62`）；节流 2000ms（`DraftJournal.java:18`）；关闭前强制落盘（`RuleEditorScreen.java:1854-1860` `onClose` → `model.persistNow()`）。不足：**无 tmp+move 原子替换**（F-F）、**写盘失败不再重试**（F-G）、**应用前未强制落盘**（F-I）、主线程同步 I/O（F-K） |
| 3 | 重启恢复与冲突 | 通过（判定逻辑正确） | 三态判定 `RuleEditorModel.java:280-318`：`created && serverBody!=null`→TARGET_EXISTS(:295)；`!created && serverBody==null`→TARGET_MISSING(:297)；`baseline!=null && !baseline.equals(serverBody)`→REMOTE_CHANGED(:299)（**整条规则 equals**，符合要求）；`keepDraft`→`captureBaseline`+`journal.writeNow`(:411-446)、`use_server`→`journal.drop`+重载会话，两条路径均由用户显式选择；`discardAllDrafts(:449-455)` 后 `persistNow` 直接返回(:376-387)，不会把放弃的草稿写回。风险：缺 `baseline` 的旧文件不判冲突（F-C） |
| 4 | 撤销/重做 | 通过（附 1 个低风险） | `EditSession.java:24` `HISTORY_LIMIT=100`；`Entry(opKey, snapshot, deleted, flag)`(:56) 用 `deepCopy`(:91-93)；变化判定含 `deletedBefore` 与 `flagBefore`(:97-100) → 删除标记与「恢复原始版本」标记可撤销；新编辑 `redoStack.clear()`(:104)；`replaceWith` 深拷贝(:271-273) 无别名污染；undo/redo 末尾均 `notifyListeners()`(:164/:177) → 脏标记与按钮态刷新。低风险：9 个 `OP_*` 常量无调用点，条件树编辑统一记 `OP_SET_FIELD`，标签退化为「编辑字段」（F-J） |
| 5 | 统一应用与冻结 | **部分通过（F-B）** | 单一变更集：`RuleEditorModel.buildChangeSetJson(:228-249)` 只 `new EditorChangeSet(workspace.contextRevision())`；APPLYING 冻结入口在 `RuleEditorScreen.java:1710` `editable = workspace.active()`（`EditorWorkspaceView.java:23-26` 不含 APPLYING → 冻结生效）+ `applyEditableState`(:1746-1764)；回执分支 `pollWorkspace(:1813-1836)` 区分 SUCCESS/NO_CHANGES/SAVED_NOT_RELOADED/其它；失败后草稿保留（`acceptSaved` 仅在成功/已保存分支调用）。不足：冻结只禁用四个表单 + 关选择器，**列表页 7 个按钮与效果条 4 个按钮无 enabled 管理、动作方法无 `active()` 守卫**（F-B） |
| 6 | 门面纪律（无绕过 EditSession 的写点） | 通过 | 全仓 `.draft()` 共 22 处；写操作仅 `RuleEditorModel.java:205`（在 `session.apply(OP_DELETE_RULE, …)` 内）、`RuleEditorModel.java:303-304`（恢复磁盘草稿，非用户编辑）、`EditSession.java:161/162/174/175`（undo/redo 内部）；其余 17 处只读（`RuleEditorScreen.java:407/408/761/812/836/1008/1074/1092/1160/1179/1194/1303/1311`、`client/ui/screen/form/FormView.java:142/158`）。字段与条件树写入统一走 `FormView.java:171/174` → `session.apply(OP_SET_FIELD, …)` |
| 7 | F1 修复正确性与误杀风险 | 通过（附 1 个中风险） | `RuleEditorScreen.localIssues(:1330-1384)`：规则级 `validateConditionTree(draft, "", RuleFields.CONDITIONS, issues)`(:1343) + 每个效果 `effects[i].conditions`(:1345-1353) → F1 覆盖完整；`validateConditionTree(:1387-1408)` 首行 `rawConditionsAt==null → return`（缺省 conditions 不判非法）；`core/model/ConditionExpression.java:35-40` 单叶合法、只有空 `all_of/any_of` 与无 term 的 `inverted` 非法 → 不误杀；`issuePath==draftPath` 指向 conditions 元素本身，与 `RuleDraft.rawConditionsAt(:215-218)` 语义一致。中风险：未注册/第三方条件类型被判 `conditions_decode` 阻塞保存，与控件层 `fallbackRaw` 原样保留语义矛盾（F-D） |
| 8 | F2/F3 现状 | 部分通过（F2）/ 通过（F3） | F2：`save()` 失败时 `notice.issue(first.label(), first.message())`(:1253-1279) 已含字段名 + 问题；字段级问题由 `FormView.tooltipAt(:484-502)` → `row.control.issues(row.path)` 悬停展示。仍缺「跳转到/聚焦出错字段」（`FormIssue.path` 未用于导航）。F3：`RuleEditorScreen.java:354-360` 每帧调用（`modals.isEmpty() && !pickerOpen && mode==EDIT`），`activeFormTooltip(:364-376)` 逐表单判 null/`isVisible()`，`tooltipAt` 跳过 NOTE 行、issues 非空取首条 → 无 NPE、弹窗/选择器时正确不渲染 |
| 9 | 接缝与 i18n | 通过 | `client/net` 下 `import …client.(ui|edit).` = 0 命中；`client/ui` 下 `core.network.transport` = 0 命中；lang `en_us.json`/`zh_cn.json` **各 632 键、集合完全一致、空值 0**（`itemdespawntowhat.edit.status.*` 12 键、`gui.itemdespawntowhat.edit.undo.*`、`conflict.message.*` 在位）。已知偏差（沿用 P5 结论，本阶段未扩大）：`client/ui` 引 `core.network.protocol` DTO；`client/edit/LiveEditorWorkspace.java:11` 引 `core.network.transport.RuleSaveResultPayload`（该适配类职责即收敛 payload） |
| 10 | 服务端权威性 | 通过 | 保存统一经 `RuleEditClientWorkspace.save()` → `RuleEditService`（锁校验 + 校验器 + 覆盖层写入），界面/模型不直接改 `entries`/`effective`，无本地乐观更新快照的捷径；`RuleEditService.java:295-311` 仅在 VERSION_CONFLICT/SAVED_NOT_RELOADED 附送快照。唯一乐观处是 `acceptSaved(:252-275)` 用本地草稿当新基线（P6 设计如此，配合 F-E 会放大不一致窗口） |

## 发现的缺陷/风险

### 高

**F-A 快照到达不驱动界面刷新（首次打开编辑器列表为空）**
- 证据：`RuleEditorScreen.java:173-180`（构造时 `model.refresh()`，此时快照尚未返回，`RuleEditorModel.java:73-77` 因 `snapshot == null` 直接 return false）；`RuleEditClientWorkspace.java:283-284`（`requestSnapshot()` 后立即 `EditorScreenHooks.open(...)`）、`:288-303`（`onSnapshot` 只赋值 `snapshot`/`contextRevision`，**不通知界面**）；`RuleEditorScreen.java:1813-1832`（`pollWorkspace` 只在 `resultSeq` 变化时 `model.refresh()`）；全仓 `model.refresh()` 仅 3 个调用点（`:179`、`:1806` 仅屏幕已存在时、`:1818` 仅回执后），`RuleEditorModel.isLoaded()` 无任何调用点。
- 影响：首次打开编辑器时 `entries` 为空 → `refreshList(:685-715)` 无内容，无法选择规则编辑；同一原因导致 `applyRestoredDrafts` 不执行，**磁盘草稿恢复、恢复提示（`notice.restored_draft`）与冲突弹窗都不会出现**。
- 复现思路：单人存档进游戏 → `/idtw config edit` → 界面打开后列表为空；等待一整秒（心跳/租约继续）也不恢复；此时触发一次保存回执后列表才可能填充。断线重连或再次 `/idtw config edit`（屏幕已存在）走 `RuleEditorOpener.java:52` 的 `refreshFromWorkspace` 才会显示。
- 修复建议（client/ui，task-14）：在 `tick()`/`pollWorkspace()` 中加 `if (model.refresh()) { refreshList(); rebuildEdit(); rebuildFocus(); }`（`refresh()` 已按快照实例比较，幂等、无额外开销），或在 `RuleEditClientWorkspace.onSnapshot` 后置一个「快照序号」供界面轮询。

### 中

**F-B APPLYING 冻结未覆盖列表页与效果条按钮**
- 证据：`RuleEditorScreen.java:1755-1764` `applyEditableStateNow` 只对 `infoForm/sourceForm/conditionsForm/effectForm` `setEnabled` + `closePicker`；`updateActionButtons(:1721-1743)` 只管 apply/undo/redo；`buildListButtons(:262-280)`（新建/模板/复制/编辑/停用/屏蔽/恢复/删除）与 `buildEffectButtons(:293-300)`（增/删/上移/下移）无 enabled 管理；`workspace.active()` 全屏仅命中 `:345/:386/:797/:1326/:1710/:1722`，动作方法（`openEditor:926`、`backToList:943`、`promptNewRule:960/964`、`promptDuplicate:997`、`openTemplatePicker:1034`、`toggleSelectedEnabled:1063`、`maskSelected:1081`、`restoreSelected:1099`、`deleteSelected:1116`、`openEffectPicker:1140`）无守卫。
- 影响：APPLYING 期间（已发出变更集）用户仍能改草稿，这些改动不会进入该变更集；随后 `acceptSaved(:252-275)` 把本地草稿记为基线并 `journal.drop` → 静默不一致/丢改动。
- 复现思路：保存后立刻点列表页「停用/删除」或多选后「编辑」，成功回执到达后检查该规则内容与列表标签。
- 修复建议：上述按钮统一 `setEnabled(editable)`，并在每个动作方法首行加 `if (!workspace.active()) return;`（`active()` 已是 ACTIVE-only，天然覆盖 APPLYING）。

**F-C 旧格式草稿（缺 baseline）无冲突保护**
- 证据：`RuleEditorModel.java:290-301`（只有 `baseline != null` 才判 REMOTE_CHANGED）、`client/edit/draft/PersistedDraft.java:48-69`（`baseline` 可缺；`FORMAT_VERSION=1`）。
- 影响：由早期构建写出的草稿文件在服务端规则已变化时直接覆盖，无冲突提示、无「改用服务端」机会。
- 复现思路：手改一个草稿 JSON 删掉 `baseline` 字段，再改服务端同 id 规则后重启。
- 修复建议：`baseline == null && !created` 时按 REMOTE_CHANGED 处理（或读取时补写 baseline 前先提示）。

**F-D 条件树保存前检查对未注册类型误杀**
- 证据：`RuleEditorScreen.java:1393-1396`（`conditionsAt(...) == null` → `issue.conditions_decode`，`collectIssues` 非空则阻塞保存）；`client/ui/screen/form/FormControl.java:1013-1038`（同一解码失败场景选择 `fallbackRaw` 只读原样保留，`store()` 原样返回）。
- 影响：含第三方/未注册条件类型的规则（用户完全没碰条件树）也无法保存，而控件本身设计为可原样保留 → 语义矛盾。
- 修复建议：当 `fallbackRaw` 路径可原样回写时降为 WARNING（不阻塞保存），或让 `localIssues` 复用控件的 `fallbackRaw` 判据。

**F-E 保存成功后界面不刷新快照**
- 证据：`core/service/RuleEditService.java:295-311`（仅 VERSION_CONFLICT/SAVED_NOT_RELOADED 附送快照）；`client/net/RuleEditClientWorkspace.java:334-339`（`onResult` 成功后只回到 ACTIVE、更新 `contextRevision`，不回补 `requestSnapshot()`）。
- 影响：保存成功后 `entries[].effective` 仍是旧快照，列表标签/摘要与服务端不一致；冲突判定以本地草稿为基线（配合 F-C 风险更大）。
- 修复建议：客户端在 SUCCESS/NO_CHANGES 回执后自动 `requestSnapshot()`，或服务端在这两个状态也附送快照。

### 低

- **F-F 草稿写盘非原子**：`DraftStore.java:52-62` 直接 `Files.writeString`，无 tmp+move；中断可能留下截断 JSON，`read(:115-123)` 解析失败仅 warn 跳过 = 草稿静默丢失。与要求 2「tmp+move 或等价」不符。建议：写 `.tmp` 后 `ATOMIC_MOVE`。
- **F-G 写盘失败不重试**：`DraftJournal.java:71-86` 先 `pending.remove` 再 `store.save`（忽略返回值），`flushAll(:89-97)` 只遍历 `pending` → 一次失败后本次会话不再落盘（内存仍在，退出即丢）。建议：仅当 `save` 成功才移除 pending，失败时保留并重试。
- **F-H 草稿文件名归一化冲突**：`DraftStore.java:126-140` 把 `:` 与 `/` 都换成 `_` → `idtw:foo/bar` 与 `idtw:foo_bar` 映射同一文件互相覆盖。建议：对归一化结果做可逆编码（如把 `/` 编为 `%2F`）或名称冲突时加短哈希后缀。
- **F-I 应用前未强制落盘**：`RuleEditorScreen.save()(:1253-1279)` 未先 `model.persistNow()`；崩溃窗口内最近 ≤2000ms 的编辑可能丢（关闭路径已覆盖）。建议：`save()` 开头先 `persistNow()`。
- **F-J 撤销标签退化**：`EditSession.java:28-40` 中 `OP_SET_SOURCE/OP_ADD_CONDITION/OP_ADD_GROUP/OP_WRAP_NOT/OP_DELETE_NODE/OP_MOVE_NODE/OP_TOGGLE_KIND/OP_CREATE_RULE/OP_EDIT_CONDITIONS` 9 个常量无调用点，条件树编辑统一记 `OP_SET_FIELD` → 撤销按钮显示「编辑字段」。功能不受影响，属文案/可发现性退化。
- **F-K 主线程同步 I/O**：`RuleEditorScreen` 构造 → `RuleEditorModel` → `journal.restore()` 同步读全部草稿；`persistNow` 逐条同步写。当前文件小、数量有限，暂判可接受，建议 P7 观察草稿数量增长后再考虑异步/合并。

## 无法静态判定的项（需真人实机）

1. **F-A 的实机表现**：需要一次真实 `/idtw config edit` 打开界面确认列表是否为空、草稿恢复提示与冲突弹窗是否出现（F-A 为静态推断，唯一需要「1 分钟」实机确认的结论）。
2. **写盘中断风险**：Windows 下进程被强杀/断电时的截断文件行为（F-F），需要故障注入验证。
3. **磁盘满/无写权限**：`DraftStore.save` 失败路径的实际表现（当前仅日志、界面无反馈），需人为把草稿目录设为只读验证。
4. **APPLYING 窗口时长**：单机/局域网下回执延迟决定 F-B 的可利用窗口，需实机观察。
5. **冲突弹窗体验**：`REMOTE_CHANGED` 三态在多人并发编辑时的实际触发频率与文案可读性。
6. **lang 实机渲染**：632 键键集合一致已静态确认，但参数占位符（如 `notice.issue`、`conflict.message.*`）在两种语言下的实际显示需实机切换语言确认。
7. **主线程 I/O 影响**：草稿数量增长到数十条时的帧时间影响，需实机测量。

## 对 task-14 / P7 的建议

1. **task-14（kit-dev，client/ui）优先修 F-A**：在 `tick()`/`pollWorkspace()` 中消费快照变化（`if (model.refresh()) { refreshList(); rebuildEdit(); rebuildFocus(); }`）。这是唯一阻断「编辑器可用」的缺陷，修完即可让草稿恢复、冲突弹窗、列表内容一并生效。
2. **同批修 F-B**：把列表页与效果条按钮统一纳入 `applyEditableState`，并给动作方法加 `workspace.active()` 守卫（最小侵入、与现有冻结语义一致）。
3. **F2 收尾**：`FormIssue.path` 已具备，可加「点击通知跳转到对应字段」；`FormView.tooltipAt` 已接线，如要继续可补 `isEnabled` 判断（当前无害）。
4. **P7 手工验收建议顺序**：先确认 F-A 修复（打开即见列表）→ 草稿恢复与三态冲突（含 F-C 场景：删掉 baseline 字段的旧文件）→ APPLYING 冻结（含 F-B 场景）→ 撤销/重做（含删除标记与恢复原始版本）→ 断线/租约/`/idtw config edit-lock status`/release。
5. **稳定性建议（可并入 P7 或后续）**：F-F/F-G/F-H 三项都在小文件层面，改动量小、收益明显（原子替换 + 失败重试 + 文件名可逆），建议在 P7 前一次做完。
6. **不必改动**：F-J/F-K 属文案与性能观察项，无需在 P7 前处理。

---

**复核范围与方法**：仅读取 `client/edit/**`、`client/edit/draft/**`、`client/ui/screen/**`、`client/net/**`、`core/service/RuleEditService.java`、lang 两份 JSON，并用 grep 做调用点与 import 纪律核对；**未修改任何 Java/JSON/lang**。构建复跑见文首。
