package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;

import java.util.List;

/**
 * 一次规则加载的结果：解码成功的规则列表、全过程问题与同批原始来源索引。
 * issues 为可变收集器实例，加载完成后由调用方只读使用。
 */
public record RuleLoadResult<T>(List<LoadedRule<T>> rules, IssueCollector issues, RuleSourceIndex sourceIndex) {

    // 是否存在 ERROR 级问题（存在即视为本次加载不可全信，由调用方决定后续动作）
    public boolean hasErrors() {
        return issues.hasErrors();
    }

    // 全部问题
    public List<Issue> allIssues() {
        return issues.issues();
    }
}
