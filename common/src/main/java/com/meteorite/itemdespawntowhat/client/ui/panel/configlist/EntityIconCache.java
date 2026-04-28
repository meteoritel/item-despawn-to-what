package com.meteorite.itemdespawntowhat.client.ui.panel.configlist;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EntityIconCache {

    private static final int MAX_CACHE_SIZE = 50;
    private static final Map<EntityType<?>, LivingEntity> ENTITY_ICON_CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<EntityType<?>, LivingEntity> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            };

    private EntityIconCache() {
    }

    @Nullable
    public static LivingEntity getOrCreateEntityIcon(EntityType<?> type, Level level) {
        return ENTITY_ICON_CACHE.computeIfAbsent(type, t -> {
            var entity = t.create(level);
            return (entity instanceof LivingEntity livingEntity) ? livingEntity : null;
        });
    }

    public static void clear() {
        ENTITY_ICON_CACHE.clear();
    }
}
