package com.meteorite.itemdespawntowhat.client.net;

import com.meteorite.itemdespawntowhat.core.network.transport.RuleChunkPayload;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleSourceView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import org.jetbrains.annotations.Nullable;

/** 会话内来源视图的按需请求、超时与 LRU；重组机制复用单批次缓冲。 */
final class SourceViewRequests {
    private static final int CACHE_LIMIT = 8;
    private static final long TIMEOUT_MILLIS = 5_000L;

    private static final class Load {
        final PendingRequest request = new PendingRequest();
        @Nullable RuleSourceView value;
        ResourceView<RuleSourceView> published = ResourceView.empty();
    }

    private final Map<String, Load> loads = new LinkedHashMap<>(16, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Load> eldest) {
            return size() > CACHE_LIMIT;
        }
    };
    private final RuleChunkBuffer chunks = new RuleChunkBuffer();

    void request(String ruleId, BiPredicate<String, String> sender) {
        Load load = loads.computeIfAbsent(ruleId, ignored -> new Load());
        load.request.expire(System.currentTimeMillis());
        if (load.value != null || load.request.waiting()) return;
        String id = load.request.start(System.currentTimeMillis(), TIMEOUT_MILLIS);
        if (!sender.test(ruleId, id)) load.request.fail();
    }

    ResourceView<RuleSourceView> view(String ruleId) {
        Load load = loads.get(ruleId);
        if (load == null) return ResourceView.empty();
        if (load.published.value() != load.value || load.published.loading() != load.request.waiting()
                || load.published.failed() != load.request.failed()) {
            load.published = new ResourceView<>(load.value, load.request.waiting(), load.request.failed());
        }
        return load.published;
    }

    boolean pending(String requestId) { return find(requestId) != null; }

    void accept(String requestId, @Nullable RuleSourceView view) {
        Load load = find(requestId);
        if (load == null) return;
        load.value = view;
        if (view == null) load.request.fail();
        else load.request.complete();
    }

    boolean fail(String requestId) {
        Load load = find(requestId);
        if (load == null) return false;
        load.request.fail();
        return true;
    }

    @Nullable String chunk(RuleChunkPayload payload, long tick) {
        return pending(payload.correlationId()) ? chunks.accept(payload, tick) : null;
    }

    void tick(long now) { loads.values().forEach(load -> load.request.expire(now)); }
    void clear() { loads.clear(); chunks.clear(); }

    private @Nullable Load find(String requestId) {
        for (Load load : loads.values()) {
            load.request.expire(System.currentTimeMillis());
            if (load.request.matches(requestId)) return load;
        }
        return null;
    }
}
