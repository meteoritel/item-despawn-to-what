package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 九宫格贴图：四角保持原生尺寸，边和中心各一次拉伸绘制，总计九个四边形。kit 公开绘制入口之一。
 *
 * <p>贴图可以是一整张九宫格，也可以是一张图集里的子区域：{@code originU/originV} 指向子区域左上角，
 * {@code sheetWidth/sheetHeight} 是整张图集的真实尺寸（1.21.1 的 blit 用它换算 UV）。
 * 子区域版本让多张九宫格共用一张 PNG，避免为每个切片单独出文件。</p>
 *
 * @param texture     贴图或图集路径
 * @param originU     子区域左上角在图集中的横坐标；整张图使用时为 0
 * @param originV     子区域左上角在图集中的纵坐标；整张图使用时为 0
 * @param size        九宫格边长；子区域边长必须不超过图集剩余范围
 * @param corner      四角边长，必须大于 0 且小于 {@code size} 的一半
 * @param sheetWidth  贴图真实宽度，参与 UV 换算
 * @param sheetHeight 贴图真实高度，参与 UV 换算
 */
public record UiNineSlice(ResourceLocation texture, int originU, int originV, int size, int corner,
                          int sheetWidth, int sheetHeight) {

    public UiNineSlice {
        Objects.requireNonNull(texture, "Texture");
        if (corner <= 0 || size <= corner * 2) throw new IllegalArgumentException("Invalid nine slice");
        if (originU < 0 || originV < 0
                || originU + size > sheetWidth || originV + size > sheetHeight) {
            throw new IllegalArgumentException("Invalid nine slice region");
        }
    }

    /** 整张贴图就是一个九宫格：子区域从 (0,0) 开始，图集宽高等于 {@code size}。 */
    public UiNineSlice(ResourceLocation texture, int size, int corner) {
        this(texture, 0, 0, size, corner, size, size);
    }

    public void render(GuiGraphics graphics, UiRect rect) {
        int edgeX = Math.min(corner, rect.width() / 2);
        int edgeY = Math.min(corner, rect.height() / 2);
        graphics.flush();
        for (int row = 0; row < 3; row++) {
            int y = row == 0 ? rect.y() : row == 1 ? rect.y() + edgeY : rect.bottom() - edgeY;
            int height = row == 1 ? rect.height() - edgeY * 2 : edgeY;
            int localV = row == 0 ? 0 : row == 1 ? corner : size - corner;
            int sourceHeight = row == 1 ? size - corner * 2 : corner;
            for (int col = 0; col < 3; col++) {
                int x = col == 0 ? rect.x() : col == 1 ? rect.x() + edgeX : rect.right() - edgeX;
                int width = col == 1 ? rect.width() - edgeX * 2 : edgeX;
                int localU = col == 0 ? 0 : col == 1 ? corner : size - corner;
                int sourceWidth = col == 1 ? size - corner * 2 : corner;
                if (width > 0 && height > 0) {
                    graphics.blit(texture, x, y, width, height,
                            (float) (originU + localU), (float) (originV + localV),
                            sourceWidth, sourceHeight, sheetWidth, sheetHeight);
                }
            }
        }
    }
}
