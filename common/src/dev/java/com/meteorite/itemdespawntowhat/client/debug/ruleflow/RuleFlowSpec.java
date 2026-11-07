package com.meteorite.itemdespawntowhat.client.debug.ruleflow;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 场景内容描述：三列各自的物品、列标题与触发条件文案。
 *
 * <p>只承载数据，不持有屏幕或全局时钟；视图与模块因此可以按同一份 spec 重绘。
 * 页面标题、目录按钮与卡片标题共用一个语言 key，也收在这里，避免标题字符串散落在屏幕与视图两处。</p>
 */
record RuleFlowSpec(List<ItemStack> inputs, List<ItemStack> results,
                    Component inputTitle, Component triggerTitle, Component resultTitle,
                    Component triggerDelay, List<Component> triggerDetails) {
    /** 页面标题、目录按钮与卡片标题共用的 key。 */
    static final String UI = "gui.itemdespawntowhat.debug.workbench.rule_flow";
    /** 页脚返回沿用工作台通用 key，场景不重复定义一份返回文案。 */
    static final String BACK_KEY = "gui.itemdespawntowhat.debug.workbench.back";

    RuleFlowSpec {
        inputs = List.copyOf(inputs);
        results = List.copyOf(results);
        triggerDetails = List.copyOf(triggerDetails);
    }
}
