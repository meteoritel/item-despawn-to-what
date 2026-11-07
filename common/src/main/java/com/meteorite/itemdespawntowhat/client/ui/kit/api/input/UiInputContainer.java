package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;

import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * 普通容器的成员与输入接入；特殊裁剪、命令和视口回退由容器覆写。
 * 持续拖动与匹配释放始终由作用域直接送给会话目标。
 */
public interface UiInputContainer extends UiInputTarget {
    /** 实际成员按绘制顺序存放，容器稳定持有同一集合。 */
    UiChildren<? extends UiInputTarget> inputChildren();

    /** 保留实际叶子与容器身份。 */
    @Override default @Nullable UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = inputChildren().hitTest(x, y);
        return hit == null ? null : hit.within(this);
    }

    /** 焦点贡献从实际成员派生。 */
    @Override default void addFocusTargets(Consumer<UiFocusTarget> out) { inputChildren().addFocusTargets(out); }
    /** 子树结构变化传播到作用域。 */
    @Override default long focusRevision() { return inputChildren().focusRevision(); }
    /** 归属沿实际成员查询，不构造临时焦点列表。 */
    @Override default boolean hasFocus() { return inputChildren().hasFocus(); }
    /** 本地修饰态在作用域结束会话后清理。 */
    @Override default void scopeExited(UiInteractionSession.EndReason reason) { inputChildren().scopeExited(reason); }
    /** 只交给命中子树。 */
    @Override default boolean mousePressed(UiInputContext context) { return inputChildren().mousePressed(context); }
    /** 无会话配对释放沿原命中子树派发。 */
    @Override default boolean mouseReleased(UiInputContext context) { return inputChildren().mouseReleased(context); }
    /** 子树未消费时允许外部视口回退。 */
    @Override default boolean mouseScrolled(UiInputContext context, double x, double y) { return inputChildren().mouseScrolled(context, x, y); }
    /** 普通按键沿持焦子树派发。 */
    @Override default boolean keyPressed(int key, int scan, int modifiers) { return inputChildren().keyPressed(key, scan, modifiers); }
    /** 修饰态释放沿持焦子树派发。 */
    @Override default boolean keyReleased(int key, int scan, int modifiers) { return inputChildren().keyReleased(key, scan, modifiers); }
    /** 字符沿持焦子树派发。 */
    @Override default boolean charTyped(char value, int modifiers) { return inputChildren().charTyped(value, modifiers); }
}
