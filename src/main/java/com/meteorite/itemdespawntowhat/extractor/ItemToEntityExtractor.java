package com.meteorite.itemdespawntowhat.extractor;

import com.meteorite.itemdespawntowhat.config.ItemToEntityConfig;

// 用于item -> entity 的配置提取器
@Deprecated
public class ItemToEntityExtractor extends BaseConfigExtractor<ItemToEntityConfig> {
    private static final ItemToEntityExtractor INSTANCE = new ItemToEntityExtractor();

    private ItemToEntityExtractor() {
        super("item_to_entity");
    }

    public static ItemToEntityExtractor getInstance() {
        return INSTANCE;
    }


}
