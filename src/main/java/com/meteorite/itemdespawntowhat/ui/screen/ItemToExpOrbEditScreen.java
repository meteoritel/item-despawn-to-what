package com.meteorite.itemdespawntowhat.ui.screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import com.meteorite.itemdespawntowhat.ui.panel.FormListPanel;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class ItemToExpOrbEditScreen extends BaseConfigEditScreen<ItemToExpOrbConfig>{
    private EditBox xpPerItemInput;

    public ItemToExpOrbEditScreen() {
        super(ConfigType.ITEM_TO_XP_ORB);
    }

    @Override
    protected void addCustomEntries(FormListPanel fromList) {
        xpPerItemInput = numericBox();
        formList.add(Component.translatable(LABEL_PREFIX + "xp_pre_item"), xpPerItemInput);
    }

    @Override
    protected ItemToExpOrbConfig createConfigFromFields() {
        ItemToExpOrbConfig config = new ItemToExpOrbConfig();
        populateCommonFields(config);
        populateCustomFields(config);
        return config;
    }

    @Override
    protected void populateCustomFields(ItemToExpOrbConfig config) {
        config.setXpPerItem(parseInt(xpPerItemInput.getValue(), 1));
    }

    @Override
    protected void clearCustomFields() {
        xpPerItemInput.setValue("");
    }

    @Override
    protected void refillCustomFields(ItemToExpOrbConfig config) {
        xpPerItemInput.setValue(String.valueOf(config.getXpPerItem()));
    }

    @Override
    protected boolean shouldShowResultId() {
        return false;
    }
}
