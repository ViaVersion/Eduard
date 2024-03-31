package com.viaversion.eduard.command.base;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

@FunctionalInterface
public interface CommandHandler {

    void action(SlashCommandInteractionEvent event);
}
