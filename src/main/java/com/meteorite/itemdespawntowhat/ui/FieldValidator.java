package com.meteorite.itemdespawntowhat.ui;

import net.minecraft.network.chat.Component;

// 字段校验器接口，用于对表单输入框的值进行合法性校验
public interface FieldValidator {
    // 返回 true 表示值合法
    boolean validate(String value);
    // 返回校验失败时显示的错误提示
    Component getErrorMessage();
}
