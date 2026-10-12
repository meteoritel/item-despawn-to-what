package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;


// ---- 布尔开关 ----

final class BoolControl extends FormControl {

    // 复选框自身携带标签，卡片表单不重复绘制左侧标签。
    @Override
    boolean usesLabelColumn() { return false; }

    private final UiCheckBox checkBox;

    BoolControl(Font font, EditorField field, Runnable onChanged) {
        super(field);
        this.checkBox = new UiCheckBox(THEME, font, label(), false);
        this.checkBox.setOnChanged(checked -> {
            markEdited();
            onChanged.run();
        });
    }

    @Override
    int height() {
        return DEFAULT_HEIGHT;
    }

    @Override
    int preferredWidth(int available) { return Math.min(available, checkBox.preferredWidth(2)); }

    @Override
    void setBounds(int x, int y, int width) {
        checkBox.setBounds(x, y, width, layoutHeight());
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        checkBox.render(graphics, font, mouseX, mouseY);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return checkBox.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return checkBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(checkBox);
    }

    @Override
    void setEnabled(boolean enabled) {
        checkBox.setEnabled(enabled);
    }

    @Override
    boolean isVisible() {
        return checkBox.isVisible();
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        boolean checked = value != null && value.isFlag() && value.asFlag(false);
        checkBox.setChecked(checked);
    }

    @Override
    @Nullable RuleValue store() {
        // 用户没点过开关：保持 JSON 原样（省略 enabled 的后端默认 true 不会被写成 false）
        if (!isEdited()) {
            return originalValue();
        }
        return RuleValue.flag(checkBox.isChecked());
    }
}
