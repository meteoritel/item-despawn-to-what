package com.meteorite.itemdespawntowhat.config;

public enum ConfigType {
    ITEM_TO_ITEM("item_to_item.json"),
    ITEM_TO_ENTITY("item_to_entity.json"),
    ITEM_TO_BLOCK("item_to_block.json");

    private final String fileName;

    ConfigType(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
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
