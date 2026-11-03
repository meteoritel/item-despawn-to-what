package com.meteorite.itemdespawntowhat.client.ui.kit.api.number;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;

/** 区间外壳共用的几何、可用性与交互能力，数值模型仍由具体核心提供。 */
public interface UiRangeControl extends UiFocusTarget, UiInteractionSession.Target {
    /** 当前 GUI 逻辑矩形。 */
    UiRect bounds();

    /** 设置矩形，负宽高按零处理。 */
    void setBounds(int x, int y, int width, int height);

    /** 是否启用。 */
    boolean isEnabled();

    /** 禁用时取消当前值操作。 */
    void setEnabled(boolean enabled);

    /** 是否可见。 */
    boolean isVisible();

    /** 隐藏时取消当前值操作。 */
    void setVisible(boolean visible);

    /** 是否持有指针拖动。 */
    boolean isDragging();

    /** 按原因提交或取消当前交互。 */
    void endInteraction(UiInteractionSession.EndReason reason);

    /** 当前正在编辑的端。 */
    UiRangeEnd selectedEnd();
}
