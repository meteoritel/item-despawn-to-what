package com.meteorite.itemdespawntowhat.dev.guiscreenshot;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** 仅由截图 run 加载的客户端开发入口，正式模组不引用本类。 */
@Mod(value = GuiScreenshotMod.MOD_ID, dist = Dist.CLIENT)
public final class GuiScreenshotMod {
    static final String MOD_ID = "idtw_gui_screenshot";

    /** 注册截图控制器的客户端事件，不创建世界或网络连接。 */
    public GuiScreenshotMod() {
        ScreenshotRunner runner = new ScreenshotRunner(ScreenshotOptions.read(), new ScreenshotScreens());
        NeoForge.EVENT_BUS.addListener(runner::onScreenRendered);
        NeoForge.EVENT_BUS.addListener(runner::onFrameFinished);
    }
}
