package com.meteorite.itemdespawntowhat.client.ui.view;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 单个效果的视图模型：包裹效果对象协议 JSON（type + 通用字段 + 类型专属参数）。
 * 类型的必填/区间语义由服务端校验，本层只负责读写与展示。
 */
public final class EffectView {

    private static final int DEFAULT_DELAY_TICKS = 0;
    private static final double DEFAULT_CHANCE = 1.0D;

    private final JsonObject json;

    private EffectView(JsonObject json) {
        this.json = json;
    }

    // 绑定到既有 JSON 对象（不复制），供 RuleView 暴露可写嵌套视图
    static EffectView wrap(JsonObject json) {
        return new EffectView(json);
    }

    // 新建一个只含 type 的空效果
    public static EffectView ofType(ResourceLocation type) {
        JsonObject json = new JsonObject();
        json.addProperty(RuleFields.TYPE, type.toString());
        return wrap(json);
    }

    // 由协议 JSON 深拷贝构造
    public static EffectView fromJson(JsonObject json) {
        return wrap(json.deepCopy());
    }

    public @Nullable ResourceLocation type() {
        if (!json.has(RuleFields.TYPE) || !json.get(RuleFields.TYPE).isJsonPrimitive()) {
            return null;
        }
        return ResourceLocation.tryParse(json.get(RuleFields.TYPE).getAsString());
    }

    // 效果类型 id 文本；缺失时返回空串
    public String typeText() {
        ResourceLocation id = type();
        return id == null ? "" : id.toString();
    }

    // ===== 通用字段 ===== //

    public int delayTicks() {
        return intParam(RuleFields.DELAY_TICKS, DEFAULT_DELAY_TICKS);
    }

    public void setDelayTicks(int delayTicks) {
        json.addProperty(RuleFields.DELAY_TICKS, delayTicks);
    }

    public double chance() {
        return doubleParam(RuleFields.CHANCE, DEFAULT_CHANCE);
    }

    public void setChance(double chance) {
        json.addProperty(RuleFields.CHANCE, chance);
    }

    // 效果级条件（可空 = 无条件）
    public ConditionView conditions() {
        return ConditionView.fromJson(json.get(RuleFields.CONDITIONS));
    }

    public boolean hasConditions() {
        return !conditions().isEmpty();
    }

    public void setConditions(ConditionView conditions) {
        if (conditions == null || conditions.isEmpty()) {
            json.remove(RuleFields.CONDITIONS);
        } else {
            json.add(RuleFields.CONDITIONS, conditions.toJson());
        }
    }

    // ===== 类型专属参数 ===== //

    public boolean hasParam(String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    // 参数的文本形态；缺失返回空串（区别于 JSON 里的空字符串）
    public String paramText(String key) {
        if (!hasParam(key) || !json.get(key).isJsonPrimitive()) {
            return "";
        }
        return json.get(key).getAsString();
    }

    public String stringParam(String key, String fallback) {
        String text = paramText(key);
        return text.isEmpty() ? fallback : text;
    }

    public void setStringParam(String key, @Nullable String value) {
        if (value == null || value.isBlank()) {
            json.remove(key);
        } else {
            json.addProperty(key, value.trim());
        }
    }

    public int intParam(String key, int fallback) {
        if (!hasParam(key) || !json.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public void setIntParam(String key, int value) {
        json.addProperty(key, value);
    }

    public double doubleParam(String key, double fallback) {
        if (!hasParam(key) || !json.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return json.get(key).getAsDouble();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public void setDoubleParam(String key, double value) {
        json.addProperty(key, value);
    }

    public boolean booleanParam(String key, boolean fallback) {
        if (!hasParam(key) || !json.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return json.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public void setBooleanParam(String key, boolean value) {
        json.addProperty(key, value);
    }

    public void removeParam(String key) {
        json.remove(key);
    }

    // 序列化回协议 JSON（保留未识别的参数，避免 GUI 丢字段）
    public JsonObject toJson() {
        return json.deepCopy();
    }
}
