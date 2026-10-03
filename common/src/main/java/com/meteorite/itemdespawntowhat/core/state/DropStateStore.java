package com.meteorite.itemdespawntowhat.core.state;

import com.meteorite.itemdespawntowhat.platform.Services;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 实体层掉落物状态的读写门面：所有调用点只经这里访问平台持久化实现。
 * 平台差异由 IPlatformHelper 承载（NeoForge: Entity#getPersistentData；Fabric: Data Attachment）。
 * 持续时间（新产物保护、转化冷却）由平台引导时通过 {@link #configure} 注入；0 表示不授予。
 */
public final class DropStateStore {

    // 新产物临时保护刻数（0 表示不授予）
    private static volatile int newProductProtectionTicks;
    // 转化冷却刻数（0 表示不授予）
    private static volatile int conversionCooldownTicks;

    private DropStateStore() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 服务器引导时注入配置（两端 RuleRuntimeHost 调用），停服时以 0 复位
    public static void configure(int protectionTicks, int cooldownTicks) {
        newProductProtectionTicks = Math.max(0, protectionTicks);
        conversionCooldownTicks = Math.max(0, cooldownTicks);
    }

    // 当前生效的新产物保护刻数（诊断用）
    public static int newProductProtectionTicks() {
        return newProductProtectionTicks;
    }

    // 读取实体状态：无记录或版本不可识别时按无状态处理（平台未实现时由 IPlatformHelper default 静默返回 NONE，不在热路径抛异常）
    public static DropState get(Entity entity) {
        if (entity == null) {
            return DropState.NONE;
        }
        DropState state = Services.PLATFORM.getDropState(entity);
        return state == null || !state.isSupportedVersion() ? DropState.NONE : state;
    }

    // 写入实体状态：与无状态等价时按清除处理（对平台传 null），避免给每个掉落物都写一份空数据
    public static void set(Entity entity, DropState state) {
        if (entity == null) {
            return;
        }
        DropState normalized = state == null ? DropState.NONE : state;
        // 没有任何有效字段时传 null，平台实现按「清除」处理（Fabric 移除附件 / NeoForge 移除 NBT 键）
        Services.PLATFORM.setDropState(entity, normalized.isEmpty() ? null : normalized);
    }

    // 清除实体状态
    public static void clear(Entity entity) {
        if (entity != null) {
            // 直接传 null 清除，不构造中间状态
            Services.PLATFORM.setDropState(entity, null);
        }
    }

    // 三类环境伤害且实体处于保护期：本次伤害完全不生效（判定必须先于原版扣血，D2/D4）
    public static boolean blocksEnvironmentalDamage(Entity entity, DamageSource source, long gameTime) {
        if (!DamageClassification.isEnvironmental(source)) {
            return false;
        }
        return get(entity).isProtected(gameTime);
    }

    // 授予新产物状态：临时保护 + 临时冷却，只在转化产物生成点显式调用（D18）
    public static void grantNewProduct(ItemEntity entity) {
        if (entity == null) {
            return;
        }
        int protectionTicks = newProductProtectionTicks;
        int cooldownTicks = conversionCooldownTicks;
        if (protectionTicks <= 0 && cooldownTicks <= 0) {
            return;
        }
        long now = entity.level().getGameTime();
        long protectionUntil = protectionTicks > 0 ? now + protectionTicks : 0L;
        long cooldownUntil = cooldownTicks > 0 ? now + cooldownTicks : 0L;
        set(entity, get(entity).withTemporary(protectionUntil, cooldownUntil));
    }

    // 授予返还物状态：永久保护 + 永久禁转（阶段 6 的返还交付调用）
    public static void grantPermanentReturn(ItemEntity entity) {
        if (entity == null) {
            return;
        }
        set(entity, get(entity).withPermanentReturn());
    }
}
