package com.meteorite.itemdespawntowhat.server.conversion;

import com.meteorite.itemdespawntowhat.ConfigExtractorManager;
import com.meteorite.itemdespawntowhat.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 按服务端维度维护待转换物品及其瞬态运行状态。
 */
public final class ConversionTracker {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<ServerLevel, TrackerBucket> TRACKED_BY_LEVEL = new HashMap<>();

    private ConversionTracker() {
    }

    // 仅在存在源物品规则时加入追踪集。
    public static void trackIfEligible(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel level)) {
            return;
        }
        if (itemEntity.getTags().contains(Constants.CHECK_LOCK_TAG)) {
            untrack(itemEntity);
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
        if (!ConfigExtractorManager.hasAnyConfigs(itemId)) {
            return;
        }

        TrackerBucket bucket = TRACKED_BY_LEVEL.computeIfAbsent(level, ignored -> new TrackerBucket());
        boolean added = bucket.add(itemEntity);
        if (added) {
            LOGGER.debug("Added item {} to conversion tracker", itemId);
        }
    }

    // 由平台世界 tick 在服务端线程调用。
    public static void tick(ServerLevel level, ItemLifespanResolver lifespanResolver) {
        if (!ItemConversionProcessor.shouldCheck(level)) {
            return;
        }

        TrackerBucket bucket = TRACKED_BY_LEVEL.get(level);
        if (bucket == null || bucket.tracked.isEmpty()) {
            return;
        }

        bucket.ticking = true;
        try {
            Iterator<Map.Entry<ItemEntity, ConversionState>> iterator = bucket.tracked.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ItemEntity, ConversionState> entry = iterator.next();
                ItemEntity itemEntity = entry.getKey();
                if (!isStillTracked(level, itemEntity)) {
                    iterator.remove();
                    continue;
                }

                int lifespan = lifespanResolver.getLifespan(itemEntity, level);
                if (ItemConversionProcessor.tickTrackedItem(itemEntity, entry.getValue(), lifespan)) {
                    iterator.remove();
                }
            }
        } finally {
            bucket.finishTick();
        }

        if (bucket.isEmpty()) {
            TRACKED_BY_LEVEL.remove(level);
        }
    }

    public static ConversionState getState(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel level)) {
            return null;
        }
        TrackerBucket bucket = TRACKED_BY_LEVEL.get(level);
        return bucket == null ? null : bucket.get(itemEntity);
    }

    public static boolean isTracked(ItemEntity itemEntity) {
        return getState(itemEntity) != null;
    }

    public static void untrack(ItemEntity itemEntity) {
        if (!(itemEntity.level() instanceof ServerLevel level)) {
            return;
        }
        TrackerBucket bucket = TRACKED_BY_LEVEL.get(level);
        if (bucket == null) {
            return;
        }
        bucket.remove(itemEntity);
        if (bucket.isEmpty()) {
            TRACKED_BY_LEVEL.remove(level);
        }
    }

    public static void clear(ServerLevel level) {
        TRACKED_BY_LEVEL.remove(level);
    }

    public static void clearAll() {
        TRACKED_BY_LEVEL.clear();
    }

    private static boolean isStillTracked(ServerLevel level, ItemEntity itemEntity) {
        return itemEntity.isAlive()
                && itemEntity.level() == level
                && level.isLoaded(itemEntity.blockPosition())
                && !itemEntity.getTags().contains(Constants.CHECK_LOCK_TAG);
    }

    /**
     * 隔离不同平台提供的掉落物寿命 API。
     */
    @FunctionalInterface
    public interface ItemLifespanResolver {
        int getLifespan(ItemEntity itemEntity, ServerLevel level);
    }

    private static final class TrackerBucket {
        private final Map<ItemEntity, ConversionState> tracked = new LinkedHashMap<>();
        private final Map<ItemEntity, ConversionState> pendingAdds = new LinkedHashMap<>();
        private final Set<ItemEntity> pendingRemovals = new LinkedHashSet<>();
        private boolean ticking;

        private boolean add(ItemEntity itemEntity) {
            if (tracked.containsKey(itemEntity) || pendingAdds.containsKey(itemEntity)) {
                return false;
            }
            if (ticking) {
                pendingAdds.put(itemEntity, new ConversionState());
            } else {
                tracked.put(itemEntity, new ConversionState());
            }
            pendingRemovals.remove(itemEntity);
            return true;
        }

        private ConversionState get(ItemEntity itemEntity) {
            ConversionState state = tracked.get(itemEntity);
            return state != null ? state : pendingAdds.get(itemEntity);
        }

        private void remove(ItemEntity itemEntity) {
            pendingAdds.remove(itemEntity);
            if (ticking) {
                pendingRemovals.add(itemEntity);
            } else {
                tracked.remove(itemEntity);
            }
        }

        private void finishTick() {
            ticking = false;
            for (ItemEntity itemEntity : pendingRemovals) {
                tracked.remove(itemEntity);
                pendingAdds.remove(itemEntity);
            }
            tracked.putAll(pendingAdds);
            pendingAdds.clear();
            pendingRemovals.clear();
        }

        private boolean isEmpty() {
            return tracked.isEmpty() && pendingAdds.isEmpty();
        }
    }

    /**
     * 单个被追踪物品的瞬态转换状态。
     */
    public static final class ConversionState {
        private String selectedConfigId = "";
        private int checkTimer;
        private boolean conversionLocked;

        public String selectedConfigId() {
            return selectedConfigId;
        }

        public void setSelectedConfigId(String selectedConfigId) {
            this.selectedConfigId = selectedConfigId == null ? "" : selectedConfigId;
        }

        public int checkTimer() {
            return checkTimer;
        }

        public void setCheckTimer(int checkTimer) {
            this.checkTimer = Math.max(0, checkTimer);
        }

        public boolean conversionLocked() {
            return conversionLocked;
        }

        public void setConversionLocked(boolean conversionLocked) {
            this.conversionLocked = conversionLocked;
        }

        public void resetProgress() {
            selectedConfigId = "";
            checkTimer = 0;
            conversionLocked = false;
        }
    }
}
