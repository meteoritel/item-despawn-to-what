package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.ui.widget.FormListPanel;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class ItemToBlockEditScreen extends BaseConfigEditScreen<ItemToBlockConfig>{

    private EditBox radiusLimitInput;

    public ItemToBlockEditScreen() {
        super(ConfigType.ITEM_TO_BLOCK);
    }

    @Override
    protected void addCustomEntries(FormListPanel fromList) {
        radiusLimitInput = numericBox();
        fromList.add(Component.translatable(LABEL_PREFIX + "radius_limit"), radiusLimitInput);
    }

    @Override
    protected ItemToBlockConfig createConfigFromFields() {
        ItemToBlockConfig config = new ItemToBlockConfig();
        populateCommonFields(config);
        populateCustomFields(config);
        return config;
    }

    @Override
    protected void populateCustomFields(ItemToBlockConfig config) {
        config.setRadius(parseInt(radiusLimitInput.getValue(), 6));
    }

    @Override
    protected void clearCustomFields() {
        radiusLimitInput.setValue("");
    }

    @Override
    protected void refillCustomFields(ItemToBlockConfig config) {
        radiusLimitInput.setValue(String.valueOf(config.getRadius()));
    }

    @Override
    protected void addCustomSuggestion() {
        resultIdSuggestion = registerSuggestion(resultIdInput, BuiltInRegistries.BLOCK);
        addSuggestionListener(resultIdInput, resultIdSuggestion);
    }
}
