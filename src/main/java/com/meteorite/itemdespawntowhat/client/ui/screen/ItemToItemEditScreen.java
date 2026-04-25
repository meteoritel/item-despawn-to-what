package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.client.ui.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.util.IdValidator;
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
    protected void initCustomValidators() {
        registerValidator(resultIdInput, IdValidator::isValidResultId);
    }

    @Override
    protected void addCustomSuggestion() {
        registerSuggestion(resultIdInput,
                SuggestionProvider.ofRegistry(BuiltInRegistries.ITEM));
    }
}
