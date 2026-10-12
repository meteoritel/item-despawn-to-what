package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumbers;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// ---- 气候区间（-1..1，两端可空） ----

final class ClimateControl extends FormControl {

    private static final String MIN_FIELD = "min";
    private static final String MAX_FIELD = "max";
    private final UiTextInput minInput;
    private final UiTextInput maxInput;
    // 装载时写入两个端点的文本基线
    private String loadedMin = "";
    private String loadedMax = "";

    ClimateControl(Font font, EditorField field, Runnable onChanged) {
        super(field);
        this.minInput = new UiTextInput(THEME, font,
                Component.translatable("gui.itemdespawntowhat.edit.field.climate.min"));
        this.maxInput = new UiTextInput(THEME, font,
                Component.translatable("gui.itemdespawntowhat.edit.field.climate.max"));
        this.minInput.setFilter(FormControl::decimalChars);
        this.maxInput.setFilter(FormControl::decimalChars);
        this.minInput.setOnCommit(text -> {
            markEdited();
            onChanged.run();
        });
        this.maxInput.setOnCommit(text -> {
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
        int half = Math.max(16, (width - 4) / 2);
        minInput.setBounds(x, y, half, layoutHeight());
        maxInput.setBounds(x + half + 4, y, Math.max(16, width - half - 4), layoutHeight());
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        minInput.render(graphics, font, mouseX, mouseY);
        maxInput.render(graphics, font, mouseX, mouseY);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return minInput.mouseClicked(mouseX, mouseY, button) || maxInput.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return minInput.mouseReleased(mouseX, mouseY, button) || maxInput.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return minInput.keyPressed(keyCode, scanCode, modifiers) || maxInput.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean charTyped(char codePoint, int modifiers) {
        return minInput.charTyped(codePoint, modifiers) || maxInput.charTyped(codePoint, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(minInput);
        out.add(maxInput);
    }

    @Override
    void setEnabled(boolean enabled) {
        minInput.setEditable(enabled);
        maxInput.setEditable(enabled);
    }

    @Override
    @Nullable UiFocusTarget revealPath(String path, String basePath) {
        if (path.equals(basePath + "." + MAX_FIELD)) return maxInput;
        if (path.equals(basePath + "." + MIN_FIELD)) return minInput;
        return null;
    }

    @Override
    boolean hasPendingInput() {
        return !minInput.value().equals(loadedMin) || !maxInput.value().equals(loadedMax);
    }

    @Override
    boolean isEditing() {
        return minInput.isFocused() || maxInput.isFocused();
    }

    @Override
    boolean isVisible() {
        return minInput.isVisible();
    }

    @Override
    void load(@Nullable RuleValue value) {
        rememberLoaded(value);
        Double min = null;
        Double max = null;
        if (value != null && value.isMapping()) {
            if (value.has(MIN_FIELD) && value.child(MIN_FIELD).isNumber()) {
                min = value.decimal(MIN_FIELD, 0.0D);
            }
            if (value.has(MAX_FIELD) && value.child(MAX_FIELD).isNumber()) {
                max = value.decimal(MAX_FIELD, 0.0D);
            }
        }
        this.loadedMin = min == null ? "" : trimDouble(min);
        this.loadedMax = max == null ? "" : trimDouble(max);
        minInput.setValue(loadedMin);
        maxInput.setValue(loadedMax);
    }

    @Override
    @Nullable RuleValue store() {
        // 未编辑且两端文本未变：保持 JSON 原样（缺失键不会变成空对象）
        if (!isEdited() && minInput.value().trim().equals(loadedMin.trim())
                && maxInput.value().trim().equals(loadedMax.trim())) {
            return originalValue();
        }
        RuleValue.Mapping object = RuleValue.mapping();
        Double min = UiNumbers.parseFinite(minInput.value());
        Double max = UiNumbers.parseFinite(maxInput.value());
        if (min != null) {
            object.put(MIN_FIELD, RuleValue.decimal(min));
        }
        if (max != null) {
            object.put(MAX_FIELD, RuleValue.decimal(max));
        }
        RuleValue built = object.build();
        return built.isEmpty() ? null : built;
    }

    @Override
    List<FormIssue> issues(String path) {
        Double min = UiNumbers.parseFinite(minInput.value());
        Double max = UiNumbers.parseFinite(maxInput.value());
        List<FormIssue> issues = new ArrayList<>();
        checkEnd(issues, path + "." + MIN_FIELD, minInput.value(), min);
        checkEnd(issues, path + "." + MAX_FIELD, maxInput.value(), max);
        if (min != null && max != null && min > max) {
            issues.add(FormIssue.error(path + "." + MIN_FIELD, label(), Component.translatable(ISSUE_PREFIX + "range_order")));
        }
        return issues;
    }

    private void checkEnd(List<FormIssue> issues, String path, String text, @Nullable Double value) {
        if (text.isBlank()) return;
        double lower = field.doubleMin(-1.0D);
        double upper = field.doubleMax(1.0D);
        if (value == null || !Double.isFinite(value) || value < lower || value > upper) {
            issues.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "number_range", lower, upper)));
        }
    }

}
