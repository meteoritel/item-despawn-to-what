package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.edit.EditorField;
import com.meteorite.itemdespawntowhat.client.edit.EditorPreset;
import com.meteorite.itemdespawntowhat.client.edit.FieldNumbers;
import com.meteorite.itemdespawntowhat.client.edit.FormIssue;
import com.meteorite.itemdespawntowhat.client.edit.RuleValue;
import com.meteorite.itemdespawntowhat.client.edit.NumericDomain;
import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumbers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderWindow;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiValueInteraction;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiValueTextBinding;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
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
 * 数值控件：kit 滑杆 + 行内精确输入框。
 * <p>滑杆使用字段的「常用交互窗口」，但提交始终按后端合法域校验：窗口不充当硬限额，
 * 窗口之外的既有合法值照原样显示与精确编辑，绝不在装载时被钳制。
 * <p>双向搬运分三条路径：回填走 {@link UiSlider#core()} 的精确 {@code setValue}
 * （不吸附、不回调、不产生编辑历史）；用户拖动/按键走 kit 的 begin-preview-commit-cancel
 * 生命周期，一次拖动只在提交时回调一次宿主；行内精确输入在 Enter 时提交一次、Esc 回退缓冲。
 * <p>显示单位可能不同于 JSON 单位（概率显示百分比、tick 显示秒、amplifier 显示 1 起等级），
 * 换算与解析全部委托 {@link FieldNumbers} 的纯函数，显示精度不反写 JSON。
 */
final class NumberControl extends FormControl {

    // 宿主默认主题：屏幕从主题入口取样式后注入本类装配的控件
    private static final UiHostTheme THEME = UiHostThemes.defaultTheme();

    // 行内精确输入框的最小宽度
    private static final int MIN_INPUT_WIDTH = 34;
    // 滑条的最小可拖动宽度：极窄行里优先保住滑条，输入框按需退让
    private static final int MIN_SLIDER_WIDTH = 12;
    // 输入框文本区 = 控件宽度 - 左右内边距（UiTextInput 关闭原版边框后仍保留 3 像素）
    private static final int INPUT_PADDING_X = 3;
    // 文本区末尾余量：给尾部光标与数字进位留白
    private static final int TEXT_SLACK = 4;
    // 滑杆与输入框之间的间距
    private static final int GAP = 2;
    // 单位提示的本地化 key
    private static final String UNIT_SECONDS = TypeLabels.UI_PREFIX + "unit.seconds";
    private static final String UNIT_LEVEL = TypeLabels.UI_PREFIX + "unit.level";

    private final Font font;
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
    private double windowMin;
    private double windowMax;
    // 行内输入框的宽度下限覆盖：数量对象等逐行对齐布局用各行最大值统一（0 = 各自按文本取样）
    private int inputWidthOverride;
    private UiRect unitBounds = new UiRect(0, 0, 0, 0);
    private @Nullable RuleValue loaded;
    // 装载时写入输入框的文本基线；未编辑且文本未变时视为「未编辑」
    private String loadedText = "";
    private final UiValueTextBinding<Double, String> binding;

    private NumberControl(Font font, EditorField field, Runnable onChanged, NumericDomain numbers) {
        super(field);
        this.font = font;
        this.integerDomain = numbers.integer();
        this.percentField = field.isPercent();
        this.amplifierField = field.isAmplifier();
        this.ticksField = field.isTicks();
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

        this.slider = new UiSlider(THEME, font, domainMin, domainMax, numbers.step());
        double fine = Math.min(numbers.step(), numbers.shiftStep());
        this.slider.core().setPolicy(integerDomain
                ? UiNumberPolicy.integer((int) Math.round(domainMin), (int) Math.round(domainMax),
                        (int) Math.round(numbers.step()), (int) Math.round(fine))
                : UiNumberPolicy.of(domainMin, domainMax, numbers.step(), fine));
        this.slider.core().setWindow(new UiSliderWindow(windowMin, windowMax));
        // 单位由宿主在轨道外绘制，避免文字与滑块占用同一矩形。
        this.slider.setFormatter(value -> Component.empty());
        // 不消费 PageUp/PageDown，交给 FormView 做整页滚动
        this.slider.core().setPageStep(0.0D);
        this.slider.core().setValue(neutralValue());

        Component hint = hint();
        this.input = new UiTextInput(THEME, font, hint == null ? Component.empty() : hint);
        this.input.setFilter(integerDomain || amplifierField ? FormControl::integerChars
                : FormControl::decimalChars);
        this.binding = new UiValueTextBinding<>(slider::value, slider.core()::setValue,
                input::value, input::setValue, text -> {
                    RuleValue parsed = parseText(text);
                    return parsed == null ? null : parsed.asDecimal(0.0D);
                }, this::displayText, () -> {
                    markEdited();
                    onChanged.run();
                });
        this.slider.core().setInteractionListener(new UiValueInteraction.Listener() {
            @Override
            public void began(double startValue) { binding.beginValueChange(); }

            @Override
            public void previewed(double startValue, double currentValue) {
                binding.previewValue();
                slider.setError(false);
            }

            @Override
            public void committed(double startValue, double currentValue) {
                slider.setError(false);
                binding.commitValue();
            }

            @Override
            public void cancelled(double startValue, double restoredValue) {
                binding.cancelValue();
                refreshTextError();
            }
        });
        this.input.setOnCommit(this::commitText);
        this.input.setOnCancel(() -> {
            binding.cancelText();
            refreshTextError();
        });
        this.input.setOnValueChanged(this::previewText);
        for (EditorPreset preset : field.presets()) {
            UiButton button = new UiButton(THEME, font, Component.translatable(preset.labelKey()),
                    UiButtonVariant.SECONDARY, () -> applyPreset(preset.value())).setChipStyle(true);
            presetButtons.add(button);
        }
    }

    void setWindow(double min, double max) {
        if (isEditing() || slider.isDragging() || !Double.isFinite(min) || !Double.isFinite(max)) return;
        double nextMin = Math.clamp(min, domainMin, domainMax);
        double nextMax = Math.clamp(max, domainMin, domainMax);
        if (nextMax <= nextMin || nextMin == windowMin && nextMax == windowMax) return;
        windowMin = nextMin;
        windowMax = nextMax;
        slider.core().setWindow(new UiSliderWindow(windowMin, windowMax));
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
            return UiNumbers.roundedText(jsonValue);
        }
        return FieldNumbers.text(jsonValue, field.displayPrecision());
    }

    // 界面文本解析回 JSON 值；空缓冲与非法/越界均返回 null
    private @Nullable RuleValue parseText(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (amplifierField) {
            Integer amplifier = FieldNumbers.amplifierOf(trimmed);
            return amplifier != null && withinDomain(amplifier) ? RuleValue.integer(amplifier) : null;
        }
        if (percentField) {
            Double chance = FieldNumbers.parsePercent(trimmed);
            return chance != null && withinDomain(chance) ? RuleValue.decimal(chance) : null;
        }
        if (ticksField) {
            Integer ticks = FieldNumbers.parseSecondsToTicks(trimmed);
            return ticks != null && withinDomain(ticks) ? RuleValue.integer(ticks) : null;
        }
        if (integerDomain) {
            Integer value = parseExactInt(trimmed);
            return value != null && withinDomain(value) ? RuleValue.integer(value) : null;
        }
        Double value = UiNumbers.parseFinite(trimmed);
        return value != null && withinDomain(value) ? RuleValue.decimal(value) : null;
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

    private static @Nullable Double jsonNumber(RuleValue value) {
        if (value == null || !value.isNumber()) {
            return null;
        }
        double number = value.asDecimal(0.0D);
        return Double.isFinite(number) ? number : null;
    }

    private static String rawText(RuleValue value) {
        return value.isScalar() ? value.asText() : value.toJson();
    }

    // ---- FormControl ----

    // 自然高度即默认行高；需要加高时由 FormControl 的行高覆盖（基本页优先级控件）生效
    @Override
    int height() {
        return DEFAULT_HEIGHT;
    }

    @Override
    void setBounds(int x, int y, int width) {
        // 有效行高：默认 12；被 FormControl 行高覆盖时 slider 与数字输入框一起变高
        int rowHeight = layoutHeight();
        // 加高行改用宽松档滑块外观：样式与画法成对取自注入主题，避免两套来源混用
        UiSliderSkin skin = THEME.sliderSkin(rowHeight >= 18 ? UiSliderRole.ROOMY : UiSliderRole.COMPACT);
        slider.core().setStyle(skin.style());
        slider.core().setPainter(skin.painter());
        int reserved = 0;
        for (UiButton button : presetButtons) {
            reserved += buttonWidth(button) + GAP;
        }
        Component unit = unitLabel();
        int unitWidth = unit.getString().isEmpty() ? 0 : font.width(unit) + GAP;
        unitBounds = new UiRect(x, y, unitWidth, rowHeight);
        int sliderX = x + unitWidth;
        int available = Math.max(24, width - reserved - unitWidth);
        int inputWidth = inputWidth(available);
        int sliderWidth = available - inputWidth - GAP;
        if (sliderWidth < MIN_SLIDER_WIDTH) {
            // 极窄行：输入框已放不下窗口端点，先保住滑条的最小可拖动宽度，两者不重叠
            inputWidth = Math.max(0, available - GAP - MIN_SLIDER_WIDTH);
            sliderWidth = Math.max(MIN_SLIDER_WIDTH, available - inputWidth - GAP);
        }
        slider.setBounds(sliderX, y, sliderWidth, rowHeight);
        input.setBounds(sliderX + sliderWidth + GAP, y, inputWidth, rowHeight);
        // 预设按钮保持默认高度，在加高的行里垂直居中，保证仍可点击
        int buttonY = y + Math.max(0, (rowHeight - DEFAULT_HEIGHT) / 2);
        int cursor = sliderX + available + GAP;
        for (UiButton button : presetButtons) {
            int buttonWidth = buttonWidth(button);
            button.setBounds(cursor, buttonY, buttonWidth, DEFAULT_HEIGHT);
            cursor += buttonWidth + GAP;
        }
    }

    // 输入框宽度：按窗口端点与当前文本取样，夹在最小可读宽度与可用宽度三分之一之间。
    // 三分之一只是常规上限：窗口端点（拖动可达的常用值）必须完整可见，否则原版 EditBox
    // 放不下文本会退到尾部只画子串，-24000 只剩 4000；因此上限不低于窗口端点所需宽度。
    private int inputWidth(int available) {
        int windowText = windowTextWidth();
        int minimum = Math.max(MIN_INPUT_WIDTH, windowText + INPUT_PADDING_X * 2 + TEXT_SLACK);
        int textWidth = Math.max(font.width(displayText(slider.value())), windowText);
        int sample = Math.max(textWidth + INPUT_PADDING_X * 2 + TEXT_SLACK, inputWidthOverride);
        int limit = Math.max(minimum, available / 3);
        return Math.clamp(sample, minimum, limit);
    }

    // 滑条窗口两端显示文本的宽度：拖动只能落在窗口内，端点是用户最常看到的取值
    private int windowTextWidth() {
        return Math.max(font.width(displayText(windowMin)), font.width(displayText(windowMax)));
    }

    // 行内输入框的期望宽度：对齐布局在设置覆盖前用各行最大值统一宽度
    int preferredInputWidth(int available) {
        return inputWidth(available);
    }

    // 统一行内输入框宽度下限（0 表示各自按文本取样）
    void setInputWidthOverride(int width) {
        inputWidthOverride = Math.max(0, width);
    }

    // 预设按钮宽度：文本宽度 + 内边距
    private int buttonWidth(UiButton button) {
        return Math.max(16, font.width(button.label()) + 8);
    }

    @Override
    void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        graphics.drawString(renderFont, unitLabel(), unitBounds.x(),
                unitBounds.y() + Math.max(0, (unitBounds.height() - renderFont.lineHeight) / 2),
                THEME.palette().textSecondary(), false);
        slider.render(graphics, renderFont, mouseX, mouseY);
        input.render(graphics, renderFont, mouseX, mouseY);
        for (UiButton button : presetButtons) {
            button.render(graphics, renderFont, mouseX, mouseY);
        }
    }

    @Override
    void load(@Nullable RuleValue value) {
        binding.acceptText();
        rememberLoaded(value);
        this.loaded = value == null || value.isNull() ? null : value.deepCopy();
        if (loaded == null) {
            this.loadedText = "";
            setInputValue("");
            slider.core().setValue(neutralValue());
            slider.setError(false);
            return;
        }
        Double number = jsonNumber(loaded);
        if (number == null) {
            // 非数值形态的既有值：原样显示并标错，提交时保留原值
            this.loadedText = rawText(loaded);
            setInputValue(loadedText);
            slider.setError(true);
            return;
        }
        boolean accepted = slider.core().setValue(number);
        slider.setError(!accepted);
        this.loadedText = displayText(number);
        setInputValue(loadedText);
    }

    @Override
    @Nullable RuleValue store() {
        String text = input.value().trim();
        // 未编辑且文本仍等于装载基线：原样回写原始值（缺失即 null），applyToDraft 跳过该字段
        if (!isEdited() && text.equals(loadedText.trim())) {
            return originalValue();
        }
        if (text.isEmpty()) {
            // 必填/不可空字段清空时不删除既有值，交由 issues() 提示（阻止应用）
            boolean keepExisting = loaded != null && (field.required() || !field.nullable());
            if (!keepExisting) {
                binding.acceptText();
                markEdited();
                loaded = null;
                loadedText = "";
            }
            return keepExisting ? loaded : null;
        }
        RuleValue parsed = parseText(text);
        if (parsed != null) {
            binding.acceptText();
            markEdited();
            // 失焦提交也更新回退基线，后续非法输入不能回写成更早的装载值。
            loaded = parsed.deepCopy();
            loadedText = text;
        }
        // 输入非法时保留原值，交由 issues() 提示玩家修正
        return parsed != null ? parsed : loaded;
    }

    @Override
    List<FormIssue> issues(String path) {
        String text = input.value().trim();
        if (text.isEmpty()) {
            if (field.required()) {
                return List.of(FormIssue.required(path, label()));
            }
            return List.of();
        }
        if (parseText(text) == null) {
            return List.of(FormIssue.error(path, label(), Component.translatable(ISSUE_PREFIX + "invalid_number")));
        }
        return List.of();
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
        // 预设 chip 的按下由本控件代收（FormInputRouter 记录的按下归属是本控件），释放必须转发回
        // 同一个按钮，否则 UiButton 的 activate() 永不执行，chip 点了没反应
        boolean handled = false;
        for (UiButton preset : presetButtons) {
            handled |= preset.mouseReleased(mouseX, mouseY, button);
        }
        handled |= slider.mouseReleased(mouseX, mouseY, button);
        if (!slider.core().isInteracting()) binding.finishValueChange();
        handled |= input.mouseReleased(mouseX, mouseY, button);
        return handled;
    }

    @Override
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return input.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || slider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (slider.isFocused()) {
            boolean handled = slider.keyPressed(keyCode, scanCode, modifiers);
            if (!slider.core().isInteracting()) binding.finishValueChange();
            return handled;
        }
        return input.isFocused() && input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean handled = slider.isFocused() && slider.keyReleased(keyCode, scanCode, modifiers);
        if (handled) binding.finishValueChange();
        return handled;
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
    @Nullable UiFocusTarget revealPath(String path, String basePath) {
        return path.equals(basePath) ? input : null;
    }

    @Override
    void restoreInputText(List<String> text) {
        if (text.isEmpty()) return;
        setInputValue(text.getFirst());
        RuleValue parsed = parseText(input.value());
        if (parsed != null) slider.core().setValue(parsed.asDecimal(0.0D));
        slider.setError(!input.value().isBlank() && parsed == null);
        binding.acceptText();
        markEdited();
    }

    @Override
    boolean hasPendingInput() {
        return !input.value().equals(loadedText);
    }

    @Override
    boolean isEditing() {
        return input.isFocused();
    }

    @Override
    void endInteractions(UiInputCapture.EndReason reason) {
        // 隐藏/禁用/卸载/关闭不会收到释放事件，chip 的按下态要在这里清掉，否则会一直停在按下外观
        cancelButtonPresses(presetButtons);
        slider.endInteraction(reason);
        binding.finishValueChange();
        input.endInteraction(reason);
    }

    // ---- 精确输入与预设 ----

    // Enter 提交：非法文本保留在原输入缓冲中，交给宿主校验。
    private void commitText(String text) {
        markEdited();
        slider.setError(!binding.commitText(text));
    }

    private void setInputValue(String text) {
        binding.setText(text);
    }

    private void previewText() {
        if (!input.isFocused()) return;
        boolean accepted = binding.previewText();
        if (!accepted && input.value().isBlank() && !field.required()) {
            slider.core().setValue(neutralValue());
            slider.setError(false);
        } else slider.setError(!accepted);
    }

    private void refreshTextError() {
        slider.setError(!input.value().isBlank() && parseText(input.value()) == null);
    }

    // 预设共用精确输入的提交通路与单位解析。
    private void applyPreset(String presetValue) {
        markEdited();
        if (parseText(presetValue) != null) slider.setError(!binding.commitText(presetValue));
    }
}
