package com.meteorite.itemdespawntowhat.config.handler;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ItemToItemConfigHandler extends BaseConfigHandler<ItemToItemConfig>{

    public ItemToItemConfigHandler() {
        super(ConfigType.ITEM_TO_ITEM);
    }

    @Override
    protected List<ItemToItemConfig> createDefaultEntries() {
        List<ItemToItemConfig> entries = new ArrayList<>();

        ItemToItemConfig entry = new ItemToItemConfig(
                ResourceLocation.parse("minecraft:chicken"),
                ResourceLocation.parse("minecraft:rotten_flesh")
        );
        SurroundingBlocks sbs = new SurroundingBlocks();
        sbs.set(ConfigDirection.DOWN, "minecraft:magma_block");

        entry.setConversionTime(200);
        entries.add(entry);

        return entries;
    }

}
