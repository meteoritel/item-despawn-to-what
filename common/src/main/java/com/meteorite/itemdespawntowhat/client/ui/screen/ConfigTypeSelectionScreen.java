package com.meteorite.itemdespawntowhat.client.ui.screen;

import com.meteorite.itemdespawntowhat.client.ui.view.RuleEditorSession;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleTemplate;
import com.meteorite.itemdespawntowhat.client.ui.view.RuleTemplates;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 进入具体编辑器前的模板选择界面（视图模型版）。
 * 保留原第一屏的 9 个模板入口与布局，点击模板 = 新建一条只含对应效果的规则。
 */
public class ConfigTypeSelectionScreen extends Screen {

    public ConfigTypeSelectionScreen() {
        super(Component.translatable("gui.itemdespawntowhat.config_selection.title"));
    }

    @Override
    protected void init() {
        List<RuleTemplate> templates = RuleTemplates.all();
        int y = height / 2 - (templates.size() * 25) / 2;
        for (RuleTemplate template : templates) {
            Button button = Button.builder(
                            Component.translatable(template.labelKey()),
                            btn -> openTemplate(template))
                    .bounds(width / 2 - 100, y, 200, 20).build();
            button.setTooltip(Tooltip.create(Component.translatable(template.tooltipKey())));
            addRenderableWidget(button);
            y += 25;
        }
    }

    // 先刷新覆盖层快照（规则列表屏需要），再以模板新建一条规则进入编辑器。
    private void openTemplate(RuleTemplate template) {
        if (minecraft == null) {
            return;
        }
        RuleEditorSession.get().requestSnapshot();
        minecraft.setScreen(new RuleEditScreen(this, template, null));
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
    }
}
