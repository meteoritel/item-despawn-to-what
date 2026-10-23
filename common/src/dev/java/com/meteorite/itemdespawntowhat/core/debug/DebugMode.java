package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.platform.Services;

/** 由加载器判断开发环境；IDEA Run 与 Debug 均自动启用，发布环境没有场景入口。 */
public final class DebugMode {
    public static final boolean ENABLED = Services.PLATFORM.isDevelopmentEnvironment();

    // 工具类不创建实例。
    private DebugMode() {}
}
