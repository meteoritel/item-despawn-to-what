package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.Constants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/***
 * 草稿文件仓库：客户端本地目录 {@code config/itemdespawntowhat/client/editor-drafts/}，
 * 一个编辑目标一份 JSON 文件。
 * <p>与游戏目录的关系：Minecraft 的工作目录就是游戏根目录，因此这里用相对路径
 * {@code config/...} 定位，不依赖任何加载器 API，common 侧可直接使用。
 * <p>该目录是纯客户端状态，绝不写入服务端目录，也不写进服务端 overlay 目录：
 * 编辑锁与覆盖层是服务端的事，草稿只是本机未提交的编辑中间态。
 */
public final class DraftStore {

    // 相对游戏根目录的草稿目录
    public static final String DIRECTORY = "config/itemdespawntowhat/client/editor-drafts";

    // 落盘格式：带缩进便于排查，不转义 HTML 字符
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path directory;

    public DraftStore() {
        this(Paths.get(DIRECTORY));
    }

    // 测试或自定义目录用
    public DraftStore(Path directory) {
        this.directory = directory;
    }

    // 草稿目录
    public Path directory() {
        return this.directory;
    }

    // 写出（覆盖）某个目标的草稿文件：先写同目录临时文件再原子替换，避免崩在半截 JSON 上
    public boolean save(PersistedDraft draft) {
        Path target = resolve(draft.targetId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(this.directory);
            Files.writeString(temporary, GSON.toJson(draft.toJson()) + "\n", StandardCharsets.UTF_8);
            moveInto(temporary, target);
            return true;
        } catch (IOException exception) {
            Constants.LOG.warn("写出编辑草稿失败：{}", draft.targetId(), exception);
            return false;
        }
    }

    // 原子替换，文件系统不支持时退化为普通替换
    private static void moveInto(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // 读取全部草稿；目录不存在或个别文件损坏都不算失败
    public List<PersistedDraft> loadAll() {
        if (!Files.isDirectory(this.directory)) {
            return List.of();
        }
        List<PersistedDraft> drafts = new ArrayList<>();
        try (Stream<Path> files = Files.list(this.directory)) {
            List<Path> jsonFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path file : jsonFiles) {
                PersistedDraft draft = read(file);
                if (draft != null) {
                    drafts.add(draft);
                    migrate(file, draft);
                }
            }
        } catch (IOException exception) {
            Constants.LOG.warn("读取编辑草稿目录失败：{}", this.directory, exception);
            return List.of();
        }
        return drafts;
    }

    // 删除某个目标的草稿文件（不存在也算成功）
    public void delete(String targetId) {
        try {
            Files.deleteIfExists(resolve(targetId));
        } catch (IOException exception) {
            Constants.LOG.warn("删除编辑草稿失败：{}", targetId, exception);
        }
    }

    // 清空草稿目录中本模组写出的文件
    public void clear() {
        if (!Files.isDirectory(this.directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(this.directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".json") || name.endsWith(".json.tmp")) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException exception) {
            Constants.LOG.warn("清空编辑草稿目录失败：{}", this.directory, exception);
        }
    }

    // 旧命名（无短哈希）迁移：按新文件名重写后删掉旧文件；内容里有 target_id，迁移不丢数据
    private void migrate(Path file, PersistedDraft draft) {
        if (file.getFileName().toString().equals(fileName(draft.targetId()))) {
            return;
        }
        if (!save(draft)) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            Constants.LOG.warn("清理编辑草稿旧文件失败：{}", file, exception);
        }
    }

    // 读取单个文件，损坏则返回 null
    private @Nullable PersistedDraft read(Path file) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return PersistedDraft.fromJson(json);
        } catch (IOException | RuntimeException exception) {
            Constants.LOG.warn("编辑草稿文件无法解析，已跳过：{}", file, exception);
            return null;
        }
    }

    // 规则 id 转成文件名：可读前缀（非 ASCII 字母数字与 . _ - 换成下划线）+ 稳定短哈希 + .json
    // 短哈希用于区分只差非法字符的 id（如 idtw:foo/bar 与 idtw:foo_bar 清洗后前缀相同）；
    // id 本身仍存在文件内容里，不需要从文件名反向还原。
    public static String fileName(String targetId) {
        return sanitize(targetId) + "-" + shortHash(targetId) + ".json";
    }

    // 可读前缀：只保留安全字符，空则用下划线占位
    private static String sanitize(String targetId) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < targetId.length(); index++) {
            char character = targetId.charAt(index);
            boolean keep = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '.' || character == '_' || character == '-';
            builder.append(keep ? character : '_');
        }
        if (builder.length() == 0) {
            builder.append("_");
        }
        return builder.toString();
    }

    // FNV-1a 32 位短哈希：与 JVM/平台无关的稳定值，固定 8 位十六进制
    private static String shortHash(String targetId) {
        int hash = 0x811c9dc5;
        for (byte value : targetId.getBytes(StandardCharsets.UTF_8)) {
            hash ^= value & 0xff;
            hash *= 0x01000193;
        }
        return String.format(Locale.ROOT, "%08x", hash);
    }

    private Path resolve(String targetId) {
        return this.directory.resolve(fileName(targetId));
    }
}
