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
import com.meteorite.itemdespawntowhat.core.debug.DebugProfile;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleSnapshotChunkPayload;
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
    // 首载分段观测：请求到达至完成的起点、步骤数、服务端线程与后台阶段的累计耗时
    private final long profileStartedNanos;
    private final int profileStartTick;
    private long profileStepNanos;
    private long profileReadNanos;
    private long profileAssembleNanos;
    private int profileSteps;
    private boolean profileFailed;
    private final List<LoadedRule<Rule>> decoded = new ArrayList<>();
    private final Map<ResourceLocation, JsonObject> encoded = new LinkedHashMap<>();

    RuleSnapshotTask(RuleEditServerContext context, MinecraftServer server, EditSessionManager sessions,
                     String sessionId, String requestId, Consumer<List<CustomPacketPayload>> completion) {
        this.context = context; this.server = server; this.sessions = sessions;
        store = sessions.store();
        this.sessionId = sessionId; this.requestId = requestId; this.completion = completion;
        profileStartedNanos = DebugProfile.start();
        profileStartTick = server.getTickCount();
        reloadContext = java.util.Objects.requireNonNull(context.saveReloadContext());
        reading = store.submit("snapshot-read", () -> {
            // 后台读盘与合并的耗时单独计量，不与服务端线程的解码步骤混算
            long readStarted = DebugProfile.start();
            try {
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
            } finally {
                profileReadNanos = DebugProfile.micros(readStarted);
            }
        });
    }

    void schedule() { context.scheduleTask(SNAPSHOT_REALM, this, 1); }
    void invalidate() { cancelled = true; }
    @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }
    @Override public String tracingName() { return "rule-snapshot"; }

    // 步骤包装：统计本快照实际被调度的步数与服务端线程耗时，原语义完全交给 advance
    @Override public StepResult step(ServerTickBudget budget) {
        long started = DebugProfile.start();
        if (started != 0L) { profileSteps++; }
        try {
            return advance(budget);
        } finally {
            profileStepNanos += DebugProfile.micros(started);
        }
    }

    private StepResult advance(ServerTickBudget budget) {
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
                // 交接后的容器不再由服务端线程修改；装配与分片不触碰 Codec、注册表或世界。
                Source capturedSource = source;
                List<LoadedRule<Rule>> readyRules = List.copyOf(decoded);
                Map<ResourceLocation, JsonObject> readyJson = Map.copyOf(encoded);
                int capturedVersion = version;
                preparing = store.submit("snapshot-assemble", () -> {
                    // 后台装配与切分的耗时单独计量，与解码步骤分开对比
                    long assembleStarted = DebugProfile.start();
                    try {
                        String json = RuleSnapshotAssembler.assembleEncoded(capturedSource.index(), readyRules, capturedVersion, capturedVersion, readyJson, capturedSource.issues().issues()).serialize();
                        if (!java.util.Arrays.equals(capturedSource.revision(), sessions.readDiskRevision(() -> !cancelled))) {
                            throw new IllegalStateException("快照准备期间覆盖层修订已变化");
                        }
                        if (RuleEditLimits.encodedLength(json) > RuleEditLimits.MAX_SNAPSHOT_BYTES) { return List.of(); }
                        if (RuleEditLimits.encodedLength(json) <= RuleEditLimits.MAX_DIRECT_PACKET_BYTES) {
                            return List.of(new RuleSnapshotPayload(sessionId, requestId, json));
                        }
                        return RuleSnapshotChunker.split(sessionId, requestId, json);
                    } finally {
                        profileAssembleNanos = DebugProfile.micros(assembleStarted);
                    }
                });
            }
            if (!preparing.isDone()) { return StepResult.yield(1, 1); }
            complete(preparing.join());
            return StepResult.done(1);
        } catch (RuntimeException failure) {
            profileFailed = true;
            org.apache.logging.log4j.LogManager.getLogger().error("规则快照准备失败", failure);
            complete(List.of()); return StepResult.done(1);
        }
    }

    private void complete(List<CustomPacketPayload> packets) {
        if (finished) { return; }
        finished = true;
        if (DebugProfile.enabled()) { logProfile(packets); }
        completion.accept(packets);
    }

    // 首载分段观测：一条日志给出准备阶段的步数、跨 tick 数、线程耗时与下发包数
    private void logProfile(List<CustomPacketPayload> packets) {
        JsonObject data = new JsonObject();
        data.addProperty("entries", source == null ? 0 : source.entries().size());
        data.addProperty("decoded", decoded.size());
        data.addProperty("encoded", encoded.size());
        data.addProperty("steps", profileSteps);
        data.addProperty("ticks", Math.max(0L, server.getTickCount() - profileStartTick));
        data.addProperty("step_ms", profileStepNanos / 1000.0);
        data.addProperty("read_ms", profileReadNanos / 1000.0);
        data.addProperty("assemble_ms", profileAssembleNanos / 1000.0);
        data.addProperty("packets", packets.size());
        data.addProperty("bytes", packetsBytes(packets));
        data.addProperty("failed", profileFailed);
        // 非失败但没有包：请求被取消或超过上限，两种都按中止记录
        data.addProperty("aborted", !profileFailed && packets.isEmpty());
        data.addProperty("total_ms", DebugProfile.micros(profileStartedNanos) / 1000.0);
        DebugProfile.event(DebugProfile.SCENE_FIRST_LOAD, "SERVER_PREP", data);
    }

    // 下发字节按 UTF-8 计，与分片阈值口径一致
    private static long packetsBytes(List<CustomPacketPayload> packets) {
        long bytes = 0L;
        for (CustomPacketPayload payload : packets) {
            if (payload instanceof RuleSnapshotPayload direct) {
                bytes += RuleEditLimits.encodedLength(direct.snapshotJson());
            } else if (payload instanceof RuleSnapshotChunkPayload chunk) {
                bytes += RuleEditLimits.encodedLength(chunk.chunk());
            }
        }
        return bytes;
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
