package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 本轮故障注入的声明解析与次数账：故障在场景启动时武装，只在调试包内生效。
 * 匹配目标、消耗次数与结构化事件都在这里完成，注入动作由任务装饰器与重载观测点触发。
 */
final class DebugScenarioFaults {
    private final DebugScenarioRun run;
    private final List<DebugScenarioSpec.Fault> declarations;
    private final int[] remaining;
    private int injected;

    DebugScenarioFaults(DebugScenarioRun run, List<DebugScenarioSpec.Fault> declarations) {
        this.run = run;
        this.declarations = declarations;
        this.remaining = new int[declarations.size()];
        for (int i = 0; i < declarations.size(); i++) { remaining[i] = declarations.get(i).count(); }
    }

    // 武装期逐条记录声明：日志里的声明与命中次数一一对应，便于核对配置是否真的生效
    void arm() {
        for (int i = 0; i < declarations.size(); i++) { run.fault("FAULT_ARMED", payload(i)); }
    }

    // 步骤异常在委托任务执行前注入：调度器按失败收尾并走任务自己的失败回调
    boolean step(ServerTaskKind kind, String name) {
        return hit(DebugScenarioSpec.FaultType.STEP_EXCEPTION, kind, name);
    }

    // 取消回调异常沿调用链抛出以验证隔离；装饰器仍在 finally 中完成委托清理。
    boolean cancel(ServerTaskKind kind, String name) {
        return hit(DebugScenarioSpec.FaultType.CANCEL_EXCEPTION, kind, name);
    }

    // 效果准备阶段异常在委托 prepare 前注入：异常沿转化补偿单元抛出，用于复现检查任务收尾缺陷
    void prepare(ResourceLocation effectType) {
        if (hit(DebugScenarioSpec.FaultType.PREPARE_EXCEPTION, null, effectType.toString())) {
            throw new InjectedFault("注入的效果准备异常：" + effectType);
        }
    }

    // 重载失败在应用新索引后注入：调用方按重载失败统一处理，用于复现失败后状态不一致
    void reload() {
        if (hit(DebugScenarioSpec.FaultType.RELOAD_FAILURE, null, "")) {
            throw new InjectedFault("注入的规则重载失败");
        }
    }

    // 收尾时报告未消费的声明，避免故障配置写错却静默通过
    void reportUnused() {
        for (int i = 0; i < declarations.size(); i++) {
            if (remaining[i] <= 0) { continue; }
            run.fault("FAULT_UNUSED", payload(i));
        }
    }

    int declared() { return declarations.size(); }

    int injected() { return injected; }

    int remaining() {
        int total = 0;
        for (int value : remaining) { total += value; }
        return total;
    }

    // 匹配顺序即声明顺序；分类与追踪名都为空时表示不限制目标
    private boolean hit(DebugScenarioSpec.FaultType type, @Nullable ServerTaskKind kind, String name) {
        for (int i = 0; i < declarations.size(); i++) {
            var fault = declarations.get(i);
            if (fault.type() != type || remaining[i] <= 0) { continue; }
            if (fault.kind() != null && fault.kind() != kind) { continue; }
            if (!fault.name().isEmpty() && !fault.name().equals(name)) { continue; }
            remaining[i]--;
            injected++;
            run.fault(event(type), payload(i));
            return true;
        }
        return false;
    }

    // 事件名是场景断言的消费口径，改名等于改对外契约
    private static String event(DebugScenarioSpec.FaultType type) {
        return switch (type) {
            case STEP_EXCEPTION -> "FAULT_STEP_EXCEPTION";
            case CANCEL_EXCEPTION -> "FAULT_CANCEL_EXCEPTION";
            case PREPARE_EXCEPTION -> "FAULT_PREPARE_EXCEPTION";
            case RELOAD_FAILURE -> "FAULT_RELOAD_FAILURE";
        };
    }

    // 记录声明内容与本次消耗后剩余次数，只输出既有字段，不暴露场景内部状态
    private JsonObject payload(int index) {
        var fault = declarations.get(index);
        JsonObject data = new JsonObject();
        data.addProperty("fault", fault.type().name());
        if (fault.kind() != null) { data.addProperty("kind", fault.kind().name()); }
        if (!fault.name().isEmpty()) { data.addProperty("name", fault.name()); }
        data.addProperty("remaining", remaining[index]);
        return data;
    }

    /** 注入故障专用异常：只用于让真实链路走到故障分支，不带业务语义。 */
    static final class InjectedFault extends RuntimeException {
        InjectedFault(String message) { super(message); }
    }
}
