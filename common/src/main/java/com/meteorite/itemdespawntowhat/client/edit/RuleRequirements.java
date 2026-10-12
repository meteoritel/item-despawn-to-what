package com.meteorite.itemdespawntowhat.client.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/*** 缺少必填项的导航清单；只检查可静态确认的输入与内置产出，不替代服务端校验。 */
public final class RuleRequirements {

    /*** 路径用于已有字段定位，标签使用宿主本地化 key。 */
    public record Missing(String path, String labelKey) { }

    private static final String UI = "gui.itemdespawntowhat.edit.";

    // 固定成本消耗类效果：不计入「有无产出」判定
    private static final Set<String> CONSUMPTION_TYPES = Set.of(
            "itemdespawntowhat:consume_source",
            "itemdespawntowhat:consume_catalyst",
            "itemdespawntowhat:consume_fluid");

    // 空映射：缺失字段的兜底视图
    private static final RuleValue EMPTY_MAPPING = RuleValue.mapping().build();

    private RuleRequirements() { }

    // 界面侧入口：直接以草稿为输入，界面不接触 JSON 类型
    public static List<Missing> find(RuleDraft draft) {
        return draft == null ? List.of() : find(draft.root());
    }

    // 规则值入口
    public static List<Missing> find(RuleValue rule) {
        List<Missing> result = new ArrayList<>();
        RuleValue source = mappingOf(rule == null ? null : rule.child("source"));
        if (itemsOf(source.child("items")).isEmpty()) {
            result.add(new Missing("source.items", UI + "rule.source.items"));
        }
        boolean any = inspect(itemsOf(rule == null ? null : rule.child("effects")), "effects", result);
        List<RuleValue> outcomes = itemsOf(rule == null ? null : rule.child("outcomes"));
        for (int index = 0; index < outcomes.size(); index++) {
            RuleValue candidate = mappingOf(outcomes.get(index));
            any |= inspect(itemsOf(candidate.child("effects")),
                    "outcomes[" + index + "].effects", result);
        }
        if (!any) {
            result.add(new Missing("effects", UI + "required.results"));
        }
        return List.copyOf(result);
    }

    // 逐个效果核对必填字段；返回是否存在产出（消耗类效果不算）
    private static boolean inspect(List<RuleValue> actions, String path, List<Missing> missing) {
        boolean any = !actions.isEmpty();
        for (int index = 0; index < actions.size(); index++) {
            RuleValue action = mappingOf(actions.get(index));
            String type = text(action, "type");
            if (CONSUMPTION_TYPES.contains(type)) {
                continue;
            }
            String field = switch (type) {
                case "itemdespawntowhat:spawn_entity" -> switch (text(action, "variant")) {
                    case "item" -> "item";
                    case "entity" -> "entity";
                    case "experience" -> "";
                    default -> "variant";
                };
                case "itemdespawntowhat:place_block" -> usesSourceBlock(action) ? "" : "block";
                case "itemdespawntowhat:loot_table" -> "loot_table";
                default -> "";
            };
            if (!field.isEmpty() && text(action, field).isBlank()) {
                missing.add(new Missing(path + "[" + index + "]." + field, UI + "required." + field));
            }
        }
        return any;
    }

    // 映射视图：缺失或形态不符按空映射处理（与迁移前的 object(...) 一致）
    private static RuleValue mappingOf(RuleValue value) {
        return value != null && value.isMapping() ? value : EMPTY_MAPPING;
    }

    // 列表视图：缺失或形态不符按空列表处理（与迁移前的 array(...) 一致）
    private static List<RuleValue> itemsOf(RuleValue value) {
        return value != null && value.isList() ? value.items() : List.of();
    }

    // 字符串字段：缺失或非字符串返回空串（与迁移前的 text(...) 一致）
    private static String text(RuleValue mapping, String key) {
        RuleValue value = mapping.child(key);
        return value != null && value.isText() ? value.asText() : "";
    }

    // use_source_block 布尔：与迁移前的 isJsonPrimitive() && getAsBoolean() 一致（文本按 parseBoolean 解析）
    private static boolean usesSourceBlock(RuleValue action) {
        RuleValue value = action.child("use_source_block");
        if (value == null || !value.isScalar()) {
            return false;
        }
        return value.isFlag() ? value.asFlag(false) : Boolean.parseBoolean(value.asText());
    }
}
