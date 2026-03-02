package com.meteorite.itemdespawntowhat.config;

import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionSerializable;

import java.util.Map;

public class SurroundingBlocks implements ConditionSerializable<SurroundingBlocks> {
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

    // ========== 接口实现 ========== //

    // 将非空的六面配置以 conditionKey.<direction> 为键写入 。
    @Override
    public void toConditionMap(Map<String, String> out, String conditionKey) {
        for (ConfigDirection dir : ConfigDirection.values()) {
            String value = get(dir);
            if (value != null && !value.isEmpty()) {
                out.put(conditionKey + "." + dir.name().toLowerCase(), value);
            }
        }
    }

    @Override
    public SurroundingBlocks fromConditionMap(Map<String, String> conditions, String conditionKey) {
        String prefix = conditionKey + ".";
        boolean anySet = false;

        for (Map.Entry<String, String> entry : conditions.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String dirStr = entry.getKey().substring(prefix.length());
                ConfigDirection dir = ConfigDirection.fromString(dirStr);
                if (dir != null && !entry.getValue().isEmpty()) {
                    set(dir, entry.getValue());
                    anySet = true;
                }
            }
        }

        return anySet ? this : null;
    }

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
        north = south = east = west = up = down = value;
    }
}
