package com.meteorite.itemdespawntowhat.core.state;

import com.meteorite.itemdespawntowhat.core.model.TriggerKind;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;

import java.util.Set;

/**
 * 环境销毁的伤害归因常量表（D1）：common 内唯一来源，平台层不重复定义。
 * 火 = 直接点燃（in_fire）+ 火焰持续伤害（on_fire）；岩浆 = lava；仙人掌 = cactus。
 * 明确不使用 DamageTypeTags.IS_FIRE：该标签还含 lava / hot_floor / campfire / 火球，
 * 会把岩浆误判成火，违反 PLAN §4.4「岩浆与火重叠时先区分岩浆」。
 */
public final class DamageClassification {

    // 火类别：仅直接点燃与火焰持续伤害
    private static final Set<ResourceKey<DamageType>> FIRE_TYPES = Set.of(DamageTypes.IN_FIRE, DamageTypes.ON_FIRE);
    // 岩浆类别：单独判定，与火重叠时先归岩浆
    private static final ResourceKey<DamageType> LAVA_TYPE = DamageTypes.LAVA;
    // 仙人掌类别
    private static final ResourceKey<DamageType> CACTUS_TYPE = DamageTypes.CACTUS;

    private DamageClassification() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 归类一次伤害；不属于三类环境伤害时返回 null（未知伤害不自动归为火）
    public static TriggerKind classify(DamageSource source) {
        if (source == null) {
            return null;
        }
        // 判定顺序固定：先岩浆，再火，最后仙人掌
        if (source.is(LAVA_TYPE)) {
            return TriggerKind.LAVA;
        }
        for (ResourceKey<DamageType> key : FIRE_TYPES) {
            if (source.is(key)) {
                return TriggerKind.FIRE;
            }
        }
        if (source.is(CACTUS_TYPE)) {
            return TriggerKind.CACTUS;
        }
        return null;
    }

    // 是否属于三类环境伤害
    public static boolean isEnvironmental(DamageSource source) {
        return classify(source) != null;
    }
}
