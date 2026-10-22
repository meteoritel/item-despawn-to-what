package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import org.jetbrains.annotations.Nullable;

/**
 * 一次规则重载的回执：成功时携带加载结果，失败时携带一条结构化问题，二者恰有一个非空。
 * 重载失败发生在索引换入之前，旧索引、追踪与任务队列保持有效；调用方据此给出不谎报成功的回执，
 * 并与「运行时未就绪」（无回执）区分开。
 * 对规则类型保持泛型：加载层只搬运结果，不引用模型，模型由调用方以类型实参传入。
 *
 * @param <T> 调用方使用的规则类型
 */
public record RuleReloadOutcome<T>(@Nullable RuleLoadResult<T> result, @Nullable Issue failure) {

    /**
     * 本次重载已换入新索引。
     *
     * @param result 已应用的加载结果
     * @param <T> 调用方使用的规则类型
     * @return 已应用的回执
     */
    public static <T> RuleReloadOutcome<T> applied(RuleLoadResult<T> result) {
        return new RuleReloadOutcome<>(result, null);
    }

    /**
     * 本次重载失败：失败发生在换入之前，没有可应用的新规则。
     *
     * @param failure 未应用的原因
     * @param <T> 调用方使用的规则类型
     * @return 未应用的回执
     */
    public static <T> RuleReloadOutcome<T> failed(Issue failure) {
        return new RuleReloadOutcome<>(null, failure);
    }

    /**
     * 本次重载是否已换入新索引。
     *
     * @return 已应用时为 true
     */
    public boolean isApplied() {
        return result != null;
    }
}
