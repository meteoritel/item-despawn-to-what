package com.meteorite.itemdespawntowhat.config;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public enum ConfigDirection {
    NORTH(Direction.NORTH, "gui.itemdespawntowhat.direction.north"),
    SOUTH(Direction.SOUTH, "gui.itemdespawntowhat.direction.south"),
    EAST(Direction.EAST, "gui.itemdespawntowhat.direction.east"),
    WEST(Direction.WEST, "gui.itemdespawntowhat.direction.west"),
    UP(Direction.UP, "gui.itemdespawntowhat.direction.up"),
    DOWN(Direction.DOWN, "gui.itemdespawntowhat.direction.down");

    private final Direction direction;
    private final String translationKey;

    ConfigDirection(Direction direction, String translationKey) {
        this.direction = direction;
        this.translationKey = translationKey;
    }

    public Direction getDirection() {
        return direction;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }
}
