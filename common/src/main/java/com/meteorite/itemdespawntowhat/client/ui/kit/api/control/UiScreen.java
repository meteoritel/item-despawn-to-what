package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputRouter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * 原版 Screen 的 kit 接入：统一事件桥接、作用域刷新和卸载；内容与模态优先级由根选择。
 * resize 沿用原版 init，控件由子类稳定持有；removed 结束捕获并释放根拥有的资源，再次打开可重新使用。
 */
public abstract class UiScreen extends Screen {
    private final UiInputRouter input = new UiInputRouter();
    private Language resources;

    /** 创建原版屏幕，标题由宿主本地化。 */
    protected UiScreen(Component title) { super(title); }
    /** 选出当前页或最上层模态的作用域。 */
    protected abstract UiInputScope inputScope();
    /** 业务路由可共享本根的唯一输入路由器。 */
    protected final UiInputRouter inputRouter() { return input; }
    /** 渲染前同步资源世代与焦点；不会重建内容。 */
    protected final void prepareUiFrame() {
        Language current = Language.getInstance();
        if (resources != current) {
            invalidateUiResources();
            resources = current;
        }
        selectScope();
    }
    /** 原版资源重载后使根拥有的资源和文本缓存失效。 */
    protected void invalidateUiResources() { }
    /** removed 时释放根拥有的资源；可重复调用。 */
    protected void releaseUiResources() { }
    /** 派发后允许宿主更新业务作用域，并决定模态空白是否消费。 */
    protected boolean inputFinished(boolean consumed, UiInputScope scope) { return consumed; }
    private UiInputScope selectScope() {
        UiInputScope scope = inputScope();
        scope.refreshFocus();
        input.select(scope);
        return scope;
    }
    private UiInputContext pointer(double x, double y, int button) {
        return UiInputContext.of(selectScope(), x, y, button, hasShiftDown(), hasControlDown(), hasAltDown());
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        UiInputContext context = pointer(x, y, button);
        return inputFinished(input.mousePressed(context), context.scope());
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        UiInputContext context = pointer(x, y, button);
        return inputFinished(input.mouseReleased(context), context.scope());
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        UiInputContext context = pointer(x, y, button);
        return inputFinished(input.mouseDragged(context, dx, dy), context.scope());
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        UiInputContext context = pointer(x, y, -1);
        return inputFinished(input.mouseScrolled(context, sx, sy), context.scope());
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        UiInputScope scope = selectScope();
        return inputFinished(input.keyPressed(key, scan, modifiers), scope);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        UiInputScope scope = selectScope();
        return inputFinished(input.keyReleased(key, scan, modifiers), scope);
    }
    @Override public boolean charTyped(char value, int modifiers) {
        UiInputScope scope = selectScope();
        return inputFinished(input.charTyped(value, modifiers), scope);
    }
    @Override public void removed() {
        input.close();
        releaseUiResources();
        resources = null;
        super.removed();
    }
}
