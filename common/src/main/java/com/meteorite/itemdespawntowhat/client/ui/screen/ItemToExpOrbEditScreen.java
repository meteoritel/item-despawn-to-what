package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.form.section.ItemToExperienceFormSection;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;

/**
 * 物品转经验配置编辑入口。
 */
public final class ItemToExpOrbEditScreen extends BaseConfigEditScreen<ItemToExpOrbConfig> {
    public ItemToExpOrbEditScreen() {
        super(BuiltinConversionTypes.ITEM_TO_XP_ORB, ItemToExpOrbConfig::new, new ItemToExperienceFormSection());
    }
}
