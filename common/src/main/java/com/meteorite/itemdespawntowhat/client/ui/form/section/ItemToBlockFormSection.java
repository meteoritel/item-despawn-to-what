package com.meteorite.itemdespawntowhat.client.ui.form.section;

import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormContext;
import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormSection;
import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.server.task.PlaceBlockTask.BlockPlaceShape;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/**
 * 管理物品转方块配置的专属表单与条件行。
 */
public final class ItemToBlockFormSection implements ConfigFormSection<ItemToBlockConfig> {
    private ConfigFormContext context;
    private EditBox radiusLimitInput;
    private CycleButton<BlockPlaceShape> blockPlaceShapeButton;
    private CycleButton<Boolean> enableItemBlockButton;

    @Override
    public void initialize(ConfigFormContext context) {
        this.context = context;
        radiusLimitInput = context.positiveIntBox();
        blockPlaceShapeButton = CycleButton.<BlockPlaceShape>builder(
                        shape -> Component.translatable(shape.getDescriptionId()))
                .withValues(BlockPlaceShape.values())
                .withInitialValue(BlockPlaceShape.SQUARE)
                .create(0, 0, context.boxWidth(), context.buttonHeight(), context.label("block_place_shape"));
        enableItemBlockButton = CycleButton.booleanBuilder(context.label("on"), context.label("off"))
                .withInitialValue(false)
                .create(0, 0, context.boxWidth(), context.buttonHeight(), context.label("block_of_item"),
                        (button, value) -> rebuildConditionalEntries());
        context.add("radius_limit", radiusLimitInput);
        context.add("block_place_shape", blockPlaceShapeButton);
        context.add("block_of_item", enableItemBlockButton);
        rebuildConditionalEntries();
    }

    @Override
    public void writeTo(ItemToBlockConfig config) {
        config.setRadius(context.parseInt(radiusLimitInput.getValue(), 6));
        config.setBlockPlaceShape(blockPlaceShapeButton.getValue());
        config.setEnableItemBlock(enableItemBlockButton.getValue());
        if (enableItemBlockButton.getValue()) {
            config.setResultId(null);
        }
    }

    @Override
    public void readFrom(ItemToBlockConfig config) {
        radiusLimitInput.setValue(String.valueOf(config.getRadius()));
        blockPlaceShapeButton.setValue(config.getBlockPlaceShape());
        enableItemBlockButton.setValue(config.isEnableItemBlock());
        context.resultIdInput().setValue(config.getResultId() == null ? "" : config.getResultId());
        rebuildConditionalEntries();
    }

    @Override
    public void clear() {
        radiusLimitInput.setValue("");
        blockPlaceShapeButton.setValue(BlockPlaceShape.SQUARE);
        enableItemBlockButton.setValue(false);
        context.resultIdInput().setValue("");
        rebuildConditionalEntries();
    }

    @Override
    public boolean showsResultId() {
        return false;
    }

    @Override
    public void registerValidators(ConfigFormContext context) {
        context.registerValidator(context.resultIdInput(),
                () -> !enableItemBlockButton.getValue(), IdValidator::isValidBlockId);
    }

    @Override
    public void registerSuggestions(ConfigFormContext context) {
        context.registerSuggestion(context.resultIdInput(), SuggestionProvider.ofRegistry(BuiltInRegistries.BLOCK));
    }

    private void rebuildConditionalEntries() {
        context.rebuildConditional(() -> {
            if (!enableItemBlockButton.getValue()) {
                context.addConditional("result_id", context.resultIdInput());
            } else {
                context.resultIdInput().setValue("");
            }
        });
    }
}
