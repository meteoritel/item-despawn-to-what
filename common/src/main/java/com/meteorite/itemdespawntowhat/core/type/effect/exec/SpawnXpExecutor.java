package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks;
import com.meteorite.itemdespawntowhat.core.type.effect.EntityProduct;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;

/**
 * 经验产出执行器：先汇总本组单个经验行为的总点数，再按单球安全点数分批生成。
 * per_source_item 使用本组实际扣减的源物品数，否则使用 rounds；二者不叠加。
 * 条件、概率与延迟由运行时处理，不跨行为或转化组合并经验。
 * 每球成功加入世界后回执真实点数；异常由运行时记录，未完成部分不重放。
 */
public final class SpawnXpExecutor {

    // 原版 Value 的 NBT 与生成数据包均使用有符号 short，构造参数为 int 不代表可安全存储 int。
    private static final int MAX_POINTS_PER_ORB = Short.MAX_VALUE;
    private static final int ORBS_PER_BATCH = 16;
    private static final double SCATTER_RADIUS = 0.5;
    private static final double SPAWN_HEIGHT = 0.2;
    private static final int CHUNK_RETRY_TICKS = 20;

    private SpawnXpExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 精确计算经验总量并受理分批交付，不因邻近经验球数量拒绝或截断产出。
     * @param effect 经验点数与倍率策略
     * @param context 本组执行上下文
     * @return 待交付点数的回执，点数为零时返回跳过
     */
    public static EffectResult execute(EntityProduct.Experience effect, EffectContext context) {
        int multiplier = effect.perSourceItem() ? context.coveredSourceItems() : context.rounds();
        long amount = (long) effect.amount() * multiplier;
        if (amount <= 0) {
            return EffectResult.skipped("zero_amount");
        }
        // 先登记待交付回执再开始生成，避免首批同步进度早于结算层的待交付入账。
        context.schedule(0, new XpBatch(context, amount));
        return EffectResult.deferred(amount, "awarded");
    }

    /** 单个经验行为的分批游标，仅由服务端线程访问。 */
    private static final class XpBatch implements Runnable {
        private final EffectContext context;
        private long remaining;
        private boolean feedbackSent;

        private XpBatch(EffectContext context, long amount) {
            this.context = context;
            this.remaining = amount;
        }

        @Override
        public void run() {
            // 圆盘可能跨越源区块边界；等待邻区块加载，不生成区块或提前记为成功。
            if (!LoadedChunks.containsArea(context.level(), BlockPos.containing(context.position()), 1)) {
                context.schedule(CHUNK_RETRY_TICKS, this);
                return;
            }
            for (int spawned = 0; spawned < ORBS_PER_BATCH && remaining > 0; spawned++) {
                int points = (int) Math.min(remaining, MAX_POINTS_PER_ORB);
                Vec3 position = EffectTargets.randomHorizontalDisc(context.random(), context.position(),
                        SCATTER_RADIUS, SPAWN_HEIGHT);
                // award 会再次按原版面额拆球；直接构造保留原版初速度、拾取和后续同值合并。
                EffectTargets.addEntity(context, new ExperienceOrb(context.level(), position.x, position.y, position.z, points));
                remaining -= points;
                context.reportProgress(points);
                if (!feedbackSent) {
                    VisualFeedback.entitySpawn(context.level(), context.position());
                    feedbackSent = true;
                }
            }
            if (remaining > 0) {
                context.schedule(1, this);
            }
        }
    }
}
