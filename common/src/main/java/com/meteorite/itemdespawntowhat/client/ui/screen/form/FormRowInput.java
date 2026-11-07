package com.meteorite.itemdespawntowhat.client.ui.screen.form;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContainer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** 稳定行身份的输入边界；成员次序同时用于绘制、命中和导航。 */
final class FormRowInput implements UiInputContainer {
    private final FormRow row;
    private final UiChildren<UiInputTarget> members = new UiChildren<>();

    FormRowInput(FormRow row) { this.row = row; }

    void refresh() {
        members.clear();
        members.add(row.control);
        members.addAll(row.buttons);
    }

    /** 行内唯一成员结构。 */
    @Override public UiChildren<UiInputTarget> inputChildren() { return members; }

    /** 卸载和隐藏行不再贡献命中。 */
    @Override public @Nullable UiPointerHit hitTest(double x, double y) {
        return row.attached && row.visible ? UiInputContainer.super.hitTest(x, y) : null;
    }

    /** 卸载和隐藏行不再贡献焦点。 */
    @Override public void addFocusTargets(Consumer<UiFocusTarget> out) {
        if (row.attached && row.visible) members.addFocusTargets(out);
    }

    /** 隐藏和卸载行的旧焦点不参与普通派发。 */
    @Override public boolean hasFocus() { return row.attached && row.visible && members.hasFocus(); }

    /** 资格变化与成员变化一起使作用域索引失效。 */
    @Override public long focusRevision() {
        return 31 * members.focusRevision() + (row.visible ? 1 : 0) + (row.attached ? 2 : 0);
    }
}
