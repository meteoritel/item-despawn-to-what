package com.meteorite.itemdespawntowhat.config.handler;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToExpOrbConfig;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;

public class ItemToXpConfigHandler extends BaseConfigHandler<ItemToExpOrbConfig>{
    public ItemToXpConfigHandler(Path configDir) {
        super(ConfigType.ITEM_TO_XP_ORB, configDir);
    }

    @Override
    protected Type createListType() {
        return new TypeToken<List<ItemToExpOrbConfig>>(){}.getType();
    }
}
