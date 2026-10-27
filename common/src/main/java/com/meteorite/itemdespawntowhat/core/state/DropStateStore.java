package com.meteorite.itemdespawntowhat.core.state;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.platform.Services;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体层掉落物状态的读写门面：所有调用点只经这里访问平台持久化实现。
 * 平台差异由 IPlatformHelper 承载（NeoForge: Entity#getPersistentData；Fabric: Data Attachment）。
 * 持续时间（新产物保护、转化冷却）由平台引导时通过 {@link #configure} 注入；0 表示不授予。
 *
 * <p>产物实体里只有掉落物需要跨存档、跨合并保留状态；生物、载具等其它产物的新产物保护只活几秒，
 * 用内存表按实体 UUID 记到期刻，不往实体上写持久数据。</p>
 */
public final class DropStateStore {

    private static final String LEGACY_DEATH_EXCLUSION_TAG = Constants.MOD_ID + ":check_lock";
    private static final String LEGACY_CONVERSION_COMMITTED_TAG = Constants.MOD_ID + ":converted";

    // 新产物临时保护刻数（0 表示不授予）
    private static volatile int newProductProtectionTicks;
    // 转化冷却刻数（0 表示不授予）
    private static volatile int conversionCooldownTicks;
    // 非掉落物产物实体的保护到期刻：实体 UUID 到游戏刻，只存在于内存
    private static final Map<UUID, Long> entityProductProtection = new ConcurrentHashMap<>();
    // 内存保护表规模超过该值时做一次过期剔除，避免长期运行后表只增不减
    private static final int ENTITY_PROTECTION_PRUNE_SIZE = 256;

    private DropStateStore() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 服务器引导时注入配置（两端 RuleRuntimeHost 调用），停服时以 0 复位
    public static void configure(int protectionTicks, int cooldownTicks) {
        newProductProtectionTicks = Math.max(0, protectionTicks);
        conversionCooldownTicks = Math.max(0, cooldownTicks);
        // 停服/换服时清空内存保护，避免游戏刻基线变化后旧到期刻误判
        entityProductProtection.clear();
    }

    /** 读取类型化状态并投影旧存档标记；读取不触发持久化写入。 */
    public static DropState get(Entity entity) {
        if (entity == null) {
            return DropState.NONE;
        }
        DropState state = Services.PLATFORM.getDropState(entity);
        state = state == null || !state.isSupportedVersion() ? DropState.NONE : state;
        if (entity.getTags().contains(LEGACY_DEATH_EXCLUSION_TAG)) {
            state = state.withDeathDropExcluded();
        }
        if (entity.getTags().contains(LEGACY_CONVERSION_COMMITTED_TAG)) {
            state = state.withConversionCommitted(true);
        }
        return state;
    }

    /** 写入实体状态，成功后移除旧存档标记；空状态清除平台记录。 */
    public static void set(Entity entity, DropState state) {
        if (entity == null) {
            return;
        }
        DropState normalized = state == null ? DropState.NONE : state;
        // 没有任何有效字段时传 null，平台实现按「清除」处理（Fabric 移除附件 / NeoForge 移除 NBT 键）
        Services.PLATFORM.setDropState(entity, normalized.isEmpty() ? null : normalized);
        clearLegacyTags(entity);
    }

    /** 清除实体状态及兼容标记。 */
    public static void clear(Entity entity) {
        if (entity != null) {
            // 直接传 null 清除，不构造中间状态
            Services.PLATFORM.setDropState(entity, null);
            clearLegacyTags(entity);
            entityProductProtection.remove(entity.getUUID());
        }
    }

    /** 排除死亡掉落，不改变其环境伤害保护和其它转化状态。 */
    public static void excludeDeathDrop(ItemEntity entity) {
        set(entity, get(entity).withDeathDropExcluded());
    }

    /** 更新原源实体的转化提交位；失败补偿只撤销该位。 */
    public static void setConversionCommitted(ItemEntity entity, boolean committed) {
        set(entity, get(entity).withConversionCommitted(committed));
    }

    private static void clearLegacyTags(Entity entity) {
        entity.removeTag(LEGACY_DEATH_EXCLUSION_TAG);
        entity.removeTag(LEGACY_CONVERSION_COMMITTED_TAG);
    }

    // 三类环境伤害且产物实体处于保护期：本次伤害完全不生效（判定必须先于原版扣血）
    public static boolean blocksEnvironmentalDamage(Entity entity, DamageSource source, long gameTime) {
        if (entity == null || !DamageClassification.isEnvironmental(source)) {
            return false;
        }
        if (entity instanceof ItemEntity) {
            // 掉落物读持久化状态：临时保护与永久返还要跨存档、跨合并保留
            return get(entity).isProtected(gameTime);
        }
        Long protectionUntil = entityProductProtection.get(entity.getUUID());
        if (protectionUntil == null) {
            return false;
        }
        if (gameTime < protectionUntil) {
            return true;
        }
        entityProductProtection.remove(entity.getUUID(), protectionUntil);
        return false;
    }

    /**
     * 授予新产物状态，只在转化产物生成点显式调用。
     * 掉落物写临时保护与转化冷却（冷却用于挡住转化检查）；其它产物实体只记内存态临时保护，
     * 它们不参与转化检查，也不需要跨存档保留。
     *
     * @param entity 刚生成的产物实体
     */
    public static void grantNewProduct(Entity entity) {
        if (entity == null) {
            return;
        }
        int protectionTicks = newProductProtectionTicks;
        if (entity instanceof ItemEntity item) {
            int cooldownTicks = conversionCooldownTicks;
            if (protectionTicks <= 0 && cooldownTicks <= 0) {
                return;
            }
            long now = item.level().getGameTime();
            long protectionUntil = protectionTicks > 0 ? now + protectionTicks : 0L;
            long cooldownUntil = cooldownTicks > 0 ? now + cooldownTicks : 0L;
            set(item, get(item).withTemporary(protectionUntil, cooldownUntil));
            return;
        }
        if (protectionTicks <= 0) {
            return;
        }
        long now = entity.level().getGameTime();
        entityProductProtection.put(entity.getUUID(), now + protectionTicks);
        pruneEntityProtection(now);
    }

    // 内存保护表按到期刻剔除：表规模由同一时刻在场产物数量决定，只有超过阈值才扫一次
    private static void pruneEntityProtection(long gameTime) {
        if (entityProductProtection.size() <= ENTITY_PROTECTION_PRUNE_SIZE) {
            return;
        }
        entityProductProtection.values().removeIf(until -> until <= gameTime);
    }

    // 授予返还物状态：永久保护 + 永久禁转（返还交付时调用）
    public static void grantPermanentReturn(ItemEntity entity) {
        if (entity == null) {
            return;
        }
        set(entity, get(entity).withPermanentReturn());
    }

    /**
     * 合并完成时把源实体状态并入载体：临时到期刻取较晚者，死亡排除与转化提交位取并集。
     * 排除状态作用于合并后的整堆；部分合入后仍存活的源堆继续保留自己的状态。
     */
    public static void mergeInto(ItemEntity destination, ItemEntity origin) {
        if (destination == null || origin == null) {
            return;
        }
        set(destination, get(destination).mergeCompatible(get(origin)));
        if (origin.isRemoved() || origin.getItem().isEmpty()) {
            clear(origin);
        }
    }
}
