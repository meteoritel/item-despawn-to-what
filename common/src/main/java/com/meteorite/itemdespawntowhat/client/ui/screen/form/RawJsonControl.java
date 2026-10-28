package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.JsonSummary;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;


// ---- 只读原始 JSON（第三方未注册类型回退，原样保留） ----

final class RawJsonControl extends FormControl {

    private @Nullable RuleValue raw;

    RawJsonControl(UiHostTheme theme, EditorField field) {
        super(theme, field);
    }

    @Override
    int height() {
        return DEFAULT_HEIGHT;
    }

    @Override
    void setBounds(int x, int y, int width) {
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        this.raw = value == null ? null : value.deepCopy();
    }

    @Override
    @Nullable RuleValue store() {
        // 只读控件：未编辑时原样返回装载值（显式 null 也必须保留），避免被 applyToDraft 误删
        if (!isEdited()) {
            return originalValue();
        }
        // 原样返回，保证未识别字段不丢失
        return raw;
    }

    // 该控件把 JSON 摘要画在标签右侧，由 FormView 调用
    String summary(int maxWidth) {
        return raw == null || raw.isNull() ? "" : JsonSummary.compact(raw, Math.max(4, maxWidth));
    }
}
