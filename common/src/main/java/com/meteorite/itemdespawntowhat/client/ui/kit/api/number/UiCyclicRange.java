package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;

import java.util.List;
import java.util.Objects;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 周期区间交互核心（无渲染、无宿主主题依赖）。
 *
 * <p>与普通区间不同，周期区间不强制 {@code from <= to}：{@code from > to} 表示区间跨越周期边界，
 * 由宿主按跨周期样式高亮；{@code from == to} 表示单点区间。</p>
 *
 * <p>数值一律折返到 {@code [0, period)}，比较用折返后的值，因此内部不存在「等价但不同」的两个表示。
 * 周期、刻度（吸附网格）与标记都由宿主注入：kit 不读取 {@code Level}、不假定现实世界时间，
 * 也不猜测任何一天/一月多长。</p>
 */
public final class UiCyclicRange extends AbstractRangeCore<UiCyclicRangeValue> {

    /**
     * 宿主注入的刻度；label 可为空表示只有刻度线。
     */
    public record Tick(double tick, @Nullable Component label) {
    }

    /**
     * 宿主注入的标记；只用于展示，不参与吸附。
     */
    public record Marker(double tick, int color) {
    }

    private double period;
    private double from;
    private double to;
    private double tickStep = 1.0D;
    private List<Tick> ticks = List.of();
    private List<Marker> markers = List.of();

    public UiCyclicRange(double period) {
        super((first, second) -> first.from() == second.from() && first.to() == second.to());
        this.period = requirePeriod(period);
    }

    public double period() {
        return this.period;
    }

    // 替换周期并把两端折返到新周期内
    public void setPeriod(double period) {
        this.period = requirePeriod(period);
        this.from = this.wrap(this.from);
        this.to = this.wrap(this.to);
    }

    public double from() {
        return this.from;
    }

    public double to() {
        return this.to;
    }

    /**
     * 回填接口：直接设置两端，不吸附、不回调、不产生历史，不做 {@code min <= max} 约束。
     */
    public void setRange(double from, double to) {
        this.from = this.wrap(from);
        this.to = this.wrap(to);
    }

    // 折返到 [0, period)
    public double wrap(double tick) {
        if (!Double.isFinite(tick)) {
            return 0.0D;
        }
        double wrapped = tick % this.period;
        return wrapped < 0.0D ? wrapped + this.period : wrapped;
    }

    // 从起点顺时针走到终点的长度，落在 [0, period)
    public double span() {
        return this.wrap(this.to - this.from);
    }

    // 两端同刻
    public boolean isSinglePoint() {
        return this.from == this.to;
    }

    // 是否跨周期边界（宿主据此高亮）
    public boolean crossesPeriod() {
        return this.to < this.from;
    }

    public List<Tick> ticks() {
        return this.ticks;
    }

    // 宿主注入刻度
    public void setTicks(List<Tick> ticks) {
        this.ticks = List.copyOf(Objects.requireNonNull(ticks, "ticks"));
    }

    public List<Marker> markers() {
        return this.markers;
    }

    // 宿主注入标记（不参与吸附）
    public void setMarkers(List<Marker> markers) {
        this.markers = List.copyOf(Objects.requireNonNull(markers, "markers"));
    }

    public double tickStep() {
        return this.tickStep;
    }

    // 吸附网格；必须为有限正数
    public void setTickStep(double tickStep) {
        if (!Double.isFinite(tickStep) || tickStep <= 0.0D) {
            throw new IllegalArgumentException("tickStep must be finite and positive");
        }
        this.tickStep = tickStep;
    }

    // 交互窗口上界刻度的默认落点（END 键）；等于最高一格
    public double endTick() {
        return this.wrap(Math.max(0.0D, this.period - this.tickStep));
    }

    // 吸附到刻度网格并折返
    public double snapTick(double raw) {
        if (!Double.isFinite(raw)) {
            return 0.0D;
        }
        return this.wrap(Math.round(raw / this.tickStep) * this.tickStep);
    }

    public UiRangeEnd selectEnd(UiRangeEnd end) {
        this.selectedEnd = Objects.requireNonNull(end, "end");
        return this.selectedEnd;
    }

    @Override
    public UiRangeEnd cycleSelectedEnd() {
        this.selectedEnd = this.selectedEnd.other();
        return this.selectedEnd;
    }

    public double selectedTick() {
        return this.selectedEnd == UiRangeEnd.LOW ? this.from : this.to;
    }

    // 刻度在轨道上的比例；刻度已折返，因此总在 [0,1)
    public double fractionOf(double tick) {
        return this.wrap(tick) / this.period;
    }

    public int xOf(double tick) {
        return this.trackLeft() + (int) Math.round(this.fractionOf(tick) * this.trackWidth());
    }

    /**
     * 点选轨道按有界刻度吸附，右端停在最后一格；循环步进仍使用 {@link #snapTick(double)}。
     */
    public double tickAtPointer(double pointerX) {
        double fraction = Mth.clamp((pointerX - this.trackLeft()) / (double) this.trackWidth(), 0.0D, 1.0D);
        double snapped = Math.round(fraction * this.period / this.tickStep) * this.tickStep;
        return Math.min(snapped, this.endTick());
    }

    /** 当前端是否被按下（供宿主渲染） */
    public boolean isPressed() {
        return interaction.isCaptured();
    }

    /** {@inheritDoc} */
    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!beginPress(context)) return false;
        previewSelected(tickAtPointer(context.pointerX()));
        interaction.anchor(context.pointerX(), selectedTick(), false);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        if (!isDragging()) return false;
        previewSelected(interaction.dragValue(context.pointerX(), period / trackWidth()));
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!canFocus() || !isFocused()) {
            return false;
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN -> this.adjustByKey(keyCode, -1.0D);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP -> this.adjustByKey(keyCode, 1.0D);
            case GLFW.GLFW_KEY_HOME -> this.oneShotSelected(0.0D);
            case GLFW.GLFW_KEY_END -> this.oneShotSelected(this.endTick());
            case GLFW.GLFW_KEY_ESCAPE -> this.cancelInteraction();
            default -> false;
        };
    }

    /**
     * 精确设置当前端刻度；一次调用恰好一条操作。
     */
    public boolean setSelectedTick(double raw) {
        return isEnabled() && oneShotSelected(raw);
    }

    // 命中端点；两端重叠（区分不出）时返回 null，由调用方切换当前端
    @Override
    protected @Nullable UiRangeEnd hitEnd(double pointerX) {
        int lowX = this.xOf(this.from);
        int highX = this.xOf(this.to);
        if (Math.abs(lowX - highX) <= OVERLAP_TOLERANCE_PX) {
            return null;
        }
        return Math.abs(pointerX - lowX) <= Math.abs(pointerX - highX) ? UiRangeEnd.LOW : UiRangeEnd.HIGH;
    }

    @Override
    protected boolean canStartPress() {
        return true;
    }

    private boolean adjustByKey(int keyCode, double direction) {
        interaction.beginKey(focusState(), keyCode);
        previewSelected(selectedTick() + direction * tickStep);
        return true;
    }

    private boolean oneShotSelected(double raw) {
        if (isDragging()) return false;
        interaction.finishBeforeDiscreteChange();
        double next = this.snapTick(raw);
        if (next == this.selectedTick()) {
            return true;
        }
        this.beginOperation();
        this.previewSelected(next);
        this.endInteraction(UiInteractionSession.EndReason.RELEASE);
        return true;
    }

    private boolean previewSelected(double raw) {
        double next = this.snapTick(raw);
        if (this.selectedEnd == UiRangeEnd.LOW) {
            if (next == this.from) {
                return false;
            }
            this.from = next;
        } else {
            if (next == this.to) {
                return false;
            }
            this.to = next;
        }
        this.interaction.preview(snapshotValue());
        return true;
    }

    @Override
    protected UiCyclicRangeValue snapshotValue() {
        return new UiCyclicRangeValue(from, to);
    }

    @Override
    protected void restoreValue(UiCyclicRangeValue restored) {
        from = restored.from();
        to = restored.to();
    }

    private static double requirePeriod(double period) {
        if (!Double.isFinite(period) || period <= 0.0D) {
            throw new IllegalArgumentException("period must be finite and positive");
        }
        return period;
    }
}
