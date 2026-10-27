package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.catalog.RuleCatalogType;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** 按类型聚合完整目录；分页失败保留部分结果，重新打开先验证目录修订。 */
final class CatalogDirectory {
    private static final int PAGE_SIZE = 200;
    private static final long TIMEOUT_MILLIS = 15_000L;

    @FunctionalInterface
    interface Sender {
        boolean send(RuleCatalogType type, String requestId, String filter, int page, int pageSize);
    }

    private static final class Load {
        final PendingRequest request = new PendingRequest();
        final LinkedHashMap<String, RuleCatalogEntry> entries = new LinkedHashMap<>();
        int page;
        int revision = -1;
        boolean complete;
        boolean validating;
        @Nullable RuleCatalog snapshot;
        ResourceView<RuleCatalog> published = ResourceView.empty();
    }

    private final Map<RuleCatalogType, Load> loads = new EnumMap<>(RuleCatalogType.class);

    void request(RuleCatalogType type, Sender sender) {
        Load load = loads.computeIfAbsent(type, ignored -> new Load());
        load.request.expire(System.currentTimeMillis());
        if (!load.request.waiting() && !load.request.failed() && !load.complete) sendPage(type, load, sender);
    }

    void refresh(RuleCatalogType type, Sender sender) {
        Load load = loads.get(type);
        if (load == null || !load.complete || load.request.waiting()) { request(type, sender); return; }
        load.validating = true;
        load.page = 0;
        sendPage(type, load, sender);
    }

    void retry(RuleCatalogType type, Sender sender) {
        Load load = loads.computeIfAbsent(type, ignored -> new Load());
        load.request.complete();
        sendPage(type, load, sender);
    }

    ResourceView<RuleCatalog> view(RuleCatalogType type) {
        Load load = loads.get(type);
        if (load == null) return ResourceView.empty();
        if (load.published.value() != load.snapshot || load.published.loading() != load.request.waiting()
                || load.published.failed() != load.request.failed()) {
            load.published = new ResourceView<>(load.snapshot, load.request.waiting(), load.request.failed());
        }
        return load.published;
    }

    void advance(Sender sender) {
        for (RuleCatalogType type : loads.keySet()) request(type, sender);
    }

    boolean stale(RuleCatalogType type, String requestId) {
        Load load = loads.get(type);
        if (load == null) return true;
        load.request.expire(System.currentTimeMillis());
        return !load.request.matches(requestId);
    }

    void accept(RuleCatalogType type, String requestId, @Nullable RuleCatalog catalog,
                boolean canQuery, Sender sender) {
        if (stale(type, requestId)) return;
        Load load = loads.get(type);
        if (catalog == null) { load.request.fail(); load.validating = false; return; }
        load.request.complete();
        if (load.validating && load.revision == catalog.revision()) { load.validating = false; return; }
        load.validating = false;
        if (load.revision >= 0 && load.revision != catalog.revision()) {
            load.entries.clear();
            load.page = 0;
            load.revision = -1;
            load.snapshot = null;
            load.complete = false;
            if (canQuery) sendPage(type, load, sender);
            return;
        }
        load.revision = catalog.revision();
        catalog.entries().forEach(entry -> load.entries.putIfAbsent(entry.id(), entry));
        load.complete = catalog.lastPage();
        load.snapshot = new RuleCatalog(type, load.revision, List.copyOf(load.entries.values()), load.complete);
        load.page++;
        if (!load.complete && canQuery) sendPage(type, load, sender);
    }

    boolean fail(String requestId) {
        for (Load load : loads.values()) {
            load.request.expire(System.currentTimeMillis());
            if (!load.request.matches(requestId)) continue;
            load.request.fail();
            load.validating = false;
            return true;
        }
        return false;
    }

    void tick(long now) { loads.values().forEach(load -> load.request.expire(now)); }
    void clear() { loads.clear(); }

    private void sendPage(RuleCatalogType type, Load load, Sender sender) {
        String id = load.request.start(System.currentTimeMillis(), TIMEOUT_MILLIS);
        if (!sender.send(type, id, "", load.page, PAGE_SIZE)) load.request.fail();
    }
}
