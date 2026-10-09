package com.meteorite.itemdespawntowhat.client.edit;

import com.google.gson.JsonElement;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiTreeCodec;
import com.meteorite.itemdespawntowhat.core.api.TypeRegistry;
import com.meteorite.itemdespawntowhat.core.model.ConditionExpression;
import com.meteorite.itemdespawntowhat.core.model.ConditionNode;
import com.meteorite.itemdespawntowhat.core.model.ConditionType;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 条件 JSON 的宿主适配；未修改的树保留已接受原文中的全部字段。 */
public final class ConditionTreeJsonCodec implements UiTreeCodec<ConditionNode, JsonElement> {
    private final ConditionDraftCodec codec;
    private @Nullable ConditionNode acceptedRoot;
    private @Nullable JsonElement acceptedJson;
    private boolean decoded;

    public ConditionTreeJsonCodec(TypeRegistry<ConditionType<?>> types) {
        codec = new ConditionDraftCodec(types);
    }

    @Override public DecodeResult<ConditionNode> decode(@Nullable JsonElement source) {
        ConditionExpression expression = codec.decode(source);
        decoded = expression != null;
        if (expression == null) return new DecodeResult<>(null,
                Component.translatable("gui.itemdespawntowhat.edit.tree.source_invalid"));
        acceptedRoot = expression.root();
        acceptedJson = source == null || source.isJsonNull() ? null : source.deepCopy();
        return new DecodeResult<>(acceptedRoot, null);
    }

    @Override public @Nullable JsonElement encode(@Nullable ConditionNode root) {
        if (decoded && Objects.equals(acceptedRoot, root)) {
            return acceptedJson == null ? null : acceptedJson.deepCopy();
        }
        return codec.encode(root == null ? ConditionExpression.EMPTY : new ConditionExpression(root));
    }
}
