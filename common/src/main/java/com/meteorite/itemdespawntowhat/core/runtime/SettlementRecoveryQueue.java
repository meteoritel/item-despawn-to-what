package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.config.ServerConfig;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 运行期结算恢复队列：持有「哪些记录正在被交付」「哪些记录在等退避窗口」「重试与告警计数」三组状态。
 * 设计要点：
 * - 幂等位 {@code activeRecoveries} 保证同一结算记录同一时刻只有一个待返还交付任务，重复扫描与重复入队都不会多发；
 * - 推进任务排在调度器自有的系统 realm：它只泵队列、不写世界，规则重载与维度卸载都不打断它；
 * - 维度未加载不消耗尝试次数，只做有限次复查，避免队列泵长期空转；
 * - 停服收尾后不再接受新入队，状态由 {@link #close()} 一次性清空。
 */
final class SettlementRecoveryQueue {

    private static final Logger LOGGER = LogManager.getLogger();
    // 维度未加载时的复查间隔：不消耗恢复尝试次数，等维度加载后继续
    private static final long DIMENSION_RECHECK_TICKS = 20L;
    // 维度连续未加载的复查次数上限：达到上限就把记录留在账本交给维度加载回扫或下次启动，避免队列常驻空转
    private static final int DIMENSION_RECHECK_LIMIT = 5;

    private final ServerConfig config;
    private final ServerScheduler scheduler;
    private final CatalystReservations catalystReservations;
    // 首次服务端 tick 后做一次全局恢复扫描；之后新加载的维度由回扫补扫
    private boolean scanned;
    // 观测：上次扫描中因维度未加载而保持不动的记录数（低频读取，供命令与调试输出使用）
    private long skippedDimensions;
    // 恢复幂等位：同一结算记录同一时刻只允许一个待返还交付任务
    private final Set<String> activeRecoveries = new HashSet<>();
    // 运行期恢复队列：本会话内失败的结算按记录去重，以退避重试重新交付
    private final Map<String, PendingRecovery> pendingRecoveries = new LinkedHashMap<>();
    // 已启动的恢复尝试次数：由队列推进并在成功/放弃时清理，超限判定依赖它
    private final Map<String, Integer> attempts = new HashMap<>();
    // 已告警过的记录：超限只提示一次，避免每 tick 刷日志
    private final Set<String> alerted = new HashSet<>();
    private boolean dispatchScheduled;
    // 停服收尾期间不再接受新入队：取消回调仍会把记录写回账本，但排期已经没有必要
    private boolean stopped;
    @Nullable private MinecraftServer server;
    private long attemptsTotal;
    private long abandonedTotal;
    private long alertsTotal;
    private int maxRetryDelayTicks;

    SettlementRecoveryQueue(ServerConfig config, ServerScheduler scheduler,
                            CatalystReservations catalystReservations) {
        this.config = config;
        this.scheduler = scheduler;
        this.catalystReservations = catalystReservations;
    }

    /** 催化剂预留表：恢复交付与效果支付共用同一份运行期预留视图。 */
    CatalystReservations reservations() {
        return catalystReservations;
    }

    // 首个服务端 tick 做一次全局恢复扫描：只交付记录里的待返还库存，不恢复既有效果队列；维度未加载的记录保持不动
    void ensureScanned(MinecraftServer server) {
        if (scanned) {
            return;
        }
        scanned = true;
        SettlementLedger ledger = SettlementLedger.get(server.overworld());
        int skipped = 0;
        for (SettlementRecord record : ledger.unsettled()) {
            if (activeRecoveries.contains(record.id())) {
                continue;
            }
            ServerLevel level = levelOf(server, record.dimension());
            if (level == null) {
                // 维度未加载：记录留在账本等待该维度加载时由回扫补扫，这里不重试、不空转
                skipped++;
                continue;
            }
            start(level, ledger, record);
        }
        skippedDimensions = skipped;
        if (skipped > 0 && config.debugLogging()) {
            LOGGER.info("返还恢复扫描：{} 条待返还记录所在维度尚未加载，保持在账本中等待", skipped);
        }
    }

    // 单维度恢复扫描：同一记录重复入队由幂等位拦住
    void recoverLevel(ServerLevel level) {
        SettlementLedger ledger = SettlementLedger.get(level);
        String dimension = level.dimension().location().toString();
        for (SettlementRecord record : ledger.unsettled(dimension)) {
            if (activeRecoveries.contains(record.id())) {
                continue;
            }
            start(level, ledger, record);
        }
    }

    // 按记录里的维度 key 找已加载维度；未加载返回 null
    private ServerLevel levelOf(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    // 交付任务入队：REBATE 车道；世界写入只会在 scheduler.tick 推进时发生且受公共预算约束。
    // 口径：停机或维度卸载时未交付的库存（含尚未开始组的源库存）不在停服瞬间返还，
    // 而是作为待返还记录留在账本里，于下一次服务端启动、且目标维度已加载后由本路径交付。
    private void start(ServerLevel level, SettlementLedger ledger, SettlementRecord record) {
        // 重启后源实体已不存在：位置取自记录；记录缺位置字段时退回该维度出生点
        Vec3 position = record.hasPosition()
                ? new Vec3(record.positionX(), record.positionY(), record.positionZ())
                : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        SettlementRecovery recovery = new SettlementRecovery(this, scheduler.config(), level,
                SettlementRecord.Writer.of(ledger, record), position);
        activeRecoveries.add(record.id());
        scheduler.scheduleAt(level.dimension(), recovery, level.getGameTime());
        LOGGER.debug("返还恢复已入队：记录={} 维度={} 待交付={} 位置={}",
                record.id(), record.dimension(), record.pendingDelivery(), position);
    }

    // 恢复任务收尾（交付完成或取消）后释放幂等位，允许后续扫描重新入队剩余库存。
    // 取消/中断但仍有待交付时立即交给运行期队列重排：reload 先摘 realm 再逐槽取消，旧任务被取消后
    // 若只释放幂等位，本会话内就没有人再排这份记录，库存会一直停到下次启动
    void onFinished(@Nullable MinecraftServer server, String id) {
        activeRecoveries.remove(id);
        attempts.remove(id);
        alerted.remove(id);
        if (server == null) {
            return;
        }
        SettlementRecord record = SettlementLedger.get(server.overworld()).find(id);
        if (record != null && record.needsRecovery()) {
            enqueue(server, id, "recovery-cancelled");
        }
    }

    // 结算在当前会话内失败：按记录去重入队，由队列任务在公共预算内按退避重试，不必等到下次启动
    void enqueue(MinecraftServer server, String recordId, String reason) {
        if (stopped || pendingRecoveries.containsKey(recordId) || activeRecoveries.contains(recordId)) {
            return;
        }
        this.server = server;
        pendingRecoveries.put(recordId, new PendingRecovery(recordId,
                attempts.getOrDefault(recordId, 0), scheduler.currentTick(), reason, 0));
        scheduleDispatch();
    }

    // 恢复失败：未达上限按指数退避重新入队；达到上限后把记录留在账本等下次启动扫描，并只告警一次
    void onFailed(String id, String reason) {
        activeRecoveries.remove(id);
        int tried = attempts.getOrDefault(id, 0);
        if (tried >= config.settlementRecoveryMaxAttempts()) {
            attempts.remove(id);
            abandonedTotal++;
            if (alerted.add(id)) {
                alertsTotal++;
                LOGGER.warn("结算恢复超过上限：记录={} 原因={} 已尝试={} 次，保留账本等待下次启动扫描",
                        id, reason, tried);
            }
            return;
        }
        int delay = RecoveryBackoff.delayTicks(tried);
        maxRetryDelayTicks = Math.max(maxRetryDelayTicks, delay);
        pendingRecoveries.put(id, new PendingRecovery(id, tried, scheduler.currentTick() + delay, reason, 0));
        LOGGER.warn("结算恢复失败：记录={} 原因={} 已尝试={} 次，{} tick 后重试", id, reason, tried, delay);
        scheduleDispatch();
    }

    // 队列空闲时按需排入推进任务；已在跑就不再重复排期。
    // 排进系统 realm：规则发布只摘维度 realm，不会连同队列泵一起取消
    private void scheduleDispatch() {
        if (dispatchScheduled || pendingRecoveries.isEmpty() || server == null) {
            return;
        }
        dispatchScheduled = true;
        scheduler.scheduleMaintenance(new DispatchTask(server), 0);
    }

    /** 运行期结算恢复队列快照，供开发场景断言重试与告警。 */
    ConversionRuntime.SettlementRecoveryStats stats() {
        return new ConversionRuntime.SettlementRecoveryStats(pendingRecoveries.size(), attemptsTotal,
                abandonedTotal, alertsTotal, maxRetryDelayTicks);
    }

    /** 在飞的返还恢复任务数观测 */
    int activeCount() {
        return activeRecoveries.size();
    }

    /** 观测：上次恢复扫描中因维度未加载而保持不动的记录数 */
    long skippedDimensionCount() {
        return skippedDimensions;
    }

    /** 停服收尾：拒绝新入队并清空全部队列状态，使再次构造的实例从零开始。 */
    void close() {
        stopped = true;
        activeRecoveries.clear();
        pendingRecoveries.clear();
        attempts.clear();
        alerted.clear();
        dispatchScheduled = false;
        server = null;
        scanned = false;
    }

    private record PendingRecovery(String recordId, int attempts, long readyTick, String reason, int dimensionMisses) {
    }

    // 队列推进任务：维护车道，受公共预算约束；每 tick 至多启动一条恢复，队列清空即结束，不留常驻空转任务
    private final class DispatchTask implements ServerTask {

        private final MinecraftServer server;

        private DispatchTask(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public ServerTaskKind kind() {
            return ServerTaskKind.MAINTENANCE;
        }

        @Override
        public String tracingName() {
            return "settlement-recovery-queue";
        }

        @Override
        public StepResult step(ServerTickBudget budget) {
            if (pendingRecoveries.isEmpty()) {
                dispatchScheduled = false;
                return StepResult.done(1);
            }
            budget.charge(1);
            long now = scheduler.currentTick();
            PendingRecovery ready = null;
            for (PendingRecovery entry : pendingRecoveries.values()) {
                if (entry.readyTick() <= now) {
                    ready = entry;
                    break;
                }
            }
            if (ready == null) {
                // 记录都还在退避窗口内：下刻再看
                return StepResult.yield(1, 1);
            }
            pendingRecoveries.remove(ready.recordId());
            SettlementLedger ledger = SettlementLedger.get(server.overworld());
            SettlementRecord record = ledger.find(ready.recordId());
            if (record == null || record.completed() || activeRecoveries.contains(record.id())) {
                return advance();
            }
            ServerLevel level = levelOf(server, record.dimension());
            if (level == null) {
                // 维度未加载：不消耗恢复尝试次数，隔一段再复查；连续未加载达到上限就放弃本次会话排队，
                // 记录留在账本，等该维度加载时的回扫或下次启动扫描接手，避免队列泵长期空转
                int misses = ready.dimensionMisses() + 1;
                if (misses >= DIMENSION_RECHECK_LIMIT) {
                    if (alerted.add(record.id())) {
                        LOGGER.warn("结算恢复暂缓：记录={} 维度={} 连续 {} 次未加载，保留账本等待维度加载或下次启动扫描",
                                record.id(), record.dimension(), misses);
                    }
                    return advance();
                }
                pendingRecoveries.put(record.id(), new PendingRecovery(record.id(), ready.attempts(),
                        now + DIMENSION_RECHECK_TICKS, ready.reason(), misses));
                return StepResult.yield(1, 1);
            }
            attempts.put(record.id(), ready.attempts() + 1);
            attemptsTotal++;
            start(level, ledger, record);
            return advance();
        }

        // 队列泵被取消（停服或系统 realm 清理）不会自动重排：复位标志，交由后续入队路径重新排期
        @Override
        public void onCancelled(CancelReason reason) {
            dispatchScheduled = false;
        }

        // 队列泵自身失败：复位标志后立即重排，队列里还有记录时不能让它们在本会话永久滞留
        @Override
        public void onFailed(@Nullable Throwable failure) {
            dispatchScheduled = false;
            if (!pendingRecoveries.isEmpty()) {
                scheduleDispatch();
            }
        }

        // 队列清空即结束；还有记录则下刻继续
        private StepResult advance() {
            if (pendingRecoveries.isEmpty()) {
                dispatchScheduled = false;
                return StepResult.done(1);
            }
            return StepResult.yield(1, 1);
        }
    }
}
