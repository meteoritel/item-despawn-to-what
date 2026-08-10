package com.meteorite.itemdespawntowhat.config.conversion;

/**
 * 转换配置 DTO 的公开基类。
 */
public abstract class ConversionConfig {
    /** 校验配置数据。 */
    public abstract boolean validate();

    /** 返回条件复杂度。 */
    public abstract int complexity();

    /** 解析并初始化运行时引用。 */
    public abstract void resolve();
}
