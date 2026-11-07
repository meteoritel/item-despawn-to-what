package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContainer;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiPointer;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiTypePicker;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * 编辑器页面接口：三种页（管理页、编辑页、调试页）对屏幕的唯一派发面。
 *
 * <p>窗口几何、顶部栏、提示带、页脚带与内容区由屏幕统一排版：顶部栏内标题、状态标签与换肤入口同处一栏，
 * 页只声明自己的差异度量（标题带最小高度、内边距）并按屏幕给出的区域绘制；页不互相引用，也不反向回调屏幕。
 * 页的构造期协作者与跨页命令由 {@link EditorShell} 提供。</p>
 */
interface EditorPage extends UiInputContainer {

    /**
     * 页面实例的稳定输入作用域。
     */
    UiInputScope inputScope();

    // ---- 排版度量（决策：页只声明差异，屏幕按度量统一排版）----

    /**
     * 顶部栏最小高度：屏幕按状态标签与换肤控件高度取较大值，三页共用同一栏高。
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
    default int layoutHeader(int pagePad, int top) {
        return top;
    }

    /** 布局完成后绘制头部，指针已经由页面统一解析。 */
    default void renderHeader(GuiGraphics graphics, Font font, UiPointer pointer) {
    }

    /** 同步内容与页脚几何；屏幕随后解析一次指针供各绘制分支共用。 */
    void layout(UiRect content, UiRect footer);

    // ---- 渲染与布局 ----

    void init();

    List<UiLayoutNode> layoutNodes();

    int footerHeight(int width);

    void render(GuiGraphics graphics, Font font, UiPointer pointer, UiRect content);

    void renderFooter(GuiGraphics graphics, Font font, UiPointer pointer, UiRect footer);

    // ---- 状态同步（屏幕不推送使能，页自己查询外壳）----

    /**
     * 布局和输入前同步当前页面的动作状态；后台页在激活时准备。
     */
    void prepare();

    // ---- 可选入口 ----

    /**
     * 内容区之上的覆盖层（类型选择器等）；屏幕负责渲染并计入输入门槛。
     */
    default @Nullable UiTypePicker overlay() {
        return null;
    }

    /**
     * 每帧推进；页内轮询留在这里。
     */
    default void tick() {
    }
}
