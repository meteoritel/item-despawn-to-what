package com.meteorite.itemdespawntowhat.client.ui.screen.support;

import com.meteorite.itemdespawntowhat.client.ui.widget.ICompositeWidget;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import org.jetbrains.annotations.Nullable;

public final class BaseConfigEditScreenFocusController {
    @Nullable
    private AbstractWidget focusedWidget;

    @Nullable
    public AbstractWidget getFocusedWidget() {
        return focusedWidget;
    }

    public void setFocusedWidget(@Nullable AbstractWidget widget) {
        if (focusedWidget == widget) {
            return;
        }

        if (focusedWidget != null) {
            focusedWidget.setFocused(false);
            if (focusedWidget instanceof ICompositeWidget comp) {
                comp.clearInternalFocus();
            }
        }

        focusedWidget = widget;

        if (widget != null) {
            widget.setFocused(true);
        }
    }

    public void clearAllFocus() {
        setFocusedWidget(null);
    }

    public boolean shouldTakeFocus(AbstractWidget widget) {
        return !(widget instanceof AbstractButton);
    }
}
