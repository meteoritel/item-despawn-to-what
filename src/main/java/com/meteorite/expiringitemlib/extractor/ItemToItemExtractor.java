package com.meteorite.expiringitemlib.extractor;

import com.meteorite.expiringitemlib.config.ItemToItemConfig;

@Deprecated
public class ItemToItemExtractor extends BaseConfigExtractor<ItemToItemConfig>{
    private static final ItemToItemExtractor INSTANCE = new ItemToItemExtractor();

    private ItemToItemExtractor(){
        super("item_to_item");
    }

    public static ItemToItemExtractor getInstance() {
        return INSTANCE;
    }
}
