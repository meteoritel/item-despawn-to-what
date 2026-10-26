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
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveProgress;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditLimits;
import com.meteorite.itemdespawntowhat.core.network.transport.RuleEditServerContext;
import com.meteorite.itemdespawntowhat.core.runtime.RuleIndex;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepOutcome;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 保存续接任务：后台只处理文件与独占 JSON，世界、注册表和第三方类型校验均在公共 tick 预算内执行。 */
final class RuleSaveTask implements ServerTask {
    private static final Object SAVE_REALM = new Object();
    private enum CommitState { OPEN, CANCELLED, COMMITTED }
    private enum Phase { REQUEST, VALIDATE, WRITE, READ, DECODE, INDEX, APPLY, DONE }
    record Outcome(RuleSaveStatus status, String detail, IssueCollector issues, int edited, int files,
                   int expectedVersion, boolean written) { }
    private record Request(RuleEditChangeSet changes, byte[] revision) { }
    private record Written(RuleOverlayWriter.ApplyResult result, IssueCollector issues, byte[] revision) { }
    private record RawReload(List<RawRuleEntry> entries, IssueCollector issues) { }

    private final EditSessionManager sessions;
    private final EditAdmission admission;
    private final RuleOverlayStore store;
    private final RuleEditServerContext context;
    private final MinecraftServer server;
    private final RuleSaveReloadContext reloadContext;
    private final Consumer<Outcome> completion;
    private final String sessionId;
    private final java.util.UUID owner;
    private final AtomicBoolean active = new AtomicBoolean(true);
    // 取消与最终提交只通过 CAS 竞争；文件与回滚始终由单个后台工作独占。
    private final AtomicReference<CommitState> commitState = new AtomicReference<>(CommitState.OPEN);
    private final AtomicReference<RuleSaveProgress> fileProgress = new AtomicReference<>(
            new RuleSaveProgress(RuleSaveProgress.Stage.PREPARING_FILES, 0, 0, "", true));
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
    private boolean detached;
    private @Nullable Throwable pendingFailure;
    private RawReload reload;
    private final List<LoadedRule<Rule>> decoded = new ArrayList<>();
    private ServerTask apply;

    RuleSaveTask(EditSessionManager sessions, EditAdmission admission, RuleEditServerContext context, MinecraftServer server,
                 String sessionId, java.util.UUID owner, java.util.function.Supplier<String> text, Consumer<Outcome> completion) {
        this.sessions = sessions;
        this.admission = admission;
        this.store = sessions.store();
        this.context = context;
        this.server = server;
        this.reloadContext = context.saveReloadContext();
        this.completion = completion;
        this.sessionId = sessionId;
        this.owner = owner;
        request = store.submit("save-request", () -> {
            String json = java.util.Objects.requireNonNullElse(text.get(), "");
            if (RuleEditLimits.encodedLength(json) > RuleEditLimits.MAX_CHANGE_SET_BYTES) {
                throw new IllegalArgumentException("change set too large");
            }
            var parsed = RuleEditChangeSet.parse(json);
            RuleEditChangeSet value = parsed.result().orElseThrow(() -> new IllegalArgumentException(
                    parsed.error().map(com.mojang.serialization.DataResult.Error::message).orElse("变更集解析失败")));
            return new Request(value, value.isEmpty() ? null : sessions.readDiskRevision(active::get));
        });
    }

    void schedule() { context.scheduleTask(SAVE_REALM, this, 1); }
    void invalidate() { active.set(false); commitState.compareAndSet(CommitState.OPEN, CommitState.CANCELLED); }
    void cancel() {
        if (!commitState.compareAndSet(CommitState.OPEN, CommitState.CANCELLED)) { return; }
        active.set(false);
    }

    RuleSaveProgress progress() {
        boolean cancellable = active.get() && commitState.get() == CommitState.OPEN && phase != Phase.DONE;
        if (phase == Phase.WRITE) {
            RuleSaveProgress value = fileProgress.get();
            RuleSaveProgress.Stage stage = !active.get() && value.stage() != RuleSaveProgress.Stage.ROLLING_BACK
                    ? RuleSaveProgress.Stage.CANCELLING : value.stage();
            return new RuleSaveProgress(stage, value.completed(), value.total(), value.current(), cancellable);
        }
        if (!active.get()) { return new RuleSaveProgress(RuleSaveProgress.Stage.CANCELLING, 0, 0, "", false); }
        RuleSaveProgress.Stage stage = switch (phase) {
            case REQUEST -> RuleSaveProgress.Stage.PREPARING;
            case VALIDATE -> RuleSaveProgress.Stage.VALIDATING;
            case READ -> RuleSaveProgress.Stage.READING;
            case DECODE -> RuleSaveProgress.Stage.DECODING;
            case INDEX -> RuleSaveProgress.Stage.INDEXING;
            default -> RuleSaveProgress.Stage.APPLYING;
        };
        int total = phase == Phase.VALIDATE ? changes.edits().size() : phase == Phase.DECODE ? reload.entries().size() : 0;
        String current = total == 0 ? "" : phase == Phase.VALIDATE
                ? changes.edits().get(Math.min(cursor, total - 1)).id().toString()
                : java.util.Objects.toString(reload.entries().get(Math.min(cursor, total - 1)).id(), "");
        return new RuleSaveProgress(stage, cursor, total, current, cancellable);
    }

    private boolean backgroundPending() {
        return request != null && !request.isDone() || write != null && !write.isDone()
                || raw != null && !raw.isDone() || prepared != null && !prepared.isDone();
    }
    boolean finished() { return phase == Phase.DONE; }

    @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }
    @Override public String tracingName() { return "rule-save"; }

    @Override public StepResult step(ServerTickBudget budget) {
        if (phase == Phase.DONE) { return StepResult.done(1); }
        var player = server.getPlayerList().getPlayer(owner);
        // 本步准入拒绝：中止时据此决定终态
        EditAdmission.Refusal refusal = EditAdmission.refusal(admission.admitMember(player, sessionId));
        if (refusal != null) {
            invalidate();
        }
        try {
            // 已发布索引后必须完成旧任务清理与回扫；会话失效只撤销尚未提交的文件工作。
            if ((!active.get() || context.saveReloadContext() != reloadContext) && phase != Phase.APPLY) {
                invalidate();
                if (backgroundPending()) { return StepResult.yield(1, 1); }
                if (write != null && write.isDone() && !revisionAccepted) { acceptWrite(write.join()); }
                if (write != null && write.join().result().rollbackFailed()) {
                    finish(RuleSaveStatus.WRITE_FAILED, "取消保存时部分文件回滚失败，请检查日志与 .bak");
                    return StepResult.done(1);
                }
                if (pendingFailure != null) {
                    finish(written ? RuleSaveStatus.SAVED_NOT_RELOADED : RuleSaveStatus.WRITE_FAILED, pendingFailure.toString());
                    return StepResult.done(1);
                }
                RuleSaveStatus status = written ? RuleSaveStatus.SAVED_NOT_RELOADED
                        : refusal != null ? refusal.status() : RuleSaveStatus.CANCELLED;
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
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            org.apache.logging.log4j.LogManager.getLogger().error("规则保存步骤失败：phase={}", phase, cause);
            finish(written || phase == Phase.APPLY ? RuleSaveStatus.SAVED_NOT_RELOADED
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
                if (changes.hasContentEdits()) {
                    // 内容动作才推进内容修订号并参与草稿冲突；纯启停批次不碰这两项
                    sessions.acceptDiskRevision(diskRevision, false);
                    if (changes.expectedVersion() != sessions.version()) {
                        finish(RuleSaveStatus.VERSION_CONFLICT, ""); return true;
                    }
                }
                if (reloadContext == null || context.typeRegistries() == null) { finish(RuleSaveStatus.UNAVAILABLE, ""); }
                else { phase = Phase.VALIDATE; }
            }
            case VALIDATE -> {
                if (cursor < changes.edits().size()) {
                    IssueCollector local = new IssueCollector();
                    RuleSubmissionValidator.validateEdit(changes.edits().get(cursor++), server,
                            context.typeRegistries(), reloadContext.loadContext().runtimeLimits(), local);
                    appendIssues(local);
                    if (local.hasErrors()) { finish(RuleSaveStatus.VALIDATION_FAILED, local.format()); }
                } else if (!changes.hasContentEdits()) {
                    // 纯启停批次：写状态后按保存链路的轻量路径重建索引并发布，回扫在 APPLY 步骤推进；
                    // 索引用这份任务已捕捉的数据包基底重建，不重读数据包。
                    RuleEnableApplyService.Outcome outcome = RuleEnableApplyService.apply(server, context,
                            changes.enableEdits());
                    if (outcome.changed() == 0) { finish(RuleSaveStatus.NO_CHANGES, ""); }
                    else if (outcome.rescan() == null) { finish(RuleSaveStatus.UNAVAILABLE, ""); }
                    else { apply = outcome.rescan(); phase = Phase.APPLY; }
                } else {
                    RuleEditChangeSet submitted = changes;
                    byte[] expectedRevision = diskRevision.clone();
                    write = store.submit("save-write", () -> {
                        IssueCollector local = new IssueCollector();
                        var result = new RuleOverlayWriter(java.util.Objects.requireNonNull(reloadContext.loadContext().overlayRoot()),
                                reloadContext.loadContext().overlayNamespace()).apply(submitted, local, active::get,
                                () -> Arrays.equals(expectedRevision, readRevision()),
                                () -> commitState.compareAndSet(CommitState.OPEN, CommitState.COMMITTED), value -> {
                                    RuleOverlayStore.progress();
                                    fileProgress.set(value);
                                });
                        written = result.writtenFiles() > 0;
                        if (result.rollbackFailed()) { store.requireRecovery(); }
                        // 提交先登记修订；后续读取失败不能漏计一次已确认的提交。
                        if (written) { store.committed(); }
                        byte[] revision = sessions.readDiskRevision();
                        if (written) { store.acceptRevision(revision, false); }
                        return new Written(result, local, revision);
                    });
                    phase = Phase.WRITE;
                }
            }
            case WRITE -> {
                if (!write.isDone()) { return false; }
                Written result = write.join(); acceptWrite(result); write = null;
                if (commitState.get() == CommitState.CANCELLED && !result.result().rollbackFailed()) {
                    finish(RuleSaveStatus.CANCELLED, "");
                } else if (!result.result().conflicts().isEmpty() || result.issues().hasErrors()) {
                    finish(RuleSaveStatus.WRITE_FAILED, result.issues().format());
                } else {
                    // 内容已原子提交（written=false 表示内容本身没有实际变化），紧接着写同批的启用状态：
                    // 两者在同一提交阶段生效，随后统一重建索引回扫。
                    int stateChanges = RuleEnableApplyService.commitState(server, changes.enableEdits());
                    if (!written && stateChanges == 0) { finish(RuleSaveStatus.NO_CHANGES, ""); return true; }
                    byte[] committedRevision = diskRevision.clone();
                    raw = store.submit("save-reload-read", () -> {
                        IssueCollector local = new IssueCollector();
                        reloadContext.baseIssues().forEach(local::add);
                        List<RawRuleEntry> entries = new ArrayList<>(reloadContext.baseRules());
                        entries.addAll(OverlayRuleReader.read(reloadContext.loadContext().overlayRoot(),
                                reloadContext.loadContext().overlayNamespace(), local));
                        if (!Arrays.equals(committedRevision, sessions.readDiskRevision(active::get))) {
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
                            .filter(rule -> RuleValidation.validate(rule, load.effectTypes(), load.conditionTypes(),
                                            load.runtimeLimits(), local, entry.origin().display())
                                    && RuleReferenceValidator.validate(rule, server, local, entry.origin().display()))
                            .ifPresent(rule -> decoded.add(new LoadedRule<>(entry.id(), entry.origin(), rule)));
                    appendIssues(local);
                } else {
                    // 纯模型索引构建没有世界访问，后台使用的模型集合不再由服务端线程修改。
                    // 先注入「本服覆盖 ?? 默认启用」：否则刚保存的规则会按 JSON 默认值生效，与加载链路分叉
                    List<LoadedRule<Rule>> readyRules = List.copyOf(RuleLoadingService.applyEnableState(
                            reloadContext.loadContext(), decoded));
                    byte[] indexedRevision = diskRevision.clone();
                    prepared = store.submit("save-index", () -> {
                        RuleIndex index = RuleIndex.build(readyRules, new IssueCollector());
                        if (!Arrays.equals(indexedRevision, sessions.readDiskRevision(active::get))) {
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
        try { return sessions.readDiskRevision(active::get); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private void acceptWrite(Written result) {
        if (commitState.get() != CommitState.CANCELLED || result.result().rollbackFailed()) { appendIssues(result.issues()); }
        writtenFiles = result.result().writtenFiles(); written = writtenFiles > 0;
        if (written && !revisionAccepted) { sessions.acceptDiskRevision(result.revision(), false); revisionAccepted = true; }
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
        detached = true;
        if (apply != null) { apply.onCancelled(reason); apply = null; }
    }

    @Override public void onFailed(@Nullable Throwable failure) {
        invalidate();
        detached = true;
        pendingFailure = failure == null ? new IllegalStateException("保存调度步骤失败") : failure;
        if (apply != null) { apply.onFailed(failure); apply = null; }
    }

    // 原调度句柄被摘除后，服务层仍在主线程接纳真实结果并完成业务终态。
    void pollCompletion() {
        if (finished()) { return; }
        if (store.stalled()) {
            pendingFailure = new IllegalStateException("覆盖层后台工作长时间无进展，等待文件调用退出");
            invalidate();
        }
        if (!detached || backgroundPending()) { return; }
        try {
            if (write != null) { acceptWrite(write.join()); }
            boolean rollbackFailed = write != null && write.join().result().rollbackFailed();
            finish(written || apply != null ? RuleSaveStatus.SAVED_NOT_RELOADED
                            : pendingFailure == null && !rollbackFailed ? RuleSaveStatus.CANCELLED : RuleSaveStatus.WRITE_FAILED,
                    pendingFailure == null ? "保存调度已取消，请检查回滚结果" : pendingFailure.toString());
        } catch (RuntimeException failure) {
            finish(written ? RuleSaveStatus.SAVED_NOT_RELOADED : RuleSaveStatus.WRITE_FAILED, failure.toString());
        }
    }
}
