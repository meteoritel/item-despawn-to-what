package com.meteorite.itemdespawntowhat.core.load;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
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
 * 统一严格策略：整个文件解析失败（非法 JSON / 顶层类型错误）→ 整文件拒载；
 * 单条语义非法（id 非法、控制字段非法、同文件 id 重复等）→ 仅该条拒载，其余照常。
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
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException | IllegalStateException e) {
            issues.error("JSON 解析失败: " + e.getMessage(), origin.display(), "");
            return List.of();
        }
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

        Boolean disabled = readFlag(body, RuleFields.DISABLED, origin, fieldPath, issues);
        Boolean delete = readFlag(body, RuleFields.DELETE, origin, fieldPath, issues);
        if (disabled == null || delete == null) {
            return null;
        }
        if (disabled && delete) {
            issues.error("disabled 与 delete 不能同时为 true", origin.display(), fieldPath);
            return null;
        }
        if ((disabled || delete) && origin.layer() != RuleSourceLayer.OVERLAY) {
            issues.error("disabled / delete 是覆盖层控制字段，只能出现在 " + RuleSourceLayer.OVERLAY.displayName(),
                    origin.display(), fieldPath);
            return null;
        }
        if ((disabled || delete) && !isControlOnly(body)) {
            issues.warn("控制条目只识别 " + RuleFields.ID + " / " + RuleFields.DISABLED + " / " + RuleFields.DELETE
                    + "，其余字段被忽略", origin.display(), fieldPath);
        }
        return new RawRuleEntry(id, body, origin, disabled, delete, fieldPath);
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

    // 读取布尔控制字段；缺省为 false，类型非法则报错拒载（返回 null）
    private static @Nullable Boolean readFlag(JsonObject body,
                                              String field,
                                              RuleOrigin origin,
                                              String fieldPath,
                                              IssueCollector issues) {
        if (!body.has(field)) {
            return Boolean.FALSE;
        }
        JsonElement element = body.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            issues.error(field + " 必须是布尔值", origin.display(), RulePaths.joinFieldPath(fieldPath, field));
            return null;
        }
        return element.getAsBoolean();
    }

    // 判断对象是否只含控制字段与 id
    private static boolean isControlOnly(JsonObject body) {
        for (String key : body.keySet()) {
            if (!RuleFields.ID.equals(key) && !RuleFields.DISABLED.equals(key) && !RuleFields.DELETE.equals(key)) {
                return false;
            }
        }
        return true;
    }
}
