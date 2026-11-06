package com.meteorite.itemdespawntowhat.core.command;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueSeverity;
import com.meteorite.itemdespawntowhat.core.load.RuleLoadResult;
import com.meteorite.itemdespawntowhat.core.load.RuleSourceLayer;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import com.meteorite.itemdespawntowhat.core.service.RuleLoadingService;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import java.util.Objects;

/**
 * 为单条命令反馈构造可翻译组件，并保留参数中的样式与交互。
 * 英文 fallback 随组件发送，供缺少模组语言资源的客户端显示；实例由调用线程独占。
 */
public final class RuleCommandText {

    private static final String PREFIX = Constants.MOD_ID + ".command.";
    private static final Map<String, String> DEFAULT_MESSAGES = loadDefaultMessages();

    private final String key;
    private final List<Object> arguments = new ArrayList<>();

    private RuleCommandText(String key) {
        this.key = key;
    }

    /**
     * 使用完整本地化 key 创建反馈。
     *
     * @param key 与语言资源对应的完整 key
     * @return 供本条反馈独占的构造器
     */
    public static RuleCommandText of(String key) {
        return new RuleCommandText(key);
    }

    static RuleCommandText message(String key) {
        return of(PREFIX + key);
    }

    /**
     * 按语言模板中的位置追加参数；Component 保留本地化、样式与交互。
     *
     * @param value 模板参数，null 显示为空文本
     * @return 当前构造器
     */
    public RuleCommandText arg(@Nullable Object value) {
        arguments.add(value instanceof Component ? value : Objects.toString(value, ""));
        return this;
    }

    /**
     * 保留客户端语言选择，并为缺少模组语言资源的客户端携带英文模板。
     *
     * @return 带英文 fallback 的可翻译命令反馈
     */
    public Component build() {
        return Component.translatableWithFallback(key, DEFAULT_MESSAGES.get(key), arguments.toArray());
    }

    static Component state(@Nullable Boolean value) {
        if (value == null) {
            return message("state.inherit").build();
        }
        return message(value ? "state.on" : "state.off").build();
    }

    static Component layer(RuleSourceLayer layer) {
        return message("layer." + layer.id()).build();
    }

    /**
     * 展示问题级别、内容与位置，错误标红，告警标黄。
     *
     * @param issue 本次加载的问题
     * @return 保留原始诊断内容的反馈组件
     */
    public static Component issue(Issue issue) {
        boolean error = issue.severity() == IssueSeverity.ERROR;
        return message("issue")
                .arg(message(error ? "severity.error" : "severity.warn").build())
                .arg(issue.message())
                .arg(locationOf(issue))
                .build().copy().withStyle(error ? ChatFormatting.RED : ChatFormatting.YELLOW);
    }

    /**
     * 汇总同次加载的有效规则、来源分布与问题数量。
     *
     * @param stage  命令阶段，对应 command.stage 下的本地化 key
     * @param result 完整加载结果，包含合法的停用规则
     * @return 加载统计组件
     */
    public static Component summary(String stage, RuleLoadResult<Rule> result) {
        Map<RuleSourceLayer, Integer> layerCounts = RuleLoadingService.countByLayer(result);
        return message("load.summary")
                .arg(message("stage." + stage).build())
                .arg(result.rules().size())
                .arg(layerCounts.getOrDefault(RuleSourceLayer.BUILTIN, 0))
                .arg(layerCounts.getOrDefault(RuleSourceLayer.WORLD, 0))
                .arg(layerCounts.getOrDefault(RuleSourceLayer.OVERLAY, 0))
                .arg(result.issues().errors().size())
                .arg(result.issues().warnings().size())
                .build();
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

    private static String locationOf(Issue issue) {
        String origin = issue.origin() == null ? "" : issue.origin();
        String field = issue.fieldPath() == null ? "" : issue.fieldPath();
        if (origin.isEmpty()) {
            return field;
        }
        return field.isEmpty() ? origin : origin + "#" + field;
    }

}
