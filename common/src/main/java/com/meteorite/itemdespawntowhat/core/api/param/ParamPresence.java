package com.meteorite.itemdespawntowhat.core.api.param;

/**
 * 参数在 JSON 对象中的出现形态，决定编解码时使用哪种字段构造方式。
 * <p>与「编辑器是否强制填写」解耦：wire presence 描述后端契约，编辑器标记由 {@link ParamMeta#editorRequired()} 单独表达。</p>
 */
public enum ParamPresence {

    /** 必填：字段缺失即解码失败。 */
    REQUIRED,

    /** 可省略：字段缺失时按默认值补齐，编码时照常写出。 */
    DEFAULTED,

    /** 可空：字段缺失解码为 null，编码时省略该字段。 */
    NULLABLE
}
