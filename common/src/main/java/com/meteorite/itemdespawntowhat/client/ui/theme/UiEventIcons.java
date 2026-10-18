package com.meteorite.itemdespawntowhat.client.ui.theme;

import com.meteorite.itemdespawntowhat.client.edit.TypeLabels;
import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 事件图标：天气与天象类动作的成品贴图，一张单列图集按 {@link Event} 行序取用。
 *
 * <p>图集是彩色成品而非白色字形：日光、雨滴、雷光与箭矢自带固有配色，按主题色相乘会失真，
 * 因此这里不提供染色入口；界面只在禁用态统一压暗，与物品图标的口径一致。</p>
 *
 * <p>切片规格与 {@code tools/image_generator/generate-event-icons.py} 一致：创作画布 32×32、
 * 单列五行，界面槽位按 16 像素绘制，缩放由 blit 的目标尺寸完成。kit 只提供
 * {@link UiIcon} 与贴图注入点，贴图路径留在主题层。</p>
 */
public final class UiEventIcons {

    // 事件图集：与编辑器其它贴图同目录
    private static final ResourceLocation SHEET = ResourceLocation.fromNamespaceAndPath(
            TypeLabels.OWN_NAMESPACE, "textures/gui/editor/events.png");
    // 创作画布边长与行数：行数参与 UV 除数，必须与生成脚本一致
    private static final int CELL = 32;
    private static final int ROWS = 5;
    // 界面槽位尺寸：与物品图标一致，配方与结果行无需为事件单独排版
    private static final int ICON_SIZE = 16;
    // 图标不可变，按行序预先建好，避免每帧渲染新建绘制器
    private static final UiIcon[] ICONS = createIcons();

    /** 事件种类：行序与生成脚本 {@code EVENTS} 表逐条对应。 */
    public enum Event {
        // 晴天
        CLEAR(0),
        // 下雨
        RAIN(1),
        // 雷雨
        THUNDER(2),
        // 闪电
        LIGHTNING(3),
        // 箭雨
        ARROW_RAIN(4);

        private final int row;

        Event(int row) {
            this.row = row;
        }
    }

    // 工具类，禁止实例化
    private UiEventIcons() {
    }

    /**
     * 取一个事件图标。
     *
     * @param event 事件种类，决定图集行
     * @return 固定 16 像素见方的图标，调用方只负责摆位
     */
    public static UiIcon icon(Event event) {
        return ICONS[event.ordinal()];
    }

    private static UiIcon[] createIcons() {
        Event[] events = Event.values();
        UiIcon[] icons = new UiIcon[events.length];
        for (Event event : events) {
            icons[event.ordinal()] = new UiIcon.Rendered(ICON_SIZE, ICON_SIZE,
                    (graphics, box) -> draw(graphics, event, box.x(), box.y()));
        }
        return icons;
    }

    // 原色采样：批次内没有改过着色器颜色，因此不需要像标记字形那样先取再还原。
    // 图集格子是 32 像素、槽位是 16 像素，必须用带源尺寸的 blit 重载，
    // 否则前一个重载会把「取 16x16 源区」当成完整格子，只画出左上角四分之一。
    private static void draw(GuiGraphics graphics, Event event, int x, int y) {
        graphics.blit(SHEET, x, y, ICON_SIZE, ICON_SIZE, 0.0F, (float) event.row * CELL,
                CELL, CELL, CELL, ROWS * CELL);
    }
}
