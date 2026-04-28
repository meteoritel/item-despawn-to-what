package com.meteorite.itemdespawntowhat.condition;

import java.util.Map;

public interface ConditionSerializable<T> {

    // 将此配置对象的内容序列化写入Map中
    void toConditionMap(Map<String, String> out, String conditionKey);

    // 从源条件中找到此条件
    T fromConditionMap(Map<String, String> conditions, String conditionKey);
}
