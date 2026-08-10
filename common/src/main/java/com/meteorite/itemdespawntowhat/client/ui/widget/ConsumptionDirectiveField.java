package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.form.FormFieldInput;
import com.meteorite.itemdespawntowhat.config.catalogue.CatalystItems;
import com.meteorite.itemdespawntowhat.config.consumption.ConsumptionDirective;
import com.meteorite.itemdespawntowhat.util.SafeParseUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑消耗指令；消耗行为隐含“在场”要求，不再提供重复开关。
 */
public final class ConsumptionDirectiveField extends AbstractCompositeWidget implements FormFieldInput<ConsumptionDirective> {
    private final EditBox catalystBox;
    private final EditBox countBox;
    private final EditBox fluidBox;
    private final CycleButton<Boolean> sourceButton;

    public ConsumptionDirectiveField(Font font) {
        super(0, 0, 240, 46, Component.empty());
        catalystBox = new EditBox(font, 0, 0, 150, 20, Component.empty());
        countBox = new EditBox(font, 0, 0, 80, 20, Component.empty());
        fluidBox = new EditBox(font, 0, 0, 150, 20, Component.empty());
        catalystBox.setHint(Component.translatable("gui.itemdespawntowhat.edit.consumption.catalysts"));
        countBox.setHint(Component.translatable("gui.itemdespawntowhat.edit.consumption.counts"));
        fluidBox.setHint(Component.translatable("gui.itemdespawntowhat.edit.consumption.fluid"));
        sourceButton = CycleButton.booleanBuilder(Component.translatable("gui.itemdespawntowhat.edit.on"),
                        Component.translatable("gui.itemdespawntowhat.edit.off"))
                .withInitialValue(true)
                .create(0, 0, 80, 20, Component.translatable("gui.itemdespawntowhat.edit.inner_fluid.source_label"));
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        catalystBox.setX(x);
        catalystBox.setY(y);
        countBox.setX(x + 155);
        countBox.setY(y);
        fluidBox.setX(x);
        fluidBox.setY(y + 24);
        sourceButton.setX(x + 155);
        sourceButton.setY(y + 24);
        catalystBox.render(graphics, mouseX, mouseY, partialTick);
        countBox.render(graphics, mouseX, mouseY, partialTick);
        fluidBox.render(graphics, mouseX, mouseY, partialTick);
        sourceButton.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected Iterable<EditBox> getEditBoxes() {
        return List.of(catalystBox, countBox, fluidBox);
    }

    @Override
    public @Nullable ConsumptionDirective value() {
        List<CatalystItems.CatalystEntry> catalysts = new ArrayList<>();
        String[] items = catalystBox.getValue().split(",");
        String[] counts = countBox.getValue().split(",");
        for (int i = 0; i < items.length; i++) {
            String item = items[i].trim();
            if (item.isEmpty()) continue;
            int count = i < counts.length ? SafeParseUtil.parseInt(counts[i].trim(), 1) : 1;
            catalysts.add(new CatalystItems.CatalystEntry(item, Math.max(1, count)));
        }
        String fluid = fluidBox.getValue().trim();
        ConsumptionDirective directive = new ConsumptionDirective();
        directive.setCatalystItems(catalysts);
        if (!fluid.isEmpty()) {
            directive.setInnerFluid(new ConsumptionDirective.FluidConsumption(fluid, sourceButton.getValue()));
        }
        return directive.isEmpty() ? null : directive;
    }

    @Override
    public AbstractWidget widget() {
        return this;
    }

    @Override
    public void setValue(@Nullable ConsumptionDirective value) {
        clear();
        if (value == null) return;
        catalystBox.setValue(value.catalystItems().stream().map(CatalystItems.CatalystEntry::itemId).reduce((a, b) -> a + "," + b).orElse(""));
        countBox.setValue(value.catalystItems().stream().map(entry -> Integer.toString(entry.count())).reduce((a, b) -> a + "," + b).orElse(""));
        if (value.innerFluid() != null) {
            fluidBox.setValue(value.innerFluid().fluid());
            sourceButton.setValue(value.innerFluid().requireSource());
        }
    }

    @Override
    public void clear() {
        catalystBox.setValue("");
        countBox.setValue("");
        fluidBox.setValue("");
        sourceButton.setValue(true);
    }
}
