package com.meteorite.itemdespawntowhat.core.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.IntFunction;

// 交互链接保留完整命令文本，使控制台与聊天窗口共用同一份反馈。
final class RuleCommandOutput {
    static final int PAGE_SIZE = 8;

    private RuleCommandOutput() {
    }

    static Component command(String key, String command) {
        return RuleCommandText.message(key).arg(command).build().copy()
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    static MutableComponent inspect(Component line, Component detail, String command) {
        return line.copy().withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, detail))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    static Component copyable(String text, Component hint) {
        return Component.literal(text).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hint)));
    }

    static int page(CommandContext<CommandSourceStack> command, Component title, int entryCount, int requestedPage,
                    IntFunction<String> pageCommand, IntFunction<Component> entryAt) {
        int pageCount = Math.max(1, (entryCount - 1) / PAGE_SIZE + 1);
        if (requestedPage > pageCount) {
            return RuleCommandTree.replyFailure(command, RuleCommandText.message("page.invalid").arg(pageCount).build());
        }
        RuleCommandTree.reply(command, title);
        if (entryCount == 0) {
            RuleCommandTree.reply(command, RuleCommandText.message("page.empty").build());
        }
        int start = (requestedPage - 1) * PAGE_SIZE;
        int end = Math.min(entryCount, start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            RuleCommandTree.reply(command, entryAt.apply(index));
        }
        return RuleCommandTree.reply(command, pageFooter(requestedPage, pageCount, pageCommand));
    }

    private static Component pageFooter(int page, int pageCount, IntFunction<String> pageCommand) {
        var footer = RuleCommandText.message("page.info").arg(page).arg(pageCount).build().copy();
        if (page > 1) {
            footer.append(" ").append(command("page.previous", pageCommand.apply(page - 1)));
        }
        if (page < pageCount) {
            footer.append(" ").append(command("page.next", pageCommand.apply(page + 1)));
        }
        return footer;
    }
}
