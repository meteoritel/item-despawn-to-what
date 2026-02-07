package com.meteorite.expiringitemlib.util;

import net.minecraft.core.Direction;

public enum ConfigDirection {
    NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH),
    EAST("east", Direction.EAST),
    WEST("west", Direction.WEST),
    UP("up", Direction.UP),
    DOWN("down", Direction.DOWN);

    private final String key;
    private final Direction direction;

    ConfigDirection(String key, Direction direction) {
        this.key = key;
        this.direction = direction;
    }

    public Direction getDirection() {
        return direction;
    }

    public static ConfigDirection fromString (String key) {
        for (ConfigDirection dir : values()) {
            if (dir.key.equals(key.toLowerCase())) {
                return dir;
            }
        }
        return null;
    }

}
