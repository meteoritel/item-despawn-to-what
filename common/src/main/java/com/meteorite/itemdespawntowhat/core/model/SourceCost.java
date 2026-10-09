package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.TaggedId;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

/** 每个源物品引用的固定成本；具体物品优先于标签，多个标签按来源声明顺序匹配。 */
public record SourceCost(Map<TaggedId, Integer> counts) {
    public static final int DEFAULT_COUNT = 1;
    public static final Codec<SourceCost> CODEC = Codec.unboundedMap(TaggedId.CODEC,
            Codec.intRange(DEFAULT_COUNT, Integer.MAX_VALUE)).xmap(SourceCost::new, SourceCost::counts);

    public SourceCost {
        counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    public int forStack(SourceMatcher source, ItemStack stack) {
        return forStack(source, stack, DEFAULT_COUNT);
    }

    public int forStack(SourceMatcher source, ItemStack stack, int fallback) {
        TaggedId direct = new TaggedId(BuiltInRegistries.ITEM.getKey(stack.getItem()), false);
        Integer exact = counts.get(direct);
        if (exact != null) return exact;
        for (SourceEntry entry : source.entries()) {
            TaggedId reference = entry.toTagged();
            if (reference.tag() && counts.containsKey(reference)
                    && stack.is(TagKey.create(BuiltInRegistries.ITEM.key(), reference.id()))) {
                return counts.get(reference);
            }
        }
        return fallback;
    }
}
