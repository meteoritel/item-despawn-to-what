package com.meteorite.itemdespawntowhat.config.catalogue;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionSerializable;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class CatalystItems implements ConditionSerializable<CatalystItems> {
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogManager.getLogger();

    @SerializedName("catalyst_items")
    private List<CatalystEntry> catalystList;

    @SerializedName("consume_catalyst")
    private boolean catalystConsume;

    public CatalystItems() {
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

    // ========== 条件检测方法 ========== //
    // 检查是否存在完整的一套催化剂。
    public boolean checkCondition(Map<Item, Integer> snapshot) {
        if (!hasAnyCatalyst()) {
            return true;
        }
        for (CatalystEntry entry : catalystList) {
            int available;
            if (entry.isTagEntry()) {
                available = entry.getTagItems().stream()
                        .mapToInt(item -> snapshot.getOrDefault(item, 0))
                        .sum();
            } else {
                available = snapshot.getOrDefault(entry.getItem(), 0);
            }
            if (available < entry.count()) {
                return false;
            }
        }
        return true;
    }

    // 统计起始物品所在格子内（排除自己）各物品的总数量
    public static Map<Item, Integer> collectNearbyItemCounts(ItemEntity triggerEntity) {
        BlockPos pos = triggerEntity.blockPosition();
        AABB searchBox = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(pos));

        // 一次性拉取该格内所有物品实体（排除自身和已移除实体）
        List<ItemEntity> nearby = triggerEntity.level().getEntitiesOfClass(
                ItemEntity.class,
                searchBox,
                entity -> entity != triggerEntity && entity.isAlive()
        );

        if (nearby.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Item, Integer> counts = new HashMap<>(nearby.size() * 2);
        for (ItemEntity e : nearby) {
            ItemStack stack = e.getItem();
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    // =========== 催化剂消耗 ========== //
    // 在世界上消耗催化剂物品
    public void consumeFromLevel(ItemEntity triggerEntity, int startItemCount) {
        if (!catalystConsume || !hasAnyCatalyst()) {
            return;
        }

        // 查找范围：起始物品所在格子的 1×1×1 AABB
        BlockPos pos = triggerEntity.blockPosition();
        AABB searchBox = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(pos));

        for (CatalystEntry entry : getCatalystList()) {
            if (!entry.isValid()) continue;

            int remaining = entry.getRequiredCount(startItemCount);
            if (entry.isTagEntry()) {
                // 标签条目：按展开顺序依次消耗，不够再换下一种
                for (Item tagItem : entry.getTagItems()) {
                    if (remaining <= 0) break;
                    remaining = consumeItem(triggerEntity, searchBox, tagItem, remaining);
                }
            } else {
                remaining = consumeItem(triggerEntity, searchBox, entry.getItem(), remaining);
            }
            if (remaining > 0) {
                LOGGER.warn("Catalyst consumption incomplete for {}: still need {} more",
                        entry.itemId(), remaining);
            }
        }
    }

    private int consumeItem(ItemEntity triggerEntity, AABB searchBox, Item targetItem, int remaining) {
        List<ItemEntity> candidates = triggerEntity.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                entity -> entity != triggerEntity
                        && entity.isAlive()
                        && entity.getItem().getItem() == targetItem
        );
        candidates.sort(Comparator.comparingInt(entity -> entity.getItem().getCount()));

        for (ItemEntity candidate : candidates) {
            if (remaining <= 0) break;
            int available = candidate.getItem().getCount();
            if (available <= remaining) {
                remaining -= available;
                candidate.discard();
            } else {
                candidate.getItem().shrink(remaining);
                remaining = 0;
            }
        }
        return remaining;
    }

    // 计算在消耗模式下，当前周围催化剂数量最多能支持转化多少个起始物品。
    public int getMaxConvertible(Map<Item, Integer> snapshot, int startCount) {
        if (!hasAnyCatalyst() || !catalystConsume) {
            return startCount;
        }

        int max = startCount;
        for (CatalystEntry entry : catalystList) {
            int available;
            if (entry.isTagEntry()) {
                available = entry.getTagItems().stream()
                        .mapToInt(item -> snapshot.getOrDefault(item, 0))
                        .sum();
            } else {
                available = snapshot.getOrDefault(entry.getItem(), 0);
            }
            // 每个起始物品需要 entry.count() 个催化剂，向下取整
            int possible = available / entry.count();
            if (possible < max) {
                max = possible;
            }
        }
        return Math.max(0, max);
    }

    public int getMaxConvertible(ItemEntity triggerEntity, int startCount) {
        if (!hasAnyCatalyst() || !catalystConsume) {
            return startCount;
        }
        return getMaxConvertible(collectNearbyItemCounts(triggerEntity), startCount);
    }

    // ========== 工具方法 ========== //

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
    // 当起始物品为1时需要的催化剂物品的种类和数量
    public static final class CatalystEntry {
        @SerializedName("item")
        private final String itemId;

        @SerializedName("count")
        private final int count;

        // 缓存催化剂物品，避免每次都查注册表（仅普通条目使用）
        private transient Item cachedItem;
        // 缓存标签展开的物品列表（仅标签条目使用）
        private transient List<Item> cachedTagItems;

        public CatalystEntry(String itemId, int count) {
            this.itemId = itemId;
            this.count = Math.max(1, count);
        }

        public String itemId() {
            return itemId;
        }

        public int count() {
            return Math.max(1, count);
        }

        public int getRequiredCount(int startItemCount) {
            return count() * Math.max(1, startItemCount);
        }

        public boolean isTagEntry() {
            return itemId != null && itemId.startsWith("#");
        }

        public boolean isValid() {
            return IdValidator.isValidItemId(itemId) && count >= 1;
        }

        public Item getItem() {
            if (cachedItem == null && !isTagEntry()) {
                ResourceLocation rl = ResourceLocation.tryParse(itemId != null ? itemId : "");
                cachedItem = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
            }
            return cachedItem;
        }

        public List<Item> getTagItems() {
            if (cachedTagItems == null && isTagEntry()) {
                ResourceLocation tagRl = ResourceLocation.tryParse(itemId.substring(1));
                if (tagRl == null) {
                    cachedTagItems = Collections.emptyList();
                } else {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagRl);
                    List<Item> expanded = new ArrayList<>();
                    BuiltInRegistries.ITEM.getTag(tagKey).ifPresent(holders ->
                            holders.forEach(h -> expanded.add(h.value())));
                    cachedTagItems = Collections.unmodifiableList(expanded);
                }
            }
            return cachedTagItems != null ? cachedTagItems : Collections.emptyList();
        }
    }
}
