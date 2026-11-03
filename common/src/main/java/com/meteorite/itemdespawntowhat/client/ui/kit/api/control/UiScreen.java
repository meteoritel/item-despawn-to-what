package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputRouter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;
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
    /** 内容布局钩子；稳定控件只更新几何，事件与绘制读取同一布局。 */
    protected void layoutUi() { }
    /** 在准备完布局、资源和焦点后绘制内容。 */
    protected abstract void renderUi(GuiGraphics graphics, int x, int y, float delta);
    /** 原版绘制入口统一准备，子类无需记忆调用顺序。 */
    @Override public final void render(@NotNull GuiGraphics graphics, int x, int y, float delta) {
        Language current = Language.getInstance();
        if (resources != current) {
            UiRenderLayers.releaseModels();
            invalidateUiResources();
            resources = current;
        }
        layoutUi();
        refreshUiState();
        renderUi(graphics, x, y, delta);
    }
    /** 结构或覆盖面回调后立即应用宿主的选择，旧事件由路由版本失效。 */
    protected final void refreshUiState() { selectScope(); }
    /** 原版资源重载后使根拥有的资源和文本缓存失效。 */
    protected void invalidateUiResources() { }
    /** removed 时释放根拥有的资源；可重复调用。 */
    protected void releaseUiResources() { }
    private UiInputScope selectScope() {
        UiInputScope scope = inputScope();
        scope.refreshFocus();
        input.select(scope);
        return scope;
    }
    private boolean finish(boolean consumed) {
        refreshUiState();
        return consumed;
    }
    private UiInputContext pointer(double x, double y, int button) {
        layoutUi();
        return UiInputContext.of(selectScope(), x, y, button, hasShiftDown(), hasControlDown(), hasAltDown());
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        UiInputContext context = pointer(x, y, button);
        return finish(input.mousePressed(context));
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        UiInputContext context = pointer(x, y, button);
        return finish(input.mouseReleased(context));
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        UiInputContext context = pointer(x, y, button);
        return finish(input.mouseDragged(context, dx, dy));
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        UiInputContext context = pointer(x, y, -1);
        return finish(input.mouseScrolled(context, sx, sy));
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        selectScope();
        return finish(input.keyPressed(key, scan, modifiers));
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        selectScope();
        return finish(input.keyReleased(key, scan, modifiers));
    }
    @Override public boolean charTyped(char value, int modifiers) {
        selectScope();
        return finish(input.charTyped(value, modifiers));
    }
    @Override public void removed() {
        input.close();
        try { releaseUiResources(); }
        finally {
            UiRenderLayers.releaseModels();
            resources = null;
            super.removed();
        }
    }
}
