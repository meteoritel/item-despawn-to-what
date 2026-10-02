package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.edit.LiveEditorWorkspace;
import com.meteorite.itemdespawntowhat.client.net.EditorOpenRequest;
import com.meteorite.itemdespawntowhat.client.net.EditorScreenHooks;
import com.meteorite.itemdespawntowhat.client.net.EditorScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/***
 * 编辑器界面实现（契约 §5.3）：注册到 {@link EditorScreenHooks} 后，client/net 在服务端授权成功时调用。
 * 只负责把 {@link RuleEditorScreen} 切到前台或关闭，不发起/结束会话，不引用任何网络传输负载类型。
 * 打开前工作区已处于 ACTIVE 并已发出快照请求，界面自行轮询工作区取数据。
 */
public final class RuleEditorOpener implements EditorScreenOpener {

    // 单例：重复注册保持幂等
    private static final RuleEditorOpener INSTANCE = new RuleEditorOpener();

    // 最近一次收到的打开请求，仅供诊断
    private @Nullable EditorOpenRequest lastRequest;

    // 私有构造
    private RuleEditorOpener() {
    }

    // 客户端初始化时调用（Lead 在两端客户端初始化里接线）；幂等，可重复调用
    public static void bootstrap() {
        EditorScreenHooks.setOpener(INSTANCE);
    }

    // 注销界面实现（调试或销毁用）
    public static void shutdown() {
        EditorScreenHooks.setOpener(null);
        INSTANCE.lastRequest = null;
    }

    // 最近一次打开请求
    public static @Nullable EditorOpenRequest lastRequest() {
        return INSTANCE.lastRequest;
    }

    // 会话已授权：打开或刷新编辑器界面
    @Override
    public void open(EditorOpenRequest request) {
        lastRequest = request;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Screen current = minecraft.screen;
            if (current instanceof RuleEditorScreen screen) {
                screen.refreshFromWorkspace();
            } else {
                minecraft.setScreen(new RuleEditorScreen(LiveEditorWorkspace.instance()));
            }
        });
    }

    // 会话失效：只关闭界面，不触碰工作区会话（由协议层与租约驱动）
    @Override
    public void close() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof RuleEditorScreen) {
                minecraft.setScreen(null);
            }
        });
    }
}
