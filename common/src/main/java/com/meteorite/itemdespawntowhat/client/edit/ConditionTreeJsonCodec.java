package com.meteorite.itemdespawntowhat.client.edit;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeCodec;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 条件 JSON 的宿主适配；未修改的树保留已接受原文中的全部字段。 */
public final class ConditionTreeJsonCodec implements UiTreeCodec<ConditionNode, RuleValue> {
    private final TypeRegistry<ConditionType<?>> types;
    private @Nullable ConditionNode acceptedRoot;
    private @Nullable RuleValue acceptedJson;
    private boolean decoded;

    public ConditionTreeJsonCodec(TypeRegistry<ConditionType<?>> types) {
        this.types = types;
    }

    @Override public DecodeResult<ConditionNode> decode(@Nullable RuleValue source) {
        ConditionExpression expression = RuleDraft.decodeConditions(source, types);
        decoded = expression != null;
        if (expression == null) return new DecodeResult<>(null,
                Component.translatable("gui.itemdespawntowhat.edit.tree.source_invalid"));
        acceptedRoot = expression.root();
        acceptedJson = source == null || source.isNull() ? null : source.deepCopy();
        return new DecodeResult<>(acceptedRoot, null);
    }

    @Override public @Nullable RuleValue encode(@Nullable ConditionNode root) {
        if (decoded && Objects.equals(acceptedRoot, root)) {
            return acceptedJson == null ? null : acceptedJson.deepCopy();
        }
        return RuleDraft.encodeConditionsValue(root == null ? ConditionExpression.EMPTY : new ConditionExpression(root), types);
    }
}
