package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RulePackExporter;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshotEntry;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 管理页的列表展示快照缓存：按规则 id 保存 {@link RuleListDisplaySnapshot}，一次刷新里每条规则只取一次规则体。
 *
 * <p>失效判定用「内容版本」而不是时间：快照版本（服务端下发新快照视为全部失效）、草稿修订
 * （{@link EditSession#revision()}，每次可撤销编辑、撤销重做与草稿载入都会递增）、
 * 启停意图修订与本次新建标记。
 * 因此搜索逐键刷新只重算过滤与分组，未变化条目的标题、状态、
 * 预览与提示直接复用，渲染时也不再触发规则体读取。</p>
 *
 * <p>缓存只为显示服务：不保存草稿，不做校验，也不改变任何编辑语义。</p>
 */
final class RuleListDisplayCache {

    // 无草稿会话时的修订占位值
    private static final long NO_SESSION = -1L;
    // 状态文案 key
    private static final String STATUS_ERROR = TypeLabels.UI_PREFIX + "recipe.status.error";
    private static final String STATUS_PENDING = TypeLabels.UI_PREFIX + "recipe.status.pending";
    private static final String STATUS_DISABLED = TypeLabels.UI_PREFIX + "recipe.status.disabled";
    private static final String STATUS_READONLY = TypeLabels.UI_PREFIX + "recipe.status.readonly";
    private static final String STATUS_WITH_READONLY = TypeLabels.UI_PREFIX + "recipe.status.with_readonly";
    // 本服启用覆盖的行内标记：与只读标记一样作为状态后缀，说明生效状态来自本服记录
    private static final String STATUS_LOCAL = TypeLabels.UI_PREFIX + "recipe.status.local";
    private static final String STATUS_WITH_LOCAL = TypeLabels.UI_PREFIX + "recipe.status.with_local";

    private final RuleEditorModel model;
    private final Map<String, Cached> snapshots = new LinkedHashMap<>();
    // 展示快照重建次数（每次重建只读一次规则体），只供首载分段观测说明单次取体
    private long bodyFetches;

    RuleListDisplayCache(RuleEditorModel model) {
        this.model = model;
    }

    /**
     * 取某条规则的展示快照：内容版本一致时直接复用，否则重建一次。
     *
     * @param id 规则 id
     * @return 该规则当前的展示快照；条目不存在时返回标题回退、无规则体的快照
     */
    RuleListDisplaySnapshot snapshot(String id) {
        int version = model.snapshotVersion();
        long revision = sessionRevision(id);
        int intents = model.intentRevision();
        boolean created = model.isCreated(id);
        Cached cached = snapshots.get(id);
        if (cached != null && cached.current(version, revision, intents, created)) {
            return cached.snapshot();
        }
        RuleListDisplaySnapshot built = build(id);
        snapshots.put(id, new Cached(built, version, revision, intents, created));
        return built;
    }

    /**
     * 丢弃已不存在于快照与草稿中的条目，让缓存随规则集合收敛。
     *
     * @param ids 当前全部规则 id
     */
    void retain(Collection<String> ids) {
        snapshots.keySet().retainAll(new HashSet<>(ids));
    }

    // 展示快照重建次数（观测用）
    long bodyFetches() {
        return this.bodyFetches;
    }

    // 草稿修订号；没有会话时返回占位值，与「修订 0」区分
    private long sessionRevision(String id) {
        EditSession session = model.session(id);
        return session == null ? NO_SESSION : session.revision();
    }

    // 构建一份快照：规则体只在这里读取一次，其余展示字段都由它派生
    private RuleListDisplaySnapshot build(String id) {
        RuleSnapshotEntry entry = model.entry(id);
        EditSession session = model.session(id);
        RuleValue body = model.displayBody(id);
        this.bodyFetches++;
        boolean error = model.hasError(id);
        boolean deleteMarked = session != null && session.draft().isDeleted();
        // 行是否启用看生效状态：本服覆盖与未应用意图都算在内
        boolean active = !deleteMarked && model.effectiveEnabled(id);
        boolean unfinished = isUnfinished(id, session);
        boolean issues = entry != null && !entry.issues().isEmpty();
        boolean readOnly = entry != null && entry.readOnly();
        Component state = stateComponent(error, active, readOnly, contentPending(id, session),
                model.enableIntent(id) != null, model.hasEnabledOverride(id));
        return new RuleListDisplaySnapshot(
                body,
                RuleListDisplaySnapshot.titleOf(id, body),
                entry == null ? null : entry.category(),
                entry == null ? RuleSnapshotEntry.ORIGIN_OVERLAY : entry.origin(),
                unfinished,
                isExportable(id, entry, body, deleteMarked, unfinished, issues, error),
                active,
                error,
                issues,
                RulePreviewIcons.unavailable(body),
                state == null ? Component.empty() : state,
                state != null);
    }

    // 未完成分组只表达待应用的改动：内容改动、本次新建或未应用的启停意图；沙盒内容不算
    private boolean isUnfinished(String id, @Nullable EditSession session) {
        return model.isCreated(id) || model.enableIntent(id) != null
                || contentPending(id, session);
    }

    // 内容是否有待应用的改动：沙盒内容只存在于界面，永远不算
    private boolean contentPending(String id, @Nullable EditSession session) {
        return session != null && !model.isReadOnly(id) && session.isDirty();
    }

    // 可导出条目：只导出生效且无问题的已保存规则；未保存草稿、有问题条目与非法条目不导出
    private boolean isExportable(String id, @Nullable RuleSnapshotEntry entry, @Nullable RuleValue body,
                                 boolean deleteMarked, boolean unfinished, boolean issues, boolean error) {
        if (body == null || deleteMarked || unfinished || issues || error) {
            return false;
        }
        if (entry == null) {
            return false;
        }
        // 导出要求 id 能写成数据包文件；这条判定与导出预检同一来源，弹窗计数才与实际结果一致
        if (!RulePackExporter.isWritableId(id)) {
            return false;
        }
        return RuleSnapshotEntry.STATUS_ACTIVE.equals(entry.status())
                || RuleSnapshotEntry.STATUS_DISABLED.equals(entry.status());
    }

    // 状态用明确文字，正常启用不占位；错误与待应用改动优先于停用；
    // 本服标记与只读标记并列追加在状态右侧，保证数据包条目与带覆盖条目始终有可见标识
    private @Nullable Component stateComponent(boolean error, boolean active, boolean readOnly,
                                               boolean contentPending, boolean pendingIntent, boolean local) {
        Component state = baseStateComponent(error, active, contentPending || pendingIntent);
        if (local) {
            Component localMark = Component.translatable(STATUS_LOCAL);
            state = state == null ? localMark : Component.translatable(STATUS_WITH_LOCAL, state, localMark);
        }
        if (!readOnly) {
            return state;
        }
        Component readonly = Component.translatable(STATUS_READONLY);
        return state == null ? readonly : Component.translatable(STATUS_WITH_READONLY, state, readonly);
    }

    private @Nullable Component baseStateComponent(boolean error, boolean active, boolean pending) {
        if (error) {
            return Component.translatable(STATUS_ERROR);
        }
        if (pending) {
            return Component.translatable(STATUS_PENDING);
        }
        return active ? null : Component.translatable(STATUS_DISABLED);
    }

    // 缓存条目与它建立时的内容版本，变更即失效
    private record Cached(RuleListDisplaySnapshot snapshot, int snapshotVersion, long sessionRevision,
                          int intentRevision, boolean created) {

        private boolean current(int version, long revision, int intents, boolean isCreated) {
            return snapshotVersion == version && sessionRevision == revision
                    && intentRevision == intents && created == isCreated;
        }
    }
}
