package com.meteorite.itemdespawntowhat.core.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.function.IntFunction;

/** 命令内部的分页与交互组件；控制台保留完整可输入的命令文本。 */
final class RuleCommandOutput {
    static final int PAGE_SIZE = 8;
    private static final String PREFIX = "itemdespawntowhat.command.";

    private RuleCommandOutput() { }

    static Component command(String key, String command) {
        return RuleCommandText.of(PREFIX + key).arg(command).build().copy()
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    static Component inspect(Component line, Component detail, String command) {
        return line.copy().withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, detail))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    static int page(CommandContext<CommandSourceStack> ctx, Component title, int count, int requested,
                    IntFunction<String> route, IntFunction<Component> entry) {
        int pages = Math.max(1, (count - 1) / PAGE_SIZE + 1);
        if (requested > pages) {
            return RuleCommandTree.replyFailure(ctx, RuleCommandText.of(PREFIX + "page.invalid")
                    .arg(pages).build());
        }
        RuleCommandTree.reply(ctx, title);
        if (count == 0) {
            RuleCommandTree.reply(ctx, RuleCommandText.of(PREFIX + "page.empty").build());
        }
        int start = (requested - 1) * PAGE_SIZE;
        for (int i = start; i < Math.min(count, start + PAGE_SIZE); i++) {
            RuleCommandTree.reply(ctx, entry.apply(i));
        }
        var footer = RuleCommandText.of(PREFIX + "page.info").arg(requested).arg(pages).build().copy();
        if (requested > 1) { footer.append(" ").append(command("page.previous", route.apply(requested - 1))); }
        if (requested < pages) { footer.append(" ").append(command("page.next", route.apply(requested + 1))); }
        return RuleCommandTree.reply(ctx, footer);
    }
}
