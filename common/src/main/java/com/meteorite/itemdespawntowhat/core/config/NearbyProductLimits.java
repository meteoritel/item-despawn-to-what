package com.meteorite.itemdespawntowhat.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 所有规则共用的邻近开组阈值：以源物品为中心、按半径统计已存在的产出，决定本次转化还能开多少单位。
 * 物品与实体各自检查；上限为 0 时关闭该类检查，半径下限为 1。
 * experienceOrbLimit / experienceRadius 仅保留配置读写兼容，不再影响开组或经验产出。
 */
public record NearbyProductLimits(int itemLimit, int itemRadius, int entityLimit, int entityRadius,
                                  int experienceOrbLimit, int experienceRadius) {

    private static final ConfigField<Integer> ITEM_LIMIT = ConfigField.integer("item_limit", 1024, 0, 1000000);
    private static final ConfigField<Integer> ITEM_RADIUS = ConfigField.integer("item_radius", 6, 1, 32);
    private static final ConfigField<Integer> ENTITY_LIMIT = ConfigField.integer("entity_limit", 128, 0, 1000000);
    private static final ConfigField<Integer> ENTITY_RADIUS = ConfigField.integer("entity_radius", 6, 1, 32);
    private static final ConfigField<Integer> EXPERIENCE_ORB_LIMIT = ConfigField.integer("experience_orb_limit", 64, 0, 1000000);
    private static final ConfigField<Integer> EXPERIENCE_RADIUS = ConfigField.integer("experience_radius", 6, 1, 32);
    static final List<ConfigField<?>> FIELDS = List.of(
            ITEM_LIMIT, ITEM_RADIUS, ENTITY_LIMIT, ENTITY_RADIUS, EXPERIENCE_ORB_LIMIT, EXPERIENCE_RADIUS);

    /** 物品与实体的缺省阈值及经验兼容字段的缺省值。 */
    public static final NearbyProductLimits DEFAULT = new NearbyProductLimits(
            ITEM_LIMIT.defaultValue(), ITEM_RADIUS.defaultValue(), ENTITY_LIMIT.defaultValue(),
            ENTITY_RADIUS.defaultValue(), EXPERIENCE_ORB_LIMIT.defaultValue(), EXPERIENCE_RADIUS.defaultValue());

    static final MapCodec<NearbyProductLimits> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ITEM_LIMIT.codec().forGetter(NearbyProductLimits::itemLimit),
            ITEM_RADIUS.codec().forGetter(NearbyProductLimits::itemRadius),
            ENTITY_LIMIT.codec().forGetter(NearbyProductLimits::entityLimit),
            ENTITY_RADIUS.codec().forGetter(NearbyProductLimits::entityRadius),
            EXPERIENCE_ORB_LIMIT.codec().forGetter(NearbyProductLimits::experienceOrbLimit),
            EXPERIENCE_RADIUS.codec().forGetter(NearbyProductLimits::experienceRadius)
    ).apply(instance, NearbyProductLimits::new));

    /** 字段名、范围和缺省值由配置声明提供的嵌套对象编解码器。 */
    public static final Codec<NearbyProductLimits> CODEC = MAP_CODEC.codec();
}
