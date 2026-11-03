package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 宿主选择活动作用域，路由器统一执行切换、交互收尾与输入派发。
 */
public final class UiInputRouter {
    private @Nullable UiInputScope active;
    private final Map<Integer, UiPointerHit> pressedButtons = new HashMap<>();
    private long scopeVersion;

    /**
     * 同步宿主选定的作用域，身份未变时保留交互。
     */
    public void select(@Nullable UiInputScope next) {
        if (active == next) return;
        pressedButtons.clear();
        scopeVersion++;
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
        if (scope == null || scope != active) return false;
        long version = scopeVersion;
        pressedButtons.remove(context.button());
        scope.focus().setKeyboardMode(false);
        scope.interaction().end(UiInteractionSession.EndReason.FOCUS_TRANSFER);
        if (scope != active || scopeVersion != version) return true;
        UiPointerHit hit = scope.content().hitTest(context.pointerX(), context.pointerY());
        boolean consumed = scope.content().mousePressed(context.withHit(hit == null ? UiPointerHit.EMPTY : hit));
        if (scope == active && scopeVersion == version) {
            if (context.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) scope.finishPointer(hit);
            if (consumed && scope == active && scopeVersion == version) pressedButtons.put(context.button(), hit == null ? UiPointerHit.EMPTY : hit);
        }
        return consumed;
    }

    /**
     * 拖出矩形后继续送达原会话目标。
     */
    public boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        UiInputScope scope = active;
        if (scope == null || context.scope() != scope) return false;
        UiInteractionSession session = scope.interaction();
        UiInteractionSession.Target target = session.target(UiInteractionSession.Source.POINTER, context.button());
        return (target != null && target.mouseDragged(context, dragX, dragY)) || scope.content().blocksInput();
    }

    /**
     * 会话目标优先；同一作用域内已消费的按下在无会话时向内容释放一次。
     * 切域、关屏与重复释放均不复用旧按下。
     */
    public boolean mouseReleased(UiInputContext context) {
        UiInputScope scope = active;
        if (scope == null || context.scope() != scope) return false;
        UiPointerHit paired = pressedButtons.remove(context.button());
        UiInteractionSession session = context.scope().interaction();
        UiInteractionSession.Target target = session.target(UiInteractionSession.Source.POINTER, context.button());
        if (target == null) return (paired != null && context.scope().content().mouseReleased(context.withHit(paired)))
                || scope.content().blocksInput();
        boolean consumed = target.mouseReleased(context);
        session.end(target, UiInteractionSession.EndReason.RELEASE);
        return consumed || scope.content().blocksInput();
    }

    /**
     * 正常命中派发双轴滚轮。
     */
    public boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        if (context.scope() != active) return false;
        UiPointerHit hit = context.scope().content().hitTest(context.pointerX(), context.pointerY());
        return context.scope().content().mouseScrolled(context.withHit(hit == null ? UiPointerHit.EMPTY : hit), scrollX, scrollY);
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
            return scope.focus().keyPressed(keyCode, scanCode, modifiers) || scope.content().blocksInput();
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
        return scope.focus().keyPressed(keyCode, scanCode, modifiers) || scope.content().blocksInput();
    }

    /**
     * 调整键释放匹配会话，其它释放用于内容修饰态处理。
     */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        UiInputScope scope = active;
        if (scope == null) return false;
        UiInteractionSession session = scope.interaction();
        UiInteractionSession.Target target = session.target(UiInteractionSession.Source.KEY, keyCode);
        if (target != null) {
            boolean consumed = target.keyReleased(keyCode, scanCode, modifiers);
            session.end(target, UiInteractionSession.EndReason.RELEASE);
            return consumed || scope.content().blocksInput();
        }
        return scope.content().keyReleased(keyCode, scanCode, modifiers) || scope.content().blocksInput();
    }

    /**
     * 字符沿内容树的逻辑焦点路径派发；复合控件负责内部编辑模式与业务提交。
     */
    public boolean charTyped(char codePoint, int modifiers) {
        UiInputScope scope = active;
        return scope != null && (scope.content().charTyped(codePoint, modifiers) || scope.content().blocksInput());
    }

    /**
     * 关屏先取消未完成交互，再由宿主冲刷已提交草稿。
     */
    public void close() {
        pressedButtons.clear();
        scopeVersion++;
        UiInputScope previous = active;
        active = null;
        if (previous != null) previous.deactivate(UiInteractionSession.EndReason.HOST_CLOSED);
    }
}
