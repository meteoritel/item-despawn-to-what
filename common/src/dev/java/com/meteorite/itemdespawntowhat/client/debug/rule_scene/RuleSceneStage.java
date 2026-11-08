package com.meteorite.itemdespawntowhat.client.debug.rule_scene;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiTransform;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewBudget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewScene;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewScenes;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewSubject;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.UiSceneCamera;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.UiSceneStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.UiSceneView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewResources;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 规则的三维舞台：结构方块、相机、天气与光照叠加层都集中在这里。
 *
 * <p>舞台只消费 {@link RuleSceneModel} 与经过的毫秒数，不依赖屏幕；将来接入其它宿主时可原样复用。</p>
 */
public final class RuleSceneStage implements AutoCloseable {

    private static final PreviewBudget BUDGET = new PreviewBudget(8L * 1024 * 1024,
            32L * 1024 * 1024, 32L * 1024 * 1024, 64, 2, 1_500_000L);
    private static final UiSceneStyle STYLE = new UiSceneStyle(24.0F, 0.16F);
    private static final float BASE_YAW = -0.72F;
    private static final float PITCH = 0.40F;
    private static final int ITEM_SCALE = 4;
    private static final int ITEM_GAP = 58;
    private static final int RAIN_DROPS = 46;

    private final RuleSceneModel model;
    private PreviewScenes scenes;
    private Object resourceGeneration;
    private final String sceneId;
    private final PreviewSubject.Structure subject;
    private final boolean isNether;
    private final boolean isEnd;
    private final RuleSceneModel.TimeOfDay time;
    private final RuleSceneModel.Weather weather;
    private final RuleSceneModel.LightLevel light;
    private final RuleSceneModel.YLevel yLevel;
    private final boolean isOutdoor;
    private final boolean hasFluid;

    /** 以样例 id 建立舞台；同一 id 的场景在所有者内共享缓存。 */
    public RuleSceneStage(RuleSceneModel model, String id) {
        this.model = model;
        this.sceneId = "rule_scene:" + id;
        this.subject = new PreviewSubject.Structure(RuleSceneStructure.build(model));
        this.resourceGeneration = PreviewResources.generation();
        this.scenes = PreviewIcons.createScenes(BUDGET);
        this.isNether = hasDimension(model, "the_nether");
        this.isEnd = hasDimension(model, "the_end");
        this.time = find(model, RuleSceneModel.TimeOfDay.class);
        this.weather = find(model, RuleSceneModel.Weather.class);
        this.light = find(model, RuleSceneModel.LightLevel.class);
        this.yLevel = find(model, RuleSceneModel.YLevel.class);
        this.isOutdoor = find(model, RuleSceneModel.Outdoor.class) != null;
        this.hasFluid = find(model, RuleSceneModel.FluidPresent.class) != null;
    }

    // 同一 GUI 帧可绘制底页与灯箱，两次 draw 共用一次预算与异步交接。
    void beginFrame() {
        Object generation = PreviewResources.generation();
        if (resourceGeneration != generation) {
            scenes.close();
            scenes = PreviewIcons.createScenes(BUDGET);
            resourceGeneration = generation;
        }
        scenes.beginFrame();
    }

    void endFrame() { scenes.endFrame(); }

    /** 在宿主 GUI 帧包络中绘制舞台；调用方负责外框与标题。 */
    public void render(GuiGraphics graphics, UiRect box, long elapsed, UiHostTheme theme) {
        if (box.width() == 0 || box.height() == 0) return;
        UiTransform.enableScissor(graphics, box);
        try {
            PreviewScene scene = scenes.request(sceneId, subject);
            drawSky(graphics, box);
            if (scene.state() == PreviewState.READY) {
                UiSceneView.render(graphics, box, scene, camera(elapsed), STYLE);
            } else {
                drawStatus(graphics, box, scene, theme);
            }
            drawAmbient(graphics, box);
            drawSkyLight(graphics, box, elapsed);
            drawWeather(graphics, box, elapsed);
            drawVignette(graphics, box);
            drawYLevel(graphics, box);
            drawRipples(graphics, box, elapsed);
            drawTriggerPulse(graphics, box, elapsed, theme);
            drawItems(graphics, box, elapsed);
        } finally {
            UiTransform.disableScissor(graphics);
        }
    }

    /** 释放舞台所有者；再次进入帧包络时按当前世代建立资源。 */
    @Override
    public void close() {
        scenes.close();
        resourceGeneration = null;
    }

    // 天空底色先于三维内容绘制，结构之外的区域因此不会露出窗口灰底
    private void drawSky(GuiGraphics graphics, UiRect box) {
        int top = isNether ? 0xFF2A0E12 : isEnd ? 0xFF150A22 : 0xFF7FA6D8;
        int bottom = isNether ? 0xFF6B2A16 : isEnd ? 0xFF2A1440 : 0xFFC9DCEC;
        if (time != null && isNight(time)) {
            top = isEnd ? 0xFF0B0616 : 0xFF0E1226;
            bottom = isEnd ? 0xFF1A0E2E : 0xFF1E2A44;
        }
        int strips = 24;
        int height = Math.max(1, box.height() / strips);
        for (int strip = 0; strip < strips; strip++) {
            float ratio = strip / (float) (strips - 1);
            int y = box.y() + strip * height;
            int end = strip == strips - 1 ? box.bottom() : y + height;
            graphics.fill(box.x(), y, box.right(), end, mix(top, bottom, ratio));
        }
    }

    private void drawStatus(GuiGraphics graphics, UiRect box, PreviewScene scene, UiHostTheme theme) {
        Component text = scene.state() == PreviewState.PREPARING
                ? Component.translatable(RuleSceneModel.UI + "stage.preparing")
                : Component.translatable(RuleSceneModel.UI + "stage.failed");
        int x = box.x() + (box.width() - 60) / 2;
        int y = box.y() + box.height() / 2;
        graphics.drawString(Minecraft.getInstance().font, text, x, y,
                theme.surfaces().secondaryText(), false);
    }

    // 维度与时刻叠加：内蓝、末地紫、夜晚冷色，作为场景的“光照氛围”
    private void drawAmbient(GuiGraphics graphics, UiRect box) {
        if (isNether) graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0x33FF4A1E);
        if (isEnd) graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0x33241048);
        if (time != null) {
            boolean night = isNight(time);
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), night ? 0x3A0A1024 : 0x12FFF2C0);
        }
        if (light != null && light.max() <= 6) {
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0x33000000);
        }
    }

    // 露天条件补一轮太阳与光晕：其它条件都压在场景上，只有露天改变天空本身
    private void drawSkyLight(GuiGraphics graphics, UiRect box, long elapsed) {
        if (!isOutdoor) return;
        boolean night = time != null && isNight(time);
        int centerX = box.right() - 46;
        int centerY = box.y() + 34;
        int disc = night ? 0xF0E8E0 : 0xFFFFF0C0;
        ellipse(graphics, centerX, centerY, 12, 12, disc);
        int glow = night ? 0x0CB0C4FF : 0x0CFFE9A8;
        for (int ring = 1; ring <= 10; ring++) {
            ellipse(graphics, centerX, centerY, 12 + ring * 3, 12 + ring * 3, glow);
        }
        if (night) {
            for (int star = 0; star < 14; star++) {
                int x = box.x() + Math.floorMod(star * 97, Math.max(1, box.width()));
                int y = box.y() + Math.floorMod(star * 61 + (int) (elapsed / 900), Math.max(1, box.height() / 2));
                graphics.fill(x, y, x + 1, y + 1, 0xB0FFFFFF);
            }
        }
    }

    // 天气粒子：雨为斜线、雷暴额外闪光；全部由 elapsed 决定位置
    private void drawWeather(GuiGraphics graphics, UiRect box, long elapsed) {
        if (weather == null) return;
        String kind = weather.weather();
        if ("clear".equals(kind)) return;
        if ("thunder".equals(kind) && Math.floorMod(elapsed, 2400L) < 140L) {
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0x88FFFFFF);
        }
        if ("rain".equals(kind) || "thunder".equals(kind)) {
            int color = 0x66A8CDFF;
            for (int drop = 0; drop < RAIN_DROPS; drop++) {
                int x = box.x() + Math.floorMod(drop * 43 + (int) (elapsed / 3), box.width());
                int y = box.y() + Math.floorMod(drop * 67 + (int) (elapsed / 2), box.height());
                graphics.fill(x, y, x + 1, Math.min(box.bottom(), y + 7), color);
            }
        }
        if ("rain".equals(kind) || "thunder".equals(kind)) {
            graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0x1A101820);
        }
    }

    // 低光照用逐像素加深的四边暗角模拟洞穴，光照充足时反而加一点暖白
    private void drawVignette(GuiGraphics graphics, UiRect box) {
        if (light == null) return;
        boolean dark = light.max() <= 7;
        int depth = dark ? 26 : 12;
        int peak = dark ? 190 : 40;
        int rgb = dark ? 0x000000 : 0xFFF4D8;
        for (int inset = 0; inset < depth; inset++) {
            float ratio = 1.0F - inset / (float) depth;
            int alpha = (int) (peak * ratio * ratio);
            int color = (alpha << 24) | rgb;
            graphics.fill(box.x() + inset, box.y() + inset, box.right() - inset, box.y() + inset + 1, color);
            graphics.fill(box.x() + inset, box.bottom() - inset - 1, box.right() - inset, box.bottom() - inset, color);
            graphics.fill(box.x() + inset, box.y() + inset, box.x() + inset + 1, box.bottom() - inset, color);
            graphics.fill(box.right() - inset - 1, box.y() + inset, box.right() - inset, box.bottom() - inset, color);
        }
    }

    // 高度条件用右侧刻度尺表达：轨道刻度表高度范围，亮段覆盖条件区间
    private void drawYLevel(GuiGraphics graphics, UiRect box) {
        if (yLevel == null) return;
        int x = box.right() - 12;
        int top = box.y() + 14;
        int bottom = box.bottom() - 14;
        graphics.fill(x, top, x + 3, bottom, 0x55FFFFFF);
        for (int tick = 0; tick <= 4; tick++) {
            int y = top + (bottom - top) * tick / 4;
            graphics.fill(x - 3, y, x + 3, y + 1, 0x99FFFFFF);
        }
        int min = Math.clamp(yLevel.min(), -64, 320);
        int max = Math.clamp(yLevel.max(), -64, 320);
        int lowY = bottom - (bottom - top) * (min + 64) / 384;
        int highY = bottom - (bottom - top) * (max + 64) / 384;
        graphics.fill(x - 1, highY, x + 4, lowY, 0xCCFFE08A);
        graphics.fill(x - 4, (highY + lowY) / 2, x + 6, (highY + lowY) / 2 + 1, 0xFFFFF2C0);
    }

    // 有流体时在水面画两圈涟漪，道具像是浸在液面之中
    private void drawRipples(GuiGraphics graphics, UiRect box, long elapsed) {
        if (!hasFluid) return;
        int centerX = box.x() + box.width() / 2;
        int centerY = box.y() + (int) (box.height() * 0.72F);
        for (int ring = 0; ring < 3; ring++) {
            int radius = 26 + ring * 12 + Math.floorMod((int) (elapsed / 6), 8);
            int color = 0x40BFE8FF - ring * 0x0C000000;
            ellipse(graphics, centerX, centerY, radius, radius / 3, color);
        }
    }

    // 触发阶段从场景中心扩散脉冲环；结果阶段再给一次白色爆点
    private void drawTriggerPulse(GuiGraphics graphics, UiRect box, long elapsed, UiHostTheme theme) {
        RuleSceneTimeline.Phase phase = RuleSceneTimeline.phaseOf(elapsed);
        int centerX = box.x() + box.width() / 2;
        int centerY = box.y() + (int) (box.height() * 0.50F);
        if (phase == RuleSceneTimeline.Phase.TRIGGER) {
            float progress = RuleSceneTimeline.progress(elapsed, phase);
            int radius = 18 + (int) (progress * 74);
            int alpha = (int) (0.55F * (1.0F - progress) * 255);
            int color = (Math.clamp(alpha, 0, 255) << 24) | theme.palette().controls().accent() & 0x00FFFFFF;
            ellipse(graphics, centerX, centerY, radius, radius / 2, color);
            int glow = (int) (0.25F * (1.0F - progress) * 255) << 24 | 0x00FFFFFF;
            graphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, glow);
        }
        if (phase == RuleSceneTimeline.Phase.RESULT) {
            float progress = RuleSceneTimeline.progress(elapsed, phase);
            if (progress < 0.45F) {
                float flash = 1.0F - progress / 0.45F;
                int alpha = (int) (flash * 0.6F * 255);
                int radius = 12 + (int) (progress * 40);
                ellipse(graphics, centerX, centerY, radius, radius / 2,
                        (Math.clamp(alpha, 0, 255) << 24) | 0x00FFFFFF);
            }
        }
    }

    // 输入物品漂浮在场景中央：入场下落、驻留浮动、触发时收缩消失
    private void drawItems(GuiGraphics graphics, UiRect box, long elapsed) {
        List<RuleSceneModel.ItemSlot> items = model.inputs();
        if (items.isEmpty()) return;
        RuleSceneTimeline.Phase phase = RuleSceneTimeline.phaseOf(elapsed);
        if (phase == RuleSceneTimeline.Phase.RESULT || phase == RuleSceneTimeline.Phase.HOLD) return;
        float enter = phase == RuleSceneTimeline.Phase.BUILD ? 0.0F
                : RuleSceneTimeline.easeOut(RuleSceneTimeline.progress(elapsed, RuleSceneTimeline.Phase.ITEMS));
        float exit = phase == RuleSceneTimeline.Phase.TRIGGER
                ? 1.0F - 0.6F * RuleSceneTimeline.progress(elapsed, phase) : 1.0F;
        float scale = ITEM_SCALE * (0.35F + 0.65F * Math.max(enter, phase == RuleSceneTimeline.Phase.BUILD ? 0.0F : 1.0F)) * exit;
        if (scale <= 0.05F) return;
        int centerX = box.x() + box.width() / 2;
        int baseY = box.y() + (int) (box.height() * 0.56F);
        float bob = (float) Math.sin(elapsed / 380.0D) * 2.5F;
        int y = baseY + (int) ((1.0F - enter) * -46.0F) + (int) bob;
        int start = centerX - (items.size() - 1) * ITEM_GAP / 2;
        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index).stack();
            if (stack.isEmpty()) continue;
            int x = start + index * ITEM_GAP;
            drawItem(graphics, stack, x, y, scale);
            if (items.get(index).count() > 1) {
                String label = "×" + items.get(index).count();
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(x + 12, y + 12, UiRenderLayers.FOREGROUND);
                    graphics.pose().scale(1.15F, 1.15F, 1.0F);
                    graphics.drawString(Minecraft.getInstance().font, label, 0, 0, 0xFFFFFFFF, true);
                } finally {
                    graphics.pose().popPose();
                }
            }
        }
    }

    // 以物品中心为原点缩放：原版 renderItem 以 16px 为基准并在内部平移 8px
    private static void drawItem(GuiGraphics graphics, ItemStack stack, int centerX, int centerY, float scale) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX, centerY, UiRenderLayers.CONTENT);
            graphics.pose().scale(scale, scale, 1.0F);
            UiRenderLayers.item(graphics, new UiRect(-8, -8, 16, 16), local -> local.renderItem(stack, -8, -8));
            UiRenderLayers.draw(graphics, UiRenderLayers.FOREGROUND,
                    () -> graphics.renderItemDecorations(Minecraft.getInstance().font, stack, -8, -8));
        } finally {
            graphics.pose().popPose();
        }
    }

    // 用水平扫描线近似椭圆：半径大时行数多，但只在舞台内绘制
    private static void ellipse(GuiGraphics graphics, int centerX, int centerY, int radiusX, int radiusY, int color) {
        if (radiusX <= 0 || radiusY <= 0) return;
        for (int dy = -radiusY; dy <= radiusY; dy++) {
            float ratio = dy / (float) radiusY;
            int half = (int) (radiusX * Math.sqrt(Math.max(0.0D, 1.0D - ratio * ratio)));
            graphics.fill(centerX - half, centerY + dy, centerX + half, centerY + dy + 1, color);
        }
    }

    private static UiSceneCamera camera(long elapsed) {
        float yaw = BASE_YAW + (float) Math.sin(elapsed / 3200.0D) * 0.07F;
        return new UiSceneCamera(yaw, PITCH);
    }

    private static int mix(int from, int to, float ratio) {
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * ratio);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * ratio);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * ratio);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean isNight(RuleSceneModel.TimeOfDay time) {
        int midpoint = Math.floorMod((time.from() + time.to()) / 2, 24000);
        return midpoint >= 13000 && midpoint < 23000;
    }

    private static boolean hasDimension(RuleSceneModel model, String path) {
        for (RuleSceneModel.Condition condition : model.conditions()) {
            if (condition instanceof RuleSceneModel.Dimension(List<String> dimensions)) {
                for (String id : dimensions) {
                    if (id.endsWith(path)) return true;
                }
            }
        }
        return false;
    }

    private static <T extends RuleSceneModel.Condition> T find(RuleSceneModel model, Class<T> type) {
        for (RuleSceneModel.Condition condition : model.conditions()) {
            if (type.isInstance(condition)) return type.cast(condition);
        }
        return null;
    }
}
