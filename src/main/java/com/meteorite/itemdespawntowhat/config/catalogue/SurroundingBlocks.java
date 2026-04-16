package com.meteorite.itemdespawntowhat.config.catalogue;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionSerializable;
import com.meteorite.itemdespawntowhat.config.ConfigDirection;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class SurroundingBlocks implements ConditionSerializable<SurroundingBlocks> {
    private static final Gson GSON = new Gson();
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
        setAll("");
    }

    // ========== 接口实现 ========== //
    // 将非空的六面配置以 conditionKey.<direction> 为键写入 。
    @Override
    public void toConditionMap(Map<String, String> out, String conditionKey) {
        if (hasAnySurroundBlock()) {
            out.put(conditionKey, GSON.toJson(this));
        }
    }

    @Override
    public SurroundingBlocks fromConditionMap(Map<String, String> conditions, String conditionKey) {
        String json = conditions.get(conditionKey);
        if (json == null || json.isEmpty()) {
            return null;
        }

        SurroundingBlocks parsed = GSON.fromJson(json, SurroundingBlocks.class);
        return (parsed != null && parsed.hasAnySurroundBlock()) ? parsed : null;
    }

    // 检查是否存在周围方块设置的需求
    public boolean hasAnySurroundBlock() {
        return !(north.isEmpty() && south.isEmpty() && east.isEmpty() &&
                west.isEmpty() && up.isEmpty() && down.isEmpty());
    }

    public boolean isValid() {
        for (ConfigDirection dir : ConfigDirection.values()) {
            String value = get(dir);
            if (value == null || value.isEmpty()) {
                continue; // 空值表示该方向无要求，合法
            }
            if (TagResolver.isTagId(value)) {
                // 标签：校验格式
                if (!IdValidator.isValidTagId(value)) {
                    return false;
                }
            } else {
                // 普通方块：校验格式
                if (!IdValidator.isValidString(value) || SafeParseUtil.parseResourceLocation(value) == null) {
                    return false;
                }
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

    public void setAll(String value) {
        north = south = east = west = up = down = value;
    }
}
