# P5 编辑器只读实现复核（task-12）

> 复核人：tree-dev（只读，未改动任何 Java/JSON/lang 资源）。
> 依据：[plan-frontend-rewrite-contract.md](../plan/plan-frontend-rewrite-contract.md) §3/§5/§7、[plan-frontend-rewrite-forms.md](../plan/plan-frontend-rewrite-forms.md)、[manual-acceptance.md](../guide/manual-acceptance.md)。
> 方法：源码逐文件通读 + 机械比对（forms.md 字段 × EditorField 描述符）+ 全仓 grep + i18n 键集合脚本核对；结论均附 `文件:行`。
> 快照说明：行号取自 2026-10-03 01:05 左右的工作树（`RuleEditorScreen.java` 1890 行、LastWriteTime 2026/10/3 1:03:23）。P6（task-9）正在改动该文件，合并后行号会漂移，**定位请以方法名为准**。

## 1. 结论摘要

- 整体：P5 主屏已达到「不写 JSON 也能改规则」的目标，9 条必查项中 **7 条通过、2 条存在缺口**（都不影响数据安全，服务端仍会拒绝非法保存）。
- 通过：字段覆盖（10 条件 + 12 效果 + 规则级全部字段都有真实控件）、规则生命周期六动作与两种「恢复」语义区分、条件树递归 AND/OR/NOT 可编辑且限额/空组/缺条件能被保存前拦截、协议接缝（client/net ↔ client/ui 零反向依赖、界面不碰传输载荷、关屏走 workspace.close、APPLYING 冻结）、i18n 键集合一致无空值、12 个 RuleSaveStatus 都有可读提示、非 JSON 用户主链可走通。
- 缺口 F1：效果级条件树的非法结构不做保存前本地拦截（只对规则级 conditions 做），切到别的效果后保存会被服务端以 VALIDATION_FAILED 拒绝，用户看不到「哪个效果的哪个字段」。
- 缺口 F2：错误不能定位/跳转到具体字段——FormIssue 的 path 只用于构造，界面只把第一条消息显示在通知栏；`FormView.tooltipAt` 是死代码（全客户端无调用点）。

## 2. 逐条核对表

| # | 检查项 | 结论 | 证据 | 缺口 |
|---|---|---|---|---|
| 1 | 10 条件 + 12 效果的每个字段都能在界面编辑 | **通过** | forms.md 的 24 组字段（10 条件 + 12 效果 + rule:rule + rule:source）与 `client/edit/BuiltinEditorDescriptors.java` 的描述符**逐名一致，0 缺失 0 多余**（唯一差异是效果组的公共字段 delay_ticks/chance/conditions，由 forms.md §3 统一规定，不在每个效果表内重复）；`client/ui/screen/form/FormControl.java:141-206` 的 switch 显式覆盖全部 20 种 EditorFieldType，default 分支才是 RawJsonControl，内建类型没有一个落到它 | 无 |
| 2 | 生命周期六动作 + 模板创建 + 两种恢复语义 | **通过** | 按钮与实现：`RuleEditorScreen.java:262`(new/template/duplicate/edit/toggle/mask/restore/delete)、`promptNewRule:935`、`promptDuplicate:972`、`openTemplatePicker:1009`（`RuleTemplateHooks.list()`/`load()`）、`toggleSelectedEnabled:1038`、`maskSelected:1056`、`restoreSelected:1074`、`deleteSelected:1091`。语义区分：恢复原始基底 = `RuleEditorModel.markRestore:165-169` → `buildChangeSetJson:201-204` 走 `changeSet.delete(id)`；屏蔽基底 = `markDeleted:172-177` = `session.apply(OP_RESTORE_ORIGINAL, () -> draft.setDeleted(true))` → `:212-216` 以带 delete=true 的控制条目 upsert（契约 §3.6 覆盖层语义） | 无 |
| 3 | 条件树递归 AND/OR/NOT 可编辑、与 RuleDraft API 一致、非法结构保存前拦截 | **基本通过** | `client/ui/widget/UiConditionTreeEditor.java:27-40`（直接编辑 ConditionExpression/ConditionNode，不引入平行模型）；空组可建 `:296`、包 NOT `:309`（Inverted 天然单 term）；限额用冻结常量 `ConditionLimits`：`INCOMPLETE_GROUP:447`、`MISSING_CONDITION:451`、`TOO_DEEP/TOO_MANY_NODES/TOO_MANY_LEAVES:455-464`；`FormControl.java:1041-1059` 把树内 issues 映射成 FormIssue；`RuleEditorScreen.save():1223-1229` 有 issue 即拒绝保存 | **F1**（效果级不拦截）、**F2**（不定位字段） |
| 4 | 协议接缝 (a) client/net 不依赖 client/ui\|edit (b) client/ui 不依赖 transport payload (c) 数据只经 workspace (d) 关屏 workspace.close(screen_closed) (e) APPLYING 冻结 | **通过** | (a) grep `import com.meteorite.itemdespawntowhat.client.(ui\|edit).` 于 `client/net` → **0 命中**。(b) grep `core.network.transport` 于 `client/ui` → **0 命中**；grep `CustomPacketPayload\|PacketSender` 于 `client/ui` → **0 命中**。(c) `RuleEditorScreen` 只持有 `EditorWorkspaceView`（`EditorWorkspaceView.java:14-17` 类注释「不暴露任何传输 payload 类型」）。(d) `RuleEditorScreen.java:1731`/`:1741` 均 `workspace.close("screen_closed")`（另见 `EditorWorkspaceView.java:52`）。(e) `tick():1607-1614` → `applyEditableState:1643-1656` → `FormView.setEnabled:346-351` → `ConditionTreeControl.setEnabled` = `editor.setVisible(false)`（`FormControl.java:1003-1005`）；`UiConditionTreeEditor.keyPressed:1010-1013` 首行 `if (!visible) return false`；`FormView.mouseClicked:472` `if (!enabled) return false`；保存按钮 `:345`、apply/undo/redo `:1621-1634`、`save():1240-1241` 二次校验 | 见第 4 节已知偏差（client/edit 的 protocol DTO 依赖） |
| 5 | i18n 字面量键核对 + 两 lang 键集合一致无空值 | **通过** | 脚本核对：en_us.json / zh_cn.json 各 **631** 键，onlyEn=0 / onlyZh=0 / 空值=0；其中 `gui.itemdespawntowhat.edit.*` 386 键；12 个 `itemdespawntowhat.edit.status.*`、15 个 `issue.*`、15 个协议键、8 个 `template.*`、13 个 `enum.*`、15 个 `undo*`、2 个 `preset.*`、7 个 `conflict.*` 全部存在。源码侧提取到 230 个键（含 14 个纯前缀常量），去掉纯前缀后的具体键**全部命中** | 动态拼接族的完整性靠枚举核对（已逐族核对） |
| 6 | 视觉与可访问性 | **通过（代码层）/ 其余需人工** | Tab/Shift+Tab：`client/ui/kit/UiFocusManager.java:143-148`（TAB + MOD_SHIFT → `moveFocus`±1，环绕 `:122-131`）；Enter/Space `:149-154`，宿主可用 `setEnterActivates/setSpaceActivates:158-162` 让位给模态；Esc/层级分派 `RuleEditorScreen.keyPressed:1526`（模态 → ESC → 焦点 → 搜索框 → 列表 → 表单）；颜色不单独承载信息：`statusMark:491-503` 返回 `[+]/[-]/[x]/[!]` 文本标记，注释即「颜色必须配文字」，另有 dirty `:473-475`、error `:484-488` 文本标签；窄屏：`RuleEditorScreen:84 NARROW_WIDTH=430`（320×240 单列阈值）+ `FormView:38 NARROW_WIDTH=180`（标签换行到控件上方）+ 表单自带滚动条 `FormView:412-422`；树控件深度提示 `UiConditionTreeEditor:46 DEEP_HINT_DEPTH=6` | 见第 5 节人工项；`FormView.tooltipAt` 死代码（F3） |
| 7 | 12 个 RuleSaveStatus 都有可读提示、SAVED_NOT_RELOADED 警示、错误可定位 | **部分通过** | 12 码文案：`core/network/protocol/RuleSaveStatus.java:messageKey()` = `itemdespawntowhat.edit.status.<小写名>`，lang 中 12 键齐全；界面分支 `RuleEditorScreen.java:1704-1713`：SUCCESS/NO_CHANGES → 成功色，**SAVED_NOT_RELOADED → WARNING 色（:1709-1711）**，其余非空状态 → DANGER 并带 `lastMessageArgs` 占位符（`:1713`）；VERSION_CONFLICT 冲突由 P6 的 `promptConflicts:1659` + `conflict.message.remote_changed/target_missing/target_exists` 弹窗处理 | **F2**：不能跳到具体字段；已发现的字段级提示断链（F3） |
| 8 | 非 JSON 用户可完成性（新建 → 挑物品 → 加条件 → 加效果 → 应用） | **通过（主链）/ 有缺口** | 新建 `promptNewRule:935`；挑选走目录建议：`suggestionProvider():859` + `CatalogSuggestions`（`client/ui/screen/form/CatalogSuggestions.java:28`，经 workspace.requestCatalog 拉目录），挂到 sourceForm `:763`、conditionsForm `:768`、效果表单 `:823`/`:1189`；加条件靠树控件（A/G/N/T/Shift+G/Delete/Enter）；加效果 `buildEffectButtons:293`；提交 `save():1223` + `workspace.save` | 状态效果无目录（manual-acceptance 8.7）；F1/F2/F3；错误提示只到通知栏 |
| 9 | manual-acceptance.md 事实同步 | **已修** | 本次改动：§0 标记表【P5 后】行、§0 第 1 条（改为已接线，附 Fabric `ItemDespawnToWhatClient.java:21` / NeoForge `client/register/RegisterEvent.java:24` 调用点）、第 3 条（改为第 2/4.2/6 节现已可执行）、第 4 条、缺口表 8.1（标记已解决）、8.5（去掉「P5 接线时处理」） | 其余 【P5 后】 标记（如 4.2.6、7.7）语义已可执行，属措辞残留，未在授权范围内改动 |

## 3. 验收失败项（按契约 §7 验收基线判定）

**F1（中）效果级条件树不做保存前本地拦截**
- 契约 §7 要求「空条件组 / 超限 / 旧格式 JSON 在保存前被校验拦截并定位字段」。
- 现状：`RuleEditorScreen.localIssues:1300-1356` 只取 `draft.view().get(RuleFields.CONDITIONS)`（规则级条件树，`:1307-1325`）做结构校验，**没有遍历 effects[i].conditions**；效果级条件树的违规只能在「该效果正被打开」时经 `effectForm.issues()`（`FormControl.java:1041-1059`）进入 `collectIssues():1269-1295`。
- 可复现：给效果 A 的条件树加一个空 all_of，切到效果 B（或切到「信息」页签），点保存 → 本地不拦截，发出请求后被服务端以 VALIDATION_FAILED 拒绝，用户只看到第一条通用消息。
- 影响：数据不会写坏（服务端是权威校验），但不满足「保存前定位字段」的验收口径。
- 建议修复（P6）：在 `localIssues` 内对 `effects[i].conditions` 复用同一套结构校验（可把 `:1307-1325` 抽成 `validateConditionTree(JsonElement, String path, List<FormIssue>)`，路径用 `effects[i].conditions`）。

**F2（中）错误不能定位/跳转到具体字段**
- 现状：`FormIssue(String path, Component label, Component message)`（`client/ui/screen/form/FormIssue.java:9`）的 path 只参与构造；`save():1226-1227` 只把 `issues.get(0).message()` 显示到通知栏；全客户端 grep 无 `focusField`/`scrollToPath`/高亮跳转实现。
- 语义上用户仍能看到可读中文提示（含 `too_deep` 的 `depth`/`max` 占位符），但不知道是哪个字段。
- 建议修复（P6）：给 `FormView` 增加按 path 定位（滚动 + 聚焦 + 临时高亮）与 `focusedIssue` 展示；至少把 `FormIssue.label`（字段标签）拼进通知文案。

**F3（低）字段级问题 tooltip 断链（死代码）**
- `FormView.tooltipAt:425-443` 实现了「优先显示该字段的校验问题，其次显示字段提示」，但全客户端 grep `tooltipAt` **只有定义、没有调用点**（界面用的是 `UiControlGroup.renderTooltip:132-136` 与 `UiLightbox:285-295`，只显示控件自带 tooltip，不含 FormIssue）。
- 影响：鼠标悬停看不到字段级错误，与 F2 叠加；控件自带 tooltip（如 `field.common.delay_ticks.hint`）正常。
- 建议：要么接线 `FormView.tooltipAt`，要么删除死代码避免误以为已实现（P6 二选一）。

## 4. 已知偏差（记录，不判失败）

- `client/edit/LiveEditorWorkspace.java:11` 直接 `import ...core.network.transport.RuleSaveResultPayload`——client/edit 唯一的传输载荷依赖（lead 已预查并接受），其余 12 处 `core.network.*` import 都是 `protocol` 下的只读 DTO。
- `client/ui` 从 `core.network.protocol` 引 DTO：`RuleEditorScreen.java:48-49`（RuleSaveStatus/RuleSnapshotEntry）、`RuleEditorModel.java:12-14`（RuleIssue/RuleSnapshot/RuleSnapshotEntry）、`CatalogSuggestions.java:7-9`（RuleCatalog/RuleCatalogEntry/RuleCatalogType）。这些是数据类而非 CustomPacketPayload，未违反「界面不直接收发载荷」，但使 client/ui 与协议包耦合。
- `handlesInput`（`FormView.java:556-565`）要求控件当前持有焦点，因此 `FormView.keyPressed:522-540` 未显式判 `enabled` 也不构成冻结漏洞（冻结时控件各自不可编辑：文本 `input.setEditable(false)` `FormControl.java:462-467`、树 `setVisible(false)` `:1003-1005`）。

## 5. 只能人工验收的项

- 像素观感：320×240 下灰色像素风、页签与按钮不重叠、文本不溢出（三处代码阈值只保证布局分支，不保证观感）。
- 缩放 1x/2x/3x 与实际鼠标滚轮/双击/拖拽手感。
- tooltip 在屏幕四边是否越界（渲染走原版 `renderComponentTooltip`，其夹取行为需真机确认）。
- 中文/英文语言切换后的全部文案（键集合已核对，排版未验）。
- 真实服务端链路：取锁 → 快照 → 编辑 → 应用 → 重载；SAVED_NOT_RELOADED 的故障注入（按 manual-acceptance 3.7 的 icacls 步骤）。
- 双平台一致性（Fabric/NeoForge 客户端初始化顺序差异）与多人同时编辑的锁竞争表现。

## 6. 对 P6 的风险与建议（kit-dev）

- 旁路检查通过：`client/ui` 下对 `RuleDraft` 的写操作只有 `RuleEditorModel.markDeleted:205`（位于 `session.apply(OP_DELETE_RULE, ...)` 内），其余 `session.draft().xxx()` 均为只读（`:385/:386/:739/:787/:983`、`RuleEditorModel:266/270/357/359`）；`EditSession.apply:90` 仍是唯一写门面。P6 新增入口请继续走 `EditSession.apply`，否则撤销标签会缺。
- `RuleEditorScreen.java` 正被 P6 改动（复核期间从 1770 行量级增到 1890 行，`:1599-1666` 新增 tickPersistence/consumeRestoredCount/discardAllDrafts/promptConflicts/applyEditableState）。请把 F1 的「效果级条件树校验」并进 `localIssues`，并顺手决定 F3 是接线还是删码。
- 冻结链路已复核：P6 新增的 `tickPersistence/promptConflicts` 在 `tick():1598-1614` 内、位于 `applyEditableState(editable)` 之前，未绕过冻结；`closeByUser:1731-1743` 在放弃草稿时会 `model.discardAllDrafts()` 后再 `workspace.close("screen_closed")`，与契约 §3 的关屏语义一致。

## 7. 对 P7 的建议

- 收敛 client 对协议包的 import：把只读 DTO（RuleSnapshot/RuleSnapshotEntry/RuleIssue/RuleSaveStatus/RuleCatalog*）上移到 `core/api` 或提供 `client` 侧只读视图类型，消掉 `LiveEditorWorkspace.java:11` 的 transport 依赖。
- 状态效果目录（原 manual-acceptance 8.7）**已由 task-13 解决**：`RuleCatalogType` 末尾追加 `MOB_EFFECT`（`core/network/protocol/RuleCatalogType.java:20`），数据源 `core/catalog/RuleCatalogSources.java:154-162`，label 用 `MobEffect.getDescriptionId()`；客户端映射归 kit-dev task-14。
- 发布说明记录快捷键默认未绑定（8.5）与 UI kit NOTICE（ADR-0022）。

## 附：本次复核可复现检查

- 反向依赖：`grep -rn "client\.(ui|edit)\." common/src/main/java/.../client/net`（期望 0）、`grep -rn "core\.network\.transport" common/src/main/java/.../client/ui`（期望 0）。
- i18n：解析 en_us.json/zh_cn.json 比较键集合与空值，再扫 `common/src/main/java` 提取 `"...gui.*"`/`"...itemdespawntowhat.*"` 字面量与 `前缀常量 + "..."` 拼接，逐键回查（本次 631/631、0 缺失）。
- 字段覆盖：从 forms.md 的 §5.x/§6.x 表提取「类型 → JSON 键」，与 `BuiltinEditorDescriptors` 注册行（用标签键第一段归属类型）逐名比对（24 组、0 DIFF，效果组差的公共字段由 forms.md §3 覆盖）。
