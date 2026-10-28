package com.meteorite.itemdespawntowhat.client.ui.kit;

import java.util.Objects;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

/**
 * 稳定内容实例的输入作用域，拥有唯一焦点管理器和持续交互会话。
 */
public final class UiInputScope {
    private final UiInputTarget content;
    private final UiFocusManager focus = new UiFocusManager(this);
    private final UiInteractionSession interaction = new UiInteractionSession();
    private @Nullable Consumer<UiHistoryShortcut> history;
    private Runnable escape = () -> {
    };
    private Runnable refreshFocus = () -> {
    };
    private boolean active;
    private boolean pointerHit;
    private @Nullable UiFocusTarget pointerFocus;

    /**
     * 宿主注入内容，作用域不解释页面或模态类型。
     */
    public UiInputScope(UiInputTarget content) {
        this.content = Objects.requireNonNull(content);
    }

    /**
     * 当前作用域的内容输入目标。
     */
    public UiInputTarget content() {
        return content;
    }

    /**
     * 唯一焦点管理器。
     */
    public UiFocusManager focus() {
        return focus;
    }

    /**
     * 唯一持续交互登记。
     */
    public UiInteractionSession interaction() {
        return interaction;
    }

    /**
     * 是否接收输入与 hover。
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 注入局部历史未消费时的宿主历史入口。
     */
    public void setHistory(@Nullable Consumer<UiHistoryShortcut> history) {
        this.history = history;
    }

    /**
     * 注入交互与内容未消费 Esc 时的退出动作。
     */
    public void setEscape(Runnable escape) {
        this.escape = Objects.requireNonNull(escape);
    }

    /**
     * 内容重建后重新登记实际焦点目标，输入前按需调用。
     */
    public void setFocusTargets(Runnable refreshFocus) {
        this.refreshFocus = Objects.requireNonNull(refreshFocus);
    }

    /**
     * 同步已完成布局的焦点目标，不改变仍有效的焦点。
     */
    public void refreshFocus() {
        refreshFocus.run();
    }

    @Nullable Consumer<UiHistoryShortcut> history() {
        return history;
    }

    void escape() {
        escape.run();
    }

    void activate() {
        active = true;
        focus.resume();
    }

    void deactivate(UiInteractionSession.EndReason reason) {
        active = false;
        interaction.end(reason);
        content.scopeExited(reason);
        focus.suspend();
    }

    void beginPointer() {
        pointerHit = false;
        pointerFocus = null;
    }

    /**
     * 实际命中的叶子报告焦点候选；null 是可交互但保留原焦点的区域。
     */
    public void pointerHit(@Nullable UiFocusTarget target) {
        pointerHit = true;
        pointerFocus = target;
    }

    void finishPointer() {
        if (!active) return;
        if (!pointerHit) focus.clearFocus();
        else if (pointerFocus != null && pointerFocus.canFocus()) focus.focusOn(pointerFocus);
    }
}
