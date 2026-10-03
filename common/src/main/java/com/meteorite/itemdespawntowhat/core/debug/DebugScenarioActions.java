package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.command.RuleCommandContext;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.HashSet;
import java.util.Set;

/** 资源声明动作的执行状态；准备、移动及重载通过类型分派，与场景名称无关。 */
final class DebugScenarioActions {
    private final DebugScenarioRun run;
    private final Set<Integer> completed = new HashSet<>();
    private final long startedTick;
    private int expiryLifespan;

    // 每轮动作状态独立，目录中的不可变声明可以复用。
    DebugScenarioActions(DebugScenarioRun run) {
        this.run = run;
        startedTick = run.level.getGameTime();
    }

    // 只对本轮源应用实体准备操作，随后仍由正式后端推进。
    void prepareSource(ConversionRuntime runtime, ItemEntity item, int number) {
        switch (run.definition.spec.setup()) {
            case NONE -> { }
            case EXPIRY -> {
                expiryLifespan = runtime.lifespanTicks(run.level, item);
                if (expiryLifespan > 32767) { throw new IllegalStateException("消失场景年龄超出原版NBT short范围：" + expiryLifespan); }
                CompoundTag saved = item.saveWithoutId(new CompoundTag());
                saved.putShort("Age", (short) (expiryLifespan - 1));
                item.load(saved);
            }
            case EXCLUDED -> {
                if (number == 0) { item.addTag(Constants.CHECK_LOCK_TAG); }
                else { item.setUnlimitedLifetime(); }
            }
        }
    }

    // 每个动作最多执行一次，以真实事件和世界刻共同决定触发。
    void tick(RuleCommandContext context) {
        var actions = run.definition.spec.actions();
        for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            if (completed.contains(i) || run.level.getGameTime() - startedTick < action.afterTicks()
                    || !action.afterEvent().isEmpty() && run.count(action.afterEvent()) == 0) { continue; }
            switch (action.type()) {
                case MOVE_SOURCE -> {
                    ItemEntity source = run.sources.getFirst();
                    if (!source.isAlive()) { throw new IllegalStateException("动作执行时源实体已不存在"); }
                    source.setPos(source.getX(), source.getY() + action.offsetY(), source.getZ());
                    run.trace("ACTION", source, "action", "move_source", "offset_y", action.offsetY());
                }
                case RELOAD -> {
                    if (context.reloadRules(run.level.getServer()) == null) { throw new IllegalStateException("场景中的规则重载失败"); }
                    var data = run.state();
                    data.addProperty("overlay_version", context.overlayVersion());
                    run.log("ACTION_RELOAD", data);
                }
            }
            completed.add(i);
        }
    }

    // 统一校验器读取动作计数，不另写场景专属判定。
    int completedCount() { return completed.size(); }

    // 真实平台寿命用于相对断言，避免资源写死原版寿命。
    int expiryDueAge() { return expiryLifespan - 1; }
}
