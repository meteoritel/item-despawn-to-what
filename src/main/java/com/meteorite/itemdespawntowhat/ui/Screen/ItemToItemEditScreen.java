package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.ui.widget.FormListPanel;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class ItemToItemEditScreen extends BaseConfigEditScreen<ItemToItemConfig>{
    private EditBox resultLimitInput;

    public ItemToItemEditScreen() {
        super(ConfigType.ITEM_TO_ITEM);
    }

    @Override
    protected void addCustomEntries(FormListPanel fromList) {
        resultLimitInput = numericBox();
        fromList.add(Component.translatable(LABEL_PREFIX + "result_limit"), resultLimitInput);
    }

    @Override
    protected ItemToItemConfig createConfigFromFields() {
        ItemToItemConfig config = new ItemToItemConfig();
        populateCommonFields(config);
        populateCustomFields(config);
        return config;
    }

    @Override
    protected void populateCustomFields(ItemToItemConfig config) {
        config.setResultLimit(parseInt(resultLimitInput.getValue(), 30));
    }

    @Override
    protected void clearCustomFields() {
        resultLimitInput.setValue("");
    }

    @Override
    protected void refillCustomFields(ItemToItemConfig config) {
        resultLimitInput.setValue(String.valueOf(config.getResultLimit()));
    }

    @Override
    protected void addCustomSuggestion() {
        registerSuggestion(resultIdInput, BuiltInRegistries.ITEM);
    }
}
