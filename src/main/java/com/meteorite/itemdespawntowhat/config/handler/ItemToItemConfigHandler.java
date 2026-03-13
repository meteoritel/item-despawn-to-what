package com.meteorite.itemdespawntowhat.config.handler;

import com.google.gson.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
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
    @Override
    protected Type createListType() {
        // 明确指定具体类型，避免泛型擦除问题
        return new TypeToken<List<ItemToItemConfig>>(){}.getType();
    }
}
