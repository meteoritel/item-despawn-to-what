package com.meteorite.itemdespawntowhat.client.ui.condition;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Function;

/**
 * 描述客户端可展示和编辑的一种条件类型。
 */
public record ClientConditionTypeDefinition(
        ResourceLocation id,
        String displayNameKey,
        Function<Font, ConditionParameterInput> parameterInputFactory
) {
    public ClientConditionTypeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(parameterInputFactory, "parameterInputFactory");
    }

    public ClientConditionTypeDefinition(ResourceLocation id, String displayNameKey) {
        this(id, displayNameKey, ConditionJsonEditor::new);
    }

    public Component displayName() {
        return Component.translatable(displayNameKey);
    }

    public ConditionParameterInput createParameterInput(Font font) {
        return parameterInputFactory.apply(font);
    }
}
