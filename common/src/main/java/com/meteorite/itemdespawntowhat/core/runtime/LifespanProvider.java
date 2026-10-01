package com.meteorite.itemdespawntowhat.core.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 掉落物自然消失时间的提供者（平台差异收敛点）。
 * NeoForge 端可返回 getEntityLifespan 事件值；Fabric 端返回可配置的兜底常量。
 */
@FunctionalInterface
public interface LifespanProvider {

    // 返回该掉落物的自然存活刻数
    int lifespanTicks(ServerLevel level, ItemEntity entity);

    // 原版默认值：age >= 6000 时 discard
    static LifespanProvider vanillaDefault() {
        return (level, entity) -> 6000;
    }
}
