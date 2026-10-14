package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.load.LoadedRule;
import com.meteorite.itemdespawntowhat.core.load.OverlayRuleReader;
import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.load.RuleLoader;
import com.meteorite.itemdespawntowhat.core.load.RuleMerger;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.meteorite.itemdespawntowhat.core.model.RuleValidation;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.StepOutcome;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.StepResult;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 保存续接任务：后台只处理文件与独占 JSON，世界、注册表和第三方类型校验均在公共 tick 预算内执行。 */
final class RuleSaveTask implements ServerTask {
    private static final Object SAVE_REALM = new Object();
    private static final int MAX_BACKGROUND_JOBS = 8;
    // 全进程单队列使停服取消后的回滚与下一存档的保存串行，不并发写入同一覆盖层。
    private static final ThreadPoolExecutor FILE_WORKER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_BACKGROUND_JOBS), job -> {
        Thread thread = new Thread(job, "IDTW.RuleSaveIO");
        thread.setDaemon(true);
        return thread;
    });

    private enum Phase { REQUEST, VALIDATE, WRITE, READ, DECODE, INDEX, APPLY, DONE }
    record Outcome(RuleSaveStatus status, String detail, IssueCollector issues, int edited, int files,
                   int expectedVersion, boolean written) { }
    private record Request(RuleEditChangeSet changes, byte[] revision) { }
    private record Written(RuleOverlayWriter.ApplyResult result, IssueCollector issues, byte[] revision) { }
    private record RawReload(List<RawRuleEntry> entries, IssueCollector issues) { }

    private final EditSessionManager sessions;
    private final RuleEditServerContext context;
    private final MinecraftServer server;
    private final RuleSaveReloadContext reloadContext;
    private final Consumer<Outcome> completion;
    private final String sessionId;
    private final java.util.UUID owner;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final IssueCollector issues = new IssueCollector();
    private Phase phase = Phase.REQUEST;
    private CompletableFuture<Request> request;
    private CompletableFuture<Written> write;
    private CompletableFuture<RawReload> raw;
    private CompletableFuture<RuleIndex> prepared;
    private RuleEditChangeSet changes;
    private byte[] diskRevision;
    private int cursor;
    private int writtenFiles;
    private volatile boolean written;
    private boolean revisionAccepted;
    private RawReload reload;
    private final List<LoadedRule<Rule>> decoded = new ArrayList<>();
    private ServerTask apply;

    RuleSaveTask(EditSessionManager sessions, RuleEditServerContext context, MinecraftServer server,
                 String sessionId, java.util.UUID owner, java.util.function.Supplier<String> text, Consumer<Outcome> completion) {
        this.sessions = sessions;
        this.context = context;
        this.server = server;
        this.reloadContext = context.saveReloadContext();
        this.completion = completion;
        this.sessionId = sessionId;
        this.owner = owner;
        request = background(() -> {
            String json = java.util.Objects.requireNonNullElse(text.get(), "");
            if (RuleEditLimits.encodedLength(json) > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
                throw new IllegalArgumentException("change set too large");
            }
            var parsed = RuleEditChangeSet.parse(json);
            RuleEditChangeSet value = parsed.result().orElseThrow(() -> new IllegalArgumentException(
                    parsed.error().map(com.mojang.serialization.DataResult.Error::message).orElse("变更集解析失败")));
            return new Request(value, value.isEmpty() ? null : sessions.readDiskRevision());
        });
    }

    void schedule() { context.saveScheduler().schedule(SAVE_REALM, this, 1); }
    void invalidate() { active.set(false); }
    boolean finished() { return phase == Phase.DONE; }

    @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }
    @Override public String tracingName() { return "rule-save"; }

    @Override public StepResult step(ServerTickBudget budget) {
        if (phase == Phase.DONE) { return StepResult.done(1); }
        var player = server.getPlayerList().getPlayer(owner);
        if (!sessions.owns(sessionId) || !sessions.isOwner(owner)
                || !com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler.canEdit(player)) {
            invalidate();
        }
        try {
            // 已发布索引后必须完成旧任务清理与回扫；会话失效只撤销尚未提交的文件工作。
            if ((!active.get() || context.saveReloadContext() != reloadContext) && phase != Phase.APPLY) {
                invalidate();
                if (write != null && !write.isDone()) { return StepResult.yield(1, 1); }
                if (write != null && write.isDone() && !revisionAccepted) { acceptWrite(write.join()); }
                RuleSaveStatus status = written ? RuleSaveStatus.SAVED_NOT_RELOADED
                        : !sessions.owns(sessionId) || player == null ? RuleSaveStatus.SESSION_EXPIRED
                        : !com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerHandler.canEdit(player)
                        ? RuleSaveStatus.NO_PERMISSION : RuleSaveStatus.INVALID_REQUEST;
                finish(status, "保存请求已取消");
                return StepResult.done(1);
            }
            long deadline = System.nanoTime() + RuleEditLimits.SAVE_STEP_BUDGET_NANOS;
            int processed = 0;
            while (processed < RuleEditLimits.SAVE_ENTRIES_PER_STEP && !budget.exhausted()
                    && System.nanoTime() < deadline && phase != Phase.DONE) {
                if (!advance(budget)) { break; }
                processed++;
                budget.charge(1);
            }
        } catch (RuntimeException failure) {
            if (apply != null) { apply.onFailed(failure); }
            if (written && !revisionAccepted) { sessions.acceptDiskRevision(null, true); revisionAccepted = true; }
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            org.apache.logging.log4j.LogManager.getLogger().error("规则保存步骤失败：phase={}", phase, cause);
            finish(written ? RuleSaveStatus.SAVED_NOT_RELOADED
                    : cause instanceof RejectedExecutionException ? RuleSaveStatus.UNAVAILABLE
                    : phase == Phase.REQUEST && cause instanceof IllegalArgumentException
                    ? RuleSaveStatus.INVALID_REQUEST : RuleSaveStatus.WRITE_FAILED, cause.toString());
        }
        return phase == Phase.DONE ? StepResult.done(1) : StepResult.yield(1, 1);
    }

    private boolean advance(ServerTickBudget budget) {
        switch (phase) {
            case REQUEST -> {
                if (!request.isDone()) { return false; }
                Request value = request.join();
                changes = value.changes(); diskRevision = value.revision(); request = null;
                if (changes.isEmpty()) { finish(RuleSaveStatus.NO_CHANGES, ""); return true; }
                sessions.acceptDiskRevision(diskRevision, false);
                int version = sessions.version();
                background(() -> { sessions.persistRevision(version); return null; });
                if (changes.expectedVersion() != version) { finish(RuleSaveStatus.VERSION_CONFLICT, ""); }
                else if (reloadContext == null || context.typeRegistries() == null) { finish(RuleSaveStatus.UNAVAILABLE, ""); }
                else { phase = Phase.VALIDATE; }
            }
            case VALIDATE -> {
                if (cursor < changes.edits().size()) {
                    IssueCollector local = new IssueCollector();
                    RuleSubmissionValidator.validateEdit(changes.edits().get(cursor++), server, context.typeRegistries(), local);
                    appendIssues(local);
                    if (local.hasErrors()) { finish(RuleSaveStatus.VALIDATION_FAILED, local.format()); }
                } else {
                    int version = sessions.version();
                    write = background(() -> {
                        IssueCollector local = new IssueCollector();
                        var result = new RuleOverlayWriter(java.util.Objects.requireNonNull(reloadContext.loadContext().overlayRoot()),
                                reloadContext.loadContext().overlayNamespace()).apply(changes, local, active::get,
                                () -> Arrays.equals(diskRevision, readRevision()));
                        written = result.writtenFiles() > 0;
                        if (written) { sessions.persistRevision(version + 1); }
                        byte[] revision = sessions.readDiskRevision();
                        return new Written(result, local, revision);
                    });
                    phase = Phase.WRITE;
                }
            }
            case WRITE -> {
                if (!write.isDone()) { return false; }
                Written result = write.join(); acceptWrite(result); write = null;
                if (!result.result().conflicts().isEmpty() || result.issues().hasErrors()) {
                    finish(RuleSaveStatus.WRITE_FAILED, result.issues().format());
                } else if (!written) { finish(RuleSaveStatus.NO_CHANGES, ""); }
                else {
                    raw = background(() -> {
                        IssueCollector local = new IssueCollector();
                        reloadContext.baseIssues().forEach(local::add);
                        List<RawRuleEntry> entries = new ArrayList<>(reloadContext.baseRules());
                        entries.addAll(OverlayRuleReader.read(reloadContext.loadContext().overlayRoot(),
                                reloadContext.loadContext().overlayNamespace(), local));
                        if (!Arrays.equals(diskRevision, sessions.readDiskRevision())) {
                            throw new IllegalStateException("准备保存重载时覆盖层修订已变化");
                        }
                        return new RawReload(RuleMerger.merge(entries, local), local);
                    });
                    phase = Phase.READ;
                }
            }
            case READ -> {
                if (!raw.isDone()) { return false; }
                reload = raw.join(); appendIssues(reload.issues()); raw = null;
                cursor = 0; phase = Phase.DECODE;
            }
            case DECODE -> {
                if (cursor < reload.entries().size()) {
                    RawRuleEntry entry = reload.entries().get(cursor++);
                    IssueCollector local = new IssueCollector();
                    var load = reloadContext.loadContext();
                    RuleLoader.decodeEntry(entry, RuleCodecs.decoder(load.registryAccess(), load.effectTypes(), load.conditionTypes()), local)
                            .filter(rule -> RuleValidation.validate(rule, load.effectTypes(), load.conditionTypes(), local, entry.origin().display())
                                    && RuleReferenceValidator.validate(rule, server, local, entry.origin().display()))
                            .ifPresent(rule -> decoded.add(new LoadedRule<>(entry.id(), entry.origin(), rule)));
                    appendIssues(local);
                } else {
                    // 纯模型索引构建没有世界访问，后台使用的模型集合不再由服务端线程修改。
                    prepared = background(() -> {
                        RuleIndex index = RuleIndex.build(decoded, new IssueCollector());
                        if (!Arrays.equals(diskRevision, sessions.readDiskRevision())) {
                            throw new IllegalStateException("索引准备期间覆盖层修订已变化");
                        }
                        return index;
                    });
                    phase = Phase.INDEX;
                }
            }
            case INDEX -> {
                if (!prepared.isDone()) { return false; }
                apply = context.applySavedIndex(server, prepared.join()); prepared = null;
                phase = Phase.APPLY;
            }
            case APPLY -> {
                if (apply.step(budget).outcome() == StepOutcome.DONE) {
                    finish(context.saveReloadContext() == reloadContext ? RuleSaveStatus.SUCCESS : RuleSaveStatus.SAVED_NOT_RELOADED, "");
                }
            }
            case DONE -> { return false; }
        }
        return true;
    }

    private byte[] readRevision() {
        try { return sessions.readDiskRevision(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private void acceptWrite(Written result) {
        appendIssues(result.issues());
        writtenFiles = result.result().writtenFiles(); written = writtenFiles > 0;
        if (written && !revisionAccepted) { sessions.acceptDiskRevision(result.revision(), true); revisionAccepted = true; }
        diskRevision = result.revision();
    }

    private void appendIssues(IssueCollector local) {
        // 回执本身有问题条数上限；保留有界样本，判定始终基于未经裁剪的 local。
        for (var issue : local.issues()) {
            if (issues.issues().size() >= RuleEditLimits.MAX_ISSUES_PER_RESULT) { break; }
            issues.add(issue);
        }
    }

    private void finish(RuleSaveStatus status, String detail) {
        if (phase == Phase.DONE) { return; }
        phase = Phase.DONE;
        completion.accept(new Outcome(status, detail, issues, changes == null ? 0 : changes.edits().size(),
                writtenFiles, changes == null ? -1 : changes.expectedVersion(), written));
    }

    @Override public void onCancelled(CancelReason reason) {
        invalidate();
        if (apply != null) { apply.onCancelled(reason); }
    }

    @Override public void onFailed(@Nullable Throwable failure) {
        invalidate();
        if (apply != null) { apply.onFailed(failure); }
        if (written && !revisionAccepted) { sessions.acceptDiskRevision(null, true); revisionAccepted = true; }
        finish(written ? RuleSaveStatus.SAVED_NOT_RELOADED : RuleSaveStatus.WRITE_FAILED,
                failure == null ? "FAILED" : failure.toString());
    }

    static <T> CompletableFuture<T> background(Callable<T> action) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try { return action.call(); }
                catch (Exception failure) { throw new CompletionException(failure); }
            }, FILE_WORKER);
        } catch (RejectedExecutionException busy) {
            // 只让请求失败，不把文件/JSON 工作退回主线程执行，也不扩大后台队列。
            return CompletableFuture.failedFuture(busy);
        }
    }

    static void awaitFileWork() {
        // 停服阶段等待既有磁盘工作退出/回滚，避免同进程新存档启动时读取半批替换的目录。
        CompletableFuture<Void> drained = new CompletableFuture<>();
        FILE_WORKER.prestartCoreThread();
        try {
            FILE_WORKER.getQueue().put(() -> drained.complete(null));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待保存文件队列结束时被中断", interrupted);
        }
        drained.join();
    }
}
