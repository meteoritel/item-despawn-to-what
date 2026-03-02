package com.meteorite.itemdespawntowhat.condition;

import java.util.Map;

public interface ConditionSerializable<T> {
    /**
     * 将此配置对象的内容序列化写入 {@code out}。
     *
     * @param out          目标条件 Map
     * @param conditionKey 该检查器在 Map 中的键名（或键名前缀）
     */
    void toConditionMap(Map<String, String> out, String conditionKey);

    /**
     * 从 {@code conditions} 中读取以 {@code conditionKey} 为前缀的条目，
     * 填充 {@code this} 并返回；若无有效数据则返回 {@code null}。
     *
     * @param conditions   源条件 Map
     * @param conditionKey 该检查器在 Map 中的键名（或键名前缀）
     * @return 填充好的 {@code this}，或 {@code null}
     */
    T fromConditionMap(Map<String, String> conditions, String conditionKey);
}
