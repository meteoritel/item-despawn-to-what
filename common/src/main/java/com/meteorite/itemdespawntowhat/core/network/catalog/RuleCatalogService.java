package com.meteorite.itemdespawntowhat.core.network.catalog;

import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogSource;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogSources;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.transport.RequestRuleCatalogPayload;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * <p>缓存归属当前服务端实例：条目以请求时的服务端实例为界，服务端实例变化即先整体清空再重建，
 * 因此同一进程内先后启动的不同服务端不会复用另一服务端的内容。缓存只保留当前实例的条目，
 * 不为历次服务端保留值图（与 {@code RuleCatalogSources} 的源列表缓存一致）。</p>
 * <p>两种失效来源职责不同：{@link #invalidate()} / {@link #invalidate(RuleCatalogType)} 是显式失效，只在保存真正写出后由
 * RuleEditService 调用；来源修订号在读取时不一致则就地重建缓存条目，无需调用方清空，也不要求每次重载清空。</p>
 */
public final class RuleCatalogService {

    // 未指定时的默认每页条数
    public static final int DEFAULT_PAGE_SIZE = 50;
    // 单页条数上限，与请求 payload 的上限一致
    public static final int MAX_PAGE_SIZE = RequestRuleCatalogPayload.MAX_PAGE_SIZE;

    // 每类型缓存：来源修订号 + 该修订号下的全量候选；只保存 currentServer 这一个服务端实例的条目
    private static final Map<RuleCatalogType, Cached> CACHE = new EnumMap<>(RuleCatalogType.class);

    // 当前缓存归属的服务端实例；与请求实例不同即视为换服并整体清空缓存
    private static MinecraftServer currentServer;

    // 来源实现体报错的记录器：没有日志就无法把「该类型无数据源」与「实现体抛错」区分开
    private static final Logger LOGGER = LogManager.getLogger();

    private RuleCatalogService() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 取一页目录：filter 为大小写不敏感的子串过滤（id/label/subLabel），空串或 null 表示不过滤
    // page 从 0 起，越界时夹到最后一页；pageSize<=0 用默认值，超过上限时按下限截断
    public static synchronized RuleCatalog page(@Nullable MinecraftServer server,
                                                RuleCatalogType type,
                                                @Nullable String filter,
                                                int page,
                                                int pageSize) {
        if (type == null) {
            return RuleCatalog.empty(RuleCatalogType.ITEM, 0);
        }
        Cached cached = cached(server, type);
        if (cached == null) {
            return RuleCatalog.empty(type, 0);
        }
        int size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        List<RuleCatalogEntry> matched = applyFilter(cached.entries(), filter);
        int total = matched.size();
        int lastPageIndex = total == 0 ? 0 : (total - 1) / size;
        int safePage = Math.clamp(page, 0, lastPageIndex);
        int from = safePage * size;
        int to = Math.min(from + size, total);
        List<RuleCatalogEntry> pageEntries = from >= to ? List.of() : List.copyOf(matched.subList(from, to));
        return new RuleCatalog(type, cached.revision(), pageEntries, safePage >= lastPageIndex);
    }

    // 取该类型的来源修订号；无该类型数据源时为 0
    public static synchronized int revision(@Nullable MinecraftServer server, RuleCatalogType type) {
        Cached cached = cached(server, type);
        return cached == null ? 0 : cached.revision();
    }

    // 修订失效：清空全部类型缓存（保存真正写出后调用；来源内容变化由读取时的修订校验重建，不必在此清空）
    public static synchronized void invalidate() {
        CACHE.clear();
    }

    // 修订失效：只清某个类型（类型内内容变化时使用）
    public static synchronized void invalidate(RuleCatalogType type) {
        if (type != null) {
            CACHE.remove(type);
        }
    }

    // 取（必要时重建）某类型的缓存；服务端实例变化或来源修订号变化即重建
    private static @Nullable Cached cached(@Nullable MinecraftServer server, RuleCatalogType type) {
        if (server == null) {
            return null;
        }
        // 换服先清空：缓存只服务当前实例，不能沿用另一服务端的候选
        if (currentServer != server) {
            CACHE.clear();
            currentServer = server;
        }
        RuleCatalogSource source = sourceOf(server, type);
        if (source == null) {
            CACHE.remove(type);
            return null;
        }
        int revision = source.revision();
        Cached existing = CACHE.get(type);
        if (existing != null && existing.revision() == revision) {
            return existing;
        }
        List<RuleCatalogEntry> all = source.all();
        Cached rebuilt = new Cached(revision, all == null ? List.of() : List.copyOf(all));
        CACHE.put(type, rebuilt);
        return rebuilt;
    }

    // 在冻结数据源里找对应类型；缺失或实现体抛错时返回 null 并丢弃该类型缓存
    private static @Nullable RuleCatalogSource sourceOf(MinecraftServer server, RuleCatalogType type) {
        List<RuleCatalogSource> sources;
        try {
            sources = RuleCatalogSources.builtin(server);
        } catch (RuntimeException e) {
            LOGGER.warn("读取规则目录来源失败，本类型按无数据源处理：类型={}", type, e);
            return null;
        }
        if (sources == null) {
            return null;
        }
        for (RuleCatalogSource source : sources) {
            if (source != null && source.type() == type) {
                return source;
            }
        }
        return null;
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

    // 某类型在某修订号下的全量候选
    private record Cached(int revision, List<RuleCatalogEntry> entries) {
    }
}
