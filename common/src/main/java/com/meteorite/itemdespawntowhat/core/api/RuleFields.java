package com.meteorite.itemdespawntowhat.core.api;

/**
 * 规则、效果、条件叶与覆盖层控制字段的 JSON 字段名常量。
 * 模型层、加载层与命令输出必须统一引用本类，禁止在业务代码中散落字面量。
 */
public final class RuleFields {

    // ===== 规则级 ===== //
    public static final String ID = "id";
    public static final String ENABLED = "enabled";
    public static final String PRIORITY = "priority";
    public static final String DISPLAY_NAME = "display_name";
    public static final String NOTES = "notes";
    public static final String SOURCE = "source";
    public static final String CONDITIONS = "conditions";
    public static final String EFFECTS = "effects";
    public static final String TRIGGER_AFTER_SECONDS = "trigger_after_seconds";

    // ===== 源匹配（source 对象内部） ===== //
    public static final String SOURCE_ITEMS = "items";
    public static final String SOURCE_EXCLUDE = "exclude";

    // ===== 效果级通用 ===== //
    public static final String TYPE = "type";
    public static final String DELAY_TICKS = "delay_ticks";
    public static final String CHANCE = "chance";

    // ===== 条件树节点 ===== //
    // 节点类型字段，取值为 OP_ALL_OF / OP_ANY_OF / OP_INVERTED / OP_LEAF
    public static final String OP = "op";
    // all_of 与 any_of 的子节点列表
    public static final String TERMS = "terms";
    // inverted 的单个子节点
    public static final String TERM = "term";
    // leaf 携带的具体条件对象
    public static final String CONDITION = "condition";
    public static final String OP_ALL_OF = "all_of";
    public static final String OP_ANY_OF = "any_of";
    public static final String OP_INVERTED = "inverted";
    public static final String OP_LEAF = "leaf";

    // ===== 条件叶（旧格式） ===== //
    // 旧版叶级取反字段：已废弃，条件树的解码器遇到它会明确报错，提示改用 inverted
    public static final String NEGATED = "negated";

    // ===== 覆盖层控制字段 ===== //
    // 覆盖层声明 disabled=true 表示停用同 id 的基底规则（规则本身仍可被读取）
    public static final String DISABLED = "disabled";
    // 覆盖层声明 delete=true 表示删除同 id 的基底规则
    public static final String DELETE = "delete";

    private RuleFields() {
        throw new UnsupportedOperationException("Utility class");
    }
}
