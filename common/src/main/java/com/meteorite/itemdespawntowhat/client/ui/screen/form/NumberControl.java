package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorFieldType;
import com.meteorite.itemdespawntowhat.client.edit.EditorPreset;
import com.meteorite.itemdespawntowhat.client.edit.FieldNumbers;
import com.meteorite.itemdespawntowhat.client.edit.NumericDomain;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderWindow;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiValueInteraction;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiSlider;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTextInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 数值控件：kit 滑杆 + 行内精确输入框（字段规格 §1/§4）。
 * <p>滑杆使用字段的「常用交互窗口」，但提交始终按后端合法域校验：窗口不充当硬限额，
 * 窗口之外的既有合法值照原样显示与精确编辑，绝不在装载时被钳制。
 * <p>双向搬运分三条路径：回填走 {@link UiSlider#core()} 的精确 {@code setValue}
 * （不吸附、不回调、不产生编辑历史）；用户拖动/按键走 kit 的 begin-preview-commit-cancel
 * 生命周期，一次拖动只在提交时回调一次宿主；行内精确输入在 Enter 时提交一次、Esc 回退缓冲。
 * <p>显示单位可能不同于 JSON 单位（概率显示百分比、tick 显示秒、amplifier 显示 1 起等级），
 * 换算与解析全部委托 {@link FieldNumbers} 的纯函数，显示精度不反写 JSON。
 */
final class NumberControl extends FormControl {

    // 行内精确输入框的最小宽度
    private static final int MIN_INPUT_WIDTH = 34;
    // 滑杆与输入框之间的间距
    private static final int GAP = 2;
    // 单位提示的本地化 key
    private static final String UNIT_SECONDS = "gui.itemdespawntowhat.edit.unit.seconds";
    private static final String UNIT_LEVEL = "gui.itemdespawntowhat.edit.unit.level";

    private final Font font;
    private final Runnable onChanged;
    private final UiSlider slider;
    private final UiTextInput input;
    private final List<UiButton> presetButtons = new ArrayList<>();
    private final boolean integerDomain;
    private final boolean percentField;
    private final boolean amplifierField;
    private final boolean ticksField;
    // 后端合法域（JSON 单位）
    private final double domainMin;
    private final double domainMax;
    // 滑块常用交互窗口（JSON 单位），恒为合法域的子区间
    private final double windowMin;
    private final double windowMax;
    private @Nullable JsonElement loaded;
    // 装载时写入输入框的文本基线；未编辑且文本未变时视为「未编辑」
    private String loadedText = "";

    // 滑杆交互回调：预览只刷新控件，提交才回调宿主一次
    private final UiValueInteraction.Listener interactionListener = new UiValueInteraction.Listener() {
        @Override
        public void previewed(double startValue, double currentValue) {
            input.setValue(displayText(currentValue));
            slider.setError(false);
        }

        @Override
        public void committed(double startValue, double currentValue) {
            markEdited();
            input.setValue(displayText(currentValue));
            slider.setError(false);
            onChanged.run();
        }

        @Override
        public void cancelled(double startValue, double restoredValue) {
            input.setValue(displayText(restoredValue));
            slider.setError(false);
        }
    };

    private NumberControl(Font font, EditorField field, Runnable onChanged, NumericDomain numbers) {
        super(field);
        this.font = font;
        this.onChanged = onChanged;
        this.integerDomain = numbers.integer();
        this.percentField = field.type() == EditorFieldType.PERCENT;
        this.amplifierField = field.type() == EditorFieldType.AMPLIFIER;
        this.ticksField = field.type() == EditorFieldType.TICKS;
        this.domainMin = domainMinOf(field, numbers);
        this.domainMax = domainMaxOf(field, numbers);
        double windowLow = field.sliderMin(domainMin);
        double windowHigh = field.sliderMax(domainMax);
        if (!Double.isFinite(windowLow) || !Double.isFinite(windowHigh) || windowHigh <= windowLow) {
            windowLow = domainMin;
            windowHigh = domainMax;
        }
        this.windowMin = windowLow;
        this.windowMax = windowHigh;

        this.slider = new UiSlider(font, domainMin, domainMax, numbers.step());
        double fine = Math.min(numbers.step(), numbers.shiftStep());
        this.slider.core().setPolicy(integerDomain
                ? UiNumberPolicy.integer((int) Math.round(domainMin), (int) Math.round(domainMax),
                        (int) Math.round(numbers.step()), (int) Math.round(fine))
                : UiNumberPolicy.of(domainMin, domainMax, numbers.step(), fine));
        this.slider.core().setWindow(new UiSliderWindow(windowMin, windowMax));
        this.slider.setLabel(unitLabel());
        // 值文本由行内精确输入框承担，滑杆不再重复显示，只保留单位提示
        this.slider.setFormatter(value -> Component.empty());
        // 不消费 PageUp/PageDown，交给 FormView 做整页滚动
        this.slider.core().setPageStep(0.0D);
        this.slider.core().setInteractionListener(interactionListener);
        this.slider.core().setValue(neutralValue());

        Component hint = hint();
        this.input = new UiTextInput(font, hint == null ? Component.empty() : hint);
        this.input.setFilter(integerDomain || amplifierField ? FormControl::integerChars
                : FormControl::decimalChars);
        this.input.setOnCommit(this::commitText);
        this.input.setOnCancel(this::discardTextBuffer);
        for (EditorPreset preset : field.presets()) {
            UiButton button = new UiButton(font, Component.translatable(preset.labelKey()),
                    UiButtonVariant.SECONDARY, () -> applyPreset(preset.value()));
            presetButtons.add(button);
        }
    }

    // 域元数据完整时返回数值控件，否则返回 null 由调用方回退文本控件
    static @Nullable NumberControl create(Font font, EditorField field, Runnable onChanged) {
        NumericDomain declared = field.numbers();
        if (declared == null) {
            return null;
        }
        double min = domainMinOf(field, declared);
        double max = domainMaxOf(field, declared);
        if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) {
            return null;
        }
        if (declared.integer()) {
            if (declared.step() < 1.0D || declared.shiftStep() < 1.0D) {
                return null;
            }
        } else if (!(declared.step() > 0.0D) || !(declared.shiftStep() > 0.0D)) {
            return null;
        }
        return new NumberControl(font, field, onChanged, declared);
    }

    private static double domainMinOf(EditorField field, NumericDomain numbers) {
        Double declared = numbers.backendMin();
        if (declared != null) {
            return declared;
        }
        return numbers.integer() ? field.intMin(Integer.MIN_VALUE) : field.doubleMin(-Double.MAX_VALUE);
    }

    private static double domainMaxOf(EditorField field, NumericDomain numbers) {
        Double declared = numbers.backendMax();
        if (declared != null) {
            return declared;
        }
        return numbers.integer() ? field.intMax(Integer.MAX_VALUE) : field.doubleMax(Double.MAX_VALUE);
    }

    // 单位提示：tick 以秒显示、amplifier 以等级显示
    private Component unitLabel() {
        if (ticksField) {
            return Component.translatable(UNIT_SECONDS);
        }
        if (amplifierField) {
            return Component.translatable(UNIT_LEVEL);
        }
        return Component.empty();
    }

    // 未设置时滑杆的停靠位置：0 在窗口内取 0，否则取窗口下界（不写回 JSON）
    private double neutralValue() {
        if (windowMin <= 0.0D && 0.0D <= windowMax) {
            return 0.0D;
        }
        return windowMin;
    }

    // ---- JSON 值 <-> 显示文本 ----

    // JSON 值按字段类型换算成界面文本（显示精度不反写）
    private String displayText(double jsonValue) {
        if (amplifierField) {
            return Integer.toString(FieldNumbers.levelOf((int) Math.round(jsonValue)));
        }
        if (percentField) {
            return FieldNumbers.percentText(jsonValue);
        }
        if (ticksField) {
            return FieldNumbers.ticksToSecondsText((int) Math.round(jsonValue));
        }
        if (integerDomain) {
            return Long.toString(Math.round(jsonValue));
        }
        return FieldNumbers.text(jsonValue, field.displayPrecision());
    }

    // 界面文本解析回 JSON 值；空缓冲与非法/越界均返回 null
    private @Nullable JsonElement parseText(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (amplifierField) {
            Integer amplifier = FieldNumbers.amplifierOf(trimmed);
            return amplifier != null && withinDomain(amplifier) ? new JsonPrimitive(amplifier) : null;
        }
        if (percentField) {
            Double chance = FieldNumbers.parsePercent(trimmed);
            return chance != null && withinDomain(chance) ? new JsonPrimitive(chance) : null;
        }
        if (ticksField) {
            Integer ticks = FieldNumbers.parseSecondsToTicks(trimmed);
            return ticks != null && withinDomain(ticks) ? new JsonPrimitive(ticks) : null;
        }
        if (integerDomain) {
            Integer value = parseExactInt(trimmed);
            return value != null && withinDomain(value) ? new JsonPrimitive(value) : null;
        }
        Double value = parseFinite(trimmed);
        return value != null && withinDomain(value) ? new JsonPrimitive(value) : null;
    }

    private boolean withinDomain(double value) {
        return Double.isFinite(value) && value >= domainMin && value <= domainMax;
    }

    // 整数解析防溢出：超出 int 直接判非法，不静默钳制
    private static @Nullable Integer parseExactInt(String text) {
        try {
            long parsed = Long.parseLong(text);
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // 小数解析：NaN 与 Infinity 一律拒绝
    private static @Nullable Double parseFinite(String text) {
        try {
            double parsed = Double.parseDouble(text);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static @Nullable Double jsonNumber(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double value = element.getAsDouble();
        return Double.isFinite(value) ? value : null;
    }

    private static String rawText(JsonElement element) {
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    // ---- FormControl ----

    @Override
    int height() {
        return DEFAULT_HEIGHT;
    }

    @Override
    void setBounds(int x, int y, int width) {
        int reserved = 0;
        for (UiButton button : presetButtons) {
            reserved += buttonWidth(button) + GAP;
        }
        int available = Math.max(24, width - reserved);
        int inputWidth = inputWidth(available);
        int sliderWidth = Math.max(12, available - inputWidth - GAP);
        slider.setBounds(x, y, sliderWidth, DEFAULT_HEIGHT);
        input.setBounds(x + sliderWidth + GAP, y, inputWidth, DEFAULT_HEIGHT);
        int cursor = x + available + GAP;
        for (UiButton button : presetButtons) {
            int buttonWidth = buttonWidth(button);
            button.setBounds(cursor, y, buttonWidth, DEFAULT_HEIGHT);
            cursor += buttonWidth + GAP;
        }
    }

    // 输入框宽度：按当前文本取样，夹在最小宽度与可用宽度三分之一之间
    private int inputWidth(int available) {
        int sample = font.width(displayText(slider.value())) + 10;
        int limit = Math.max(MIN_INPUT_WIDTH, available / 3);
        return Math.clamp(sample, MIN_INPUT_WIDTH, limit);
    }

    // 预设按钮宽度：文本宽度 + 内边距
    private int buttonWidth(UiButton button) {
        return Math.max(16, font.width(button.label()) + 8);
    }

    @Override
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        slider.render(graphics, renderFont, mouseX, mouseY);
        input.render(graphics, renderFont, mouseX, mouseY);
        for (UiButton button : presetButtons) {
            button.render(graphics, renderFont, mouseX, mouseY);
        }
    }

    @Override
    void load(@Nullable JsonElement value) {
        rememberLoaded(value);
        this.loaded = value == null || value.isJsonNull() ? null : value.deepCopy();
        if (loaded == null) {
            this.loadedText = "";
            input.setValue("");
            slider.core().setValue(neutralValue());
            slider.setError(false);
            return;
        }
        Double number = jsonNumber(loaded);
        if (number == null) {
            // 非数值形态的既有值：原样显示并标错，提交时保留原值
            this.loadedText = rawText(loaded);
            input.setValue(loadedText);
            slider.setError(true);
            return;
        }
        boolean accepted = slider.core().setValue(number);
        slider.setError(!accepted);
        this.loadedText = displayText(number);
        input.setValue(loadedText);
    }

    @Override
    @Nullable JsonElement store() {
        String text = input.value().trim();
        // 未编辑且文本仍等于装载基线：原样回写原始元素（缺失即 null），applyToDraft 跳过该字段
        if (!isEdited() && text.equals(loadedText.trim())) {
            return originalElement();
        }
        if (text.isEmpty()) {
            // 必填/不可空字段清空时不删除既有值，交由 issues() 提示（阻止应用）
            boolean keepExisting = loaded != null && (field.required() || !field.nullable());
            return keepExisting ? loaded : null;
        }
        JsonElement parsed = parseText(text);
        // 输入非法时保留原值，交由 issues() 提示玩家修正
        return parsed != null ? parsed : loaded;
    }

    @Override
    List<FormIssue> issues(String path) {
        String text = input.value().trim();
        if (text.isEmpty()) {
            if (field.required()) {
                return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "required")));
            }
            return List.of();
        }
        if (parseText(text) == null) {
            return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "invalid_number")));
        }
        return List.of();
    }

    @Override
    void setValueFromSuggestion(String value) {
        commitText(value);
    }

    @Override
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (UiButton preset : presetButtons) {
            if (preset.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (slider.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return input.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = slider.mouseReleased(mouseX, mouseY, button);
        return input.mouseReleased(mouseX, mouseY, button) || handled;
    }

    @Override
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return slider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (slider.isFocused()) {
            return slider.keyPressed(keyCode, scanCode, modifiers);
        }
        return input.isFocused() && input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return slider.isFocused() && slider.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    boolean charTyped(char codePoint, int modifiers) {
        return input.isFocused() && input.charTyped(codePoint, modifiers);
    }

    @Override
    void addFocusTargets(List<UiFocusTarget> out) {
        out.add(slider);
        out.add(input);
    }

    @Override
    void setEnabled(boolean enabled) {
        // 禁用会经统一结束入口回退未提交的预览
        slider.setEnabled(enabled);
        input.setEditable(enabled);
        for (UiButton preset : presetButtons) {
            preset.setEnabled(enabled);
        }
    }

    @Override
    boolean isEditing() {
        return input.isFocused();
    }

    @Override
    boolean isVisible() {
        return slider.isVisible() && input.isVisible();
    }

    @Override
    void endInteractions(UiInputCapture.EndReason reason) {
        slider.endInteraction(reason);
    }

    // ---- 精确输入与预设 ----

    // Enter 提交：合法则回填滑杆并回调一次，非法则保留缓冲并报错
    private void commitText(String text) {
        markEdited();
        JsonElement parsed = parseText(text);
        Double number = parsed == null ? null : parsed.getAsDouble();
        if (number == null) {
            slider.setError(true);
            onChanged.run();
            return;
        }
        slider.setError(!slider.core().setValue(number));
        input.setValue(displayText(number));
        onChanged.run();
    }

    // Esc 回退缓冲：丢弃非法/未提交文本，重新显示滑杆当前值
    private void discardTextBuffer() {
        input.setValue(displayText(slider.value()));
        slider.setError(false);
    }

    // 预设：按 JSON 单位解析后作为一次提交
    private void applyPreset(String presetValue) {
        markEdited();
        JsonElement parsed = parseText(presetValue);
        if (parsed == null) {
            return;
        }
        double number = parsed.getAsDouble();
        if (!slider.core().setValue(number)) {
            slider.setError(true);
            return;
        }
        slider.setError(false);
        input.setValue(displayText(number));
        onChanged.run();
    }
}
