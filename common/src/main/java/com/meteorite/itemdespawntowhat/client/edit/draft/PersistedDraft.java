package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.client.edit.EditorChangeSet;
import com.meteorite.itemdespawntowhat.client.edit.RuleDraft;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import org.jetbrains.annotations.Nullable;

/**
 * 未应用草稿的落盘载体：一个编辑目标一份。
 * <p>除了草稿内容本身，还记录建立草稿时的服务端基线与修订号，用于「整条规则比较」：
 * 重启或断线重连后用服务端最新快照与 {@link #baseline()} 比较，
 * 不一致即视为外部变更冲突，交由玩家选择保留草稿还是采用服务端版本。
 * <p>字段全部可缺省（缺省即按 false/0/null 处理），损坏的文件由 {@link #fromJson(JsonObject)}
 * 返回 null，由调用方跳过而不是让界面崩掉。
 * <p>两个 JSON 子树是「构造时交出所有权」的不可变快照：构造方必须传入自己不会再修改的对象
 * （{@link RuleDraft#toJson()} 或 {@link JsonObject#deepCopy()} 的返回值，以及解析出的独立树）。
 * 因此本记录可以安全地跨线程交给写入者序列化，序列化时不再重复整树拷贝。
 * <p>只读条目的内容属沙盒，不落盘；但它仍可能需要保留本服启停意图，
 * 因此 {@link #enableIntent()} 独立于草稿内容存在：内容为空对象时表示这条记录只承载启停意图。
 */
public record PersistedDraft(
        String targetId,
        boolean created,
        boolean deleted,
        @Nullable RuleEdit.Action enableIntent,
        @Nullable JsonObject baseline,
        JsonObject draft,
        int baseVersion,
        int baseContextRevision,
        long savedAt) {

    // 落盘格式版本：2 起记录本服启停意图；将来结构变更时可据此迁移或丢弃
    public static final int FORMAT_VERSION = 2;

    // 建立草稿时的服务端基线（动态值快照）；缺省返回 null
    public @Nullable RuleValue baselineValue() {
        return baseline == null ? null : RuleDraft.of(baseline).root();
    }

    // 草稿内容（动态值快照）
    public RuleValue draftValue() {
        return RuleDraft.of(draft).root();
    }

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
        json.addProperty("base_version", baseVersion);
        json.addProperty("base_context_revision", baseContextRevision);
        json.addProperty("saved_at", savedAt);
        if (baseline != null) {
            json.add("baseline", baseline);
        }
        json.add("draft", draft);
        return json;
    }

    // 解析落盘 JSON；缺少必要字段时返回 null
    public static @Nullable PersistedDraft fromJson(JsonObject json) {
        if (json == null || !json.has("target_id") || !json.has("draft")) {
            return null;
        }
        JsonElement draft = json.get("draft");
        if (!draft.isJsonObject()) {
            return null;
        }
        JsonObject baseline = json.has("baseline") && json.get("baseline").isJsonObject()
                ? json.getAsJsonObject("baseline")
                : null;
        return new PersistedDraft(
                json.get("target_id").getAsString(),
                readBoolean(json, "created"),
                readBoolean(json, "deleted"),
                readEnableIntent(json),
                baseline,
                draft.getAsJsonObject(),
                readInt(json, "base_version"),
                readInt(json, "base_context_revision"),
                readSavedAt(json));
    }

    // 兼容旧文件：非布尔/非数字一律按缺省值处理
    private static boolean readBoolean(JsonObject json, String name) {
        return json.has(name) && json.get(name).isJsonPrimitive() && json.get(name).getAsJsonPrimitive().isBoolean()
                && json.get(name).getAsBoolean();
    }

    // 缺失或无法识别的启停意图按「无意图」处理，旧版本草稿因此可以直接沿用
    private static @Nullable RuleEdit.Action readEnableIntent(JsonObject json) {
        if (!json.has("enable_intent") || !json.get("enable_intent").isJsonPrimitive()
                || !json.get("enable_intent").getAsJsonPrimitive().isString()) {
            return null;
        }
        RuleEdit.Action action = RuleEdit.Action.fromId(json.get("enable_intent").getAsString());
        return EditorChangeSet.isStateAction(action) ? action : null;
    }

    private static int readInt(JsonObject json, String name) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive() || !json.get(name).getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return json.get(name).getAsInt();
    }

    private static long readSavedAt(JsonObject json) {
        if (!json.has("saved_at") || !json.get("saved_at").isJsonPrimitive()
                || !json.get("saved_at").getAsJsonPrimitive().isNumber()) {
            return 0L;
        }
        return json.get("saved_at").getAsLong();
    }
}
