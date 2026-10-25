package com.meteorite.itemdespawntowhat.core.network.protocol;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 一条规则变更：upsert 携带完整规则 JSON（与文件中的规则对象同形），其余动作只带 id。
 * 内容动作（upsert/delete）改覆盖层内容并推进内容修订号；启用状态动作（enable/disable/reset）
 * 只改本服启用状态层，不改规则文件、不推进内容修订号，因此不参与草稿冲突。
 * 传输形状与覆盖层文件中的规则对象保持一致，便于服务端直接落盘。
 */
public record RuleEdit(ResourceLocation id, Action action, @Nullable JsonObject rule) {

    // 变更类型
    public enum Action {
        // 新增或整体覆盖同 id 规则（内容动作）
        UPSERT,
        // 删除覆盖层该 id 的普通条目（内容动作）
        DELETE,
        // 本服启用（状态动作）
        ENABLE,
        // 本服停用（状态动作）
        DISABLE,
        // 删除本服覆盖记录、恢复默认启用（状态动作）
        RESET;

        // JSON 取值（小写下划线）
        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        // 由 JSON 取值解析，未知取值返回 null
        public static @Nullable Action fromId(String raw) {
            if (raw == null) {
                return null;
            }
            for (Action action : values()) {
                if (action.id().equalsIgnoreCase(raw.trim())) {
                    return action;
                }
            }
            return null;
        }
    }

    public RuleEdit {
        if (id == null) {
            throw new IllegalArgumentException("规则变更的 id 不能为空");
        }
        if (action == null) {
            throw new IllegalArgumentException("规则变更的 action 不能为空");
        }
        if (action == Action.UPSERT && rule == null) {
            throw new IllegalArgumentException("upsert 变更必须携带规则内容: " + id);
        }
        if (action != Action.UPSERT && rule != null) {
            // 非内容动作只认 id：多余规则内容会让「改状态」与「改内容」边界失效
            throw new IllegalArgumentException(action.id() + " 变更不能携带规则内容: " + id);
        }
    }

    // 是否为改覆盖层内容的动作（决定是否参与内容修订号与草稿冲突）
    public boolean contentEdit() {
        return action == Action.UPSERT || action == Action.DELETE;
    }

    // 是否为改本服启用状态的动作
    public boolean enableEdit() {
        return action == Action.ENABLE || action == Action.DISABLE || action == Action.RESET;
    }

    // 序列化为协议 JSON
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("action", action.id());
        if (rule != null) {
            json.add("rule", rule.deepCopy());
        }
        return json;
    }

    // 从协议 JSON 解析；字段缺失或非法时抛 IllegalArgumentException（由调用方转成可读错误）
    public static RuleEdit fromJson(JsonObject json) {
        if (!json.has("id") || !json.has("action")) {
            throw new IllegalArgumentException("规则变更缺少 id 或 action 字段");
        }
        ResourceLocation id = ResourceLocation.tryParse(json.get("id").getAsString());
        if (id == null) {
            throw new IllegalArgumentException("规则变更的 id 非法: " + json.get("id").getAsString());
        }
        Action action = Action.fromId(json.get("action").getAsString());
        if (action == null) {
            throw new IllegalArgumentException("规则变更的 action 非法: " + json.get("action").getAsString());
        }
        JsonObject rule = json.has("rule") && json.get("rule").isJsonObject()
                ? json.getAsJsonObject("rule").deepCopy()
                : null;
        return new RuleEdit(id, action, rule);
    }
}
