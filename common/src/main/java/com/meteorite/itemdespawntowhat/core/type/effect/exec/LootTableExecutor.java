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

/**
 * loot_table 执行器：以触发位置为原点、luck 为幸运值，抽取一次战利品表并生成掉落物。
 * 约定：delay_ticks / chance / 效果级 conditions 由运行时统一处理，本类不再判断；
 * 异常不吞、不捕获，由运行时统一捕获并记录（规则 id + 效果类型 + 位置）。
 */
public final class LootTableExecutor {

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
        // 使用消费式重载，避免额外收集一层列表
        lootTable.getRandomItems(params, stack -> spawnLoot(level, position, stack));
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
