package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.model.Rule;
import org.jetbrains.annotations.Nullable;

/**
 * 一次规则重载的回执：成功时携带加载结果，失败时携带一条结构化问题，二者恰有一个非空。
 * 重载失败发生在索引换入之前，旧索引、追踪与任务队列保持有效；调用方据此给出不谎报成功的回执，
 * 并与「运行时未就绪」（无回执）区分开。
 */
public record RuleReloadOutcome(@Nullable RuleLoadResult<Rule> result, @Nullable Issue failure) {

    // 本次重载已换入新索引
    public static RuleReloadOutcome applied(RuleLoadResult<Rule> result) {
        return new RuleReloadOutcome(result, null);
    }

    // 本次重载失败：failure 说明未应用的原因，没有可应用的新规则
    public static RuleReloadOutcome failed(Issue failure) {
        return new RuleReloadOutcome(null, failure);
    }

    // 本次重载是否已应用
    public boolean isApplied() {
        return result != null;
    }
}
