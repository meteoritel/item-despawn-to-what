package com.meteorite.itemdespawntowhat.ui.widget;

import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// 复合组件统一管理接口
public interface ICompositeWidget {

    // 获取当前获得焦点的内部文本框
    @Nullable
    EditBox getFocusedEditBox();

    // 设置内部文本框的焦点
    void setFocusedEditBox(@Nullable EditBox box);

    // 清除内部焦点
    void clearInternalFocus();

    // 检查鼠标点击是否由组件内部处理
    boolean handleMouseClicked(double mouseX, double mouseY, int button);

    // 获取所有内部文本框
    Collection<EditBox> getAllEditBoxes();
}
