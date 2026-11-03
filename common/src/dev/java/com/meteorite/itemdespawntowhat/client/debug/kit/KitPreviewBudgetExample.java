package com.meteorite.itemdespawntowhat.client.debug.kit;

import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewBudget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewMotion;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewScene;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewScenes;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewState;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewSubject;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.UiSceneCamera;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.UiSceneStyle;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.UiSceneView;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiModelBounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 独立预算示例复用平台装配工厂，场景句柄、取景与账本直接消费 kit 契约。 */
public final class KitPreviewBudgetExample implements AutoCloseable {
    private static final String UI = KitShowcaseContent.UI + "showcase.preview.";
    private static final int CAPACITY_MODE = 3;
    private static final List<PreviewBudget> BUDGETS = List.of(PreviewBudget.DEFAULT,
            new PreviewBudget(2048, 4096, 4096, 4, 1, 750_000),
            new PreviewBudget(64, 128, 128, 4, 1, 750_000),
            new PreviewBudget(8L * 1024 * 1024, 32L * 1024 * 1024, 32L * 1024 * 1024, 4, 2, 1_500_000));
    private final Function<PreviewBudget, PreviewScenes> factory;
    private final List<PreviewSubject> subjects = List.of(
            new PreviewSubject.Block(Blocks.STONE.defaultBlockState(), null),
            new PreviewSubject.Block(Blocks.GLASS.defaultBlockState(), null),
            new PreviewSubject.Block(Blocks.OAK_PLANKS.defaultBlockState(), null),
            new PreviewSubject.Block(Blocks.GOLD_BLOCK.defaultBlockState(), null),
            new PreviewSubject.Block(Blocks.DIAMOND_BLOCK.defaultBlockState(), null),
            new PreviewSubject.Block(Blocks.BRICKS.defaultBlockState(), null));
    private final List<UiLayoutNode> nodes = new ArrayList<>();
    private PreviewScenes scenes;
    private int mode;
    private int page;
    private int generation;
    private PreviewScene capacityRejected;

    /** 工厂注入平台 Geometry 与 executor；本例只负责自己的场景所有者。 */
    public KitPreviewBudgetExample(Function<PreviewBudget, PreviewScenes> factory) { this.factory = factory; }
    /** 依次选择默认、小字节预算、单场景过限与请求槽位容量。 */
    public void nextBudget() { close(); mode = (mode + 1) % BUDGETS.size(); }
    /** 切换实际可见请求，使旧场景进入淘汰范围。 */
    public void nextPage() { close(); page = (page + 1) % 2; }
    /** 资源重载后释放旧世代，下一帧重建。 */
    public void invalidateResources() { close(); }
    /** 以真实场景绘制和公开状态导出预算结果。 */
    public void render(GuiGraphics graphics, Font font, int width, int height) {
        if (scenes == null) { scenes = factory.apply(BUDGETS.get(mode)); generation++; }
        nodes.clear();
        scenes.beginFrame();
        try {
            Component heading = Component.translatable(UI + "mode", mode, page, generation);
            graphics.drawString(font, heading, 8, 56, 0xFFFFFFFF, false);
            nodes.add(row(new UiRect(8, 56, width - 16, 12), heading));
            if (mode == CAPACITY_MODE && capacityRejected == null) {
                for (int index = 0; index < 4; index++) {
                    scenes.request("capacity-" + index, subjects.get(index));
                }
                capacityRejected = scenes.request("capacity-extra", subjects.get(4));
            }
            for (int index = 0; index < 3; index++) {
                PreviewSubject subject = subjects.get((mode == CAPACITY_MODE ? 0 : page * 3) + index);
                String id = "budget-" + (page * 3 + index);
                int x = 8 + index * Math.max(1, (width - 16) / 3);
                UiRect slot = new UiRect(x, 104, Math.max(1, (width - 32) / 3), Math.clamp(height - 154, 1, 120));
                UiIcon marker = new UiIcon.Rendered(slot.width(), slot.height(), (g, box) ->
                        g.renderOutline(box.x(), box.y(), box.width(), box.height(), 0xFFFF5555));
                scenes.icon(id, subject, slot.height(), new PreviewMotion(45, 0), UiSceneStyle.icon(), marker)
                        .render(graphics, x, 104);
                PreviewScene scene = scenes.request(id, subject);
                UiModelBounds bounds = scene.bounds();
                // 第二个视口直接使用公开句柄和相机；同一个模型不重复编译。
                if (scene.state() == PreviewState.READY && bounds != null) {
                    UiSceneCamera camera = new UiSceneCamera((float) Math.toRadians((Util.getMillis() % 10000) * 0.036), 0.4F);
                    UiSceneView.render(graphics, new UiRect(x, slot.bottom() + 8, slot.width(), slot.height()), scene, camera, UiSceneStyle.icon());
                }
                Component state = Component.literal(scene.state().name());
                Component details = Component.translatable(UI + "state", index,
                        bounds == null ? 0 : bounds.scale(slot, 0.4F));
                graphics.drawString(font, state, x, 76, 0xFFFFFFFF, false);
                nodes.add(row(new UiRect(x, 76, Math.max(1, (width - 16) / 3), 12), state));
                graphics.drawString(font, details, x, 90, 0xFFFFFFFF, false);
                nodes.add(row(new UiRect(x, 90, Math.max(1, (width - 16) / 3), 12), details));
            }
            if (mode == CAPACITY_MODE) {
                PreviewScene current = scenes.request("capacity-extra", subjects.get(4));
                Component capacity = Component.translatable(UI + "capacity", capacityRejected.state().name(),
                        current.state().name(), capacityRejected.reason());
                graphics.drawString(font, capacity, 8, height - 40, 0xFFFFFFFF, false);
                nodes.add(row(new UiRect(8, height - 40, Math.max(1, width - 16), 12), capacity));
            }
            long[] memory = scenes.memoryBytes();
            Component memoryText = Component.translatable(UI + "memory", memory[0], memory[1],
                    BUDGETS.get(mode).gpuBytes(), BUDGETS.get(mode).pendingBytes());
            graphics.drawString(font, memoryText, 8, height - 24, 0xFFFFFFFF, false);
            nodes.add(row(new UiRect(8, height - 24, width - 16, 12), memoryText));
        } finally { scenes.endFrame(); }
    }
    /** 仅导出实际渲染帧的状态。 */
    public List<UiLayoutNode> layoutNodes() { return List.copyOf(nodes); }
    private static UiLayoutNode row(UiRect bounds, Component text) {
        return new UiLayoutNode(UiLayoutNode.Kind.ROW, bounds, text, 0, 0, true, true, List.of());
    }
    /** 关闭包含排队、完成交接和 GPU 的完整所有者，宿主 executor 可继续使用。 */
    @Override public void close() { if (scenes != null) { scenes.close(); scenes = null; } capacityRejected = null; }
}
