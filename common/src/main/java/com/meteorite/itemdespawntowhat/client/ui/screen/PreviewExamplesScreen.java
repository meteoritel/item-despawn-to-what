package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiIcon;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewMotion;
import com.meteorite.itemdespawntowhat.client.ui.kit.preview.PreviewSubject;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewIcons;
import com.meteorite.itemdespawntowhat.client.ui.shared.PreviewResources;
import com.meteorite.itemdespawntowhat.client.ui.shared.StructurePreviewResources;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
final class PreviewExamplesScreen extends Screen {
    private static final String UI = "gui.itemdespawntowhat.edit.preview.examples.";
    private static final int ROW_HEIGHT = 100;
    private final List<Example> examples = new ArrayList<>();
    private final List<ResourceLocation> structures;
    private int scroll;
    private boolean rotating = true;

    private record Example(String label, PreviewSubject subject, boolean turntable) { }

    PreviewExamplesScreen() {
        super(Component.translatable(UI + "title"));
        structures = StructurePreviewResources.list();
        examples.add(new Example("minecraft:diamond", new PreviewSubject.Item(new ItemStack(Items.DIAMOND)), false));
        examples.add(new Example("minecraft:chest", new PreviewSubject.Block(Blocks.CHEST.defaultBlockState(), null), false));
        examples.add(new Example("minecraft:oak_sign", new PreviewSubject.Block(Blocks.OAK_SIGN.defaultBlockState(), sign()), false));
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

    /** 一帧只推进一次预算；同一行显示多个尺寸以核对缓存和固定适框。 */
    @Override public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PreviewIcons.beginFrame();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xEE171B23);
        graphics.drawString(font, title, 8, 8, 0xFFFFFF, false);
        long[] memory = PreviewIcons.memoryBytes();
        graphics.drawString(font, Component.translatable(UI + "budget", memory[0] / 1024, memory[1] / 1024), 8, 23, 0xCCCCCC, false);
        graphics.drawString(font, Component.translatable(UI + "controls"), 8, 37, 0xCCCCCC, false);
        graphics.enableScissor(0, 53, width, height);
        try {
            int total = structures.size() + examples.size();
            for (int row = 0; row < total; row++) {
                int y = 56 + row * ROW_HEIGHT - scroll;
                if (y + ROW_HEIGHT < 53 || y >= height) continue;
                if (row < structures.size()) {
                    ResourceLocation id = structures.get(row);
                    graphics.drawString(font, font.plainSubstrByWidth(id.toString(), Math.max(0, width - 210)), 8, y, 0xFFFFFF, false);
                    graphics.drawString(font, PreviewIcons.status(PreviewIcons.structureState(id)), 8, y + 13, 0xCCCCCC, false);
                    String reason = PreviewIcons.structureReason(id);
                    if (reason != null) graphics.drawString(font, font.plainSubstrByWidth(reason, Math.max(0, width - 220)), 8, y + 27, 0xFF8888, false);
                    PreviewIcons.structure(id, 16).render(graphics, width - 190, y + 36);
                    PreviewIcons.structure(id, 48).render(graphics, width - 162, y + 25);
                    PreviewIcons.structure(id, 80).render(graphics, width - 90, y + 10);
                } else {
                    Example example = examples.get(row - structures.size());
                    graphics.drawString(font, font.plainSubstrByWidth(example.label(), Math.max(0, width - 210)), 8, y, 0xFFFFFF, false);
                    PreviewMotion motion = new PreviewMotion(example.turntable() ? 0 : 45, rotating && example.turntable() ? 10_000 : 0);
                    for (int size : new int[]{16, 48, 80}) {
                        UiIcon icon = PreviewIcons.icon(example.label(), example.subject(), size, motion);
                        int x = size == 16 ? width - 190 : size == 48 ? width - 162 : width - 90;
                        icon.render(graphics, x, y + 10);
                    }
                }
            }
        } finally { graphics.disableScissor(); }
    }

    /** 鼠标滚轮浏览当前资源目录。 */
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int maximum = Math.max(0, (structures.size() + examples.size()) * ROW_HEIGHT - Math.max(0, height - 56));
        scroll = Math.clamp(scroll - (int) (vertical * 30), 0, maximum);
        return true;
    }

    /** 空格切换全部转台；R 清理世代后重读资源。 */
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE) { rotating = !rotating; PreviewIcons.setRotating(rotating); return true; }
        if (key == GLFW.GLFW_KEY_R) { PreviewResources.release(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    /** 关闭开发入口时释放全部预览资源。 */
    @Override public void removed() { PreviewIcons.setRotating(true); PreviewResources.release(); }
    /** 图标验收使用持续推进的客户端界面时间。 */
    @Override public boolean isPauseScreen() { return false; }
}
