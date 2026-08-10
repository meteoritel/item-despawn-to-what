package com.meteorite.itemdespawntowhat.client.ui.form.section;

import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormContext;
import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormSection;
import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 管理物品转物品配置的专属表单字段。
 */
public final class ItemToItemFormSection implements ConfigFormSection<ItemToItemConfig> {
    private ConfigFormContext context;
    private EditBox resultLimitInput;

    @Override
    public void initialize(ConfigFormContext context) {
        this.context = context;
        resultLimitInput = context.positiveIntBox();
        context.add("result_limit", resultLimitInput);
    }

    @Override
    public void writeTo(ItemToItemConfig config) {
        config.setResultLimit(context.parseInt(resultLimitInput.getValue(), 30));
    }

    @Override
    public void readFrom(ItemToItemConfig config) {
        resultLimitInput.setValue(String.valueOf(config.getResultLimit()));
    }

    @Override
    public void clear() {
        resultLimitInput.setValue("");
    }

    @Override
    public void registerValidators(ConfigFormContext context) {
        context.registerValidator(context.resultIdInput(), IdValidator::isValidResultId);
    }

    @Override
    public void registerSuggestions(ConfigFormContext context) {
        context.registerSuggestion(context.resultIdInput(), SuggestionProvider.ofRegistry(BuiltInRegistries.ITEM));
    }
}
