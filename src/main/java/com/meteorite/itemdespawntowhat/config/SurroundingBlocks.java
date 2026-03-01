package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;

// 六个面挨个写太麻烦，优化中
public class SurroundingBlocks {
    @SerializedName("north")
    private String north = "";

    @SerializedName("south")
    private String south = "";

    @SerializedName("east")
    private String east = "";

    @SerializedName("west")
    private String west = "";

    @SerializedName("up")
    private String up = "";

    @SerializedName("down")
    private String down = "";

//    public void setUp(String up) {
//        this.up = up;
//    }

    // 检查是否存在周围方块设置的需求
    public boolean hasAnySurroundBlock() {
        return !(north.isEmpty() && south.isEmpty() && east.isEmpty() &&
                west.isEmpty() && up.isEmpty() && down.isEmpty());
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

    public void setAll(String value) {
        north = value;
        south = value;
        east = value;
        west = value;
        up = value;
        down = value;
    }
}
