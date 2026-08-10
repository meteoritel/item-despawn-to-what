package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.form.section.ItemToWorldEffectFormSection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;

/**
 * 物品转世界效果配置编辑入口。
 */
public final class ItemToWorldEffectEditScreen extends BaseConfigEditScreen<ItemToWorldEffectConfig> {
    public ItemToWorldEffectEditScreen() {
        super(ConfigType.ITEM_TO_WORLD_EFFECT,
                ItemToWorldEffectConfig::new,
                new ItemToWorldEffectFormSection());
    }
}
