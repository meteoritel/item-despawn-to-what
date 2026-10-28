package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.TextScroll;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumberPolicy;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiNumbers;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeEnd;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeSlider;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRangeValue;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderPainter;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderWindow;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiValueTextBinding;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;

import java.util.Objects;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 区间条（宿主侧控件）。
 * <p>交互语义全部委托 kit 的 {@link UiRangeSlider}：普通区间保持 min≤max、拖到对端停在相同值、
 * 方向键按住只在抬起时合并提交一次、Esc 回退；本类只负责像素渲染（{@link UiSliderPainter}）、
 * 端点缺失提示、精确输入行以及「一次完整操作 = 一次提交」的回调。
 * <p>无约束端不编码成 0：缺失的一端显示为文字提示，玩家可用精确输入补齐该端（一次提交）。
 * 需要关闭某一端的宿主可直接用 {@link #core()} 的 {@code setEndPresent}。
 */
public final class UiRangeBar extends AbstractBarShell<UiRangeSlider> {

    /**
     * 区间的预览、提交与取消回调；预览不应写入配置历史。
     */
    public interface Listener {

        /**
         * 在预览前保存宿主输入的回退基线。
         *
         * @param value 操作开始时的区间
         */
        default void onBegan(UiRangeValue value) {
        }

        /**
         * 拖动预览时同步宿主输入框，但不产生提交历史。
         *
         * @param value 当前预览区间
         */
        default void onPreviewed(UiRangeValue value) {
        }

        /**
         * 提交一次完整区间操作。
         *
         * @param value 最终区间
         */
        void onCommitted(UiRangeValue value);

        /**
         * 操作已结清，无净变化时也收束宿主输入快照。
         */
        default void onFinished() {
        }

        /**
         * 取消操作时恢复宿主输入。
         *
         * @param value 回退后的区间
         */
        default void onCancelled(UiRangeValue value) {
        }
    }

    /**
     * 区间输入栏的宿主本地化文本：两端标签、缺端提示与精确输入提示。
     */
    public record Texts(Component lowEnd, Component highEnd, Component absentHint, Component preciseHint) {
    }

    private static final int VALUE_SLOT_WIDTH = 54;

    // 取值文本的小数位数（与迁移前 %.2f 一致）
    private static final int VALUE_DECIMALS = 2;

    private final Texts texts;
    private final UiValueTextBinding<Double, String> preciseBinding;
    private final UiSliderPainter painter;
    private final UiSliderStyle style;

    private final @Nullable Listener listener;

    /**
     * 装配区间条与精确文本绑定；主题、提示文案和提交接收方由宿主注入。
     *
     * @param theme    宿主主题
     * @param font     文本测量与绘制字体
     * @param policy   数值合法域与交互步长
     * @param initial  初始区间
     * @param texts    本地化提示
     * @param listener 可选的宿主交互监听器
     */
    public UiRangeBar(UiHostTheme theme, Font font, UiNumberPolicy policy, UiRangeValue initial, Texts texts,
                      @Nullable Listener listener) {
        super(theme, font, Objects.requireNonNull(texts, "texts").preciseHint(), 24,
                new UiRangeSlider(Objects.requireNonNull(policy, "policy"),
                        new UiSliderWindow(policy.min(), policy.max()),
                        initial == null ? UiRangeValue.EMPTY : initial));
        this.texts = texts;
        this.listener = listener;
        this.painter = theme.sliderSkin(UiSliderRole.COMPACT).painter();
        this.style = theme.sliderSkin(UiSliderRole.COMPACT).style();
        this.core.setThumbWidth(style.thumbWidth());
        this.core.setListener(new UiRangeSlider.Listener() {
            @Override
            public void began(UiRangeValue startValue) {
                if (listener != null) listener.onBegan(startValue);
            }

            @Override
            public void committed(UiRangeValue startValue, UiRangeValue currentValue) {
                fireCommitted(currentValue);
            }

            @Override
            public void previewed(UiRangeValue startValue, UiRangeValue currentValue) {
                if (listener != null) listener.onPreviewed(currentValue);
            }

            @Override
            public void finished() {
                if (listener != null) listener.onFinished();
            }

            @Override
            public void cancelled(UiRangeValue startValue, UiRangeValue restoredValue) {
                if (listener != null) listener.onCancelled(restoredValue);
                closePrecise();
            }
        });
        this.preciseBinding = new UiValueTextBinding<>(core::selectedValue,
                value -> core.setSelectedValueOrPresent(value, false), precise::value, precise::setValue,
                UiNumbers::parseFinite, this::plainText, () -> {
        });
    }

    /**
     * 区间条不含提示行时所需的最小高度：标签行 + 轨道行。
     * <p>宿主按本值分配高度即可让绘制完全落在自身 bounds 内，聚焦提示会并入标签行的空白处显示。</p>
     *
     * @return 标签行与轨道行的高度之和
     */
    public static int baseHeight() {
        return LABEL_HEIGHT + TRACK_ROW_HEIGHT;
    }

    /**
     * 区间条带独立提示行时的首选高度：{@link #baseHeight()} 再加一行提示。
     * <p>宿主给足本高度时聚焦提示独占一行；否则提示并入标签行，不会压住轨道。</p>
     *
     * @return 标签行、轨道行与提示行的高度之和
     */
    public static int preferredHeight() {
        return baseHeight() + HINT_HEIGHT;
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
        applyEnabled(enabled);
        return this;
    }

    public UiRangeBar setVisible(boolean visible) {
        applyVisible(visible);
        return this;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        int rowHeight = layoutRows(x, y, width, height);
        // 高度预算够时提示独占一行；不够时留空，提示改在标签行内绘制
        layoutHintRow(LABEL_HEIGHT + rowHeight);
        layoutCoreBounds();
        layoutPreciseRow(trackRow.bottom());
    }

    // ---- 渲染 ----

    @Override
    public void render(GuiGraphics graphics, Font renderFont, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        UiRangeValue value = core.value();
        // 没有独立提示行时，聚焦提示挤进标签行的空白处，避免压住轨道
        drawLabelRow(graphics, hintRow == null && isFocused() && !preciseActive);
        drawTrack(graphics, value, mouseX, mouseY);
        if (preciseActive) {
            precise.render(graphics, renderFont, mouseX, mouseY);
        } else {
            drawHintRow(graphics, hintText(value));
        }
    }

    private void drawLabelRow(GuiGraphics graphics, boolean inlineHint) {
        Component label = core.label();
        UiRangeValue value = core.value();
        Component shown = Component.translatable(TypeLabels.UI_PREFIX + "range.value",
                formatValue(value.low()), formatValue(value.high()));
        int valueWidth = Math.min(bounds.width(), Math.max(VALUE_SLOT_WIDTH, font.width(shown)));
        int labelEnd = bounds.x();
        if (label != null) {
            String text = TextScroll.trimToWidth(font, label.getString(),
                    Math.max(1, bounds.width() - valueWidth));
            graphics.drawString(font, text, bounds.x(), bounds.y(), theme.palette().textSecondary(), false);
            labelEnd = bounds.x() + font.width(text);
        }
        String valueText = TextScroll.trimToWidth(font, shown.getString(),
                Math.max(1, label == null ? bounds.width() : valueWidth));
        int valueX = Math.max(bounds.x(), bounds.right() - font.width(valueText));
        if (inlineHint) {
            drawInlineHint(graphics, labelEnd, valueX, hintText(value));
        }
        graphics.drawString(font, valueText, valueX, bounds.y(),
                enabled ? theme.palette().textPrimary() : theme.palette().textDisabled(), false);
    }

    // 聚焦时的按键提示：两端齐全提示精确输入，缺端时提示回车补端
    private Component hintText(UiRangeValue value) {
        return value.lowPresent() && value.highPresent() ? texts.preciseHint() : texts.absentHint();
    }

    private void drawTrack(GuiGraphics graphics, UiRangeValue value, int mouseX, int mouseY) {
        int trackY = trackRow.y() + Math.max(0, (trackRow.height() - style.trackHeight()) / 2);
        UiRect track = new UiRect(core.trackLeft(), trackY, Math.max(1, core.trackRight() - core.trackLeft()),
                Math.max(1, style.trackHeight()));
        int lowX = value.lowPresent() ? core.xOf(value.low()) : -1;
        int highX = value.highPresent() ? core.xOf(value.high()) : -1;
        int fillLeft = lowX >= 0 ? lowX : track.x();
        int fillRight = highX >= 0 ? highX : (value.isEmpty() ? fillLeft : track.right());
        UiRect filled = new UiRect(Math.min(fillLeft, fillRight), trackY, Math.abs(fillRight - fillLeft),
                Math.max(1, style.trackHeight()));
        double selected = core.selectedValue();
        int thumbX = Double.isFinite(selected) ? core.xOf(selected) : track.x();
        UiRect thumb = new UiRect(thumbX - core.thumbWidth() / 2, trackRow.y(),
                Double.isFinite(selected) ? core.thumbWidth() : 0,
                Math.max(1, trackRow.height()));
        UiRect textSlot = new UiRect(0, 0, 0, 0);
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        painter.paint(graphics, new UiSliderPainter.Frame(bounds, track, filled, thumb, textSlot, style,
                enabled, hovered, core.isDragging(), isFocusVisible(), core.isDragging(), false, false, false));
        UiRangeEnd other = core.selectedEnd().other();
        if (value.isPresent(other)) {
            UiRect otherThumb = new UiRect(core.xOf(value.value(other)) - core.thumbWidth() / 2,
                    trackRow.y(), core.thumbWidth(), Math.max(1, trackRow.height()));
            UiRect empty = new UiRect(0, 0, 0, 0);
            // 第二个手柄复用主题绘制，不重复覆盖轨道或当前手柄的焦点框。
            painter.paint(graphics, new UiSliderPainter.Frame(empty, empty, empty, otherThumb, empty, style,
                    enabled, enabled && otherThumb.contains(mouseX, mouseY), false, false, false,
                    false, false, false));
        }
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
        graphics.drawString(font, text, x, trackRow.bottom() - 9, theme.palette().textDisabled(), false);
    }

    private Component formatValue(double value) {
        if (!Double.isFinite(value)) {
            return Component.literal("-");
        }
        return Component.literal(UiNumbers.plainExact(value, VALUE_DECIMALS, UiNumbers.MAX_LONG_AS_DOUBLE));
    }

    // ---- 精确输入 ----

    private String plainText(double value) {
        if (!Double.isFinite(value)) {
            return "";
        }
        return UiNumbers.plainExact(value, VALUE_DECIMALS, UiNumbers.MAX_LONG_AS_DOUBLE);
    }

    private void fireCommitted(UiRangeValue value) {
        Listener current = listener;
        if (current != null) {
            current.onCommitted(value);
        }
    }

    // ---- 焦点与生命周期 ----


    @Override
    public Component accessibleName() {
        Component label = core.label() == null ? Component.empty() : core.label().copy().append(" ");
        UiRangeValue value = core.value();
        return label.copy().append("[" + formatValue(value.lowPresent() ? value.low() : Double.NaN).getString()
                + ", " + formatValue(value.highPresent() ? value.high() : Double.NaN).getString() + "]");
    }

    @Override
    protected UiValueTextBinding<Double, String> preciseBinding() {
        return preciseBinding;
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
    protected void coreEndInteraction(UiInteractionSession.EndReason reason) {
        core.endInteraction(reason);
    }


    @Override
    protected UiRangeEnd coreSelectedEnd() {
        return core.selectedEnd();
    }
}
