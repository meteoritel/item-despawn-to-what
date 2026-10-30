package com.meteorite.itemdespawntowhat.client.ui.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 两个目录宿主共用的搜索语法、过滤与不可变分页结果。 */
public final class CatalogDisplayQuery {
    /** 客户端展示页容量，与网络拉取批量分别管理。 */
    public static final int PAGE_SIZE = 200;

    /** 一次页请求的内容与导航；没有来源页时 content 为空。 */
    public record Page<T>(@Nullable T content, int total, int index, int count) {
        /** 是否存在后页。 */
        public boolean hasNext() { return index + 1 < count; }
    }

    /** 同一次查询的所有页；翻页复用已过滤内容与切片。 */
    public record Pages<T>(List<T> contents, int total) {
        /** 发布不可变查询结果。 */
        public Pages { contents = List.copyOf(contents); }
        /** 集中夹紧页号；零来源也保留一页空态。 */
        public Page<T> page(int requested) {
            int count = Math.max(1, contents.size());
            int index = Math.clamp(requested, 0, count - 1);
            return new Page<>(contents.isEmpty() ? null : contents.get(index), total, index, count);
        }
    }

    /** 分组目录的一个子页，保留组身份与组内页号。 */
    public record Part<G, T>(G group, List<T> items, int index, int count) { }

    /** 前导 # 仅在支持混合标签的目录中解释为标签搜索。 */
    public record Search(String needle, boolean tagsOnly) {
        /** 匹配稳定 id、本地化名称与来源原文。 */
        public boolean matches(String id, boolean tag, Component label, @Nullable Component subLabel) {
            return (!tagsOnly || tag) && (contains(id, needle) || contains(label.getString(), needle)
                    || subLabel != null && contains(subLabel.getString(), needle));
        }
    }

    private CatalogDisplayQuery() { }

    /** 归一化查询；引用语法仍由 TaggedId 单独解释。 */
    public static Search search(String query, boolean includeTags) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean tagsOnly = includeTags && text.startsWith("#");
        return new Search(tagsOnly ? text.substring(1) : text, tagsOnly);
    }

    /** 平铺目录一次过滤并建立页切片。 */
    public static <T> Pages<List<T>> flat(List<T> source, Predicate<T> matches) {
        List<T> filtered = matching(source, matches);
        return new Pages<>(split(filtered), filtered.size());
    }

    /** 保留组顺序和空组；这里只处理轻量键，完整展示条目由宿主按当前页创建。 */
    public static <G, T> Pages<Part<G, T>> grouped(List<G> groups, Function<G, List<T>> candidates,
                                                BiPredicate<G, T> matches) {
        List<Part<G, T>> pages = new ArrayList<>();
        int total = 0;
        for (G group : groups) {
            List<T> filtered = matching(candidates.apply(group), item -> matches.test(group, item));
            List<List<T>> parts = split(filtered);
            total += filtered.size();
            for (int index = 0; index < parts.size(); index++) {
                pages.add(new Part<>(group, parts.get(index), index, parts.size()));
            }
        }
        return new Pages<>(pages, total);
    }

    private static <T> List<T> matching(List<T> source, Predicate<T> matches) {
        return source.stream().filter(matches).toList();
    }

    private static <T> List<List<T>> split(List<T> source) {
        if (source.isEmpty()) return List.of(List.of());
        List<List<T>> pages = new ArrayList<>();
        for (int from = 0; from < source.size(); from += PAGE_SIZE) {
            pages.add(List.copyOf(source.subList(from, Math.min(source.size(), from + PAGE_SIZE))));
        }
        return List.copyOf(pages);
    }

    private static boolean contains(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
