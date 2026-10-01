package com.meteorite.itemdespawntowhat.core.api;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;

/**
 * 单条规则 JSON 对象的解码契约。
 * 由加载层（core/load）持有并注入，解码实现由模型层（core/model）提供；
 * 两侧都只依赖本接口，从而可并行开发。
 */
@FunctionalInterface
public interface RuleDecoder<T> {

    // 解码单条规则；失败时返回带可读原因的 DataResult.error，并允许向 issues 追加告警（如未知字段）
    DataResult<T> decode(JsonObject body, IssueCollector issues);
}
