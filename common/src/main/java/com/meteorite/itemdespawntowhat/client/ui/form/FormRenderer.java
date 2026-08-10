package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.client.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.client.ui.support.ConfigEditScreenFocusController;
import com.meteorite.itemdespawntowhat.client.ui.support.ConfigEditScreenSuggestionController;
import com.meteorite.itemdespawntowhat.client.ui.widget.AbstractCompositeWidget;
import com.meteorite.itemdespawntowhat.client.ui.widget.ICompositeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一处理表单布局、条件可见性、校验、建议与焦点遍历。
 */
public final class FormRenderer<C> {
    private static final int TAB_KEY = 258;
    private static final int SHIFT_MODIFIER = 1;
    private static final int ERROR_COLOR = 0xFFFF4444;

    private final Font font;
    private final FormListPanel panel;
    private final FormDefinition<C> definition;
    private final ConfigEditScreenSuggestionController suggestionController =
            new ConfigEditScreenSuggestionController();
    private final ConfigEditScreenFocusController focusController =
            new ConfigEditScreenFocusController();
    private final Map<FormField<C>, Component> validationErrors = new LinkedHashMap<>();
    private boolean visibilityInitialized;

    public FormRenderer(Font font, FormListPanel panel, FormDefinition<C> definition) {
        this.font = font;
        this.panel = panel;
        this.definition = definition;
    }

    public void initialize() {
        suggestionController.clear();
        focusController.clearAllFocus();
        panel.setFocusDelegate(widget -> {
            if (focusController.shouldTakeFocus(widget)) {
                focusController.setFocusedWidget(widget);
            }
        });

        for (FormField<C> field : definition.fields()) {
            AbstractWidget widget = field.input().widget();
            if (widget instanceof AbstractCompositeWidget composite) {
                composite.setFocusDelegate(focusController::setFocusedWidget);
            }
            for (FormField.SuggestionBinding suggestion : field.suggestions()) {
                suggestionController.registerSuggestion(font, suggestion.editBox(),
                        suggestion.provider(), suggestion.commaSeparated());
            }
        }
        refreshVisibility();
    }

    public void readFrom(C config) {
        definition.readFrom(config);
        refreshVisibility();
        clearSuggestions();
        validationErrors.clear();
    }

    public void writeTo(C config) {
        refreshVisibility();
        definition.writeTo(config);
    }

    public void clear() {
        definition.clear();
        validationErrors.clear();
        clearSuggestions();
        refreshVisibility();
    }

    public boolean validateAll() {
        refreshVisibility();
        validationErrors.clear();
        for (FormField<C> field : definition.fields()) {
            if (!definition.isVisible(field)) {
                continue;
            }
            Component error = field.validate();
            if (error != null) {
                validationErrors.put(field, error);
            }
        }
        return validationErrors.isEmpty();
    }

    public void refreshVisibility() {
        boolean changed = !visibilityInitialized;
        Map<FormField<C>, Boolean> current = new LinkedHashMap<>();
        for (FormField<C> field : definition.fields()) {
            boolean visible = field.isVisible(definition);
            current.put(field, visible);
            boolean wasVisible = definition.isVisible(field);
            if (visibilityInitialized && wasVisible && !visible && field.clearWhenHidden()) {
                field.clear();
            }
            if (!visibilityInitialized || wasVisible != visible) {
                changed = true;
            }
        }
        current.forEach(definition::setVisible);
        visibilityInitialized = true;
        if (changed) {
            rebuildPanel();
            validationErrors.keySet().removeIf(field -> !definition.isVisible(field));
        }
        refreshExistingValidationErrors();
    }

    private void rebuildPanel() {
        double scroll = panel.getScrollAmount();
        focusController.clearAllFocus();
        suggestionController.hideAll();
        panel.clearFormEntries();
        definition.fields().stream()
                .filter(definition::isVisible)
                .forEach(field -> panel.add(field.label(), field.input().widget()));
        panel.setScrollAmount(scroll);
    }

    private void refreshExistingValidationErrors() {
        if (validationErrors.isEmpty()) {
            return;
        }
        for (FormField<C> field : List.copyOf(validationErrors.keySet())) {
            Component error = definition.isVisible(field) ? field.validate() : null;
            if (error == null) {
                validationErrors.remove(field);
            } else {
                validationErrors.put(field, error);
            }
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        renderValidation(graphics, mouseX, mouseY);
        suggestionController.render(graphics, mouseX, mouseY);
    }

    private void renderValidation(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Map.Entry<FormField<C>, Component> entry : validationErrors.entrySet()) {
            FormFieldInput<?> input = entry.getKey().input();
            List<EditBox> boxes = input.editBoxes();
            if (boxes.isEmpty()) {
                renderOutlineIfVisible(graphics, input.widget());
            } else {
                boxes.forEach(box -> renderOutlineIfVisible(graphics, box));
            }
            if (isMouseOver(input.widget(), mouseX, mouseY)) {
                graphics.renderTooltip(font, entry.getValue(), mouseX, mouseY);
            }
        }
    }

    private void renderOutlineIfVisible(GuiGraphics graphics, AbstractWidget widget) {
        int x = widget.getX();
        int y = widget.getY();
        if (x <= 0 && y <= 0) {
            return;
        }
        if (y < panel.getY() || y + widget.getHeight() > panel.getBottom()) {
            return;
        }
        graphics.renderOutline(x - 1, y - 1, widget.getWidth() + 2, widget.getHeight() + 2, ERROR_COLOR);
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (suggestionController.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        focusController.clearAllFocus();
        suggestionController.hideSuggestionsNotUnderMouse(mouseX, mouseY);
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        return suggestionController.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == TAB_KEY && moveFocus((modifiers & SHIFT_MODIFIER) != 0)) {
            return true;
        }
        AbstractWidget focused = focusController.getFocusedWidget();
        if (suggestionController.keyPressed(keyCode, focused)) {
            return true;
        }
        return focused != null && focused.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        AbstractWidget focused = focusController.getFocusedWidget();
        return focused != null && focused.charTyped(codePoint, modifiers);
    }

    private boolean moveFocus(boolean backwards) {
        List<FocusTarget> targets = focusTargets();
        if (targets.isEmpty()) {
            return false;
        }
        int current = currentFocusIndex(targets);
        int delta = backwards ? -1 : 1;
        int next = current < 0
                ? (backwards ? targets.size() - 1 : 0)
                : Math.floorMod(current + delta, targets.size());
        FocusTarget target = targets.get(next);
        focusController.setFocusedWidget(target.owner());
        if (target.owner() instanceof ICompositeWidget composite && target.internal() != null) {
            composite.focusInternal(target.internal());
        }
        return true;
    }

    private List<FocusTarget> focusTargets() {
        List<FocusTarget> targets = new ArrayList<>();
        for (FormField<C> field : definition.fields()) {
            if (!definition.isVisible(field)) {
                continue;
            }
            AbstractWidget owner = field.input().widget();
            if (owner instanceof ICompositeWidget composite) {
                composite.getInternalEditBoxes().forEach(box -> targets.add(new FocusTarget(owner, box)));
            } else {
                targets.add(new FocusTarget(owner, null));
            }
        }
        return targets;
    }

    private int currentFocusIndex(List<FocusTarget> targets) {
        AbstractWidget focused = focusController.getFocusedWidget();
        for (int i = 0; i < targets.size(); i++) {
            FocusTarget target = targets.get(i);
            if (target.owner() != focused) {
                continue;
            }
            if (target.internal() == null) {
                return i;
            }
            if (focused instanceof ICompositeWidget composite
                    && composite.getInternalFocused() == target.internal()) {
                return i;
            }
        }
        return -1;
    }

    public void clearFocus() {
        focusController.clearAllFocus();
    }

    public void clearSuggestions() {
        suggestionController.hideAll();
    }

    private static boolean isMouseOver(AbstractWidget widget, double mouseX, double mouseY) {
        return mouseX >= widget.getX() && mouseX <= widget.getX() + widget.getWidth()
                && mouseY >= widget.getY() && mouseY <= widget.getY() + widget.getHeight();
    }

    private record FocusTarget(AbstractWidget owner, @Nullable EditBox internal) {
    }
}
