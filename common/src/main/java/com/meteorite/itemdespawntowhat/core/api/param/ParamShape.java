package com.meteorite.itemdespawntowhat.core.api.param;

/**
 * 编辑器投影需要特殊形态的参数结构；普通单值参数使用 {@link #DEFAULT}。
 */
public enum ParamShape {

    /** 按参数自身类型常规投影。 */
    DEFAULT,

    /** 气候区间：对象只含可空的 min/max 两端，界面按区间控件展示。 */
    CLIMATE_RANGE,

    /** 枚举轮询：候选共用一个按钮轮换，界面只显示当前取值，点击切换到下一个。 */
    ENUM_CYCLE,

    /** 卡片列表：列表元素由引用与少量数值组成，界面把每一条渲染成一张紧凑卡片。 */
    CARD_LIST
}
