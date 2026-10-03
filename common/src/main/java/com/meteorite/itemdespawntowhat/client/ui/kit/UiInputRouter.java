package com.meteorite.itemdespawntowhat.client.ui.kit;

import org.jetbrains.annotations.Nullable;

/**
 * 输入路由顺序：作用域捕获目标 → 聚焦控件 → 容器导航，事件只消费一次。
 *
 * <p>宿主先自行解析顶层 modal 作用域（kit 不持有模态栈），把该作用域内指针命中的控件作为
 * {@code hitTarget} 传入。指针按下/拖动/滚轮先问捕获目标，再问命中控件；释放只交给捕获目标，
 * 保证一次拖动只提交一次、且指针离开矩形后仍能收到释放。键盘先给聚焦控件，被消费后不再做
 * 容器导航（PageUp/PageDown 等容器键必须在控件之后询问）。</p>
 *
 * <p>该类的存在是为了让「聚焦控件优先」成为可复用的显式顺序；宿主表单/屏幕若自行提前拦截
 * 容器键，需要改为通过本类路由。</p>
 */
public final class UiInputRouter {

    private UiInputRouter() {
    }

    /** 指针事件阶段。 */
    public enum PointerPhase {
        PRESSED,
        DRAGGED,
        RELEASED,
        SCROLLED
    }

    // 指针按下：捕获目标优先，其次命中控件
    public static boolean pressed(@Nullable UiInputTarget captureTarget, @Nullable UiInputTarget hitTarget,
                                  UiInputContext context) {
        return routePointer(captureTarget, hitTarget, context, PointerPhase.PRESSED, 0.0D);
    }

    // 指针拖动：捕获目标优先，保证拖出矩形仍继续
    public static boolean dragged(@Nullable UiInputTarget captureTarget, @Nullable UiInputTarget hitTarget,
                                  UiInputContext context) {
        return routePointer(captureTarget, hitTarget, context, PointerPhase.DRAGGED, 0.0D);
    }

    // 指针释放：只交给捕获目标；没有捕获时按普通命中派发
    public static boolean released(@Nullable UiInputTarget captureTarget, @Nullable UiInputTarget hitTarget,
                                   UiInputContext context) {
        return routePointer(captureTarget, hitTarget, context, PointerPhase.RELEASED, 0.0D);
    }

    // 滚轮：捕获目标优先，其次命中控件
    public static boolean scrolled(@Nullable UiInputTarget captureTarget, @Nullable UiInputTarget hitTarget,
                                   UiInputContext context, double scrollY) {
        return routePointer(captureTarget, hitTarget, context, PointerPhase.SCROLLED, scrollY);
    }

    // 键按下：聚焦控件优先，未消费才交给容器导航
    public static boolean keyPressed(@Nullable UiInputTarget focused, @Nullable UiInputTarget container,
                                     int keyCode, int scanCode, int modifiers) {
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        return container != null && container != focused && container.keyPressed(keyCode, scanCode, modifiers);
    }

    // 键抬起：与 keyPressed 同一顺序，用于把按键重复合并成一次提交
    public static boolean keyReleased(@Nullable UiInputTarget focused, @Nullable UiInputTarget container,
                                      int keyCode, int scanCode, int modifiers) {
        if (focused != null && focused.keyReleased(keyCode, scanCode, modifiers)) return true;
        return container != null && container != focused && container.keyReleased(keyCode, scanCode, modifiers);
    }

    // 字符输入：聚焦控件优先
    public static boolean charTyped(@Nullable UiInputTarget focused, @Nullable UiInputTarget container,
                                    char codePoint, int modifiers) {
        if (focused != null && focused.charTyped(codePoint, modifiers)) return true;
        return container != null && container != focused && container.charTyped(codePoint, modifiers);
    }

    private static boolean routePointer(@Nullable UiInputTarget captureTarget, @Nullable UiInputTarget hitTarget,
                                        UiInputContext context, PointerPhase phase, double scrollY) {
        if (captureTarget != null) {
            if (phase == PointerPhase.RELEASED) return dispatch(captureTarget, context, phase, scrollY);
            if (dispatch(captureTarget, context, phase, scrollY)) return true;
        }
        if (hitTarget == null || hitTarget == captureTarget) return false;
        return dispatch(hitTarget, context, phase, scrollY);
    }

    private static boolean dispatch(UiInputTarget target, UiInputContext context, PointerPhase phase, double scrollY) {
        return switch (phase) {
            case PRESSED -> target.mousePressed(context);
            case DRAGGED -> target.mouseDragged(context);
            case RELEASED -> target.mouseReleased(context);
            case SCROLLED -> target.mouseScrolled(context, scrollY);
        };
    }
}
