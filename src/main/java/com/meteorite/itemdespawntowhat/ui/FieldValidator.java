package com.meteorite.itemdespawntowhat.ui;

// 字段校验器函数式接口，用于对表单输入框的值进行合法性校验
@FunctionalInterface
public interface FieldValidator {
    // 返回 true 表示值合法
    boolean validate(String value);
}
