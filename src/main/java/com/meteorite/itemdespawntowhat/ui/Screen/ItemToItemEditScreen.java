package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.ui.FormList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class ItemToItemEditScreen extends BaseConfigEditScreen<ItemToItemConfig>{
    private EditBox resultLimitInput;

    public ItemToItemEditScreen() {
        super(ConfigType.ITEM_TO_ITEM);
    }

    @Override
    protected void addCustomEntries(FormList fromList) {
        resultLimitInput = numericBox();
        fromList.add(Component.translatable(LABEL_PREFIX + "result_limit"), resultLimitInput);
    }

    @Override
    protected ItemToItemConfig createConfigFromFields() {
        ItemToItemConfig config = new ItemToItemConfig();
        populateCommonFields(config); // 填充通用字段

        config.setResultLimit(parseInt(resultLimitInput.getValue(), 30));
        return config;
    }

    @Override
    protected void clearCustomFields() {
        resultLimitInput.setValue("");
    }

    @Override
    protected void addCustomSuggestion() {
        resultIdSuggestion = registerSuggestion(resultIdInput, BuiltInRegistries.ITEM);
        addSuggestionListener(resultIdInput, resultIdSuggestion);
    }
}
