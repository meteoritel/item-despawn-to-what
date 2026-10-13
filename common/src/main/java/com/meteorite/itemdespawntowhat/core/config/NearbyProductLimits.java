package com.meteorite.itemdespawntowhat.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 所有规则共用的邻近开组阈值：以源物品为中心、按半径统计已存在的产出，决定本次转化还能开多少单位。
 * 三类上限（物品 / 实体 / 经验球）各自独立；上限为 0 表示关闭该类检查，半径下限为 1。
 */
public record NearbyProductLimits(int itemLimit, int itemRadius, int entityLimit, int entityRadius,
                                  int experienceOrbLimit, int experienceRadius) {

    // 默认阈值：物品 1024 / 实体 128 / 经验球 64，三类半径均为 6 格
    public static final NearbyProductLimits DEFAULT = new NearbyProductLimits(1024, 6, 128, 6, 64, 6);

    // 配置编解码：字段名 snake_case；上限允许 0（关闭该类检查），半径下限 1
    public static final Codec<NearbyProductLimits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 1000000).optionalFieldOf("item_limit", DEFAULT.itemLimit()).forGetter(NearbyProductLimits::itemLimit),
            Codec.intRange(1, 32).optionalFieldOf("item_radius", DEFAULT.itemRadius()).forGetter(NearbyProductLimits::itemRadius),
            Codec.intRange(0, 1000000).optionalFieldOf("entity_limit", DEFAULT.entityLimit()).forGetter(NearbyProductLimits::entityLimit),
            Codec.intRange(1, 32).optionalFieldOf("entity_radius", DEFAULT.entityRadius()).forGetter(NearbyProductLimits::entityRadius),
            Codec.intRange(0, 1000000).optionalFieldOf("experience_orb_limit", DEFAULT.experienceOrbLimit()).forGetter(NearbyProductLimits::experienceOrbLimit),
            Codec.intRange(1, 32).optionalFieldOf("experience_radius", DEFAULT.experienceRadius()).forGetter(NearbyProductLimits::experienceRadius)
    ).apply(instance, NearbyProductLimits::new));
}
