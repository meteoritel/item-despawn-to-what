package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import com.meteorite.itemdespawntowhat.core.model.RuleCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端条件草稿编解码：允许空组合与缺少子项的 NOT，叶参数仍通过正式类型 Codec 处理。
 * 正式配置的解码与保存规则由服务端保持严格；无根表达式由宿主省略 conditions 字段。
 */
public final class ConditionDraftCodec {

    private static final Set<String> GROUP_FIELDS = Set.of(RuleFields.OP, RuleFields.TERMS);
    private static final Set<String> INVERTED_FIELDS = Set.of(RuleFields.OP, RuleFields.TERM);
    private final Codec<ConditionExpression> strictCodec;

    public ConditionDraftCodec(TypeRegistry<ConditionType<?>> conditionTypes) {
        strictCodec = RuleCodecs.conditionExpressionCodec(conditionTypes);
    }

    public @Nullable ConditionExpression decode(@Nullable JsonElement raw) {
        if (raw == null || raw.isJsonNull()) {
            return ConditionExpression.EMPTY;
        }
        ConditionNode root = decodeNode(raw);
        return root == null ? null : new ConditionExpression(root);
    }

    public @Nullable JsonElement encode(ConditionExpression expression) {
        return expression.isEmpty() ? null : encodeNode(expression.root());
    }

    private @Nullable ConditionNode decodeNode(JsonElement raw) {
        if (!(raw instanceof JsonObject object)) {
            return null;
        }
        JsonElement rawOp = object.get(RuleFields.OP);
        if (rawOp == null || !rawOp.isJsonPrimitive() || !rawOp.getAsJsonPrimitive().isString()) {
            return null;
        }
        return switch (rawOp.getAsString()) {
            case RuleFields.OP_ALL_OF -> decodeGroup(object, true);
            case RuleFields.OP_ANY_OF -> decodeGroup(object, false);
            case RuleFields.OP_INVERTED -> decodeInverted(object);
            case RuleFields.OP_LEAF -> {
                ConditionExpression expression = strictCodec.parse(JsonOps.INSTANCE, object).result().orElse(null);
                yield expression == null ? null : expression.root();
            }
            default -> null;
        };
    }

    private @Nullable ConditionNode decodeGroup(JsonObject object, boolean allOf) {
        if (!GROUP_FIELDS.containsAll(object.keySet()) || !(object.get(RuleFields.TERMS) instanceof JsonArray array)) {
            return null;
        }
        List<ConditionNode> children = new ArrayList<>(array.size());
        for (JsonElement rawChild : array) {
            ConditionNode child = decodeNode(rawChild);
            if (child == null) {
                return null;
            }
            children.add(child);
        }
        return allOf ? new ConditionNode.AllOf(children) : new ConditionNode.AnyOf(children);
    }

    private @Nullable ConditionNode decodeInverted(JsonObject object) {
        if (!INVERTED_FIELDS.containsAll(object.keySet())) {
            return null;
        }
        if (!object.has(RuleFields.TERM)) {
            return new ConditionNode.Inverted(null);
        }
        ConditionNode child = decodeNode(object.get(RuleFields.TERM));
        return child == null ? null : new ConditionNode.Inverted(child);
    }

    private @Nullable JsonElement encodeNode(@Nullable ConditionNode node) {
        if (node instanceof ConditionNode.Leaf) {
            return strictCodec.encodeStart(JsonOps.INSTANCE, new ConditionExpression(node)).result().orElse(null);
        }
        JsonObject object = new JsonObject();
        if (node instanceof ConditionNode.Inverted inverted) {
            object.addProperty(RuleFields.OP, RuleFields.OP_INVERTED);
            if (inverted.term() != null) {
                JsonElement child = encodeNode(inverted.term());
                if (child == null) return null;
                object.add(RuleFields.TERM, child);
            }
            return object;
        }
        List<ConditionNode> children;
        if (node instanceof ConditionNode.AllOf allOf) {
            object.addProperty(RuleFields.OP, RuleFields.OP_ALL_OF);
            children = allOf.terms();
        } else if (node instanceof ConditionNode.AnyOf anyOf) {
            object.addProperty(RuleFields.OP, RuleFields.OP_ANY_OF);
            children = anyOf.terms();
        } else {
            return null;
        }
        JsonArray terms = new JsonArray();
        for (ConditionNode child : children) {
            JsonElement encoded = encodeNode(child);
            if (encoded == null) return null;
            terms.add(encoded);
        }
        object.add(RuleFields.TERMS, terms);
        return object;
    }
}
