package com.meteorite.itemdespawntowhat.client.ui.kit.api.text;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.control.UiWidget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/** 单行文本的提交、取消与会话通知；文本模型和呈现由实际编辑控件持有。 */
public interface UiSingleLineEditor extends UiWidget, UiTextValue {
    /** 设置 Enter 提交通知。 */
    UiSingleLineEditor setOnCommit(Consumer<String> listener);

    /** 设置 Esc 取消通知。 */
    UiSingleLineEditor setOnCancel(Runnable listener);

    /** 设置文本交互结束通知，组合控件据此提交或恢复自己的值。 */
    UiSingleLineEditor setOnInteractionEnd(@Nullable Consumer<UiInteractionSession.EndReason> listener);
}
