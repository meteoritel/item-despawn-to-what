package com.meteorite.itemdespawntowhat.client.ui.shared;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.world.level.block.Blocks;

/** 预览资源生命周期的宿主所有者；各类型缓存和目录名称索引平级消费同一世代。 */
public final class PreviewResources {
    private static Object world;
    private static Object language;
    private static Object models;
    private static Object epoch = new Object();
    private static long probedTick = Long.MIN_VALUE;
    private PreviewResources() {}

    /** 世界和语言当帧失效；模型身份探测在客户端游戏 tick 内合并。 */
    public static Object generation() {
        Minecraft minecraft = Minecraft.getInstance();
        Object nextWorld = minecraft.level;
        Object nextLanguage = Language.getInstance();
        if (world != nextWorld || language != nextLanguage) {
            world = nextWorld;
            language = nextLanguage;
            models = null;
            probedTick = Long.MIN_VALUE;
            epoch = new Object();
        }
        long tick = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
        if (tick != Long.MIN_VALUE && tick != probedTick) {
            Object nextModels = minecraft.getBlockRenderer().getBlockModelShaper().getBlockModel(Blocks.STONE.defaultBlockState());
            probedTick = tick;
            if (models != nextModels) { models = nextModels; epoch = new Object(); }
        }
        return epoch;
    }

    /** 关闭或离开世界时统一释放资源缓存；外部持有的 wrapper 下次绘制会按新世代取值。 */
    public static void release() {
        EntityPreviewIcons.clear();
        BlockPreviewIcons.clear();
        CatalogPreviewIcons.clear();
        world = null;
        language = null;
        models = null;
        probedTick = Long.MIN_VALUE;
        epoch = new Object();
    }
}
