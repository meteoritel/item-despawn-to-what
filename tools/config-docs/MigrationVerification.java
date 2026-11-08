package com.meteorite.itemdespawntowhat.core.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.JsonDocumentMigrations;
import com.meteorite.itemdespawntowhat.core.api.JsonFileMigration;
import com.meteorite.itemdespawntowhat.core.model.RuleDocuments;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** 离线验证真实迁移 Interface 与临时目录提交，不进入模组发布产物。 */
public final class MigrationVerification {
    private MigrationVerification() { }

    /**
     * 执行版本、配置语义和文件恢复验证；不启动游戏或探针。
     * @param args 无参数
     * @throws IOException 临时目录操作失败时
     */
    public static void main(String[] args) throws IOException {
        verifyVersions();
        Path directory = Files.createTempDirectory("idtw-migrations-");
        try {
            verifyConfiguration(directory);
            verifyFileCommit(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
            }
        }
        System.out.println("迁移验证通过：连续版本、严格输入、原文保持、配置缺省、备份与外部修改。");
    }

    private static void verifyVersions() {
        JsonDocumentMigrations migrations = new JsonDocumentMigrations("version", 1, 3, List.of(
                json -> {
                    json.add("renamed", json.remove("old"));
                    return DataResult.success(json);
                },
                json -> {
                    json.addProperty("result", json.get("renamed").getAsInt() * 2);
                    return DataResult.success(json);
                }));
        JsonObject source = object("{\"old\":7,\"extension\":{\"keep\":true}}");
        JsonObject before = source.deepCopy();
        var result = migrations.upgrade(source).getOrThrow();
        require(source.equals(before), "转换修改了输入");
        require(result.sourceVersion() == 1 && result.targetVersion() == 3, "缺省版本起点漂移");
        require(result.document().get("result").getAsInt() == 14, "连续转换顺序错误");
        require(result.document().get("extension").equals(source.get("extension")), "无关字段丢失");
        require(migrations.upgrade(result.document()).getOrThrow().document().equals(result.document()), "转换重入改变结果");
        for (String value : List.of("\"1\"", "1.5", "true", "null", "2147483648", "0", "4")) {
            require(migrations.upgrade(object("{\"version\":" + value + "}")).result().isEmpty(), "非法版本被接受: " + value);
        }
        JsonDocumentMigrations broken = new JsonDocumentMigrations("version", 1, 2,
                List.of(json -> DataResult.error(() -> "字段冲突", json)));
        require(broken.upgrade(source).result().isEmpty(), "部分转换结果被当作成功");
        require(RuleDocuments.upgrade(new JsonObject()).getOrThrow().document().get("schema_version").getAsInt() == 1,
                "2.0.0 规则基线错误");
        require(RuleDocuments.upgrade(object("{\"schema_version\":2}")).result().isEmpty(), "超前规则版本被接受");
    }

    private static void verifyConfiguration(Path directory) throws IOException {
        Path file = directory.resolve("server.json");
        String legacy = "{\"max_checks_per_tick\":37,\"debug_logging\":false,\"extension\":{\"keep\":true}}";
        Files.writeString(file, legacy);
        IssueCollector issues = new IssueCollector();
        ServerConfig config = ServerConfig.loadOrCreate(file, issues);
        require(!issues.hasErrors() && config.effectiveMaxWorkUnitsPerTick() == 37, "动态工作量缺省改变");
        JsonObject current = object(Files.readString(file));
        require(current.get("config_version").getAsInt() == 1, "配置缺少显式版本");
        current.remove("config_version");
        require(current.equals(object(legacy)), "配置显式默认或未知字段被改写");
        byte[] first = Files.readAllBytes(file);
        long backups;
        try (var paths = Files.list(directory.resolve(".migration-backups"))) { backups = paths.count(); }
        ServerConfig.loadOrCreate(file, new IssueCollector());
        require(Arrays.equals(first, Files.readAllBytes(file)), "当前配置被重复写回");
        try (var paths = Files.list(directory.resolve(".migration-backups"))) {
            require(paths.count() == backups, "重复读取生成了备份");
        }
        Files.writeString(file, "{\"config_version\":2}");
        try {
            ServerConfig.loadOrCreate(file, new IssueCollector());
            throw new IllegalStateException("超前配置版本静默回退默认");
        } catch (IllegalStateException rejected) {
            require(rejected.getMessage().contains("版本迁移失败"), "配置版本失败原因丢失");
        }
        Path missing = directory.resolve("new/server.json");
        ServerConfig.loadOrCreate(missing, new IssueCollector());
        require(object(Files.readString(missing)).get("config_version").getAsInt() == 1, "新配置缺少版本");
    }

    private static void verifyFileCommit(Path directory) throws IOException {
        Path file = directory.resolve("rules.json");
        byte[] original = "[ {\"id\":\"idtw:a\"}, {\"id\":\"idtw:a\"} ]\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "[]\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        Path backup = JsonFileMigration.commit(new JsonFileMigration.Replacement(file, original, current), directory.resolve("backups"));
        require(Arrays.equals(original, Files.readAllBytes(backup)), "备份未保持原始字节");
        require(Arrays.equals(current, Files.readAllBytes(file)), "原子提交内容错误");
        Files.writeString(file, "external");
        try {
            JsonFileMigration.commit(new JsonFileMigration.Replacement(file, current, original), directory.resolve("backups"));
            throw new IllegalStateException("外部修改被覆盖");
        } catch (JsonFileMigration.SourceChangedException expected) {
            require(Files.readString(file).equals("external"), "外部内容被改写");
        }
    }

    private static JsonObject object(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void require(boolean condition, String reason) {
        if (!condition) { throw new IllegalStateException(reason); }
    }
}
