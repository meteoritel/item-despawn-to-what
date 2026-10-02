package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.condition.ConditionJsonEditor;
import com.meteorite.itemdespawntowhat.client.ui.condition.ConditionParameterInput;
import com.meteorite.itemdespawntowhat.client.ui.view.ConditionView;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleConditionInputs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 编辑 DNF 条件表达式的独立子屏（视图模型版），组间 OR、组内 AND。
 * 复用既有条件参数输入控件（client/ui/condition），布局与交互与原条件子屏一致。
 */
public final class RuleConditionEditorScreen extends Screen {

    private final Screen parent;
    private final Consumer<ConditionView> onSave;
    private final ConditionView draft;
    private final List<LeafRow> rows = new ArrayList<>();
    private Component errorMessage;
    private int scrollOffset;
    private int maxScroll;
    private boolean suppressRowSync;

    public RuleConditionEditorScreen(Screen parent, ConditionView source, Consumer<ConditionView> onSave) {
        super(Component.translatable("gui.itemdespawntowhat.edit.conditions.title"));
        this.parent = parent;
        this.onSave = onSave;
        this.draft = ConditionView.copyOf(source);
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        if (!suppressRowSync) {
            rows.forEach(LeafRow::sync);
        }
        suppressRowSync = false;
        clearWidgets();
        rows.clear();
        int y = 34 - scrollOffset;
        List<ResourceLocation> typeIds = RuleConditionInputs.all();
        for (int groupIndex = 0; groupIndex < draft.groups().size(); groupIndex++) {
            int currentGroupIndex = groupIndex;
            List<ConditionView.Leaf> group = draft.groups().get(groupIndex);
            for (int leafIndex = 0; leafIndex < group.size(); leafIndex++) {
                ConditionView.Leaf leaf = group.get(leafIndex);
                if (isContentVisible(y)) {
                    LeafRow row = new LeafRow(groupIndex, leaf, typeIds, y);
                    rows.add(row);
                    addRenderableWidget(row.typeButton);
                    addRenderableWidget(row.negatedButton);
                    addRenderableWidget(row.paramsEditor.widget());
                    addRenderableWidget(row.deleteButton);
                }
                y += 26;
            }
            if (isContentVisible(y)) {
                addRenderableWidget(Button.builder(Component.translatable("gui.itemdespawntowhat.edit.conditions.add_leaf"),
                        button -> addLeaf(currentGroupIndex)).bounds(10, y, 110, 20).build());
            }
            y += 26;
            if (groupIndex < draft.groups().size() - 1) {
                if (isContentVisible(y)) {
                    addRenderableWidget(Button.builder(Component.translatable("gui.itemdespawntowhat.condition.or"),
                            button -> { }).bounds(10, y, 45, 20).build());
                }
                y += 26;
            }
        }
        int contentHeight = y + scrollOffset - 34;
        maxScroll = Math.max(0, contentHeight - Math.max(20, height - 88));
        scrollOffset = Math.min(scrollOffset, maxScroll);
        int buttonWidth = Math.min(90, Math.max(70, (width - 40) / 3));
        int buttonLeft = (width - (buttonWidth * 3 + 10)) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(buttonLeft, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.itemdespawntowhat.edit.conditions.add_group"),
                button -> addGroup()).bounds(buttonLeft + buttonWidth + 5, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> close())
                .bounds(buttonLeft + (buttonWidth + 5) * 2, height - 28, buttonWidth, 20).build());
    }

    private boolean isContentVisible(int y) {
        return y + 20 >= 32 && y <= height - 54;
    }

    private void addGroup() {
        draft.groups().add(new ArrayList<>());
        rebuildWidgets();
    }

    private void addLeaf(int groupIndex) {
        List<ResourceLocation> typeIds = RuleConditionInputs.all();
        if (typeIds.isEmpty()) {
            return;
        }
        draft.groups().get(groupIndex).add(ConditionView.Leaf.of(typeIds.getFirst()));
        rebuildWidgets();
    }

    private void saveAndClose() {
        if (rows.stream().anyMatch(row -> !row.paramsEditor.isValid())) {
            errorMessage = Component.translatable("gui.itemdespawntowhat.edit.conditions.invalid_params");
            return;
        }
        rows.forEach(LeafRow::sync);
        draft.groups().removeIf(List::isEmpty);
        onSave.accept(draft);
        close();
    }

    @Override
    public void onClose() {
        close();
    }

    private void close() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (errorMessage != null) {
            graphics.drawCenteredString(font, errorMessage, width / 2, 24, 0xFF5555);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0 && scrollY != 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(scrollY) * 26));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled) {
            setFocused(null);
        }
        return handled;
    }

    /**
     * 一行条件叶：类型选择 + 取反开关 + 参数编辑器 + 删除按钮。
     */
    private final class LeafRow {
        private final int groupIndex;
        private final ConditionView.Leaf leaf;
        private final CycleButton<ResourceLocation> typeButton;
        private final CycleButton<Boolean> negatedButton;
        private final ConditionParameterInput paramsEditor;
        private final Button deleteButton;

        private LeafRow(int groupIndex, ConditionView.Leaf leaf, List<ResourceLocation> typeIds, int y) {
            this.groupIndex = groupIndex;
            this.leaf = leaf;
            int typeWidth = Math.min(110, Math.max(80, width / 4));
            int negatedWidth = 65;
            int deleteWidth = 20;
            int paramsWidth = Math.max(70, width - 20 - typeWidth - negatedWidth - deleteWidth - 15);
            int typeX = 10;
            int negatedX = typeX + typeWidth + 5;
            int paramsX = negatedX + negatedWidth + 5;
            int deleteX = paramsX + paramsWidth + 5;
            ConditionParameterInput registered = RuleConditionInputs.create(leaf.typeId(), font);
            boolean knownType = registered != null;
            this.paramsEditor = knownType ? registered : new ConditionJsonEditor(font);
            this.paramsEditor.widget().setX(paramsX);
            this.paramsEditor.widget().setY(y);
            this.paramsEditor.setEditorWidth(paramsWidth);
            this.paramsEditor.setValue(leaf.params());
            List<ResourceLocation> rowTypeIds = new ArrayList<>(typeIds);
            if (leaf.typeId() != null && !rowTypeIds.contains(leaf.typeId())) {
                rowTypeIds.addFirst(leaf.typeId());
            }
            this.typeButton = CycleButton.<ResourceLocation>builder(id ->
                            RuleConditionInputs.isKnown(id) ? RuleConditionInputs.displayName(id) : Component.literal(id.toString()))
                    .withValues(rowTypeIds.toArray(ResourceLocation[]::new))
                    .withInitialValue(leaf.typeId())
                    .create(typeX, y, typeWidth, 20, Component.translatable("gui.itemdespawntowhat.condition.type"),
                            (button, value) -> {
                                rows.forEach(LeafRow::sync);
                                leaf.setTypeId(value);
                                leaf.setParams(new com.google.gson.JsonObject());
                                suppressRowSync = true;
                                rebuildWidgets();
                            });
            this.negatedButton = CycleButton.booleanBuilder(Component.translatable("gui.itemdespawntowhat.condition.not"),
                            Component.translatable("gui.itemdespawntowhat.condition.normal"))
                    .withInitialValue(leaf.negated())
                    .create(negatedX, y, negatedWidth, 20, Component.empty(), (button, value) -> leaf.setNegated(value));
            this.paramsEditor.setEditable(knownType);
            this.typeButton.active = true;
            this.deleteButton = Button.builder(Component.literal("X"), button -> {
                List<ConditionView.Leaf> group = draft.groups().get(groupIndex);
                group.remove(leaf);
                if (group.isEmpty()) {
                    draft.groups().remove(group);
                }
                rebuildWidgets();
            }).bounds(deleteX, y, deleteWidth, 20).build();
        }

        // 把控件当前值同步回条件叶
        private void sync() {
            leaf.setParams(paramsEditor.value());
            leaf.setNegated(negatedButton.getValue());
        }
    }
}
