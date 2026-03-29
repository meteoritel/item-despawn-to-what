package com.meteorite.itemdespawntowhat.ui.validator;

import com.meteorite.itemdespawntowhat.ui.FieldValidator;
import net.minecraft.network.chat.Component;

// 校验值不为空
public class NonEmptyValidator implements FieldValidator {
    private static final NonEmptyValidator INSTANCE = new NonEmptyValidator();

    public static NonEmptyValidator get() {
        return INSTANCE;
    }

    @Override
    public boolean validate(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public Component getErrorMessage() {
        return Component.translatable("gui.itemdespawntowhat.edit.save_error");
    }
}
