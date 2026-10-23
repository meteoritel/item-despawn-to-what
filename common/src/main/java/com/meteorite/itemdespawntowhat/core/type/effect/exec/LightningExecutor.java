package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.EffectExecution;
import com.meteorite.itemdespawntowhat.core.api.EffectPreparation;
import com.meteorite.itemdespawntowhat.core.api.EffectResult;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTickBudget;
import com.meteorite.itemdespawntowhat.core.api.scheduler.StepWorkCounter;
import com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks;
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
import org.jetbrains.annotations.Nullable;

/**
 * lightning 执行器：第一道闪电沿触发位置的水平坐标落下，后续在附近散布，落点贴合最上层碰撞或流体表面。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果），
 * 也不参与容量计算与组数限制。
 * 本次次数、散布半径与基准位置在创建执行状态时固定，之后只按各自到期刻逐道推进；
 * 每道闪电真正加入世界后才通过 context.reportProgress(1) 回执真实完成量，
 * 全部落完的终态回执不再重复计入产出，只给出剩余增量 0。
 * 区块未加载时保留尚未落下的这一道，退避后按同一水平坐标重试（地表高度仍在执行期实时求交），不提前落下也不重新抽取坐标。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class LightningExecutor {

    // 相邻两道闪电的间隔（刻）
    public static final int INTERVAL_TICKS = 8;
    // 后续闪电的水平散布半径
    public static final double SPREAD_RADIUS = 5.0;

    // 序列开始前要求的整片已加载半径：生成位置可能越过源区块边界
    private static final int SERIES_CHUNK_RADIUS = 6;
    // 拒绝直接调用的原因：没有共享预算的实现无法按 tick 推进
    private static final String BUDGET_REQUIRED = "budget_required";
    // 缺少成功转化当刻固定输入的原因：本类型只能在准备阶段之后执行
    private static final String MISSING_PREPARATION = "missing_preparation";

    private LightningExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 拒绝没有共享预算的直接执行，闪电序列只能经执行状态在预算内推进。
     * @param effect 闪电效果参数
     * @param context 规则执行上下文
     * @return 表示必须使用执行状态入口的失败回执，不做任何世界写入
     */
    public static EffectResult execute(LightningEffect effect, EffectContext context) {
        return EffectResult.failed(BUDGET_REQUIRED + ": " + effect.type() + " @ " + context.ruleId());
    }

    /**
     * 按成功转化当刻固定的输入创建执行状态；次数与基准位置取自本次转换。
     * @param effect 闪电效果参数
     * @param context 本组上下文
     * @param preparation 成功转化当刻固定的输入；null 表示本类型未按约定准备，属真失败
     * @return 可跨 tick 续接的闪电序列执行状态
     */
    public static EffectExecution start(LightningEffect effect, EffectContext context,
                                        @Nullable EffectPreparation preparation) {
        if (preparation == null) {
            // 没有准备结果说明调用方绕过了准备阶段：条件与概率无从固定，按失败回执而非静默降级
            return budget -> EffectResult.failed(
                    MISSING_PREPARATION + ": " + effect.type() + " @ " + context.ruleId());
        }
        if (preparation.gate() != null) {
            // 已判定不执行：不重新判定条件或概率，也不做世界写入
            String reason = preparation.gate();
            return budget -> EffectResult.skipped(reason);
        }
        return new StrikeSeries(effect, context);
    }

    /** 闪电序列的推进状态：逐道落下，间隔与区块等待由本状态内的到期刻门控。 */
    private static final class StrikeSeries implements EffectExecution {
        private final EffectContext context;
        private final int count;
        // 成功转化当刻固定的基准位置：后续不再读取源实体
        private final Vec3 origin;
        private int launched;
        private long nextDueTick;
        // 尚未落下的这一道的水平坐标：等待区块时保留，不重新抽取
        private double pendingX;
        private double pendingZ;
        private boolean pendingTarget;
        private EffectResult terminal;
        private boolean waitingForChunks;

        StrikeSeries(LightningEffect effect, EffectContext context) {
            this.context = context;
            this.count = effect.count();
            this.origin = context.position();
            this.nextDueTick = context.level().getGameTime();
        }

        @Override
        public @Nullable EffectResult advance(ServerTickBudget budget) {
            if (terminal != null) {
                return terminal;
            }
            if (launched >= count) {
                return finish();
            }
            ServerLevel level = context.level();
            long now = level.getGameTime();
            if (now < nextDueTick) {
                return null;
            }
            waitingForChunks = false;
            if (!LoadedChunks.containsArea(level, BlockPos.containing(origin), SERIES_CHUNK_RADIUS)) {
                waitingForChunks = true;
                // 整片未加载：本次不计入账目，退避后仍在同一基准位置重来
                nextDueTick = now + LoadedChunks.RETRY_TICKS;
                return null;
            }
            // 每步只落一道：单道世界操作不可抢占，步间让出以遵守共享预算
            if (budget.exhausted()) {
                return null;
            }
            if (!pendingTarget) {
                if (launched == 0) {
                    // 第 1 道保留基准位置的水平坐标，后续按散布半径抽取
                    pendingX = origin.x;
                    pendingZ = origin.z;
                } else {
                    double angle = level.random.nextDouble() * 2.0 * Math.PI;
                    double spread = Math.sqrt(level.random.nextDouble()) * SPREAD_RADIUS;
                    pendingX = origin.x + Math.cos(angle) * spread;
                    pendingZ = origin.z + Math.sin(angle) * spread;
                }
                pendingTarget = true;
            }
            if (!LoadedChunks.contains(level, BlockPos.containing(pendingX, origin.y, pendingZ))) {
                waitingForChunks = true;
                // 该列未加载：保留这道闪电及其坐标，退避后重试，不推进次数
                nextDueTick = now + LoadedChunks.RETRY_TICKS;
                return null;
            }
            if (!strike(level, pendingX, pendingZ)) {
                waitingForChunks = true;
                // 两次检查之间该列卸载：保留已确定的水平坐标，退避后按同一坐标重新求地表，不推进次数
                nextDueTick = now + LoadedChunks.RETRY_TICKS;
                return null;
            }
            context.reportProgress(1);
            StepWorkCounter.report(1);
            launched++;
            pendingTarget = false;
            nextDueTick = now + INTERVAL_TICKS;
            return launched >= count ? finish() : null;
        }

        @Override
        public boolean waitingForChunks() {
            return waitingForChunks;
        }

        // 全部落完：真实完成量已逐道回执，终态只给出剩余增量，不重复累计
        private EffectResult finish() {
            terminal = EffectResult.applied(0, "strike_series").asOneShot();
            return terminal;
        }

        /**
         * 在指定水平坐标的地面高度召唤一道闪电。
         * @param level 目标世界
         * @param strikeX 已确定的水平坐标 X
         * @param strikeZ 已确定的水平坐标 Z
         * @return 已加入世界返回 true；该列在写入前卸载返回 false
         */
        private boolean strike(ServerLevel level, double strikeX, double strikeZ) {
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
            // 生成列已确认加载：此处加入世界是即时完成的，回执才代表真实落雷
            return EffectTargets.addEntityNow(context, bolt, false);
        }
    }
}
