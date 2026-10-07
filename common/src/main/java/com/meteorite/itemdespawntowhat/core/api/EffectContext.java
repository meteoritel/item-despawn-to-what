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
 * 阶段 4 起上下文按「转化组」构造：一轮一组，组内效果共享同一份源成本与候选结果信息。
 */
public interface EffectContext {

    // 规则触发所在的维度
    ServerLevel level();

    // 触发本次转化的源掉落物（执行期间仍有效，但不保证后续 tick 仍存在）
    ItemEntity source();

    // 触发时的物品栈快照，后续消耗和实体移除不会改变它。
    ItemStack sourceStack();

    // 效果作用位置（默认为源掉落物位置）
    Vec3 position();

    // 随机源：一律使用维度随机，保证联机一致性
    RandomSource random();

    // 触发本条规则的规则 id（用于日志与调试定位）
    ResourceLocation ruleId();

    /**
     * 本组一次性应用的轮数（≥1）。
     * 阶段 4 起「一轮 = 一组」，运行时逐组派发效果，因此本值恒为 1；
     * 产出类与消耗类效果仍按本值缩放（与旧实现写法保持一致）。
     * 一次性世界效果（闪电/爆炸/箭雨/天气）不乘。
     */
    int rounds();

    /**
     * 本组实际扣减的源物品数量（不消耗源物品的规则为整堆数量）。
     * 需要"按源物品逐个计算"的效果（如实体产出经验子类的 per_source_item）应使用本值而不是 rounds。
     */
    int coveredSourceItems();

    // 当前候选结果 id（顶层 effects 隐式映射为唯一候选时恒为 default）
    String outcomeId();

    // 当前组序号（0 起）
    int groupIndex();

    // 本次转化计划执行的组数
    int groupCount();

    // 本组已支付的固定源成本
    int groupSourceCost();

    // 登记延迟任务：0 表示本刻就绪，受预算约束时可顺延。
    void schedule(int delayTicks, Runnable task);

    /**
     * 异步效果的真实产出回执：分批生成真正完成若干个单位时调用。
     * 结算层用它把「已受理」收敛为「已完成」，禁止用计划数当成功量。
     */
    default void reportProgress(int units) {
        // 默认忽略：不参与账目统计的实现无需处理
    }

    // 候选级安全生成开关：为 true 时生成位置需要在原点附近搜索安全点（阶段 5）
    default boolean safeSpawn() {
        // 默认关闭：沿用原点生成行为
        return false;
    }

    // 候选级起点填充开关：为 false 时方块效果不尝试填充触发位置本身（阶段 5）
    default boolean fillOrigin() {
        // 默认开启：沿用原先中心位置的填充尝试
        return true;
    }

    // 位置搜索每刻候选检查上限（阶段 5 安全生成与返还位置搜索共用）：未完成则下刻从断点继续
    default int positionSearchChecksPerTick() {
        // 默认 16：与配置键 position_search_checks_per_tick 的默认值一致
        return 16;
    }
}
