package com.meteorite.itemdespawntowhat.ui.validator;

import com.meteorite.itemdespawntowhat.ui.FieldValidator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// 校验值为合法的非空 ResourceLocation（如 minecraft:stone）
public class ResourceLocationValidator implements FieldValidator {
    private static final ResourceLocationValidator INSTANCE = new ResourceLocationValidator();

    public static ResourceLocationValidator get() {
        return INSTANCE;
    }

    @Override
    public boolean validate(String value) {
        if (value == null || value.isEmpty()) return false;
        return ResourceLocation.tryParse(value) != null;
    }

    @Override
    public Component getErrorMessage() {
        return Component.translatable("gui.itemdespawntowhat.edit.save_error");
    }
}
