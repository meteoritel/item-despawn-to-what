package com.meteorite.itemdespawntowhat.core.model;

import com.meteorite.itemdespawntowhat.core.api.RuleDecodeBudget;

/**
 * 条件树与规则的规模上限：校验、序列化与编辑器共用同一套常量，避免各处写死魔数。
 * 条件树的深度与节点预算与契约层的 {@link RuleDecodeBudget} 同源，解码期与校验期不可能出现两份口径。
 */
public final class ConditionLimits {

    // 单条规则条件树的最大叶数
    public static final int MAX_LEAVES = 128;

    // 单条规则条件树的最大节点数（组合节点与叶都计入）；取值同解码期预算
    public static final int MAX_NODES = RuleDecodeBudget.MAX_CONDITION_NODES;

    // 条件树最大深度，根节点深度为 1；取值同解码期预算
    public static final int MAX_DEPTH = RuleDecodeBudget.MAX_CONDITION_DEPTH;

    // 单条规则的最大效果数
    public static final int MAX_EFFECTS = 32;

    // 源匹配器（source）最大条目数
    public static final int MAX_SOURCE_ENTRIES = 256;

    // 规则显示名（display_name）最大码点数
    public static final int MAX_DISPLAY_NAME_CODEPOINTS = 128;

    // 工具类不创建实例。
    private ConditionLimits() {}
}
