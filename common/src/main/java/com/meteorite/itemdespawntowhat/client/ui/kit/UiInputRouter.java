package com.meteorite.itemdespawntowhat.client.ui.kit;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 宿主选择活动作用域，路由器统一执行切换、交互收尾与输入派发。
 */
public final class UiInputRouter {
    private @Nullable UiInputScope active;

    /**
     * 同步宿主选定的作用域，身份未变时保留交互。
     */
    public void select(@Nullable UiInputScope next) {
        if (active == next) return;
        UiInputScope previous = active;
        active = next;
        if (previous != null) previous.deactivate(UiInteractionSession.EndReason.SCOPE_CHANGED);
        if (next != null) next.activate();
    }

    /**
     * 输入与 hover 门禁共用当前作用域。
     */
    public @Nullable UiInputScope active() {
        return active;
    }

    /**
     * 正常命中报告焦点候选，不另行扫描焦点矩形。
     */
    public boolean mousePressed(UiInputContext context) {
        UiInputScope scope = context.scope();
        if (scope != active) return false;
        scope.focus().setKeyboardMode(false);
        scope.interaction().end(UiInteractionSession.EndReason.FOCUS_TRANSFER);
        if (scope != active) return true;
        scope.beginPointer();
        boolean consumed = scope.content().mousePressed(context);
        if (context.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) scope.finishPointer();
        return consumed;
    }

    /**
     * 拖出矩形后继续送达原会话目标。
     */
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        if (context.scope() != active) return false;
        UiInteractionSession session = context.scope().interaction();
        UiInteractionSession.Target target = session.target(UiInteractionSession.Source.POINTER, context.button());
        return target != null && target.mouseDragged(context, dragX, dragY);
    }

    /**
     * 迟到释放不会交给新作用域内容。
     */
    public boolean mouseReleased(UiInputContext context) {
        if (context.scope() != active) return false;
        UiInteractionSession session = context.scope().interaction();
        UiInteractionSession.Target target = session.target(UiInteractionSession.Source.POINTER, context.button());
        if (target == null) return false;
        boolean consumed = target.mouseReleased(context);
        session.end(target, UiInteractionSession.EndReason.RELEASE);
        return consumed;
    }

    /**
     * 正常命中派发双轴滚轮。
     */
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        if (context.scope() != active) return false;
        return context.scope().content().mouseScrolled(context, scrollX, scrollY);
    }

    /**
     * Tab 先完成交互；内容输入优先于宿主历史和焦点激活。
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        UiInputScope scope = active;
        if (scope == null) return false;
        scope.focus().setKeyboardMode(true);
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            scope.interaction().end(UiInteractionSession.EndReason.FOCUS_TRANSFER);
            if (scope != active) return true;
            return scope.focus().keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && scope.interaction().end(UiInteractionSession.EndReason.CANCEL))
            return true;
        UiHistoryShortcut history = UiHistoryShortcut.fromKey(keyCode, modifiers);
        boolean consumed = scope.content().keyPressed(keyCode, scanCode, modifiers);
        if (history != null) {
            if (!consumed && scope.history() != null) scope.history().accept(history);
            return true;
        }
        if (consumed) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            scope.escape();
            return true;
        }
        return scope.focus().keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 调整键释放匹配会话，其它释放用于内容修饰态处理。
     */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (active == null) return false;
        UiInteractionSession session = active.interaction();
        UiInteractionSession.Target target = session.target(UiInteractionSession.Source.KEY, keyCode);
        if (target != null) {
            boolean consumed = target.keyReleased(keyCode, scanCode, modifiers);
            session.end(target, UiInteractionSession.EndReason.RELEASE);
            return consumed;
        }
        return active.content().keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * 字符只送达逻辑焦点；复合控件负责内部编辑模式。
     */
    public boolean charTyped(char codePoint, int modifiers) {
        return active != null && active.content().charTyped(codePoint, modifiers);
    }

    /**
     * 关屏先取消未完成交互，再由宿主冲刷已提交草稿。
     */
    public void close() {
        UiInputScope previous = active;
        active = null;
        if (previous != null) previous.deactivate(UiInteractionSession.EndReason.HOST_CLOSED);
    }
}
