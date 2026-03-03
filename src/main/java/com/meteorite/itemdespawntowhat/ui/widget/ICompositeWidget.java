package com.meteorite.itemdespawntowhat.ui.widget;

import net.minecraft.client.gui.components.EditBox;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

// 复合组件统一管理接口
public interface ICompositeWidget {
    // 获取当前获得焦点的内部文本框
    @Nullable
    EditBox getInternalFocused();
    // 清除内部焦点
    void clearInternalFocus();

}
