package com.meteorite.itemdespawntowhat.client.edit;

/**
 * 编辑器字段类型：决定表单控件如何渲染、如何校验取值。
 * <p>本枚举只描述「字段长什么样」，不含具体参数语义；
 * 注册表访问等规则专用逻辑不进本包。
 */
public enum EditorFieldType {
    // 单行文本
    TEXT,
    // 多行文本（notes 等）
    LONG_TEXT,
    // 整数，取值域形如 "0..64"
    INTEGER,
    // 物品引用到数量的对象，每个物品独立显示数值滑条
    ITEM_COUNTS,
    // 小数，取值域形如 "0.0..1.0"
    DECIMAL,
    // 布尔开关
    BOOLEAN,
    // 枚举，取值域为逗号分隔的取值列表（JSON 一律小写）
    ENUM,
    // 资源位置（namespace:path，不允许标签）
    RESOURCE_LOCATION,
    // 注册表 id，取值域为注册表名（例如 minecraft:item），不允许标签
    REGISTRY_ID,
    // 可带标签（#）的 id，取值域为标签注册表名
    TAG,
    // 可带标签的 id 列表
    TAG_LIST,
    // 纯资源位置列表（不给标签模式）
    RL_LIST,
    // 字符串列表
    STRING_LIST,
    // 气候区间 [-1,1]，两端可空
    CLIMATE_RANGE,
    // 百分比：JSON 按声明域取值，界面显示原值 ×100
    PERCENT,
    // 药水等级：JSON 存 amplifier（0 起算），界面输入 1..255 并以罗马数字显示
    AMPLIFIER,
    // 刻数：整数，0 显示为「立即」
    TICKS,
    // 子列表（例如 conditions）：自身是对象数组，每项展开成完整子表单
    SUBLIST,
    // 卡片列表：对象数组，每项的引用与数值折进一张紧凑卡片
    CARD_LIST,
    // 单个嵌套对象：自身是映射，内部字段由 subFields 描述
    OBJECT,
    // 条件树（条件编辑器与效果编辑器共用同一控件）
    CONDITION_TREE,
    // 只读说明行，不写入 JSON
    NOTE,
    // 原始 JSON（第三方类型只读回退时使用）
    RAW_JSON
}
