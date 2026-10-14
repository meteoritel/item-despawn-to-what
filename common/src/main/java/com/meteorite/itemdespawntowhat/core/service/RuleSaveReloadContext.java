package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.load.RawRuleEntry;
import com.meteorite.itemdespawntowhat.core.api.Issue;
import java.util.List;

/**
 * 保存重载的只读基底快照；身份用于发现保存期间的数据包重载，后台仅合并原始 JSON。
 * @param loadContext 服务端线程解码与校验所需的上下文
 * @param baseRules 数据包层原始条目，发布后不再修改
 * @param baseIssues 数据包文件读取与解析问题
 */
public record RuleSaveReloadContext(RuleLoadContext loadContext, List<RawRuleEntry> baseRules, List<Issue> baseIssues) {
    /**
     * 固定基底条目集合，避免后台与发布方共享可变容器。
     * @param loadContext 加载上下文
     * @param baseRules 原始基底条目
     * @param baseIssues 基底文件问题
     */
    public RuleSaveReloadContext {
        baseRules = List.copyOf(baseRules);
        baseIssues = List.copyOf(baseIssues);
    }
}
