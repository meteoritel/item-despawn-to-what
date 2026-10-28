package com.meteorite.itemdespawntowhat.core.api;

import java.util.List;

/**
 * 规则、效果与条件叶的 JSON 字段名常量。
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

    /** ===== 效果通用字段 ===== // */
    public static final String TYPE = "type";
    public static final String DELAY_TICKS = "delay_ticks";
    public static final String CHANCE = "chance";
    /** 单个行为相对成功触发点的三轴整数偏移。 */
    public static final String OFFSET = "offset";

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

    // ===== 条件叶参数（条件对象内部） ===== //
    // 条件叶与效果对象存在同拼写字段（items / count / mode），但分属不同 JSON 对象、语义不同，
    // 因此按条件语义在此单独登记，不与效果侧或规则成本侧常量混用；客户端读写与表单描述符共用这一组。
    /** 维度白名单（数组）；命中任一即满足 */
    public static final String CONDITION_DIMENSIONS = "dimensions";
    /** 群系模式字段；取值见 CONDITION_MODE_EXACT / CONDITION_MODE_CLIMATE */
    public static final String CONDITION_MODE = "mode";
    /** 群系模式取值：按 CONDITION_BIOMES 列表匹配 */
    public static final String CONDITION_MODE_EXACT = "exact";
    /** 群系模式取值：按 CONDITION_AXES 的各气候轴区间匹配 */
    public static final String CONDITION_MODE_CLIMATE = "climate";
    /** 群系列表（数组）；仅 exact 模式读取 */
    public static final String CONDITION_BIOMES = "biomes";
    /** 天气条件的天气种类字段；取值由天气枚举定义 */
    public static final String CONDITION_WEATHER = "weather";
    /** 周围方块条件的单个方向字段；方向名同时是表单标签键的后缀 */
    public static final String CONDITION_SIDE_UP = "up";
    public static final String CONDITION_SIDE_DOWN = "down";
    public static final String CONDITION_SIDE_NORTH = "north";
    public static final String CONDITION_SIDE_SOUTH = "south";
    public static final String CONDITION_SIDE_EAST = "east";
    public static final String CONDITION_SIDE_WEST = "west";
    /** 周围方块条件的六方向字段，顺序即表单与摘要的展示顺序 */
    public static final List<String> CONDITION_SIDES = List.of(
            CONDITION_SIDE_UP, CONDITION_SIDE_DOWN, CONDITION_SIDE_NORTH,
            CONDITION_SIDE_SOUTH, CONDITION_SIDE_EAST, CONDITION_SIDE_WEST);
    /** 催化剂存在条件的物品列表 */
    public static final String CONDITION_ITEMS = "items";
    /** 催化剂存在条件的默认数量；逐物品数量仍看 ITEM_COUNTS */
    public static final String CONDITION_COUNT = "count";
    /** 时刻条件的区间始末刻（24 小时制，0..23999） */
    public static final String CONDITION_FROM = "from";
    public static final String CONDITION_TO = "to";
    /** 数值区间条件的下界与上界；y_level 与 light_level 共用 */
    public static final String CONDITION_MIN = "min";
    public static final String CONDITION_MAX = "max";
    /** 气候轴字段；每个轴是一个 {min, max} 区间对象，仅在 climate 模式下读取 */
    public static final String CONDITION_AXIS_TEMPERATURE = "temperature";
    public static final String CONDITION_AXIS_HUMIDITY = "humidity";
    public static final String CONDITION_AXIS_CONTINENTALNESS = "continentalness";
    public static final String CONDITION_AXIS_EROSION = "erosion";
    public static final String CONDITION_AXIS_DEPTH = "depth";
    public static final String CONDITION_AXIS_WEIRDNESS = "weirdness";
    /** 气候轴的读取与展示顺序，与编辑器表单一致 */
    public static final List<String> CONDITION_AXES = List.of(
            CONDITION_AXIS_TEMPERATURE, CONDITION_AXIS_HUMIDITY, CONDITION_AXIS_CONTINENTALNESS,
            CONDITION_AXIS_EROSION, CONDITION_AXIS_DEPTH, CONDITION_AXIS_WEIRDNESS);

    private RuleFields() {
        throw new UnsupportedOperationException("Utility class");
    }
}
