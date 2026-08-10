package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.form.section.ItemToBlockFormSection;
import com.meteorite.itemdespawntowhat.config.type.BuiltinConversionTypes;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;

/**
 * 物品转方块配置编辑入口。
 */
public final class ItemToBlockEditScreen extends BaseConfigEditScreen<ItemToBlockConfig> {
    public ItemToBlockEditScreen() {
        super(BuiltinConversionTypes.ITEM_TO_BLOCK, ItemToBlockConfig::new, new ItemToBlockFormSection());
    }
}
