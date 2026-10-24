package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

/**
 * 画布点阵网格：在给定屏幕矩形内按缩放档位换算步长，绘制 1 像素方格。
 *
 * <p>网格锚定在矩形左上角，因此适合作为固定背景（不随内容平移蠕动）；
 * 步长等于 {@code baseStep * scale} 取整后至少 1 像素。</p>
 *
 * <p>点阵不逐点填充：把一个步长周期画进按步长缓存的动态纹理，再用少量 1:1 blit 平铺整块矩形。
 * 纹理边长取步长的整数倍，平铺相位与矩形左上角天然对齐，任意矩形尺寸下的像素位置都与逐点绘制一致；
 * 纹理只取决于步长与颜色，平移或改变矩形大小不会重建，只有缩放档位或配色变化时重建一次。
 * 纹理背景透明，混合状态由调用方负责：本类只提交 blit，不读也不改渲染状态；调用方须在绘制前提交批次并开启混合，
 * 否则透明间隙会按混合关闭的结果写成黑色。</p>
 */
public final class UiDotGrid {
    /** 单次绘制的点数上限：异常大的矩形直接放弃绘制，避免平铺次数把一帧拖死。 */
    private static final long MAX_DOTS = 4_000_000L;
    /** 单块点阵纹理的最大边长：纹理只含一个周期内的重复点，边长取步长整数倍并封顶，避免超大纹理。 */
    private static final int MAX_TILE = 512;

    // 单条目纹理缓存：步长与颜色决定纹理内容，管理器实例与注册项用来确认缓存仍然有效
    private static @Nullable DynamicTexture tileTexture;
    private static @Nullable ResourceLocation tileId;
    private static @Nullable TextureManager tileManager;
    private static int tileStep = -1;
    private static int tileColor;
    private static int tileWidth;
    private static int tileHeight;

    private UiDotGrid() {
    }

    /**
     * 在给定矩形内绘制点阵网格；矩形无效、缩放非法、点数超过上限或纹理创建失败时放弃绘制。
     *
     * @param scale 缩放档位
     * @param x0 矩形一端 X 坐标
     * @param y0 矩形一端 Y 坐标
     * @param x1 矩形另一端 X 坐标
     * @param y1 矩形另一端 Y 坐标
     * @param baseStep 未缩放的基准步长（像素）
     * @param color 方格颜色
     */
    public static void draw(GuiGraphics graphics, double scale, int x0, int y0, int x1, int y1,
                            int baseStep, int color) {
        int left = Math.min(x0, x1);
        int top = Math.min(y0, y1);
        int right = Math.max(x0, x1);
        int bottom = Math.max(y0, y1);
        if (right <= left || bottom <= top || baseStep <= 0) return;
        if (!Double.isFinite(scale) || scale <= 0) return;
        int step = Math.max(1, (int) Math.round(baseStep * scale));
        long columns = (right - left + (long) step - 1L) / step;
        long rows = (bottom - top + (long) step - 1L) / step;
        if (columns * rows > MAX_DOTS) return;
        ResourceLocation texture = tileFor(step, color);
        if (texture == null) return;
        int width = tileWidth;
        int height = tileHeight;
        // 边缘瓦片按剩余尺寸截断，右/下边缘不足一个周期时少铺几像素，不动纹理
        for (int tileY = top; tileY < bottom; tileY += height) {
            int destHeight = Math.min(height, bottom - tileY);
            for (int tileX = left; tileX < right; tileX += width) {
                int destWidth = Math.min(width, right - tileX);
                graphics.blit(texture, tileX, tileY, destWidth, destHeight, 0.0F, 0.0F,
                        destWidth, destHeight, width, height);
            }
        }
    }

    // 取步长 + 颜色对应的点阵纹理；缓存命中直接复用，否则释放旧纹理后重建
    private static @Nullable ResourceLocation tileFor(int step, int color) {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        // 注册项只由本类注销，纹理管理器实例在客户端生命周期内唯一，因此管理器 + 步长 + 颜色一致即为命中
        if (tileTexture != null && step == tileStep && color == tileColor && manager == tileManager) {
            return tileId;
        }
        releaseTile();
        // 边长取步长整数倍：平铺后每个周期的相位都与矩形左上角对齐，像素位置与逐点绘制一致
        int side = step * Math.max(1, MAX_TILE / step);
        // 该构造参数是像素缓冲的零初始化开关：true 才能保证非点像素透明，false 会留下未初始化内存
        DynamicTexture texture = new DynamicTexture(side, side, true);
        var pixels = texture.getPixels();
        if (pixels == null) {
            // 纹理不持有像素时无法写入点阵，释放后放弃本次绘制
            texture.close();
            return null;
        }
        // 像素按小端 RGBA 存放，需把 ARGB 颜色转成 ABGR 布局
        int pixel = FastColor.ABGR32.fromArgb32(color);
        for (int y = 0; y < side; y += step) {
            for (int x = 0; x < side; x += step) {
                pixels.setPixelRGBA(x, y, pixel);
            }
        }
        // 该构造器只准备纹理不上传，写完像素后必须显式上传
        texture.upload();
        // 点阵按 1:1 显示：最近邻采样、无 mipmap，避免采样把 1 像素点糊开
        texture.setFilter(false, false);
        tileTexture = texture;
        tileManager = manager;
        tileStep = step;
        tileColor = color;
        tileWidth = side;
        tileHeight = side;
        // 名称由管理器分配（minecraft:dynamic/...），重建时按返回的 id 注销旧纹理
        tileId = manager.register("itemdespawntowhat_ui_dot_grid", texture);
        return tileId;
    }

    // 注销并释放旧纹理；管理器关闭时其纹理随 close 一并关闭，这里只丢弃引用
    private static void releaseTile() {
        if (tileTexture == null) return;
        if (tileManager != null && tileId != null) {
            tileManager.release(tileId);
        }
        tileTexture = null;
        tileId = null;
        tileManager = null;
        tileStep = -1;
        tileWidth = 0;
        tileHeight = 0;
    }
}
