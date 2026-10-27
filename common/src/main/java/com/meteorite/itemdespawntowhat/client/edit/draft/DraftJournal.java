package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 草稿保留策略、基线与落盘生命周期：按业务状态决定保留或删除，再按时间窗口合并写盘。
 * <p>节流窗口 {@link #SAVE_DELAY_MS} 毫秒：连续编辑只在停顿后写一次盘，
 * 避免每次按键/每次结构变换都碰磁盘；界面关闭、会话失效等关键时刻调用
 * {@link #flushAll()} 立即写盘，保证草稿不丢。
 * <p>真正的文件操作由 {@link DraftWriter} 在一条后台线程上串行执行：本类只维护内存状态与到期时间，
 * 提交给写入者的都是主线程捕获好的 {@link PersistedDraft} 不可变快照，写盘时不再回读实时编辑状态。
 * 因此调用方可以在渲染或 tick 路径上调用本类的方法，磁盘 I/O 不会占住客户端线程。
 */
public final class DraftJournal {

    /**
     * 编辑停顿多久后写盘
     */
    public static final long SAVE_DELAY_MS = 2000L;
    /**
     * 关闭界面/存档等关键点等待写盘完成的上限
     */
    public static final long DRAIN_WAIT_MS = 1500L;

    private final DraftStore store = new DraftStore();
    // 已确认的保存基线；只在建立会话、玩家保留草稿或应用成功时推进。
    private final Map<String, JsonObject> baselines = new LinkedHashMap<>();
    // 磁盘写入的单写入者：所有 save/delete/clear 都经它按提交顺序串行执行
    private final DraftWriter writer;
    // 目标 id -> 最近一次待写/已写内容
    private final Map<String, PersistedDraft> entries = new LinkedHashMap<>();
    // 目标 id -> 允许写盘的时间点（毫秒时间戳）
    private final Map<String, Long> pending = new LinkedHashMap<>();

    /** 装配本地仓库与单写入者，界面只接触业务状态。 */
    public DraftJournal() {
        this.writer = new DraftWriter(this.store);
    }

    /** 恢复业务状态；同目标保留 saved_at 较新的一份，原始基线留给后续冲突处理。 */
    public List<RestoredDraft> restore() {
        for (PersistedDraft draft : this.store.loadAll()) {
            PersistedDraft current = this.entries.get(draft.targetId());
            if (current == null || draft.savedAt() >= current.savedAt()) {
                this.entries.put(draft.targetId(), draft);
            }
        }
        List<RestoredDraft> restored = new ArrayList<>();
        for (PersistedDraft draft : this.entries.values()) {
            if (draft.baseline() != null) {
                this.baselines.put(draft.targetId(), draft.baseline());
            }
            restored.add(new RestoredDraft(draft.targetId(), draft.created(), draft.deleted(), draft.enableIntent(),
                    draft.baseline() == null ? null : RuleDraft.of(draft.baseline()).root(),
                    draft.draft().isEmpty() ? null : RuleDraft.of(draft.draft()).root()));
        }
        return restored;
    }

    /** 捕获已确认的服务端基线；快照独立保存，后续落盘直接复用。 */
    public void captureBaseline(String targetId, @Nullable JsonObject serverBody) {
        if (serverBody == null) {
            this.baselines.remove(targetId);
        } else {
            this.baselines.put(targetId, serverBody.deepCopy());
        }
    }

    /** 依据内容、可写性与意图保留或删除草稿，连续改动按窗口合并。 */
    public void markDirty(DraftState state) {
        PersistedDraft snapshot = snapshotOf(state);
        if (snapshot == null) {
            drop(state.targetId());
            return;
        }
        this.entries.put(snapshot.targetId(), snapshot);
        this.pending.put(snapshot.targetId(), System.currentTimeMillis() + SAVE_DELAY_MS);
    }

    private @Nullable PersistedDraft snapshotOf(DraftState state) {
        RuleDraft content = state.content();
        boolean writableContent = !state.readOnly() && content != null;
        boolean keepContent = writableContent && (content.isDirty() || state.created() || state.unresolvedConflict());
        boolean keepIntent = state.enableIntent() != null && (writableContent || state.readOnly());
        if (!keepContent && !keepIntent) {
            return null;
        }
        return new PersistedDraft(state.targetId(), writableContent && state.created(),
                writableContent && content.isDeleted(), state.enableIntent(), this.baselines.get(state.targetId()),
                writableContent ? content.toJson() : new JsonObject(), System.currentTimeMillis());
    }

    /**
     * 取走并清空写盘失败事件计数（界面提示用）
     */
    public int consumeFailures() {
        return this.writer.consumeFailures();
    }

    /**
     * 显式重试上次写失败的草稿：由玩家的保存/关闭动作触发，后台不会自动重排。
     * 重试取内存里的最新快照，而不是写入者记住的那份旧内容；已被丢弃的目标不再写回
     */
    public void retryFailed() {
        for (PersistedDraft failed : this.writer.takeFailures()) {
            PersistedDraft latest = this.entries.get(failed.targetId());
            if (latest != null) {
                this.writer.submitSave(latest);
            }
        }
    }

    /**
     * 立即交出单个目标的写盘任务（冲突处理、应用失败后保留草稿等关键时刻）
     */
    public void writeNow(DraftState state) {
        PersistedDraft snapshot = snapshotOf(state);
        if (snapshot == null) {
            drop(state.targetId());
            return;
        }
        this.entries.put(snapshot.targetId(), snapshot);
        this.pending.remove(snapshot.targetId());
        this.writer.submitSave(snapshot);
    }

    /**
     * 交出所有到期条目的写盘任务
     */
    public void flushDue(long now) {
        for (String targetId : new ArrayList<>(this.pending.keySet())) {
            Long deadline = this.pending.get(targetId);
            if (deadline == null || deadline > now) {
                continue;
            }
            PersistedDraft snapshot = this.entries.get(targetId);
            this.pending.remove(targetId);
            if (snapshot == null) {
                continue;
            }
            this.writer.submitSave(snapshot);
        }
    }

    /**
     * 无视节流窗口立即交出全部待写条目（界面关闭时调用）
     */
    public void flushAll() {
        for (String targetId : new ArrayList<>(this.pending.keySet())) {
            PersistedDraft snapshot = this.entries.get(targetId);
            this.pending.remove(targetId);
            if (snapshot != null) {
                this.writer.submitSave(snapshot);
            }
        }
    }

    /**
     * 交出全部待写条目并等待写入完成（有上限）。
     *
     * <p>关闭界面与存档时调用：写入始终在后台线程执行，这里只保证进程退出前草稿已经落盘；
     * 等待有上限，避免磁盘异常时卡住客户端线程。</p>
     *
     * @param timeoutMs 最长等待毫秒数
     */
    public void drain(long timeoutMs) {
        flushAll();
        retryFailed();
        if (!this.writer.awaitIdle(timeoutMs)) {
            Constants.LOG.warn("草稿写盘未在 {} 毫秒内完成，剩余任务会继续在后台执行", timeoutMs);
        }
    }

    /**
     * 丢弃单个目标的草稿（同时删文件）：删除排在已提交的写入之后，同目标合并时删除生效
     */
    public void drop(String targetId) {
        this.entries.remove(targetId);
        this.pending.remove(targetId);
        this.writer.submitDelete(targetId);
    }

    /**
     * 丢弃全部草稿（同时删文件）：清空是屏障，先前排队的写入被丢弃，不会把已放弃的草稿写回来；
     * 顺带清掉 json 与写盘残留的 .tmp，覆盖「条目已不在跟踪表里但文件还在」的情况
     */
    public void dropAll() {
        this.entries.clear();
        this.baselines.clear();
        this.pending.clear();
        this.writer.submitClear();
    }
}
