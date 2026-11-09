package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;


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
    private boolean focusDirty = true;
    private long focusRevision = Long.MIN_VALUE;
    private boolean active;

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

    /** 布局完成后创建绘制快照，作用域与持续指针交互共同决定门禁。 */
    public UiPointer pointer(double x, double y) {
        return new UiPointer(x, y, active, interaction.pointerTarget(), null);
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
    public void invalidateFocus() {
        focusDirty = true;
    }

    /**
     * 同步已完成布局的焦点目标，不改变仍有效的焦点。
     */
    public void refreshFocus() {
        long revision = content.focusRevision();
        if (focusDirty || focusRevision != revision) {
            focusDirty = false;
            try {
                focus.updateTargets(registration -> content.addFocusTargets(registration::add));
                focusRevision = revision;
            } catch (RuntimeException failure) {
                focusDirty = true;
                throw failure;
            }
        }
        focus.requireUpdateComplete();
        focus.focused();
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

    void finishPointer(@Nullable UiPointerHit hit) {
        if (!active) return;
        if (hit == null) focus.clearFocus();
        else if (hit.focus() != null && hit.focus().canFocus()) focus.focusOn(hit.focus());
    }
}
