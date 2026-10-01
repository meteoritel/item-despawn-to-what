package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.Issue;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;

import java.util.List;

/**
 * 一次规则加载的结果：解码成功的规则列表 + 全过程问题记录。
 * issues 为可变收集器实例，加载完成后由调用方只读使用。
 */
public record RuleLoadResult<T>(List<LoadedRule<T>> rules, IssueCollector issues) {

    // 是否存在 ERROR 级问题（存在即视为本次加载不可全信，由调用方决定后续动作）
    public boolean hasErrors() {
        return issues.hasErrors();
    }

    // 是否存在 WARN 级问题
    public boolean hasWarnings() {
        return !issues.warnings().isEmpty();
    }

    // 供日志与命令回显使用的多行问题文本
    public String formatIssues() {
        return issues.format();
    }

    // 全部问题
    public List<Issue> allIssues() {
        return issues.issues();
    }
}
