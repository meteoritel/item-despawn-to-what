package com.meteorite.itemdespawntowhat.client.ui.kit.api.control;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 覆盖面的共同外壳：拥有阻断作用域、背景命中和局部遮罩/内容层。
 * 宿主选择活动覆盖面及深度，内容仍解释自己的布局、导航和关闭业务。
 */
public final class UiOverlay implements UiInputTarget {
    private final UiInputTarget content;
    private final UiInputScope scope = new UiInputScope(this);

    /** 包装稳定内容；不另持有其编辑状态或指针捕获。 */
    public UiOverlay(UiInputTarget content) { this.content = content; }
    /** 供根选择的唯一覆盖作用域。 */
    public UiInputScope inputScope() { return scope; }
    /** 相对宿主当前绘制域安排遮罩及内容；透明遮罩仍保留输入阻断。 */
    public void render(GuiGraphics graphics, UiRect bounds, int depth, int mask, Runnable painter) {
        UiRenderLayers.mask(graphics, bounds, depth, mask);
        UiRenderLayers.draw(graphics, UiRenderLayers.modalContent(depth), painter);
    }
    /** 覆盖面的内部空白保持焦点并阻断底层。 */
    @Override public UiPointerHit hitTest(double x, double y) {
        UiPointerHit hit = content.hitTest(x, y);
        return hit == null ? UiPointerHit.leaf(this, x, y).preservingFocus() : hit.within(this);
    }
    /** 焦点完全来自实际内容。 */
    @Override public void addFocusTargets(Consumer<UiFocusTarget> out) { content.addFocusTargets(out); }
    /** 子内容结构变化传播到本域。 */
    @Override public long focusRevision() { return content.focusRevision(); }
    /** 未消费事件也由覆盖域阻断。 */
    @Override public boolean blocksInput() { return true; }
    /** 已命中内容只派发一次，覆盖空白由壳消费。 */
    @Override public boolean mousePressed(UiInputContext context) { content.mousePressed(context); return true; }
    /** 配对释放只送本层内容，持续交互由路由器直接派发。 */
    @Override public boolean mouseReleased(UiInputContext context) { content.mouseReleased(context); return true; }
    /** 滚轮不会穿透本层。 */
    @Override public boolean mouseScrolled(UiInputContext context, double x, double y) { content.mouseScrolled(context, x, y); return true; }
    /** 保留内容快捷键与路由器的焦点激活顺序。 */
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return content.keyPressed(key, scan, modifiers); }
    /** 修饰态结束仍归内容所有。 */
    @Override public boolean keyReleased(int key, int scan, int modifiers) { return content.keyReleased(key, scan, modifiers); }
    /** 字符沿内容的焦点路径发送。 */
    @Override public boolean charTyped(char value, int modifiers) { return content.charTyped(value, modifiers); }
    /** 切域后只结束本层生命周期。 */
    @Override public void scopeExited(UiInteractionSession.EndReason reason) { content.scopeExited(reason); }
}
