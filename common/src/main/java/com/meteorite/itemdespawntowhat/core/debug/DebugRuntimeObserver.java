package com.meteorite.itemdespawntowhat.core.debug;

import com.meteorite.itemdespawntowhat.core.api.EffectContext;
import com.meteorite.itemdespawntowhat.core.api.RuntimeObserver;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ScheduledTaskHandle;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTask;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.api.scheduler.TaskQueue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 运行时观测接缝的调试实现：把生产侧上报的观测点与排期请求转交给调试场景子系统。
 *
 * <p>本类只做转发，不持有场景状态，因此每次调用都是对 {@link DebugScenarioManager} 的等价调用；
 * 由平台运行时在服务端装配阶段注入（见 {@link RuntimeObserver#install(RuntimeObserver)}）。
 * 仅在开发环境（{@link DebugMode#ENABLED}）下才可能产生观测开销，发布环境该常量为 false，
 * 各转发方法在调试子系统内部立即返回。
 */
public final class DebugRuntimeObserver implements RuntimeObserver {

    /** 创建调试观测实现；无状态，可由装配方按需实例化。 */
    public DebugRuntimeObserver() {
    }

    @Override
    public boolean enabled() {
        return DebugMode.ENABLED;
    }

    @Override
    public boolean inactive(MinecraftServer server) {
        return DebugScenarioManager.inactive(server);
    }

    @Override
    public void observe(ItemEntity source, String event, Object... fields) {
        DebugScenarioManager.observe(source, event, fields);
    }

    @Override
    public boolean allowsRuntimeLogging(ItemEntity source) {
        return DebugScenarioManager.allowsRuntimeLogging(source);
    }

    @Override
    public void converted(ItemEntity source, String ruleId, int rounds) {
        DebugScenarioManager.converted(source, ruleId, rounds);
    }

    @Override
    public @Nullable List<?> candidates(ItemEntity item) {
        return DebugScenarioManager.candidates(item);
    }

    @Override
    public void prepareOutput(EffectContext context, Entity entity) {
        DebugScenarioManager.prepareOutput(context, entity);
    }

    @Override
    public void outputAdded(EffectContext context, Entity entity) {
        DebugScenarioManager.outputAdded(context, entity);
    }

    @Override
    public void reloadApplying(MinecraftServer server) {
        DebugScenarioManager.reloadApplying(server);
    }

    @Override
    public void effectPreparing(MinecraftServer server, ResourceLocation effectType) {
        DebugScenarioManager.effectPreparing(server, effectType);
    }

    @Override
    public ScheduledTaskHandle schedule(ItemEntity source, TaskQueue queue, Object realmKey, ServerTaskKind kind,
                                        long dueTick, String name, Runnable action) {
        return DebugScenarioManager.schedule(source, queue, realmKey, kind, dueTick, name, action);
    }

    @Override
    public ScheduledTaskHandle schedule(ItemEntity source, TaskQueue queue, Object realmKey, long dueTick,
                                        ServerTask task) {
        return DebugScenarioManager.schedule(source, queue, realmKey, dueTick, task);
    }
}
