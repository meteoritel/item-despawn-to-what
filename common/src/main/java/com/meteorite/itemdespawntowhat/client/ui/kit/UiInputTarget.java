package com.meteorite.itemdespawntowhat.client.ui.kit;

/**
 * kit 的统一输入契约：指针、滚轮、键盘与字符输入，全部由 {@link UiInputContext} 显式携带修饰键。
 *
 * <p>本接口与宿主 widget/UiWidget 是两套契约：kit 核心只依赖本接口，宿主控件通过适配方法把
 * 旧签名转发进来。所有方法返回「是否消费」，宿主据此保证同一事件只被消费一次。</p>
 */
public interface UiInputTarget {

    // 指针按下；返回 true 表示消费本次事件
    default boolean mousePressed(UiInputContext context) {
        return false;
    }

    // 指针拖动；捕获目标在指针离开矩形后仍会收到
    default boolean mouseDragged(UiInputContext context) {
        return false;
    }

    // 指针释放；拖动中的目标即使指针在矩形外也收到
    default boolean mouseReleased(UiInputContext context) {
        return false;
    }

    // 滚轮；scrollY 为正表示向上滚
    default boolean mouseScrolled(UiInputContext context, double scrollY) {
        return false;
    }

    // 键按下；返回 true 表示消费，宿主不再做容器导航
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    // 键抬起；用于把按键重复合并成一次提交
    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    // 字符输入
    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
}
