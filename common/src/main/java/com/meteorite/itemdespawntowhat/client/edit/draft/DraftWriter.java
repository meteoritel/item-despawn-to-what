package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.meteorite.itemdespawntowhat.Constants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 草稿磁盘写入的单写入者：把 {@link DraftStore} 的同步文件操作挪到一条后台线程上串行执行。
 *
 * <p>之所以要独占一个写入者：{@link DraftStore#save} 先写同名临时文件再原子替换，
 * 同一个目标若被两处并发写入，会共用同一个临时文件而互相覆盖出半截内容；删除与写入并发时
 * 还可能出现「刚删掉又被写回」。把这些操作集中到一条线程上，天然得到「同一目标的写、删按提交顺序生效」。
 *
 * <p>队列按目标合并：同一个目标只保留最后一次提交的操作，连续编辑不会堆积成写盘风暴；
 * 目标是新建、删除还是普通覆盖由最后一次提交决定。全局清空（{@link #submitClear()}）是一次屏障，
 * 提交时丢弃先前排队的一切操作，因此「放弃全部草稿」之后不会有旧写入把文件写回来。
 *
 * <p>失败不在队列里无限重试：写盘失败只记录一次失败事件并丢弃该次操作，界面层据此提示一次；
 * 重新写盘的入口是玩家的显式动作（再次编辑、保存或关闭界面触发的 {@link DraftJournal#flushAll()}/{@link DraftJournal#retryFailed()}）。
 * 线程是 daemon 且空闲 {@link #IDLE_TIMEOUT_MS} 毫秒后自行退出，不会挂住游戏进程。
 */
final class DraftWriter {

    // 空闲多久后释放线程（下次提交时按需重建）
    private static final long IDLE_TIMEOUT_MS = 10_000L;

    private final DraftStore store;
    // 保护队列与线程状态
    private final Object lock = new Object();
    // 待执行操作，按提交先后排列
    private final Deque<Command> queue = new ArrayDeque<>();
    // 目标 id -> 队列中的待执行操作，用于同目标合并
    private final Map<String, Command> indexed = new LinkedHashMap<>();
    // 目标 id -> 写盘失败的快照，供显式重试（不在后台自动重排，避免失败后无限重试刷日志）
    private final Map<String, PersistedDraft> failed = new LinkedHashMap<>();
    // 写盘失败事件计数（连续失败只算一次，界面提示一条）
    private int failures;
    private boolean failing;
    private boolean running;
    // 是否有一条操作正在写盘（已出队但未完成）；读写都在 lock 内
    private boolean busy;

    /**
     * 建立一个写入者；线程在第一次提交时才启动。
     *
     * @param store 实际执行文件操作的草稿存储
     */
    public DraftWriter(DraftStore store) {
        this.store = store;
    }

    // 排队写出一条草稿：同目标的旧操作被本次取代
    public void submitSave(PersistedDraft snapshot) {
        enqueue(new Save(snapshot));
    }

    // 排队删除一条草稿：同目标已排队的写入被取消（删除后不应再被写回），
    // 该目标先前留下的失败快照同时作废，避免后续显式重试把已放弃的草稿写回来
    public void submitDelete(String targetId) {
        synchronized (this.lock) {
            this.failed.remove(targetId);
            enqueueLocked(new Delete(targetId));
        }
    }

    // 排队清空草稿目录：作为屏障丢弃先前排队的一切操作，避免旧写入把已放弃的草稿写回来
    public void submitClear() {
        synchronized (this.lock) {
            this.queue.clear();
            this.indexed.clear();
            // 清空意图覆盖一切：先前失败的快照也不再重试，避免放弃后又被写回磁盘
            this.failed.clear();
            this.queue.addLast(Clear.INSTANCE);
            startLocked();
        }
    }

    /**
     * 取走写盘失败的快照并清空失败记录。
     *
     * <p>只交出「失败过的目标」，重新提交的内容由上层从当前内存状态取最新的一份，
     * 因此重试不会把已经过时或已放弃的旧快照写回磁盘。</p>
     *
     * @return 失败过的快照（内容可能已过时，仅用于定位目标）
     */
    public List<PersistedDraft> takeFailures() {
        synchronized (this.lock) {
            List<PersistedDraft> snapshots = new ArrayList<>(this.failed.values());
            this.failed.clear();
            return snapshots;
        }
    }

    /**
     * 等待队列排空（关闭界面、存档等关键时刻用）。
     *
     * <p>写入本身始终在后台线程执行，这里只是等它把已提交的任务做完，保证进程退出前草稿已经落盘；
     * 等待有上限，磁盘异常慢时宁可放弃等待也不卡住客户端线程。</p>
     *
     * @param timeoutMs 最长等待毫秒数
     * @return 是否在超时前排空
     */
    public boolean awaitIdle(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (this.lock) {
            // 队列空不代表写完：正在写盘的那一条也要等，否则关闭界面时可能半途返回
            while ((!this.queue.isEmpty() || this.busy) && this.running) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return false;
                }
                try {
                    this.lock.wait(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return this.queue.isEmpty() && !this.busy;
        }
    }

    // 取走并清零写盘失败事件计数
    public int consumeFailures() {
        synchronized (this.lock) {
            int count = this.failures;
            this.failures = 0;
            return count;
        }
    }

    // 入队并确保线程在跑
    private void enqueue(Command command) {
        synchronized (this.lock) {
            enqueueLocked(command);
        }
    }

    // 入队实现（调用方必须持有 lock）：同目标合并时先摘掉旧操作，保持「最后一次提交生效」
    private void enqueueLocked(Command command) {
        String key = command.key();
        if (key != null) {
            Command previous = this.indexed.remove(key);
            if (previous != null) {
                this.queue.remove(previous);
            }
            this.indexed.put(key, command);
        }
        this.queue.addLast(command);
        startLocked();
        // 写入线程可能正在空闲等待：入队必须唤醒它，否则任务要等到空闲超时才被取走
        this.lock.notifyAll();
    }

    // 按需启动写入线程（调用方必须持有 lock）
    private void startLocked() {
        if (this.running) {
            return;
        }
        this.running = true;
        Thread thread = new Thread(this::drainLoop, Constants.MOD_ID + "-draft-writer");
        thread.setDaemon(true);
        thread.start();
    }

    // 写入线程主体：取一条、执行一条，直到队列空且空闲超时
    private void drainLoop() {
        while (true) {
            Command next;
            synchronized (this.lock) {
                long idleDeadline = System.currentTimeMillis() + IDLE_TIMEOUT_MS;
                while (this.queue.isEmpty()) {
                    long remaining = idleDeadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        // 空闲足够久：释放线程，下次提交时重建
                        this.running = false;
                        this.lock.notifyAll();
                        return;
                    }
                    try {
                        this.lock.wait(remaining);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        this.running = false;
                        this.lock.notifyAll();
                        return;
                    }
                }
                next = this.queue.pollFirst();
                String key = next == null ? null : next.key();
                if (key != null) {
                    this.indexed.remove(key);
                }
                this.busy = next != null;
            }
            if (next != null) {
                execute(next);
            }
        }
    }

    // 在写入线程上执行一条操作；失败只记一条事件，不在队列里重试
    private void execute(Command command) {
        boolean ok;
        try {
            ok = command.run(this.store);
        } catch (RuntimeException exception) {
            // 单写入者线程不能因一次意外异常就退出：那会让之后所有草稿永远不再落盘
            Constants.LOG.warn("草稿写盘任务异常：{}", command.key(), exception);
            ok = false;
        }
        synchronized (this.lock) {
            try {
                applyResult(command, ok);
            } finally {
                this.busy = false;
                // 唤醒等待排空的调用方
                this.lock.notifyAll();
            }
        }
    }

    // 写入线程上更新失败状态（调用方必须持有 lock）
    private void applyResult(Command command, boolean ok) {
        if (command.clearsFailures()) {
            this.failed.clear();
            this.failing = false;
            return;
        }
        String key = command.key();
        if (key != null && command.forgetsFailure()) {
            // 删除即使失败也作废该目标的失败快照：宁可少写一次，也不把已放弃的草稿写回去
            this.failed.remove(key);
        }
        if (ok) {
            this.failing = false;
            if (key != null) {
                this.failed.remove(key);
            }
            return;
        }
        // 失败快照留着等显式重试；连续失败只记一次事件，避免界面反复刷同一条提示
        PersistedDraft retry = command.retrySnapshot();
        if (key != null && retry != null) {
            this.failed.put(key, retry);
        }
        if (!this.failing) {
            this.failing = true;
            this.failures++;
        }
    }

    // 队列操作：key 为目标 id（全局清空没有 key），run 在写入线程上执行
    private interface Command {

        // 同目标合并用的键
        @Nullable String key();

        // 在写入线程上执行，返回是否成功
        boolean run(DraftStore store);

        // 失败后可供显式重试的快照；删除与清空没有
        @Nullable PersistedDraft retrySnapshot();

        // 是否为全局清空：成功后之前的失败记录一并作废
        default boolean clearsFailures() {
            return false;
        }

        // 是否作废该目标的失败快照（删除意图优先于重试）
        default boolean forgetsFailure() {
            return false;
        }
    }

    // 写出一条草稿快照
    private record Save(PersistedDraft snapshot) implements Command {

        @Override
        public String key() {
            return this.snapshot.targetId();
        }

        @Override
        public boolean run(DraftStore store) {
            return store.save(this.snapshot);
        }

        @Override
        public @Nullable PersistedDraft retrySnapshot() {
            return this.snapshot;
        }
    }

    private record Delete(String targetId) implements Command {

        @Override
        public String key() {
            return this.targetId;
        }

        @Override
        public boolean run(DraftStore store) {
            store.delete(this.targetId);
            return true;
        }

        @Override
        public @Nullable PersistedDraft retrySnapshot() {
            return null;
        }

        @Override
        public boolean forgetsFailure() {
            return true;
        }
    }

    private enum Clear implements Command {
        INSTANCE;

        @Override
        public @Nullable String key() {
            return null;
        }

        @Override
        public boolean run(DraftStore store) {
            store.clear();
            return true;
        }

        @Override
        public @Nullable PersistedDraft retrySnapshot() {
            return null;
        }

        @Override
        public boolean clearsFailures() {
            return true;
        }
    }
}
