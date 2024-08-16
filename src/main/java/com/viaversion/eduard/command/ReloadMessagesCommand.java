package com.viaversion.eduard.command;

import com.viaversion.eduard.ViaEduardBot;
import com.viaversion.eduard.command.base.CommandHandler;
import com.viaversion.eduard.util.SupportMessage;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import java.util.ArrayList;
import java.util.List;

public final class ReloadMessagesCommand implements CommandHandler {

    private final ViaEduardBot bot;

    public ReloadMessagesCommand(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void action(final SlashCommandInteractionEvent event) {
        final List<SupportMessage> messages = new ArrayList<>(bot.getSupportMessages());
        bot.getSupportMessages().clear();

        try {
            bot.loadMessages();
            event.reply("Reloaded messages!").queue();
        } catch (final Exception e) {
            e.printStackTrace();
            event.reply("Failed to reload messages. Check logs").setEphemeral(true).queue();
            bot.getSupportMessages().addAll(messages);
        }
    }
}
