package com.meteorite.itemdespawntowhat.core.api;

/**
 * 规则、效果、条件叶与覆盖层控制字段的 JSON 字段名常量。
 * 模型层、加载层与命令输出必须统一引用本类，禁止在业务代码中散落字面量。
 */
public final class RuleFields {

    /** ===== 规则级 ===== // */
    public static final String ID = "id";
    public static final String ENABLED = "enabled";
    public static final String PRIORITY = "priority";
    public static final String DISPLAY_NAME = "display_name";
    public static final String NOTES = "notes";
    public static final String SOURCE = "source";
    public static final String CONDITIONS = "conditions";
    public static final String EFFECTS = "effects";
    public static final String TRIGGER_AFTER_SECONDS = "trigger_after_seconds";
    /** 消失方式集合（数组）；缺省表示仅自然消失 */
    public static final String TRIGGERS = "triggers";
    /** 规则级源物品门槛和每轮扣除量；未配置引用默认 1 */
    public static final String SOURCE_COST = "source_cost";
    // 规则级催化剂配置（对象 {items, counts, consume_counts, radius}）；缺省表示未声明。
    /** 只接受对象写法；整数写法由 RuleCodecs 的解码前检查明确报错，不做静默兼容 */
    public static final String CATALYST_COST = "catalyst_cost";
    /** 规则级流体源消耗开关；沿触发位置接触流体的同族液位追溯。 */
    public static final String CONSUME_FLUID = "consume_fluid";
    /** 候选结果组合模式：round_robin（默认） / priority */
    public static final String COMBINATION = "combination";
    /** 候选结果集合（数组）；缺省表示由顶层 effects 隐式映射为唯一候选 */
    public static final String OUTCOMES = "outcomes";
    /** 规则契约结构版本（正整数）；当前仅支持 1 */
    public static final String SCHEMA_VERSION = "schema_version";
    /** 顶层可选展示分类：只被快照装配读取，不进入运行时规则模型，也不参与解码与执行 */
    public static final String CATEGORY = "category";

    // ===== 候选结果（outcomes 数组元素内部） ===== //
    /** 候选标识：规则内唯一 */
    public static final String CANDIDATE_ID = "id";
    /** 候选包含的效果列表（与顶层 effects 同形） */
    public static final String CANDIDATE_EFFECTS = "effects";
    /** 安全生成位置开关（水平 5x5、上下各 2 格）；默认关闭 */
    public static final String SAFE_SPAWN = "safe_spawn";
    /** 起点填充开关；默认开启 */
    public static final String FILL_ORIGIN = "fill_origin";

    // ===== 催化剂固定成本（catalyst_cost 对象内部） ===== //
    // 规则物品引用、门槛、实际消耗与共享半径；
    /** 常量在此独立声明，避免 core/api 反向依赖 core/type */
    public static final String CATALYST_ITEMS = "items";
    public static final String CATALYST_COUNT = "count";
    public static final String ITEM_COUNTS = "counts";
    /** 催化剂实际扣除数量；与 counts（存在门槛）分离。 */
    public static final String CONSUME_COUNTS = "consume_counts";
    public static final String FLUIDS = "fluids";
    public static final String FLUID = "fluid";
    public static final String CATALYST_RADIUS = "radius";

    /** ===== 源匹配（source 对象内部） ===== // */
    public static final String SOURCE_ITEMS = "items";
    public static final String SOURCE_EXCLUDE = "exclude";

    /** ===== 效果级通用 ===== // */
    public static final String TYPE = "type";
    public static final String DELAY_TICKS = "delay_ticks";
    public static final String CHANCE = "chance";

    // ===== 条件树节点 ===== //
    /** 节点类型字段，取值为 OP_ALL_OF / OP_ANY_OF / OP_INVERTED / OP_LEAF */
    public static final String OP = "op";
    /** all_of 与 any_of 的子节点列表 */
    public static final String TERMS = "terms";
    /** inverted 的单个子节点 */
    public static final String TERM = "term";
    /** leaf 携带的具体条件对象 */
    public static final String CONDITION = "condition";
    public static final String OP_ALL_OF = "all_of";
    public static final String OP_ANY_OF = "any_of";
    public static final String OP_INVERTED = "inverted";
    public static final String OP_LEAF = "leaf";

    // ===== 条件叶的叶级字段 ===== //
    /** 叶级取反字段：不受支持，条件树的解码器遇到它会明确报错，提示改用 inverted 节点 */
    public static final String NEGATED = "negated";

    // ===== 覆盖层控制字段 ===== //
    /** 覆盖层声明 disabled=true 表示停用同 id 的基底规则（规则本身仍可被读取） */
    public static final String DISABLED = "disabled";
    /** 覆盖层声明 delete=true 表示删除同 id 的基底规则 */
    public static final String DELETE = "delete";

    private RuleFields() {
        throw new UnsupportedOperationException("Utility class");
    }
}
