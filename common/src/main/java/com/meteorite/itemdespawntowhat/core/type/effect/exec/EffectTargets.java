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
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;

/**
 * 效果执行器共享的引用解析与空间查询助手。
 * 约定：执行器不捕获、不吞异常；引用无法解析等硬错误直接抛出，由运行时统一捕获并记录。
 */
public final class EffectTargets {

    // 产物初速：源物品水平速度低于该阈值视为静止，沿用既有随机散布，避免没有方向信息时凭空造方向
    private static final double STILL_SOURCE_HORIZONTAL_SPEED = 0.01D;
    // 静止源产物的水平抖动量与竖直抬升：与既有产物外观保持一致
    private static final double PRODUCT_SCATTER_VELOCITY = 0.1D;
    private static final double PRODUCT_LIFT_VELOCITY = 0.2D;

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
     * 授予与加入落在同一实体上；未写入时不计完成量，重试和实体生命周期由调用方管理。
     *
     * @param context 本组执行上下文
     * @param product 已构造好的掉落物产物
     * @return 已写入世界返回 true；邻区块未加载返回 false
     */
    static boolean addConversionProductNow(EffectContext context, net.minecraft.world.entity.item.ItemEntity product) {
        ReturnItemSpawner.applyGrant(product, ReturnItemSpawner.Kind.CONVERSION_PRODUCT);
        return addEntityNow(context, product, true);
    }

    /**
     * 立即把实体类转化产物写入世界：先授予新产物保护，再按 {@link #addEntityNow} 加入。
     * 保护只对三类环境伤害生效（岩浆、火、仙人掌），且只在保护期内；其它产物的保护不写实体持久数据。
     *
     * @param context 本组执行上下文
     * @param product 已构造好的产物实体
     * @return 已写入世界返回 true；邻区块未加载返回 false
     */
    static boolean addEntityProductNow(EffectContext context, net.minecraft.world.entity.Entity product) {
        DropStateStore.grantNewProduct(product);
        return addEntityNow(context, product, true);
    }

    /**
     * 计算转化产物的初速度：源物品被破坏前仍有水平运动时，水平方向取其反向、竖直方向至少保留既有抬升，
     * 让抛入的源物品把产物送回玩家一侧，同时避免产物被压进地面；源物品静止时沿用既有随机散布。
     *
     * @param context 本组执行上下文，提供成功转化当刻固定的源速度快照
     * @return 产物初速度
     */
    static Vec3 productInitialVelocity(EffectContext context) {
        Vec3 sourceVelocity = context.sourceVelocity();
        if (sourceVelocity.horizontalDistanceSqr() < STILL_SOURCE_HORIZONTAL_SPEED * STILL_SOURCE_HORIZONTAL_SPEED) {
            RandomSource random = context.random();
            return new Vec3((random.nextDouble() - 0.5D) * PRODUCT_SCATTER_VELOCITY, PRODUCT_LIFT_VELOCITY,
                    (random.nextDouble() - 0.5D) * PRODUCT_SCATTER_VELOCITY);
        }
        // 水平取反把产物抛回来向，竖直取反只用于保留抛入时的下坠/上抛趋势，且不低于既有抬升
        return new Vec3(-sourceVelocity.x, Math.max(PRODUCT_LIFT_VELOCITY, -sourceVelocity.y), -sourceVelocity.z);
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

}
