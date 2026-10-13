package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumbers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiCheckBox;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiRangeBar;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


// 气候参数独立启用；区间条与精确输入共用表单的提交和输入缓冲。

final class ClimateControl extends FormControl {

    private static final String MIN_FIELD = "min";
    private static final String MAX_FIELD = "max";
    private static final String CLIMATE_UI = "gui.itemdespawntowhat.edit.field.climate.";
    // 复选框自带文案、方框与文字间距只有 3px，立体 inset 主题下描边外扩后几乎贴住文字；
    // 本行不显示行标签，文案是唯一说明，因此前置一个空格（约 4px）保证可见间距
    private static final String TOGGLE_TEXT_INDENT = " ";
    private static final int TOGGLE_HEIGHT = 16;
    private static final int RANGE_HEIGHT = 24;
    private static final int GAP = 2;
    private static final double CLIMATE_MIN = -1.0D;
    private static final double CLIMATE_MAX = 1.0D;
    private static final double STEP = 0.1D;
    private static final double FINE_STEP = 0.01D;
    private final UiCheckBox matching;
    private final UiRangeBar range;
    private final UiTextInput minInput;
    private final UiTextInput maxInput;
    private boolean enabled = true;
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
        matching = new UiCheckBox(THEME, font, toggleLabel(), false);
        range = new UiRangeBar(THEME, font,
                UiNumberPolicy.of(lower(), upper(), STEP, FINE_STEP), fullRange(),
                new UiRangeBar.Texts(Component.translatable(CLIMATE_UI + MIN_FIELD),
                        Component.translatable(CLIMATE_UI + MAX_FIELD),
                        Component.translatable("gui.itemdespawntowhat.edit.range.absent"),
                        Component.translatable("gui.itemdespawntowhat.edit.range.precise")), value -> {
                    minInput.setValue(trimDouble(value.low()));
                    maxInput.setValue(trimDouble(value.high()));
                    markEdited();
                    onChanged.run();
                });
        range.setPreciseEditor(end -> {
            range.setFocused(false);
            minInput.setFocused(end == UiRangeEnd.LOW);
            maxInput.setFocused(end == UiRangeEnd.HIGH);
        });
        minInput.setOnValueChanged(this::syncRange);
        maxInput.setOnValueChanged(this::syncRange);
        matching.setOnChanged(checked -> {
            range.endInteraction(UiInputCapture.EndReason.CANCEL);
            minInput.setFocused(false);
            maxInput.setFocused(false);
            minInput.setValue(checked ? trimDouble(lower()) : "");
            maxInput.setValue(checked ? trimDouble(upper()) : "");
            syncRange();
            updateEnabled();
            markEdited();
            onChanged.run();
        });
        updateEnabled();
    }

    // 气候开关的文案：本行唯一文案，前置缩进让复选框方框与文字留有可见间距
    private Component toggleLabel() {
        return Component.literal(TOGGLE_TEXT_INDENT).append(Component.translatable(CLIMATE_UI + "enabled", label()));
    }

    @Override
    int height() {
        return TOGGLE_HEIGHT + RANGE_HEIGHT + DEFAULT_HEIGHT + GAP * 2;
    }

    @Override
    void setBounds(int x, int y, int width) {
        int half = Math.max(16, (width - 4) / 2);
        matching.setBounds(x, y, width, TOGGLE_HEIGHT);
        range.setBounds(x, y + TOGGLE_HEIGHT + GAP, width, RANGE_HEIGHT);
        int inputY = y + TOGGLE_HEIGHT + RANGE_HEIGHT + GAP * 2;
        minInput.setBounds(x, inputY, half, DEFAULT_HEIGHT);
        maxInput.setBounds(x + half + 4, inputY, Math.max(16, width - half - 4), DEFAULT_HEIGHT);
    }

    @Override
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        matching.render(graphics, font, mouseX, mouseY);
        range.render(graphics, font, mouseX, mouseY);
        minInput.render(graphics, font, mouseX, mouseY);
        maxInput.render(graphics, font, mouseX, mouseY);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled) return false;
        if (matching.mouseClicked(mouseX, mouseY, button)) return true;
        return matching.isChecked() && (range.mouseClicked(mouseX, mouseY, button)
                || minInput.mouseClicked(mouseX, mouseY, button) || maxInput.mouseClicked(mouseX, mouseY, button));
    }

    @Override
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return minInput.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || maxInput.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || range.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        return range.mouseReleased(mouseX, mouseY, button)
                || minInput.mouseReleased(mouseX, mouseY, button) || maxInput.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (matching.isFocused()) return matching.keyPressed(keyCode, scanCode, modifiers);
        if (range.isFocused()) return range.keyPressed(keyCode, scanCode, modifiers);
        return minInput.keyPressed(keyCode, scanCode, modifiers) || maxInput.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return range.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    void endInteractions(UiInputCapture.EndReason reason) {
        range.endInteraction(reason);
        minInput.endInteraction(reason);
        maxInput.endInteraction(reason);
    }

    @Override
    boolean charTyped(char codePoint, int modifiers) {
        return minInput.charTyped(codePoint, modifiers) || maxInput.charTyped(codePoint, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(matching);
        out.add(range);
        out.add(minInput);
        out.add(maxInput);
    }

    @Override
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        updateEnabled();
    }

    private void updateEnabled() {
        matching.setEnabled(enabled);
        boolean active = enabled && matching.isChecked();
        range.setEnabled(active);
        minInput.setEditable(active);
        maxInput.setEditable(active);
    }

    @Override
    @Nullable UiFocusTarget revealPath(String path, String basePath) {
        if (path.equals(basePath + "." + MAX_FIELD)) return maxInput;
        if (path.equals(basePath + "." + MIN_FIELD)) return minInput;
        return null;
    }

    @Override
    boolean hasPendingInput() {
        return isEdited() || !minInput.value().equals(loadedMin) || !maxInput.value().equals(loadedMax);
    }

    @Override
    void inputAccepted() {
        loadedMin = minInput.value();
        loadedMax = maxInput.value();
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
            RuleValue low = value.child(MIN_FIELD);
            RuleValue high = value.child(MAX_FIELD);
            if (low != null && low.isNumber()) {
                min = low.asDecimal(CLIMATE_MIN);
            }
            if (high != null && high.isNumber()) {
                max = high.asDecimal(CLIMATE_MAX);
            }
        }
        this.loadedMin = min == null ? "" : trimDouble(min);
        this.loadedMax = max == null ? "" : trimDouble(max);
        minInput.setValue(loadedMin);
        maxInput.setValue(loadedMax);
        matching.setChecked(value != null && value.isMapping() && (value.has(MIN_FIELD) || value.has(MAX_FIELD)));
        syncRange();
        updateEnabled();
    }

    @Override
    List<String> inputText() {
        return List.of(Boolean.toString(matching.isChecked()), minInput.value(), maxInput.value());
    }

    @Override
    void restoreInputText(List<String> text) {
        if (text.size() != 3) return;
        matching.setChecked(Boolean.parseBoolean(text.getFirst()));
        minInput.setValue(text.get(1));
        maxInput.setValue(text.get(2));
        syncRange();
        updateEnabled();
        markEdited();
    }

    private double lower() { return field.doubleMin(CLIMATE_MIN); }

    private double upper() { return field.doubleMax(CLIMATE_MAX); }

    private UiRangeValue fullRange() { return UiRangeValue.of(lower(), upper()); }

    private void syncRange() {
        Double min = UiNumbers.parseFinite(minInput.value());
        Double max = UiNumbers.parseFinite(maxInput.value());
        double low = min == null ? lower() : min;
        double high = max == null ? upper() : max;
        // 非法文本留在精确输入中供校验；不得构造倒置区间或改写草稿。
        range.core().setValue(low >= lower() && high <= upper() && low <= high
                ? UiRangeValue.of(low, high) : fullRange());
    }

    @Override
    @Nullable RuleValue store() {
        // 未编辑且两端文本未变：保持 JSON 原样（缺失键不会变成空对象）
        if (!isEdited() && minInput.value().trim().equals(loadedMin.trim())
                && maxInput.value().trim().equals(loadedMax.trim())) {
            return originalValue();
        }
        if (!matching.isChecked()) return null;
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
        if (!matching.isChecked()) return List.of();
        Double min = UiNumbers.parseFinite(minInput.value());
        Double max = UiNumbers.parseFinite(maxInput.value());
        List<FormIssue> issues = new ArrayList<>();
        checkEnd(issues, path + "." + MIN_FIELD, minInput.value(), min);
        checkEnd(issues, path + "." + MAX_FIELD, maxInput.value(), max);
        if (minInput.value().isBlank() && maxInput.value().isBlank()) {
            issues.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "required")));
        }
        if (min != null && max != null && min > max) {
            issues.add(FormIssue.error(path + "." + MIN_FIELD, label(), Component.translatable(ISSUE_PREFIX + "range_order")));
        }
        return issues;
    }

    private void checkEnd(List<FormIssue> issues, String path, String text, @Nullable Double value) {
        if (text.isBlank()) return;
        double lower = lower();
        double upper = upper();
        if (value == null || !Double.isFinite(value) || value < lower || value > upper) {
            issues.add(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "number_range", lower, upper)));
        }
    }

}
