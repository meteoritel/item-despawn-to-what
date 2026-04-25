package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class ItemToMobEditScreen extends BaseConfigEditScreen<ItemToMobConfig> {
    private EditBox resultLimitInput;
    private EditBox entityAgeInput;

    public ItemToMobEditScreen() {
        super(ConfigType.ITEM_TO_MOB);
    }

    @Override
    protected void addCustomEntries(FormListPanel fromList) {
        resultLimitInput = numericBox();
        entityAgeInput = numericBox();

        fromList.add(Component.translatable(LABEL_PREFIX + "result_limit"), resultLimitInput);
        fromList.add(Component.translatable(LABEL_PREFIX + "result_age"), entityAgeInput);
    }

    @Override
    protected ItemToMobConfig createConfigFromFields() {
        ItemToMobConfig config = new ItemToMobConfig();
        populateCommonFields(config);
        populateCustomFields(config);
        return config;
    }

    @Override
    protected void populateCustomFields(ItemToMobConfig config) {
        config.setResultLimit(parseInt(resultLimitInput.getValue(), 30));
        config.setEntityAge(parseInt(entityAgeInput.getValue(), 0));
    }

    @Override
    protected void clearCustomFields() {
        resultLimitInput.setValue("");
        entityAgeInput.setValue("");
    }

    @Override
    protected void refillCustomFields(ItemToMobConfig config) {
        resultLimitInput.setValue(String.valueOf(config.getResultLimit()));
        entityAgeInput.setValue(String.valueOf(config.getEntityAge()));
    }

    @Override
    protected void initCustomValidators() {
        registerValidator(resultIdInput, IdValidator::isValidEntityId);
    }

    @Override
    protected void addCustomSuggestion() {
        registerSuggestion(resultIdInput,
                SuggestionProvider.ofMobEntityTypes());
    }
}
