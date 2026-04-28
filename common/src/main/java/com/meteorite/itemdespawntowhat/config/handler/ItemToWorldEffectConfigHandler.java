package com.meteorite.itemdespawntowhat.config.handler;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;

public class ItemToWorldEffectConfigHandler extends BaseConfigHandler<ItemToWorldEffectConfig>{
    public ItemToWorldEffectConfigHandler(Path configDir) {
        super(ConfigType.ITEM_TO_WORLD_EFFECT, configDir);
    }

    @Override
    protected Type createListType() {
        return new TypeToken<List<ItemToWorldEffectConfig>>(){}.getType();
    }
}
