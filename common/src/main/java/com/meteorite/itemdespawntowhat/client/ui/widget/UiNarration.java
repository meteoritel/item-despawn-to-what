package com.meteorite.itemdespawntowhat.client.ui.widget;

import com.meteorite.itemdespawntowhat.client.ui.kit.UiFocusTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 旁白适配（主计划 §8「键盘与朗读」）：把控件的 {@link UiFocusTarget#accessibleName()}
 * 交给 Minecraft narrator 播报。
 * <p>只读取当前选项状态：未开启朗读、平台不支持或朗读服务不可用时保持静默，不影响界面输入。
 * <p>{@code GameNarrator.sayNow(Component)} 自身会检查 narrator 状态并在需要时打断上一句，
 * 因此这里不重复判断开关。
 */
public final class UiNarration {

    private UiNarration() {
    }

    // 焦点变化：播报新焦点控件的可读名称（无名称时静默）
    public static void focus(@Nullable UiFocusTarget target) {
        if (target == null) {
            return;
        }
        say(target.accessibleName());
    }

    // 播报一条本地化文本；朗读失败只静默忽略
    public static void say(@Nullable Component message) {
        if (message == null || message.getString().isBlank()) {
            return;
        }
        try {
            Minecraft.getInstance().getNarrator().sayNow(message);
        } catch (RuntimeException | LinkageError ignored) {
            // 朗读服务不可用时不影响界面
        }
    }
}
