package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.LightningEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * lightning 执行器：第一道闪电精确落在触发位置，后续按间隔在附近散布，均落到地面高度。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果）。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class LightningExecutor {

    // 相邻两道闪电的间隔（刻）
    public static final int INTERVAL_TICKS = 8;
    // 后续闪电的水平散布半径
    public static final double SPREAD_RADIUS = 5.0;

    private LightningExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(LightningEffect effect, EffectContext context) {
        if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.containsArea(context.level(),
                net.minecraft.core.BlockPos.containing(context.position()), 6)) {
            context.schedule(20, () -> execute(effect, context));
            return;
        }
        ServerLevel level = context.level();
        Vec3 origin = context.position();
        for (int i = 0; i < effect.count(); i++) {
            int delay = i * INTERVAL_TICKS;
            boolean exact = i == 0;
            // 延迟任务绑定维度与位置，不依赖源实体存活
            context.schedule(delay, () -> strike(context, level, origin, exact));
        }
    }

    // 在指定水平坐标的地面高度召唤一道闪电
    private static void strike(EffectContext context, ServerLevel level, Vec3 origin, boolean exact) {
        double strikeX;
        double strikeZ;
        if (exact) {
            strikeX = origin.x;
            strikeZ = origin.z;
        } else {
            double angle = level.random.nextDouble() * 2.0 * Math.PI;
            double spread = Math.sqrt(level.random.nextDouble()) * SPREAD_RADIUS;
            strikeX = origin.x + Math.cos(angle) * spread;
            strikeZ = origin.z + Math.sin(angle) * spread;
        }
        if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.contains(level, BlockPos.containing(strikeX, origin.y, strikeZ))) {
            context.schedule(20, () -> strike(context, level, origin, exact));
            return;
        }
        BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                BlockPos.containing(strikeX, origin.y, strikeZ));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            throw new IllegalStateException("无法创建闪电实体");
        }
        bolt.moveTo(strikeX, groundPos.getY(), strikeZ);
        EffectTargets.addEntity(context, bolt);
    }
}
