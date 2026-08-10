package com.meteorite.itemdespawntowhat.config.catalogue;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.util.IdValidator;

import java.util.stream.Stream;

/**
 * 六个方向的相邻方块条件数据。
 */
public class SurroundingBlocks {
    @SerializedName("north")
    private String north;
    @SerializedName("south")
    private String south;
    @SerializedName("east")
    private String east;
    @SerializedName("west")
    private String west;
    @SerializedName("up")
    private String up;
    @SerializedName("down")
    private String down;

    public SurroundingBlocks() {
    }

    // 检查是否存在周围方块设置的需求
    public boolean hasAnySurroundBlock() {
        return Stream.of(north, south, east, west, down, up)
                .anyMatch(s ->s != null && !s.isBlank());
    }

    public boolean isValid() {
        for (ConfigDirection dir : ConfigDirection.values()) {
            String value = get(dir);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!IdValidator.isValidBlockId(value)) {
                return false;
            }
        }
        return true;
    }

    public String get(ConfigDirection dir) {
        return switch(dir) {
            case NORTH -> north;
            case SOUTH -> south;
            case EAST -> east;
            case WEST -> west;
            case UP -> up;
            case DOWN -> down;
        };
    }

    public void set(ConfigDirection dir, String value) {
        switch (dir) {
            case NORTH -> north = value;
            case SOUTH -> south = value;
            case EAST -> east = value;
            case WEST -> west = value;
            case UP -> up = value;
            case DOWN -> down = value;
        }
    }
}
