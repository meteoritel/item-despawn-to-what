package com.meteorite.itemdespawntowhat.handler;

import com.google.gson.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.ItemToBlockConfig;
import net.minecraft.resources.ResourceLocation;

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
                ResourceLocation.parse("minecraft:oak_sapling"),
                ResourceLocation.parse("minecraft:oak_sapling")
        );

        entries.add(saplingEntry);

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
