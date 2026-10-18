package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiControlStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderRole;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiSliderSkin;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiStyleSource;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 宿主主题：控件与屏幕消费的全部视觉规格都经本接口注入，控件不再静态读取主题类。
 *
 * <p>本接口是 kit 的 {@link UiStyleSource} 在宿主侧的宿主入口：kit 只约定控件样式与滑块外观的类型，
 * 视觉规范（调色板、度量、结构绘制原语）全部由宿主实现。屏幕在装配控件时从 {@link UiHostThemes}
 * 取主题并传入控件，因此换主题或测试替换主题都不依赖全局静态状态。</p>
 *
 * <p>度量与绘制原语保持与既有像素主题逐一相同：像素皮肤是默认实现，画布页的扁平外壳仍由
 * {@code UiFlatSkin} 在调用方显式注入，本接口不改变既有画面。</p>
 */
public interface UiHostTheme extends UiStyleSource {

    // ---- 图标图集行号 ----
    // 与 UiFlatSkin 的图集行序一致；图标图集是宿主共用资源，两种皮肤同源

    // 页面层经主题消费；本包内暂无调用点
    int ICON_UNDO = UiFlatSkin.ICON_UNDO;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_REDO = UiFlatSkin.ICON_REDO;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_VIEW_TREE = UiFlatSkin.ICON_VIEW_TREE;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_VIEW_JSON = UiFlatSkin.ICON_VIEW_JSON;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_HELP = UiFlatSkin.ICON_HELP;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_ADD_CONDITION = UiFlatSkin.ICON_ADD_CONDITION;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_NEW_BRANCH = UiFlatSkin.ICON_NEW_BRANCH;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_INVERT = UiFlatSkin.ICON_INVERT;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_REMOVE = UiFlatSkin.ICON_REMOVE;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_MOVE_UP = UiFlatSkin.ICON_MOVE_UP;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_MOVE_DOWN = UiFlatSkin.ICON_MOVE_DOWN;
    // 页面层经主题消费；本包内暂无调用点
    int ICON_WRAP_ALL = UiFlatSkin.ICON_WRAP_ALL;
    // 页面层经主题消费；本包内暂无调用点
    // 条件树「任一满足」字形：画布页暂未接入，保留常量供后续宿主使用
    @SuppressWarnings("unused")
    int ICON_GROUP_ANY = UiFlatSkin.ICON_GROUP_ANY;
    // 页面层经主题消费；本包内暂无调用点
    // 条件树「任一包成组」字形：与 ICON_GROUP_ANY 同批保留
    @SuppressWarnings("unused")
    int ICON_WRAP_ANY = UiFlatSkin.ICON_WRAP_ANY;

    // 控件可见的调色板
    UiHostPalette palette();

    // ---- 度量 ----

    // 面板内边距
    int padding();

    // 标准行高
    int rowHeight();

    // 文本相对行顶部的偏移
    int textOffset();

    // 分区标题栏高度
    int headerHeight();

    // 边框宽度
    int border();

    // 列表选中行标记宽度
    int selectMarkerWidth();

    // ---- 文本色 ----

    // 按启用状态返回标准文本色
    int textColor(boolean enabled);

    // 次要说明文本色
    int secondaryTextColor();

    // 控件语义角色对应的文本色
    int controlTextColor(UiControlRole role);

    // ---- 控件样式 ----

    // 控件结构样式
    @Override
    UiControlStyle controlStyle(UiControlRole role);

    // 滑块外观
    @Override
    UiSliderSkin sliderSkin(UiSliderRole role);

    // 浮动调板外壳样式（画布页的面板与条带属于宿主外壳规格，kit 的控件角色不表达它）
    // 页面层经主题消费；本包内暂无调用点
    // 浮动调板外壳样式：当前由 widget/screen 侧持有，宿主入口先保留
    @SuppressWarnings("unused")
    UiControlStyle panelStyle();

    // 功能栏与工具列格子样式（与浮动调板同属宿主外壳）
    // 页面层经主题消费；本包内暂无调用点
    UiControlStyle stripStyle();

    // ---- 结构绘制 ----

    // 带高光与阴影的窗口面板
    void drawWindow(GuiGraphics graphics, UiRect rect);

    // 内凹区域（输入框、列表底、槽位）
    void drawInset(GuiGraphics graphics, UiRect rect);

    // 分区标题栏
    void drawHeader(GuiGraphics graphics, UiRect rect);

    // 水平分隔线
    void drawDivider(GuiGraphics graphics, int x, int y, int width);

    // 外凸立体边：上/左高光、下/右阴影
    void drawBevel(GuiGraphics graphics, UiRect rect, int light, int dark);

    // 键盘焦点轮廓
    void drawFocusOutline(GuiGraphics graphics, UiRect rect);

    // 选中背景
    void drawSelection(GuiGraphics graphics, UiRect rect);

    // 列表/树选中行左侧的三角标记
    void drawSelectMarker(GuiGraphics graphics, UiRect rect);

    // 水平进度/滚动轨道
    void drawScrollTrack(GuiGraphics graphics, UiRect rect);

    // 分段按钮底色
    void drawSegment(GuiGraphics graphics, UiRect rect, boolean enabled, boolean selected, boolean hovered);

    // 圆角卡片（像素皮肤为九宫格贴图，扁平皮肤为同色描边面）
    // 页面层经主题消费；本包内暂无调用点
    void drawCard(GuiGraphics graphics, UiRect rect);

    // 分区面板
    // 页面层经主题消费；本包内暂无调用点
    void drawPanel(GuiGraphics graphics, UiRect rect);

    // 可选中小底（选中 / 悬停 / 常态）
    // 页面层经主题消费；本包内暂无调用点
    void drawChip(GuiGraphics graphics, UiRect rect, boolean selected, boolean hovered);

    // ---- 宿主外壳（画布页使用扁平实现；其它页面不装配外壳） ----

    // 取图标图集中的一个图标；disabled 为真时取禁用字形
    UiIcon icon(int row, boolean disabled);

    // 功能栏 / 工具列底板
    void drawChrome(GuiGraphics graphics, UiRect rect);

    // 底部状态栏底板
    void drawStatusBar(GuiGraphics graphics, UiRect rect);

    // 帮助浮层底板
    // 页面层经主题消费；本包内暂无调用点
    void drawHelp(GuiGraphics graphics, UiRect rect);

    // 纯色底 + 1 像素描边（宿主外壳通用原语）
    void drawSurface(GuiGraphics graphics, UiRect rect, int fill, int border);
}
