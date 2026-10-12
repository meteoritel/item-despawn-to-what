package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * 控件结构样式的语义角色：宿主主题按角色给出 {@link UiControlStyle}，控件不硬编码「角色 → 颜色」的映射。
 *
 * <p>角色只表达控件在界面里的语义，不表达颜色；同一角色在不同主题下可以是完全不同的色板，
 * 因此 kit 只固定角色名，具体取值一律由宿主注入（见 {@link UiStyleSource}）。</p>
 *
 * <p>交互状态不属于角色：普通 / 悬停 / 按下 / 选中 / 禁用由控件在渲染时按自身标志解析，
 * 角色只决定取哪一套状态色，见 {@link UiControlStyle#background(UiControlStyle.State)}。</p>
 */
public enum UiControlRole {
    /** 次要操作：默认按钮、行内工具按钮等常规可点元素。 */
    SECONDARY,
    /** 主要操作：确认、提交等主路径动作。 */
    PRIMARY,
    /** 危险操作：删除、清空等不可逆动作。 */
    DANGER,
    /** 禁用占位：不可交互但需要保持版式的元素。 */
    DISABLED,
    /** 纯标签：只作视觉容器、不响应输入的底。 */
    LABEL
}
