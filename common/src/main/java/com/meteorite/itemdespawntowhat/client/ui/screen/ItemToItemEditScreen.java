package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.form.section.ItemToItemFormSection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;

/**
 * 物品转物品配置编辑入口。
 */
public final class ItemToItemEditScreen extends BaseConfigEditScreen<ItemToItemConfig> {
    public ItemToItemEditScreen() {
        super(ConfigType.ITEM_TO_ITEM, ItemToItemConfig::new, new ItemToItemFormSection());
    }
}
