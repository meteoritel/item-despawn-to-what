package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 效果执行上下文的服务端实现。
 * 延迟任务经维度级调度器登记，绑定"维度 + 位置"；源实体可能已消失，执行器需自行容错。
 */
public final class RuntimeEffectContext implements EffectContext {

    private final ServerLevel level;
    private final ItemEntity source;
    private final ItemStack sourceSnapshot;
    private final Vec3 position;
    private final ResourceLocation ruleId;
    private final TickScheduler scheduler;
    private final int rounds;
    private final int coveredSourceItems;

    public RuntimeEffectContext(ServerLevel level, ItemEntity source, Vec3 position,
                               ResourceLocation ruleId, TickScheduler scheduler,
                               int rounds, int coveredSourceItems) {
        this.level = level;
        this.source = source;
        this.sourceSnapshot = source.getItem().copy();
        this.position = position;
        this.ruleId = ruleId;
        this.scheduler = scheduler;
        this.rounds = Math.max(1, rounds);
        this.coveredSourceItems = Math.max(1, coveredSourceItems);
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
    public void schedule(int delayTicks, Runnable task) {
        DebugScenarioManager.schedule(source, scheduler, level.getGameTime(), delayTicks, () -> runWhenLoaded(task));
    }

    // 延迟效果等待目标区块加载，不读取或生成未加载区块。
    private void runWhenLoaded(Runnable task) {
        if (!LoadedChunks.contains(level, net.minecraft.core.BlockPos.containing(position))) {
            schedule(20, task);
            return;
        }
        task.run();
    }
}
