package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 源匹配：决定一条规则适用于哪些掉落物物品。
 * entries 为匹配项（物品或标签），exclude 为排除项；排除优先于匹配。
 */
public record SourceMatcher(List<SourceEntry> entries, List<SourceEntry> exclude) {

    public static final Codec<SourceMatcher> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SourceEntry.CODEC.listOf().fieldOf(RuleFields.SOURCE_ITEMS).forGetter(SourceMatcher::entries),
            SourceEntry.CODEC.listOf().optionalFieldOf(RuleFields.SOURCE_EXCLUDE, List.of()).forGetter(SourceMatcher::exclude)
    ).apply(instance, SourceMatcher::new));

    public SourceMatcher {
        entries = List.copyOf(entries);
        exclude = List.copyOf(exclude);
    }

    // 只填匹配项、无排除项的便捷构造
    public static SourceMatcher of(List<SourceEntry> entries) {
        return new SourceMatcher(entries, List.of());
    }

    // 是否没有任何匹配项（视为无效配置）
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    // 判断某个物品是否命中本匹配器；标签解析由调用方注入（运行时按需展开并缓存），本层不访问 Mojang 注册表
    public boolean matches(ResourceLocation itemId, Function<ResourceLocation, Set<ResourceLocation>> tagResolver) {
        for (SourceEntry entry : exclude) {
            if (matchesEntry(entry, itemId, tagResolver)) {
                return false;
            }
        }
        for (SourceEntry entry : entries) {
            if (matchesEntry(entry, itemId, tagResolver)) {
                return true;
            }
        }
        return false;
    }

    // 单项匹配：物品直接比较，标签查展开结果
    private static boolean matchesEntry(SourceEntry entry, ResourceLocation itemId,
                                        Function<ResourceLocation, Set<ResourceLocation>> tagResolver) {
        if (!entry.tag()) {
            return entry.id().equals(itemId);
        }
        Set<ResourceLocation> members = tagResolver.apply(entry.id());
        return members != null && members.contains(itemId);
    }

    // 字段名常量引用点（统一指向 core/api/RuleFields）
    public static final String ITEMS_FIELD = RuleFields.SOURCE_ITEMS;
    public static final String EXCLUDE_FIELD = RuleFields.SOURCE_EXCLUDE;

    // 供校验层统一输出的字段路径
    public static String fieldPath(String child) {
        return RuleFields.SOURCE + "." + child;
    }
}
