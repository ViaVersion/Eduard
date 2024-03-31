package com.viaversion.eduard.command;

import com.viaversion.eduard.command.base.CommandHandler;
import com.viaversion.eduard.util.EmbedMessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public final class MemoryCommand implements CommandHandler {

    @Override
    public void action(final SlashCommandInteractionEvent event) {
        final long totalMemory = Runtime.getRuntime().totalMemory();
        final long freeMemory = Runtime.getRuntime().freeMemory();
        event.replyEmbeds(EmbedMessageUtil.getMessage(
            "Total: " + toMegabytes(totalMemory)
                + "\nFree: " + toMegabytes(freeMemory)
                + "\nUsed: " + (toMegabytes(totalMemory - freeMemory)))
        ).queue();
    }

    private static String toMegabytes(final long bytes) {
        return ((int) (bytes / 1024 / 1024)) + "MB";
    }
}
