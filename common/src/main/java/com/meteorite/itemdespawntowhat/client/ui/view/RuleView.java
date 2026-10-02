package com.meteorite.itemdespawntowhat.client.ui.view;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条规则的视图模型：包裹规则对象协议 JSON，提供字段读写、嵌套视图与变更集构造。
 * 解析入口是 RuleSnapshot 携带的原始 JSON，本层不引用 core/model 的服务端模型类。
 */
public final class RuleView {

    private static final int DEFAULT_TRIGGER_AFTER_SECONDS = 300;
    private static final int DEFAULT_PRIORITY = 0;

    private final JsonObject json;

    private RuleView(JsonObject json) {
        this.json = json;
    }

    // 由协议 JSON 深拷贝构造
    public static RuleView fromJson(JsonObject raw) {
        return new RuleView(raw.deepCopy());
    }

    // 新建一条最小可用规则（单效果由模板填充）
    public static RuleView blank(ResourceLocation id) {
        JsonObject json = new JsonObject();
        json.addProperty(RuleFields.ID, id.toString());
        json.addProperty(RuleFields.ENABLED, true);
        json.addProperty(RuleFields.PRIORITY, DEFAULT_PRIORITY);
        json.addProperty(RuleFields.TRIGGER_AFTER_SECONDS, DEFAULT_TRIGGER_AFTER_SECONDS);
        SourceView source = SourceView.empty();
        source.setItems(List.of());
        json.add(RuleFields.SOURCE, source.toJson());
        json.add(RuleFields.EFFECTS, new JsonArray());
        return new RuleView(json);
    }

    // 从服务端快照读取全部规则视图（兼容 envelope 与裸规则两种形状）
    public static List<RuleView> listFrom(@Nullable RuleSnapshot snapshot) {
        List<RuleView> result = new ArrayList<>();
        for (RuleEntry entry : RuleEntry.listFrom(snapshot)) {
            result.add(entry.rule());
        }
        return result;
    }

    // 从服务端快照读取全部条目（含来源标注与可编辑标记）
    public static List<RuleEntry> entriesFrom(@Nullable RuleSnapshot snapshot) {
        return RuleEntry.listFrom(snapshot);
    }

    // 从原始 JSON 列表读取（供快照之外的自检场景复用）
    public static List<RuleView> listFromJson(@Nullable List<JsonObject> rules) {
        List<RuleView> result = new ArrayList<>();
        if (rules == null) {
            return result;
        }
        for (JsonObject rule : rules) {
            result.add(fromJson(rule));
        }
        return result;
    }

    // ===== 规则级字段 ===== //

    public String id() {
        if (!json.has(RuleFields.ID) || !json.get(RuleFields.ID).isJsonPrimitive()) {
            return "";
        }
        return json.get(RuleFields.ID).getAsString();
    }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            json.remove(RuleFields.ID);
        } else {
            json.addProperty(RuleFields.ID, id.trim());
        }
    }

    public @Nullable ResourceLocation idLocation() {
        return ResourceLocation.tryParse(id());
    }

    public boolean enabled() {
        return !json.has(RuleFields.ENABLED) || json.get(RuleFields.ENABLED).getAsBoolean();
    }

    public void setEnabled(boolean enabled) {
        json.addProperty(RuleFields.ENABLED, enabled);
    }

    public int priority() {
        if (!json.has(RuleFields.PRIORITY) || !json.get(RuleFields.PRIORITY).isJsonPrimitive()) {
            return DEFAULT_PRIORITY;
        }
        try {
            return json.get(RuleFields.PRIORITY).getAsInt();
        } catch (RuntimeException ignored) {
            return DEFAULT_PRIORITY;
        }
    }

    public void setPriority(int priority) {
        json.addProperty(RuleFields.PRIORITY, priority);
    }

    public @Nullable String notes() {
        if (!json.has(RuleFields.NOTES) || !json.get(RuleFields.NOTES).isJsonPrimitive()) {
            return null;
        }
        return json.get(RuleFields.NOTES).getAsString();
    }

    public String notesText() {
        String notes = notes();
        return notes == null ? "" : notes;
    }

    public void setNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            json.remove(RuleFields.NOTES);
        } else {
            json.addProperty(RuleFields.NOTES, notes);
        }
    }

    public int triggerAfterSeconds() {
        if (!json.has(RuleFields.TRIGGER_AFTER_SECONDS)
                || !json.get(RuleFields.TRIGGER_AFTER_SECONDS).isJsonPrimitive()) {
            return DEFAULT_TRIGGER_AFTER_SECONDS;
        }
        try {
            return json.get(RuleFields.TRIGGER_AFTER_SECONDS).getAsInt();
        } catch (RuntimeException ignored) {
            return DEFAULT_TRIGGER_AFTER_SECONDS;
        }
    }

    public void setTriggerAfterSeconds(int seconds) {
        json.addProperty(RuleFields.TRIGGER_AFTER_SECONDS, seconds);
    }

    // ===== 嵌套视图 ===== //

    // 源匹配（绑定回规则 JSON，可写）
    public SourceView source() {
        JsonObject sourceJson;
        if (json.has(RuleFields.SOURCE) && json.get(RuleFields.SOURCE).isJsonObject()) {
            sourceJson = json.getAsJsonObject(RuleFields.SOURCE);
        } else {
            sourceJson = new JsonObject();
            json.add(RuleFields.SOURCE, sourceJson);
        }
        return SourceView.wrap(sourceJson);
    }

    // 条件表达式（解析为独立结构，经 setConditions 写回）
    public ConditionView conditions() {
        return ConditionView.fromJson(json.get(RuleFields.CONDITIONS));
    }

    public void setConditions(@Nullable ConditionView conditions) {
        if (conditions == null || conditions.isEmpty()) {
            json.remove(RuleFields.CONDITIONS);
        } else {
            json.add(RuleFields.CONDITIONS, conditions.toJson());
        }
    }

    // 效果列表（绑定回规则 JSON，可写）
    public List<EffectView> effects() {
        List<EffectView> result = new ArrayList<>();
        if (!json.has(RuleFields.EFFECTS) || !json.get(RuleFields.EFFECTS).isJsonArray()) {
            return result;
        }
        for (JsonElement element : json.getAsJsonArray(RuleFields.EFFECTS)) {
            if (element.isJsonObject()) {
                result.add(EffectView.wrap(element.getAsJsonObject()));
            }
        }
        return result;
    }

    public void setEffects(List<EffectView> effects) {
        JsonArray array = new JsonArray();
        if (effects != null) {
            for (EffectView effect : effects) {
                array.add(effect.toJson());
            }
        }
        json.add(RuleFields.EFFECTS, array);
    }

    public void addEffect(EffectView effect) {
        List<EffectView> all = effects();
        all.add(effect);
        setEffects(all);
    }

    // 首个效果（单效果编辑的最小实现边界）
    public @Nullable EffectView primaryEffect() {
        List<EffectView> all = effects();
        return all.isEmpty() ? null : all.get(0);
    }

    // 首个效果的类型
    public @Nullable ResourceLocation primaryEffectType() {
        EffectView effect = primaryEffect();
        return effect == null ? null : effect.type();
    }

    // 多效果规则超出最小实现边界，只读展示
    public boolean isMultiEffect() {
        return effects().size() > 1;
    }

    public boolean hasEffect() {
        return !effects().isEmpty();
    }

    // ===== 序列化与变更集 ===== //

    public JsonObject toJson() {
        return json.deepCopy();
    }

    public RuleView copy() {
        return fromJson(json);
    }

    // 构造 upsert 变更（id 非法时抛异常，由界面在保存前校验拦截）
    public RuleEdit toUpsertEdit() {
        ResourceLocation id = idLocation();
        if (id == null) {
            throw new IllegalStateException("规则 id 非法，无法构造变更: " + id());
        }
        return new RuleEdit(id, RuleEdit.Action.UPSERT, toJson());
    }
}
