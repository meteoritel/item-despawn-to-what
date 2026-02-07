package com.meteorite.expiringitemlib.handler;

import com.google.gson.reflect.TypeToken;
import com.meteorite.expiringitemlib.config.ItemToBlockConfig;
import com.meteorite.expiringitemlib.config.SurroundingBlocks;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ItemToBlockConfigHandler extends BaseConfigHandler<ItemToBlockConfig>{

    public ItemToBlockConfigHandler() {
        super("item_to_block.json");
    }

    @Override
    protected List<ItemToBlockConfig> createDefaultEntries() {
        List<ItemToBlockConfig> entries = new ArrayList<>();

        ItemToBlockConfig leatherEntry = new ItemToBlockConfig(
                ResourceLocation.parse("minecraft:oak_sapling"),
                ResourceLocation.parse("minecraft:oak_sapling")
        );

        SurroundingBlocks blocks = new SurroundingBlocks();
        blocks.setDown("minecraft:dirt");
        leatherEntry.setSurroundingBlocks(blocks);

        entries.add(leatherEntry);

        return entries;
    }

    @Override
    protected Type createListType() {

        return new TypeToken<List<ItemToBlockConfig>>(){}.getType();
    }

    @Override
    public List<ItemToBlockConfig> loadConfig() {
        return super.loadConfig();
    }
}
