package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.runtime.RuleRuntimeHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 世界探针共用的规则、实体与文件夹具；清理只触达本轮登记对象。 */
final class BackendProbeFixtures {
    // 探针专用命名空间，避免与正式规则命名空间混用
    static final String PROBE_NAMESPACE = Constants.ID_NAMESPACE + "_probe";
    final List<ItemEntity> sources = new ArrayList<>();
    final List<ItemEntity> outputs = new ArrayList<>();
    private final List<Path> files = new ArrayList<>();
    private final MinecraftServer server;
    private final ServerLevel level;

    BackendProbeFixtures(MinecraftServer server) { this.server = server; level = server.overworld(); }

    Path ruleRoot() throws IOException {
        Path root = java.util.Objects.requireNonNull(RuleRuntimeHost.diagnosticsContext().editContext().overlayRoot()).resolve("rules");
        Files.createDirectories(root);
        return root;
    }

    void writeRule(String id, String output, int count, String... triggers) throws IOException {
        writeFile(ruleRoot().resolve("probe-" + id + ".json"), rule(id, output, count, triggers).toString());
    }

    void writeFile(Path path, String content) throws IOException {
        if (!files.contains(path)) { files.add(path); }
        Files.writeString(path, content);
    }

    static JsonObject rule(String id, String output, int count, String... triggers) {
        JsonObject rule = JsonParser.parseString("{\"id\":\"" + PROBE_NAMESPACE + ":" + id + "\",\"enabled\":true,\"priority\":1000,"
                + "\"trigger_after_seconds\":0,\"source\":{\"items\":[\"minecraft:paper\"],\"exclude\":[]},"
                + "\"effects\":[{\"type\":\"" + Constants.ID_NAMESPACE + ":spawn_entity\",\"item\":\"minecraft:" + output
                + "\",\"count\":" + count + ",\"delay_ticks\":0,\"chance\":1,\"variant\":\"item\"}]}").getAsJsonObject();
        JsonArray array = new JsonArray();
        for (String trigger : triggers) { array.add(trigger); }
        rule.add("triggers", array);
        return rule;
    }

    static RuleEdit edit(String id, int count) {
        return new RuleEdit(ResourceLocation.fromNamespaceAndPath(PROBE_NAMESPACE, id), RuleEdit.Action.UPSERT,
                rule(id, "prismarine_shard", count, "natural"));
    }

    static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> bytes = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                bytes.put(root.relativize(path).toString(), Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return bytes;
    }

    ItemEntity spawn(Item type, double offset) {
        ItemEntity item = new ItemEntity(level, 3 + offset, 100, 8, new ItemStack(type, 1));
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        sources.add(item);
        if (!level.addFreshEntity(item)) { throw new IllegalStateException("实体加入失败"); }
        return item;
    }

    void reload() {
        var outcome = DebugProbePerformance.operation("RELOAD", () -> RuleRuntimeHost.diagnosticsContext().reloadRules(server));
        if (outcome == null || outcome.result() == null || outcome.result().hasErrors()) {
            throw new IllegalStateException("正式规则重载失败: " + outcome);
        }
    }

    long count(Item type) {
        return outputs.stream().filter(Entity::isAlive).filter(item -> item.getItem().is(type))
                .mapToLong(item -> item.getItem().getCount()).sum();
    }

    long liveSources() { return sources.stream().filter(Entity::isAlive).mapToLong(item -> item.getItem().getCount()).sum(); }

    void close() {
        sources.forEach(Entity::discard);
        outputs.forEach(Entity::discard);
        try {
            for (Path file : files) { Files.deleteIfExists(file); }
            if (!files.isEmpty()) { reload(); }
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
