package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.DebugTestRules;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorChangeSet;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleCategoryAssignment;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.draft.DraftConflict;
import com.meteorite.itemdespawntowhat.client.edit.draft.DraftJournal;
import com.meteorite.itemdespawntowhat.client.edit.draft.DraftStore;
import com.meteorite.itemdespawntowhat.client.edit.draft.PersistedDraft;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/***
 * 编辑页数据模型：把服务端快照映射成「按规则 id 的草稿会话」。
 * 所有字段与结构编辑都必须经过 {@link EditSession#apply}，保证每个编辑入口都有撤销标签。
 */
public final class RuleEditorModel {

    // 工作区只读视图
    private final EditorWorkspaceView workspace;
    // 快照条目（按 id，保持服务端顺序）
    private final LinkedHashMap<String, RuleSnapshotEntry> entries = new LinkedHashMap<>();
    // 草稿会话（按 id）
    private final LinkedHashMap<String, EditSession> sessions = new LinkedHashMap<>();
    // 本次会话中新建、服务端快照尚不存在的规则 id
    private final Set<String> createdIds = new LinkedHashSet<>();
    // 已消费的快照
    private @Nullable RuleSnapshot seenSnapshot;
    // 已消费的快照版本
    private int snapshotVersion = -1;
    // 是否至少成功加载过一次快照
    private boolean loaded;
    // 快照级错误汇总（随快照重建）：列表按 id 查询，不再每次遍历问题清单
    private final Set<String> errorIds = new LinkedHashSet<>();
    // 生效视图的规则体缓存：同一快照版本内每条规则只做一次深拷贝
    private final Map<String, RuleValue> effectiveBodies = new LinkedHashMap<>();

    // 草稿落盘日志（跨界面关闭 / 断线 / 重启恢复）
    private final DraftJournal journal = new DraftJournal(new DraftStore());
    // 从磁盘读回、等待绑定到服务端快照的草稿（按目标 id）
    private final LinkedHashMap<String, PersistedDraft> restored = new LinkedHashMap<>();
    // 草稿建立时的服务端整条规则基线（null 表示当时服务端没有该规则）
    private final LinkedHashMap<String, RuleValue> baselines = new LinkedHashMap<>();
    // 与服务端不一致、等待玩家选择的规则（按目标 id）
    private final LinkedHashMap<String, DraftConflict> conflicts = new LinkedHashMap<>();
    // 本次恢复到的草稿数量（界面提示一次后清空）
    private int restoredCount;
    // 玩家确认放弃草稿后，本模型不再落盘
    private boolean draftsDiscarded;

    // 构造：读回磁盘草稿；真正绑定要等服务端快照，放在 refresh() 里
    public RuleEditorModel(EditorWorkspaceView workspace) {
        this.workspace = workspace;
        for (PersistedDraft draft : this.journal.restore()) {
            this.restored.put(draft.targetId(), draft);
        }
    }

    // 工作区只读视图
    public EditorWorkspaceView workspace() {
        return this.workspace;
    }

    // 消费新快照；返回 true 表示确实有新数据
    public boolean refresh() {
        RuleSnapshot snapshot = this.workspace.snapshot();
        if (snapshot == null || snapshot == this.seenSnapshot) {
            return false;
        }
        this.seenSnapshot = snapshot;
        this.snapshotVersion = snapshot.version();
        this.entries.clear();
        for (RuleSnapshotEntry entry : snapshot.entries()) {
            this.entries.put(entry.id().toString(), entry);
        }
        // 规则体与错误汇总都随快照重建：换快照即全部失效
        this.effectiveBodies.clear();
        this.collectErrorIds(snapshot);
        // 先绑定磁盘草稿（需要最新快照做整条规则比较），再重绑未被改动的内存会话
        this.applyRestoredDrafts();
        // 未改动且非本次新建的会话重新绑定到服务端内容
        var iterator = this.sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var current = iterator.next();
            if (this.createdIds.contains(current.getKey()) || current.getValue().isDirty()) {
                continue;
            }
            RuleSnapshotEntry entry = this.entries.get(current.getKey());
            RuleValue body = entryBody(current.getKey());
            if (body == null) {
                iterator.remove();
                continue;
            }
            current.getValue().load(RuleDraft.of(categorySeeded(entry, body)));
            current.getValue().clearHistory();
        }
        this.createdIds.removeIf(id -> !this.sessions.containsKey(id));
        this.loaded = true;
        return true;
    }

    // 是否已加载过快照
    public boolean isLoaded() {
        return this.loaded;
    }

    // 快照版本
    public int snapshotVersion() {
        return this.snapshotVersion;
    }

    // 全部规则 id（服务端顺序，含本次新建）
    public List<String> ruleIds() {
        List<String> ids = new ArrayList<>(this.entries.keySet());
        for (String id : this.createdIds) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    // 快照条目
    public @Nullable RuleSnapshotEntry entry(String id) {
        return this.entries.get(id);
    }

    // 某条规则的草稿会话
    public @Nullable EditSession session(String id) {
        return this.sessions.get(id);
    }

    // 某条规则是否在本次会话中新建
    public boolean isCreated(String id) {
        return this.createdIds.contains(id);
    }

    /**
     * 编辑页打开某条规则时按需请求来源视图：只有服务端已有该条目时才有来源可看。
     * 请求失败或超时只影响来源对照，不影响本页的草稿编辑与保存。
     *
     * @param id 规则 id
     */
    public void requestSourceView(String id) {
        if (id != null && this.entries.containsKey(id)) {
            this.workspace.requestSourceView(id);
        }
    }

    // 打开（必要时懒创建）某条规则的草稿会话
    public @Nullable EditSession openSession(String id) {
        EditSession existing = this.sessions.get(id);
        if (existing != null) {
            return existing;
        }
        RuleSnapshotEntry entry = this.entries.get(id);
        RuleValue body = entryBody(id);
        if (body == null) {
            return null;
        }
        EditSession session = new EditSession(id, RuleDraft.of(categorySeeded(entry, body)));
        this.sessions.put(id, session);
        // 基线取服务端「生效视图」的深拷贝快照；没有生效视图时记为不存在
        this.captureBaseline(id, entry.effective() == null ? null : RuleDraft.of(entry.effective()).root());
        this.bindSession(id, session);
        return session;
    }

    // 显示和筛选优先读取草稿，不为浏览列表额外创建会话。
    public @Nullable RuleValue displayBody(String id) {
        EditSession session = sessions.get(id);
        return session == null ? entryBody(id) : session.draft().root();
    }

    /**
     * 服务端生效视图的规则体：同一快照版本内每条规则只做一次深拷贝。
     *
     * <p>列表展示、页头标题与提示都读这一份，避免每帧按条目重新解码与深拷贝；值对象是只读句柄，
     * 写入仍走 {@link EditSession#apply} 与 {@link RuleDraft}，不会改到缓存内容。</p>
     *
     * @param id 规则 id
     * @return 规则体；条目不存在或没有可读的生效视图时为 null
     */
    public @Nullable RuleValue entryBody(String id) {
        if (!this.entries.containsKey(id)) {
            return null;
        }
        if (this.effectiveBodies.containsKey(id)) {
            return this.effectiveBodies.get(id);
        }
        RuleValue body = ruleBody(this.entries.get(id));
        this.effectiveBodies.put(id, body);
        return body;
    }

    // 条目只带生效视图：服务端在解码失败时已把覆盖或基底原文回退为生效视图
    private static @Nullable RuleValue ruleBody(@Nullable RuleSnapshotEntry entry) {
        if (entry == null || entry.effective() == null) {
            return null;
        }
        RuleValue candidate = RuleDraft.of(entry.effective()).root();
        return hasRuleBody(candidate) ? candidate : null;
    }

    /**
     * 打开会话用的规则体：把快照条目下发的分类补回草稿 JSON。
     *
     * <p>生效视图来自模型编码结果，不含 {@code category}；不补回的话，作者手写的分类会在
     * 保存时丢失。分类以草稿基线形式存在，未改动直接保存不会把规则判为待保存。</p>
     *
     * @param entry 快照条目，允许为 null（仅本次新建或已不存在的目标）
     * @param body 规则体，允许为 null
     * @return 补回分类后的规则体；无分类可补时原样返回
     */
    private static @Nullable RuleValue categorySeeded(@Nullable RuleSnapshotEntry entry, @Nullable RuleValue body) {
        return RuleCategoryAssignment.seed(body, entry == null ? null : entry.category());
    }

    // delete/enabled 控制条目不是完整规则体，不能当作空规则交给表单。
    private static boolean hasRuleBody(@Nullable RuleValue body) {
        return body != null && (body.has(RuleFields.SOURCE)
                || body.has(RuleFields.EFFECTS)
                || body.has(RuleFields.OUTCOMES));
    }

    // 新建一条规则草稿
    public void createSession(String id, RuleValue body) {
        // 初始态用「带 id 的默认骨架」，再把传入内容作为一次可撤销的「新建规则」写入：
        // 撤销新建后仍是带 id 的合法骨架，而不是空对象
        RuleDraft draft = RuleDraft.of(BuiltinEditorDefaults.ruleBody(id));
        EditSession session = new EditSession(id, draft);
        session.apply(EditSession.OP_CREATE_RULE, () -> draft.replaceWith(body));
        this.sessions.put(id, session);
        this.createdIds.add(id);
        // 新建规则在服务端还没有内容，基线为「不存在」
        this.captureBaseline(id, null);
        this.bindSession(id, session);
    }

    // 关闭某条规则的草稿（放弃未保存改动，同时删掉落盘草稿）
    public void dropSession(String id) {
        this.sessions.remove(id);
        this.createdIds.remove(id);
        this.baselines.remove(id);
        this.conflicts.remove(id);
        this.journal.drop(id);
    }

    // 标记某条规则待删除
    public void markDeleted(String id, boolean deleted) {
        EditSession session = this.sessions.get(id);
        if (session != null) {
            session.apply(EditSession.OP_DELETE_RULE, () -> session.draft().setDeleted(deleted));
        }
    }

    // ---- 调试测试规则 ----

    /**
     * 统计当前语境下的测试规则数量：包含服务端已有的与本次新建的草稿，不含草稿被标记删除的规则。
     *
     * @return 测试规则条数
     */
    public int testRuleCount() {
        int total = 0;
        for (String id : ruleIds()) {
            if (DebugTestRules.isTestRule(id) && !isDraftDeleted(id)) {
                total++;
            }
        }
        return total;
    }

    /**
     * 按序号批量填充测试规则草稿：id 已存在的测试规则被跳过（草稿被标记删除时恢复为未删除）。
     *
     * <p>只写草稿、不落盘，规则立即可在管理页看到；落盘仍由统一的「应用全部」入口完成。</p>
     *
     * @param count 期望的测试规则条数，调用方需保证落在
     *              [{@link DebugTestRules#MIN_COUNT}, {@link DebugTestRules#MAX_COUNT}] 区间内
     * @return 本次真正新建的测试规则条数
     */
    public int createTestRules(int count) {
        int created = 0;
        for (int index = 1; index <= count; index++) {
            String id = DebugTestRules.ruleId(index);
            if (this.entries.containsKey(id)) {
                // 服务端已有：仅在草稿被标记删除时恢复，避免覆盖玩家对同序号规则的改动
                if (isDraftDeleted(id) && openSession(id) != null) {
                    markDeleted(id, false);
                }
                continue;
            }
            if (this.createdIds.contains(id)) {
                continue;
            }
            createSession(id, DebugTestRules.ruleBody(id));
            created++;
        }
        return created;
    }

    /**
     * 删除全部测试规则草稿：新建的草稿直接丢弃，服务端已有的标记为待删除。
     *
     * <p>判定只依据 id 前缀，因此不会影响任何非测试规则；落盘仍由统一的「应用全部」入口完成。</p>
     *
     * @return 本次受影响的测试规则条数
     */
    public int deleteTestRules() {
        int affected = 0;
        for (String id : ruleIds()) {
            if (!DebugTestRules.isTestRule(id)) {
                continue;
            }
            if (this.createdIds.contains(id)) {
                dropSession(id);
                affected++;
                continue;
            }
            if (isDraftDeleted(id)) {
                continue;
            }
            if (openSession(id) != null) {
                markDeleted(id, true);
                affected++;
            }
        }
        return affected;
    }

    // 草稿是否已标记删除（未打开会话的规则按未删除处理）
    private boolean isDraftDeleted(String id) {
        EditSession session = this.sessions.get(id);
        return session != null && session.draft().isDeleted();
    }

    // 存在改动的规则 id（保持服务端顺序）
    public List<String> dirtyRuleIds() {
        List<String> ids = new ArrayList<>();
        for (String id : ruleIds()) {
            EditSession session = this.sessions.get(id);
            if (session != null && (session.isDirty() || this.createdIds.contains(id))) {
                ids.add(id);
            }
        }
        return ids;
    }

    // 是否存在任何改动
    public boolean hasDirty() {
        return !dirtyRuleIds().isEmpty();
    }

    // 装配变更集 JSON；无改动返回 null
    public @Nullable String buildChangeSetJson() {
        EditorChangeSet changeSet = new EditorChangeSet(this.workspace.contextRevision());
        for (String id : dirtyRuleIds()) {
            EditSession session = this.sessions.get(id);
            if (session == null) {
                continue;
            }
            RuleDraft draft = session.draft();
            RuleSnapshotEntry entry = this.entries.get(id);
            boolean overlayOnly = entry != null && RuleSnapshotEntry.ORIGIN_OVERLAY.equals(entry.origin());
            if (draft.isDeleted() && !this.createdIds.contains(id) && overlayOnly) {
                changeSet.delete(id);
            } else {
                changeSet.upsert(draft);
            }
        }
        return changeSet.isEmpty() ? null : changeSet.serialize();
    }

    // 保存成功后收敛草稿：服务端内容成为新的基线
    public void acceptSaved() {
        for (String id : dirtyRuleIds()) {
            EditSession session = this.sessions.get(id);
            if (session == null) {
                continue;
            }
            session.load(RuleDraft.of(session.draft().root()));
            session.clearHistory();
            this.createdIds.remove(id);
            // 服务端已接受的内容成为新的草稿基线，并删掉落盘草稿
            this.captureBaseline(id, session.draft().root());
            this.conflicts.remove(id);
            this.journal.drop(id);
        }
    }

    // ---- P6：草稿落盘 / 恢复 / 冲突 ----

    // 把磁盘草稿绑定到最新快照：整条规则比较与基线一致才无冲突
    private void applyRestoredDrafts() {
        if (this.restored.isEmpty()) {
            return;
        }
        List<PersistedDraft> drafts = new ArrayList<>(this.restored.values());
        this.restored.clear();
        for (PersistedDraft stored : drafts) {
            String id = stored.targetId();
            if (this.sessions.containsKey(id)) {
                continue;
            }
            RuleSnapshotEntry entry = this.entries.get(id);
            // 服务端当前内容的值快照；没有生效视图即视为目标不存在
            RuleValue serverBody = entry == null || entry.effective() == null
                    ? null : RuleDraft.of(entry.effective()).root();
            // 落盘草稿的基线快照；既有草稿未记录基线时为 null，表示基线未知
            RuleValue baseline = stored.baselineValue();
            // 整条规则比较（不做逐字段合并）：目标消失、同名新规则、内容被改都会提示
            if (stored.created() && serverBody != null) {
                this.conflicts.put(id, new DraftConflict(id, DraftConflict.Reason.TARGET_EXISTS));
            } else if (!stored.created() && serverBody == null) {
                this.conflicts.put(id, new DraftConflict(id, DraftConflict.Reason.TARGET_MISSING));
            } else if (serverBody != null && (baseline == null || !baseline.equals(serverBody))) {
                // 基线缺失（既有草稿未记录基线）或内容确实变了：保守判为远端已变，交给玩家选
                this.conflicts.put(id, new DraftConflict(id, DraftConflict.Reason.REMOTE_CHANGED));
            }
            RuleDraft draft = RuleDraft.of(categorySeeded(entry, serverBody));
            draft.replaceWith(stored.draftValue());
            draft.setDeleted(stored.deleted());
            EditSession session = new EditSession(id, draft);
            if (stored.created()) {
                this.createdIds.add(id);
            }
            this.sessions.put(id, session);
            this.captureBaseline(id, serverBody);
            this.bindSession(id, session);
            this.journal.markDirty(snapshotOf(id, session));
            this.restoredCount++;
        }
    }

    // 把会话接到草稿日志：此后每次可撤销操作都会（节流）落盘
    private void bindSession(String id, EditSession session) {
        session.addListener(() -> this.onSessionChanged(id, session));
        if (this.isJournalWorthy(id, session)) {
            this.journal.writeNow(snapshotOf(id, session));
        }
    }

    // 会话内容变化：有未应用改动就落盘，回到干净状态就删文件
    private void onSessionChanged(String id, EditSession session) {
        if (this.draftsDiscarded || !this.isJournalWorthy(id, session)) {
            this.journal.drop(id);
            return;
        }
        this.journal.markDirty(snapshotOf(id, session));
    }

    // 是否值得落盘：有改动或本次新建
    private boolean isJournalWorthy(String id, EditSession session) {
        return session.isDirty() || this.createdIds.contains(id);
    }

    // 当前草稿快照（含基线，供落盘与冲突比较）
    private PersistedDraft snapshotOf(String id, EditSession session) {
        RuleValue baseline = this.baselines.get(id);
        return new PersistedDraft(
                id,
                this.createdIds.contains(id),
                session.draft().isDeleted(),
                // 落盘载体仍以 JSON 对象表达：值快照经草稿入口转回 JSON
                baseline == null ? null : RuleDraft.of(baseline).toJson(),
                session.draft().toJson(),
                this.snapshotVersion,
                this.workspace.contextRevision(),
                System.currentTimeMillis());
    }

    // 记录草稿建立时的服务端整条规则内容
    private void captureBaseline(String id, @Nullable RuleValue serverBody) {
        this.baselines.put(id, serverBody == null ? null : serverBody.deepCopy());
    }

    // 节流写盘（界面每帧调用）
    public void tickPersistence() {
        this.journal.flushDue(System.currentTimeMillis());
    }

    // 立即写盘全部草稿（界面关闭 / 会话失效时调用，保证不丢）
    public void persistNow() {
        if (this.draftsDiscarded) {
            return;
        }
        for (String id : new ArrayList<>(this.sessions.keySet())) {
            EditSession session = this.sessions.get(id);
            if (session != null && this.isJournalWorthy(id, session)) {
                this.journal.writeNow(snapshotOf(id, session));
            }
        }
        this.journal.flushAll();
    }

    // 取走写盘失败事件计数（界面只提示一次，避免反复刷）
    public int consumeDraftFailures() {
        return this.journal.consumeFailures();
    }

    // 取走「恢复了几条草稿」的提示计数（只提示一次）
    public int consumeRestoredCount() {
        int count = this.restoredCount;
        this.restoredCount = 0;
        return count;
    }

    // 待玩家处理的冲突（保持目标顺序）
    public List<DraftConflict> conflicts() {
        return new ArrayList<>(this.conflicts.values());
    }

    // 玩家选择：keepDraft=true 保留草稿（把基线重设到服务端当前内容），false 用服务端版本并丢弃草稿
    public void resolveConflict(String id, boolean keepDraft) {
        if (this.conflicts.remove(id) == null) {
            return;
        }
        EditSession session = this.sessions.get(id);
        RuleSnapshotEntry entry = this.entries.get(id);
        RuleValue serverBody = entry == null || entry.effective() == null
                ? null : RuleDraft.of(entry.effective()).root();
        if (keepDraft) {
            this.captureBaseline(id, serverBody);
            // 目标已不存在时草稿转为「新建规则」；目标已存在则不再是新建
            if (serverBody == null) {
                this.createdIds.add(id);
            } else {
                this.createdIds.remove(id);
            }
            if (session != null) {
                this.journal.writeNow(snapshotOf(id, session));
            }
            return;
        }
        this.journal.drop(id);
        this.baselines.remove(id);
        if (session == null) {
            return;
        }
        if (this.createdIds.contains(id) || serverBody == null) {
            this.sessions.remove(id);
            this.createdIds.remove(id);
            return;
        }
        session.load(RuleDraft.of(categorySeeded(entry, serverBody)));
        session.clearHistory();
        this.createdIds.remove(id);
        this.captureBaseline(id, serverBody);
    }

    // 玩家确认放弃全部草稿：删盘且本模型不再写盘
    public void discardAllDrafts() {
        this.draftsDiscarded = true;
        this.journal.dropAll();
        this.conflicts.clear();
        this.restored.clear();
        this.restoredCount = 0;
    }

    // 是否含错误级问题（筛选「问题」用）：按快照版本预计算，列表与筛选按 id 直接查询
    public boolean hasError(String id) {
        return this.errorIds.contains(id);
    }

    // 汇总本快照的错误级问题：条目级与快照级问题各扫一遍，之后按 id 查询
    private void collectErrorIds(RuleSnapshot snapshot) {
        this.errorIds.clear();
        for (RuleSnapshotEntry entry : snapshot.entries()) {
            for (RuleIssue issue : entry.issues()) {
                if (RuleIssue.SEVERITY_ERROR.equals(issue.severity())) {
                    this.errorIds.add(entry.id().toString());
                    break;
                }
            }
        }
        if (snapshot.issues() == null) {
            return;
        }
        for (RuleIssue issue : snapshot.issues()) {
            if (RuleIssue.SEVERITY_ERROR.equals(issue.severity()) && issue.ruleId() != null) {
                this.errorIds.add(issue.ruleId());
            }
        }
    }
}
