package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.ui.FormList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class ItemToBlockEditScreen extends BaseConfigEditScreen<ItemToBlockConfig>{

    private EditBox radiusLimitInput;

    public ItemToBlockEditScreen() {
        super(ConfigType.ITEM_TO_BLOCK);
    }

    @Override
    protected void addCustomEntries(FormList fromList) {
        radiusLimitInput = numericBox();
        fromList.add(Component.translatable(LABEL_PREFIX + "radius limit"), radiusLimitInput);
    }

    @Override
    protected ItemToBlockConfig createConfigFromFields() {
        ItemToBlockConfig config = new ItemToBlockConfig();
        populateCommonFields(config); // 填充通用字段
        config.setRadius(parseInt(radiusLimitInput.getValue(), 6));
        return config;
    }

    @Override
    protected void clearCustomFields() {
        radiusLimitInput.setValue("");
    }

}
