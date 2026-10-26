package com.meteorite.itemdespawntowhat.core.api.param;

/**
 * 参数值的语义单位，编辑器投影据此选择控件形态（刻数、百分比、等级、件数或普通数值）。
 */
public enum ParamUnit {

    /** 无特殊语义的普通数值。 */
    PLAIN,

    /** 刻数：界面按秒换算显示。 */
    TICKS,

    /** 概率：JSON 存 0..1，界面显示百分比。 */
    PERCENT,

    /** 等级：JSON 存 0 起算的 amplifier，界面显示 1 起算的等级。 */
    LEVEL,

    /** 件数：每个引用各自保存数量。 */
    COUNT
}
