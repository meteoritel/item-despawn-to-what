package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import net.minecraft.network.chat.Component;

/** 普通与宿主特殊按钮的共同接入面，供模态等容器统一布局、快照和焦点。 */
public interface UiButtonWidget extends UiWidget, UiFocusTarget, UiLayoutSource {
    /** 使用自身字体量取标签、图标和留白。 */
    int preferredWidth(int padding);
    /** 原始本地化标签。 */
    Component label();
    /** 更新可用性并结束失效交互。 */
    UiButtonWidget setEnabled(boolean enabled);
}
