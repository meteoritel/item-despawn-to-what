package com.meteorite.itemdespawntowhat.client.debug;

import com.meteorite.itemdespawntowhat.client.debug.kit.KitPreviewBudgetExample;
import com.meteorite.itemdespawntowhat.client.debug.kit.WorkbenchLayout;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.geometry.UiRect;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiFocusTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputContext;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputRouter;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputScope;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiInputTarget;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.input.UiPointerHit;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutNode;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.layout.UiLayoutSource;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewMotion;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.preview.PreviewSubject;
import com.meteorite.itemdespawntowhat.client.ui.kit.api.render.UiRenderLayers;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewResources;
import com.meteorite.itemdespawntowhat.client.ui.shared.StructurePreviewResources;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostTheme;
import com.meteorite.itemdespawntowhat.client.ui.theme.UiHostThemes;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButton;
import com.meteorite.itemdespawntowhat.client.ui.widget.UiButtonVariant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/** 开发环境图标验收入口，资源枚举与所有示例共同消费正式预览门面。 */
final class PreviewExamplesScreen extends Screen implements UiLayoutSource {
    private static final String UI = "gui.itemdespawntowhat.edit.preview.examples.";
    private static final int ROW_HEIGHT = 96;
    private static final int ICON_GAP = 12;
    private static final int ICON_SIZES = 3;
    private final UiHostTheme theme = UiHostThemes.defaultTheme();
    private final UiInputRouter input = new UiInputRouter();
    private final List<Example> examples = new ArrayList<>();
    private final List<ResourceLocation> structures;
    private final Runnable back;
    private final List<UiLayoutNode> nodes = new ArrayList<>();
    private int scroll;
    private int scrollMax;
    private boolean rotating = true;
    private boolean budgetsVisible;
    private UiButton backButton;
    private UiInputScope scope;
    private final KitPreviewBudgetExample budgets = new KitPreviewBudgetExample(theme, PreviewIcons::createScenes);
    private Language resources;

    private record Example(String label, PreviewSubject subject, boolean turntable) { }

    PreviewExamplesScreen(Runnable back) {
        super(Component.translatable(UI + "title"));
        this.back = back;
        structures = StructurePreviewResources.list();
        examples.add(new Example("minecraft:diamond", new PreviewSubject.Item(new ItemStack(Items.DIAMOND)), false));
        examples.add(new Example("minecraft:chest", new PreviewSubject.Block(Blocks.CHEST.defaultBlockState(), null), true));
        examples.add(new Example("minecraft:oak_sign", new PreviewSubject.Block(Blocks.OAK_SIGN.defaultBlockState(), sign()), true));
        examples.add(new Example("minecraft:water", new PreviewSubject.Fluid(Blocks.WATER.defaultBlockState().getFluidState()), false));
        examples.add(new Example("minecraft:pig / Age=-24000", new PreviewSubject.Entity(entity("minecraft:pig", -24000)), true));
        CompoundTag item = entity("minecraft:item", 0);
        CompoundTag stack = new CompoundTag(); stack.putString("id", "minecraft:diamond"); stack.putInt("count", 1);
        item.put("Item", stack);
        examples.add(new Example("minecraft:item / diamond", new PreviewSubject.Entity(item), true));
        CompoundTag display = entity("minecraft:item_display", 0); display.put("item", stack.copy());
        examples.add(new Example("minecraft:item_display / diamond", new PreviewSubject.Entity(display), true));
        examples.add(new Example("minecraft:marker", new PreviewSubject.Entity(entity("minecraft:marker", 0)), true));
    }

    private static CompoundTag entity(String id, int age) {
        CompoundTag tag = new CompoundTag(); tag.putString("id", id); tag.putInt("Age", age); return tag;
    }

    private static CompoundTag sign() {
        CompoundTag tag = new CompoundTag(), text = new CompoundTag();
        ListTag messages = new ListTag();
        messages.add(StringTag.valueOf("{\"text\":\"Preview\"}"));
        for (int i = 1; i < 4; i++) messages.add(StringTag.valueOf("{\"text\":\"\"}"));
        text.put("messages", messages); text.putString("color", "black");
        tag.put("front_text", text); return tag;
    }

    /** 页脚返回按钮与整页共用一个输入域，Esc 仍走原版关闭。 */
    @Override protected void init() {
        if (backButton == null) {
            backButton = new UiButton(theme, font, Component.translatable(UI + "back"), UiButtonVariant.SECONDARY, this::onClose);
            scope = new UiInputScope(new UiInputTarget() {
                @Override public void addFocusTargets(Consumer<UiFocusTarget> out) { out.accept(backButton); }
                @Override public UiPointerHit hitTest(double x, double y) { return UiPointerHit.topmost(x, y, List.of(backButton)); }
                @Override public boolean mousePressed(UiInputContext context) { return backButton.mousePressed(context); }
            });
            scope.setEscape(this::onClose);
        }
        WorkbenchLayout.placeFooterBack(backButton, height);
        scope.refreshFocus();
        input.select(scope);
    }

    /** 一帧只推进一次预算；同一行显示多个尺寸以核对缓存和固定适框。 */
    @Override public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (resources != Language.getInstance()) { budgets.invalidateResources(); resources = Language.getInstance(); }
        nodes.clear();
        PreviewIcons.renderFrame(() -> renderExamples(graphics, mouseX, mouseY, partialTick));
    }

    private void renderExamples(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        WorkbenchLayout.drawPage(graphics, font, theme, width, height, title);
        long[] memory = PreviewIcons.memoryBytes();
        Component budget = Component.translatable(UI + "budget", memory[0] / 1024, memory[1] / 1024);
        graphics.drawString(font, budget, width - WorkbenchLayout.INSET - font.width(budget), WorkbenchLayout.TITLE_Y,
                theme.surfaces().secondaryText(), false);
        WorkbenchLayout.drawSubtitle(graphics, font, theme, width, Component.translatable(UI + "controls"));
        WorkbenchLayout.drawFooterRule(graphics, theme, width, height);
        var pointer = scope.pointer(mouseX, mouseY).resolve(scope.content());
        backButton.render(graphics, font, pointer);
        nodes.addAll(backButton.layoutNodes());
        if (budgetsVisible) {
            budgets.render(graphics, font, width, height);
            nodes.addAll(budgets.layoutNodes());
            return;
        }
        int top = WorkbenchLayout.CONTENT_TOP;
        int bottom = WorkbenchLayout.contentBottom(height);
        scrollMax = Math.max(0, (structures.size() + examples.size()) * ROW_HEIGHT - Math.max(0, bottom - top));
        scroll = Math.clamp(scroll, 0, scrollMax);
        int contentWidth = WorkbenchLayout.contentWidth(width);
        int large = width - WorkbenchLayout.INSET - 80;
        int medium = large - ICON_GAP - 48;
        int small = medium - ICON_GAP - 16;
        int labelWidth = Math.max(40, small - ICON_GAP - WorkbenchLayout.INSET);
        graphics.enableScissor(WorkbenchLayout.INSET, top, width - WorkbenchLayout.INSET, bottom);
        try {
            int total = structures.size() + examples.size();
            for (int row = 0; row < total; row++) {
                int rowTop = top + row * ROW_HEIGHT - scroll;
                if (rowTop + ROW_HEIGHT <= top || rowTop >= bottom) continue;
                String label = row < structures.size() ? structures.get(row).toString() : examples.get(row - structures.size()).label();
                // 左侧文字块与右侧图标一起垂直居中：结构行带状态行，整体上移半行。
                int textTop = rowTop + (ROW_HEIGHT - font.lineHeight) / 2 - (row < structures.size() ? 7 : 0);
                graphics.drawString(font, WorkbenchLayout.ellipsize(font, Component.literal(label), labelWidth),
                        WorkbenchLayout.INSET, textTop, theme.surfaces().text(), false);
                if (row < structures.size()) {
                    ResourceLocation id = structures.get(row);
                    graphics.drawString(font, PreviewIcons.status(PreviewIcons.structureState(id)),
                            WorkbenchLayout.INSET, textTop + 14, theme.surfaces().secondaryText(), false);
                    String reason = PreviewIcons.structureReason(id);
                    if (reason != null) {
                        graphics.drawString(font, WorkbenchLayout.ellipsize(font, Component.literal(reason), labelWidth),
                                WorkbenchLayout.INSET, textTop + 27, theme.palette().controls().danger(), false);
                    }
                    drawSlot(graphics, small, rowTop, 16);
                    drawSlot(graphics, medium, rowTop, 48);
                    drawSlot(graphics, large, rowTop, 80);
                    PreviewIcons.structure(id, 16).render(graphics, small, rowTop + (ROW_HEIGHT - 16) / 2);
                    PreviewIcons.structure(id, 48).render(graphics, medium, rowTop + (ROW_HEIGHT - 48) / 2);
                    PreviewIcons.structure(id, 80).render(graphics, large, rowTop + (ROW_HEIGHT - 80) / 2);
                } else {
                    Example example = examples.get(row - structures.size());
                    PreviewMotion motion = new PreviewMotion(example.turntable() ? 0 : 45, rotating && example.turntable() ? 10_000 : 0);
                    int[] sizes = {16, 48, 80};
                    int[] columns = {small, medium, large};
                    for (int i = 0; i < ICON_SIZES; i++) {
                        int size = sizes[i];
                        int x = columns[i];
                        int y = rowTop + (ROW_HEIGHT - size) / 2;
                        drawSlot(graphics, x, rowTop, size);
                        PreviewIcons.icon(example.label(), example.subject(), size, motion).render(graphics, x, y);
                    }
                }
                theme.drawDivider(graphics, WorkbenchLayout.INSET, rowTop + ROW_HEIGHT - 1, contentWidth);
                int visibleTop = Math.max(top, rowTop);
                UiRect rect = new UiRect(WorkbenchLayout.INSET, visibleTop, contentWidth, Math.min(ROW_HEIGHT, bottom - visibleTop));
                nodes.add(new UiLayoutNode(UiLayoutNode.Kind.ROW, rect, Component.literal(label), font.width(label), contentWidth, true, true, List.of()));
            }
        } finally { graphics.disableScissor(); graphics.flush(); }
    }

    /** 图标预先在浅色槽位里排好，深色贴图不会直接压在窗口底色上。 */
    private void drawSlot(GuiGraphics graphics, int x, int rowTop, int size) {
        theme.drawInset(graphics, new UiRect(x, rowTop + (ROW_HEIGHT - size) / 2, size, size));
    }

    /** 返回目录时重建工作台场景。 */
    @Override public void onClose() { back.run(); }
    /** 只读导出当前可见预览行。 */
    @Override public List<UiLayoutNode> layoutNodes() { return List.copyOf(nodes); }

    /** 鼠标滚轮浏览当前资源目录。 */
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (budgetsVisible) { return false; }
        scroll = Math.clamp(scroll - (int) (vertical * 30), 0, scrollMax);
        return true;
    }

    /** 空格切换全部转台；R 清理世代后重读资源。 */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_B) { budgetsVisible = true; budgets.nextBudget(); return true; }
        if (key == GLFW.GLFW_KEY_N && budgetsVisible) { budgets.nextPage(); return true; }
        if (key == GLFW.GLFW_KEY_SPACE) { rotating = !rotating; PreviewIcons.setRotating(rotating); return true; }
        if (key == GLFW.GLFW_KEY_R) { PreviewResources.release(); budgets.invalidateResources(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        return input.mousePressed(UiInputContext.of(scope, x, y, button, hasShiftDown(), hasControlDown(), hasAltDown()));
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return input.mouseReleased(UiInputContext.of(scope, x, y, button, hasShiftDown(), hasControlDown(), hasAltDown()));
    }

    /** 关闭开发入口时恢复转台设置并短期保留近期就绪场景。 */
    @Override public void removed() {
        input.close();
        budgets.close();
        PreviewIcons.setRotating(true);
        PreviewResources.screenClosed();
        UiRenderLayers.releaseModels();
    }
    /** 图标验收使用持续推进的客户端界面时间。 */
    @Override public boolean isPauseScreen() { return false; }
}