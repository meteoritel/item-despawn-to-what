package com.meteorite.itemdespawntowhat.config.handler;

import com.google.gson.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.ItemToItemConfig;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ItemToItemConfigHandler extends BaseConfigHandler<ItemToItemConfig>{

    public ItemToItemConfigHandler() {
        super(ConfigType.ITEM_TO_ITEM);
    }

    // 默认有一个腐肉变皮革的实例
    @Override
    protected List<ItemToItemConfig> createDefaultEntries() {
        List<ItemToItemConfig> entries = new ArrayList<>();

        ItemToItemConfig leatherEntry = new ItemToItemConfig(
                ResourceLocation.parse("minecraft:rotten_flesh"),
                ResourceLocation.parse("minecraft:leather")
        );

        SurroundingBlocks blocks = new SurroundingBlocks();
        blocks.setDown("minecraft:magma_block");
        leatherEntry.setSurroundingBlocks(blocks);

        entries.add(leatherEntry);

        return entries;
    }
    @Override
    protected Type createListType() {
        // 明确指定具体类型，避免泛型擦除问题
        return new TypeToken<List<ItemToItemConfig>>(){}.getType();
    }

    @Override
    public List<ItemToItemConfig> loadConfig() {
        return super.loadConfig();
    }
}
