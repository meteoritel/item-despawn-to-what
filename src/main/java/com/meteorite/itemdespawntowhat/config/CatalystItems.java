package com.meteorite.itemdespawntowhat.config;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionSerializable;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CatalystItems implements ConditionSerializable<CatalystItems> {
    private static final Gson GSON = new Gson();

    @SerializedName("catalyst_items")
    private List<CatalystEntry> catalystList;

    public CatalystItems() {
        this.catalystList = new ArrayList<>();
    }

    // ========== 接口实现 ========== //
    @Override
    public void toConditionMap(Map<String, String> out, String conditionKey) {
        if (hasAnyCatalyst()) {
            out.put(conditionKey, GSON.toJson(this));
        }
    }

    @Override
    public CatalystItems fromConditionMap(Map<String, String> conditions, String conditionKey) {
        String json = conditions.get(conditionKey);
        if (json == null || json.isEmpty()) {
            return null;
        }

        CatalystItems parsed = GSON.fromJson(json, CatalystItems.class);
        if (parsed == null || !parsed.hasAnyCatalyst()) {
            return null;
        }

        parsed.getCatalystList().removeIf(entry -> !entry.isValid());
        return parsed.hasAnyCatalyst() ? parsed : null;
    }

    public boolean hasAnyCatalyst() {
        return catalystList != null && !catalystList.isEmpty();
    }

    public List<CatalystEntry> getCatalystList() {
        return catalystList == null ? new ArrayList<>() : catalystList;
    }

    public void setCatalystList(List<CatalystEntry> catalystList) {
        this.catalystList = catalystList;
    }

    // ========== 内部条目类 ========== //
    public static class CatalystEntry {
        // 当起始物品为1时需要的催化剂物品的种类和数量
        @SerializedName("item")
        private ResourceLocation itemId;
        @SerializedName("count")
        private int count;

        public CatalystEntry() {
            this.count = 1;
        }

        public CatalystEntry(ResourceLocation itemId, int count) {
            this.itemId = itemId;
            this.count = Math.max(1, count);
        }

        public ResourceLocation getItemId() {
            return itemId;
        }

        public void setItemId(ResourceLocation itemId) {
            this.itemId = itemId;
        }

        public int getCount() {
            return Math.max(1, count);
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getRequiredCount(int startItemCount) {
            return getCount() * Math.max(1, startItemCount);
        }

        public boolean isValid() {
            return itemId != null && !itemId.getPath().isEmpty() && count >= 1;
        }
    }
}
