package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * kit 的统一输入契约：指针、滚轮、键盘与字符输入。
 *
 * <p>kit 核心与宿主控件共享本契约。指针事件显式携带所属作用域及修饰键，键盘事件沿用
 * GLFW 键码与修饰键掩码。内容树只派发一次；持续交互的拖动与释放由
 * {@link UiInputRouter} 直接交给作用域登记的目标。</p>
 */
public interface UiInputTarget {

    /**
     * 离开整个作用域时清理按住的修饰态；会话已由作用域结束。
     */
    default void scopeExited(UiInteractionSession.EndReason reason) {
    }

    /** 指针按下；真实命中的叶子报告焦点候选，返回是否消费。 */
    default boolean mousePressed(UiInputContext context) {
        return false;
    }

    /** 指针拖动；匹配会话目标在指针离开矩形后仍会收到完整增量。 */
    default boolean mouseDragged(UiInputContext context, double dragX, double dragY) {
        return false;
    }

    /** 匹配会话的指针释放；目标自行判断是否仍满足激活条件。 */
    default boolean mouseReleased(UiInputContext context) {
        return false;
    }

    /** 双轴滚轮；scrollY 为正表示向上滚，返回是否消费。 */
    default boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        return false;
    }

    /** 内容树中的键按下；返回 true 后路由器不再执行焦点激活。 */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** 键抬起；重复调整按会话来源与键码匹配后完成一次操作。 */
    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** 当前文本目标处理字符，返回是否消费。 */
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
}
