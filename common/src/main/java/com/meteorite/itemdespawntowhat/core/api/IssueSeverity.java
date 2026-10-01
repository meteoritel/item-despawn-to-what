package com.meteorite.itemdespawntowhat.core.api;

/**
 * 配置问题的严重级别。
 */
public enum IssueSeverity {
    // 会导致条目或文件被拒载
    ERROR,
    // 不影响加载，但需要提示使用者
    WARN
}
