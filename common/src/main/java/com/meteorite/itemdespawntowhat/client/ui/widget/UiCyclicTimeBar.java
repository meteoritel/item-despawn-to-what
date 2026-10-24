package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiCyclicRange;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputCapture;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumbers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiValueTextBinding;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 昼夜时间条（宿主侧控件，底层用 kit 的周期区间核心）。
 * <p>交互语义委托 kit 的 {@link UiCyclicRange}：端点允许完整合法刻域、from&gt;to 表示跨零点、
 * 同刻为单点；普通拖动按 {@link #DRAG_STEP} 刻吸附，精确输入接受任意合法刻。
 * <p>0 刻对应 Minecraft 06:00 的换算由宿主的 formatter 提供，本类不假设世界时间。
 * <p>预设（全天/白天/夜间）由宿主注入，点击即一次提交。
 */
public final class UiCyclicTimeBar extends AbstractBarShell<UiCyclicRange> {

    /** 区间提交回调：拖动释放、方向键抬起、精确输入提交与预设点击时回调一次最终刻域，预览过程不回调。 */
    public interface Listener {

        void onCommitted(double fromTick, double toTick);
    }

    /** 时间条提示文案：单点区间、跨零点与精确输入提示，由宿主提供本地化文本。 */
    public record Texts(Component singlePoint, Component wrapHint, Component preciseHint) {
    }

    /** 宿主注入的预设区间：点击即按该区间提交一次，不经过拖动预览。 */
    public record Preset(Component label, double fromTick, double toTick) {
    }

    // 一天的刻数
    public static final double PERIOD = 24000.0D;
    // 普通拖动吸附刻
    public static final double DRAG_STEP = 100.0D;
    // 精确输入允许的刻域
    public static final double MIN_TICK = 0.0D;
    public static final double MAX_TICK = 23999.0D;

    private static final int PRESET_HEIGHT = 12;
    private static final int TRACK_COLOR_HEIGHT = 4;
    private static final int TICK_LABEL_MIN_GAP = 16;

    private final Texts texts;
    private final UiValueTextBinding<Double, String> preciseBinding;
    private final List<Preset> presets = new ArrayList<>();

    private @Nullable Listener listener;
    private UiRect presetRow = new UiRect(0, 0, 0, 0);

    /**
     * 装配世界时间条与精确文本绑定；世界时间刻域和主题由宿主定义。
     *
     * @param theme 宿主主题
     * @param font 文本测量与绘制字体
     * @param texts 本地化提示
     * @param listener 可选的宿主提交监听器
     */
    public UiCyclicTimeBar(UiHostTheme theme, Font font, Texts texts, @Nullable Listener listener) {
        super(theme, font, Objects.requireNonNull(texts, "texts").preciseHint(), 16, new UiCyclicRange(PERIOD));
        this.texts = texts;
        this.listener = listener;
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
        this.preciseBinding = new UiValueTextBinding<>(core::selectedTick,
                core::setSelectedTick, precise::value, precise::setValue, UiCyclicTimeBar::parsePrecise,
                value -> Integer.toString((int) Math.round(value)), () -> {});
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
        applyEnabled(enabled);
        return this;
    }

    public UiCyclicTimeBar setVisible(boolean visible) {
        applyVisible(visible);
        return this;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        int rowHeight = layoutRows(x, y, width, height);
        this.presetRow = new UiRect(x, trackRow.bottom(), bounds.width(),
                Math.min(PRESET_HEIGHT, Math.max(0, bounds.bottom() - trackRow.bottom())));
        // 高度预算够时提示独占一行；不够时留空，提示改在标签行内绘制
        layoutHintRow(LABEL_HEIGHT + rowHeight + presetRow.height());
        layoutCoreBounds();
        layoutPreciseRow(presetRow.bottom());
    }

    /**
     * 时间条不含提示行时所需的最小高度：标签行 + 轨道行 + 预设行。
     * <p>宿主按本值分配高度即可让绘制完全落在自身 bounds 内，聚焦提示会并入标签行的空白处显示。</p>
     *
     * @return 标签行、轨道行与预设行的高度之和
     */
    public static int baseHeight() {
        return LABEL_HEIGHT + TRACK_ROW_HEIGHT + PRESET_HEIGHT;
    }

    /**
     * 时间条带独立提示行时的首选高度：{@link #baseHeight()} 再加一行提示。
     * <p>宿主给足本高度时聚焦提示独占一行；否则提示并入标签行，不会压住预设按钮。</p>
     *
     * @return 标签行、轨道行、预设行与提示行的高度之和
     */
    public static int preferredHeight() {
        return baseHeight() + HINT_HEIGHT;
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        // 没有独立提示行时，聚焦提示挤进标签行的空白处，避免压住预设按钮
        drawLabelRow(graphics, hintRow == null && focused && !preciseActive);
        drawTrack(graphics);
        drawPresets(graphics, mouseX, mouseY);
        if (preciseActive) {
            precise.render(graphics, renderFont, mouseX, mouseY);
        } else {
            drawHintRow(graphics, texts.preciseHint());
        }
    }

    private void drawLabelRow(GuiGraphics graphics, boolean inlineHint) {
        Component label = core.label();
        String left = label == null ? "" : label.getString();
        String trimmed = TextScroll.trimToWidth(font, left, Math.max(1, bounds.width() / 2));
        graphics.drawString(font, trimmed, bounds.x(), bounds.y(),
                enabled ? theme.palette().textSecondary() : theme.palette().textDisabled(), false);
        String range = tickText(core.from()) + " -> " + tickText(core.to());
        String state = core.isSinglePoint() ? texts.singlePoint().getString()
                : (core.crossesPeriod() ? texts.wrapHint().getString() : "");
        String right = TextScroll.trimToWidth(font, range + (state.isEmpty() ? "" : " " + state),
                Math.max(1, bounds.width() - font.width(trimmed) - HINT_GAP));
        int rightX = Math.max(bounds.x(), bounds.right() - font.width(right));
        if (inlineHint) {
            drawInlineHint(graphics, bounds.x() + font.width(trimmed), rightX, texts.preciseHint());
        }
        graphics.drawString(font, right, rightX, bounds.y(),
                enabled ? theme.palette().textPrimary() : theme.palette().textDisabled(), false);
    }

    private void drawTrack(GuiGraphics graphics) {
        if (trackRow.width() <= 0) {
            return;
        }
        int trackY = trackRow.y() + Math.max(0, (trackRow.height() - TRACK_COLOR_HEIGHT) / 2);
        int left = core.trackLeft();
        int right = core.trackRight();
        graphics.fill(left, trackY, right, trackY + TRACK_COLOR_HEIGHT,
                enabled ? theme.palette().scrollTrack() : theme.palette().controlDisabled());
        int fromX = core.xOf(core.from());
        int toX = core.xOf(core.to());
        int highlight = enabled ? theme.palette().accent() : theme.palette().controlDisabled();
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
            graphics.fill(x, trackRow.y(), x + 1, trackRow.y() + 2, theme.palette().textDisabled());
            if (tick.label() != null && gap >= TICK_LABEL_MIN_GAP) {
                String text = tick.label().getString();
                graphics.drawString(font, text, x - font.width(text) / 2, trackRow.y(), theme.palette().textDisabled(),
                        false);
            }
        }
        drawThumb(graphics, fromX, theme.palette().accent());
        drawThumb(graphics, toX, theme.palette().accentHover());
    }

    private void drawThumb(GuiGraphics graphics, int x, int color) {
        int width = core.thumbWidth();
        graphics.fill(x - width / 2, trackRow.y(), x - width / 2 + width, trackRow.bottom(),
                enabled ? color : theme.palette().controlDisabled());
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
            int fill = activePreset ? theme.palette().controlSelected()
                    : (hovered ? theme.palette().controlHover() : theme.palette().controlFill());
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
            theme.drawBevel(graphics, rect, theme.palette().controlBevelLight(), theme.palette().controlBevelDark());
            String text = TextScroll.trimToWidth(font, preset.label().getString(), Math.max(1, width - 4));
            graphics.drawString(font, text, rect.x() + Math.max(0, (width - font.width(text)) / 2),
                    rect.y() + Math.max(0, (rect.height() - 8) / 2),
                    enabled ? theme.palette().textPrimary() : theme.palette().textDisabled(), false);
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

    // 时间条只响应左键
    @Override
    protected boolean acceptsClick(int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    // 预设按钮在边界判定之前命中，一次点击即一次提交
    @Override
    protected boolean handleShellClick(double mouseX, double mouseY, int button) {
        Preset preset = presetAt(mouseX, mouseY);
        if (preset == null) {
            return false;
        }
        closePrecise();
        core.setRange(preset.fromTick(), preset.toTick());
        fireCommitted(preset.fromTick(), preset.toTick());
        return true;
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

    // 合法刻域属于宿主世界时间规则，kit 只负责绑定与周期交互。
    private static @Nullable Double parsePrecise(String text) {
        Integer parsed = UiNumbers.parseInt(text);
        return parsed != null && parsed >= MIN_TICK && parsed <= MAX_TICK ? parsed.doubleValue() : null;
    }

    private void fireCommitted(double from, double to) {
        Listener current = listener;
        if (current != null) {
            current.onCommitted(from, to);
        }
    }

    @Override
    public Component accessibleName() {
        return Component.empty().append(tickText(core.from()) + " -> " + tickText(core.to())
                + (core.crossesPeriod() ? " " + texts.wrapHint().getString() : ""));
    }

    @Override
    protected UiValueTextBinding<Double, String> preciseBinding() {
        return preciseBinding;
    }

    // 捕获结束只收尾 kit 核心；精确输入行由生命周期入口统一关闭，不在此重复处理
    public void captureEnded(UiInputCapture.EndReason reason) {
        core.captureEnded(reason);
    }

    // ---- kit 核心适配 ----

    @Override
    protected boolean coreDragging() {
        return core.isDragging();
    }

    @Override
    protected void coreSetBounds(int x, int y, int width, int height) {
        core.setBounds(x, y, width, height);
    }

    @Override
    protected void coreSetEnabled(boolean enabled) {
        core.setEnabled(enabled);
    }

    @Override
    protected void coreSetVisible(boolean visible) {
        core.setVisible(visible);
    }

    @Override
    protected void coreEndInteraction(UiInputCapture.EndReason reason) {
        core.endInteraction(reason);
    }

    @Override
    protected void coreUnmount() {
        core.unmount();
    }

    @Override
    protected void coreOnFocusScopeChanged() {
        core.onFocusScopeChanged();
    }

    @Override
    protected void coreOnHostClosed() {
        core.onHostClosed();
    }

    @Override
    protected UiRangeEnd coreSelectedEnd() {
        return core.selectedEnd();
    }
}
