package com.meteorite.itemdespawntowhat.client.edit.draft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/***
 * 未应用草稿的落盘载体：一个编辑目标一份。
 * <p>除了草稿内容本身，还记录建立草稿时的服务端基线与修订号，用于任务书交付项 4
 * 的「整条规则比较」：重启或断线重连后用服务端最新快照与 {@link #baseline()} 比较，
 * 不一致即视为外部变更冲突，交由玩家选择保留草稿还是采用服务端版本。
 * <p>字段全部可缺省（缺省即按 false/0/null 处理），损坏的文件由 {@link #fromJson(JsonObject)}
 * 返回 null，由调用方跳过而不是让界面崩掉。
 */
public record PersistedDraft(
        String targetId,
        boolean created,
        boolean restore,
        boolean deleted,
        @Nullable JsonObject baseline,
        JsonObject draft,
        int baseVersion,
        int baseContextRevision,
        long savedAt) {

    // 落盘格式版本：将来结构变更时可据此迁移或丢弃
    public static final int FORMAT_VERSION = 1;

    // 序列化为落盘 JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT_VERSION);
        json.addProperty("target_id", targetId);
        json.addProperty("created", created);
        json.addProperty("restore", restore);
        json.addProperty("deleted", deleted);
        json.addProperty("base_version", baseVersion);
        json.addProperty("base_context_revision", baseContextRevision);
        json.addProperty("saved_at", savedAt);
        if (baseline != null) {
            json.add("baseline", baseline.deepCopy());
        }
        json.add("draft", draft.deepCopy());
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
                readBoolean(json, "restore"),
                readBoolean(json, "deleted"),
                baseline,
                draft.getAsJsonObject(),
                readInt(json, "base_version"),
                readInt(json, "base_context_revision"),
                readLong(json, "saved_at"));
    }

    // 兼容旧文件：非布尔/非数字一律按缺省值处理
    private static boolean readBoolean(JsonObject json, String name) {
        return json.has(name) && json.get(name).isJsonPrimitive() && json.get(name).getAsJsonPrimitive().isBoolean()
                && json.get(name).getAsBoolean();
    }

    private static int readInt(JsonObject json, String name) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive() || !json.get(name).getAsJsonPrimitive().isNumber()) {
            return 0;
        }
        return json.get(name).getAsInt();
    }

    private static long readLong(JsonObject json, String name) {
        if (!json.has(name) || !json.get(name).isJsonPrimitive() || !json.get(name).getAsJsonPrimitive().isNumber()) {
            return 0L;
        }
        return json.get(name).getAsLong();
    }
}
