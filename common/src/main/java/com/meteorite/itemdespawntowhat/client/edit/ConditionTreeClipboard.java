package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionLimits;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 剪贴板子树列表是编辑器交换格式；不改变正式条件配置，也不包含会话输入缓冲。 */
public final class ConditionTreeClipboard {
    public record ReadResult(List<ConditionNode> nodes, @Nullable Component error) {
        public ReadResult { nodes = List.copyOf(nodes); }
    }
    private record Pending(JsonElement raw, int depth) {}
    private final ConditionDraftCodec codec;

    public ConditionTreeClipboard(TypeRegistry<ConditionType<?>> types) { codec = new ConditionDraftCodec(types); }

    public ReadResult read(String text) {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            List<JsonElement> raw = new ArrayList<>();
            if (parsed instanceof JsonArray array) array.forEach(raw::add); else raw.add(parsed);
            if (raw.isEmpty()) return invalid();
            Component limit = checkLimits(raw);
            if (limit != null) return new ReadResult(List.of(), limit);
            List<ConditionNode> nodes = new ArrayList<>();
            for (JsonElement entry : raw) {
                ConditionExpression expression = codec.decode(entry);
                if (expression == null || expression.root() == null) return invalid();
                nodes.add(expression.root());
            }
            return new ReadResult(nodes, null);
        } catch (JsonParseException | IllegalStateException invalidJson) {
            return invalid();
        }
    }

    private static ReadResult invalid() {
        return new ReadResult(List.of(), Component.translatable("gui.itemdespawntowhat.edit.tree.clipboard_invalid"));
    }

    // 在递归条件 Codec 前按表达式边界检查树层级，不遍历叶内部的任意参数 JSON。
    private static @Nullable Component checkLimits(List<JsonElement> roots) {
        ArrayDeque<Pending> pending = new ArrayDeque<>();
        roots.forEach(raw -> pending.addLast(new Pending(raw, 1)));
        int nodes = 0;
        int leaves = 0;
        while (!pending.isEmpty()) {
            Pending current = pending.removeFirst();
            if (++nodes > ConditionLimits.MAX_NODES) return Component.translatable(
                    "gui.itemdespawntowhat.edit.issue.too_many_nodes", nodes, ConditionLimits.MAX_NODES);
            if (current.depth() > ConditionLimits.MAX_DEPTH) return Component.translatable(
                    "gui.itemdespawntowhat.edit.issue.too_deep", current.depth(), ConditionLimits.MAX_DEPTH);
            if (!current.raw().isJsonObject()) continue;
            var object = current.raw().getAsJsonObject();
            JsonElement op = object.get(RuleFields.OP);
            if (op == null || !op.isJsonPrimitive() || !op.getAsJsonPrimitive().isString()) continue;
            if (RuleFields.OP_LEAF.equals(op.getAsString())) {
                if (++leaves > ConditionLimits.MAX_LEAVES) return Component.translatable(
                        "gui.itemdespawntowhat.edit.issue.too_many_leaves", leaves, ConditionLimits.MAX_LEAVES);
            } else if (RuleFields.OP_INVERTED.equals(op.getAsString())) {
                JsonElement child = object.get(RuleFields.TERM);
                if (child != null) pending.addLast(new Pending(child, current.depth() + 1));
            } else if (RuleFields.OP_ALL_OF.equals(op.getAsString()) || RuleFields.OP_ANY_OF.equals(op.getAsString())) {
                if (object.get(RuleFields.TERMS) instanceof JsonArray children) {
                    for (JsonElement child : children) pending.addLast(new Pending(child, current.depth() + 1));
                }
            }
        }
        return null;
    }
}
