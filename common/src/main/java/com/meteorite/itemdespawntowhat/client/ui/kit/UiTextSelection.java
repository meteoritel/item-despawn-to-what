package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;
import org.lwjgl.glfw.GLFW;

/** 单行与多行文本共用的鼠标选择：固定锚点、Shift 扩选与矩形外拖动。 */
public final class UiTextSelection implements UiInputTarget, UiInputCapture.Target {

    /** 宿主提供文本模型与坐标映射；光标和锚点采用模型的 UTF-16 索引。 */
    public interface Target {
        int cursor();
        int anchor();
        void seek(double pointerX, double pointerY);
        void select(int anchor, int cursor);
    }

    private final Target target;
    private final UiInputCapture capture = new UiInputCapture();
    private UiRect bounds = new UiRect(0, 0, 0, 0);
    private boolean enabled = true;
    private boolean visible = true;
    private int anchor;
    private int initialAnchor;
    private int initialCursor;

    public UiTextSelection(Target target) {
        this.target = Objects.requireNonNull(target, "Text selection target");
    }

    public void setBounds(UiRect bounds) {
        this.bounds = Objects.requireNonNull(bounds, "Text selection bounds");
    }

    public void setEnabled(boolean enabled) {
        if (!enabled) endInteraction(UiInputCapture.EndReason.DISABLED);
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        if (!visible) endInteraction(UiInputCapture.EndReason.HIDDEN);
        this.visible = visible;
    }

    @Override
    public boolean mousePressed(UiInputContext context) {
        if (!visible || !enabled || context.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || !bounds.contains(context.pointerX(), context.pointerY())) return false;
        endInteraction(UiInputCapture.EndReason.RELEASE);
        initialAnchor = target.anchor();
        initialCursor = target.cursor();
        target.seek(context.pointerX(), context.pointerY());
        anchor = context.shift() ? initialAnchor : target.cursor();
        target.select(anchor, target.cursor());
        capture.begin(this);
        return true;
    }

    @Override
    public boolean mouseDragged(UiInputContext context) {
        if (!isSelecting() || context.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        target.seek(context.pointerX(), context.pointerY());
        target.select(anchor, target.cursor());
        return true;
    }

    @Override
    public boolean mouseReleased(UiInputContext context) {
        return context.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && capture.release();
    }

    public boolean isSelecting() {
        return capture.isCapturedBy(this);
    }

    /** 正常结束保留选择；取消恢复按下前的光标和选择，不改变文本。 */
    public void endInteraction(UiInputCapture.EndReason reason) {
        capture.end(reason);
    }

    @Override
    public void captureEnded(UiInputCapture.EndReason reason) {
        if (reason.cancels()) target.select(initialAnchor, initialCursor);
    }
}
