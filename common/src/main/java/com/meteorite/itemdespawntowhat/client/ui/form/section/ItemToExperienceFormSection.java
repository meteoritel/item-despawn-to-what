package com.meteorite.itemdespawntowhat.client.ui.form.section;

import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormContext;
import com.meteorite.itemdespawntowhat.client.ui.form.ConfigFormSection;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;
import net.minecraft.client.gui.components.EditBox;

/**
 * 管理物品转经验配置的专属表单字段。
 */
public final class ItemToExperienceFormSection implements ConfigFormSection<ItemToExpOrbConfig> {
    private ConfigFormContext context;
    private EditBox xpPerItemInput;

    @Override
    public void initialize(ConfigFormContext context) {
        this.context = context;
        xpPerItemInput = context.positiveIntBox();
        context.add("xp_pre_item", xpPerItemInput);
    }

    @Override
    public void writeTo(ItemToExpOrbConfig config) {
        config.setXpPerItem(context.parseInt(xpPerItemInput.getValue(), 1));
    }

    @Override
    public void readFrom(ItemToExpOrbConfig config) {
        xpPerItemInput.setValue(String.valueOf(config.getXpPerItem()));
    }

    @Override
    public void clear() {
        xpPerItemInput.setValue("");
    }

    @Override
    public boolean showsResultId() {
        return false;
    }
}
