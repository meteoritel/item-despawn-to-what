package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.load.RulePaths;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;

/**
 * 规则导出：把列表中的规则写成一个可跨设备共享的数据包 zip。
 *
 * <p>导出包沿用数据包层的目录契约 {@code data/<ns>/idtw/rules/**}{@code .json}，因此解压或直接
 * 放进存档的 {@code datapacks/} 目录即可被服务端按普通数据包读取。规则体取列表当前显示的内容，
 * 未保存的草稿也会被导出；每条规则强制写回自身 id，避免单对象文件按路径推导出不同身份。</p>
 *
 * <p>导出是客户端的本地文件操作：目标目录相对游戏根目录，与草稿仓库
 * {@link com.meteorite.itemdespawntowhat.client.edit.draft.DraftStore} 的相对路径口径一致，
 * 不依赖加载器 API，也不写入服务端的覆盖层或数据包目录。</p>
 */
public final class RulePackExporter {

    // 导出目录（相对游戏根目录）
    public static final String DIRECTORY = "itemdespawntowhat-exports";

    // 导出范围标识：只导出覆盖层条目 / 覆盖层与数据包条目
    public static final String SCOPE_OVERLAY = "overlay";
    public static final String SCOPE_ALL = "overlay-datapack";

    // 数据包描述
    private static final String PACK_DESCRIPTION = "itemDespawnToWhat rules";

    // 落盘格式：带缩进便于作者后续手工调整
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // 文件名时间戳
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private RulePackExporter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 待导出一条规则：稳定 id 与当前显示体。
     *
     * @param id   规则 id，同时决定数据包内的命名空间与文件路径
     * @param body 规则体，取列表当前显示的草稿或生效视图
     */
    public record RuleEntry(String id, RuleValue body) {
    }

    /**
     * 导出结果。
     *
     * @param file  写出的 zip 文件，路径相对游戏根目录
     * @param count 实际写入的规则条数
     */
    public record Result(Path file, int count) {

        /**
         * 展示用路径：统一使用正斜杠，避免不同系统的分隔符差异。
         *
         * @return 形如 {@code itemdespawntowhat-exports/xxx.zip} 的路径文本
         */
        public String displayPath() {
            return file.toString().replace('\\', '/');
        }
    }

    /**
     * 把规则写成数据包 zip。
     *
     * @param scope   导出范围标识，只用于文件名
     * @param entries 待导出规则；调用方负责按范围过滤
     * @return 写出的文件与实际条数
     * @throws IOException 目录创建或写文件失败
     */
    public static Result export(String scope, List<RuleEntry> entries) throws IOException {
        Path directory = Paths.get(DIRECTORY);
        Files.createDirectories(directory);
        String baseName = "itemdespawntowhat-" + scope + "-" + STAMP.format(LocalDateTime.now());
        Path file = uniquePath(directory, baseName);
        List<RuleEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(RuleEntry::id));
        int count = 0;
        try (OutputStream out = Files.newOutputStream(file);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            write(zip, "pack.mcmeta", packMeta().toString());
            for (RuleEntry entry : sorted) {
                ResourceLocation id = ResourceLocation.tryParse(entry.id());
                JsonObject body = id == null ? null : ruleJson(entry);
                if (id == null || body == null || !writablePath(id)) {
                    continue;
                }
                write(zip, rulePath(id), GSON.toJson(body) + "\n");
                count++;
            }
        } catch (IOException exception) {
            Files.deleteIfExists(file);
            throw exception;
        }
        return new Result(file, count);
    }

    // 同一秒内重复导出时追加序号，避免覆盖上一次结果
    private static Path uniquePath(Path directory, String baseName) {
        Path candidate = directory.resolve(baseName + ".zip");
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(baseName + "-" + index + ".zip");
            index++;
        }
        return candidate;
    }

    // 数据包清单：格式版本取当前游戏的数据包版本，避免跨版本维护魔法值
    private static JsonObject packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        pack.addProperty("description", PACK_DESCRIPTION);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    // 规则体转对象并强制写回 id；非对象内容无法作为规则导出
    private static @Nullable JsonObject ruleJson(RuleEntry entry) {
        try {
            JsonElement parsed = JsonParser.parseString(entry.body().toJson());
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject json = parsed.getAsJsonObject();
            json.addProperty(RuleFields.ID, entry.id());
            return json;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    // 规则 id 的路径部分只接受资源路径字符，并排除上级目录写法
    private static boolean writablePath(ResourceLocation id) {
        String path = id.getPath();
        return !path.isBlank() && !path.contains("..");
    }

    // 数据包内规则文件路径：data/<ns>/idtw/rules/<path>.json
    private static String rulePath(ResourceLocation id) {
        return "data/" + id.getNamespace() + "/" + RulePaths.DATAPACK_RULES_DIRECTORY + "/"
                + id.getPath() + RulePaths.RULE_FILE_EXTENSION;
    }

    // 写入一个 UTF-8 文本条目；条目名固定使用正斜杠
    private static void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
