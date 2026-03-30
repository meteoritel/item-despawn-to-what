package com.meteorite.itemdespawntowhat.ui.validator;

import com.meteorite.itemdespawntowhat.ui.FieldValidator;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.network.chat.Component;

// 校验 itemId：支持 #tag:id 格式，排除 minecraft:air
public class ResourceLocationValidator implements FieldValidator {
    private static final ResourceLocationValidator INSTANCE = new ResourceLocationValidator();

    public static ResourceLocationValidator get() {
        return INSTANCE;
    }

    @Override
    public boolean validate(String value) {
        return IdValidator.isValidItemId(value);
    }

    @Override
    public Component getErrorMessage() {
        return Component.translatable("gui.itemdespawntowhat.edit.save_error");
    }
}
