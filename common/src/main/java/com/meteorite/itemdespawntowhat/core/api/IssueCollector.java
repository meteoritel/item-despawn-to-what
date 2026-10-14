package com.meteorite.itemdespawntowhat.core.api;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配置问题收集器：加载与校验过程中累积 Issue，供调用方决定拒载粒度与输出。
 * 本类不是线程安全的，仅在同一加载流程内传递使用。
 * 通过 {@link #originScoped(String)} 可派生出「来源继承」的子收集器，
 * 使只关心字段路径的公共校验也走同一条议题收集通道。
 */
public final class IssueCollector {

    private final List<Issue> issues = new ArrayList<>();

    // 派生收集器的上游与默认来源；parent 为 null 表示本实例是根收集器
    private final @Nullable IssueCollector parent;
    private final @Nullable String defaultOrigin;

    // 根收集器：没有上游，也不改写议题来源
    public IssueCollector() {
        this(null, null);
    }

    // 派生收集器的构造：上游与默认来源只能由 originScoped 给出
    private IssueCollector(@Nullable IssueCollector parent, @Nullable String defaultOrigin) {
        this.parent = parent;
        this.defaultOrigin = defaultOrigin;
    }

    /**
     * 派生一个「来源继承」的子收集器：写入子收集器的议题会同时落入本收集器，
     * 且议题自身 origin 为空时按给定 origin 补齐。
     * 用于把只关心字段路径的公共校验（如 RefChecks）接入统一议题通道，
     * 调用方不必逐条复制并重建 Issue；子收集器的读取接口只反映它自身收到的议题。
     *
     * @param origin 子收集器写入议题时使用的默认来源；null 表示保持议题原有来源
     * @return 新的子收集器，未写入时不影响本收集器
     */
    public IssueCollector originScoped(@Nullable String origin) {
        return new IssueCollector(this, origin);
    }

    // 追加一条已构造好的问题；派生收集器会向上游转发并按默认来源补齐 origin
    public void add(Issue issue) {
        Issue stamped = stampOrigin(issue);
        issues.add(stamped);
        if (parent != null) {
            parent.add(stamped);
        }
    }

    // 追加一条错误级问题
    public void error(String message, @Nullable String origin, @Nullable String fieldPath) {
        add(Issue.error(message, origin, fieldPath));
    }

    // 追加一条告警级问题
    public void warn(String message, @Nullable String origin, @Nullable String fieldPath) {
        add(Issue.warn(message, origin, fieldPath));
    }

    // 累积另一个收集器的内容
    public void addAll(IssueCollector other) {
        for (Issue issue : other.issues) {
            add(issue);
        }
    }

    // 按默认来源补齐议题的 origin；议题已有来源或本收集器未设默认来源时原样返回
    private Issue stampOrigin(Issue issue) {
        if (defaultOrigin == null || issue.origin() != null) {
            return issue;
        }
        return new Issue(issue.severity(), issue.message(), defaultOrigin, issue.fieldPath());
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
