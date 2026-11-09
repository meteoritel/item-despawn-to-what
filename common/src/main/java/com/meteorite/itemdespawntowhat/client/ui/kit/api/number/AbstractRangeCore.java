package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidgetShell;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * 区间核心的端点选择与共同公开入口；会话和拖动锚点由 SliderInteraction 组合实现。
 */
abstract class AbstractRangeCore<V> implements UiRangeControl {
    /**
     * 两端重叠时无法按距离区分，点击改为切换当前端。
     */
    static final int OVERLAP_TOLERANCE_PX = 4;

    private final UiWidgetShell shell = new UiWidgetShell(this, this::endInteraction);
    final SliderInteraction<V> interaction;
    UiRangeEnd selectedEnd = UiRangeEnd.LOW;
    @Nullable Function<Double, Component> formatter;
    @Nullable Component label;
    // 手柄宽度由当前宿主皮肤提供，命中与绘制使用同一几何。
    private int thumbWidth = 3;
    private @Nullable UiValueInteraction.Listener<V> listener;

    AbstractRangeCore(BiPredicate<V, V> equivalent) {
        interaction = new SliderInteraction<>(this, Objects::nonNull, equivalent,
                this::snapshotValue, this::restoreValue, this::listener);
    }

    /**
     * 当前值操作监听器。
     */
    public @Nullable UiValueInteraction.Listener<V> listener() {
        return listener;
    }

    /**
     * 后续操作使用新监听器，进行中的操作保留自己的监听器快照。
     */
    public void setListener(@Nullable UiValueInteraction.Listener<V> listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiRect bounds() {
        return shell.bounds();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        shell.setBounds(x, y, width, height);
    }

    /**
     * 当前真实手柄宽度，单位为 GUI 逻辑像素。
     */
    public int thumbWidth() {
        return thumbWidth;
    }

    /**
     * 同步宿主皮肤手柄宽度，最小为一个像素。
     */
    public void setThumbWidth(int thumbWidth) {
        this.thumbWidth = Math.max(1, thumbWidth);
    }

    /**
     * 手柄中心可移动的轨道左端。
     */
    public int trackLeft() {
        return SliderTrack.left(bounds(), thumbWidth);
    }

    /**
     * 手柄中心可移动的轨道右端。
     */
    public int trackRight() {
        return SliderTrack.right(bounds(), thumbWidth);
    }

    /**
     * 轨道至少保留一个逻辑像素，供指针换算使用。
     */
    public int trackWidth() {
        return SliderTrack.width(bounds(), thumbWidth);
    }

    /**
     * 指针是否位于当前控件几何内。
     */
    final boolean containsPointer(double pointerX, double pointerY) {
        return bounds().contains(pointerX, pointerY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDragging() {
        return interaction.isDragging();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiRangeEnd selectedEnd() {
        return selectedEnd;
    }

    /**
     * 当前端点显示文本的格式化函数。
     */
    public @Nullable Function<Double, Component> formatter() {
        return formatter;
    }

    /**
     * 替换端点显示文本，数值语义由具体模型保持。
     */
    public void setFormatter(@Nullable Function<Double, Component> formatter) {
        this.formatter = formatter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled() {
        return shell.isEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnabled(boolean enabled) {
        shell.setEnabled(enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVisible() {
        return shell.isVisible();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVisible(boolean visible) {
        shell.setVisible(visible);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiFocusState focusState() {
        return shell.focusState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canFocus() {
        return shell.canFocus();
    }

    /**
     * 区间端点由方向键或宿主精确入口编辑，通用激活键由宿主解释。
     */
    @Override
    public boolean activate() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Component accessibleName() {
        return label;
    }

    /**
     * 当前语义标签。
     */
    public @Nullable Component label() {
        return label;
    }

    /**
     * 设置语义标签，null 表示没有标签。
     */
    public void setLabel(@Nullable Component label) {
        this.label = label;
    }

    // 端点选择是区间专属，标量只组合相同的会话实现。
    final boolean beginPress(UiInputContext context) {
        if (!shell.isInteractive() || context.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !containsPointer(context.pointerX(), context.pointerY()) || !canStartPress()) return false;
        if (isDragging()) endInteraction(UiInteractionSession.EndReason.RELEASE);
        UiRangeEnd target = hitEnd(context.pointerX());
        selectedEnd = target != null ? target : cycleSelectedEnd();
        interaction.beginPointer(context);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseReleased(UiInputContext context) {
        return interaction.releasePointer(context.button());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return interaction.releaseKey(keyCode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endInteraction(UiInteractionSession.EndReason reason) {
        interaction.end(reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interactionEnded(UiInteractionSession.EndReason reason) {
        interaction.finish(reason);
    }

    final boolean cancelInteraction() {
        return interaction.cancel();
    }

    /**
     * 本模型的不可变值快照。
     */
    abstract V snapshotValue();

    /**
     * 取消时在通知监听器前恢复实际模型。
     */
    abstract void restoreValue(V value);

    /**
     * 两端重叠或无法区分时返回 null，由区间切换当前端。
     */
    abstract @Nullable UiRangeEnd hitEnd(double pointerX);

    /**
     * 在本模型可用端之间切换当前端。
     */
    abstract UiRangeEnd cycleSelectedEnd();

    /**
     * 本模型是否存在可开始指针操作的端点。
     */
    abstract boolean canStartPress();
}
