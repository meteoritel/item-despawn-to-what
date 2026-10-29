package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.EditorChangeSet;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * 未应用草稿的落盘载体：一个编辑目标一份。
 * <p>除了草稿内容本身，还记录建立草稿时的服务端基线，用于「整条规则比较」：
 * 重启或断线重连后用服务端最新快照与 {@link #baseline()} 比较，
 * 不一致即视为外部变更冲突，交由玩家选择保留草稿还是采用服务端版本。
 * <p>只读取当前格式版本；format、target_id、created、deleted、saved_at、draft 与 pending_inputs
 * 必须存在。未完成文本按路径独立保存，不混入规则体；解析失败由仓库记录并跳过该文件。
 * <p>两个 JSON 子树是「构造时交出所有权」的不可变快照：构造方必须传入自己不会再修改的对象
 * （{@link RuleDraft#toJson()} 或 {@link JsonObject#deepCopy()} 的返回值，以及解析出的独立树）。
 * 因此本记录可以安全地跨线程交给写入者序列化，序列化时不再重复整树拷贝。
 * <p>只读条目的内容属沙盒，不落盘；但它仍可能需要保留本服启停意图，
 * 因此 {@link #enableIntent()} 独立于草稿内容存在：内容为空对象时表示这条记录只承载启停意图。
 */
record PersistedDraft(
        String targetId,
        boolean created,
        boolean deleted,
        @Nullable RuleEdit.Action enableIntent,
        @Nullable JsonObject baseline,
        JsonObject draft,
        Map<String, List<String>> pendingInputs,
        long savedAt) {

    // 规则体与未完成输入分别保存，恢复时由表单重新校验文本。
    public static final int FORMAT_VERSION = 3;
    private static final String PENDING_INPUTS = "pending_inputs";

    // 序列化为落盘 JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT_VERSION);
        json.addProperty("target_id", targetId);
        json.addProperty("created", created);
        json.addProperty("deleted", deleted);
        if (enableIntent != null) {
            json.addProperty("enable_intent", enableIntent.id());
        }
        json.addProperty("saved_at", savedAt);
        if (baseline != null) {
            json.add("baseline", baseline);
        }
        json.add("draft", draft);
        JsonObject inputs = new JsonObject();
        pendingInputs.forEach((path, text) -> {
            JsonArray values = new JsonArray();
            text.forEach(values::add);
            inputs.add(path, values);
        });
        json.add(PENDING_INPUTS, inputs);
        return json;
    }

    // 只解析当前落盘格式；版本或字段不符时交由仓库记录并跳过。
    public static PersistedDraft fromJson(JsonObject json) {
        if (json.get("format").getAsInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("草稿格式版本不匹配：" + json.get("format"));
        }
        return new PersistedDraft(
                json.get("target_id").getAsString(),
                json.get("created").getAsBoolean(),
                json.get("deleted").getAsBoolean(),
                readEnableIntent(json),
                json.has("baseline") ? json.getAsJsonObject("baseline") : null,
                json.get("draft").getAsJsonObject(),
                readPendingInputs(json),
                json.get("saved_at").getAsLong());
    }

    // 输入文本按控件顺序保存为字符串数组，损坏形状交由仓库报告。
    private static Map<String, List<String>> readPendingInputs(JsonObject json) {
        Map<String, List<String>> inputs = new LinkedHashMap<>();
        for (var entry : json.getAsJsonObject(PENDING_INPUTS).entrySet()) {
            List<String> text = new ArrayList<>();
            for (JsonElement value : entry.getValue().getAsJsonArray()) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("草稿输入文本必须是字符串：" + entry.getKey());
                }
                text.add(value.getAsString());
            }
            inputs.put(entry.getKey(), List.copyOf(text));
        }
        return Map.copyOf(inputs);
    }

    // 启停意图可选；存在时必须是启停动作。
    private static @Nullable RuleEdit.Action readEnableIntent(JsonObject json) {
        if (!json.has("enable_intent")) return null;
        RuleEdit.Action action = RuleEdit.Action.fromId(json.get("enable_intent").getAsString());
        if (!EditorChangeSet.isStateAction(action)) {
            throw new IllegalArgumentException("草稿启停意图无效：" + json.get("enable_intent"));
        }
        return action;
    }
}
