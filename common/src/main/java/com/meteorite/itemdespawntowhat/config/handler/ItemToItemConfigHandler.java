package com.meteorite.itemdespawntowhat.config.handler;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ItemToItemConfigHandler extends BaseConfigHandler<ItemToItemConfig>{

    public ItemToItemConfigHandler(Path configDir) {
        super(ConfigType.ITEM_TO_ITEM, configDir);
    }

    @Override
    protected List<ItemToItemConfig> createDefaultEntries() {
        List<ItemToItemConfig> entries = new ArrayList<>();

        ItemToItemConfig entry = new ItemToItemConfig(
                "minecraft:chicken",
                "minecraft:rotten_flesh"
        );
        SurroundingBlocks sbs = new SurroundingBlocks();
        sbs.set(ConfigDirection.DOWN, "minecraft:magma_block");

        entry.setConversionTime(200);
        entries.add(entry);

        return entries;
    }

    @Override
    protected Type createListType() {
        return new TypeToken<List<ItemToItemConfig>>(){}.getType();
    }
}
