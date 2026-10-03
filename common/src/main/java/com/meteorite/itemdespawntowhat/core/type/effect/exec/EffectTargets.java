package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import com.meteorite.itemdespawntowhat.core.debug.DebugScenarioManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import java.util.List;
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

    // 物品栈是否命中 items 中的任一引用（非 tag 比物品，tag 比标签）；未注册的物品 id 与不存在的标签都不算命中
    // 催化剂固定成本与效果式消耗共用同一口径：本方法任何改动同时影响两处，禁止在调用方复刻
    public static boolean matchesAny(List<TaggedId> items, ItemStack stack) {
        for (TaggedId reference : items) {
            if (reference == null) {
                continue;
            }
            if (reference.tag()) {
                if (stack.is(TagKey.create(Registries.ITEM, reference.id()))) {
                    return true;
                }
                continue;
            }
            if (!BuiltInRegistries.ITEM.containsKey(reference.id())) {
                continue;
            }
            if (stack.is(BuiltInRegistries.ITEM.get(reference.id()))) {
                return true;
            }
        }
        return false;
    }

    // 实际生成位置可能越过源区块边界；保留实体等区块加载后再加入。
    // 此处借用 Minecraft 管理的实例，生命周期由游戏负责，不能在此关闭。
    @SuppressWarnings("resource")
    static void addEntity(EffectContext context, net.minecraft.world.entity.Entity entity) {
        if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.contains(context.level(), entity.blockPosition())) {
            context.schedule(20, () -> addEntity(context, entity));
        } else {
            if (DebugMode.ENABLED) { DebugScenarioManager.prepareOutput(context, entity); }
            if (!context.level().addFreshEntity(entity)) {
                throw new IllegalStateException("实体生成被拒绝：规则=" + context.ruleId() + " 类型=" + entity.getType());
            }
            if (DebugMode.ENABLED) { DebugScenarioManager.outputAdded(context, entity); }
        }
    }

    // 转化产物加入世界：显式区别于其它实体来源，只在这里授予新产物临时保护与冷却（D18）；
    // 授予动作与返还物共用 ReturnItemSpawner.applyGrant，避免两处状态语义漂移（阶段 5）
    static void addConversionProduct(EffectContext context, net.minecraft.world.entity.item.ItemEntity product) {
        ReturnItemSpawner.applyGrant(product, ReturnItemSpawner.Kind.CONVERSION_PRODUCT);
        addEntity(context, product);
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
