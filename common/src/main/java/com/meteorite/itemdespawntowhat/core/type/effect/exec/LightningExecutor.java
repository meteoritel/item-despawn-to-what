package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.type.effect.LightningEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * lightning 执行器：第一道闪电沿触发位置的水平坐标落下，后续在附近散布，落点贴合最上层碰撞或流体表面。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果），
 * 也不参与容量计算与组数限制。
 * 每道闪电通过 context.reportProgress(1) 回执真实完成量。
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

    /**
     * 按时间间隔调度闪电，实际落点在每道闪电执行时计算。
     * @param effect 闪电效果参数
     * @param context 规则执行上下文
     * @return 一次性闪电序列的待完成回执
     */
    public static EffectResult execute(LightningEffect effect, EffectContext context) {
        if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.containsArea(context.level(),
                net.minecraft.core.BlockPos.containing(context.position()), 6)) {
            context.schedule(20, () -> execute(effect, context));
            // 等待区块加载：本次不计入账目，真实结果由后续调用回执
            return EffectResult.deferred(0, "awaiting_chunk");
        }
        ServerLevel level = context.level();
        Vec3 origin = context.position();
        for (int i = 0; i < effect.count(); i++) {
            int delay = i * INTERVAL_TICKS;
            boolean exact = i == 0;
            // 延迟任务绑定维度与位置，不依赖源实体存活
            context.schedule(delay, () -> strike(context, level, origin, exact));
        }
        return EffectResult.deferred(effect.count(), "strike_series").asOneShot();
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
        // 高度图只给整格高度；沿实际水平坐标向下求交，贴合下半砖、台阶和流体表面。
        // 射线保持在已加载的同一列中，不向外搜索或加载新区块；虚空无表面时沿用高度图落点。
        Vec3 rayStart = new Vec3(strikeX, groundPos.getY() + 1.0D, strikeZ);
        Vec3 rayEnd = new Vec3(strikeX, level.getMinBuildHeight() - 1.0D, strikeZ);
        BlockHitResult surface = level.clip(new ClipContext(rayStart, rayEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, bolt));
        double strikeY = surface.getType() == HitResult.Type.BLOCK ? surface.getLocation().y : groundPos.getY();
        bolt.moveTo(strikeX, strikeY, strikeZ);
        EffectTargets.addEntity(context, bolt);
        // 真实完成量回执：每道闪电计 1
        context.reportProgress(1);
    }
}
