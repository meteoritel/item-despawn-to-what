package com.meteorite.itemdespawntowhat.core.load;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecodeBudget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则文件解析：把一份 JSON 文本转换为原始条目列表。
 * 支持"单条规则对象"与"规则数组"两种文件形状；id 优先取字段，缺省由文件路径推导为 &lt;ns&gt;:&lt;相对路径去扩展名&gt;。
 * 统一严格策略：整个文件解析失败（非法 JSON / 嵌套深度超限 / 顶层类型错误）→ 整文件拒载；
 * 单条语义非法（id 非法、同文件 id 重复等）→ 仅该条拒载，其余照常。
 * 解析层不再识别任何覆盖控制字段：disabled / delete 等写法按普通未知字段保留在 body 里，
 * 交由解码层按未知字段告警，加载行为与普通规则一致。
 */
public final class RuleFileParser {

    private RuleFileParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 解析一份规则文件，返回解析通过的原始条目（顺序与文件中一致）
    // json 为文件文本；origin 为来源标识；derivedId 为由文件路径推导出的 id，无法推导时为 null

    public static List<RawRuleEntry> parse(String json,
                                           RuleOrigin origin,
                                           @Nullable ResourceLocation derivedId,
                                           IssueCollector issues) {
        RuleDecodeBudget.ParseResult parsed = RuleDecodeBudget.parse(json);
        if (!parsed.ok()) {
            // 统一走有界解析入口：嵌套超深与语法非法都只报结构化议题，不让解析期错误向上逃逸
            issues.error("JSON 解析失败: " + parsed.failureReason(), origin.display(), "");
            return List.of();
        }
        JsonElement root = parsed.element();
        if (root == null || root.isJsonNull()) {
            issues.error("规则文件为空", origin.display(), "");
            return List.of();
        }

        List<JsonElement> elements = new ArrayList<>();
        boolean arrayRoot;
        if (root.isJsonObject()) {
            elements.add(root);
            arrayRoot = false;
        } else if (root.isJsonArray()) {
            arrayRoot = true;
            for (JsonElement element : root.getAsJsonArray()) {
                elements.add(element);
            }
        } else {
            issues.error("规则文件顶层必须是规则对象或规则数组", origin.display(), "");
            return List.of();
        }

        // 仅当文件只含一条规则时，才允许用文件名推导 id；多条目文件必须逐条显式声明 id
        boolean canDerive = elements.size() == 1;
        List<RawRuleEntry> entries = new ArrayList<>();
        Set<ResourceLocation> seenIds = new HashSet<>();
        for (int index = 0; index < elements.size(); index++) {
            String fieldPath = arrayRoot ? "[" + index + "]" : "";
            RawRuleEntry entry = parseEntry(elements.get(index), origin, fieldPath, canDerive, derivedId, issues);
            if (entry == null) {
                continue;
            }
            if (!seenIds.add(entry.id())) {
                issues.error("同文件内规则 id 重复: " + entry.id(),
                        origin.display(), RulePaths.joinFieldPath(fieldPath, RuleFields.ID));
                continue;
            }
            entries.add(entry);
        }
        return entries;
    }

    // 解析单条规则；返回 null 表示该条拒载（问题已进入 issues）
    private static @Nullable RawRuleEntry parseEntry(JsonElement element,
                                                     RuleOrigin origin,
                                                     String fieldPath,
                                                     boolean canDerive,
                                                     @Nullable ResourceLocation derivedId,
                                                     IssueCollector issues) {
        if (!element.isJsonObject()) {
            issues.error("规则条目必须是 JSON 对象", origin.display(), fieldPath);
            return null;
        }
        JsonObject body = element.getAsJsonObject();

        ResourceLocation id = resolveId(body, origin, fieldPath, canDerive, derivedId, issues);
        if (id == null) {
            return null;
        }
        return new RawRuleEntry(id, body, origin, fieldPath);
    }

    // 解析 id：优先取字段，缺省时（仅单条文件）由文件路径推导
    private static @Nullable ResourceLocation resolveId(JsonObject body,
                                                       RuleOrigin origin,
                                                       String fieldPath,
                                                       boolean canDerive,
                                                       @Nullable ResourceLocation derivedId,
                                                       IssueCollector issues) {
        if (body.has(RuleFields.ID)) {
            JsonElement idElement = body.get(RuleFields.ID);
            if (!idElement.isJsonPrimitive() || !idElement.getAsJsonPrimitive().isString()) {
                issues.error("id 必须是字符串", origin.display(), RulePaths.joinFieldPath(fieldPath, RuleFields.ID));
                return null;
            }
            ResourceLocation id = ResourceLocation.tryParse(idElement.getAsString());
            if (id == null) {
                issues.error("非法的规则 id: " + idElement.getAsString(),
                        origin.display(), RulePaths.joinFieldPath(fieldPath, RuleFields.ID));
                return null;
            }
            return id;
        }
        if (!canDerive) {
            issues.error("多条目规则文件中的每条规则都必须显式声明 id",
                    origin.display(), RulePaths.joinFieldPath(fieldPath, RuleFields.ID));
            return null;
        }
        if (derivedId == null) {
            issues.error("规则缺少 id，且无法由文件路径推导出合法 id（请显式声明 id）",
                    origin.display(), RulePaths.joinFieldPath(fieldPath, RuleFields.ID));
            return null;
        }
        return derivedId;
    }

}
