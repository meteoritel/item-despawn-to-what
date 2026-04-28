package com.meteorite.itemdespawntowhat.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public class ModKeyBindings {
    public static final String KEY_CATEGORY = "key.category.itemdespawntowhat.general";
    public static final String KEY_NAME_OPEN_GUI = "key.itemdespawntowhat.open_gui";

    public static KeyMapping openGuiKey = new KeyMapping(
            KEY_NAME_OPEN_GUI,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY);
}
