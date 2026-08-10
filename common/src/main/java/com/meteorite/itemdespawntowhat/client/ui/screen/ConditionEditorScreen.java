package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.google.gson.Gson;
import com.meteorite.itemdespawntowhat.client.ui.condition.ClientConditionTypeDefinition;
import com.meteorite.itemdespawntowhat.client.ui.condition.ClientConditionTypeRegistry;
import com.meteorite.itemdespawntowhat.client.ui.condition.ConditionJsonEditor;
import com.meteorite.itemdespawntowhat.client.ui.condition.ConditionParameterInput;
import com.meteorite.itemdespawntowhat.config.condition.ConditionExpression;
import com.meteorite.itemdespawntowhat.config.condition.ConditionGroup;
import com.meteorite.itemdespawntowhat.config.condition.ConditionLeaf;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 编辑 DNF 条件表达式的独立子屏，组间 OR、组内 AND。
 */
public final class ConditionEditorScreen extends Screen {
    private static final Gson GSON = new Gson();
    private final Screen parent;
    private final Consumer<ConditionExpression> onSave;
    private final ConditionExpression draft;
    private final List<LeafRow> rows = new ArrayList<>();
    private Button addGroupButton;
    private Component errorMessage;
    private int scrollOffset;
    private int maxScroll;
    private boolean suppressRowSync;

    public ConditionEditorScreen(Screen parent, ConditionExpression source, Consumer<ConditionExpression> onSave) {
        super(Component.translatable("gui.itemdespawntowhat.edit.conditions.title"));
        this.parent = parent;
        this.onSave = onSave;
        this.draft = copy(source);
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
        List<ResourceLocation> typeIds = ClientConditionTypeRegistry.all().stream()
                .map(ClientConditionTypeDefinition::id).toList();
        for (int groupIndex = 0; groupIndex < draft.groups().size(); groupIndex++) {
            int currentGroupIndex = groupIndex;
            ConditionGroup group = draft.groups().get(groupIndex);
            for (int leafIndex = 0; leafIndex < group.conditions().size(); leafIndex++) {
                ConditionLeaf leaf = group.conditions().get(leafIndex);
                if (isContentVisible(y)) {
                    LeafRow row = new LeafRow(groupIndex, leafIndex, leaf, typeIds, y);
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
        addGroupButton = Button.builder(Component.translatable("gui.itemdespawntowhat.edit.conditions.add_group"),
                button -> addGroup()).bounds(buttonLeft + buttonWidth + 5, height - 28, buttonWidth, 20).build();
        addRenderableWidget(addGroupButton);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> close())
                .bounds(buttonLeft + (buttonWidth + 5) * 2, height - 28, buttonWidth, 20).build());
    }

    private boolean isContentVisible(int y) {
        return y + 20 >= 32 && y <= height - 54;
    }

    private void addGroup() {
        draft.groups().add(new ConditionGroup());
        rebuildWidgets();
    }

    private void addLeaf(int groupIndex) {
        List<ClientConditionTypeDefinition> definitions = ClientConditionTypeRegistry.all();
        if (definitions.isEmpty()) return;
        ClientConditionTypeDefinition definition = definitions.getFirst();
        draft.groups().get(groupIndex).conditions().add(new ConditionLeaf(definition.id(), null, false));
        rebuildWidgets();
    }

    private void saveAndClose() {
        if (rows.stream().anyMatch(row -> !row.paramsEditor.isValid())) {
            errorMessage = Component.translatable("gui.itemdespawntowhat.edit.conditions.invalid_params")
                    .withStyle(ChatFormatting.RED);
            return;
        }
        rows.forEach(LeafRow::sync);
        draft.groups().removeIf(group -> group.conditions().isEmpty());
        onSave.accept(draft);
        close();
    }

    @Override
    public void onClose() {
        close();
    }

    private void close() {
        if (minecraft != null) minecraft.setScreen(parent);
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

    private static ConditionExpression copy(ConditionExpression source) {
        return source == null ? new ConditionExpression() : GSON.fromJson(GSON.toJson(source), ConditionExpression.class);
    }

    private final class LeafRow {
        private final int groupIndex;
        private final int leafIndex;
        private final ConditionLeaf leaf;
        private final CycleButton<ResourceLocation> typeButton;
        private final CycleButton<Boolean> negatedButton;
        private final ConditionParameterInput paramsEditor;
        private final Button deleteButton;

        private LeafRow(int groupIndex, int leafIndex, ConditionLeaf leaf,
                        List<ResourceLocation> typeIds, int y) {
            this.groupIndex = groupIndex;
            this.leafIndex = leafIndex;
            this.leaf = leaf;
            int typeWidth = Math.min(110, Math.max(80, width / 4));
            int negatedWidth = 65;
            int deleteWidth = 20;
            int paramsWidth = Math.max(70, width - 20 - typeWidth - negatedWidth - deleteWidth - 15);
            int typeX = 10;
            int negatedX = typeX + typeWidth + 5;
            int paramsX = negatedX + negatedWidth + 5;
            int deleteX = paramsX + paramsWidth + 5;
            ClientConditionTypeDefinition currentDefinition = ClientConditionTypeRegistry.byId(leaf.typeId());
            this.paramsEditor = currentDefinition == null
                    ? new ConditionJsonEditor(font) : currentDefinition.createParameterInput(font);
            this.paramsEditor.widget().setX(paramsX);
            this.paramsEditor.widget().setY(y);
            this.paramsEditor.setEditorWidth(paramsWidth);
            this.paramsEditor.setValue(leaf.params());
            List<ResourceLocation> rowTypeIds = new ArrayList<>(typeIds);
            if (!rowTypeIds.contains(leaf.typeId())) {
                rowTypeIds.addFirst(leaf.typeId());
            }
            this.typeButton = CycleButton.<ResourceLocation>builder(id -> {
                        ClientConditionTypeDefinition definition = ClientConditionTypeRegistry.byId(id);
                        return definition == null ? Component.literal(id.toString()) : definition.displayName();
                    }).withValues(rowTypeIds.toArray(ResourceLocation[]::new))
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
            boolean unknownType = currentDefinition == null;
            this.paramsEditor.setEditable(!unknownType);
            this.typeButton.active = !unknownType;
            this.deleteButton = Button.builder(Component.literal("X"), button -> {
                ConditionGroup group = draft.groups().get(groupIndex);
                group.conditions().remove(leaf);
                if (group.conditions().isEmpty()) {
                    draft.groups().remove(group);
                }
                rebuildWidgets();
            }).bounds(deleteX, y, deleteWidth, 20).build();
        }

        private void sync() {
            leaf.setParams(paramsEditor.value());
            leaf.setNegated(negatedButton.getValue());
        }
    }
}
