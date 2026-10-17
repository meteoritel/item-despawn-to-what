package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

/**
 * 效果执行器共享的引用解析与空间查询助手。
 * 约定：执行器不捕获、不吞异常；引用无法解析等硬错误直接抛出，由运行时统一捕获并记录。
 */
public final class EffectTargets {

    private EffectTargets() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 解析单个引用；tag 为空或无成员时返回 null（空标签属合法情况），非 tag 未命中同样返回 null
    static <T> @Nullable T resolve(TaggedId reference, Registry<T> registry, RandomSource random) {
        if (reference == null) {
            return null;
        }
        if (!reference.tag()) {
            // 必须用 containsKey：DefaultedRegistry（物品/方块/实体/流体）对未知 id 会返回默认值而非 null
            return registry.containsKey(reference.id()) ? registry.get(reference.id()) : null;
        }
        return registry.getTag(TagKey.create(registry.key(), reference.id()))
                .flatMap(tag -> tag.getRandomElement(random))
                .map(Holder::value)
                .orElse(null);
    }

    // 解析引用，未命中即抛异常（加载期已校验，运行期缺失说明注册表或数据包发生变动，必须可见）
    static <T> T resolveOrThrow(TaggedId reference, Registry<T> registry, RandomSource random, String fieldName) {
        T value = resolve(reference, registry, random);
        if (value == null) {
            throw new IllegalStateException(
                    fieldName + " 无法解析引用 " + (reference == null ? "<空>" : reference.serialized()));
        }
        return value;
    }

    // 以方块位置为中心、边长 2*radius+1 格的立方搜索盒；
    // 效果式消耗与催化剂固定成本（core/runtime）共用本方法，禁止在调用方复刻同一口径
    public static AABB blockBox(BlockPos center, int radius) {
        return AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(center)).inflate(radius);
    }

    // sqrt 将均匀随机数转换为面积均匀的半径，避免圆心过密或方形散点。
    static Vec3 randomHorizontalDisc(RandomSource random, Vec3 center, double radius, double height) {
        double angle = random.nextDouble() * Math.TAU;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        return center.add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
    }

    // 实际生成位置可能越过源区块边界；保留实体等区块加载后再加入。
    static void addEntity(EffectContext context, net.minecraft.world.entity.Entity entity) {
        addEntity(context, entity, false);
    }

    // 产物反馈随延迟加入任务保留，箭雨与闪电沿用各自的原版视觉表现。
    static void addEntityProduct(EffectContext context, net.minecraft.world.entity.Entity entity) {
        addEntity(context, entity, true);
    }

    private static void addEntity(EffectContext context, net.minecraft.world.entity.Entity entity, boolean productFeedback) {
        if (!addEntityNow(context, entity, productFeedback)) {
            context.schedule(20, () -> addEntity(context, entity, productFeedback));
        }
    }

    /**
     * 立即把实体写入世界，不自行排期：区块未加载时直接返回 false，由调用方按自身节奏重试。
     * 供在共享预算内逐单位推进、且只在真实写入后才回执完成量的执行器使用；
     * 回执时机由调用方掌握，本方法不产生任何进度或账目变更。
     *
     * @param context         本组执行上下文
     * @param entity          待加入世界的实体
     * @param productFeedback 是否播放入世界视觉反馈
     * @return 已写入世界返回 true；邻区块未加载返回 false
     */
    static boolean addEntityNow(EffectContext context, net.minecraft.world.entity.Entity entity, boolean productFeedback) {
        if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.contains(context.level(), entity.blockPosition())) {
            return false;
        }
        if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().prepareOutput(context, entity); }
        if (!context.level().addFreshEntity(entity)) {
            throw new IllegalStateException("实体生成被拒绝：规则=" + context.ruleId() + " 类型=" + entity.getType());
        }
        if (productFeedback) { VisualFeedback.entitySpawn(context.level(), entity.position()); }
        if (RuntimeObserver.current().enabled()) { RuntimeObserver.current().outputAdded(context, entity); }
        return true;
    }

    /**
     * 立即把转化产物写入世界：先授予新产物保护与冷却，再按 {@link #addEntityNow} 加入。
     * 授予与加入落在同一实体上；未写入时该实体被丢弃、不计完成量，调用方重试时重新构造。
     *
     * @param context 本组执行上下文
     * @param product 已构造好的掉落物产物
     * @return 已写入世界返回 true；邻区块未加载返回 false
     */
    static boolean addConversionProductNow(EffectContext context, net.minecraft.world.entity.item.ItemEntity product) {
        ReturnItemSpawner.applyGrant(product, ReturnItemSpawner.Kind.CONVERSION_PRODUCT);
        return addEntityNow(context, product, true);
    }

    // 转化产物加入世界：显式区别于其它实体来源，只在这里授予新产物临时保护与冷却；
    // 授予动作与返还物共用 ReturnItemSpawner.applyGrant，避免两处状态语义漂移
    static void addConversionProduct(EffectContext context, net.minecraft.world.entity.item.ItemEntity product) {
        ReturnItemSpawner.applyGrant(product, ReturnItemSpawner.Kind.CONVERSION_PRODUCT);
        addEntityProduct(context, product);
    }

    // 饱和加法：避免计数溢出为负数
    static int saturatedAdd(int left, int right) {
        long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    // 饱和乘法：避免倍率相乘溢出为负数
    static int saturatedMultiply(int left, int right) {
        long product = (long) left * right;
        return product >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) product;
    }

    // 大量产出拆为小批次；保留游标继续执行，不因每刻预算丢弃剩余数量。
    static void forEachStep(EffectContext context, int count, int batchSize, IntConsumer operation) {
        forEachStep(context, count, batchSize, remaining -> remaining, operation);
    }

    // 带容量上限的重载：每批实际处理量再受 capacity（剩余容量）夹紧，容量为 0 时不以空批空转
    static void forEachStep(EffectContext context, int count, int batchSize, IntUnaryOperator capacity, IntConsumer operation) {
        new StepBatch(context, count, batchSize, capacity, operation).run();
    }

    /** 分批世界操作的游标；仅由服务端线程访问。 */
    private static final class StepBatch implements Runnable {
        private final EffectContext context;
        private final int count;
        private final int batchSize;
        private final IntConsumer operation;
        private final IntUnaryOperator capacity;
        private int cursor;
        private StepBatch(EffectContext context, int count, int batchSize, IntUnaryOperator capacity, IntConsumer operation) {
            this.context = context; this.count = count; this.batchSize = batchSize; this.operation = operation; this.capacity = capacity;
        }
        @Override
        public void run() {
            if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.containsArea(context.level(), BlockPos.containing(context.position()), 1)) {
                context.schedule(20, this); return;
            }
            int available = Math.max(0, capacity.applyAsInt(count - cursor));
            int end = (int) Math.min(count, (long) cursor + Math.min(batchSize, available));
            if (end == cursor) { return; }
            while (cursor < end) { operation.accept(cursor++); }
            if (cursor < count) { context.schedule(1, this); }
        }
    }
}
