package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

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

    /** 按语言资源中的完整 key 创建反馈，参数由 arg 按位置追加。 */
    public static RuleCommandText of(String key) {
        return new RuleCommandText(key);
    }

    /** 追加位置参数；保留 Component 的本地化、样式与交互，其余值转换为字符串。 */
    public RuleCommandText arg(Object value) {
        args.add(value instanceof Component ? value : value == null ? "" : String.valueOf(value));
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

    static Component state(@Nullable Boolean value) {
        return of("itemdespawntowhat.command.state." + (value == null ? "inherit" : value ? "on" : "off")).build();
    }

    static Component layer(RuleSourceLayer layer) {
        return of("itemdespawntowhat.command.layer." + layer.id()).build();
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

    /** 把问题级别与位置展示为可读反馈；错误用红色，告警用黄色。 */
    public static Component issue(Issue issue) {
        return of("itemdespawntowhat.command.issue")
                .arg(of("itemdespawntowhat.command.severity."
                        + (issue.severity() == IssueSeverity.ERROR ? "error" : "warn")).build())
                .arg(issue.message())
                .arg(locationOf(issue))
                .build().copy().withStyle(issue.severity() == IssueSeverity.ERROR ? ChatFormatting.RED : ChatFormatting.YELLOW);
    }

    /** 展示已校验规则的来源分布与同次加载的问题数量。 */
    public static Component summary(String stage, RuleLoadResult<Rule> result) {
        return of("itemdespawntowhat.command.load.summary")
                .arg(of("itemdespawntowhat.command.stage." + stage).build())
                .arg(result.rules().size())
                .arg(countLayer(result, RuleSourceLayer.BUILTIN))
                .arg(countLayer(result, RuleSourceLayer.WORLD))
                .arg(countLayer(result, RuleSourceLayer.OVERLAY))
                .arg(result.issues().errors().size())
                .arg(result.issues().warnings().size())
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
