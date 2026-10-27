package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecoder;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceRead;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleLoader;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceIndex;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.api.DevelopmentDiagnostics;
import com.mojang.serialization.Codec;
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

    /**
     * 一次快照准备的交付结果：已装配的列表 JSON，以及本次装配使用的来源分层索引。
     * 来源索引随快照一起交付，供编辑页按需生成来源视图，避免为单条规则再读一次磁盘。
     *
     * @param json 已装配内容；失败或取消时为空
     * @param index   本次装配的来源分层索引；未完成读取或已取消时为 null
     */
    record Delivery(@Nullable String json, @Nullable RuleSourceIndex index) { }
    private final RuleAuthoringContext context;
    private final MinecraftServer server;
    private final EditAdmission admission;
    private final RuleOverlayStore store;
    private final RuleSaveReloadContext reloadContext;
    private final String sessionId;
    private final Consumer<Delivery> completion;
    private CompletableFuture<Source> reading;
    private CompletableFuture<String> preparing;
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

    RuleSnapshotTask(RuleAuthoringContext context, MinecraftServer server,
                     RuleOverlayStore store, EditAdmission admission, String sessionId, Consumer<Delivery> completion) {
        this.context = context; this.server = server; this.admission = admission;
        this.store = store;
        this.sessionId = sessionId; this.completion = completion;
        profileStartedNanos = DevelopmentDiagnostics.start();
        profileStartTick = server.getTickCount();
        reloadContext = java.util.Objects.requireNonNull(context.saveReloadContext());
        reading = store.submit("snapshot-read", () -> {
            // 后台读盘与合并的耗时单独计量，不与服务端线程的解码步骤混算
            long readStarted = DevelopmentDiagnostics.start();
            try {
                RuleOverlayStore.Sources sources = store.readSources(reloadContext, () -> !cancelled);
                RuleSourceRead read = sources.read();
                return new Source(sources.revision(), read.entries(), read.index(), read.issues());
            } finally {
                profileReadNanos = DevelopmentDiagnostics.micros(readStarted);
            }
        });
    }

    void schedule() { context.scheduleTask(SNAPSHOT_REALM, this, 1); }
    void invalidate() { cancelled = true; }
    @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }
    @Override public String tracingName() { return "rule-snapshot"; }

    // 步骤包装：统计本快照实际被调度的步数与服务端线程耗时，原语义完全交给 advance
    @Override public StepResult step(ServerTickBudget budget) {
        long started = DevelopmentDiagnostics.start();
        if (started != 0L) { profileSteps++; }
        try {
            return advance(budget);
        } finally {
            profileStepNanos += DevelopmentDiagnostics.micros(started);
        }
    }

    private StepResult advance(ServerTickBudget budget) {
        if (finished) { return StepResult.done(1); }
        if (cancelled || !admission.sessionMatches(sessionId)
                || context.saveReloadContext() != reloadContext) {
            invalidate();
            pollCompletion();
            return finished ? StepResult.done(1) : StepResult.yield(1, 1);
        }
        try {
            if (source == null) {
                if (!reading.isDone()) { return StepResult.yield(1, 1); }
                source = reading.join(); reading = null;
                store.acceptRevision(source.revision(), false);
                version = store.version();
            }
            if (decoder == null && cursor < source.entries().size() && !budget.exhausted()) {
                // Codec 构建同样受公共 tick 预算约束，同一快照内复用，不为每条规则重建整棵 Codec。
                var load = reloadContext.loadContext();
                decoder = RuleCodecs.decoder(load.registryAccess(), load.effectTypes(), load.conditionTypes());
                encoder = RuleCodecs.codec(load.effectTypes(), load.conditionTypes());
            }
            int processed = 0;
            while (cursor < source.entries().size() && processed < RuleAuthoringLimits.SNAPSHOT_ENTRIES_PER_STEP
                    && !budget.exhausted()) {
                RawRuleEntry entry = source.entries().get(cursor++);
                var load = reloadContext.loadContext();
                RuleLoader.decodeEntry(entry, decoder, source.issues())
                        .filter(rule -> RuleValidation.validate(rule, load.effectTypes(), load.conditionTypes(),
                                        load.runtimeLimits(), source.issues(), entry.origin().display())
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
                // 交接后的容器不再由服务端线程修改；装配不触碰 Codec 或世界，
                // 效果类型注册表已在启动时冻结，可跨线程只读，用于派生未显式声明的分类。
                Source capturedSource = source;
                List<LoadedRule<Rule>> readyRules = List.copyOf(decoded);
                Map<ResourceLocation, JsonObject> readyJson = Map.copyOf(encoded);
                var effectTypes = reloadContext.loadContext().effectTypes();
                // 启用三值按本服状态层计算；这里只读快照，不改运行时对象
                var enableResolver = reloadContext.loadContext().enableResolver();
                int capturedVersion = version;
                preparing = store.submit("snapshot-assemble", () -> {
                    // 后台装配的耗时单独计量，与解码步骤分开对比
                    long assembleStarted = DevelopmentDiagnostics.start();
                    try {
                        String json = RuleSnapshotAssembler.assembleEncoded(capturedSource.index(), readyRules, effectTypes,
                                capturedVersion, capturedVersion, readyJson, capturedSource.issues().issues(),
                                enableResolver).serialize();
                        if (!java.util.Arrays.equals(capturedSource.revision(), store.fingerprint(() -> !cancelled))) {
                            throw new IllegalStateException("快照准备期间覆盖层修订已变化");
                        }
                        return json;
                    } finally {
                        profileAssembleNanos = DevelopmentDiagnostics.micros(assembleStarted);
                    }
                });
            }
            if (!preparing.isDone()) { return StepResult.yield(1, 1); }
            complete(preparing.join());
            return StepResult.done(1);
        } catch (RuntimeException failure) {
            profileFailed = true;
            org.apache.logging.log4j.LogManager.getLogger().error("规则快照准备失败", failure);
            complete(null); return StepResult.done(1);
        }
    }

    private void complete(@Nullable String json) {
        if (finished) { return; }
        finished = true;
        if (DevelopmentDiagnostics.enabled()) { logProfile(json); }
        Source ready = source;
        completion.accept(new Delivery(json, json == null || ready == null ? null : ready.index()));
    }

    // 首载分段观测：一条日志给出准备阶段的步数、跨 tick 数、线程耗时与内容字节数
    private void logProfile(@Nullable String json) {
        JsonObject data = new JsonObject();
        data.addProperty("entries", source == null ? 0 : source.entries().size());
        data.addProperty("decoded", decoded.size());
        data.addProperty("encoded", encoded.size());
        data.addProperty("steps", profileSteps);
        data.addProperty("ticks", Math.max(0L, server.getTickCount() - profileStartTick));
        data.addProperty("step_ms", profileStepNanos / 1000.0);
        data.addProperty("read_ms", profileReadNanos / 1000.0);
        data.addProperty("assemble_ms", profileAssembleNanos / 1000.0);

        data.addProperty("bytes", json == null ? 0 : json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        data.addProperty("failed", profileFailed);
        // 非失败但没有内容：准备被取消，按中止记录
        data.addProperty("aborted", !profileFailed && json == null);
        data.addProperty("total_ms", DevelopmentDiagnostics.micros(profileStartedNanos) / 1000.0);
        DevelopmentDiagnostics.event(DevelopmentDiagnostics.SCENE_FIRST_LOAD, "SERVER_PREP", data);
    }

    @Override public void onCancelled(CancelReason reason) { invalidate(); }
    @Override public void onFailed(@Nullable Throwable failure) { invalidate(); }

    // 服务层在原句柄取消后仍负责交付完成；等待真实文件调用退出再释放快照槽位。
    void pollCompletion() {
        if (finished) { return; }
        if (store.stalled()) { invalidate(); }
        if (cancelled && (reading == null || reading.isDone()) && (preparing == null || preparing.isDone())) {
            complete(null);
        }
    }
}
