package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * 服务器问题路径 → 编辑器稳定身份的映射核心（主计划 §7）。
 * <p>分两段：{@link #anchor(String, String, JsonObject)} 在问题产生时的草稿形状上先记下节点身份
 * （候选 id、效果类型、条件节点 op/类型与出现序号）；{@link IssueAnchor#resolve(JsonObject)}
 * 再把身份映射到当前草稿的下标。数组重排或增删后不会跳错对象：身份找不到时才回落到原下标。
 * <p>同一数组里存在多个完全同类型节点（如同型效果各一份、同型条件叶多份）时身份不唯一，
 * 此时按出现顺序取第一个匹配项；这一点在界面上只表现为「定位到同类的第一个节点」。
 * <p>未知、空或畸变路径都不抛异常：至少落到对应规则与问题页签（{@link Tab#INFO}）。
 * <p>界面侧的选中与展开由 P3 负责，本类只提供映射结果。
 */
public final class RuleIssueLocator {

    // 工具类
    private RuleIssueLocator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 问题所属的编辑器页签：只表达「问题区」，不绑定具体界面控件。
     */
    public enum Tab {
        SOURCE,
        CONDITIONS,
        RESULTS,
        INFO
    }

    /**
     * 锚点步骤：路径段名、锚定时的下标、以及该数组元素的身份（无身份时为 null）。
     */
    public record AnchorStep(String name, int index, @Nullable String identity) {
    }

    /**
     * 解析后的步骤：步骤名、当前草稿中的下标、以及当前草稿中的元素。
     */
    public record ResolvedStep(String name, int index, @Nullable JsonElement element) {
    }

    /**
     * 问题锚点：规则 id、原始问题路径、锚点步骤与页签。
     */
    public record IssueAnchor(String ruleId, String fieldPath, List<AnchorStep> steps, Tab tab) {

        // 把锚点映射到当前草稿形状；不给草稿时等价于原样保留下标
        public Location resolve(@Nullable JsonObject draft) {
            List<ResolvedStep> resolved = new ArrayList<>(steps.size());
            JsonElement current = draft;
            for (AnchorStep step : steps) {
                try {
                    JsonElement container = member(current, step.name());
                    if (step.index() < 0) {
                        resolved.add(new ResolvedStep(step.name(), -1, container));
                        current = container;
                        continue;
                    }
                    int index = matchIndex(container, step.index(), step.identity());
                    JsonElement element = elementAt(container, index);
                    resolved.add(new ResolvedStep(step.name(), index, element));
                    current = element;
                } catch (RuntimeException error) {
                    break;
                }
            }
            int candidateIndex = -1;
            String candidateId = null;
            int effectIndex = -1;
            String effectType = null;
            String conditionPath = null;
            for (int i = 0; i < resolved.size(); i++) {
                ResolvedStep step = resolved.get(i);
                if (step.index() < 0) {
                    continue;
                }
                if (RuleFields.OUTCOMES.equals(step.name())) {
                    candidateIndex = step.index();
                    JsonObject candidate = asObject(step.element());
                    candidateId = candidate == null ? null : stringOf(candidate, RuleFields.CANDIDATE_ID);
                } else if (RuleFields.EFFECTS.equals(step.name())) {
                    effectIndex = step.index();
                    JsonObject effect = asObject(step.element());
                    effectType = effect == null ? null : stringOf(effect, RuleFields.TYPE);
                } else if (RuleFields.TERMS.equals(step.name()) || RuleFields.TERM.equals(step.name())) {
                    conditionPath = render(resolved, i + 1);
                }
            }
            return new Location(ruleId, tab, fieldPath, render(resolved, resolved.size()),
                    candidateId, candidateIndex, effectType, effectIndex, conditionPath);
        }

        // 不给草稿时的映射结果（下标原样保留）
        public Location location() {
            return resolve(null);
        }
    }

    /**
     * 定位结果：规则、页签、重算后的路径与各层稳定身份。
     * <p>{@code rawPath} 是服务端给的原始路径；{@code currentPath} 是按当前草稿重算的路径，
     * 界面可直接用它高亮对应字段。
     */
    public record Location(String ruleId, Tab tab, String rawPath, String currentPath,
                           @Nullable String candidateId, int candidateIndex,
                           @Nullable String effectType, int effectIndex,
                           @Nullable String conditionPath) {

        // 是否携带了可用的问题路径
        public boolean known() {
            return rawPath != null && !rawPath.isBlank();
        }

        // 是否定位到了某个候选
        public boolean hasCandidate() {
            return candidateId != null || candidateIndex >= 0;
        }

        // 是否定位到了某个效果
        public boolean hasEffect() {
            return effectType != null || effectIndex >= 0;
        }
    }

    /**
     * 记录问题锚点：先读节点身份，再落下标。
     * <p>draft 为 null（尚未打开草稿）时只记下标，不抛异常。
     */
    public static IssueAnchor anchor(@Nullable String ruleId, @Nullable String fieldPath, @Nullable JsonObject draft) {
        String path = fieldPath == null ? "" : fieldPath;
        List<DraftPaths.Segment> segments;
        try {
            segments = DraftPaths.parse(path);
        } catch (RuntimeException error) {
            segments = List.of();
        }
        List<AnchorStep> steps = new ArrayList<>(segments.size());
        JsonElement current = draft;
        for (DraftPaths.Segment segment : segments) {
            try {
                JsonElement container = member(current, segment.name());
                if (!segment.hasIndex()) {
                    steps.add(new AnchorStep(segment.name(), -1, null));
                    current = container;
                    continue;
                }
                steps.add(new AnchorStep(segment.name(), segment.index(), identityAt(container, segment.index())));
                current = elementAt(container, segment.index());
            } catch (RuntimeException error) {
                break;
            }
        }
        return new IssueAnchor(ruleId == null ? "" : ruleId, path, List.copyOf(steps), tabOf(path));
    }

    // 批量记录同一规则的问题锚点
    public static List<IssueAnchor> anchorAll(@Nullable String ruleId, @Nullable Iterable<String> fieldPaths,
                                              @Nullable JsonObject draft) {
        List<IssueAnchor> anchors = new ArrayList<>();
        if (fieldPaths == null) {
            return anchors;
        }
        for (String path : fieldPaths) {
            anchors.add(anchor(ruleId, path, draft));
        }
        return anchors;
    }

    // 问题区映射：只按路径首段归类，未知首段落 INFO
    public static Tab tabOf(@Nullable String fieldPath) {
        List<DraftPaths.Segment> segments;
        try {
            segments = DraftPaths.parse(fieldPath == null ? "" : fieldPath);
        } catch (RuntimeException error) {
            return Tab.INFO;
        }
        if (segments.isEmpty()) {
            return Tab.INFO;
        }
        return switch (segments.getFirst().name()) {
            case RuleFields.SOURCE -> Tab.SOURCE;
            case RuleFields.CONDITIONS -> Tab.CONDITIONS;
            case RuleFields.EFFECTS, RuleFields.OUTCOMES -> Tab.RESULTS;
            default -> Tab.INFO;
        };
    }

    // 在当前草稿中按身份找下标；身份缺失或找不到时回落原下标
    private static int matchIndex(@Nullable JsonElement container, int fallback, @Nullable String identity) {
        if (identity == null || !(container instanceof JsonArray array)) {
            return fallback;
        }
        for (int i = 0; i < array.size(); i++) {
            if (identity.equals(identityAt(container, i))) {
                return i;
            }
        }
        return fallback;
    }

    // 数组元素的身份：候选 id > 效果类型 > 条件节点 op+类型；都没有则无身份
    private static @Nullable String identityAt(@Nullable JsonElement container, int index) {
        JsonObject object = asObject(elementAt(container, index));
        if (object == null) {
            return null;
        }
        String id = stringOf(object, RuleFields.CANDIDATE_ID);
        if (id != null && !id.isBlank()) {
            return "id:" + id;
        }
        String type = stringOf(object, RuleFields.TYPE);
        if (type != null && !type.isBlank()) {
            return "type:" + type;
        }
        String op = stringOf(object, RuleFields.OP);
        if (op == null || op.isBlank()) {
            return null;
        }
        JsonObject condition = asObject(object.get(RuleFields.CONDITION));
        String conditionType = condition == null ? "" : stringOf(condition, RuleFields.TYPE);
        return "op:" + op + ":" + (conditionType == null ? "" : conditionType);
    }

    // 取对象字段
    private static @Nullable JsonElement member(@Nullable JsonElement element, String name) {
        if (!(element instanceof JsonObject object) || name.isEmpty()) {
            return null;
        }
        return object.get(name);
    }

    // 取数组元素
    private static @Nullable JsonElement elementAt(@Nullable JsonElement element, int index) {
        if (!(element instanceof JsonArray array) || index < 0 || index >= array.size()) {
            return null;
        }
        return array.get(index);
    }

    // 把前 count 个已解析步骤渲染成路径
    private static String render(List<ResolvedStep> resolved, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count && i < resolved.size(); i++) {
            ResolvedStep step = resolved.get(i);
            if (step.name().isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(step.name());
            if (step.index() >= 0) {
                builder.append('[').append(step.index()).append(']');
            }
        }
        return builder.toString();
    }

    // 转对象（可能为 null）
    private static @Nullable JsonObject asObject(@Nullable JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    // 读字符串字段（缺省或类型不符返回 null）
    private static @Nullable String stringOf(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return null;
    }
}
