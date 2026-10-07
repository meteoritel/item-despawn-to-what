package com.meteorite.itemdespawntowhat.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/*** 所有规则共用的邻近开组阈值；零关闭对应检查，已开始组不受截断。 */
public record NearbyProductLimits(int itemLimit, int itemRadius, int entityLimit, int entityRadius,
                                  int experienceOrbLimit, int experienceRadius) {
    public static final NearbyProductLimits DEFAULT = new NearbyProductLimits(1024, 6, 128, 6, 64, 6);
    public static final Codec<NearbyProductLimits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 1000000).optionalFieldOf("item_limit", DEFAULT.itemLimit()).forGetter(NearbyProductLimits::itemLimit),
            Codec.intRange(1, 32).optionalFieldOf("item_radius", DEFAULT.itemRadius()).forGetter(NearbyProductLimits::itemRadius),
            Codec.intRange(0, 1000000).optionalFieldOf("entity_limit", DEFAULT.entityLimit()).forGetter(NearbyProductLimits::entityLimit),
            Codec.intRange(1, 32).optionalFieldOf("entity_radius", DEFAULT.entityRadius()).forGetter(NearbyProductLimits::entityRadius),
            Codec.intRange(0, 1000000).optionalFieldOf("experience_orb_limit", DEFAULT.experienceOrbLimit()).forGetter(NearbyProductLimits::experienceOrbLimit),
            Codec.intRange(1, 32).optionalFieldOf("experience_radius", DEFAULT.experienceRadius()).forGetter(NearbyProductLimits::experienceRadius)
    ).apply(instance, NearbyProductLimits::new));
}
