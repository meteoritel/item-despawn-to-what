package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEdit;
import com.meteorite.itemdespawntowhat.core.service.edit.RuleEditChangeSet;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 客户端变更集装配器：把若干草稿装配成一次 {@link RuleEditChangeSet} 提交。
 * 同一规则 id 只保留最后一次编辑；id 缺少命名空间时按本模组命名空间补齐。
 * <p>内容编辑与启停动作分别去重：只读条目的内容不进变更集，但它的本服启停意图要提交，
 * 而可写条目可能同时带内容改动与启停改动，因此两者不能挤在同一个 id 槽位上。
 */
public final class EditorChangeSet {

    // 本模组规则 id 的短命名空间
    public static final String OWN_NAMESPACE = Constants.ID_NAMESPACE;

    // 提交依据的服务端版本
    private final int expectedVersion;
    // 按 id 去重的编辑列表（保持提交顺序）
    private final Map<String, RuleEdit> edits = new LinkedHashMap<>();
    // 按 id 去重的启停动作（与内容编辑分开，允许同一 id 各一条）
    private final Map<String, RuleEdit> stateEdits = new LinkedHashMap<>();

    // 构造：expectedVersion 取自快照版本
    public EditorChangeSet(int expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public int expectedVersion() {
        return this.expectedVersion;
    }

    public boolean isEmpty() {
        return this.edits.isEmpty() && this.stateEdits.isEmpty();
    }

    // 编辑条数（内容动作与启停动作合计）
    public int size() {
        return this.edits.size() + this.stateEdits.size();
    }

    // 覆盖写入一条草稿（含删除标记与停用标记）
    public EditorChangeSet upsert(RuleDraft draft) {
        return upsert(draft.id(), draft.toJson());
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

    /**
     * 追加一条启停动作。
     * <p>服务端处理混合批次时先写内容、再落启停状态，所以同一规则可以同时出现在两段里。
     *
     * @param ruleId 规则 id，缺命名空间时补本模组命名空间
     * @param action enable/disable/reset 之一
     * @return this
     */
    public EditorChangeSet state(String ruleId, RuleEdit.Action action) {
        Objects.requireNonNull(action, "action");
        if (!isStateAction(action)) {
            throw new IllegalArgumentException("not an enable action: " + action);
        }
        ResourceLocation id = ruleId(ruleId);
        if (id != null) {
            this.stateEdits.put(id.toString(), new RuleEdit(id, action, null));
        }
        return this;
    }

    // 序列化为提交 JSON；无编辑时返回 null
    public @Nullable String serialize() {
        if (isEmpty()) {
            return null;
        }
        List<RuleEdit> all = new ArrayList<>(this.edits.values());
        all.addAll(this.stateEdits.values());
        return new RuleEditChangeSet(this.expectedVersion, all).serialize();
    }

    /**
     * 是否为启停类动作（enable/disable/reset）：内容动作既不能当启停提交，也不能当意图恢复。
     *
     * @param action 动作，允许为 null
     * @return true 表示属于启停三类之一
     */
    public static boolean isStateAction(@Nullable RuleEdit.Action action) {
        return action == RuleEdit.Action.ENABLE || action == RuleEdit.Action.DISABLE
                || action == RuleEdit.Action.RESET;
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
