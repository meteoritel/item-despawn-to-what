package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSegmentedControl;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// ---- 枚举分段选择 ----
final class EnumControl extends FormControl {

    private final UiSegmentedControl segments;

    EnumControl(UiHostTheme theme, Font font, EditorField field, Runnable onChanged) {
        super(theme, field);
        String group = field.enumGroup() == null || field.enumGroup().isBlank() ? field.name() : field.enumGroup();
        List<UiSegmentedControl.Option> options = new ArrayList<>();
        for (String value : field.enumValues()) {
            options.add(new UiSegmentedControl.Option(value,
                    Component.translatable(TypeLabels.UI_PREFIX + "enum." + group + "." + value)));
        }
        this.segments = new UiSegmentedControl(THEME, font, options);
        this.segments.setOnChanged(value -> {
            markEdited();
            onChanged.run();
        });
    }

    @Override
    int height() {
        return DEFAULT_HEIGHT;
    }

    @Override
    void setBounds(int x, int y, int width) {
        segments.setBounds(x, y, width, layoutHeight());
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        segments.render(graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        return segments.mousePressed(context);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return segments.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(segments);
    }

    @Override
    void setEnabled(boolean enabled) {
        segments.setEnabled(enabled);
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        segments.setSelected(value != null && value.isScalar() ? value.asText() : field.enumValues().isEmpty() ? "" : field.enumValues().getFirst());
    }

    @Override
    @Nullable RuleValue store() {
        // 用户没点过分段：保持 JSON 原样（省略 shape/pickup 时不会写入或写成空串）
        if (!isEdited()) {
            return originalValue();
        }
        String selected = segments.selected();
        return selected == null || selected.isEmpty() ? null : RuleValue.text(selected);
    }

    @Override
    List<FormIssue> issues(String path) {
        if (field.required() && segments.selected().isBlank()) {
            return List.of(FormIssue.required(path, label()));
        }
        return List.of();
    }
}
