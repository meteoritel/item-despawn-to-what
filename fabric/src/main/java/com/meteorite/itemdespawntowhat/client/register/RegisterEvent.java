package com.meteorite.itemdespawntowhat.client.register;

import com.meteorite.itemdespawntowhat.client.key.ModKeyBindings;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * 注册 Fabric 客户端按键绑定。
 */
public class RegisterEvent {
    public static void register() {
        KeyBindingHelper.registerKeyBinding(ModKeyBindings.openGuiKey);
    }
}
