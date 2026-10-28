package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInteractionSession;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusManager;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑器页面接口：三种页（管理页、编辑页、调试页）对屏幕的唯一派发面。
 *
 * <p>窗口几何、标题带、提示带、页脚带与内容区由屏幕统一排版，页只声明自己的差异度量
 * （头部高度、内边距）并按屏幕给出的区域绘制；页不互相引用，也不反向回调屏幕。
 * 页的构造期协作者与跨页命令由 {@link EditorShell} 提供。</p>
 */
interface EditorPage extends UiInputTarget {

    /**
     * 页面实例的稳定输入作用域。
     */
    UiInputScope inputScope();

    // ---- 排版度量（决策：页只声明差异，屏幕按度量统一排版）----

    /**
     * 标题带高度。
     */
    int headerHeight();

    /**
     * 内容区与页脚带的内边距。
     */
    int pagePadding();

    /**
     * 标题带文本；null 表示沿用屏幕标题。
     */
    default @Nullable String title() {
        return null;
    }

    /**
     * 标题带右侧的状态标签文本。
     */
    String stateLabel();

    /**
     * 状态标签是否带底色面板（目前只有编辑页需要）。
     */
    default boolean statePanel() {
        return false;
    }

    /**
     * 状态面板是否用警示底色（只读内容）。
     */
    default boolean statePanelHighlighted() {
        return false;
    }

    /**
     * 标题被截断时悬停显示的完整文本；null 表示不提供。
     */
    default @Nullable String titleDetail() {
        return null;
    }

    /**
     * 头部附加内容（编辑页的页签条）：返回内容区起始 y，默认不占高度。
     */
    default int layoutHeader(GuiGraphics graphics, Font font, int mouseX, int mouseY, int pagePad, int top) {
        return top;
    }

    // ---- 渲染与布局 ----

    void init();

    List<UiLayoutNode> layoutNodes();

    int footerHeight(int width);

    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, UiRect content);

    void renderFooter(GuiGraphics graphics, Font font, int mouseX, int mouseY, UiRect footer);

    void addFocusTargets(UiFocusManager focus);

    // ---- 输入派发 ----

    boolean mousePressed(UiInputContext context);

    boolean mouseReleased(UiInputContext context);

    boolean mouseDragged(UiInputContext context, double dragX, double dragY);

    default boolean mouseScrolled(UiInputContext context, double scrollX, double scrollY) {
        return false;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers);

    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    boolean charTyped(char codePoint, int modifiers);

    // ---- 状态同步（屏幕不推送使能，页自己查询外壳）----

    /**
     * 按当前状态刷新本页按钮的可用状态与标签。
     */
    void updateButtons();

    /**
     * 输入作用域切换：结束本页正在进行的交互与捕获。
     */
    @Override
    default void scopeExited(UiInteractionSession.EndReason reason) {
    }

    // ---- 可选入口 ----

    /**
     * 内容区之上的覆盖层（类型选择器等）；屏幕负责渲染并计入输入门槛。
     */
    default @Nullable UiTypePicker overlay() {
        return null;
    }

    /**
     * 绘制本页自己的悬停说明；返回 true 表示已绘制。
     */
    default boolean renderDescriptionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        return false;
    }

    /**
     * 单行悬停文本；屏幕在页未自行绘制说明时使用。
     */
    default @Nullable Component tooltipAt(double mouseX, double mouseY) {
        return null;
    }

    /**
     * 每帧推进；页内轮询留在这里。
     */
    default void tick() {
    }
}
