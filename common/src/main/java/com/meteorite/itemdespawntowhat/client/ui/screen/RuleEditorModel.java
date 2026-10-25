package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.JsonObject;
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
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
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

/**
 * 编辑页数据模型：把服务端快照映射成「按规则 id 的草稿会话」。
 * 所有字段与结构编辑都必须经过 {@link EditSession#apply}，保证每个编辑入口都有撤销标签。
 * <p>内容只读（数据包来源）的条目打开的是沙盒会话：内容只在界面生命周期内存在，
 * 既不落盘也不进变更集；但本服启停意图独立于内容保存在本模型与草稿文件里，
 * 因此内容只读的条目同样能改启停、复制为新规则。</p>
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
    // 本服启停意图（按 id）：内容只读的条目也能改启停，意图必须独立于沙盒内容存在
    private final LinkedHashMap<String, RuleEdit.Action> enableIntents = new LinkedHashMap<>();
    // 启停意图变更计数：意图不推进会话修订号，列表显示缓存据此判断失效
    private int intentRevision;

    // 草稿落盘日志（跨界面关闭 / 断线 / 重启恢复）
    private final DraftJournal journal = new DraftJournal(new DraftStore());
    // 从磁盘读回、等待绑定到服务端快照的草稿（按目标 id）
    private final LinkedHashMap<String, PersistedDraft> restored = new LinkedHashMap<>();
    // 草稿建立时的服务端整条规则基线（按目标 id，缺席表示当时服务端没有该规则）
    // 只保留 JSON 形态：建立基线时转换一次，落盘快照直接复用，避免每次写盘都重新派生整棵树
    private final LinkedHashMap<String, JsonObject> baselineJson = new LinkedHashMap<>();
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
        // 沙盒会话（内容只读）静默回滚到服务端内容：沙盒内容本来就不可提交，
        // 快照刷新不需要征求确认，也不能借此丢掉尚未应用的启停意图
        var iterator = this.sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var current = iterator.next();
            String id = current.getKey();
            EditSession session = current.getValue();
            if (isReadOnly(id) && !this.createdIds.contains(id)) {
                RuleValue body = entryBody(id);
                if (body == null) {
                    iterator.remove();
                    if (!this.enableIntents.containsKey(id)) {
                        this.journal.drop(id);
                    }
                    continue;
                }
                // 玩家正在沙盒里改内容时保留会话，等离开编辑页再统一丢弃
                if (!session.isDirty()) {
                    session.load(RuleDraft.of(categorySeeded(this.entries.get(id), body)));
                    session.clearHistory();
                }
                continue;
            }
            // 未改动且非本次新建的会话重新绑定到服务端内容
            if (this.createdIds.contains(id) || session.isDirty()) {
                continue;
            }
            RuleSnapshotEntry entry = this.entries.get(id);
            RuleValue body = entryBody(id);
            if (body == null) {
                iterator.remove();
                continue;
            }
            session.load(RuleDraft.of(categorySeeded(entry, body)));
            session.clearHistory();
        }
        this.createdIds.removeIf(id -> !this.sessions.containsKey(id));
        this.loaded = true;
        return true;
    }

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

    /**
     * 某条规则的内容是否只读（内置或世界数据包来源）。
     *
     * <p>只读只约束内容：条目仍可在编辑页里沙盒编辑，也能改本服启停与复制；
     * 沙盒内容不落盘、不进变更集，删除也仍然拒绝。</p>
     *
     * <p>本次新建与已不存在的目标按可写处理；服务端快照未到达时也按可写处理，
     * 由界面原有的冻结判定兜底。</p>
     *
     * @param id 规则 id
     * @return true 表示内容改动只存在于当前界面会话，不会保存
     */
    public boolean isReadOnly(String id) {
        RuleSnapshotEntry entry = this.entries.get(id);
        return entry != null && entry.readOnly();
    }

    // ---- 本服启停状态（默认启用 / 本服覆盖 / 恢复默认） ----

    /**
     * 某条规则的默认启用状态：规则 JSON 的 {@code enabled} 字段。
     *
     * <p>可写条目在已打开会话时读草稿内容（表单里的改动属于内容改动）；只读条目的会话是沙盒，
     * 其内容改动不会保存、也不代表服务端状态，因此仍读服务端快照，避免界面假象污染生效状态。</p>
     *
     * @param id 规则 id
     * @return 默认启用状态
     */
    public boolean defaultEnabled(String id) {
        EditSession session = this.sessions.get(id);
        if (session != null && !isReadOnly(id)) {
            return session.draft().getBoolean(RuleFields.ENABLED, true);
        }
        RuleSnapshotEntry entry = this.entries.get(id);
        return entry == null || entry.defaultEnabled();
    }

    /**
     * 某条规则在本服的生效启用状态：有未应用的意图时按意图解析，否则本服覆盖优先于默认启用。
     *
     * @param id 规则 id
     * @return 服务端视角下的生效启用状态
     */
    public boolean effectiveEnabled(String id) {
        RuleEdit.Action intent = this.enableIntents.get(id);
        if (intent != null) {
            return switch (intent) {
                case ENABLE -> true;
                case DISABLE -> false;
                // RESET 表示本服不再保留覆盖，回到内容里的默认启用
                default -> defaultEnabled(id);
            };
        }
        RuleSnapshotEntry entry = this.entries.get(id);
        if (entry != null && entry.enabledOverride() != null) {
            return entry.enabledOverride();
        }
        return defaultEnabled(id);
    }

    /**
     * 某条规则待提交的启停意图。
     *
     * @param id 规则 id
     * @return enable/disable/reset；null 表示没有未应用的启停改动
     */
    public @Nullable RuleEdit.Action enableIntent(String id) {
        return this.enableIntents.get(id);
    }

    /**
     * 某条规则是否带本服启用覆盖（含未应用的意图）：列表行「（本服）」后缀与「恢复默认」按钮据此显示。
     *
     * @param id 规则 id
     * @return true 表示生效状态来自本服记录而非默认启用
     */
    public boolean hasEnabledOverride(String id) {
        RuleEdit.Action intent = this.enableIntents.get(id);
        if (intent != null) {
            return intent != RuleEdit.Action.RESET;
        }
        RuleSnapshotEntry entry = this.entries.get(id);
        return entry != null && entry.enabledOverride() != null;
    }

    /**
     * 切换某条规则在本服的启用状态。
     *
     * <p>目标值等于默认启用时改用 reset 消除冗余覆盖；本次新建的规则在服务端还没有条目，
     * 直接改内容里的 {@code enabled} 字段，否则意图要等内容保存后才有着落。</p>
     *
     * @param id 规则 id
     * @return 实际采用的启停动作；条目不存在且没有会话时为 null
     */
    public @Nullable RuleEdit.Action toggleEnabled(String id) {
        RuleSnapshotEntry entry = this.entries.get(id);
        if (entry == null || this.createdIds.contains(id)) {
            EditSession session = this.sessions.get(id);
            if (session == null) {
                return null;
            }
            boolean enabled = !session.draft().getBoolean(RuleFields.ENABLED, true);
            session.apply(EditSession.OP_SET_FIELD,
                    () -> session.draft().setBoolean(RuleFields.ENABLED, enabled));
            return enabled ? RuleEdit.Action.ENABLE : RuleEdit.Action.DISABLE;
        }
        // 列表页直接点启停时还没有会话：先按服务端内容打开，草稿才会带上内容与基线
        ensureSessionForIntent(id);
        boolean target = !effectiveEnabled(id);
        if (target == defaultEnabled(id)) {
            // 回到默认启用即消除覆盖；服务端本来没有覆盖时连意图都不需要留
            if (entry.enabledOverride() == null) {
                this.enableIntents.remove(id);
                this.intentRevision++;
                this.republishIntent(id);
                return RuleEdit.Action.RESET;
            }
            this.enableIntents.put(id, RuleEdit.Action.RESET);
            this.intentRevision++;
            this.republishIntent(id);
            return RuleEdit.Action.RESET;
        }
        RuleEdit.Action action = target ? RuleEdit.Action.ENABLE : RuleEdit.Action.DISABLE;
        this.enableIntents.put(id, action);
        this.intentRevision++;
        this.republishIntent(id);
        return action;
    }

    /**
     * 恢复某条规则的本服启用状态为默认：清除本服覆盖。
     *
     * @param id 规则 id
     */
    public void resetEnabled(String id) {
        ensureSessionForIntent(id);
        RuleSnapshotEntry entry = this.entries.get(id);
        if (entry == null || entry.enabledOverride() == null) {
            this.enableIntents.remove(id);
        } else {
            this.enableIntents.put(id, RuleEdit.Action.RESET);
        }
        this.intentRevision++;
        this.republishIntent(id);
    }

    /**
     * 按生效状态设置某条规则的启停意图。
     *
     * <p>复制为新规则时用它把源条目的本服覆盖带到新 id：副本的内容可能与源不同，
     * 只有写同样的意图才能让副本的生效状态与源一致。</p>
     *
     * @param id 规则 id
     * @param enabled 期望的生效启用状态
     */
    public void setEnableIntent(String id, boolean enabled) {
        ensureSessionForIntent(id);
        if (enabled == defaultEnabled(id)) {
            this.enableIntents.remove(id);
        } else {
            this.enableIntents.put(id, enabled ? RuleEdit.Action.ENABLE : RuleEdit.Action.DISABLE);
        }
        this.intentRevision++;
        this.republishIntent(id);
    }

    // 启停意图变更计数：列表显示缓存按它判断是否需要重建
    public int intentRevision() {
        return this.intentRevision;
    }

    /**
     * 丢弃沙盒会话（内容只读条目的会话）：内容回到服务端版本，启停意图保留。
     *
     * <p>沙盒内容既不落盘也不进变更集，离开编辑页、切换条目或关闭界面时就该消失；
     * 保留意图是为了不丢玩家刚点过的启停操作。</p>
     *
     * @param keep 需要保留会话的规则 id（正在编辑的条目）；null 表示全部丢弃
     */
    public void dropSandboxSessions(@Nullable String keep) {
        for (String id : new ArrayList<>(this.sessions.keySet())) {
            if (id.equals(keep) || !isReadOnly(id) || this.createdIds.contains(id)) {
                continue;
            }
            RuleValue body = entryBody(id);
            boolean keepDraft = this.enableIntents.containsKey(id);
            if (body == null) {
                this.sessions.remove(id);
            } else {
                EditSession session = this.sessions.get(id);
                if (session == null) {
                    continue;
                }
                session.load(RuleDraft.of(categorySeeded(this.entries.get(id), body)));
                session.clearHistory();
            }
            if (!keepDraft) {
                this.journal.drop(id);
            }
        }
    }

    // 记录启停意图前确保可写条目已有会话：草稿落盘要带服务端内容与基线，
    // 否则重启恢复会把「只有意图、内容为空」的草稿当成凭空出现的空规则并误判冲突。
    private void ensureSessionForIntent(String id) {
        if (this.createdIds.contains(id) || this.sessions.containsKey(id) || isReadOnly(id)) {
            return;
        }
        openSession(id);
    }

    // 意图变化后同步草稿日志：只读条目没有可写内容，草稿文件只为意图存在；
    // 可写条目没有会话说明快照里没有可读内容，写出的草稿只会变成空内容假象，直接丢弃
    private void republishIntent(String id) {
        if (this.draftsDiscarded) {
            return;
        }
        EditSession session = this.sessions.get(id);
        if (session == null && !isReadOnly(id)) {
            this.journal.drop(id);
            return;
        }
        if (this.isJournalWorthy(id, session)) {
            this.journal.markDirty(snapshotOf(id, session));
        } else {
            this.journal.drop(id);
        }
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
        // 基线取服务端「生效视图」；没有生效视图时记为不存在（快照内部会做一次独立拷贝）
        this.captureBaseline(id, entry.effective());
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
        this.baselineJson.remove(id);
        this.conflicts.remove(id);
        if (this.enableIntents.remove(id) != null) {
            this.intentRevision++;
        }
        this.journal.drop(id);
    }

    // 标记某条规则待删除；只读数据包条目不允许改变删除标记
    public void markDeleted(String id, boolean deleted) {
        if (isReadOnly(id)) {
            return;
        }
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
            if (DebugTestRules.isTestRule(id) && !isDeleteMarked(id)) {
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
                if (isDeleteMarked(id) && openSession(id) != null) {
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
            if (isDeleteMarked(id)) {
                continue;
            }
            if (openSession(id) != null) {
                markDeleted(id, true);
                affected++;
            }
        }
        return affected;
    }

    /**
     * 某条规则是否已在草稿中被标记删除。
     *
     * <p>删除只是草稿状态：服务端快照在应用前仍包含该条目，列表据此提前隐藏，
     * 应用成功后由新快照完成真正的移除。未打开会话的规则按未删除处理。</p>
     *
     * @param id 规则 id
     * @return 已打开会话且草稿标记为删除时为 true
     */
    public boolean isDeleteMarked(String id) {
        EditSession session = this.sessions.get(id);
        return session != null && session.draft().isDeleted();
    }

    // 存在改动的规则 id（保持服务端顺序）：内容改动与启停意图都算；沙盒内容不算
    public List<String> dirtyRuleIds() {
        List<String> ids = new ArrayList<>();
        for (String id : ruleIds()) {
            if (this.enableIntents.containsKey(id) || hasContentDirty(id)) {
                ids.add(id);
            }
        }
        // 服务端已移除的条目仍可能留着未应用的启停意图，提交时一并带上
        for (String id : this.enableIntents.keySet()) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    // 是否存在任何改动（页头每帧查询：只遍历已打开的会话，不构造 id 列表）
    public boolean hasDirty() {
        if (!this.enableIntents.isEmpty()) {
            return true;
        }
        for (String id : this.sessions.keySet()) {
            if (hasContentDirty(id)) {
                return true;
            }
        }
        return false;
    }

    // 内容是否有待应用的改动：沙盒内容只存在于界面，永远不算
    private boolean hasContentDirty(String id) {
        EditSession session = this.sessions.get(id);
        return session != null && !isReadOnly(id) && (session.isDirty() || this.createdIds.contains(id));
    }

    // 装配变更集 JSON；无改动返回 null
    public @Nullable String buildChangeSetJson() {
        EditorChangeSet changeSet = new EditorChangeSet(this.workspace.contextRevision());
        for (String id : dirtyRuleIds()) {
            EditSession session = this.sessions.get(id);
            // 沙盒内容不进变更集：只读条目只提交启停意图
            if (session != null && hasContentDirty(id)) {
                RuleDraft draft = session.draft();
                RuleSnapshotEntry entry = this.entries.get(id);
                // 任何含覆盖副本（mixed）或仅覆盖的条目都能通过删除覆盖层表达删除；
                // 数据包基底本身不落覆盖层，不能写成带 delete 的完整规则体。
                boolean overlayOwned = entry != null && !RuleSnapshotEntry.ORIGIN_DATAPACK.equals(entry.origin());
                if (draft.isDeleted() && !this.createdIds.contains(id) && overlayOwned) {
                    changeSet.delete(id);
                } else {
                    changeSet.upsert(draft);
                }
            }
            // 启停动作与内容动作同批提交：服务端先写内容再落状态
            RuleEdit.Action intent = this.enableIntents.get(id);
            if (intent != null) {
                changeSet.state(id, intent);
            }
        }
        return changeSet.isEmpty() ? null : changeSet.serialize();
    }

    // 保存成功后收敛草稿：服务端内容成为新的基线，已提交的启停意图转为服务端覆盖
    public void acceptSaved() {
        for (String id : dirtyRuleIds()) {
            EditSession session = this.sessions.get(id);
            if (this.enableIntents.remove(id) != null) {
                this.intentRevision++;
            }
            // 只读条目的内容不进变更集，收敛时只清掉已应用的意图
            if (session == null || isReadOnly(id)) {
                this.journal.drop(id);
                continue;
            }
            session.load(RuleDraft.of(session.draft().root()));
            session.clearHistory();
            this.createdIds.remove(id);
            // 服务端已接受的内容成为新的草稿基线，并删掉落盘草稿
            this.captureBaseline(id, session.draft().toJson());
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
            // 只读条目的内容属沙盒、无法提交：只恢复本服启停意图，不建会话也不判内容冲突
            if (isReadOnly(id)) {
                RuleEdit.Action intent = stored.enableIntent();
                if (intent == null) {
                    this.journal.drop(id);
                    continue;
                }
                this.enableIntents.put(id, intent);
                this.intentRevision++;
                this.journal.markDirty(snapshotOf(id, null));
                this.restoredCount++;
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
            if (stored.enableIntent() != null) {
                this.enableIntents.put(id, stored.enableIntent());
                this.intentRevision++;
            }
            this.sessions.put(id, session);
            this.captureBaseline(id, entry == null ? null : entry.effective());
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

    // 是否值得落盘：有内容改动、本次新建或存在启停意图；只读条目的沙盒内容不产生草稿。
    // 可写条目没有会话说明服务端没有可读内容，只写意图会落出「内容为空」的假草稿，重启时被误判冲突。
    private boolean isJournalWorthy(String id, @Nullable EditSession session) {
        if (this.enableIntents.containsKey(id)) {
            return session != null || isReadOnly(id);
        }
        return session != null && !isReadOnly(id) && (session.isDirty() || this.createdIds.contains(id));
    }

    // 当前草稿快照（含基线与启停意图，供落盘与冲突比较）
    private PersistedDraft snapshotOf(String id, @Nullable EditSession session) {
        // 基线 JSON 在建立基线时已转换好且此后不再修改，这里直接交给快照，不再重复深拷贝
        JsonObject baseline = this.baselineJson.get(id);
        boolean readOnly = isReadOnly(id);
        // 沙盒内容不落盘：只读条目只保留启停意图，草稿内容写空对象
        JsonObject content = readOnly || session == null ? new JsonObject() : session.draft().toJson();
        boolean deleted = !readOnly && session != null && session.draft().isDeleted();
        return new PersistedDraft(
                id,
                this.createdIds.contains(id),
                deleted,
                this.enableIntents.get(id),
                baseline,
                content,
                this.snapshotVersion,
                this.workspace.contextRevision(),
                System.currentTimeMillis());
    }

    // 记录草稿建立时的服务端整条规则内容：这里拷一份独立快照，之后不再随快照对象变化
    private void captureBaseline(String id, @Nullable JsonObject serverBody) {
        if (serverBody == null) {
            this.baselineJson.remove(id);
            return;
        }
        this.baselineJson.put(id, serverBody.deepCopy());
    }

    // 节流写盘（界面每帧调用）
    public void tickPersistence() {
        this.journal.flushDue(System.currentTimeMillis());
    }

    // 立即交出全部草稿的写盘任务（界面关闭 / 保存后调用，保证不丢）
    public void persistNow() {
        if (this.draftsDiscarded) {
            return;
        }
        // 只有启停意图的条目没有会话，也要落盘
        Set<String> ids = new LinkedHashSet<>(this.sessions.keySet());
        ids.addAll(this.enableIntents.keySet());
        for (String id : ids) {
            EditSession session = this.sessions.get(id);
            if (this.isJournalWorthy(id, session)) {
                this.journal.writeNow(snapshotOf(id, session));
            }
        }
        // 交出剩余条目并等写入完成：这里可能紧接着关闭界面甚至退出游戏，草稿必须已经落盘；
        // 等待有上限，磁盘异常慢时宁可放弃等待也不卡住客户端线程。
        // 这次冲刷同时是写盘失败的显式重试入口（后台不会自动循环重试）
        this.journal.drain(DraftJournal.DRAIN_WAIT_MS);
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
        // 只读来源不能保留本地草稿：冲突解决只能回到服务端内容，仅保留启停意图
        if (isReadOnly(id)) {
            if (this.enableIntents.containsKey(id)) {
                this.journal.markDirty(snapshotOf(id, null));
            } else {
                this.journal.drop(id);
            }
            return;
        }
        EditSession session = this.sessions.get(id);
        RuleSnapshotEntry entry = this.entries.get(id);
        RuleValue serverBody = entry == null || entry.effective() == null
                ? null : RuleDraft.of(entry.effective()).root();
        if (keepDraft) {
            this.captureBaseline(id, entry == null ? null : entry.effective());
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
        this.baselineJson.remove(id);
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
        this.captureBaseline(id, entry.effective());
    }

    // 玩家确认放弃全部草稿：删盘且本模型不再写盘
    public void discardAllDrafts() {
        this.draftsDiscarded = true;
        this.journal.dropAll();
        this.conflicts.clear();
        this.restored.clear();
        this.enableIntents.clear();
        this.intentRevision++;
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
