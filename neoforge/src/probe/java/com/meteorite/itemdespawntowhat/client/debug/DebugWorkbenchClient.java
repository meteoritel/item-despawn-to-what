package com.meteorite.itemdespawntowhat.client.debug;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.client.ui.screen.DebugWorkbenchHooks;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** 仅开发客户端装配 Ctrl+F8 入口，正式产物不加载调试工具类。 */
@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
public final class DebugWorkbenchClient {
    private DebugWorkbenchClient() {}
    /** 客户端 tick 安装中立打开接缝。 */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { DebugWorkbenchHooks.install(DebugWorkbenchScreen::open); }
    /** 标题画面与普通 Screen 通过平台真实输入事件进入。 */
    @SubscribeEvent public static void screenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!shortcut(event.getKeyCode(), event.getModifiers()) || event.getScreen() instanceof RuleEditorScreen) { return; }
        DebugWorkbenchScreen.open(); event.setCanceled(true);
    }
    /** 没有 Screen 时处理游戏内同一快捷键。 */
    @SubscribeEvent public static void gameKey(InputEvent.Key event) {
        if (Minecraft.getInstance().screen == null && event.getAction() == GLFW.GLFW_PRESS && shortcut(event.getKey(), event.getModifiers())) { DebugWorkbenchScreen.open(); }
    }
    private static boolean shortcut(int key, int modifiers) { return key == GLFW.GLFW_KEY_F8 && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0; }
}
