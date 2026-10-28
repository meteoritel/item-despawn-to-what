package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令反馈组件构造器。
 * 输出带内置英文 fallback 的可翻译组件：已安装模组的客户端按玩家语言渲染，
 * 缺少语言资源的客户端使用随组件发送的英文模板。模板从语言资源读取并缓存。
 * 参数一律按追加顺序对应语言文件里的 {@code %1$s/%2$s...}，因此每个 key 的参数个数固定——
 * 条件分支的场景用空串占位，不做可变参数。禁止在命令实现里拼中文字面量。
 */
public final class RuleCommandText {

    private static final Map<String, String> DEFAULT_MESSAGES = loadDefaultMessages();

    private final String key;
    private final List<Object> args = new ArrayList<>(4);

    private RuleCommandText(String key) {
        this.key = key;
    }

    // 以 i18n key 开始构造一条反馈组件
    public static RuleCommandText of(String key) {
        return new RuleCommandText(key);
    }

    // 追加一个位置参数；参数名只用于代码可读性标注，实际按追加顺序对应 %1$s、%2$s…
    // 统一转成字符串，避免翻译参数类型差异导致的渲染差异
    public RuleCommandText kv(String name, Object value) {
        args.add(value == null ? "" : String.valueOf(value));
        return this;
    }

    /**
     * 保留客户端语言选择，并为缺少模组语言资源的客户端携带英文模板。
     *
     * @return 带英文 fallback 的可翻译命令反馈
     */
    public Component build() {
        return Component.translatableWithFallback(key, DEFAULT_MESSAGES.get(key), args.toArray());
    }

    static String showInheritFallback() {
        String key = "itemdespawntowhat.command.rule.show.inherit";
        return DEFAULT_MESSAGES.getOrDefault(key, key);
    }

    private static Map<String, String> loadDefaultMessages() {
        String path = "/assets/" + Constants.MOD_ID + "/lang/en_us.json";
        try (InputStream stream = RuleCommandText.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing command translations: " + path);
            }
            Map<String, String> messages = new HashMap<>();
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                        .forEach(entry -> messages.put(entry.getKey(), entry.getValue().getAsString()));
            }
            return Map.copyOf(messages);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read command translations: " + path, exception);
        }
    }

    // 单条问题的组件：severity / message / 位置串（"来源#字段"，可空，固定 3 个参数）
    public static Component issue(Issue issue) {
        return of("itemdespawntowhat.command.issue")
                .kv("severity", issue.severity() == null
                        ? "" : issue.severity().name().toLowerCase(java.util.Locale.ROOT))
                .kv("message", issue.message())
                .kv("location", locationOf(issue))
                .build();
    }

    // 加载结果的问题清单（逐条一个组件）
    public static List<Component> issues(RuleLoadResult<Rule> result) {
        List<Component> lines = new ArrayList<>();
        for (Issue issue : result.allIssues()) {
            lines.add(issue(issue));
        }
        return lines;
    }

    // 加载结果的**纯文本**问题清单：供 RuleSnapshotAssembler 的快照内容使用（协议层仍是字符串）
    public static List<String> issueTexts(RuleLoadResult<Rule> result) {
        List<String> lines = new ArrayList<>();
        for (Issue issue : result.allIssues()) {
            lines.add(issue.format());
        }
        return lines;
    }

    // 一次加载的统计行：阶段名 + 总条数 + 三层来源计数 + 错误与告警数（固定 7 个参数）
    public static Component summary(String stage, RuleLoadResult<Rule> result) {
        return of("itemdespawntowhat.command.load.summary")
                .kv("stage", stage)
                .kv("total", result.rules().size())
                .kv("builtin", countLayer(result, RuleSourceLayer.BUILTIN))
                .kv("world", countLayer(result, RuleSourceLayer.WORLD))
                .kv("overlay", countLayer(result, RuleSourceLayer.OVERLAY))
                .kv("errors", result.issues().errors().size())
                .kv("warnings", result.issues().warnings().size())
                .build();
    }

    // 问题的位置串："来源#字段"；两者都为空时返回空串
    private static String locationOf(Issue issue) {
        String origin = issue.origin() == null ? "" : issue.origin();
        String field = issue.fieldPath() == null ? "" : issue.fieldPath();
        if (origin.isEmpty()) {
            return field;
        }
        return field.isEmpty() ? origin : origin + "#" + field;
    }

    // 某个来源层的规则条数
    private static int countLayer(RuleLoadResult<Rule> result, RuleSourceLayer layer) {
        int count = 0;
        for (var entry : result.rules()) {
            if (entry.origin().layer() == layer) {
                count++;
            }
        }
        return count;
    }
}
