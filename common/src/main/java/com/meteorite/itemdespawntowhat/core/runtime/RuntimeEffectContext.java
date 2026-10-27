package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntConsumer;

/**
 * 效果执行上下文的服务端实现。
 * 每个转化组各持有一个实例；跨 tick 的推进只经执行状态的 {@code advance} 返回继续请求，
 * 异步批次通过进度回执把「已受理」收敛为「已完成」。
 */
public final class RuntimeEffectContext implements EffectContext {

    private final ServerLevel level;
    private final ItemEntity source;
    private final ItemStack sourceSnapshot;
    private final Vec3 sourceVelocity;
    private final Vec3 position;
    private final ResourceLocation ruleId;
    private final int positionSearchChecksPerTick;
    private final int rounds;
    private final int coveredSourceItems;
    private final String outcomeId;
    private final int groupIndex;
    private final int groupCount;
    private final int groupSourceCost;
    private final IntConsumer progressSink;
    private final boolean safeSpawn;
    private final boolean fillOrigin;

    /** 便捷构造：源物品快照取实体当前堆叠，未注入进度回执，候选级开关取默认值。 */
    public RuntimeEffectContext(ServerLevel level, ItemEntity source, Vec3 position,
                               ResourceLocation ruleId, int positionSearchChecksPerTick,
                               int rounds, int coveredSourceItems, String outcomeId, int groupIndex,
                               int groupCount, int groupSourceCost) {
        this(level, source, position, ruleId, positionSearchChecksPerTick, source.getItem().copy(), source.getDeltaMovement(),
                rounds, coveredSourceItems, outcomeId, groupIndex, groupCount, groupSourceCost, null,
                // 未显式给出候选级开关时沿用默认：安全生成关闭、起点填充开启
                false, true);
    }

    /** 便捷构造：在上一构造基础上注入进度回执通道，候选级开关仍取默认值。 */
    public RuntimeEffectContext(ServerLevel level, ItemEntity source, Vec3 position,
                               ResourceLocation ruleId, int positionSearchChecksPerTick,
                               int rounds, int coveredSourceItems, String outcomeId, int groupIndex,
                               int groupCount, int groupSourceCost, IntConsumer progressSink) {
        this(level, source, position, ruleId, positionSearchChecksPerTick, source.getItem().copy(), source.getDeltaMovement(),
                rounds, coveredSourceItems, outcomeId, groupIndex, groupCount, groupSourceCost, progressSink,
                false, true);
    }

    /** 完整构造：显式给出源物品快照（结算任务在计划期就把整堆移出实体，快照必须单独传入）。 */
    public RuntimeEffectContext(ServerLevel level, ItemEntity source, Vec3 position,
                               ResourceLocation ruleId, int positionSearchChecksPerTick,
                               ItemStack sourceSnapshot, Vec3 sourceVelocity, int rounds, int coveredSourceItems,
                               String outcomeId, int groupIndex, int groupCount, int groupSourceCost,
                               IntConsumer progressSink, boolean safeSpawn, boolean fillOrigin) {
        this.level = level;
        this.source = source;
        this.sourceSnapshot = sourceSnapshot == null ? ItemStack.EMPTY : sourceSnapshot.copy();
        this.sourceVelocity = sourceVelocity == null ? Vec3.ZERO : sourceVelocity;
        this.position = position;
        this.ruleId = ruleId;
        this.positionSearchChecksPerTick = positionSearchChecksPerTick;
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
        return positionSearchChecksPerTick;
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
    // 返回快照副本：调用方就地修改（如调整数量）不会污染上下文持有的快照
    public ItemStack sourceStack() {
        return sourceSnapshot.copy();
    }

    @Override
    public Vec3 position() {
        return position;
    }

    @Override
    // 返回成功转化当刻固定的快照：源实体移除后不再读取实体本身
    public Vec3 sourceVelocity() {
        return sourceVelocity;
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
    // 进度回执：只接受正向计数；未注入回执通道时静默忽略
    public void reportProgress(int units) {
        if (progressSink != null && units > 0) {
            progressSink.accept(units);
        }
    }

}
