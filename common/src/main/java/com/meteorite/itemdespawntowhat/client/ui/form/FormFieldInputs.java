package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 创建内置表单控件适配器。
 */
public final class FormFieldInputs {
    private FormFieldInputs() {
    }

    public static FormFieldInput<String> text(EditBox editBox) {
        return new FormFieldInput<>() {
            @Override
            public AbstractWidget widget() {
                return editBox;
            }

            @Override
            public String value() {
                return editBox.getValue();
            }

            @Override
            public void setValue(String value) {
                editBox.setValue(value == null ? "" : value);
            }

            @Override
            public void clear() {
                editBox.setValue("");
            }

            @Override
            public List<EditBox> editBoxes() {
                return List.of(editBox);
            }
        };
    }

    public static <V> FormFieldInput<V> cycle(CycleButton<V> button, V defaultValue) {
        return new FormFieldInput<>() {
            @Override
            public AbstractWidget widget() {
                return button;
            }

            @Override
            public V value() {
                return button.getValue();
            }

            @Override
            public void setValue(V value) {
                button.setValue(value == null ? defaultValue : value);
            }

            @Override
            public void clear() {
                button.setValue(defaultValue);
            }
        };
    }

    public static <V> FormFieldInput<V> composite(
            AbstractCompositeWidget widget,
            Supplier<V> getter,
            Consumer<V> setter,
            Runnable clearer) {
        Objects.requireNonNull(widget, "widget");
        return new FormFieldInput<>() {
            @Override
            public AbstractWidget widget() {
                return widget;
            }

            @Override
            public V value() {
                return getter.get();
            }

            @Override
            public void setValue(V value) {
                setter.accept(value);
            }

            @Override
            public void clear() {
                clearer.run();
            }

            @Override
            public List<EditBox> editBoxes() {
                return widget.getInternalEditBoxes();
            }
        };
    }
}
