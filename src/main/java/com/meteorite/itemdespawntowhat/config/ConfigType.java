package com.meteorite.itemdespawntowhat.config;

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

    public static ConfigType fromString(String type) {
        for (ConfigType ct : values()) {
            if (ct.name().equalsIgnoreCase(type) || ct.fileName.equals(type)) {
                return ct;
            }
        }
        throw new IllegalArgumentException("Unknown config type: " + type);
    }
}
