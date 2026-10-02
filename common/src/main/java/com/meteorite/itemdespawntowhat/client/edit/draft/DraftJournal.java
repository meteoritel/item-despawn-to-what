package com.meteorite.itemdespawntowhat.client.edit.draft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/***
 * 草稿落盘节流器：内存里记住「哪些目标待写盘」，按时间窗口合并写盘请求。
 * <p>节流窗口 {@link #SAVE_DELAY_MS} 毫秒：连续编辑只在停顿后写一次盘，
 * 避免每次按键/每次结构变换都碰磁盘；界面关闭、会话失效等关键时刻调用
 * {@link #flushAll()} 立即写盘，保证草稿不丢。
 */
public final class DraftJournal {

    // 编辑停顿多久后写盘
    public static final long SAVE_DELAY_MS = 2000L;

    private final DraftStore store;
    // 目标 id -> 最近一次待写/已写内容
    private final Map<String, PersistedDraft> entries = new LinkedHashMap<>();
    // 目标 id -> 允许写盘的时间点（毫秒时间戳）
    private final Map<String, Long> pending = new LinkedHashMap<>();
    // 写盘失败事件计数（一次成功之前的连续失败只算一次，避免界面反复刷同一条提示）
    private int failures;
    // 当前是否处于连续失败状态
    private boolean failing;

    public DraftJournal(DraftStore store) {
        this.store = store;
    }

    public DraftStore store() {
        return this.store;
    }

    // 从磁盘恢复全部草稿（同 id 保留 saved_at 较新的一份）
    public List<PersistedDraft> restore() {
        for (PersistedDraft draft : this.store.loadAll()) {
            PersistedDraft current = this.entries.get(draft.targetId());
            if (current == null || draft.savedAt() >= current.savedAt()) {
                this.entries.put(draft.targetId(), draft);
            }
        }
        return new ArrayList<>(this.entries.values());
    }

    public boolean has(String targetId) {
        return this.entries.containsKey(targetId);
    }

    public @Nullable PersistedDraft get(String targetId) {
        return this.entries.get(targetId);
    }

    public List<String> trackedIds() {
        return new ArrayList<>(this.entries.keySet());
    }

    // 记录改动并安排延迟写盘
    public void markDirty(PersistedDraft snapshot) {
        this.entries.put(snapshot.targetId(), snapshot);
        this.pending.put(snapshot.targetId(), System.currentTimeMillis() + SAVE_DELAY_MS);
    }

    // 取走并清空写盘失败事件计数（界面提示用）
    public int consumeFailures() {
        int count = this.failures;
        this.failures = 0;
        return count;
    }

    // 立即写盘单个目标（冲突处理、应用失败后保留草稿等关键时刻）
    public void writeNow(PersistedDraft snapshot) {
        this.entries.put(snapshot.targetId(), snapshot);
        persist(snapshot.targetId(), snapshot, System.currentTimeMillis());
    }

    // 写盘一个目标：成功则清掉待写标记；失败则重新排入待写队列（下个窗口重试）并记录一次失败事件
    private boolean persist(String targetId, PersistedDraft snapshot, long now) {
        if (this.store.save(snapshot)) {
            this.failing = false;
            this.pending.remove(targetId);
            return true;
        }
        if (!this.failing) {
            this.failing = true;
            this.failures++;
        }
        this.pending.put(targetId, now + SAVE_DELAY_MS);
        return false;
    }

    // 写入所有到期条目；有写入返回 true
    public boolean flushDue(long now) {
        boolean wrote = false;
        for (String targetId : new ArrayList<>(this.pending.keySet())) {
            Long deadline = this.pending.get(targetId);
            if (deadline == null || deadline > now) {
                continue;
            }
            PersistedDraft snapshot = this.entries.get(targetId);
            if (snapshot == null) {
                this.pending.remove(targetId);
                continue;
            }
            wrote |= persist(targetId, snapshot, now);
        }
        return wrote;
    }

    // 无视节流窗口立即写盘全部待写条目
    public void flushAll() {
        long now = System.currentTimeMillis();
        for (String targetId : new ArrayList<>(this.pending.keySet())) {
            PersistedDraft snapshot = this.entries.get(targetId);
            if (snapshot == null) {
                this.pending.remove(targetId);
                continue;
            }
            persist(targetId, snapshot, now);
        }
    }

    // 丢弃单个目标的草稿（同时删文件）
    public void drop(String targetId) {
        this.entries.remove(targetId);
        this.pending.remove(targetId);
        this.store.delete(targetId);
    }

    // 丢弃全部草稿（同时删文件）
    public void dropAll() {
        for (String targetId : new ArrayList<>(this.entries.keySet())) {
            this.store.delete(targetId);
        }
        this.entries.clear();
        this.pending.clear();
    }
}
