package com.meteorite.itemdespawntowhat.client.ui.panel.configlist;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class EntityIconCache {

    private static final Map<EntityType<?>, LivingEntity> ENTITY_ICON_CACHE = new HashMap<>();

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
