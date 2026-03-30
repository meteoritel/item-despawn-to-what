package com.meteorite.itemdespawntowhat.config.handler;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ItemToBlockConfigHandler extends BaseConfigHandler<ItemToBlockConfig>{

    public ItemToBlockConfigHandler() {
        super(ConfigType.ITEM_TO_BLOCK);
    }

    @Override
    protected List<ItemToBlockConfig> createDefaultEntries() {
        List<ItemToBlockConfig> entries = new ArrayList<>();

        ItemToBlockConfig saplingEntry = new ItemToBlockConfig(
                "minecraft:oak_sapling",
                "minecraft:oak_sapling"
        );
        SurroundingBlocks blocks = new SurroundingBlocks();
        blocks.set(ConfigDirection.DOWN, "#minecraft:dirt");
        saplingEntry.setSurroundingBlocks(blocks);

        entries.add(saplingEntry);

        return entries;
    }
    @Override
    protected Type createListType() {
        return new TypeToken<List<ItemToBlockConfig>>(){}.getType();
    }
}
