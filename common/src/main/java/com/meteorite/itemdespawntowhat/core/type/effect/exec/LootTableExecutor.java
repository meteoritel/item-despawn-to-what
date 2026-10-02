package com.meteorite.itemdespawntowhat.core.type.effect.exec;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.type.effect.LootTableEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * loot_table 执行器：以触发位置为原点、luck 为幸运值开表并生成掉落物。
 * rounds 语义：按 rounds 次开表（等价的产出折算），受安全上限收敛并记录。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class LootTableExecutor {

    private static final Logger LOGGER = LogManager.getLogger();

    // 单次转化的开表次数安全上限：极端堆叠下避免一次触发产生过多实体
    public static final int MAX_ROLLS = 256;

    private LootTableExecutor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void execute(LootTableEffect effect, EffectContext context) {
        ServerLevel level = context.level();
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, effect.lootTable());
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, context.position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, context.source())
                .withLuck(effect.luck())
                .create(LootContextParamSets.CHEST);
        Vec3 position = context.position();
        int rounds = context.rounds();
        int rolls = Math.min(rounds, MAX_ROLLS);
        if (rolls < rounds) {
            LOGGER.debug("loot_table 开表次数被安全上限收敛：规则={} 期望={} 实际={}", context.ruleId(), rounds, rolls);
        }
        // 使用消费式重载，避免额外收集一层列表；每轮开表一次以按 rounds 放大产出
        for (int i = 0; i < rolls; i++) {
            lootTable.getRandomItems(params, stack -> spawnLoot(level, position, stack));
        }
    }

    // 在触发位置附近生成一件战利品掉落物
    private static void spawnLoot(ServerLevel level, Vec3 position, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity entity = new ItemEntity(level,
                position.x + (level.random.nextDouble() - 0.5) * 0.3,
                position.y + 0.1,
                position.z + (level.random.nextDouble() - 0.5) * 0.3,
                stack.copy());
        entity.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2,
                (level.random.nextDouble() - 0.5) * 0.1);
        level.addFreshEntity(entity);
    }
}
