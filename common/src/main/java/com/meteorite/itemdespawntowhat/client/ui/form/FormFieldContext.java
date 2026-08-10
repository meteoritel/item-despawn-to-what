package com.meteorite.itemdespawntowhat.client.ui.form;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 向表单定义工厂提供统一尺寸、文本与基础控件构造能力。
 */
public record FormFieldContext(Font font) {
    public static final String LABEL_PREFIX = "gui.itemdespawntowhat.edit.";
    public static final int BOX_WIDTH = 240;
    public static final int BUTTON_HEIGHT = 18;

    public Component label(String suffix) {
        return Component.translatable(LABEL_PREFIX + suffix);
    }

    public EditBox textBox() {
        EditBox box = new EditBox(font, 0, 0, BOX_WIDTH, BUTTON_HEIGHT, Component.empty());
        box.setMaxLength(256);
        return box;
    }

    public EditBox integerBox() {
        EditBox box = textBox();
        box.setFilter(value -> value.matches("-?\\d*"));
        return box;
    }

    public EditBox positiveIntegerBox() {
        EditBox box = textBox();
        box.setFilter(value -> value.matches("\\d*"));
        return box;
    }

    public EditBox positiveDecimalBox() {
        EditBox box = textBox();
        box.setFilter(value -> value.matches("\\d*\\.?\\d*"));
        return box;
    }

    public CycleButton<Boolean> booleanButton(String labelSuffix) {
        return CycleButton.booleanBuilder(label("on"), label("off"))
                .withInitialValue(false)
                .create(0, 0, BOX_WIDTH, BUTTON_HEIGHT, label(labelSuffix));
    }

    public <E> CycleButton<E> enumButton(
            String labelSuffix,
            List<E> values,
            E initialValue,
            Function<E, Component> valueLabel) {
        return CycleButton.<E>builder(valueLabel)
                .withValues(values)
                .withInitialValue(initialValue)
                .create(0, 0, BOX_WIDTH, BUTTON_HEIGHT, label(labelSuffix));
    }
}
