package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiChildren;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContainer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;

/** 页面与公共 chrome 共用作用域，命中和 Tab 分别保留各自顺序。 */
final class EditorPageInputRoot implements UiInputContainer {
    private final UiChildren<UiInputTarget> members = new UiChildren<>();

    EditorPageInputRoot(EditorPage page, UiButton themeButton) {
        members.add(page);
        members.add(themeButton);
        members.setTabOrder(themeButton, -1);
    }

    /** 页面和 chrome 的唯一成员登记。 */
    @Override
    public UiChildren<UiInputTarget> inputChildren() {
        return members;
    }
}
