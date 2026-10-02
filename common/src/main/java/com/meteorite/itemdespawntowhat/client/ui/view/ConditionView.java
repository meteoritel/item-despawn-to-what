package com.meteorite.itemdespawntowhat.client.ui.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件表达式的视图模型：对应 DNF 二维数组协议 JSON（外层 OR、内层 AND，空 = 恒真）。
 * 复用客户端条件类型注册表做显示名解析，不引用服务端模型。
 */
public final class ConditionView {

    private final List<List<Leaf>> groups = new ArrayList<>();

    public static ConditionView empty() {
        return new ConditionView();
    }

    // 由协议 JSON 解析；非二维数组时返回空视图
    public static ConditionView fromJson(@Nullable JsonElement element) {
        ConditionView view = new ConditionView();
        if (element == null || !element.isJsonArray()) {
            return view;
        }
        for (JsonElement groupElement : element.getAsJsonArray()) {
            if (!groupElement.isJsonArray()) {
                continue;
            }
            List<Leaf> group = new ArrayList<>();
            for (JsonElement leafElement : groupElement.getAsJsonArray()) {
                if (leafElement.isJsonObject()) {
                    group.add(Leaf.fromJson(leafElement.getAsJsonObject()));
                }
            }
            view.groups.add(group);
        }
        return view;
    }

    // 深拷贝（经 JSON 往返，避免共享可变结构）
    public static ConditionView copyOf(@Nullable ConditionView source) {
        return source == null ? empty() : fromJson(source.toJson());
    }

    // 组列表（可写，组间 OR）
    public List<List<Leaf>> groups() {
        return groups;
    }

    // 是否为空表达式（恒真）
    public boolean isEmpty() {
        for (List<Leaf> group : groups) {
            if (!group.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // 序列化回协议 JSON
    public JsonArray toJson() {
        JsonArray root = new JsonArray();
        for (List<Leaf> group : groups) {
            JsonArray groupArray = new JsonArray();
            for (Leaf leaf : group) {
                groupArray.add(leaf.toJson());
            }
            root.add(groupArray);
        }
        return root;
    }

    // 条件摘要文本，供主表单按钮展示
    public Component summary() {
        if (isEmpty()) {
            return Component.translatable("gui.itemdespawntowhat.condition.always");
        }
        MutableComponent result = Component.empty();
        boolean firstGroup = true;
        for (List<Leaf> group : groups) {
            if (group.isEmpty()) {
                continue;
            }
            if (!firstGroup) {
                result.append(Component.translatable("gui.itemdespawntowhat.condition.or"));
            }
            firstGroup = false;
            boolean firstLeaf = true;
            for (Leaf leaf : group) {
                if (!firstLeaf) {
                    result.append(Component.translatable("gui.itemdespawntowhat.condition.and"));
                }
                firstLeaf = false;
                if (leaf.negated()) {
                    result.append(Component.translatable("gui.itemdespawntowhat.condition.not"));
                }
                result.append(leaf.displayName());
            }
        }
        return result;
    }

    /**
     * 单个条件叶：type + negated + 类型专属参数（参数直接平铺在同一个 JSON 对象里）。
     */
    public static final class Leaf {

        private ResourceLocation typeId;
        private boolean negated;
        private JsonObject params = new JsonObject();

        private Leaf(ResourceLocation typeId) {
            this.typeId = typeId;
        }

        // 由协议 JSON 解析：type / negated 之外的所有字段都是类型参数
        public static Leaf fromJson(JsonObject json) {
            ResourceLocation parsed = json.has("type") && json.get("type").isJsonPrimitive()
                    ? ResourceLocation.tryParse(json.get("type").getAsString()) : null;
            Leaf leaf = new Leaf(parsed);
            leaf.negated = json.has("negated") && json.get("negated").isJsonPrimitive()
                    && json.get("negated").getAsBoolean();
            for (String key : json.keySet()) {
                if ("type".equals(key) || "negated".equals(key)) {
                    continue;
                }
                leaf.params.add(key, json.get(key).deepCopy());
            }
            return leaf;
        }

        // 新建一个指定类型的空参数条件叶
        public static Leaf of(ResourceLocation typeId) {
            return new Leaf(typeId);
        }

        public @Nullable ResourceLocation typeId() {
            return typeId;
        }

        public void setTypeId(ResourceLocation typeId) {
            this.typeId = typeId;
        }

        public boolean negated() {
            return negated;
        }

        public void setNegated(boolean negated) {
            this.negated = negated;
        }

        public JsonObject params() {
            return params;
        }

        public void setParams(JsonObject params) {
            this.params = params == null ? new JsonObject() : params;
        }

        // 显示名取自新协议条件类型登记表，未登记类型退化为 id 文本（不引用旧链路条件注册表）
        public Component displayName() {
            if (typeId == null) {
                return Component.literal("?");
            }
            return RuleConditionInputs.isKnown(typeId)
                    ? RuleConditionInputs.displayName(typeId)
                    : Component.literal(typeId.toString());
        }

        // 序列化回协议 JSON
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            if (typeId != null) {
                json.addProperty("type", typeId.toString());
            }
            json.addProperty("negated", negated);
            for (String key : params.keySet()) {
                if ("type".equals(key) || "negated".equals(key)) {
                    continue;
                }
                json.add(key, params.get(key).deepCopy());
            }
            return json;
        }
    }
}
