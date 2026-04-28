package com.meteorite.itemdespawntowhat.config.handler;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToBlockConfig;
import com.meteorite.itemdespawntowhat.config.catalogue.SurroundingBlocks;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ItemToBlockConfigHandler extends BaseConfigHandler<ItemToBlockConfig>{

    public ItemToBlockConfigHandler(Path configDir) {
        super(ConfigType.ITEM_TO_BLOCK, configDir);
    }

    @Override
    protected List<ItemToBlockConfig> createDefaultEntries() {
        List<ItemToBlockConfig> entries = new ArrayList<>();

        ItemToBlockConfig saplingEntry = new ItemToBlockConfig();
        saplingEntry.setItemId("#minecraft:saplings");
        saplingEntry.setEnableItemBlock(true);

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
