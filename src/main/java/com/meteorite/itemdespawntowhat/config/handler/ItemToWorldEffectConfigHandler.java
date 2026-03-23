package com.meteorite.itemdespawntowhat.config.handler;

import com.google.common.reflect.TypeToken;
import com.meteorite.itemdespawntowhat.config.ConfigType;
import com.meteorite.itemdespawntowhat.config.conversion.ItemToWorldEffectConfig;

import java.lang.reflect.Type;
import java.util.List;

public class ItemToWorldEffectConfigHandler extends BaseConfigHandler<ItemToWorldEffectConfig>{
    public ItemToWorldEffectConfigHandler() {
        super(ConfigType.ITEM_TO_WORLD_EFFECT);
    }

    @Override
    protected Type createListType() {
        return new TypeToken<List<ItemToWorldEffectConfig>>(){}.getType();
    }
}
