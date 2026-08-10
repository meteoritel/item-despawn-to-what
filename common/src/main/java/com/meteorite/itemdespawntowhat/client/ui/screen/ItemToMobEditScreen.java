package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.form.section.ItemToMobFormSection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;

/**
 * 物品转生物配置编辑入口。
 */
public final class ItemToMobEditScreen extends BaseConfigEditScreen<ItemToMobConfig> {
    public ItemToMobEditScreen() {
        super(ConfigType.ITEM_TO_MOB, ItemToMobConfig::new, new ItemToMobFormSection());
    }
}
