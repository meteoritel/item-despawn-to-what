package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecoder;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.OverlayRuleReader;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleLoader;
import com.meteorite.itemdespawntowhat.core.load.RuleMerger;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunker;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotPayload;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.mojang.serialization.Codec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** 快照续接任务：保存完成后的列表刷新也按公共 tick 预算解码/编码，后台完成磁盘读取与 JSON 装配。 */
final class RuleSnapshotTask implements ServerTask {
    private static final Object SNAPSHOT_REALM = new Object();
    private record Source(byte[] revision, List<RawRuleEntry> entries, RuleSourceIndex index, IssueCollector issues) { }
    private final RuleEditServerContext context;
    private final MinecraftServer server;
    private final EditSessionManager sessions;
    private final RuleOverlayStore store;
    private final RuleSaveReloadContext reloadContext;
    private final String sessionId;
    private final String requestId;
    private final Consumer<List<CustomPacketPayload>> completion;
    private CompletableFuture<Source> reading;
    private CompletableFuture<List<CustomPacketPayload>> preparing;
    private Source source;
    private RuleDecoder<Rule> decoder;
    private Codec<Rule> encoder;
    private int version;
    private int cursor;
    private volatile boolean cancelled;
    private boolean finished;
    private final List<LoadedRule<Rule>> decoded = new ArrayList<>();
    private final Map<ResourceLocation, JsonObject> encoded = new LinkedHashMap<>();

    RuleSnapshotTask(RuleEditServerContext context, MinecraftServer server, EditSessionManager sessions,
                     String sessionId, String requestId, Consumer<List<CustomPacketPayload>> completion) {
        this.context = context; this.server = server; this.sessions = sessions;
        store = sessions.store();
        this.sessionId = sessionId; this.requestId = requestId; this.completion = completion;
        reloadContext = java.util.Objects.requireNonNull(context.saveReloadContext());
        reading = store.submit("snapshot-read", () -> {
            byte[] revision = sessions.readDiskRevision(() -> !cancelled);
            IssueCollector issues = new IssueCollector();
            reloadContext.baseIssues().forEach(issues::add);
            var load = reloadContext.loadContext();
            List<RawRuleEntry> entries = new ArrayList<>(reloadContext.baseRules());
            entries.addAll(OverlayRuleReader.read(load.overlayRoot(), load.overlayNamespace(), issues));
            if (!java.util.Arrays.equals(revision, sessions.readDiskRevision(() -> !cancelled))) {
                throw new IllegalStateException("准备快照时覆盖层修订已变化");
            }
            return new Source(revision, RuleMerger.merge(entries, issues), RuleSourceIndex.build(entries), issues);
        });
    }

    void schedule() { context.scheduleTask(SNAPSHOT_REALM, this, 1); }
    void invalidate() { cancelled = true; }
    @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }
    @Override public String tracingName() { return "rule-snapshot"; }

    @Override public StepResult step(ServerTickBudget budget) {
        if (finished) { return StepResult.done(1); }
        if (cancelled || !sessions.owns(sessionId) || context.saveReloadContext() != reloadContext) {
            invalidate();
            pollCompletion();
            return finished ? StepResult.done(1) : StepResult.yield(1, 1);
        }
        try {
            if (source == null) {
                if (!reading.isDone()) { return StepResult.yield(1, 1); }
                source = reading.join(); reading = null;
                sessions.acceptDiskRevision(source.revision(), false);
                version = sessions.version();
            }
            if (decoder == null && cursor < source.entries().size() && !budget.exhausted()) {
                // Codec 构建同样受公共 tick 预算约束，同一快照内复用，不为每条规则重建整棵 Codec。
                var load = reloadContext.loadContext();
                decoder = RuleCodecs.decoder(load.registryAccess(), load.effectTypes(), load.conditionTypes());
                encoder = RuleCodecs.codec(load.effectTypes(), load.conditionTypes());
            }
            int processed = 0;
            while (cursor < source.entries().size() && processed < RuleEditLimits.SNAPSHOT_ENTRIES_PER_STEP
                    && !budget.exhausted()) {
                RawRuleEntry entry = source.entries().get(cursor++);
                var load = reloadContext.loadContext();
                RuleLoader.decodeEntry(entry, decoder, source.issues())
                        .filter(rule -> RuleValidation.validate(rule, load.effectTypes(), load.conditionTypes(), source.issues(), entry.origin().display())
                                && RuleReferenceValidator.validate(rule, server, source.issues(), entry.origin().display()))
                        .ifPresent(rule -> {
                            decoded.add(new LoadedRule<>(entry.id(), entry.origin(), rule));
                            JsonObject json = RuleSnapshotAssembler.encode(encoder, rule);
                            if (json != null) { encoded.put(entry.id(), json); }
                        });
                budget.charge(1); processed++;
            }
            if (cursor < source.entries().size()) { return StepResult.yield(1, 1); }
            if (preparing == null) {
                // 交接后的容器不再由服务端线程修改；装配与分片不触碰 Codec 或世界，
                // 效果类型注册表已在启动时冻结，可跨线程只读，用于派生未显式声明的分类。
                Source capturedSource = source;
                List<LoadedRule<Rule>> readyRules = List.copyOf(decoded);
                Map<ResourceLocation, JsonObject> readyJson = Map.copyOf(encoded);
                var effectTypes = reloadContext.loadContext().effectTypes();
                int capturedVersion = version;
                preparing = store.submit("snapshot-assemble", () -> {
                    String json = RuleSnapshotAssembler.assembleEncoded(capturedSource.index(), readyRules, effectTypes,
                            capturedVersion, capturedVersion, readyJson, capturedSource.issues().issues()).serialize();
                    if (!java.util.Arrays.equals(capturedSource.revision(), sessions.readDiskRevision(() -> !cancelled))) {
                        throw new IllegalStateException("快照准备期间覆盖层修订已变化");
                    }
                    if (RuleEditLimits.encodedLength(json) > RuleEditLimits.MAX_SNAPSHOT_BYTES) { return List.of(); }
                    if (RuleEditLimits.encodedLength(json) <= RuleEditLimits.MAX_DIRECT_PACKET_BYTES) {
                        return List.of(new RuleSnapshotPayload(sessionId, requestId, json));
                    }
                    return RuleSnapshotChunker.split(sessionId, requestId, json);
                });
            }
            if (!preparing.isDone()) { return StepResult.yield(1, 1); }
            complete(preparing.join());
            return StepResult.done(1);
        } catch (RuntimeException failure) {
            org.apache.logging.log4j.LogManager.getLogger().error("规则快照准备失败", failure);
            complete(List.of()); return StepResult.done(1);
        }
    }

    private void complete(List<CustomPacketPayload> packets) {
        if (finished) { return; }
        finished = true;
        completion.accept(packets);
    }

    @Override public void onCancelled(CancelReason reason) { invalidate(); }
    @Override public void onFailed(@Nullable Throwable failure) { invalidate(); }

    // 服务层在原句柄取消后仍负责交付完成；等待真实文件调用退出再释放快照槽位。
    void pollCompletion() {
        if (finished) { return; }
        if (store.stalled()) { invalidate(); }
        if (cancelled && (reading == null || reading.isDone()) && (preparing == null || preparing.isDone())) {
            complete(List.of());
        }
    }
}
