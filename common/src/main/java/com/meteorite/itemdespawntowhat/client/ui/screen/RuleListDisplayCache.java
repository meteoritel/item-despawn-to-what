package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.EditSession;
import com.meteorite.itemdespawntowhat.client.edit.RulePackExporter;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
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
 * （{@link EditSession#revision()}，每次可撤销编辑、撤销重做与草稿载入都会递增）以及本次新建标记。
 * 因此搜索逐键刷新只重算过滤与分组，未变化条目的标题、状态、
 * 预览与提示直接复用，渲染时也不再触发规则体读取。</p>
 *
 * <p>缓存只为显示服务：不保存草稿，不做校验，也不改变任何编辑语义。</p>
 */
final class RuleListDisplayCache {

    // 无草稿会话时的修订占位值
    private static final long NO_SESSION = -1L;
    // 状态文案 key
    private static final String STATUS_ERROR = "gui.itemdespawntowhat.edit.recipe.status.error";
    private static final String STATUS_PENDING = "gui.itemdespawntowhat.edit.recipe.status.pending";
    private static final String STATUS_MASKED = "gui.itemdespawntowhat.edit.recipe.status.masked";
    private static final String STATUS_DISABLED = "gui.itemdespawntowhat.edit.recipe.status.disabled";

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
        boolean created = model.isCreated(id);
        Cached cached = snapshots.get(id);
        if (cached != null && cached.current(version, revision, created)) {
            return cached.snapshot();
        }
        RuleListDisplaySnapshot built = build(id);
        snapshots.put(id, new Cached(built, version, revision, created));
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
        boolean active = isActive(id, entry, session);
        boolean masked = entry != null && RuleSnapshotEntry.STATUS_MASKED.equals(entry.status());
        boolean deleteMarked = body != null && body.flag(RuleFields.DELETE, false);
        boolean unfinished = isUnfinished(id, session);
        boolean issues = entry != null && !entry.issues().isEmpty();
        Component state = stateComponent(session, error, active, masked);
        return new RuleListDisplaySnapshot(
                body,
                RuleListDisplaySnapshot.titleOf(id, body),
                entry == null ? null : entry.category(),
                entry == null ? RuleSnapshotEntry.ORIGIN_OVERLAY : entry.origin(),
                unfinished,
                isExportable(id, entry, body, deleteMarked, unfinished, issues, error),
                active,
                error,
                isEnabled(body),
                issues,
                RulePreviewIcons.unavailable(body),
                state == null ? Component.empty() : state,
                state != null);
    }

    // 未完成分组只表达草稿状态：本次新建或有未保存改动
    private boolean isUnfinished(String id, @Nullable EditSession session) {
        return session != null && (session.isDirty() || model.isCreated(id));
    }

    // 可导出条目：只导出生效且无问题的已保存规则；未保存草稿、有问题条目与屏蔽/非法条目不导出
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

    // 状态用明确文字，正常启用不占位；错误、草稿改动与屏蔽优先于停用
    private @Nullable Component stateComponent(@Nullable EditSession session,
                                               boolean error, boolean active, boolean masked) {
        if (error) {
            return Component.translatable(STATUS_ERROR);
        }
        if (session != null && session.isDirty()) {
            return Component.translatable(STATUS_PENDING);
        }
        if (masked) {
            return Component.translatable(STATUS_MASKED);
        }
        return active ? null : Component.translatable(STATUS_DISABLED);
    }

    // 规则体自身是否声明为启用：缺少字段按启用处理
    private static boolean isEnabled(@Nullable RuleValue body) {
        return body != null && (!body.has(RuleFields.ENABLED) || body.flag(RuleFields.ENABLED, false));
    }

    // 尚未修改的条目以快照状态为准；改动与新建条目以当前草稿为准。
    private boolean isActive(String id, @Nullable RuleSnapshotEntry entry, @Nullable EditSession session) {
        if (session != null && (session.isDirty() || model.isCreated(id))) {
            return !session.draft().isDeleted() && session.draft().getBoolean(RuleFields.ENABLED, true);
        }
        return entry == null || RuleSnapshotEntry.STATUS_ACTIVE.equals(entry.status());
    }

    // 缓存条目与它建立时的内容版本，变更即失效
    private record Cached(RuleListDisplaySnapshot snapshot, int snapshotVersion, long sessionRevision,
                          boolean created) {

        private boolean current(int version, long revision, boolean isCreated) {
            return snapshotVersion == version && sessionRevision == revision && created == isCreated;
        }
    }
}
