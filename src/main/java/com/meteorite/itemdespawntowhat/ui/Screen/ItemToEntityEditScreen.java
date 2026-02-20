package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.ItemToEntityConfig;
import com.meteorite.itemdespawntowhat.ui.FormList;
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
    protected void addCustomEntries(FormList fromList) {
        resultLimitInput = numericBox();
        entityAgeInput = numericBox();

        fromList.add(Component.translatable(LABEL_PREFIX + "result_limit"), resultLimitInput);
        fromList.add(Component.translatable(LABEL_PREFIX + "result_age"), entityAgeInput);

        resultIdSuggestion = registerSuggestion(resultLimitInput, BuiltInRegistries.ENTITY_TYPE);
        addSuggestionListener(resultIdInput, resultIdSuggestion);
    }

    @Override
    protected ItemToEntityConfig createConfigFromFields() {
        ItemToEntityConfig config = new ItemToEntityConfig();
        populateCommonFields(config); // 填充通用字段
        config.setResultLimit(parseInt(resultLimitInput.getValue(), 30));
        config.setEntityAge(parseInt(entityAgeInput.getValue(), 0));

        return config;
    }

    @Override
    protected void clearCustomFields() {
        resultLimitInput.setValue("");
        entityAgeInput.setValue("");
    }

}
