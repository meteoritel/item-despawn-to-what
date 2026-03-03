package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.ItemToEntityConfig;
import com.meteorite.itemdespawntowhat.ui.widget.FormListPanel;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class ItemToEntityEditScreen extends BaseConfigEditScreen<ItemToEntityConfig> {
    private EditBox resultLimitInput;
    private EditBox entityAgeInput;

    public ItemToEntityEditScreen() {
        super(ConfigType.ITEM_TO_ENTITY);
    }

    @Override
    protected void addCustomEntries(FormListPanel fromList) {
        resultLimitInput = numericBox();
        entityAgeInput = numericBox();

        fromList.add(Component.translatable(LABEL_PREFIX + "result_limit"), resultLimitInput);
        fromList.add(Component.translatable(LABEL_PREFIX + "result_age"), entityAgeInput);
    }

    @Override
    protected ItemToEntityConfig createConfigFromFields() {
        ItemToEntityConfig config = new ItemToEntityConfig();
        populateCommonFields(config);
        populateCustomFields(config);
        return config;
    }

    @Override
    protected void populateCustomFields(ItemToEntityConfig config) {
        config.setResultLimit(parseInt(resultLimitInput.getValue(), 30));
        config.setEntityAge(parseInt(entityAgeInput.getValue(), 0));
    }

    @Override
    protected void clearCustomFields() {
        resultLimitInput.setValue("");
        entityAgeInput.setValue("");
    }

    @Override
    protected void refillCustomFields(ItemToEntityConfig config) {
        resultLimitInput.setValue(String.valueOf(config.getResultLimit()));
        entityAgeInput.setValue(String.valueOf(config.getEntityAge()));
    }

    @Override
    protected void addCustomSuggestion() {
        registerSuggestion(resultIdInput, BuiltInRegistries.ENTITY_TYPE);
    }

}
