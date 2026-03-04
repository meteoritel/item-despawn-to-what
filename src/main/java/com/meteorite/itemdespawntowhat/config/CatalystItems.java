package com.meteorite.itemdespawntowhat.config;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionSerializable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CatalystItems implements ConditionSerializable<CatalystItems> {
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogManager.getLogger();

    @SerializedName("catalyst_items")
    private List<CatalystEntry> catalystList;

    @SerializedName("consume_catalyst")
    private boolean catalystConsume;

    public CatalystItems() {
        this.catalystList = new ArrayList<>();
        this.catalystConsume = true;
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

    // =========== 催化剂消耗 ========== //
    // 在世界上消耗催化剂物品
    public void consumeFromLevel(ItemEntity triggerEntity, int startItemCount) {
        if (!catalystConsume || !hasAnyCatalyst()) {
            return;
        }

        // 查找范围：起始物品所在格子的 1×1×1 AABB
        BlockPos blockPos = triggerEntity.blockPosition();
        AABB searchBox = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(blockPos));

        for (CatalystEntry entry : getCatalystList()) {
            if (!entry.isValid()) continue;

            // 需要消耗的总数量 = 催化剂倍率 × 起始物品堆叠数
            Item targetItem = BuiltInRegistries.ITEM.get(entry.getItemId());
            int remaining = entry.getRequiredCount(startItemCount);

            List<ItemEntity> candidates = triggerEntity.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                    entity -> entity != triggerEntity
                            && entity.isAlive()
                            && entity.getItem().getItem() == targetItem
            );

            for (ItemEntity candidate : candidates) {
                if (remaining <= 0) {
                    break;
                }

                // 按顺序消耗周围的物品实体
                int available = candidate.getItem().getCount();
                if (available <= remaining) {
                    remaining -= available;
                    candidate.discard();
                    LOGGER.debug("Consumed entire catalyst entity: {} x{}", entry.getItemId(), available);
                } else {
                    candidate.getItem().shrink(remaining);
                    LOGGER.debug("Consumed partial catalyst: {} x{} (entity has {} remaining)",
                            entry.getItemId(), remaining, candidate.getItem().getCount());
                    remaining = 0;
                }
            }
            if (remaining > 0) {
                // 理论上不应发生，记录警告
                LOGGER.warn("Catalyst consumption incomplete for {}: still need {} more",
                        entry.getItemId(), remaining);
            }
        }
    }

    // 计算在消耗模式下，当前周围催化剂数量最多能支持转化多少个起始物品。
    public int getMaxConvertible(ItemEntity triggerEntity, int startCount) {
        if (!hasAnyCatalyst() || !catalystConsume) {
            // 无催化剂或不消耗模式：整堆都可转化
            return startCount;
        }
        Map<Item, Integer> nearbyCounts = collectNearbyItemCounts(triggerEntity);
        int max = startCount;
        for (CatalystEntry entry : catalystList) {
            Item requiredItem = BuiltInRegistries.ITEM.get(entry.getItemId());
            int available = nearbyCounts.getOrDefault(requiredItem, 0);
            int possible = available / entry.getCount(); // 整数除法，向下取整
            if (possible < max) {
                max = possible;
            }
        }
        return Math.max(0, max);
    }

    // 统计起始物品所在格子内（排除自己）各物品的总数量
    public static Map<Item, Integer> collectNearbyItemCounts(ItemEntity triggerEntity) {
        BlockPos pos = triggerEntity.blockPosition();
        AABB searchBox = new AABB(pos);

        Map<Item, Integer> counts = new HashMap<>();
        triggerEntity.level().getEntitiesOfClass(
                ItemEntity.class,
                searchBox,
                e -> e != triggerEntity && e.isAlive()
        ).forEach(e -> {
            ItemStack stack = e.getItem();
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        });
        return counts;
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

    public boolean isCatalystConsume() {
        return catalystConsume;
    }

    public void setCatalystConsume(boolean catalystConsume) {
        this.catalystConsume = catalystConsume;
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
