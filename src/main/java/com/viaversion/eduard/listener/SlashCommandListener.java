package com.viaversion.eduard.listener;

import com.viaversion.eduard.ViaEduardBot;
import com.viaversion.eduard.command.base.CommandHandler;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class SlashCommandListener extends ListenerAdapter {
    private final ViaEduardBot bot;

    public SlashCommandListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull final SlashCommandInteractionEvent event) {
        final CommandHandler command = bot.getCommand(event.getName());
        if (command != null) {
            command.action(event);
        }
    }
}
