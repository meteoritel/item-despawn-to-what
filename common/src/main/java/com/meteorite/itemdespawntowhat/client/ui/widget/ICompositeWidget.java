package com.meteorite.itemdespawntowhat.client.ui.widget;

import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 复合表单控件统一暴露内部焦点目标。
 */
public interface ICompositeWidget {
    // 获取当前获得焦点的内部文本框
    @Nullable
    EditBox getInternalFocused();

    // 返回稳定顺序的内部输入框，供表单统一遍历焦点和校验。
    List<EditBox> getInternalEditBoxes();

    // 将焦点移动到指定内部输入框。
    void focusInternal(EditBox editBox);

    // 清除内部焦点
    void clearInternalFocus();
}
