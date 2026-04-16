package com.meteorite.itemdespawntowhat.client.ui.screen.support;

import com.meteorite.itemdespawntowhat.client.ui.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.widget.ICompositeWidget;
import com.meteorite.itemdespawntowhat.client.ui.widget.SuggestionWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BaseConfigEditScreenSuggestionController {
    private final List<SuggestionWidget> suggestionWidgets = new ArrayList<>();

    public void clear() {
        suggestionWidgets.clear();
    }

    public void registerSuggestion(Font font, EditBox editBox, SuggestionProvider provider) {
        SuggestionWidget widget = new SuggestionWidget(font, editBox, provider);
        suggestionWidgets.add(widget);
        editBox.setResponder(text -> widget.updateSuggestions());
    }

    public void hideAll() {
        suggestionWidgets.forEach(SuggestionWidget::hide);
    }

    public void hideSuggestionsNotUnderMouse(double mouseX, double mouseY) {
        for (SuggestionWidget widget : suggestionWidgets) {
            EditBox box = widget.getAttachedBox();
            if (box != null && !isMouseOverBox(box, mouseX, mouseY)) {
                widget.hide();
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        for (SuggestionWidget widget : suggestionWidgets) {
            if (widget.isVisible() && widget.mouseClicked(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        for (SuggestionWidget widget : suggestionWidgets) {
            if (widget.isVisible() && widget.mouseScrolled(mouseX, mouseY, scrollDelta)) {
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, @Nullable AbstractWidget focusedWidget) {
        if (focusedWidget instanceof EditBox editBox) {
            return keyPressedForBox(keyCode, editBox);
        }
        if (focusedWidget instanceof ICompositeWidget composite) {
            EditBox active = composite.getInternalFocused();
            if (active != null) {
                return keyPressedForBox(keyCode, active);
            }
        }
        return false;
    }

    private boolean keyPressedForBox(int keyCode, EditBox editBox) {
        for (SuggestionWidget widget : suggestionWidgets) {
            if (widget.getAttachedBox() == editBox && widget.isVisible()) {
                return widget.keyPressed(keyCode);
            }
        }
        return false;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        for (SuggestionWidget widget : suggestionWidgets) {
            if (widget.isVisible()) {
                widget.render(guiGraphics, mouseX, mouseY);
            }
        }
        guiGraphics.pose().popPose();
    }

    public List<SuggestionWidget> getWidgets() {
        return suggestionWidgets;
    }

    private boolean isMouseOverBox(EditBox box, double mouseX, double mouseY) {
        return mouseX >= box.getX() && mouseX <= box.getX() + box.getWidth()
                && mouseY >= box.getY() && mouseY <= box.getY() + box.getHeight();
    }
}
