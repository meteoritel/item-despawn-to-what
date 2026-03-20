package com.meteorite.itemdespawntowhat.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

// ========== 内部可监听的文本框类 ========== //
public class FocusAwareEditBox extends EditBox {
    @Nullable
    private Consumer<Boolean> focusListener;
    public FocusAwareEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    public void setFocusListener(@Nullable Consumer<Boolean> listener) {
        this.focusListener = listener;
    }

    @Override
    public void setFocused(boolean focused) {
        boolean old = isFocused();
        super.setFocused(focused);
        if (old != focused && focusListener != null) {
            focusListener.accept(focused);
        }
    }
}
