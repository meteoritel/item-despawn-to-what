package com.meteorite.itemdespawntowhat.client.ui.form;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;

/**
 * 统一封装文本框、选择按钮和复合控件的值与焦点入口。
 */
public interface FormFieldInput<V> {
    AbstractWidget widget();

    V value();

    void setValue(V value);

    void clear();

    default List<EditBox> editBoxes() {
        return List.of();
    }
}
