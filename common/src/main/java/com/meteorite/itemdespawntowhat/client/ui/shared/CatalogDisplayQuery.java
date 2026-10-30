package com.meteorite.itemdespawntowhat.client.ui.shared;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 客户端目录展示查询共用的文本匹配与页范围计算。 */
public final class CatalogDisplayQuery {
    public record PageRange(int from, int to, int pageCount) { }

    private CatalogDisplayQuery() { }

    /** 归一化搜索词。 */
    public static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    /** 匹配稳定 id、本地化名称或副标题。 */
    public static boolean matches(String needle, String id, Component label, @Nullable Component subLabel) {
        return contains(id, needle) || contains(label.getString(), needle)
                || subLabel != null && contains(subLabel.getString(), needle);
    }

    /** 计算从零开始的页数和当前页半开区间。空结果仍显示一页。 */
    public static PageRange range(int total, int page, int pageSize) {
        int count = Math.max(1, (total + pageSize - 1) / pageSize);
        int safePage = Math.clamp(page, 0, count - 1);
        int from = safePage * pageSize;
        return new PageRange(from, Math.min(total, from + pageSize), count);
    }

    private static boolean contains(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
