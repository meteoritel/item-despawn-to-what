package com.meteorite.itemdespawntowhat.config;

import java.util.List;

public enum ConfigType {
    ITEM_TO_ITEM("item_to_item.json", ItemToItemConfig.class),
    ITEM_TO_ENTITY("item_to_entity.json", ItemToEntityConfig.class),
    ITEM_TO_BLOCK("item_to_block.json", ItemToBlockConfig.class);

    private final String fileName;
    private final Class<? extends BaseConversionConfig> configClass;

    ConfigType(String fileName, Class<? extends BaseConversionConfig> configClass) {
        this.fileName = fileName;
        this.configClass = configClass;
    }

    public String getFileName() {
        return fileName;
    }

    public Class<? extends BaseConversionConfig> getConfigClass() {
        return configClass;
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseConversionConfig> List<T> cast(List<? extends BaseConversionConfig> list) {
        return list.stream()
                .map(cfg -> (T) configClass.cast(cfg))
                .toList();
    }
}
