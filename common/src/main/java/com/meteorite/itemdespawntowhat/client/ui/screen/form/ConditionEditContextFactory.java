package com.meteorite.itemdespawntowhat.client.ui.screen.form;

/**
 * 条件编辑上下文的工厂：以实际条件字段的绝对草稿路径与所属对象路径创建局部目标上下文。
 *
 * <p>所属对象由持有该字段的表单显式给出，不在上下文中按字段名或后缀反推。每次创建都按当时传入的
 * 字段路径与所属对象即时生成上下文，不缓存绝对路径；因此子列表重排改变了字段路径后，后续创建自然
 * 得到新的绝对坐标，无需在上下文中做增量更新。</p>
 */
@FunctionalInterface
public interface ConditionEditContextFactory {

    /**
     * 为实际条件字段创建局部上下文。
     *
     * @param scope     条件字段在草稿中的绝对路径
     * @param ownerPath 该字段所属对象在草稿中的绝对路径；规则级为空串
     * @return 该字段对应的局部上下文
     */
    ConditionEditContext create(String scope, String ownerPath);
}
