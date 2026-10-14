package com.meteorite.itemdespawntowhat.dev.guiscreenshot;

import com.meteorite.itemdespawntowhat.client.ui.prototype.UiPrototypeScreen;
import com.meteorite.itemdespawntowhat.client.ui.screen.RuleEditorScreen;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.Screen;

/** 开发工具的 Screen 工厂注册表；每次截图创建独立 Screen 和 fixture。 */
final class ScreenshotScreens {
    private final Map<String, Supplier<Screen>> factories = new LinkedHashMap<>();

    ScreenshotScreens() {
        register("prototype", UiPrototypeScreen::new);
        register("editor-list", () -> new RuleEditorScreen(new ScreenshotWorkspace()));
    }

    void register(String id, Supplier<Screen> factory) {
        if (factories.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("重复 Screen id: " + id);
        }
    }

    Screen create(String id) {
        Supplier<Screen> factory = factories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("未知 Screen id: " + id + "；可选项: " + factories.keySet());
        }
        return factory.get();
    }
}
