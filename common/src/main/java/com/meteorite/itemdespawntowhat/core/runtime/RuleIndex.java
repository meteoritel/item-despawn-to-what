package com.meteorite.itemdespawntowhat.core.runtime;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.SourceEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则索引：把规则集合整理成「物品 → 候选规则」的查询结构。
 * 直接物品项在构建期建索引；标签项**懒展开**并缓存（首次查询到该标签时才解析成员），
 * 缓存随 reload 整体替换（索引对象被替换），不做单独失效。
 */
public final class RuleIndex {

    // 规则排序：优先级降序 → 条件叶数降序 → 定义序（stable sort 保证定义序兜底）
    private static final Comparator<Rule> RULE_ORDER = Comparator
            .comparingInt(Rule::priority).reversed()
            .thenComparing(Comparator.comparingInt(Rule::complexity).reversed());

    private final List<LoadedRule<Rule>> ordered;
    private final Map<ResourceLocation, List<Rule>> directIndex;
    private final List<Rule> tagRules;
    private final Map<ResourceLocation, Set<ResourceLocation>> tagCache = new HashMap<>();
    private final Map<ResourceLocation, List<Rule>> queryCache = new HashMap<>();

    private RuleIndex(List<LoadedRule<Rule>> ordered,
                      Map<ResourceLocation, List<Rule>> directIndex,
                      List<Rule> tagRules) {
        this.ordered = ordered;
        this.directIndex = directIndex;
        this.tagRules = tagRules;
    }

    // 构建索引：不可运行的规则（disabled / 无效果）被剔除
    public static RuleIndex build(List<LoadedRule<Rule>> loaded, IssueCollector issues) {
        List<LoadedRule<Rule>> runnable = new ArrayList<>(loaded.size());
        for (LoadedRule<Rule> entry : loaded) {
            if (entry.value().isRunnable()) {
                runnable.add(entry);
            }
        }
        runnable.sort(Comparator.comparing(entry -> entry.value(), RULE_ORDER));

        Map<ResourceLocation, List<Rule>> direct = new LinkedHashMap<>();
        List<Rule> tagRules = new ArrayList<>();
        for (LoadedRule<Rule> entry : runnable) {
            Rule rule = entry.value();
            boolean hasTag = false;
            for (SourceEntry source : rule.source().entries()) {
                if (source.tag()) {
                    hasTag = true;
                } else {
                    List<Rule> bucket = direct.computeIfAbsent(source.id(), key -> new ArrayList<>());
                    if (!bucket.contains(rule)) {
                        bucket.add(rule);
                    }
                }
            }
            if (hasTag) {
                tagRules.add(rule);
            }
        }
        return new RuleIndex(List.copyOf(runnable), direct, List.copyOf(tagRules));
    }

    // 空索引：无任何规则时使用
    public static RuleIndex empty() {
        return new RuleIndex(List.of(), Map.of(), List.of());
    }

    public boolean isEmpty() {
        return ordered.isEmpty();
    }

    // 全部可运行规则（已排序），供调试与命令输出
    public List<LoadedRule<Rule>> ordered() {
        return ordered;
    }

    // 查询某个物品的候选规则（已按优先级排序）；无候选返回空列表
    public List<Rule> candidates(ResourceLocation itemId) {
        List<Rule> cached = queryCache.get(itemId);
        if (cached != null) {
            return cached;
        }
        if (tagRules.isEmpty()) {
            List<Rule> directOnly = directIndex.getOrDefault(itemId, List.of()).stream()
                    .filter(rule -> rule.source().matches(itemId, this::expandItemTag)).toList();
            queryCache.put(itemId, directOnly);
            return directOnly;
        }
        // 先算出命中集合（直接命中 + 标签命中），再按全局顺序过滤，
        // 这样"优先级 → 叶数 → 定义序"的第三条排序键在直接/标签并列时同样成立
        Set<Rule> hits = new LinkedHashSet<>(directIndex.getOrDefault(itemId, List.of()));
        for (Rule rule : tagRules) {
            if (rule.source().matches(itemId, this::expandItemTag)) {
                hits.add(rule);
            }
        }
        List<Rule> result = new ArrayList<>(hits.size());
        for (LoadedRule<Rule> entry : ordered) {
            if (hits.contains(entry.value()) && entry.value().source().matches(itemId, this::expandItemTag)) {
                result.add(entry.value());
            }
        }
        List<Rule> immutable = List.copyOf(result);
        queryCache.put(itemId, immutable);
        return immutable;
    }

    // 懒展开物品标签并缓存；标签不存在时缓存空集合（避免反复查表）
    private Set<ResourceLocation> expandItemTag(ResourceLocation tagId) {
        return tagCache.computeIfAbsent(tagId, id -> {
            HolderSet.Named<Item> holders = BuiltInRegistries.ITEM
                    .getTag(TagKey.create(Registries.ITEM, id))
                    .orElse(null);
            if (holders == null) {
                return Set.of();
            }
            Set<ResourceLocation> members = new HashSet<>();
            for (Holder<Item> holder : holders) {
                holder.unwrapKey().ifPresent(key -> members.add(key.location()));
            }
            return Set.copyOf(members);
        });
    }
}
