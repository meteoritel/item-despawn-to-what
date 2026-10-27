package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * 维度级回扫：持有「已加载掉落物索引」与「分步回扫续接状态」，负责 reload 后重建追踪。
 * 设计要点：
 * - 索引按实体 id 有序，登记与注销由 join/leave 事件驱动；有序游标允许跨 tick 续接，不跨 tick 持有世界迭代器；
 * - 回扫以 token 标识当前一轮，完成标记只由 token 匹配的收尾路径清除，新旧两轮不会交错；
 * - 世界扫描模式在入口一次性收集已加载掉落物的引用快照，步骤按快照分片处理；
 * - 追踪表本身归 {@link ConversionRuntime} 所有，本类只通过宿主回调重置与重新登记。
 */
final class WorldRescan {

    private static final Logger LOGGER = LogManager.getLogger();
    // 排队重试上限：换入已生效时排队失败不能留到下次回扫，重试仍失败只记录，由下次回扫或维度加载兜底
    private static final int QUEUE_RETRY_LIMIT = 3;

    private final ServerConfig config;
    private final ServerScheduler scheduler;
    // 维度状态查询：维度已卸载时返回 null，本类绝不在卸载后重建状态
    private final Function<ServerLevel, State> states;
    private final Consumer<ServerLevel> resetTracking;
    private final BiConsumer<ServerLevel, ItemEntity> reRegister;
    private final ToIntFunction<ServerLevel> trackedCount;
    private final SettlementRecoveryQueue recoveries;

    WorldRescan(ServerConfig config, ServerScheduler scheduler, Function<ServerLevel, State> states,
                Consumer<ServerLevel> resetTracking, BiConsumer<ServerLevel, ItemEntity> reRegister,
                ToIntFunction<ServerLevel> trackedCount, SettlementRecoveryQueue recoveries) {
        this.config = config;
        this.scheduler = scheduler;
        this.states = states;
        this.resetTracking = resetTracking;
        this.reRegister = reRegister;
        this.trackedCount = trackedCount;
        this.recoveries = recoveries;
    }

    /** 单维度的索引与回扫续接状态；字段只由本类读写，实例随维度状态一同创建与丢弃。 */
    static final class State {
        // 所有已加载掉落物都登记，即使暂时没有匹配规则；有序 id 游标允许跨 tick 回扫。
        private final NavigableMap<Integer, ItemEntity> items = new TreeMap<>();
        // token 标识当前一轮，完成标记只由 token 匹配的收尾路径清除，避免新旧两轮交错
        private long token;
        private boolean pending;
        private int steps;
        // 世界扫描模式在入口一次性收集已加载掉落物引用，步骤按快照分片处理，不跨 tick 迭代实体表
        private List<ItemEntity> snapshot;

        /** 登记已加载掉落物：join 事件与回扫分片都走这里，重复登记幂等。 */
        void register(ItemEntity item) {
            items.put(item.getId(), item);
        }

        /** 注销掉落物：leave 事件驱动，登记表不保留已移除实体的引用。 */
        void unregister(ItemEntity item) {
            items.remove(item.getId());
        }

        /** 该维度是否仍有分步回扫在推进 */
        boolean isPending() {
            return pending;
        }

    }

    /**
     * 启动与维度加载回扫：这些实体的 join 事件可能早于运行时装配，必须主动扫描世界补登记。
     * 入口只收集一次已加载掉落物的引用快照（不改追踪状态），分步任务按快照切片登记，
     * 既不必每步重扫整维实体，也不跨 tick 持有世界迭代器。
     */
    void rebuild(ServerLevel level, State state) {
        // 新装配或新加载的维度以世界扫描为准，先丢弃旧登记表，避免把已卸载实体的过期引用当成已处理。
        state.items.clear();
        List<ItemEntity> snapshot = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item && !item.isRemoved()) { snapshot.add(item); }
        }
        state.snapshot = snapshot;
        enqueue(level, state, true);
    }

    /**
     * reload 后回扫已加载实体重建追踪（使热重载对已存在的掉落物同样生效）。
     * 只入队分步续接任务：实体有序表由 join/leave 事件持续维护，按 id 游标重建即可，不再同步遍历整维实体。
     */
    void rescan(ServerLevel level, State state) {
        // 还没消化完的世界扫描快照仍然可信：取消任务不会丢掉快照，重载后接着按快照补齐，避免漏登记。
        enqueue(level, state, state.snapshot != null);
    }

    /**
     * 换入后回扫排队失败的有界重试：该维度追踪已随换入清空，不补排会失联到下次回扫。
     * 重试以维护任务入队，不阻塞当前 tick，也不改变已发生的换入结果。
     *
     * @param level 排队失败的维度
     * @param attempt 已失败次数，从 1 起
     */
    void retry(ServerLevel level, int attempt) {
        scheduler.scheduleMaintenance(new RescanQueueRetry(level, attempt), 1);
    }

    /** 该维度是否仍有分步回扫在推进（供开发场景核对让出与最终一致性） */
    boolean isPending(ServerLevel level) {
        State state = states.apply(level);
        return state != null && state.pending;
    }

    /** 该维度累计完成的回扫步数 */
    int steps(ServerLevel level) {
        State state = states.apply(level);
        return state == null ? 0 : state.steps;
    }

    private void enqueue(ServerLevel level, State state, boolean worldScan) {
        long token = ++state.token;
        state.pending = true;
        state.steps = 0;
        resetTracking.accept(level);
        // 旧队列已由入口摘除：发布走 publish/publishDecoded，维度卸载走 clear(level) 的卸载清理；
        // 这里只登记新一轮 token 与新回扫任务，不再同步取消（取消已交给逐槽清理任务）。
        // 维度加载/重扫时补扫该维度的待返还记录：只交付库存，不恢复既有效果队列（与规则内容无关）
        recoveries.recoverLevel(level);
        // 批量取消/加载会把无待交付的记录留在账本，重扫前顺带裁剪，避免无恢复价值的记录白占账本
        SettlementLedger.get(level).prune(level.getGameTime());
        scheduler.schedule(level.dimension(), new RescanTask(level, state, token, worldScan), 0);
    }

    /** 单一路径清除回扫完成标记：只有当前一轮的 token 才能清除，旧一轮的迟到回调不碰新状态 */
    private void clear(State state, long token) {
        if (state.token != token) { return; }
        state.pending = false;
    }

    private void finish(ServerLevel level, State state, long token, boolean worldScan) {
        if (state.token != token) { return; }
        // 世界扫描完成后丢弃快照：之后的重载只按有序游标，不复用可能已卸载的旧引用
        if (worldScan) { state.snapshot = null; }
        if (config.debugLogging()) {
            LOGGER.info("回扫完成：维度 {} 现有 {} 个被追踪掉落物（模式：{}，分步 {}）",
                    level.dimension().location(), trackedCount.applyAsInt(level), worldScan ? "世界扫描" : "有序游标", state.steps);
        }
        clear(state, token);
    }

    // 回扫排队重试任务：维护车道占一个预算，失败再排下一份，用尽后只记录，不再占用队列
    private final class RescanQueueRetry implements ServerTask {

        private final ServerLevel level;
        private final int attempt;

        private RescanQueueRetry(ServerLevel level, int attempt) {
            this.level = level;
            this.attempt = attempt;
        }

        @Override
        public ServerTaskKind kind() {
            return ServerTaskKind.MAINTENANCE;
        }

        @Override
        public String tracingName() {
            return "rescan-queue-retry/" + level.dimension().location();
        }

        @Override
        public StepResult step(ServerTickBudget budget) {
            budget.charge(1);
            State state = states.apply(level);
            if (state == null) {
                // 维度已卸载：没有可重建的状态，排队失败也就无从补救
                return StepResult.done(1);
            }
            try {
                rescan(level, state);
            } catch (RuntimeException e) {
                if (attempt >= QUEUE_RETRY_LIMIT) {
                    LOGGER.error("数据包重载后的回扫排队重试失败，已重试 {} 次；该维度将在下次回扫或维度加载时重建追踪",
                            attempt, e);
                } else {
                    retry(level, attempt + 1);
                }
            }
            return StepResult.done(1);
        }
    }

    /**
     * 分步回扫续接任务：单步只处理有限个掉落物，预算耗尽即让出，下一 tick 从游标继续。
     * 不跨 tick 持有实体迭代器，因为调度期间实体表会被 join/leave 事件修改。
     */
    private final class RescanTask implements ServerTask {
        private final ServerLevel level;
        private final State state;
        private final long token;
        private final boolean worldScan;
        private Integer cursor;
        private int snapshotIndex;
        // 快照是否已全部登记：世界扫描登记完快照后继续走 items 游标，非世界扫描从一开始就只走游标
        private boolean snapshotDone;
        private int retries;

        private RescanTask(ServerLevel level, State state, long token, boolean worldScan) {
            this.level = level;
            this.state = state;
            this.token = token;
            this.worldScan = worldScan;
            this.snapshotDone = !worldScan;
        }

        @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }

        @Override public String tracingName() {
            return (worldScan ? "rescan-scan/" : "rescan-cursor/") + level.dimension().location();
        }

        @Override public StepResult step(ServerTickBudget budget) {
            if (states.apply(level) != state || state.token != token) {
                // 维度已卸载或被更新一轮回扫接管：本任务不再触碰状态，完成标记归接管方维护
                return StepResult.done(1);
            }
            int limit = Math.max(1, config.rescanEntitiesPerStep());
            int processed = 0;
            boolean complete = true;
            if (!snapshotDone) {
                List<ItemEntity> snapshot = state.snapshot;
                if (snapshot != null) {
                    int size = snapshot.size();
                    while (snapshotIndex < size) {
                        if (processed >= limit || budget.exhausted()) { complete = false; break; }
                        ItemEntity item = snapshot.get(snapshotIndex++);
                        // 快照持有引用期间实体可能已卸载或被移除，跳过即可；重复登记由宿主幂等兜底
                        if (!item.isRemoved()) { reRegister.accept(level, item); }
                        processed++;
                    }
                }
                if (!complete) {
                    state.steps++;
                    return StepResult.yield(Math.max(1, processed), 1);
                }
                snapshotDone = true;
            }
            // 快照登记完后继续按 items 游标补齐：快照创建之后才 join 的实体只进了 items、不在快照里，
            // 重载会清空 tracked，不补这一步它们就再也不会被登记追踪；重复登记由宿主幂等兜底
            while (true) {
                if (processed >= limit || budget.exhausted()) { complete = false; break; }
                var next = cursor == null ? state.items.firstEntry() : state.items.higherEntry(cursor);
                if (next == null) { break; }
                cursor = next.getKey();
                ItemEntity item = next.getValue();
                // 已移除但离开事件尚未到达的条目跳过，登记表由离开事件兜底清理
                if (!item.isRemoved()) { reRegister.accept(level, item); }
                processed++;
            }
            state.steps++;
            if (!complete) { return StepResult.yield(Math.max(1, processed), 1); }
            finish(level, state, token, worldScan);
            return StepResult.done(1);
        }

        @Override public void onCancelled(CancelReason reason) {
            clear(state, token);
        }

        @Override public void onFailed(@Nullable Throwable failure) {
            // 同一轮只重排一份：游标与剩余工作都在本任务字段里，多份任务同时推进会让游标交错。
            if (retries++ == 0 && states.apply(level) == state && state.token == token) {
                scheduler.schedule(level.dimension(), this, 1);
                return;
            }
            clear(state, token);
        }
    }
}
