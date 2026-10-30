package com.meteorite.itemdespawntowhat.core.catalog;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 规则目录服务：消费 core/catalog 的候选数据源，
 * 负责按"类型 + 来源修订号"缓存全量候选、大小写不敏感过滤、分页切分与修订失效，产出可下发的 RuleCatalog。
 * 只做读取与切分，不做权限与会话校验（那属 RuleEditService）。
 * <p>来源列表与分页缓存归属本次服务端的编辑宿主，随宿主关闭释放。</p>
 * <p>两种失效来源职责不同：{@link #invalidate()} 是显式失效，只在保存真正写出后由
 * RuleEditService 调用；来源修订号在读取时不一致则就地重建缓存条目，无需调用方清空，也不要求每次重载清空。</p>
 */
public final class RuleCatalogService {

    /**
     * 请求入口校验使用的单页条数上限
     */
    public static final int MAX_PAGE_SIZE = 200;

    // 每类型缓存：来源修订号 + 该修订号下的全量候选；由本次服务端持有
    private final Map<RuleCatalogType, CatalogSourceSnapshot> cache = new EnumMap<>(RuleCatalogType.class);

    // 来源与缓存随同一编辑宿主创建并释放
    private final List<SnapshotCatalogSource> sources;

    /** @param server 当前服务端；仅本实例持有由其注册表创建的来源 */
    public RuleCatalogService(MinecraftServer server) {
        sources = RuleCatalogSources.builtinSnapshots(server);
    }

    /**
     * 取一页目录：filter 为大小写不敏感的子串过滤（id/label/subLabel），空串或 null 表示不过滤
     * page 从 0 起，越界时夹到最后一页；调用方已完成 type 非空与 pageSize 为 1..MAX_PAGE_SIZE 的校验。
     */
    public synchronized RuleCatalog page(RuleCatalogType type,
                                                @Nullable String filter,
                                                int page,
                                                int pageSize) {
        CatalogSourceSnapshot cached = cached(type);
        List<RuleCatalogEntry> matched = applyFilter(cached.entries(), filter);
        int total = matched.size();
        int lastPageIndex = total == 0 ? 0 : (total - 1) / pageSize;
        int safePage = Math.clamp(page, 0, lastPageIndex);
        int from = safePage * pageSize;
        int to = Math.min(from + pageSize, total);
        List<RuleCatalogEntry> pageEntries = from >= to ? List.of() : List.copyOf(matched.subList(from, to));
        return new RuleCatalog(type, cached.revision(), pageEntries, safePage >= lastPageIndex);
    }

    /**
     * 修订失效：清空全部类型缓存（保存真正写出后调用；来源内容变化由读取时的修订校验重建，不必在此清空）
     */
    public synchronized void invalidate() {
        cache.clear();
    }

    // 每页只读取一次来源，缓存复用同批修订和条目。
    private CatalogSourceSnapshot cached(RuleCatalogType type) {
        CatalogSourceSnapshot snapshot = sourceOf(type).snapshot();
        CatalogSourceSnapshot existing = cache.get(type);
        if (existing != null && existing.revision() == snapshot.revision()) return existing;
        cache.put(type, snapshot);
        return snapshot;
    }

    // 内置工厂穷举所有目录；缺失属于装配错误，保留具体类型供诊断。
    private SnapshotCatalogSource sourceOf(RuleCatalogType type) {
        for (SnapshotCatalogSource source : sources) {
            if (source.type() == type) return source;
        }
        throw new IllegalArgumentException("Missing catalog source: " + type);
    }

    // 大小写不敏感的 id/label/subLabel 子串过滤；同时保留过滤前的相对顺序
    private static List<RuleCatalogEntry> applyFilter(List<RuleCatalogEntry> entries, @Nullable String filter) {
        if (filter == null || filter.isBlank()) {
            return entries;
        }
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<RuleCatalogEntry> matched = new ArrayList<>();
        for (RuleCatalogEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (contains(entry.id(), needle) || contains(entry.label(), needle) || contains(entry.subLabel(), needle)) {
                matched.add(entry);
            }
        }
        return matched;
    }

    // 单个字段的大小写不敏感子串匹配
    private static boolean contains(@Nullable String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

}
