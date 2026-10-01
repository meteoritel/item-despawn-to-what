package com.meteorite.itemdespawntowhat.core.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 效果执行上下文：由运行时在触发规则时构造，效果执行器只依赖本接口。
 * 延迟任务必须绑定「维度 + 位置」而不是源实体（源实体可能已被消耗或拾取）。
 */
public interface EffectContext {

    // 规则触发所在的维度
    ServerLevel level();

    // 触发本次转化的源掉落物（执行期间仍有效，但不保证后续 tick 仍存在）
    ItemEntity source();

    // 源掉落物的当前物品栈快照
    ItemStack sourceStack();

    // 效果作用位置（默认为源掉落物位置）
    Vec3 position();

    // 随机源：一律使用维度随机，保证联机一致性
    RandomSource random();

    // 触发本条规则的规则 id（用于日志与调试定位）
    ResourceLocation ruleId();

    // 登记一个延迟任务：delayTicks 为 0 时立即执行
    void schedule(int delayTicks, Runnable task);
}
