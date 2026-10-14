package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
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
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.StepResult;
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
    private final RuleSaveReloadContext reloadContext;
    private final String sessionId;
    private final String requestId;
    private final Consumer<List<CustomPacketPayload>> completion;
    private CompletableFuture<Source> reading;
    private CompletableFuture<List<CustomPacketPayload>> preparing;
    private Source source;
    private int version;
    private int cursor;
    private boolean cancelled;
    private boolean finished;
    private final List<LoadedRule<Rule>> decoded = new ArrayList<>();
    private final Map<ResourceLocation, JsonObject> encoded = new LinkedHashMap<>();

    RuleSnapshotTask(RuleEditServerContext context, MinecraftServer server, EditSessionManager sessions,
                     String sessionId, String requestId, Consumer<List<CustomPacketPayload>> completion) {
        this.context = context; this.server = server; this.sessions = sessions;
        this.sessionId = sessionId; this.requestId = requestId; this.completion = completion;
        reloadContext = java.util.Objects.requireNonNull(context.saveReloadContext());
        reading = RuleSaveTask.background(() -> {
            byte[] revision = sessions.readDiskRevision();
            IssueCollector issues = new IssueCollector();
            reloadContext.baseIssues().forEach(issues::add);
            var load = reloadContext.loadContext();
            List<RawRuleEntry> entries = new ArrayList<>(reloadContext.baseRules());
            entries.addAll(OverlayRuleReader.read(load.overlayRoot(), load.overlayNamespace(), issues));
            if (!java.util.Arrays.equals(revision, sessions.readDiskRevision())) {
                throw new IllegalStateException("准备快照时覆盖层修订已变化");
            }
            return new Source(revision, RuleMerger.merge(entries, issues), RuleSourceIndex.build(entries), issues);
        });
    }

    void schedule() { context.saveScheduler().schedule(SNAPSHOT_REALM, this, 1); }
    void invalidate() { cancelled = true; }
    @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }
    @Override public String tracingName() { return "rule-snapshot"; }

    @Override public StepResult step(ServerTickBudget budget) {
        if (finished) { return StepResult.done(1); }
        if (cancelled || !sessions.owns(sessionId) || context.saveReloadContext() != reloadContext) {
            complete(List.of()); return StepResult.done(1);
        }
        try {
            if (source == null) {
                if (!reading.isDone()) { return StepResult.yield(1, 1); }
                source = reading.join(); reading = null;
                sessions.acceptDiskRevision(source.revision(), false);
                version = sessions.version();
                RuleSaveTask.background(() -> { sessions.persistRevision(version); return null; });
            }
            long deadline = System.nanoTime() + RuleEditLimits.SAVE_STEP_BUDGET_NANOS;
            int processed = 0;
            while (cursor < source.entries().size() && processed < RuleEditLimits.SAVE_ENTRIES_PER_STEP
                    && !budget.exhausted() && System.nanoTime() < deadline) {
                RawRuleEntry entry = source.entries().get(cursor++);
                var load = reloadContext.loadContext();
                RuleLoader.decodeEntry(entry, RuleCodecs.decoder(load.registryAccess(), load.effectTypes(), load.conditionTypes()), source.issues())
                        .filter(rule -> RuleValidation.validate(rule, load.effectTypes(), load.conditionTypes(), source.issues(), entry.origin().display())
                                && RuleReferenceValidator.validate(rule, server, source.issues(), entry.origin().display()))
                        .ifPresent(rule -> {
                            decoded.add(new LoadedRule<>(entry.id(), entry.origin(), rule));
                            JsonObject json = RuleSnapshotAssembler.encodeRule(rule, load.effectTypes(), load.conditionTypes());
                            if (json != null) { encoded.put(entry.id(), json); }
                        });
                budget.charge(1); processed++;
            }
            if (cursor < source.entries().size()) { return StepResult.yield(1, 1); }
            if (preparing == null) {
                // 交接后的容器不再由服务端线程修改；装配与分片不触碰 Codec、注册表或世界。
                preparing = RuleSaveTask.background(() -> {
                    String json = RuleSnapshotAssembler.assembleEncoded(source.index(), decoded, version, version, encoded, source.issues().issues()).serialize();
                    if (!java.util.Arrays.equals(source.revision(), sessions.readDiskRevision())) {
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
    @Override public void onFailed(@Nullable Throwable failure) { complete(List.of()); }
}
