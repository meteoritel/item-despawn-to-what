package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;

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

    public String getDown() {
        return down;
    }

    public void setDown(String down) {
        this.down = down;
    }

    public String getEast() {
        return east;
    }

    public void setEast(String east) {
        this.east = east;
    }

    public String getNorth() {
        return north;
    }

    public void setNorth(String north) {
        this.north = north;
    }

    public String getSouth() {
        return south;
    }

    public void setSouth(String south) {
        this.south = south;
    }

    public String getUp() {
        return up;
    }

    public void setUp(String up) {
        this.up = up;
    }

    public String getWest() {
        return west;
    }

    public void setWest(String west) {
        this.west = west;
    }

    // 检查是否存在周围方块设置的需求
    public boolean hasAnySurroundBlock() {
        return !(north.isEmpty() && south.isEmpty() && east.isEmpty() &&
                west.isEmpty() && up.isEmpty() && down.isEmpty());
    }

}
