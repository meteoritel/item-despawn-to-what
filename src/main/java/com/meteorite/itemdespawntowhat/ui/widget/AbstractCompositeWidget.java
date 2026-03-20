package com.meteorite.itemdespawntowhat.ui.widget;

import com.meteorite.itemdespawntowhat.ui.screen.BaseConfigEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractCompositeWidget extends AbstractWidget
        implements ICompositeWidget {
    @Nullable
    protected EditBox internalFocused;

    public AbstractCompositeWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected void setInternalFocused(@Nullable EditBox box) {
        if (internalFocused == box) return;
        if (internalFocused != null) {
            internalFocused.setFocused(false);
        }
        internalFocused = box;
        if (internalFocused != null) {
            internalFocused.setFocused(true);
        }
    }

    @Override
    public void clearInternalFocus() {
        setInternalFocused(null);
    }

    @Override
    @Nullable
    public EditBox getInternalFocused() {
        return internalFocused;
    }

    // 子类返回所有可交互的 EditBox，供 mouseClicked 遍历使用。
    protected abstract Iterable<EditBox> getEditBoxes();

    // 焦点统一管理
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        EditBox clicked = null;
        for (EditBox box : getEditBoxes()) {
            if (box.mouseClicked(mouseX, mouseY, button)) {
                clicked = box;
                break;
            }
        }
        if (clicked == null) return false;

        setInternalFocused(clicked);
        if (Minecraft.getInstance().screen instanceof BaseConfigEditScreen<?> screen) {
            screen.setFocusedWidget(this);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (internalFocused != null && internalFocused.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (internalFocused != null && internalFocused.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {}

}
