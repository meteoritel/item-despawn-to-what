package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.BuiltinEditorDefaults;
import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.EditorChangeSet;
import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
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
import java.util.Set;

/***
 * 编辑页数据模型：把服务端快照映射成「按规则 id 的草稿会话」。
 * 所有字段与结构编辑都必须经过 {@link EditSession#apply}，保证每个编辑入口都有撤销标签（契约 §5，task-9 前置）。
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
    // 待「恢复原始版本」（删除覆盖层条目）的规则 id
    private final Set<String> restoreIds = new LinkedHashSet<>();
    // 已消费的快照
    private @Nullable RuleSnapshot seenSnapshot;
    // 已消费的快照版本
    private int snapshotVersion = -1;
    // 是否至少成功加载过一次快照
    private boolean loaded;

    // 草稿落盘日志（P6：跨界面关闭 / 断线 / 重启恢复）
    private final DraftJournal journal = new DraftJournal(new DraftStore());
    // 从磁盘读回、等待绑定到服务端快照的草稿（按目标 id）
    private final LinkedHashMap<String, PersistedDraft> restored = new LinkedHashMap<>();
    // 草稿建立时的服务端整条规则基线（null 表示当时服务端没有该规则）
    private final LinkedHashMap<String, JsonObject> baselines = new LinkedHashMap<>();
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
            if (entry == null || entry.effective() == null) {
                iterator.remove();
                continue;
            }
            current.getValue().load(RuleDraft.of(entry.effective()));
            current.getValue().clearHistory();
        }
        this.createdIds.removeIf(id -> !this.sessions.containsKey(id));
        this.restoreIds.removeIf(id -> !this.sessions.containsKey(id));
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

    // 某条规则是否标记为恢复原始版本
    public boolean isRestoring(String id) {
        return this.restoreIds.contains(id);
    }

    // 打开（必要时懒创建）某条规则的草稿会话
    public @Nullable EditSession openSession(String id) {
        EditSession existing = this.sessions.get(id);
        if (existing != null) {
            return existing;
        }
        RuleSnapshotEntry entry = this.entries.get(id);
        JsonObject body = null;
        if (entry != null) {
            if (entry.effective() != null) {
                body = entry.effective().deepCopy();
            } else if (entry.base() != null) {
                body = entry.base().deepCopy();
            }
        }
        if (body == null) {
            return null;
        }
        EditSession session = new EditSession(id, RuleDraft.of(body));
        this.sessions.put(id, session);
        this.captureBaseline(id, entry == null ? null : entry.effective());
        this.bindSession(id, session);
        return session;
    }

    // 新建一条规则草稿
    public EditSession createSession(String id, JsonObject body) {
        // 初始态用「带 id 的默认骨架」，再把模板/复制内容作为一次可撤销的「新建规则」写入：
        // 撤销新建后仍是带 id 的合法骨架，而不是空对象
        RuleDraft draft = RuleDraft.of(BuiltinEditorDefaults.ruleBody(id));
        EditSession session = new EditSession(id, draft);
        session.apply(EditSession.OP_CREATE_RULE, () -> draft.replaceWith(body));
        this.sessions.put(id, session);
        this.createdIds.add(id);
        // 新建规则在服务端还没有内容，基线为「不存在」
        this.captureBaseline(id, null);
        this.bindSession(id, session);
        return session;
    }

    // 关闭某条规则的草稿（放弃未保存改动，同时删掉落盘草稿）
    public void dropSession(String id) {
        this.sessions.remove(id);
        this.createdIds.remove(id);
        this.restoreIds.remove(id);
        this.baselines.remove(id);
        this.conflicts.remove(id);
        this.journal.drop(id);
    }

    // 标记某条规则为「恢复原始版本」（删除覆盖层条目）
    public void markRestore(String id) {
        if (this.sessions.containsKey(id)) {
            this.restoreIds.add(id);
        }
    }

    // 标记某条规则待删除
    public void markDeleted(String id, boolean deleted) {
        EditSession session = this.sessions.get(id);
        if (session != null) {
            session.apply(EditSession.OP_DELETE_RULE, () -> session.draft().setDeleted(deleted));
        }
    }

    // 存在改动的规则 id（保持服务端顺序）
    public List<String> dirtyRuleIds() {
        List<String> ids = new ArrayList<>();
        for (String id : ruleIds()) {
            EditSession session = this.sessions.get(id);
            if (session != null && (session.isDirty() || this.createdIds.contains(id)
                    || this.restoreIds.contains(id))) {
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
            if (this.restoreIds.contains(id)) {
                changeSet.delete(id);
                continue;
            }
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
            if (this.restoreIds.contains(id)) {
                this.sessions.remove(id);
                this.createdIds.remove(id);
                this.baselines.remove(id);
                this.conflicts.remove(id);
                this.journal.drop(id);
                continue;
            }
            session.load(RuleDraft.of(session.draft().toJson()));
            session.clearHistory();
            this.createdIds.remove(id);
            // 服务端已接受的内容成为新的草稿基线，并删掉落盘草稿
            this.captureBaseline(id, session.draft().toJson());
            this.conflicts.remove(id);
            this.journal.drop(id);
        }
        this.restoreIds.clear();
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
            JsonObject serverBody = entry == null || entry.effective() == null
                    ? null : entry.effective().deepCopy();
            // 整条规则比较（不做逐字段合并）：目标消失、同名新规则、内容被改都会提示
            if (stored.created() && serverBody != null) {
                this.conflicts.put(id, new DraftConflict(id, DraftConflict.Reason.TARGET_EXISTS));
            } else if (!stored.created() && serverBody == null) {
                this.conflicts.put(id, new DraftConflict(id, DraftConflict.Reason.TARGET_MISSING));
            } else if (serverBody != null && (stored.baseline() == null || !stored.baseline().equals(serverBody))) {
                // 基线缺失（旧版草稿）或内容确实变了：保守判为远端已变，交给玩家选
                this.conflicts.put(id, new DraftConflict(id, DraftConflict.Reason.REMOTE_CHANGED));
            }
            RuleDraft draft = serverBody == null ? RuleDraft.of(new JsonObject()) : RuleDraft.of(serverBody);
            draft.replaceWith(stored.draft());
            draft.setDeleted(stored.deleted());
            EditSession session = new EditSession(id, draft);
            if (stored.created()) {
                this.createdIds.add(id);
            }
            if (stored.restore()) {
                this.restoreIds.add(id);
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
        // 「恢复原始版本」只改标记不改 JSON，把标记接到会话历史上才能撤销
        session.setFlagBridge(() -> this.restoreIds.contains(id), value -> {
            if (value) {
                this.restoreIds.add(id);
            } else {
                this.restoreIds.remove(id);
            }
        });
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

    // 是否值得落盘：有改动、或本次新建、或标记恢复原始版本
    private boolean isJournalWorthy(String id, EditSession session) {
        return session.isDirty() || this.createdIds.contains(id) || this.restoreIds.contains(id);
    }

    // 当前草稿快照（含基线，供落盘与冲突比较）
    private PersistedDraft snapshotOf(String id, EditSession session) {
        JsonObject baseline = this.baselines.get(id);
        return new PersistedDraft(
                id,
                this.createdIds.contains(id),
                this.restoreIds.contains(id),
                session.draft().isDeleted(),
                baseline == null ? null : baseline.deepCopy(),
                session.draft().toJson(),
                this.snapshotVersion,
                this.workspace.contextRevision(),
                System.currentTimeMillis());
    }

    // 记录草稿建立时的服务端整条规则内容
    private void captureBaseline(String id, @Nullable JsonObject serverBody) {
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

    // 待写盘的草稿数量（诊断用）
    public int pendingDraftCount() {
        return this.journal.trackedIds().size();
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

    public boolean hasConflict() {
        return !this.conflicts.isEmpty();
    }

    // 玩家选择：keepDraft=true 保留草稿（把基线重设到服务端当前内容），false 用服务端版本并丢弃草稿
    public void resolveConflict(String id, boolean keepDraft) {
        if (this.conflicts.remove(id) == null) {
            return;
        }
        EditSession session = this.sessions.get(id);
        RuleSnapshotEntry entry = this.entries.get(id);
        JsonObject serverBody = entry == null || entry.effective() == null ? null : entry.effective();
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
            this.restoreIds.remove(id);
            return;
        }
        session.load(RuleDraft.of(serverBody.deepCopy()));
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

    // 快照级问题（服务端汇总）
    public List<RuleIssue> snapshotIssues() {
        RuleSnapshot snapshot = this.workspace.snapshot();
        return snapshot == null || snapshot.issues() == null ? List.of() : List.copyOf(snapshot.issues());
    }

    // 某条规则的服务端问题
    public List<RuleIssue> issuesFor(String id) {
        RuleSnapshotEntry entry = this.entries.get(id);
        return entry == null || entry.issues() == null ? List.of() : List.copyOf(entry.issues());
    }

    // 是否含错误级问题（筛选「问题」用）
    public boolean hasError(String id) {
        for (RuleIssue issue : issuesFor(id)) {
            if (RuleIssue.SEVERITY_ERROR.equals(issue.severity())) {
                return true;
            }
        }
        for (RuleIssue issue : snapshotIssues()) {
            if (RuleIssue.SEVERITY_ERROR.equals(issue.severity()) && id.equals(issue.ruleId())) {
                return true;
            }
        }
        return false;
    }
}
