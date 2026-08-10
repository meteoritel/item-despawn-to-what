package com.meteorite.itemdespawntowhat.client.ui.form;

import com.meteorite.itemdespawntowhat.client.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.client.ui.screen.BaseConfigEditScreen;
import com.meteorite.itemdespawntowhat.client.ui.support.FieldValidator;
import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * 向表单 section 暴露有限的组件构建与注册能力。
 */
public final class ConfigFormContext {
    private final BaseConfigEditScreen<?> screen;
    private final FormListPanel formList;
    private final EditBox resultIdInput;

    public ConfigFormContext(BaseConfigEditScreen<?> screen, FormListPanel formList, EditBox resultIdInput) {
        this.screen = screen;
        this.formList = formList;
        this.resultIdInput = resultIdInput;
    }

    public EditBox textBox() {
        return screen.createTextBox();
    }

    public EditBox numericBox() {
        return screen.createNumericBox();
    }

    public EditBox positiveIntBox() {
        return screen.createPositiveIntBox();
    }

    public EditBox positiveDecimalBox() {
        return screen.createPositiveDecimalBox();
    }

    public EditBox resultIdInput() {
        return resultIdInput;
    }

    public Font font() {
        return screen.getScreenFont();
    }

    public int boxWidth() {
        return BaseConfigEditScreen.BOX_WIDTH;
    }

    public int buttonHeight() {
        return BaseConfigEditScreen.BUTTON_HEIGHT;
    }

    public Component label(String suffix) {
        return Component.translatable(BaseConfigEditScreen.LABEL_PREFIX + suffix);
    }

    public void add(String labelSuffix, AbstractWidget widget) {
        formList.add(label(labelSuffix), widget);
    }

    public void addConditional(String labelSuffix, AbstractWidget widget) {
        formList.addConditional(label(labelSuffix), widget);
    }

    public void rebuildConditional(Runnable populate) {
        screen.rebuildConditionalEntries(populate);
    }

    public void registerValidator(EditBox box, FieldValidator... validators) {
        screen.registerSectionValidator(box, validators);
    }

    public void registerValidator(EditBox box, BooleanSupplier condition, FieldValidator... validators) {
        screen.registerSectionValidator(box, condition, validators);
    }

    public void registerSuggestion(EditBox box, SuggestionProvider provider) {
        screen.registerSectionSuggestion(box, provider);
    }

    public void registerCommaSeparatedSuggestion(EditBox box, SuggestionProvider provider) {
        screen.registerSectionCommaSeparatedSuggestion(box, provider);
    }

    public void focus(AbstractWidget widget) {
        screen.setFocusedWidget(widget);
    }

    public int parseInt(String value, int defaultValue) {
        return screen.parseSectionInt(value, defaultValue);
    }

    public float parseFloat(String value) {
        return screen.parseSectionFloat(value);
    }
}
