package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiCyclicRange;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiPalette;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 昼夜时间条（宿主侧控件，主计划 §4 触发与条件、kit 规格 §5 周期区间）。
 * <p>交互语义委托 kit 的 {@link UiCyclicRange}：端点允许完整合法刻域、from&gt;to 表示跨零点、
 * 同刻为单点；普通拖动按 {@link #DRAG_STEP} 刻吸附，精确输入接受任意合法刻。
 * <p>0 刻对应 Minecraft 06:00 的换算由宿主的 formatter 提供，本类不假设世界时间。
 * <p>预设（全天/白天/夜间）由宿主注入，点击即一次提交。
 */
public final class UiCyclicTimeBar implements UiWidget, UiFocusTarget {

    // 一次完整操作结束（拖动释放、方向键抬起、精确输入提交、预设点击）时回调
    public interface Listener {

        void onCommitted(double fromTick, double toTick);
    }

    // 宿主注入的本地化文本
    public record Texts(Component singlePoint, Component wrapHint, Component preciseHint) {
    }

    // 宿主注入的预设区间
    public record Preset(Component label, double fromTick, double toTick) {
    }

    // 一天的刻数
    public static final double PERIOD = 24000.0D;
    // 普通拖动吸附刻
    public static final double DRAG_STEP = 100.0D;
    // 精确输入允许的刻域
    public static final double MIN_TICK = 0.0D;
    public static final double MAX_TICK = 23999.0D;

    private static final int LABEL_HEIGHT = 10;
    private static final int TRACK_ROW_HEIGHT = 12;
    private static final int PRESET_HEIGHT = 12;
    private static final int TRACK_COLOR_HEIGHT = 4;
    private static final int TICK_LABEL_MIN_GAP = 16;

    private final Font font;
    private final Texts texts;
    private final UiCyclicRange core;
    private final UiTextInput precise;
    private final List<Preset> presets = new ArrayList<>();

    private @Nullable Listener listener;
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private UiRect trackRow = new UiRect(0, 0, 0, 0);
    private UiRect presetRow = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private boolean focused;
    private boolean preciseActive;
    private @Nullable Consumer<UiRangeEnd> preciseEditor;

    public UiCyclicTimeBar(Font font, Texts texts, @Nullable Listener listener) {
        this.font = Objects.requireNonNull(font, "font");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.listener = listener;
        this.core = new UiCyclicRange(PERIOD);
        this.core.setTickStep(DRAG_STEP);
        this.core.setThumbWidth(3);
        this.core.setListener(new UiCyclicRange.Listener() {
            @Override
            public void committed(double startFrom, double startTo, double from, double to) {
                fireCommitted(from, to);
            }

            @Override
            public void cancelled(double startFrom, double startTo, double restoredFrom, double restoredTo) {
                closePrecise();
            }
        });
        this.precise = new UiTextInput(font, texts.preciseHint());
        this.precise.setMaxLength(16);
        this.precise.setVisible(false);
        this.precise.setOnCommit(this::commitPrecise);
        this.precise.setOnCancel(() -> {
            closePrecise();
            this.core.endInteraction(UiInputCapture.EndReason.CANCEL);
        });
    }

    // 回填区间：不吸附、不回调、不产生历史
    public UiCyclicTimeBar setRange(double fromTick, double toTick) {
        this.core.setRange(fromTick, toTick);
        return this;
    }

    public UiCyclicTimeBar setPresets(List<Preset> next) {
        presets.clear();
        if (next != null) {
            presets.addAll(next);
        }
        return this;
    }

    public UiCyclicTimeBar setTicks(List<UiCyclicRange.Tick> ticks) {
        this.core.setTicks(ticks == null ? List.of() : ticks);
        return this;
    }

    // 刻度文字（0 刻 = 06:00 的换算由宿主提供）
    public UiCyclicTimeBar setFormatter(@Nullable Function<Double, Component> formatter) {
        this.core.setFormatter(formatter);
        return this;
    }

    public UiCyclicTimeBar setLabel(@Nullable Component label) {
        this.core.setLabel(label);
        return this;
    }

    public UiCyclicTimeBar setEnabled(boolean enabled) {
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

    public UiCyclicTimeBar setVisible(boolean visible) {
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
        this.presetRow = new UiRect(x, trackRow.bottom(), bounds.width(),
                Math.min(PRESET_HEIGHT, Math.max(0, bounds.bottom() - trackRow.bottom())));
        this.core.setBounds(trackRow.x(), trackRow.y(), trackRow.width(), trackRow.height());
        this.precise.setBounds(x, presetRow.bottom(), Math.max(24, bounds.width()), UiTheme.ROW_HEIGHT);
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
        drawLabelRow(graphics);
        drawTrack(graphics);
        drawPresets(graphics, mouseX, mouseY);
        if (preciseActive) {
            precise.render(graphics, renderFont, mouseX, mouseY);
        } else if (focused) {
            String hint = TextScroll.trimToWidth(font, texts.preciseHint().getString(), Math.max(1, bounds.width()));
            graphics.drawString(font, hint, bounds.x(), Math.max(bounds.y(), bounds.bottom() - 9),
                    UiPalette.TEXT_DISABLED, false);
        }
    }

    private void drawLabelRow(GuiGraphics graphics) {
        Component label = core.label();
        String left = label == null ? "" : label.getString();
        String trimmed = TextScroll.trimToWidth(font, left, Math.max(1, bounds.width() / 2));
        graphics.drawString(font, trimmed, bounds.x(), bounds.y(),
                enabled ? UiPalette.TEXT_SECONDARY : UiPalette.TEXT_DISABLED, false);
        String range = tickText(core.from()) + " -> " + tickText(core.to());
        String state = core.isSinglePoint() ? texts.singlePoint().getString()
                : (core.crossesPeriod() ? texts.wrapHint().getString() : "");
        String right = TextScroll.trimToWidth(font, range + (state.isEmpty() ? "" : " " + state),
                Math.max(1, bounds.width() - font.width(trimmed) - 4));
        graphics.drawString(font, right, Math.max(bounds.x(), bounds.right() - font.width(right)), bounds.y(),
                enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
    }

    private void drawTrack(GuiGraphics graphics) {
        if (trackRow.width() <= 0) {
            return;
        }
        int trackY = trackRow.y() + Math.max(0, (trackRow.height() - TRACK_COLOR_HEIGHT) / 2);
        int left = core.trackLeft();
        int right = core.trackRight();
        graphics.fill(left, trackY, right, trackY + TRACK_COLOR_HEIGHT,
                enabled ? UiPalette.SCROLL_TRACK : UiPalette.CONTROL_DISABLED);
        int fromX = core.xOf(core.from());
        int toX = core.xOf(core.to());
        int highlight = enabled ? UiPalette.ACCENT : UiPalette.CONTROL_DISABLED;
        if (core.isSinglePoint()) {
            graphics.fill(Math.max(left, fromX - 1), trackY - 1, Math.min(right, fromX + 1),
                    trackY + TRACK_COLOR_HEIGHT + 1, highlight);
        } else if (core.crossesPeriod()) {
            // 跨零点：两段高亮，文字同时给出跨周期提示
            graphics.fill(fromX, trackY, right, trackY + TRACK_COLOR_HEIGHT, highlight);
            graphics.fill(left, trackY, toX, trackY + TRACK_COLOR_HEIGHT, highlight);
        } else {
            graphics.fill(fromX, trackY, toX, trackY + TRACK_COLOR_HEIGHT, highlight);
        }
        // 刻度线（有标签的刻度在间距足够时绘文字）
        List<UiCyclicRange.Tick> ticks = core.ticks();
        int gap = ticks.isEmpty() ? Integer.MAX_VALUE : Math.max(1, core.trackWidth() / ticks.size());
        for (UiCyclicRange.Tick tick : ticks) {
            int x = core.xOf(tick.tick());
            graphics.fill(x, trackRow.y(), x + 1, trackRow.y() + 2, UiPalette.TEXT_DISABLED);
            if (tick.label() != null && gap >= TICK_LABEL_MIN_GAP) {
                String text = tick.label().getString();
                graphics.drawString(font, text, x - font.width(text) / 2, trackRow.y(), UiPalette.TEXT_DISABLED,
                        false);
            }
        }
        drawThumb(graphics, fromX, UiPalette.ACCENT);
        drawThumb(graphics, toX, UiPalette.ACCENT_HOVER);
    }

    private void drawThumb(GuiGraphics graphics, int x, int color) {
        int width = core.thumbWidth();
        graphics.fill(x - width / 2, trackRow.y(), x - width / 2 + width, trackRow.bottom(),
                enabled ? color : UiPalette.CONTROL_DISABLED);
    }

    private void drawPresets(GuiGraphics graphics, int mouseX, int mouseY) {
        if (presets.isEmpty() || presetRow.height() <= 0) {
            return;
        }
        int x = presetRow.x();
        for (Preset preset : presets) {
            int width = Math.min(presetRow.width(), font.width(preset.label()) + 8);
            if (x + width > presetRow.right()) {
                break;
            }
            UiRect rect = new UiRect(x, presetRow.y(), width, presetRow.height());
            boolean activePreset = Math.abs(preset.fromTick() - core.from()) < 0.5D
                    && Math.abs(preset.toTick() - core.to()) < 0.5D;
            boolean hovered = enabled && rect.contains(mouseX, mouseY);
            int fill = activePreset ? UiPalette.CONTROL_SELECTED
                    : (hovered ? UiPalette.CONTROL_HOVER : UiPalette.CONTROL_FILL);
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
            UiTheme.drawBevel(graphics, rect, UiPalette.CONTROL_BEVEL_LIGHT, UiPalette.CONTROL_BEVEL_DARK);
            String text = TextScroll.trimToWidth(font, preset.label().getString(), Math.max(1, width - 4));
            graphics.drawString(font, text, rect.x() + Math.max(0, (width - font.width(text)) / 2),
                    rect.y() + Math.max(0, (rect.height() - 8) / 2),
                    enabled ? UiPalette.TEXT_PRIMARY : UiPalette.TEXT_DISABLED, false);
            x += width + 2;
        }
    }

    private String tickText(double tick) {
        if (core.formatter() != null) {
            return core.formatter().apply(tick).getString();
        }
        return Integer.toString((int) Math.round(tick));
    }

    // ---- 输入 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || !enabled || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (preciseActive && precise.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        Preset preset = presetAt(mouseX, mouseY);
        if (preset != null) {
            closePrecise();
            core.setRange(preset.fromTick(), preset.toTick());
            fireCommitted(preset.fromTick(), preset.toTick());
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

    private @Nullable Preset presetAt(double mouseX, double mouseY) {
        if (!presetRow.contains(mouseX, mouseY)) {
            return null;
        }
        int x = presetRow.x();
        for (Preset preset : presets) {
            int width = Math.min(presetRow.width(), font.width(preset.label()) + 8);
            if (x + width > presetRow.right()) {
                return null;
            }
            if (mouseX >= x && mouseX < x + width) {
                return preset;
            }
            x += width + 2;
        }
        return null;
    }

    // 打开当前端的精确输入（允许任意合法刻）
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
        precise.setValue(Integer.toString((int) Math.round(core.selectedTick())));
        precise.selectAll();
        return true;
    }

    private void commitPrecise(@Nullable String text) {
        String raw = text == null ? "" : text.trim();
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException invalid) {
            closePrecise();
            return;
        }
        if (parsed < MIN_TICK || parsed > MAX_TICK) {
            closePrecise();
            return;
        }
        boolean accepted = core.setSelectedTick(parsed);
        closePrecise();
        if (accepted) {
            fireCommitted(core.from(), core.to());
        }
    }

    private void closePrecise() {
        preciseActive = false;
        precise.setVisible(false);
        precise.setFocused(false);
    }

    private void fireCommitted(double from, double to) {
        Listener current = listener;
        if (current != null) {
            current.onCommitted(from, to);
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
        return Component.empty().append(tickText(core.from()) + " -> " + tickText(core.to())
                + (core.crossesPeriod() ? " " + texts.wrapHint().getString() : ""));
    }

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

    public void captureEnded(UiInputCapture.EndReason reason) {
        core.captureEnded(reason);
    }
}
