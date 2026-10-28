package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 按视觉顺序管理一个作用域的唯一逻辑焦点，键盘提示与业务选中分别表达。
 */
public final class UiFocusManager {
    /**
     * 宿主通过通知接入旁白与滚动到焦点。
     */
    public interface Listener {
        /**
         * 焦点已切换，原生控件适配亦已同步。
         */
        void focusChanged(@Nullable UiFocusTarget previous, @Nullable UiFocusTarget current);
    }

    private final UiInputScope scope;
    private final List<UiFocusTarget> targets = new ArrayList<>();
    private final List<UiFocusTarget> nextTargets = new ArrayList<>();
    private @Nullable UiFocusTarget focused;
    private @Nullable UiFocusTarget anchor;
    private @Nullable Listener listener;
    private boolean updating;
    private boolean suspended = true;
    private boolean keyboardMode;
    private boolean enterActivates = true;
    private boolean spaceActivates = true;

    UiFocusManager(UiInputScope scope) {
        this.scope = scope;
    }

    UiInputScope scope() {
        return scope;
    }

    @Nullable UiFocusTarget current() {
        return suspended ? null : focused;
    }

    boolean keyboardMode() {
        return keyboardMode;
    }

    boolean containsFocus(@Nullable UiFocusTarget target) {
        return !suspended && focused != null && target != null && (focused == target || anchor == target || target.focusTargets().contains(focused));
    }

    /**
     * 显式记录输入方式，只影响额外焦点提示。
     */
    public void setKeyboardMode(boolean keyboardMode) {
        this.keyboardMode = keyboardMode;
    }

    /**
     * 开始替换视觉顺序；未重新登记的目标在结束更新时卸载。
     */
    public void beginUpdate() {
        nextTargets.clear();
        updating = true;
    }

    /**
     * 登记目标，同时将包装控件与核心的焦点视图接入本作用域。
     */
    public void add(UiFocusTarget target) {
        Objects.requireNonNull(target);
        target.focusState().attach(this, target);
        List<UiFocusTarget> children = target.focusTargets();
        if (!(children.size() == 1 && children.getFirst() == target)) {
            for (UiFocusTarget child : children) add(child);
            return;
        }
        List<UiFocusTarget> destination = updating ? nextTargets : targets;
        if (!destination.contains(target)) destination.add(target);
        target.focusState().attach(this, target);
    }

    /**
     * 完成登记并取消已卸载目标的未完成交互。
     */
    public void endUpdate() {
        if (updating) {
            if (focused != null && !nextTargets.contains(anchor == null ? focused : anchor))
                invalidate(UiInteractionSession.EndReason.UNMOUNTED);
            targets.clear();
            targets.addAll(nextTargets);
        }
        nextTargets.clear();
        updating = false;
        refresh();
    }

    /**
     * 卸载全部焦点目标。
     */
    public void clear() {
        invalidate(UiInteractionSession.EndReason.UNMOUNTED);
        targets.clear();
        nextTargets.clear();
        updating = false;
    }

    /**
     * 登记数量。
     */
    public int size() {
        return targets.size();
    }

    /**
     * 只读视觉顺序。
     */
    public List<UiFocusTarget> targets() {
        return List.copyOf(targets);
    }

    /**
     * 活动作用域内的当前目标。
     */
    public @Nullable UiFocusTarget focused() {
        refresh();
        return current();
    }

    /**
     * 当前目标在视觉序列中的位置，无焦点时为 -1。
     */
    public int focusedIndex() {
        UiFocusTarget target = focused();
        return target == null ? -1 : targets.indexOf(anchor == null ? target : anchor);
    }

    /**
     * 进入复合控件的内部文本模式，不创建第二个焦点所有者。
     */
    public boolean focusWithin(UiFocusTarget group, UiFocusTarget child) {
        UiFocusTarget stop = targets.contains(group) ? group : anchor;
        if (stop == null || !targets.contains(stop) || !child.canFocus()) return false;
        child.focusState().attach(this, child);
        setFocusedInternal(child);
        anchor = stop;
        return true;
    }

    /**
     * 请求焦点；目标须已登记且当前具备焦点资格。
     */
    public boolean focusOn(@Nullable UiFocusTarget target) {
        if (target == null) return clearFocus();
        if (!targets.contains(target)) {
            for (UiFocusTarget registered : targets) {
                if (registered.focusState() == target.focusState()) {
                    target = registered;
                    break;
                }
            }
        }
        if (!targets.contains(target) || !target.canFocus()) return false;
        setFocusedInternal(target);
        anchor = target;
        return true;
    }

    /**
     * 清除当前焦点，完成该目标尚未结清的有效编辑。
     */
    public boolean clearFocus() {
        if (focused == null) return false;
        setFocusedInternal(null);
        return true;
    }

    /**
     * 选择首个合格目标。
     */
    public boolean focusFirst() {
        for (UiFocusTarget target : targets) if (target.canFocus()) return focusOn(target);
        return false;
    }

    /**
     * Tab 在合格目标间按视觉顺序环绕。
     */
    public boolean moveFocus(int delta) {
        refresh();
        List<UiFocusTarget> available = targets.stream().filter(UiFocusTarget::canFocus).toList();
        if (available.isEmpty()) return false;
        int index = available.indexOf(anchor == null ? focused : anchor);
        int next = index < 0 ? (delta < 0 ? available.size() - 1 : 0) : Math.floorMod(index + delta, available.size());
        return focusOn(available.get(next));
    }

    /**
     * 激活当前焦点，不改变业务选中状态的归属。
     */
    public boolean activateFocused() {
        UiFocusTarget target = focused();
        return target != null && target.activate();
    }

    /**
     * 处理 Tab 和内容未消费的 Enter/Space。
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) return moveFocus((modifiers & GLFW.GLFW_MOD_SHIFT) == 0 ? 1 : -1);
        if (enterActivates && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER))
            return activateFocused();
        UiFocusTarget target = focused();
        return spaceActivates && keyCode == GLFW.GLFW_KEY_SPACE && target != null && !target.acceptsTextInput() && activateFocused();
    }

    /**
     * 宿主的 Enter 激活政策。
     */
    public void setEnterActivates(boolean value) {
        enterActivates = value;
    }

    /**
     * 宿主的 Space 激活政策。
     */
    public void setSpaceActivates(boolean value) {
        spaceActivates = value;
    }

    /**
     * 接入旁白和滚动通知。
     */
    public void setListener(@Nullable Listener value) {
        listener = value;
    }

    void suspend() {
        UiFocusTarget previous = current();
        suspended = true;
        if (previous != null) previous.focusChanged(false);
    }

    void resume() {
        refresh();
        suspended = false;
        if (focused == null) focusFirst();
        else {
            focused.focusChanged(true);
            if (listener != null) listener.focusChanged(null, focused);
        }
    }

    private void refresh() {
        if (focused != null && (!targets.contains(anchor == null ? focused : anchor) || !focused.canFocus()))
            invalidate(UiInteractionSession.EndReason.UNMOUNTED);
    }

    private void invalidate(UiInteractionSession.EndReason reason) {
        if (focused == null) return;
        endFocusedInteraction(reason);
        setFocusedInternal(null);
    }

    private void endFocusedInteraction(UiInteractionSession.EndReason reason) {
        UiInteractionSession.Target target = scope.interaction().target();
        if (target instanceof UiFocusTarget focusTarget && focused != null && focusTarget.focusState() == focused.focusState()) {
            scope.interaction().end(reason);
        }
    }

    private void setFocusedInternal(@Nullable UiFocusTarget next) {
        if (focused == next) return;
        UiFocusTarget previous = focused;
        endFocusedInteraction(UiInteractionSession.EndReason.FOCUS_TRANSFER);
        focused = next;
        if (next == null) anchor = null;
        if (!suspended) {
            if (previous != null) previous.focusChanged(false);
            if (next != null) next.focusChanged(true);
            if (listener != null) listener.focusChanged(previous, next);
        }
    }
}
