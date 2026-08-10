package com.meteorite.itemdespawntowhat.client.ui.form.section;

import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormContext;
import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormSection;
import com.meteorite.itemdespawntowhat.client.ui.support.SuggestionProvider;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.client.gui.components.EditBox;

/**
 * 管理物品转生物配置的专属表单字段。
 */
public final class ItemToMobFormSection implements ConfigFormSection<ItemToMobConfig> {
    private ConfigFormContext context;
    private EditBox resultLimitInput;
    private EditBox entityAgeInput;

    @Override
    public void initialize(ConfigFormContext context) {
        this.context = context;
        resultLimitInput = context.positiveIntBox();
        entityAgeInput = context.numericBox();
        context.add("result_limit", resultLimitInput);
        context.add("result_age", entityAgeInput);
    }

    @Override
    public void writeTo(ItemToMobConfig config) {
        config.setResultLimit(context.parseInt(resultLimitInput.getValue(), 30));
        config.setEntityAge(context.parseInt(entityAgeInput.getValue(), 0));
    }

    @Override
    public void readFrom(ItemToMobConfig config) {
        resultLimitInput.setValue(String.valueOf(config.getResultLimit()));
        entityAgeInput.setValue(String.valueOf(config.getEntityAge()));
    }

    @Override
    public void clear() {
        resultLimitInput.setValue("");
        entityAgeInput.setValue("");
    }

    @Override
    public void registerValidators(ConfigFormContext context) {
        context.registerValidator(context.resultIdInput(), IdValidator::isValidEntityId);
    }

    @Override
    public void registerSuggestions(ConfigFormContext context) {
        context.registerSuggestion(context.resultIdInput(), SuggestionProvider.ofMobEntityTypes());
    }

}
