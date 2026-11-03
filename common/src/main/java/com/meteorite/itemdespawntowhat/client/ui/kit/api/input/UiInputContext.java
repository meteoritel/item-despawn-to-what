package com.meteorite.itemdespawntowhat.client.ui.kit.api.input;

import org.lwjgl.glfw.GLFW;

/**
 * 一次输入事件的显式上下文快照：指针坐标、按键编号与 Shift/Ctrl/Alt 修饰键状态。
 *
 * <p>kit 控件不在内部查询全局 {@code Screen} 或 {@code Minecraft} 实例，宿主必须在事件入口
 * 构造本对象再交给控件。这样修饰键与指针位置
 * 走同一条通路，拖动、细调与捕获判断都不依赖静态状态。</p>
 *
 * <p>输入事件必须携带活动作用域；纯绘制与程序化数值计算使用的上下文可不带作用域。
 * 指针坐标为 GUI 逻辑坐标；{@link #button()} 为 GLFW 鼠标键编号，键盘或程序化调用可传 {@code -1}。</p>
 */
public record UiInputContext(UiInputScope scope, double pointerX, double pointerY, int button,
                             boolean shift, boolean ctrl, boolean alt, UiPointerHit hit) {

    /** 创建尚未解析命中的事件；路由入口负责解析一次。 */
    public UiInputContext(UiInputScope scope, double pointerX, double pointerY, int button,
                          boolean shift, boolean ctrl, boolean alt) {
        this(scope, pointerX, pointerY, button, shift, ctrl, alt, null);
    }

    /** 保留事件参数，附上当前布局解析出的唯一命中。 */
    public UiInputContext withHit(UiPointerHit hit) {
        return new UiInputContext(scope, pointerX, pointerY, button, shift, ctrl, alt, hit);
    }

    /** 已有快照时按归属判断；直接调用控件时允许在入口解析。 */
    public boolean hits(UiInputTarget target) {
        return hit == null ? target.hitTest(pointerX, pointerY) != null : hit.owns(target);
    }

    /**
     * 空上下文：键盘事件或程序化调用没有指针信息时使用。
     */
    public static final UiInputContext EMPTY = new UiInputContext(null, 0.0D, 0.0D, -1, false, false, false);

    /**
     * 由 GLFW 修饰键位掩码构造；宿主从原版事件拿到的 modifiers 可直接传入
     */
    public static UiInputContext pointer(UiInputScope scope, double pointerX, double pointerY, int button, int modifiers) {
        return new UiInputContext(scope, pointerX, pointerY, button,
                (modifiers & GLFW.GLFW_MOD_SHIFT) != 0,
                (modifiers & GLFW.GLFW_MOD_CONTROL) != 0,
                (modifiers & GLFW.GLFW_MOD_ALT) != 0);
    }

    /**
     * 只有修饰键、没有指针的上下文
     */
    public static UiInputContext keys(int modifiers) {
        return pointer(null, 0.0D, 0.0D, -1, modifiers);
    }

    /**
     * 完整显式构造，便于需要单独指定三个修饰键的宿主
     */
    public static UiInputContext of(UiInputScope scope, double pointerX, double pointerY, int button,
                                    boolean shift, boolean ctrl, boolean alt) {
        return new UiInputContext(scope, pointerX, pointerY, button, shift, ctrl, alt);
    }

    /**
     * 保留按键与修饰键，替换指针坐标
     */
    public UiInputContext withPointer(double pointerX, double pointerY) {
        return new UiInputContext(scope, pointerX, pointerY, button, shift, ctrl, alt, hit);
    }

    /**
     * 还原为 GLFW 修饰键位掩码，便于继续交给原版控件
     */
    public int modifiers() {
        int modifiers = 0;
        if (shift) modifiers |= GLFW.GLFW_MOD_SHIFT;
        if (ctrl) modifiers |= GLFW.GLFW_MOD_CONTROL;
        if (alt) modifiers |= GLFW.GLFW_MOD_ALT;
        return modifiers;
    }

    public boolean shiftDown() {
        return shift;
    }

    public boolean ctrlDown() {
        return ctrl;
    }

    public boolean altDown() {
        return alt;
    }
}
