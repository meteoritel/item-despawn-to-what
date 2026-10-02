package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.ExplosionEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * explosion 执行器：产生一次爆炸；visual_only 为真时只播放粒子与音效，不破坏方块、不伤害实体。
 * rounds 语义：一次性世界效果，不随 rounds 缩放（不是「每个物品一份」的结果）。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class ExplosionExecutor {

    private ExplosionExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(ExplosionEffect effect, EffectContext context) {
        if (!com.meteorite.itemdespawntowhat.core.runtime.LoadedChunks.containsArea(context.level(),
                net.minecraft.core.BlockPos.containing(context.position()), (int) Math.ceil(effect.power() * 2) + 1)) {
            context.schedule(20, () -> execute(effect, context));
            return;
        }
        ServerLevel level = context.level();
        Vec3 position = context.position();
        if (effect.visualOnly()) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    position.x, position.y, position.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.playSound(null, position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE,
                    SoundSource.BLOCKS, 4.0F,
                    (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
            return;
        }
        level.explode(null, position.x, position.y, position.z, effect.power(), effect.fire(),
                Level.ExplosionInteraction.TNT);
    }
}
