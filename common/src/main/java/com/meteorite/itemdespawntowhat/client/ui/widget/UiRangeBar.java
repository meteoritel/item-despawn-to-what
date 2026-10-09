package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.PixelSliderPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeSlider;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderWindow;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 区间条（宿主侧控件，主计划 §4 条件与 §8 视觉）。
 * <p>交互语义全部委托 kit 的 {@link UiRangeSlider}：普通区间保持 min≤max、拖到对端停在相同值、
 * 方向键按住只在抬起时合并提交一次、Esc 回退；本类只负责像素渲染（{@link UiSliderPainter}）、
 * 端点缺失提示、精确输入行以及「一次完整操作 = 一次提交」的回调。
 * <p>无约束端不编码成 0：缺失的一端显示为文字提示，玩家可用精确输入补齐该端（一次提交）。
 * 需要关闭某一端的宿主可直接用 {@link #core()} 的 {@code setEndPresent}。
 */
public final class UiRangeBar implements UiWidget, UiFocusTarget {

    // 一次完整操作结束（鼠标释放、方向键抬起、精确输入提交）时回调
    public interface Listener {

        void onCommitted(UiRangeValue value);
    }

    // 宿主提供的本地化文本
    public record Texts(Component lowEnd, Component highEnd, Component absentHint, Component preciseHint) {
    }

    private static final int LABEL_HEIGHT = 10;
    private static final int TRACK_ROW_HEIGHT = 12;
    private static final int VALUE_SLOT_WIDTH = 54;

    private final Font font;
    private final Texts texts;
    private final UiRangeSlider core;
    private final UiTextInput precise;
    private final UiSliderPainter painter = PixelSliderPainter.INSTANCE;
    private final UiSliderStyle style = UiTheme.sliderStyle();

    private @Nullable Listener listener;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect trackRow = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private boolean preciseActive;
    private @Nullable Consumer<UiRangeEnd> preciseEditor;

    public UiRangeBar(Font font, UiNumberPolicy policy, UiRangeValue initial, Texts texts,
                      @Nullable Listener listener) {
        this.font = Objects.requireNonNull(font, "font");
        Objects.requireNonNull(policy, "policy");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.listener = listener;
        this.core = new UiRangeSlider(policy, new UiSliderWindow(policy.min(), policy.max()),
                initial == null ? UiRangeValue.EMPTY : initial);
        this.core.setThumbWidth(3);
        this.core.setListener(new UiRangeSlider.Listener() {
            @Override
            public void committed(UiRangeValue startValue, UiRangeValue currentValue) {
                fireCommitted(currentValue);
            }

            @Override
            public void cancelled(UiRangeValue startValue, UiRangeValue restoredValue) {
                closePrecise();
            }
        });
        this.precise = new UiTextInput(font, texts.preciseHint());
        this.precise.setMaxLength(24);
        this.precise.setVisible(false);
        this.precise.setOnCommit(this::commitPrecise);
        this.precise.setOnCancel(() -> {
            closePrecise();
            this.core.endInteraction(UiInputCapture.EndReason.CANCEL);
        });
    }

    // kit 核心：需要窗口/端点开关/分页步长等能力的宿主直接用
    public UiRangeSlider core() {
        return this.core;
    }

    public UiRangeBar setLabel(@Nullable Component label) {
        this.core.setLabel(label);
        return this;
    }

    public UiRangeBar setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.core.setEnabled(enabled);
        if (!enabled) {
            closePrecise();
        }
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UiRangeBar setVisible(boolean visible) {
        this.visible = visible;
        this.core.setVisible(visible);
        if (!visible) {
            closePrecise();
        }
        return this;
    }

    @Override
    public UiRect bounds() {
        return bounds;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.bounds = new UiRect(x, y, Math.max(0, width), Math.max(0, height));
        int rowHeight = Math.min(TRACK_ROW_HEIGHT, Math.max(4, bounds.height() - LABEL_HEIGHT));
        this.trackRow = new UiRect(x, y + LABEL_HEIGHT, bounds.width(), rowHeight);
        this.core.setBounds(trackRow.x(), trackRow.y(), trackRow.width(), trackRow.height());
        this.precise.setBounds(x, trackRow.bottom(), Math.max(24, bounds.width()), UiTheme.ROW_HEIGHT);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        UiRangeValue value = core.value();
        drawLabelRow(graphics);
        drawTrack(graphics, value, mouseX, mouseY);
        if (preciseActive) {
            precise.render(graphics, renderFont, mouseX, mouseY);
        } else {
            drawHint(graphics, value);
        }
    }

    private void drawLabelRow(GuiGraphics graphics) {
        Component label = core.label();
        if (label != null) {
            String text = TextScroll.trimToWidth(font, label.getString(),
                    Math.max(1, bounds.width() - VALUE_SLOT_WIDTH));
            graphics.drawString(font, text, bounds.x(), bounds.y(), UiPalette.TEXT_SECONDARY, false);
        }
        Component shown = formatValue(core.selectedValue());
        String valueText = TextScroll.trimToWidth(font, shown.getString(), Math.max(1, VALUE_SLOT_WIDTH));
        graphics.drawString(font, valueText, Math.max(bounds.x(), bounds.right() - font.width(valueText)), bounds.y(),
                enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
    }

    private void drawTrack(GuiGraphics graphics, UiRangeValue value, int mouseX, int mouseY) {
        int trackY = trackRow.y() + Math.max(0, (trackRow.height() - style.trackHeight()) / 2);
        UiRect track = new UiRect(core.trackLeft(), trackY, Math.max(1, core.trackRight() - core.trackLeft()),
                Math.max(1, style.trackHeight()));
        int lowX = value.lowPresent() ? core.xOf(value.low()) : -1;
        int highX = value.highPresent() ? core.xOf(value.high()) : -1;
        int fillLeft = lowX >= 0 ? lowX : (highX >= 0 ? highX : track.x());
        int fillRight = highX >= 0 ? highX : fillLeft;
        UiRect filled = new UiRect(Math.min(fillLeft, fillRight), trackY, Math.abs(fillRight - fillLeft),
                Math.max(1, style.trackHeight()));
        double selected = core.selectedValue();
        int thumbX = Double.isFinite(selected) ? core.xOf(selected) : track.x();
        UiRect thumb = new UiRect(thumbX - core.thumbWidth() / 2, trackRow.y(), core.thumbWidth(),
                Math.max(1, trackRow.height()));
        UiRect textSlot = new UiRect(0, 0, 0, 0);
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        painter.paint(graphics, new UiSliderPainter.Frame(bounds, track, filled, thumb, textSlot, style,
                enabled, hovered, core.isDragging(), focused, core.isDragging(), false, false, false));
        // 端点缺失时给出文字提示：颜色不是唯一线索
        if (!value.lowPresent()) {
            drawEndHint(graphics, track.x(), texts.lowEnd());
        }
        if (!value.highPresent()) {
            String hint = texts.highEnd().getString();
            drawEndHint(graphics, Math.max(track.x(), track.right() - font.width(hint)), texts.highEnd());
        }
    }

    private void drawEndHint(GuiGraphics graphics, int x, Component hint) {
        String text = TextScroll.trimToWidth(font, hint.getString(), Math.max(1, bounds.width() / 2));
        graphics.drawString(font, text, x, trackRow.bottom() - 9, UiPalette.TEXT_DISABLED, false);
    }

    private void drawHint(GuiGraphics graphics, UiRangeValue value) {
        if (!focused) {
            return;
        }
        Component hint = value.lowPresent() && value.highPresent() ? texts.preciseHint() : texts.absentHint();
        String text = TextScroll.trimToWidth(font, hint.getString(), Math.max(1, bounds.width()));
        graphics.drawString(font, text, bounds.x(), Math.max(bounds.y(), bounds.bottom() - 9),
                UiPalette.TEXT_DISABLED, false);
    }

    private Component formatValue(double value) {
        if (!Double.isFinite(value)) {
            return Component.literal("-");
        }
        if (Math.rint(value) == value) {
            return Component.literal(Long.toString((long) value));
        }
        return Component.literal(String.format(Locale.ROOT, "%.2f", value));
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled) {
            return false;
        }
        if (preciseActive && precise.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        closePrecise();
        return core.mousePressed(context(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (core.isDragging()) {
            return core.mouseDragged(context(mouseX, mouseY, button));
        }
        return preciseActive && precise.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (core.isDragging()) {
            return core.mouseReleased(context(mouseX, mouseY, button));
        }
        return preciseActive && precise.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !enabled) {
            return false;
        }
        if (preciseActive && precise.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (core.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return openPrecise();
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (preciseActive && precise.keyReleased(keyCode, scanCode, modifiers)) {
            return true;
        }
        return core.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return preciseActive && precise.charTyped(codePoint, modifiers);
    }

    // 打开当前端的精确输入行；当前端缺失时同样可输入，提交即补齐该端
    // 复合表单可将精确编辑交给已有字段输入，统一缓冲与错误定位。
    public void setPreciseEditor(Consumer<UiRangeEnd> editor) { preciseEditor = editor; }

    public boolean openPrecise() {
        if (!enabled || !visible) {
            return false;
        }
        if (preciseEditor != null) {
            preciseEditor.accept(core.selectedEnd());
            return true;
        }
        preciseActive = true;
        precise.setVisible(true);
        precise.setFocused(true);
        double selected = core.selectedValue();
        precise.setValue(Double.isFinite(selected) ? plainText(selected) : "");
        precise.selectAll();
        return true;
    }

    private void commitPrecise(@Nullable String text) {
        String raw = text == null ? "" : text.trim();
        double parsed;
        try {
            parsed = Double.parseDouble(raw);
        } catch (NumberFormatException invalid) {
            closePrecise();
            return;
        }
        UiRangeValue current = core.value();
        if (!current.isPresent(core.selectedEnd())) {
            // 补齐缺失端：一次回填 + 一次提交，仍是一条撤销记录
            double bounded = core.policy().clamp(parsed);
            UiRangeValue next = current.withValue(core.selectedEnd(), bounded);
            core.setValue(next);
            closePrecise();
            fireCommitted(next);
            return;
        }
        boolean accepted = core.setSelectedValue(parsed, false);
        closePrecise();
        if (accepted) {
            // setSelectedValue 已经通过 committed 回调提交；这里保证端点缺失分支也提交一次
            fireCommitted(core.value());
        }
    }

    private String plainText(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void closePrecise() {
        preciseActive = false;
        precise.setVisible(false);
        precise.setFocused(false);
    }

    private void fireCommitted(UiRangeValue value) {
        Listener current = listener;
        if (current != null) {
            current.onCommitted(value);
        }
    }

    private static UiInputContext context(double mouseX, double mouseY, int button) {
        int modifiers = 0;
        if (Screen.hasShiftDown()) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }
        if (Screen.hasControlDown()) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (Screen.hasAltDown()) {
            modifiers |= GLFW.GLFW_MOD_ALT;
        }
        return UiInputContext.pointer(mouseX, mouseY, button, modifiers);
    }

    // ---- 焦点与生命周期 ----

    @Override
    public boolean canFocus() {
        return visible && enabled;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (!focused) {
            closePrecise();
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean activate() {
        return openPrecise();
    }

    @Override
    public Component accessibleName() {
        Component label = core.label() == null ? Component.empty() : core.label().copy().append(" ");
        UiRangeValue value = core.value();
        return label.copy().append("[" + formatValue(value.lowPresent() ? value.low() : Double.NaN).getString()
                + ", " + formatValue(value.highPresent() ? value.high() : Double.NaN).getString() + "]");
    }

    // 宿主隐藏/禁用/卸载/失焦范围切换/关闭时统一结束预览
    public void endInteraction(UiInputCapture.EndReason reason) {
        core.endInteraction(reason);
        closePrecise();
    }

    public void unmount() {
        core.unmount();
        closePrecise();
    }

    public void onFocusScopeChanged() {
        core.onFocusScopeChanged();
        closePrecise();
    }

    public void onHostClosed() {
        core.onHostClosed();
        closePrecise();
    }
}
