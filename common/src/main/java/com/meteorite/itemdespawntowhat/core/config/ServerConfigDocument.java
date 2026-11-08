package com.meteorite.itemdespawntowhat.core.config;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.JsonDocumentMigrations;
import com.meteorite.itemdespawntowhat.core.api.JsonFileMigration;
import com.meteorite.itemdespawntowhat.core.api.RuleDecodeBudget;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;

/** 配置文件 Adapter；版本元数据独立于运行参数，自动写回保留原始字段与动态缺省。 */
final class ServerConfigDocument {
    private static final int BASELINE_VERSION = 1;
    static final int CURRENT_VERSION = 1;
    static final ConfigField<Integer> VERSION = ConfigField.integer("config_version", CURRENT_VERSION, BASELINE_VERSION, CURRENT_VERSION);
    private static final JsonDocumentMigrations MIGRATIONS = new JsonDocumentMigrations(
            VERSION.name(), BASELINE_VERSION, CURRENT_VERSION, List.of());

    private ServerConfigDocument() { }

    static ServerConfig load(Path file, IssueCollector issues) {
        if (file == null) { return ServerConfig.DEFAULT; }
        if (!Files.exists(file)) {
            try {
                save(ServerConfig.DEFAULT, file);
            } catch (FileAlreadyExistsException createdExternally) {
                return load(file, issues);
            } catch (IOException failure) {
                issues.warn("默认配置创建失败: " + failure, file.toString(), null);
            }
            return ServerConfig.DEFAULT;
        }
        byte[] original;
        try {
            JsonFileMigration.requireSafePath(file);
            original = Files.readAllBytes(file);
        } catch (IOException failure) {
            issues.error("server.json 读取失败: " + failure, file.toString(), null);
            return ServerConfig.DEFAULT;
        }
        var parsed = RuleDecodeBudget.parse(new String(original, StandardCharsets.UTF_8));
        if (!parsed.ok() || !(parsed.element() instanceof JsonObject source)) {
            issues.error("server.json 解析失败: " + (parsed.ok() ? "顶层必须为对象" : parsed.failureReason()),
                    file.toString(), null);
            return ServerConfig.DEFAULT;
        }
        var migration = MIGRATIONS.upgrade(source);
        if (migration.error().isPresent()) {
            String reason = migration.error().orElseThrow().message();
            issues.error(reason, file.toString(), VERSION.name());
            throw new IllegalStateException("server.json 版本迁移失败: " + reason);
        }
        var upgraded = migration.result().orElseThrow();
        JsonObject current = upgraded.document();
        JsonObject parameters = current.deepCopy();
        parameters.remove(VERSION.name());
        ConfigField.warnUnknown(parameters, ServerConfig.FIELDS, issues, file.toString(), "");
        DataResult<ServerConfig> decoded = ServerConfig.CODEC.parse(JsonOps.INSTANCE, parameters);
        if (decoded.error().isPresent()) {
            String reason = decoded.error().orElseThrow().message();
            issues.error("server.json 解析失败: " + reason, file.toString(), null);
            if (upgraded.sourceVersion() != upgraded.targetVersion()) {
                throw new IllegalStateException("迁移后配置校验失败: " + reason);
            }
            return ServerConfig.DEFAULT;
        }
        if (!source.equals(current)) {
            try {
                Path backup = JsonFileMigration.commit(new JsonFileMigration.Replacement(file, original,
                        (current + "\n").getBytes(StandardCharsets.UTF_8)), file.toAbsolutePath().getParent().resolve(".migration-backups"));
                LogManager.getLogger().info("配置格式 {} -> {}，升级前备份: {}",
                        upgraded.sourceVersion(), upgraded.targetVersion(), backup);
            } catch (JsonFileMigration.SourceChangedException changed) {
                throw new IllegalStateException("配置迁移期间源文件发生变化，请重启后重新读取", changed);
            } catch (IOException failure) {
                try {
                    if (!Arrays.equals(original, Files.readAllBytes(file))) {
                        throw new IllegalStateException("配置迁移提交状态发生变化，请检查备份并重启", failure);
                    }
                } catch (IOException unreadable) {
                    throw new IllegalStateException("配置迁移后无法确认源文件状态，请检查备份并重启", unreadable);
                }
                issues.warn("配置已在内存升级，磁盘写回待重试: " + failure, file.toString(), VERSION.name());
            }
        }
        return decoded.result().orElseThrow();
    }

    static JsonObject encode(ServerConfig config) {
        JsonObject json = ServerConfig.CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow().getAsJsonObject();
        json.addProperty(VERSION.name(), CURRENT_VERSION);
        return json;
    }

    static void save(ServerConfig config, Path file) throws IOException {
        JsonFileMigration.requireSafePath(file);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, encode(config) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
