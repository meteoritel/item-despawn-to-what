package com.meteorite.itemdespawntowhat.core.api;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配置问题收集器：加载与校验过程中累积 Issue，供调用方决定拒载粒度与输出。
 * 本类不是线程安全的，仅在同一加载流程内传递使用。
 */
public final class IssueCollector {

    private final List<Issue> issues = new ArrayList<>();

    // 追加一条已构造好的问题
    public void add(Issue issue) {
        issues.add(issue);
    }

    // 追加一条错误级问题
    public void error(String message, @Nullable String origin, @Nullable String fieldPath) {
        issues.add(Issue.error(message, origin, fieldPath));
    }

    // 追加一条告警级问题
    public void warn(String message, @Nullable String origin, @Nullable String fieldPath) {
        issues.add(Issue.warn(message, origin, fieldPath));
    }

    // 累积另一个收集器的内容
    public void addAll(IssueCollector other) {
        issues.addAll(other.issues);
    }

    // 全部问题（不可变视图）
    public List<Issue> issues() {
        return Collections.unmodifiableList(issues);
    }

    // 仅错误级问题
    public List<Issue> errors() {
        return issues.stream().filter(i -> i.severity() == IssueSeverity.ERROR).toList();
    }

    // 仅告警级问题
    public List<Issue> warnings() {
        return issues.stream().filter(i -> i.severity() == IssueSeverity.WARN).toList();
    }

    // 是否存在错误级问题（用于决定条目/文件是否拒载）
    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == IssueSeverity.ERROR);
    }

    // 是否没有任何问题
    public boolean isEmpty() {
        return issues.isEmpty();
    }

    // 汇总为多行文本（每行一条），用于日志与命令回显
    public String format() {
        StringBuilder sb = new StringBuilder();
        for (Issue issue : issues) {
            if (!sb.isEmpty()) {
                sb.append(System.lineSeparator());
            }
            sb.append(issue.format());
        }
        return sb.toString();
    }
}
