package com.meteorite.itemdespawntowhat.client.ui.screen.form;

/**
 * 条件编辑的宿主协调责任：打开编辑作用域前提交原宿主表单中待处理的输入，
 * 正常关闭时结束视图、刷新原宿主并通知草稿已变化，作用域失效时只丢弃旧视图。
 *
 * <p>本能力只处理宿主生命周期，不推断编辑目标：绑定到哪个草稿字段由
 * {@link ConditionEditContext} 表达，两者可以分别装配。</p>
 */
public interface ConditionHostCoordination {

    /** 打开作用域前提交原宿主表单中待处理的输入。 */
    void prepareHost();

    /**
     * 正常关闭：按既有顺序结束作用域视图、刷新原宿主并通知宿主草稿已变化。
     *
     * @param view 被关闭的作用域视图
     */
    void closeView(ConditionTreeOverlay view);

    /**
     * 失效关闭：只丢弃旧视图，不提交该路径的控件输入。
     *
     * @param view 失效的作用域视图
     */
    void discardView(ConditionTreeOverlay view);
}
