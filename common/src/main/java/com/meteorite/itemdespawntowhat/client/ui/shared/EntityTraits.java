package com.meteorite.itemdespawntowhat.client.ui.shared;

import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * 实体类型的能力分类缓存：区分生物与其他实体，并判断该类型是否吃年龄。
 * <p>分类依据是实体原型实例的类型判定（Mob / AgeableMob），与执行器 SpawnEntityExecutor
 * 判定是否写入年龄的口径一致。MobCategory 不能用于分类：MISC 同时包含村民、铁傀儡与盔甲架。
 * <p>结果只随客户端世界换代失效：实体类型注册表可能被数据包改动，换世界后必须重判。
 * 不在世界内、id 无法解析或原型实例化抛错时返回 UNKNOWN，界面按保守口径处理（视为生物且吃年龄）。
 * <p>「能不能作为产物」的判定在 {@link EntityProduct#isExcludedEntityProduct}：不可召唤类型与技术性实体
 * 不进任何一档列表，也不需要世界上下文，因此单独由 {@link #isExcluded(String)} 提供。
 */
public final class EntityTraits {
    // 单次会话内可能出现的实体类型数量上限：超出后不再写缓存，避免被异常 id 撑大
    private static final int MAX_ENTRIES = 512;
    // 信息不足时的保守结论：按生物且吃年龄处理，避免误删年龄输入
    public static final Traits UNKNOWN = new Traits(true, true);

    private static final Map<String, Traits> CACHE = new HashMap<>();
    private static @Nullable Object world;

    private EntityTraits() { }

    /**
     * 实体类型的能力分类。
     * @param mob 是否为生物（Mob 及其子类）
     * @param ageable 是否吃年龄（AgeableMob 及其子类）
     */
    public record Traits(boolean mob, boolean ageable) { }

    /** 清空缓存，下次读取时按当前世界重新判定。 */
    public static void clear() {
        CACHE.clear();
        world = null;
    }

    /**
     * 类型 id 是否为生物。
     * @param id 形如 minecraft:cow 的实体类型 id；空值返回保守结论
     */
    public static boolean isMob(@Nullable String id) {
        return traits(id).mob();
    }

    /**
     * 类型 id 是否吃年龄。
     * @param id 实体类型 id；空值返回保守结论
     */
    public static boolean isAgeable(@Nullable String id) {
        return traits(id).ageable();
    }

    /**
     * 类型 id 能否作为转化产物：不可召唤类型（玩家、浮漂）与技术性实体（展示、标记、瞬态表现）返回 false。
     *
     * <p>判定只依赖实体类型注册表，不需要世界上下文，因此不受「不在世界内」的保守口径影响。
     * @param id 实体类型 id；空值或解析失败返回 true（不额外排除，交给加载期校验）
     */
    public static boolean isExcluded(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return false;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        return type != null && EntityProduct.isExcludedEntityProduct(type);
    }

    /**
     * 读取类型的能力分类。罕见路径（不在世界内、解析失败、实例化抛错）不写缓存，等条件满足后重判。
     * @param id 实体类型 id
     */
    public static Traits traits(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return UNKNOWN;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (world != level) {
            CACHE.clear();
            world = level;
        }
        if (level == null) {
            return UNKNOWN;
        }
        Traits cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        Traits resolved = classify(level, id);
        if (resolved == null) {
            return UNKNOWN;
        }
        if (CACHE.size() < MAX_ENTRIES) {
            CACHE.put(id, resolved);
        }
        return resolved;
    }

    // 原型实例是判定的唯一可靠依据；模组实体可能违反非空契约，故保留防御检查
    private static @Nullable Traits classify(ClientLevel level, String id) {
        try {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null) {
                return null;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
            if (type == null) {
                return null;
            }
            // 排除类型不实例化：玩家这类没有工厂的类型会直接失败，而且它们本来就进不了列表
            if (EntityProduct.isExcludedEntityProduct(type)) {
                return new Traits(false, false);
            }
            Entity prototype = type.create(level);
            if (prototype == null) {
                return null;
            }
            if (prototype instanceof Mob mob) {
                return new Traits(true, mob instanceof AgeableMob);
            }
            return new Traits(false, prototype instanceof AgeableMob);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
