package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.scheduler.RunnableTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveProgress;
import com.meteorite.itemdespawntowhat.core.runtime.ProbeLedgerAccess;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ScheduledTask;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.SchedulerConfig;
import com.meteorite.itemdespawntowhat.core.runtime.scheduler.ServerScheduler;
import com.meteorite.itemdespawntowhat.core.service.EditSessionManager;
import com.meteorite.itemdespawntowhat.core.service.RuleOverlayWriter;
import com.meteorite.itemdespawntowhat.core.state.DropState;
import com.meteorite.itemdespawntowhat.core.state.DropStateStore;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** 正式索引与原版实体入口探针；只在独立开发存档中创建少量实物。 */
final class BackendWorldProbe {
    private static boolean rejectReload;
    private static BackendWorldProbe active;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final String name;
    private final long start;
    private final JsonObject metrics = new JsonObject();
    private final DebugScenarioChecks checks = new DebugScenarioChecks(metrics);
    private final List<ItemEntity> sources = new ArrayList<>();
    private final List<ItemEntity> outputs = new ArrayList<>();
    private final List<Path> files = new ArrayList<>();
    private final List<ItemEntity> budgetItems = new ArrayList<>();
    private final List<ScheduledTask> budgetTasks = new ArrayList<>();
    private final int[] budgetDeliveries = {0, 0};
    private int budgetEarly;
    private boolean middle;

    BackendWorldProbe(MinecraftServer server, String name) throws IOException {
        this.server = server;
        level = server.overworld();
        this.name = name;
        start = level.getGameTime();
        active = this;
        switch (name) {
            case "loading" -> {
                var runtime = java.util.Objects.requireNonNull(RuleRuntimeHost.commandContext().runtime());
                check("development_datapack_base_loaded", runtime.index().ordered().stream()
                        .anyMatch(rule -> rule.id().toString().equals("idtw_probe:base") && rule.value().triggerAfterSeconds() == 300));
                // 实体先于新规则存在，成功产出还验证了正式回扫。
                spawn(Items.PAPER, 0);
                writeRule("base", "prismarine_shard", 2, "natural");
                Path broken = ruleRoot().resolve("probe-broken.json");
                Files.writeString(broken, "{broken"); files.add(broken);
                var outcome = RuleRuntimeHost.commandContext().reloadRules(server);
                check("reload_applied", outcome != null && outcome.isApplied());
                check("bad_file_reported", outcome != null && outcome.result() != null && outcome.result().hasErrors());
            }
            case "damage" -> {
                writeRule("damage", "prismarine_shard", 1, "fire", "lava", "cactus");
                reload();
                for (int i = 0; i < 3; i++) {
                    ItemEntity source = spawn(Items.PAPER, i * 2);
                    source.hurt(switch (i) { case 0 -> level.damageSources().inFire(); case 1 -> level.damageSources().lava(); default -> level.damageSources().cactus(); }, 100);
                    check("lethal_source_removed/" + i, !source.isAlive());
                }
                ItemEntity nonlethal = spawn(Items.PAPER, 6);
                nonlethal.hurt(level.damageSources().inFire(), 1);
                check("nonlethal_source_alive", nonlethal.isAlive());
                ItemEntity unrelated = spawn(Items.PAPER, 8);
                unrelated.hurt(level.damageSources().generic(), 100);
                check("unrelated_damage_removed", !unrelated.isAlive());
            }
            case "state" -> {
                writeRule("state", "prismarine_shard", 1, "natural"); reload();
                ItemEntity protectedItem = spawn(Items.STICK, 0);
                DropStateStore.set(protectedItem, new DropState(start + 20, 0, false, false));
                protectedItem.hurt(level.damageSources().cactus(), 100);
                check("temporary_damage_protection", protectedItem.isAlive());
                ItemEntity cooling = spawn(Items.PAPER, 3);
                DropStateStore.set(cooling, new DropState(0, start + 40, false, false));
                ItemEntity permanent = spawn(Items.PAPER, 6);
                DropStateStore.grantPermanentReturn(permanent);
                permanent.hurt(level.damageSources().lava(), 100);
                check("permanent_damage_protection", permanent.isAlive());
                // 普通同组件堆可被原版合并；永久标志不同的两堆保留分离。
                ItemEntity ordinary = spawn(Items.PAPER, 6);
                ordinary.setPos(permanent.position());
                DropStateStore.set(ordinary, new DropState(0, start + 1000, false, false));
                ItemEntity firstMerge = spawn(Items.COAL, 10);
                ItemEntity secondMerge = spawn(Items.COAL, 10);
                DropStateStore.set(firstMerge, new DropState(start + 60, start + 200, false, false));
                DropStateStore.set(secondMerge, new DropState(start + 80, start + 400, false, false));
                var saved = secondMerge.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                ItemEntity decoded = new ItemEntity(level, 0, 100, 0, new ItemStack(Items.COAL));
                decoded.load(saved);
                check("entity_nbt_state_round_trip", DropStateStore.get(secondMerge).equals(DropStateStore.get(decoded)));
            }
            case "save" -> save();
            case "budget" -> budget();
            case "restart" -> check("record_loaded", ProbeLedgerAccess.snapshot(server).get("exists").getAsBoolean());
            default -> throw new IllegalArgumentException(name);
        }
    }

    JsonObject tick() throws IOException {
        long elapsed = level.getGameTime() - start;
        if (!middle && elapsed >= 20) {
            middle = true;
            if (name.equals("state")) {
                checks.equal("cooldown_before_boundary/source_alive", 1, sources.get(1).isAlive() ? 1 : 0);
                sources.getFirst().hurt(level.damageSources().cactus(), 100);
                check("protection_at_boundary_expired", !sources.getFirst().isAlive());
            }
            if (name.equals("loading")) {
                // 新文件可加载，但发布前故障必须留下旧索引。
                writeRule("base", "diamond", 5, "natural");
                rejectReload = true;
                var rejected = RuleRuntimeHost.commandContext().reloadRules(server);
                check("reload_failure_rejected", rejected != null && !rejected.isApplied());
                check("reload_fault_reached", !rejectReload);
                spawn(Items.PAPER, 3);
            }
            if (name.equals("restart")) { reload(); reload(); }
        }
        if (!name.equals("save") && elapsed < 100) { return null; }
        switch (name) {
            case "loading" -> {
                checks.equal("live_output/prismarine_shard", 4, count(Items.PRISMARINE_SHARD));
                checks.equal("live_output/diamond", 0, count(Items.DIAMOND));
                checks.equal("remaining_sources", 0, liveSources());
            }
            case "damage" -> {
                checks.equal("live_output/three_triggers", 3, count(Items.PRISMARINE_SHARD));
                checks.equal("nonlethal_not_converted", 1, liveSources());
            }
            case "state" -> {
                checks.equal("cooldown_eventually_converted", 1, count(Items.PRISMARINE_SHARD));
                check("permanent_ban_survives", sources.get(2).isAlive());
                check("different_permanent_flags_do_not_merge", sources.get(2).isAlive() && sources.get(3).isAlive());
                checks.equal("both_separate_counts", 2, sources.get(2).getItem().getCount() + sources.get(3).getItem().getCount());
                var merged = sources.subList(4, 6).stream().filter(Entity::isAlive).toList();
                checks.equal("compatible_stacks_merged", 1, merged.size());
                if (merged.size() == 1) {
                    checks.equal("merge_quantity", 2, merged.getFirst().getItem().getCount());
                    checks.equal("merge_protection_max", start + 80, DropStateStore.get(merged.getFirst()).temporaryProtectionUntil());
                    checks.equal("merge_cooldown_max", start + 400, DropStateStore.get(merged.getFirst()).temporaryCooldownUntil());
                }
            }
            case "budget" -> {
                checks.equal("host_overworld_delivered", 20, budgetDeliveries[0]);
                checks.equal("host_nether_delivered", 20, budgetDeliveries[1]);
                checks.equal("host_no_early_output", 0, budgetEarly);
                checks.equal("host_live_items", 40, budgetItems.stream().filter(Entity::isAlive).mapToLong(item -> item.getItem().getCount()).sum());
                checks.equal("host_handles_done", 40, budgetTasks.stream().filter(task -> task.state() == ScheduledTask.State.DONE).count());
            }
            case "restart" -> {
                JsonObject ledger = ProbeLedgerAccess.snapshot(server);
                metrics.add("ledger", ledger);
                checks.equal("pending_delivery", 0, ledger.get("pending").getAsInt());
                checks.equal("delivered_returns", 7, ledger.get("delivered").getAsInt());
                checks.equal("source_conservation", ledger.get("initial").getAsInt(), ledger.get("consumed").getAsInt()
                        + ledger.get("delivered").getAsInt() + ledger.get("pending").getAsInt());
                long returned = 0, protectedCount = 0;
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof ItemEntity item && item.isAlive() && ProbeLedgerAccess.marked(item.getItem())) {
                        returned += item.getItem().getCount();
                        var state = DropStateStore.get(item);
                        if (state.permanentProtection() && state.permanentConversionBan()) { protectedCount += item.getItem().getCount(); }
                    }
                }
                checks.equal("live_returned_after_repeated_reload", 7, returned);
                checks.equal("permanent_return_state", 7, protectedCount);
            }
            default -> { }
        }
        JsonObject result = new JsonObject();
        metrics.addProperty("server_tick", server.getTickCount());
        metrics.addProperty("world_game_time", level.getGameTime());
        result.add("metrics", metrics);
        result.add("checks", checks.results());
        boolean passed = !checks.results().isEmpty();
        for (var check : checks.results()) { passed &= check.getAsJsonObject().get("pass").getAsBoolean(); }
        result.addProperty("verdict", passed ? "PASS" : "FAIL");
        return result;
    }

    private Path ruleRoot() throws IOException {
        Path root = java.util.Objects.requireNonNull(RuleRuntimeHost.commandContext().editContext().overlayRoot()).resolve("rules");
        Files.createDirectories(root);
        return root;
    }

    private void writeRule(String id, String output, int count, String... triggers) throws IOException {
        Path path = ruleRoot().resolve("probe-" + id + ".json");
        JsonObject rule = rule(id, output, count, triggers);
        Files.writeString(path, rule.toString());
        if (!files.contains(path)) { files.add(path); }
    }

    private static JsonObject rule(String id, String output, int count, String... triggers) {
        JsonObject rule = JsonParser.parseString("{\"id\":\"idtw_probe:" + id + "\",\"enabled\":true,\"priority\":1000,"
                + "\"trigger_after_seconds\":0,\"source\":{\"items\":[\"minecraft:" + "paper" + "\"],\"exclude\":[]},"
                + "\"effects\":[{\"type\":\"itemdespawntowhat:spawn_entity\",\"item\":\"minecraft:" + output
                + "\",\"count\":" + count + ",\"delay_ticks\":0,\"chance\":1,\"variant\":\"item\"}]}").getAsJsonObject();
        var array = new com.google.gson.JsonArray();
        for (String trigger : triggers) { array.add(trigger); }
        rule.add("triggers", array);
        return rule;
    }

    private ItemEntity spawn(Item type, double offset) {
        ItemEntity item = new ItemEntity(level, 3 + offset, 100, 8, new ItemStack(type, 1));
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        sources.add(item);
        if (!level.addFreshEntity(item)) { throw new IllegalStateException("实体加入失败"); }
        return item;
    }

    private void reload() {
        var outcome = RuleRuntimeHost.commandContext().reloadRules(server);
        if (outcome == null || outcome.result() == null || outcome.result().hasErrors()) {
            throw new IllegalStateException("正式规则重载失败: " + outcome);
        }
    }

    private long count(Item type) {
        long count = outputs.stream().filter(Entity::isAlive).filter(item -> item.getItem().is(type))
                .mapToLong(item -> item.getItem().getCount()).sum();
        metrics.addProperty("live/" + type, count);
        return count;
    }

    private long liveSources() { return sources.stream().filter(Entity::isAlive).mapToLong(item -> item.getItem().getCount()).sum(); }

    private void check(String key, boolean pass) { checks.equal(key, 1, pass ? 1 : 0); }

    private void save() throws IOException {
        Path root = Path.of("probe-writer");
        var writer = new RuleOverlayWriter(root, "idtw_probe");
        var initial = new RuleEditChangeSet(0, List.of(edit("a", 1), edit("b", 1)));
        var result = writer.apply(initial, new IssueCollector());
        checks.equal("initial_written", 2, result.writtenFiles());
        checks.equal("initial_conflicts", 0, result.conflicts().size());
        Map<String, String> before = snapshot(root);
        boolean[] fault = {false};
        var changed = new RuleEditChangeSet(0, List.of(edit("a", 2), edit("b", 2)));
        var failed = writer.apply(changed, new IssueCollector(), () -> true, () -> true, () -> true, progress -> {
            if (!fault[0] && progress.stage() == RuleSaveProgress.Stage.WRITING && progress.completed() == 1) {
                fault[0] = true; throw new IllegalStateException("probe_second_file_commit");
            }
        });
        check("second_commit_fault_reached", fault[0]);
        check("failure_reported", !failed.conflicts().isEmpty());
        check("rollback_succeeded", !failed.rollbackFailed());
        check("entire_directory_bytes_restored", before.equals(snapshot(root)));
        var success = writer.apply(changed, new IssueCollector());
        checks.equal("retry_written", 2, success.writtenFiles());
        checks.equal("retry_conflicts", 0, success.conflicts().size());
        check("retry_changed_files", !before.equals(snapshot(root)));
        for (String id : List.of("a", "b")) {
            var actual = JsonParser.parseString(Files.readString(root.resolve("rules/idtw_probe/" + id + ".json"))).getAsJsonObject();
            check("retry_file_content/" + id, actual.equals(rule(id, "prismarine_shard", 2, "natural")));
        }
        EditSessionManager sessions = new EditSessionManager(Path.of("probe-session"));
        var first = sessions.acquire(UUID.randomUUID(), "probe", 1);
        check("acquire", first.acquired());
        check("exclusive", !sessions.acquire(UUID.randomUUID(), "other", 1).acquired());
        String id = first.session().sessionId();
        check("confirm", sessions.confirm(id, 2));
        check("begin_apply", sessions.beginApply(id));
        sessions.release();
        checks.equal("release_deferred_while_applying", 1, sessions.activeSessionCount());
        sessions.finishApply(id, 3);
        checks.equal("release_after_apply", 0, sessions.activeSessionCount());
    }

    private static RuleEdit edit(String id, int count) {
        return new RuleEdit(ResourceLocation.fromNamespaceAndPath("idtw_probe", id), RuleEdit.Action.UPSERT,
                rule(id, "prismarine_shard", count, "natural"));
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> bytes = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                bytes.put(root.relativize(path).toString(), java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return bytes;
    }

    private void budget() {
        // 同一生产调度器、两个真实维度键、混合 lane；小预算放大跨 tick 行为。
        ServerScheduler scheduler = new ServerScheduler(new SchedulerConfig(1_000_000_000L, 12, 8, 1, 32, 4, 4, 4));
        List<ScheduledTask> handles = new ArrayList<>();
        int[] delivered = {0, 0};
        int[] early = {0};
        for (int realm = 0; realm < 2; realm++) {
            int index = realm;
            for (int i = 0; i < 20; i++) {
                handles.add(scheduler.scheduleAt(realm == 0 ? net.minecraft.world.level.Level.OVERWORLD : net.minecraft.world.level.Level.NETHER,
                        new RunnableTask(i % 2 == 0 ? ServerTaskKind.EFFECT : ServerTaskKind.CONDITION_CHECK, "probe_budget", () -> {
                            if (scheduler.currentTick() < 3) { early[0]++; }
                            delivered[index]++;
                        }), 3));
            }
        }
        scheduler.tick(1); scheduler.tick(2);
        checks.equal("before_due", 0, delivered[0] + delivered[1]);
        scheduler.tick(3);
        int first = delivered[0] + delivered[1];
        check("shared_tick_did_not_drain_all", first < 40);
        scheduler.tick(3);
        checks.equal("same_tick_does_not_reset_budget", first, delivered[0] + delivered[1]);
        int maxPerTick = first;
        for (long tick = 4; tick <= 80; tick++) {
            int before = delivered[0] + delivered[1];
            scheduler.tick(tick);
            maxPerTick = Math.max(maxPerTick, delivered[0] + delivered[1] - before);
        }
        metrics.addProperty("max_completed_per_tick", maxPerTick);
        check("all_realms_share_global_work_cap", maxPerTick <= 12);
        checks.equal("overworld_completed", 20, delivered[0]);
        checks.equal("nether_completed", 20, delivered[1]);
        checks.equal("no_early_execution", 0, early[0]);
        checks.equal("all_handles_done", 40, handles.stream().filter(handle -> handle.state() == ScheduledTask.State.DONE).count());
        var runtime = java.util.Objects.requireNonNull(RuleRuntimeHost.commandContext().runtime());
        for (int dimension = 0; dimension < 2; dimension++) {
            int index = dimension;
            ServerLevel target = java.util.Objects.requireNonNull(server.getLevel(dimension == 0
                    ? net.minecraft.world.level.Level.OVERWORLD : net.minecraft.world.level.Level.NETHER));
            target.setChunkForced(0, 0, true);
            target.getChunk(0, 0);
            for (int i = 0; i < 20; i++) {
                budgetTasks.add(runtime.scheduler().scheduleAt(target.dimension(), new RunnableTask(
                        i % 2 == 0 ? ServerTaskKind.EFFECT : ServerTaskKind.CONDITION_CHECK, "probe_host_budget", () -> {
                    if (level.getGameTime() < start + 5) { budgetEarly++; }
                    ItemEntity item = new ItemEntity(target, 8, 100, 8, new ItemStack(Items.BEDROCK));
                    item.setNoGravity(true); item.setDeltaMovement(Vec3.ZERO); item.setNeverPickUp();
                    if (!target.addFreshEntity(item)) { throw new IllegalStateException("预算任务实物加入失败"); }
                    budgetItems.add(item);
                    budgetDeliveries[index]++;
                }), start + 5));
            }
        }
    }

    static void reloadApplying() {
        if (rejectReload) { rejectReload = false; throw new IllegalStateException("probe_before_index_publish"); }
    }

    static void prepareRestart(MinecraftServer server) { ProbeLedgerAccess.prepare(server); }

    static void outputAdded(ItemEntity source, Entity output) {
        if (active != null && active.sources.contains(source) && output instanceof ItemEntity item) {
            active.outputs.add(item);
        }
    }

    void close() {
        active = null;
        if (name.equals("restart")) { return; }
        budgetItems.forEach(Entity::discard);
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item) { item.discard(); }
        }
        try {
            for (Path file : files) { Files.deleteIfExists(file); }
            if (!files.isEmpty()) { reload(); }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
