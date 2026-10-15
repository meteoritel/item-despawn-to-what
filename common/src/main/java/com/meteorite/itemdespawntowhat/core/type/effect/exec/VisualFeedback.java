package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 服务端可见的转化反馈效果。
 * 在转化提交、产物生成与返还交付节点触发粒子；只能由服务端主线程调用。
 * 同刻同方块的同类反馈合并，每种反馈设独立上限，避免密集产出堆叠粒子与网络包。
 */
public final class VisualFeedback {
    private static final int MAX_BURSTS_PER_KIND_PER_TICK = 16;
    private static final ParticleStyle CONVERSION_PORTAL =
            new ParticleStyle(ParticleTypes.PORTAL, 18, 0.25D, 0.35D, 0.25D, 0.15D);
    private static final ParticleStyle CONVERSION_SPARKLE =
            new ParticleStyle(ParticleTypes.ENCHANT, 9, 0.35D, 0.25D, 0.2D, 0.8D);
    private static final ParticleStyle ENTITY_SPAWN =
            new ParticleStyle(ParticleTypes.END_ROD, 8, 0.35D, 0.2D, 0.25D, 0.02D);
    private static final ParticleStyle ITEM_RETURN =
            new ParticleStyle(ParticleTypes.HAPPY_VILLAGER, 10, 0.2D, 0.25D, 0.2D, 0.05D);
    // 值不持有世界引用，维度卸载后可回收限流状态；集合大小受单刻上限约束。
    private static final Map<ServerLevel, TickFeedback> FEEDBACK = new WeakHashMap<>();

    private VisualFeedback() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 播放转化任务成功提交后的源位置粒子。
     * @param level 源物品所在世界
     * @param position 源物品位置
     */
    public static void conversion(ServerLevel level, Vec3 position) {
        if (allow(level, position, Kind.CONVERSION)) {
            emit(level, position, CONVERSION_PORTAL);
            emit(level, position, CONVERSION_SPARKLE);
        }
    }

    /**
     * 播放实体产物成功加入世界后的生成粒子。
     * @param level 产物所在世界
     * @param position 实际产出位置
     */
    public static void entitySpawn(ServerLevel level, Vec3 position) {
        if (allow(level, position, Kind.SPAWN)) {
            emit(level, position, ENTITY_SPAWN);
        }
    }

    /**
     * 播放返还物成功加入世界后的绿色粒子。
     * @param level 返还物所在世界
     * @param position 实际返还位置
     */
    public static void returnItem(ServerLevel level, Vec3 position) {
        if (allow(level, position, Kind.RETURN)) {
            emit(level, position, ITEM_RETURN);
        }
    }

    private static boolean allow(ServerLevel level, Vec3 position, Kind kind) {
        TickFeedback feedback = FEEDBACK.computeIfAbsent(level, ignored -> new TickFeedback());
        long gameTime = level.getGameTime();
        if (feedback.gameTime != gameTime) {
            feedback.gameTime = gameTime;
            feedback.positions.clear();
        }
        Set<BlockPos> positions = feedback.positions.computeIfAbsent(kind, ignored -> new HashSet<>());
        return positions.size() < MAX_BURSTS_PER_KIND_PER_TICK && positions.add(BlockPos.containing(position));
    }

    private static void emit(ServerLevel level, Vec3 position, ParticleStyle style) {
        level.sendParticles(style.particle(), position.x, position.y + style.height(), position.z,
                style.count(), style.horizontalSpread(), style.verticalSpread(), style.horizontalSpread(), style.speed());
    }

    private enum Kind { CONVERSION, SPAWN, RETURN }

    private record ParticleStyle(SimpleParticleType particle, int count, double height,
                                 double horizontalSpread, double verticalSpread, double speed) {}

    private static final class TickFeedback {
        private long gameTime = Long.MIN_VALUE;
        private final Map<Kind, Set<BlockPos>> positions = new EnumMap<>(Kind.class);
    }
}
