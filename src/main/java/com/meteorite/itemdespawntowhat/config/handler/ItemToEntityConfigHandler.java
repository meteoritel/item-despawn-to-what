package com.meteorite.itemdespawntowhat.config.handler;

import com.google.gson.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.ItemToEntityConfig;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ItemToEntityConfigHandler extends BaseConfigHandler<ItemToEntityConfig> {


    public ItemToEntityConfigHandler() {
        super(ConfigType.ITEM_TO_ENTITY);
    }

    // 默认有一个鸡蛋变成小鸡的示例
    @Override
    protected List<ItemToEntityConfig> createDefaultEntries() {
        List<ItemToEntityConfig> entries = new ArrayList<>();

        ItemToEntityConfig eggEntry = new ItemToEntityConfig(
                ResourceLocation.parse("minecraft:egg"),
                ResourceLocation.parse("minecraft:chicken")
        );

        SurroundingBlocks blocks = new SurroundingBlocks();
        blocks.setDown("minecraft:hay_block");
        eggEntry.setSurroundingBlocks(blocks);

        entries.add(eggEntry);

        return entries;
    }

    @Override
    protected Type createListType() {
        // 明确指定具体类型，避免泛型擦除问题
        return new TypeToken<List<ItemToEntityConfig>>(){}.getType();
    }

    @Override
    public List<ItemToEntityConfig> loadConfig() {
        return super.loadConfig();
    }
}
