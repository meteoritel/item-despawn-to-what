package com.meteorite.itemdespawntowhat.config.catalogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.meteorite.itemdespawntowhat.condition.ConditionSerializable;
import com.meteorite.itemdespawntowhat.config.ConversionLimits;
import com.meteorite.itemdespawntowhat.util.IdValidator;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import com.meteorite.itemdespawntowhat.util.TagResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * 催化剂条件、轮数计算与世界物品消耗逻辑。
 */
public class CatalystItems implements ConditionSerializable<CatalystItems> {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();
    private static final Logger LOGGER = LogManager.getLogger();

    @SerializedName("catalyst_items")
    private List<CatalystEntry> catalystList;

    @SerializedName("consume_catalyst")
    private boolean catalystConsume = true;

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

        return parsed;
    }

    // ========== 条件检测方法 ========== //
    // 检查是否存在完整的一套催化剂。
    public boolean checkCondition(Map<Item, Integer> snapshot, int sourceMultiple) {
        if (!hasAnyCatalyst()) {
            return true;
        }
        return canAllocateRounds(snapshot, 1, Math.max(1, sourceMultiple));
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
                counts.merge(stack.getItem(), stack.getCount(), CatalystItems::saturatedAdd);
            }
        }
        return counts;
    }

    // =========== 催化剂消耗 ========== //
    // 在世界上消耗催化剂物品
    public void consumeFromLevel(ItemEntity triggerEntity, int consumeStartItem) {
        if (!catalystConsume || !hasAnyCatalyst()) {
            return;
        }

        // 查找范围：起始物品所在格子的 1×1×1 AABB
        BlockPos pos = triggerEntity.blockPosition();
        AABB searchBox = AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(pos));

        for (CatalystEntry entry : getCatalystList()) {
            if (!entry.isValid()) continue;

            int remaining = entry.getRequiredCount(consumeStartItem);
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

    // 计算催化剂能支持的最大转化轮数，每轮会消耗 sourceMultiple 个源物品
    public int getMaxConvertibleRounds(ItemEntity triggerEntity, int sourceMultiple) {
        if (!hasAnyCatalyst() || !catalystConsume) {
            return Integer.MAX_VALUE;
        }
        Map<Item, Integer> snapshot = collectNearbyItemCounts(triggerEntity);
        int normalizedSourceMultiple = Math.max(1, sourceMultiple);
        int maxRounds = Integer.MAX_VALUE;
        for (CatalystEntry entry : catalystList) {
            int available = getAvailableCount(entry, snapshot);
            int requiredPerRound = entry.count() * normalizedSourceMultiple;
            int possible = available / requiredPerRound;
            if (possible < maxRounds) {
                maxRounds = possible;
            }
        }

        int low = 0;
        int high = Math.max(0, maxRounds);
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (canAllocateRounds(snapshot, middle, normalizedSourceMultiple)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private boolean canAllocateRounds(Map<Item, Integer> snapshot, int rounds, int sourceMultiple) {
        Map<Item, Integer> remainingCounts = new HashMap<>(snapshot);
        for (CatalystEntry entry : catalystList) {
            long requiredLong = (long) entry.count() * sourceMultiple * rounds;
            if (requiredLong > Integer.MAX_VALUE) {
                return false;
            }
            int required = (int) requiredLong;
            if (entry.isTagEntry()) {
                for (Item item : entry.getTagItems()) {
                    int available = remainingCounts.getOrDefault(item, 0);
                    int consumed = Math.min(available, required);
                    required -= consumed;
                    remainingCounts.put(item, available - consumed);
                    if (required == 0) {
                        break;
                    }
                }
            } else {
                Item item = entry.getItem();
                int available = remainingCounts.getOrDefault(item, 0);
                int consumed = Math.min(available, required);
                required -= consumed;
                remainingCounts.put(item, available - consumed);
            }
            if (required > 0) {
                return false;
            }
        }
        return true;
    }

    private static int getAvailableCount(CatalystEntry entry, Map<Item, Integer> snapshot) {
        if (!entry.isTagEntry()) {
            return snapshot.getOrDefault(entry.getItem(), 0);
        }

        int available = 0;
        for (Item item : entry.getTagItems()) {
            available = saturatedAdd(available, snapshot.getOrDefault(item, 0));
        }
        return available;
    }

    private static int saturatedAdd(int left, int right) {
        long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    // ========== 工具方法 ========== //

    public boolean hasAnyCatalyst() {
        return catalystList != null && !catalystList.isEmpty() && isAllEntryValid();
    }

    public boolean isAllEntryValid() {
        return catalystList != null && catalystList.stream().allMatch(CatalystEntry::isValid);
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
    /**
     * 单种催化剂物品或物品标签及其单位源物品需求量。
     */
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
            long required = (long) count() * Math.max(1, startItemCount);
            return required >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required;
        }

        public boolean isTagEntry() {
            return TagResolver.isTagId(itemId);
        }

        public boolean isValid() {
            return IdValidator.isValidItemId(itemId)
                    && count >= 1
                    && count <= ConversionLimits.MAX_CATALYST_ENTRY_COUNT;
        }

        private ResourceLocation parseItemRl() {
            return SafeParseUtil.parseResourceLocation(itemId);
        }

        public Item getItem() {
            if (cachedItem == null && !isTagEntry()) {
                ResourceLocation rl = parseItemRl();
                cachedItem = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
            }
            return cachedItem;
        }

        public List<Item> getTagItems() {
            if (cachedTagItems == null && isTagEntry()) {
                cachedTagItems = TagResolver.resolveTagItems(BuiltInRegistries.ITEM, Registries.ITEM, itemId);
            }
            return cachedTagItems != null ? cachedTagItems : List.of();
        }
    }
}
