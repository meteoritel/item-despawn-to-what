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
 * 由 NeoForge 开发源集在服务端装配阶段注入（见 {@link RuntimeObserver#install(RuntimeObserver)}）。
 * 本实现与场景资源随开发源集编译，生产运行使用契约层的空实现。
 */
public class DebugRuntimeObserver implements RuntimeObserver {

    /** 创建调试观测实现；无状态，可由装配方按需实例化。 */
    public DebugRuntimeObserver() {
    }

    @Override
    public boolean enabled() {
        return DebugMode.ENABLED;
    }

    /** 记录生产运行时推进的起点，不把场景准备成本计入运行时。 */
    @Override
    public void runtimeTickStarted(MinecraftServer server) {
        DebugSessionManager.beginRuntimeTick(server);
    }

    /** 封存同 tick 的运行时成本。 */
    @Override
    public void runtimeTickFinished(MinecraftServer server) {
        DebugSessionManager.endRuntimeTick(server);
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
    public void taskStepStarting(MinecraftServer server, ServerTaskKind kind, String tracingName) {
        DebugScenarioManager.taskStepStarting(server, kind, tracingName);
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
