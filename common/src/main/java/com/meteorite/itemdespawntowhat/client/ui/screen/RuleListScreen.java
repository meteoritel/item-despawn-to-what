package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.view.EditMessage;
import com.meteorite.itemdespawntowhat.client.ui.view.EffectView;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleEditorSession;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleEntry;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 规则列表子屏（视图模型版）：展示当前快照中的规则条目，支持编辑、删除与刷新。
 * 每条规则按来源标注是否可编辑；保存/删除均经变更集提交。
 */
public final class RuleListScreen extends Screen {

    private final Screen parent;
    private final RuleEditorSession session = RuleEditorSession.get();
    private RuleListWidget list;
    private Button editButton;
    private Button deleteButton;
    private int lastVersion = Integer.MIN_VALUE;
    private String lastSeenResult;

    public RuleListScreen(Screen parent) {
        super(Component.translatable("gui.itemdespawntowhat.rule.list.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new RuleListWidget(minecraft, width, height);
        addRenderableWidget(list);
        rebuildEntries();

        int btnW = 80;
        int btnH = 20;
        int gap = 6;
        int totalW = btnW * 4 + gap * 3;
        int startX = (width - totalW) / 2;
        int btnY = height - 28;
        editButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemdespawntowhat.edit.list.edit"),
                        b -> requestEdit())
                .bounds(startX, btnY, btnW, btnH).build());
        deleteButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemdespawntowhat.edit.list.delete"),
                        b -> requestDelete())
                .bounds(startX + btnW + gap, btnY, btnW, btnH).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.itemdespawntowhat.rule.list.refresh"),
                        b -> session.requestSnapshot())
                .bounds(startX + (btnW + gap) * 2, btnY, btnW, btnH).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(startX + (btnW + gap) * 3, btnY, btnW, btnH).build());
        editButton.active = false;
        deleteButton.active = false;
    }

    private void rebuildEntries() {
        list.rebuild(session.entries());
        lastVersion = session.version();
    }

    @Override
    public void tick() {
        super.tick();
        if (session.version() != lastVersion) {
            rebuildEntries();
        }
        RuleRow selected = list == null ? null : list.getSelected();
        boolean hasSelection = selected != null;
        editButton.active = hasSelection;
        deleteButton.active = hasSelection && selected.entry().editable();
        String result = session.lastResult();
        if (result != null && !result.equals(lastSeenResult)) {
            lastSeenResult = result;
        }
    }

    private void requestEdit() {
        RuleRow selected = list.getSelected();
        if (selected == null || minecraft == null) {
            return;
        }
        minecraft.setScreen(new RuleEditScreen(this, null, selected.entry()));
    }

    private void requestDelete() {
        RuleRow selected = list.getSelected();
        if (selected == null || !selected.entry().editable()) {
            return;
        }
        session.submitDelete(selected.entry().ruleId());
        session.requestSnapshot();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (list == null || list.children().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.itemdespawntowhat.rule.list.empty"),
                    width / 2, height / 2, 0xAAAAAA);
        }
        int total = session.rules().size();
        Component stat = Component.translatable("gui.itemdespawntowhat.rule.list.stat", total, session.version());
        graphics.drawString(font, stat, width - font.width(stat) - 6, 12, 0xAAAAAA);
        if (lastSeenResult != null) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.itemdespawntowhat.rule.result", EditMessage.translate(lastSeenResult)),
                    width / 2, 24, 0xFFFFFF);
        }
    }

    // 规则行：id + 效果类型 + 只读/多效果标记，右侧标注来源
    private final class RuleRow extends ObjectSelectionList.Entry<RuleRow> {

        private final RuleEntry entry;

        private RuleRow(RuleEntry entry) {
            this.entry = entry;
        }

        private RuleEntry entry() {
            return entry;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int rowWidth,
                           int rowHeight, int mouseX, int mouseY, boolean hovering, float partialTick) {
            RuleView rule = entry.rule();
            StringBuilder effectNames = new StringBuilder();
            for (EffectView effect : rule.effects()) {
                if (!effectNames.isEmpty()) {
                    effectNames.append(" + ");
                }
                ResourceLocation type = effect.type();
                effectNames.append(type == null ? "?" : type.getPath());
            }
            Component line = Component.literal(rule.id() + "  ->  " + effectNames);
            if (!rule.enabled()) {
                line = Component.translatable("gui.itemdespawntowhat.list.disabled", line);
            }
            if (rule.isMultiEffect()) {
                line = line.copy().append(Component.translatable("gui.itemdespawntowhat.rule.multi_effect_suffix"));
            }
            if (!entry.editable()) {
                line = line.copy().append(Component.translatable("gui.itemdespawntowhat.rule.readonly_suffix"));
            }
            graphics.drawString(font, line, left + 4, top + 7, 0xFFFFFF, false);
            String origin = entry.originText();
            if (!origin.isEmpty()) {
                String clipped = font.plainSubstrByWidth(origin, rowWidth / 2);
                graphics.drawString(font, clipped, left + rowWidth - font.width(clipped) - 4, top + 7,
                        0x808080, false);
            }
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.literal(entry.ruleId());
        }
    }

    // 规则滚动列表
    private final class RuleListWidget extends ObjectSelectionList<RuleRow> {

        private RuleListWidget(Minecraft mc, int listWidth, int listHeight) {
            super(mc, listWidth, listHeight - 80, 36, 24);
        }

        @Override
        public int getRowWidth() {
            return 340;
        }

        // 列表填充必须在本类内完成（AbstractSelectionList 的 clearEntries/addEntry 为 protected）
        private void rebuild(List<RuleEntry> entries) {
            clearEntries();
            for (RuleEntry entry : entries) {
                addEntry(new RuleRow(entry));
            }
        }
    }
}
