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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 *
 * <p><b>导出预检</b>：写盘前先检查全部条目能否落成数据包内的规则文件，任何一条不可写都整体拒绝
 * （{@link ExportRejectedException}），不会写出一个悄悄少几条规则、却仍报成功的包。</p>
 *
 * <p><b>线程约定</b>：{@link RuleValue} 只是草稿的实时视图，导出线程不能读它；调用方在客户端线程
 * 用 {@link RuleEntry} 取好 JSON 文本快照后交给 {@link #exportAsync}，写盘在单独的守护线程执行，
 * 结果再回客户端线程处理。</p>
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

    // 后台导出线程：串行执行，避免同一秒内多次导出争用同一个文件名；守护线程不阻止游戏退出
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "itemDespawnToWhat-export");
        thread.setDaemon(true);
        return thread;
    });

    private RulePackExporter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 待导出一条规则：稳定 id 与规则体的 JSON 文本快照。
     *
     * <p>{@link RuleValue} 是草稿的实时视图，跨线程读取会与编辑并发；因此快照在客户端线程取成
     * 文本（{@link RuleValue#toJson()}），导出线程只处理不可变值。</p>
     *
     * @param id       规则 id，同时决定数据包内的命名空间与文件路径
     * @param bodyJson 规则体的紧凑 JSON 文本，取列表当前显示的草稿或生效视图
     */
    public record RuleEntry(String id, String bodyJson) {
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
     * 导出预检失败：至少一条规则无法写成数据包文件，本次整体不写盘。
     *
     * <p>异常只携带 id 列表，界面据此拼本地化提示，不在异常里存文案。</p>
     */
    public static final class ExportRejectedException extends IOException {

        private final List<String> ids;

        private ExportRejectedException(List<String> ids) {
            super("Unwritable rule ids: " + String.join(", ", ids));
            this.ids = List.copyOf(ids);
        }

        /** @return 预检未通过、因此被拒绝导出的规则 id，按输入顺序 */
        public List<String> ids() {
            return ids;
        }
    }

    /**
     * 把规则写成数据包 zip；写盘前先预检全部条目。
     *
     * @param scope   导出范围标识，只用于文件名
     * @param entries 待导出规则；调用方负责按范围过滤
     * @return 写出的文件与实际条数
     * @throws ExportRejectedException 有条目无法写成数据包文件；此时未创建任何文件
     * @throws IOException             目录创建或写文件失败
     */
    public static Result export(String scope, List<RuleEntry> entries) throws IOException {
        List<Prepared> prepared = prepare(entries);
        Path directory = Paths.get(DIRECTORY);
        Files.createDirectories(directory);
        String baseName = "itemdespawntowhat-" + scope + "-" + STAMP.format(LocalDateTime.now());
        Path file = uniquePath(directory, baseName);
        try (OutputStream out = Files.newOutputStream(file);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            write(zip, "pack.mcmeta", packMeta().toString());
            for (Prepared entry : prepared) {
                write(zip, rulePath(entry.id()), GSON.toJson(entry.body()) + "\n");
            }
        } catch (IOException exception) {
            Files.deleteIfExists(file);
            throw exception;
        }
        return new Result(file, prepared.size());
    }

    /**
     * 在后台线程执行同一次导出。
     *
     * <p>条目列表在此处复制一份：调用方随后改动自己的集合不会影响在途导出。</p>
     *
     * @param scope   导出范围标识，只用于文件名
     * @param entries 客户端线程取好的规则快照
     * @return 完成时给出导出结果的 future，失败时带原始 {@link IOException}
     */
    public static CompletableFuture<Result> exportAsync(String scope, List<RuleEntry> entries) {
        List<RuleEntry> snapshot = List.copyOf(entries);
        CompletableFuture<Result> future = new CompletableFuture<>();
        WORKER.execute(() -> {
            try {
                future.complete(export(scope, snapshot));
            // 写盘失败与序列化意外都必须结束 future，否则界面会一直停在导出中的禁用态
            } catch (IOException | RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    // 预检：任何条目不可写都整体拒绝，避免写出少几条却仍报成功的包
    private static List<Prepared> prepare(List<RuleEntry> entries) throws ExportRejectedException {
        List<Prepared> prepared = new ArrayList<>(entries.size());
        List<String> rejected = new ArrayList<>();
        for (RuleEntry entry : entries) {
            ResourceLocation location = ResourceLocation.tryParse(entry.id());
            JsonObject body = location == null ? null : ruleJson(entry);
            if (location == null || body == null || !writablePath(location)) {
                rejected.add(entry.id());
                continue;
            }
            prepared.add(new Prepared(location, body));
        }
        if (!rejected.isEmpty()) {
            throw new ExportRejectedException(rejected);
        }
        prepared.sort(Comparator.comparing(Prepared::id));
        return prepared;
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
            JsonElement parsed = JsonParser.parseString(entry.bodyJson());
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

    /**
     * 该规则 id 能否写成数据包内的规则文件。
     *
     * <p>列表的「可导出」计数与真正的导出入参共用这条判定：否则弹窗显示 N 条、
     * 实际却会因为 id 非法而整体拒绝导出。</p>
     *
     * @param id 规则 id，可为 null
     * @return 能解析为资源位置且其文件路径可写成规则文件时为 true
     */
    public static boolean isWritableId(@Nullable String id) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        return location != null && writablePath(location);
    }

    // 规则 id 必须逐段可写成文件：只有 "." 与 ".." 段会改变目录层级，a..b 这类含连续点的
    // 合法名字继续放行；再按归一化边界确认落点仍在 data/<ns>/idtw/rules 之内。
    private static boolean writablePath(ResourceLocation id) {
        String entry = rulePath(id);
        for (String segment : entry.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        Path root = Paths.get("data", id.getNamespace(), RulePaths.DATAPACK_RULES_DIRECTORY).normalize();
        return Paths.get(entry).normalize().startsWith(root);
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

    // 已通过预检的一条规则：资源位置与已写回 id 的规则对象
    private record Prepared(ResourceLocation id, JsonObject body) {
    }
}
