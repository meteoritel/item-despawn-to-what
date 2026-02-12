package com.meteorite.itemdespawntowhat.util;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
// 用于json文件中的变量排序
// 注解的数字越小优先级越高，不注解默认排在最后
public @interface JsonOrder {
    int value() default 0;
}
