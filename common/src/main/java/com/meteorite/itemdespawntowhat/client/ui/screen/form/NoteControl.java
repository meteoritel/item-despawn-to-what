package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// ---- 只读说明行（不写入 JSON） ----

final class NoteControl extends FormControl {

    NoteControl(UiHostTheme theme, EditorField field) {
        super(theme, field);
    }

    @Override
    int height() {
        return defaultHeight();
    }

    @Override
    void setBounds(int x, int y, int width) {
    }

    @Override
    void render(GuiGraphics graphics, Font font, UiPointer incomingPointer) {
    }

    @Override
    void load(@Nullable RuleValue value) {
    }

    @Override
    @Nullable RuleValue store() {
        return null;
    }

    @Override
    boolean usesLabelColumn() {
        return false;
    }

    // 说明文本就是 labelKey 本身
    Component note() {
        return Component.translatable(field.labelKey());
    }
}
