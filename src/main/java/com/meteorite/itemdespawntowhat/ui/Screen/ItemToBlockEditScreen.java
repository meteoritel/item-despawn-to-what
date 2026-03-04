package com.meteorite.itemdespawntowhat.ui.Screen;

import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.ui.widget.FormListPanel;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class ItemToBlockEditScreen extends BaseConfigEditScreen<ItemToBlockConfig>{

    private EditBox radiusLimitInput;
    protected CycleButton<Boolean> enableItemBlockButton;

    public ItemToBlockEditScreen() {
        super(ConfigType.ITEM_TO_BLOCK);
    }

    @Override
    protected void addCustomEntries(FormListPanel fromList) {
        radiusLimitInput = numericBox();
        enableItemBlockButton = CycleButton.booleanBuilder(
                        Component.translatable(LABEL_PREFIX + "on"),
                        Component.translatable(LABEL_PREFIX + "off")
                ).withInitialValue(false)
                .create(0, 0, BOX_WIDTH, 18, Component.translatable(LABEL_PREFIX + "block_of_item"));
        fromList.add(Component.translatable(LABEL_PREFIX + "radius_limit"), radiusLimitInput);
        fromList.add(Component.translatable(LABEL_PREFIX + "block_of_item") ,enableItemBlockButton);
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
        config.setEnableItemBlock(enableItemBlockButton.getValue());
    }

    @Override
    protected void clearCustomFields() {
        radiusLimitInput.setValue("");
        enableItemBlockButton.setValue(false);
    }

    @Override
    protected void refillCustomFields(ItemToBlockConfig config) {
        radiusLimitInput.setValue(String.valueOf(config.getRadius()));
        enableItemBlockButton.setValue(config.isEnableItemBlock());
    }

    @Override
    protected void addCustomSuggestion() {
        registerSuggestion(resultIdInput, BuiltInRegistries.BLOCK);
    }
}
