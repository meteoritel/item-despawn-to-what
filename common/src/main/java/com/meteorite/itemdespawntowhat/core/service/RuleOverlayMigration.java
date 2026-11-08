package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.JsonFileMigration;
import com.meteorite.itemdespawntowhat.core.api.RuleDecodeBudget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.scheduler.CancelReason;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepResult;
import com.meteorite.itemdespawntowhat.core.load.RulePaths;
import com.meteorite.itemdespawntowhat.core.model.RuleDocuments;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;
import com.meteorite.itemdespawntowhat.core.type.BuiltinTypeRegistries;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;

/** 初始化迁移续接：原始位置遍历与提交归文件队列，完整校验归公共 tick 预算。 */
public final class RuleOverlayMigration implements ServerTask {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private record Candidate(Path file, byte[] original, JsonElement document, List<JsonObject> entries) { }
    private record Scan(List<Candidate> files, IssueCollector issues) { }
    private final RuleOverlayStore store;
    private final RuleLoadContext context;
    private final BuiltinTypeRegistries types;
    private final Path root;
    private final IssueCollector issues = new IssueCollector();
    private final CompletableFuture<Scan> reading;
    private CompletableFuture<IssueCollector> writing;
    private List<Candidate> files;
    private int fileIndex;
    private int entryIndex;
    private boolean valid = true;
    private volatile boolean active = true;
    private boolean finished;
    private @Nullable RuntimeException failure;

    /**
     * 从唯一文件队列捕获本次原始文件，尚未发布规则和编辑状态。
     * @param store 当前覆盖层文件所有者
     * @param context 注册表、来源目录与当前服务端
     * @param types 已冻结的类型声明
     */
    public RuleOverlayMigration(RuleOverlayStore store, RuleLoadContext context, BuiltinTypeRegistries types) {
        this.store = store;
        this.context = context;
        this.types = types;
        root = Objects.requireNonNull(context.overlayRoot()).toAbsolutePath().normalize();
        reading = store.submit("migration-read", this::scanFiles);
    }

    /** @return 初始化迁移是否已收尾 */
    public boolean finished() { return finished; }
    /** @return 无法安全发布时的失败，普通条目错误保存在问题汇总中 */
    public @Nullable RuntimeException failure() { return failure; }
    /** @return 迁移收尾后的逐文件问题汇总 */
    public IssueCollector issues() { return issues; }
    @Override public ServerTaskKind kind() { return ServerTaskKind.MAINTENANCE; }
    @Override public String tracingName() { return "rule-overlay-migration"; }

    @Override
    public StepResult step(ServerTickBudget budget) {
        if (files == null) {
            if (!reading.isDone()) { return StepResult.yield(1, 1); }
            Scan scan = reading.join();
            files = scan.files();
            scan.issues().issues().forEach(issues::add);
        }
        int processed = 0;
        long deadline = System.nanoTime() + RuleAuthoringLimits.SAVE_STEP_BUDGET_NANOS;
        while (fileIndex < files.size() && processed < RuleAuthoringLimits.SAVE_ENTRIES_PER_STEP
                && !budget.exhausted() && System.nanoTime() < deadline) {
            Candidate file = files.get(fileIndex);
            if (writing != null) {
                if (!writing.isDone()) { return StepResult.yield(1, 1); }
                writing.join().issues().forEach(issues::add);
                writing = null;
                nextFile();
            } else if (entryIndex < file.entries().size()) {
                validateEntry(file, entryIndex++);
            } else if (!valid) {
                nextFile();
            } else {
                writing = store.submit("migration-write", () -> commitFile(file));
            }
            processed++;
            budget.charge(1);
        }
        finished = fileIndex == files.size();
        return finished ? StepResult.done(1) : StepResult.yield(1, 1);
    }

    @Override public void onCancelled(CancelReason reason) { active = false; }
    @Override public void onFailed(@Nullable Throwable thrown) {
        active = false;
        failure = new IllegalStateException("覆盖层初始化迁移失败", thrown);
        finished = true;
    }

    private void nextFile() {
        fileIndex++;
        entryIndex = 0;
        valid = true;
    }

    private void validateEntry(Candidate file, int index) {
        JsonObject body = file.entries().get(index);
        ResourceLocation id = null;
        JsonElement declared = body.get(RuleFields.ID);
        if (declared != null && declared.isJsonPrimitive() && declared.getAsJsonPrimitive().isString()) {
            id = ResourceLocation.tryParse(declared.getAsString());
        } else if (declared == null && file.entries().size() == 1) {
            String relative = root.resolve(RulePaths.OVERLAY_RULES_DIRECTORY).relativize(file.file()).toString().replace('\\', '/');
            id = ResourceLocation.tryParse(context.overlayNamespace() + ":" + RulePaths.stripExtension(relative));
        }
        String path = file.document().isJsonArray() ? "[" + index + "]" : "";
        if (id == null) {
            issues.error("迁移保留原文件：规则 id 非法或缺失", file.file().toString(), path);
            valid = false;
            return;
        }
        IssueCollector local = new IssueCollector();
        RuleEdit prepared = RuleSubmissionValidator.prepareEdit(new RuleEdit(id, RuleEdit.Action.UPSERT, body),
                Objects.requireNonNull(context.server()), types, context.runtimeLimits(), local);
        local.issues().forEach(issue -> issues.add(new Issue(
                issue.severity(), issue.message(), file.file().toString(), RulePaths.joinFieldPath(path, issue.fieldPath()))));
        if (local.hasErrors()) { valid = false; }
        if (prepared != null) {
            JsonObject current = Objects.requireNonNull(prepared.rule());
            if (declared == null) { current.remove(RuleFields.ID); }
            body.entrySet().clear();
            current.entrySet().forEach(entry -> body.add(entry.getKey(), entry.getValue()));
        }
    }

    private Scan scanFiles() throws IOException {
        IssueCollector collected = new IssueCollector();
        List<Candidate> candidates = new ArrayList<>();
        Path directory = root.resolve(RulePaths.OVERLAY_RULES_DIRECTORY);
        JsonFileMigration.requireSafePath(directory);
        if (!Files.isDirectory(directory)) { return new Scan(List.of(), collected); }
        try (var walk = Files.walk(directory)) {
            for (Path file : walk.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                requireActive();
                try {
                    Candidate candidate = prepareFile(file, collected);
                    if (candidate != null) { candidates.add(candidate); }
                } catch (IOException unreadable) {
                    collected.error("迁移保留原文件：读取失败: " + unreadable, file.toString(), null);
                }
            }
        }
        return new Scan(List.copyOf(candidates), collected);
    }

    private @Nullable Candidate prepareFile(Path file, IssueCollector collected) throws IOException {
        JsonFileMigration.requireSafePath(file);
        byte[] original = Files.readAllBytes(file);
        var parsed = RuleDecodeBudget.parse(new String(original, StandardCharsets.UTF_8));
        if (!parsed.ok() || parsed.element() == null
                || (!parsed.element().isJsonObject() && !parsed.element().isJsonArray())) {
            collected.error("迁移保留原文件：" + (parsed.ok() ? "顶层必须为规则对象或数组" : parsed.failureReason()), file.toString(), null);
            return null;
        }
        JsonElement source = parsed.element();
        JsonElement current = source.deepCopy();
        List<JsonElement> entries = current.isJsonObject() ? List.of(current) : current.getAsJsonArray().asList();
        List<JsonObject> prepared = new ArrayList<>();
        boolean complete = true;
        for (int index = 0; index < entries.size(); index++) {
            requireActive();
            String path = current.isJsonArray() ? "[" + index + "]" : "";
            JsonElement entry = entries.get(index);
            if (!(entry instanceof JsonObject body)) {
                collected.error("迁移保留原文件：规则条目必须是对象", file.toString(), path);
                complete = false;
                continue;
            }
            var result = RuleDocuments.upgrade(body);
            if (result.error().isPresent()) {
                collected.error(result.error().orElseThrow().message(), file.toString(), path);
                complete = false;
                continue;
            }
            JsonObject document = result.result().orElseThrow().document();
            prepared.add(document);
            if (current.isJsonObject()) { current = document; }
            else { current.getAsJsonArray().set(index, document); }
        }
        return complete && !source.equals(current) ? new Candidate(file, original, current, prepared) : null;
    }

    private IssueCollector commitFile(Candidate file) throws IOException {
        requireActive();
        IssueCollector collected = new IssueCollector();
        byte[] current = (GSON.toJson(file.document()) + "\n").getBytes(StandardCharsets.UTF_8);
        Path relative = root.resolve(RulePaths.OVERLAY_RULES_DIRECTORY).relativize(file.file());
        try {
            Path backup = JsonFileMigration.commit(new JsonFileMigration.Replacement(file.file(), file.original(), current),
                    root.resolve(".migration-backups").resolve(relative).getParent());
            store.committed();
            LogManager.getLogger().info("覆盖层已升级为规则格式 {}: {}，备份: {}",
                    RuleDocuments.CURRENT_VERSION, file.file(), backup);
        } catch (JsonFileMigration.SourceChangedException changed) {
            collected.warn(changed.getMessage() + "；加载时重新读取磁盘", file.file().toString(), null);
        } catch (IOException failed) {
            if (!Arrays.equals(file.original(), Files.readAllBytes(file.file()))) {
                store.requireRecovery();
                throw new IOException("迁移后源文件状态无法确认，请检查备份: " + file.file(), failed);
            }
            collected.warn("规则已准备，磁盘迁移待下次启动重试: " + failed, file.file().toString(), null);
        }
        return collected;
    }

    private void requireActive() {
        if (!active) { throw new CompletionException(new IllegalStateException("初始化迁移已取消")); }
        RuleOverlayStore.progress();
    }
}
