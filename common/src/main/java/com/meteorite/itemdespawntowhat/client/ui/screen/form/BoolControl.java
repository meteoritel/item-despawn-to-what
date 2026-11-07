package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import java.util.function.Consumer;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;


import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;


// ---- 布尔开关 ----

final class BoolControl extends FormControl {

    // 复选框自身携带标签，卡片表单不重复绘制左侧标签。
    @Override
    boolean usesLabelColumn() {
        return false;
    }

    private final UiCheckBox checkBox;

    BoolControl(UiHostTheme theme, Font font, EditorField field, Runnable onChanged) {
        super(theme, field);
        this.checkBox = new UiCheckBox(THEME, font, label(), false);
        this.checkBox.setOnChanged(checked -> {
            markEdited();
            onChanged.run();
        });
    }

    @Override
    int height() {
        return defaultHeight();
    }

    @Override
    int preferredWidth(int available) {
        return Math.min(available, checkBox.preferredWidth(2));
    }

    @Override
    void setBounds(int x, int y, int width) {
        checkBox.setBounds(x, y, width, layoutHeight());
    }

    @Override
    void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
        UiPointer pointer = incomingPointer.resolve(this).forTarget(this);
        checkBox.render(graphics, font, pointer);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        return checkBox.mousePressed(context);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return checkBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void addFocusTargets(Consumer<UiFocusTarget> out) {
        out.accept(checkBox);
    }

    @Override
    void setEnabled(boolean enabled) {
        checkBox.setEnabled(enabled);
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        boolean checked = value != null && value.isFlag() && value.asFlag(false);
        checkBox.setChecked(checked);
    }

    @Override
    @Nullable RuleValue draftValue() {
        // 用户没点过开关：保持 JSON 原样（省略 enabled 的后端默认 true 不会被写成 false）
        if (!isEdited()) {
            return originalValue();
        }
        return RuleValue.flag(checkBox.isChecked());
    }
}
