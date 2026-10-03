package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 所有功能与性能场景共用的资源契约，规则、输入、动作及断言均由资源声明。 */
record DebugScenarioSpec(String key, int sources, int stackSize, int seconds, int catalystCount,
                         ResourceLocation sourceItem, ResourceLocation catalystItem, Setup setup,
                         List<Action> actions, JsonObject expected, JsonArray rules) {
    static final int MAX_FUNCTIONAL_SOURCES = 16;
    static final int MAX_BENCHMARK_SOURCES = 1000;

    /** 准备阶段的实体操作，不依赖场景名称。 */
    enum Setup { NONE, EXPIRY, EXCLUDED }

    /** 场景动作的普通代码入口，新增动作不需要改命令或执行器的场景名分支。 */
    enum ActionType { MOVE_SOURCE, RELOAD }

    /** 动作触发门槛：世界刻、真实事件及移动距离。 */
    record Action(ActionType type, int afterTicks, String afterEvent, int offsetY) {}

    // 路径前缀同时区分同名功能与性能场景。
    boolean benchmark() { return key.startsWith("bench/"); }

    // 保留现有命令名称，目录迁移不影响游戏内触发。
    String name() { return key.substring(key.indexOf('/') + 1); }

    // 全部描述使用同一组本地化键，包括性能场景。
    String descriptionKey() { return "itemdespawntowhat.command.debug.scene.description." + key.replace('/', '.'); }
}
