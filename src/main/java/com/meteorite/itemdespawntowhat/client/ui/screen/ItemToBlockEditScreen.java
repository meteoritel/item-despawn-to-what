package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.SuggestionProvider;
import com.meteorite.itemdespawntowhat.client.ui.panel.FormListPanel;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.server.task.PlaceBlockTask.BlockPlaceShape;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public class ItemToBlockEditScreen extends BaseConfigEditScreen<ItemToBlockConfig>{

    private EditBox radiusLimitInput;
    private CycleButton<BlockPlaceShape> blockPlaceShapeButton;
    protected CycleButton<Boolean> enableItemBlockButton;

    public ItemToBlockEditScreen() {
        super(ConfigType.ITEM_TO_BLOCK);
    }

    @Override
    protected boolean shouldShowResultId() {
        return false;
    }

    @Override
    protected void addCustomEntries(FormListPanel fromList) {
        radiusLimitInput = numericBox();
        blockPlaceShapeButton = CycleButton.<BlockPlaceShape>builder(
                        shape -> Component.translatable(shape.getDescriptionId()))
                .withValues(BlockPlaceShape.values())
                .withInitialValue(BlockPlaceShape.SQUARE)
                .create(0, 0, BOX_WIDTH, 18, Component.translatable(LABEL_PREFIX + "block_place_shape"),
                        (button, value) -> {});
        enableItemBlockButton = CycleButton.booleanBuilder(
                        Component.translatable(LABEL_PREFIX + "on"),
                        Component.translatable(LABEL_PREFIX + "off")
                ).withInitialValue(false)
                .create(0, 0, BOX_WIDTH, 18, Component.translatable(LABEL_PREFIX + "block_of_item"),
                        (button, value) -> rebuildConditionalEntries());
        fromList.add(Component.translatable(LABEL_PREFIX + "radius_limit"), radiusLimitInput);
        fromList.add(Component.translatable(LABEL_PREFIX + "block_place_shape"), blockPlaceShapeButton);
        fromList.add(Component.translatable(LABEL_PREFIX + "block_of_item"), enableItemBlockButton);
        rebuildConditionalEntries();
    }

    private void rebuildConditionalEntries() {
        if (formList == null || enableItemBlockButton == null) {
            return;
        }

        formList.removeConditionalEntries();
        if (!enableItemBlockButton.getValue()) {
            formList.addConditional(Component.translatable(LABEL_PREFIX + "result_id"), resultIdInput);
        } else {
            resultIdInput.setValue("");
        }
        clearAllSuggestions();
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
        config.setBlockPlaceShape(blockPlaceShapeButton.getValue());
        config.setEnableItemBlock(enableItemBlockButton.getValue());
        if (enableItemBlockButton.getValue()) {
            config.setResultId(null);
        }
    }

    @Override
    protected void clearCustomFields() {
        radiusLimitInput.setValue("");
        blockPlaceShapeButton.setValue(BlockPlaceShape.SQUARE);
        enableItemBlockButton.setValue(false);
        resultIdInput.setValue("");
        rebuildConditionalEntries();
    }

    @Override
    protected void refillCustomFields(ItemToBlockConfig config) {
        radiusLimitInput.setValue(String.valueOf(config.getRadius()));
        blockPlaceShapeButton.setValue(config.getBlockPlaceShape() != null ? config.getBlockPlaceShape() : BlockPlaceShape.SQUARE);
        enableItemBlockButton.setValue(config.isEnableItemBlock());
        resultIdInput.setValue(config.getResultId() != null ? config.getResultId() : "");
        rebuildConditionalEntries();
    }

    @Override
    protected void initValidators() {
        registerValidator(resultIdInput, () -> !enableItemBlockButton.getValue(), IdValidator::isValidBlockId);
    }

    @Override
    protected void addCustomSuggestion() {
        registerSuggestion(resultIdInput,
                SuggestionProvider.ofRegistry(BuiltInRegistries.BLOCK));
    }
}
