package com.meteorite.itemdespawntowhat.client.ui.screen;

import org.jetbrains.annotations.Nullable;

/** 客户端调试工具打开接缝，开发装配提供实现，生产界面只查询实际能力。 */
public final class DebugWorkbenchHooks {
    private static @Nullable Runnable opener;
    private DebugWorkbenchHooks() {}
    /** 客户端装配安装工具目录入口。 */
    public static void install(@Nullable Runnable action) { opener = action; }
    /** 当前客户端是否装配调试工具。 */
    public static boolean available() { return opener != null; }
    /** 打开已装配的工具目录。 */
    public static boolean open() {
        if (opener == null) { return false; }
        opener.run();
        return true;
    }
}
