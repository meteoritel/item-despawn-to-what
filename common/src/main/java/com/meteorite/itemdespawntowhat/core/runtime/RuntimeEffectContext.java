package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTaskKind;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntConsumer;

/**
 * 效果执行上下文的服务端实现。
 * 延迟任务经公共服务器调度器登记，绑定"维度 realm + 位置"；源实体可能已消失，执行器需自行容错。
 * 阶段 4 起每个转化组各持有一个实例，异步批次通过进度回执把「已受理」收敛为「已完成」。
 */
public final class RuntimeEffectContext implements EffectContext {

    private final ServerLevel level;
    private final ItemEntity source;
    private final ItemStack sourceSnapshot;
    private final Vec3 position;
    private final ResourceLocation ruleId;
    private final ServerScheduler scheduler;
    private final Object realmKey;
    private final int rounds;
    private final int coveredSourceItems;
    private final String outcomeId;
    private final int groupIndex;
    private final int groupCount;
    private final int groupSourceCost;
    private final IntConsumer progressSink;
    private final boolean safeSpawn;
    private final boolean fillOrigin;

    public RuntimeEffectContext(ServerLevel level, ItemEntity source, Vec3 position,
                               ResourceLocation ruleId, ServerScheduler scheduler, Object realmKey,
                               int rounds, int coveredSourceItems, String outcomeId, int groupIndex,
                               int groupCount, int groupSourceCost) {
        this(level, source, position, ruleId, scheduler, realmKey, source.getItem().copy(), rounds,
                coveredSourceItems, outcomeId, groupIndex, groupCount, groupSourceCost, null,
                // 未显式给出候选级开关时沿用默认：安全生成关闭、起点填充开启
                false, true);
    }

    public RuntimeEffectContext(ServerLevel level, ItemEntity source, Vec3 position,
                               ResourceLocation ruleId, ServerScheduler scheduler, Object realmKey,
                               int rounds, int coveredSourceItems, String outcomeId, int groupIndex,
                               int groupCount, int groupSourceCost, IntConsumer progressSink) {
        this(level, source, position, ruleId, scheduler, realmKey, source.getItem().copy(), rounds,
                coveredSourceItems, outcomeId, groupIndex, groupCount, groupSourceCost, progressSink,
                false, true);
    }

    // 完整构造：显式给出源物品快照（结算任务在计划期就把整堆移出实体，快照必须单独传入）
    public RuntimeEffectContext(ServerLevel level, ItemEntity source, Vec3 position,
                               ResourceLocation ruleId, ServerScheduler scheduler, Object realmKey,
                               ItemStack sourceSnapshot, int rounds, int coveredSourceItems, String outcomeId,
                               int groupIndex, int groupCount, int groupSourceCost, IntConsumer progressSink,
                               boolean safeSpawn, boolean fillOrigin) {
        this.level = level;
        this.source = source;
        this.sourceSnapshot = sourceSnapshot == null ? ItemStack.EMPTY : sourceSnapshot.copy();
        this.position = position;
        this.ruleId = ruleId;
        this.scheduler = scheduler;
        this.realmKey = realmKey;
        this.rounds = Math.max(1, rounds);
        this.coveredSourceItems = Math.max(0, coveredSourceItems);
        this.outcomeId = outcomeId == null ? "default" : outcomeId;
        this.groupIndex = Math.max(0, groupIndex);
        this.groupCount = Math.max(1, groupCount);
        this.groupSourceCost = Math.max(0, groupSourceCost);
        this.progressSink = progressSink;
        this.safeSpawn = safeSpawn;
        this.fillOrigin = fillOrigin;
    }

    @Override
    public boolean safeSpawn() {
        return safeSpawn;
    }

    @Override
    public boolean fillOrigin() {
        return fillOrigin;
    }

    @Override
    public int positionSearchChecksPerTick() {
        return scheduler.config().positionSearchChecksPerTick();
    }

    @Override
    public int rounds() {
        return rounds;
    }

    @Override
    public int coveredSourceItems() {
        return coveredSourceItems;
    }

    @Override
    public String outcomeId() {
        return outcomeId;
    }

    @Override
    public int groupIndex() {
        return groupIndex;
    }

    @Override
    public int groupCount() {
        return groupCount;
    }

    @Override
    public int groupSourceCost() {
        return groupSourceCost;
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public ItemEntity source() {
        return source;
    }

    @Override
    public ItemStack sourceStack() {
        return sourceSnapshot.copy();
    }

    @Override
    public Vec3 position() {
        return position;
    }

    @Override
    public RandomSource random() {
        return level.random;
    }

    @Override
    public ResourceLocation ruleId() {
        return ruleId;
    }

    @Override
    public void reportProgress(int units) {
        if (progressSink != null && units > 0) {
            progressSink.accept(units);
        }
    }

    @Override
    public void schedule(int delayTicks, Runnable task) {
        // 绝对到期 tick 由当前游戏刻推导：跨 tick 延后但保留原始时间，不会提前执行
        long dueTick = level.getGameTime() + Math.max(0, delayTicks);
        DebugScenarioManager.schedule(source, scheduler, realmKey, ServerTaskKind.EFFECT, dueTick, "effect",
                () -> runWhenLoaded(task));
    }

    // 延迟效果等待目标区块加载，不读取或生成未加载区块。
    private void runWhenLoaded(Runnable task) {
        if (!LoadedChunks.contains(level, BlockPos.containing(position))) {
            schedule(20, task);
            return;
        }
        task.run();
    }
}
