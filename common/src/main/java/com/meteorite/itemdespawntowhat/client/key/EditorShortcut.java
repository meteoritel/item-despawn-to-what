package com.meteorite.itemdespawntowhat.client.key;

import com.meteorite.itemdespawntowhat.client.net.EditorOpenRequest;
import com.meteorite.itemdespawntowhat.client.net.EditorScreenHooks;
import com.meteorite.itemdespawntowhat.client.net.EditorScreenOpener;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientWorkspace;
import com.meteorite.itemdespawntowhat.client.ui.prototype.UiPrototypeScreen;
import com.meteorite.itemdespawntowhat.core.debug.DebugMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/***
 * 编辑器快捷键的共用逻辑（契约 §5.3）：快捷键只做「重开已有会话的界面」，
 * 绝不自行发起编辑会话；没有会话时只提示玩家使用 /idtw config edit 指令。
 * <p>开发环境（{@link DebugMode#ENABLED}）下没有会话时改为打开 UI kit 原型屏，便于美术核对；
 * 该分支在发布环境不存在，因此不构成绕过编辑锁的生产入口。
 */
public final class EditorShortcut {

    // 无会话时的提示文案
    public static final String HINT_KEY = "gui.itemdespawntowhat.edit.hint.use_command";

    private EditorShortcut() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 处理一次快捷键：已有会话且界面实现已注册则重开界面，否则提示（开发环境先开原型屏）
    public static void activate() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        RuleEditClientWorkspace workspace = RuleEditClientWorkspace.instance();
        EditorScreenOpener opener = EditorScreenHooks.opener();
        EditorOpenRequest request = workspace.state() == RuleEditClientState.FREE ? null : workspace.openRequest();
        if (opener != null && request != null) {
            // 只重开界面：不改变工作区状态，也不向服务端重复请求授权
            opener.open(request);
            return;
        }
        Screen prototype = prototypeScreen();
        if (prototype != null) {
            client.setScreen(prototype);
            return;
        }
        client.player.displayClientMessage(Component.translatable(HINT_KEY), true);
    }

    // 开发环境的 UI kit 原型屏；发布环境返回 null
    private static Screen prototypeScreen() {
        return DebugMode.ENABLED ? new UiPrototypeScreen() : null;
    }
}
