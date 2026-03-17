package com.meteorite.itemdespawntowhat.config.handler;

import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToMobConfig;
import com.meteorite.itemdespawntowhat.config.SurroundingBlocks;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ItemToMobConfigHandler extends BaseConfigHandler<ItemToMobConfig> {

    public ItemToMobConfigHandler() {
        super(ConfigType.ITEM_TO_MOB);
    }

    // 默认有一个鸡蛋变成小鸡的示例
    @Override
    protected List<ItemToMobConfig> createDefaultEntries() {
        List<ItemToMobConfig> entries = new ArrayList<>();

        ItemToMobConfig eggEntry = new ItemToMobConfig(
                ResourceLocation.parse("minecraft:egg"),
                ResourceLocation.parse("minecraft:chicken")
        );

        SurroundingBlocks blocks = new SurroundingBlocks();
        blocks.set(ConfigDirection.DOWN,"minecraft:hay_block");
        eggEntry.setEntityAge(-24000);
        eggEntry.setSurroundingBlocks(blocks);

        entries.add(eggEntry);

        return entries;
    }

}
