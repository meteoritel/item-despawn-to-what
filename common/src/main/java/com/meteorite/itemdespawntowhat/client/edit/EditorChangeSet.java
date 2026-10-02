package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/***
 * 客户端变更集装配器：把若干草稿装配成一次 {@link RuleEditChangeSet} 提交（契约 §3.3）。
 * 同一规则 id 只保留最后一次编辑；id 缺少命名空间时按本模组命名空间补齐。
 */
public final class EditorChangeSet {

    // 本模组命名空间
    public static final String OWN_NAMESPACE = "itemdespawntowhat";

    // 提交依据的服务端版本
    private final int expectedVersion;
    // 按 id 去重的编辑列表（保持提交顺序）
    private final Map<String, RuleEdit> edits = new LinkedHashMap<>();

    // 构造：expectedVersion 取自快照版本
    public EditorChangeSet(int expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    // 提交依据的服务端版本
    public int expectedVersion() {
        return this.expectedVersion;
    }

    // 是否没有任何编辑
    public boolean isEmpty() {
        return this.edits.isEmpty();
    }

    // 编辑条数
    public int size() {
        return this.edits.size();
    }

    // 覆盖写入一条草稿（含删除标记与停用标记）
    public EditorChangeSet upsert(RuleDraft draft) {
        return upsert(draft.id(), draft.toOverlayJson());
    }

    // 覆盖写入一条规则体
    public EditorChangeSet upsert(String ruleId, JsonObject rule) {
        ResourceLocation id = ruleId(ruleId);
        if (id == null) {
            return this;
        }
        JsonObject body = rule.deepCopy();
        body.addProperty(RuleFields.ID, id.toString());
        this.edits.put(id.toString(), new RuleEdit(id, RuleEdit.Action.UPSERT, body));
        return this;
    }

    // 删除覆盖层中的一条规则（仅对覆盖层条目有效）
    public EditorChangeSet delete(String ruleId) {
        ResourceLocation id = ruleId(ruleId);
        if (id != null) {
            this.edits.put(id.toString(), new RuleEdit(id, RuleEdit.Action.DELETE, null));
        }
        return this;
    }

    // 序列化为提交 JSON；无编辑时返回 null
    public @Nullable String serialize() {
        if (this.edits.isEmpty()) {
            return null;
        }
        return new RuleEditChangeSet(this.expectedVersion, List.copyOf(this.edits.values())).serialize();
    }

    // 解析规则 id：缺少命名空间时补本模组命名空间；非法返回 null
    public static @Nullable ResourceLocation ruleId(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String full = trimmed.indexOf(':') >= 0 ? trimmed : OWN_NAMESPACE + ":" + trimmed;
        return ResourceLocation.tryParse(full);
    }
}
