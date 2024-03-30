package eu.kennytv.viaeduard.command;

import eu.kennytv.viaeduard.command.base.CommandHandler;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public final class MessageCommand implements CommandHandler {

    @Override
    public void action(final SlashCommandInteractionEvent event) {
        final GuildChannelUnion channel = event.getInteraction().getOption("channel").getAsChannel();
        if (channel.getType() != ChannelType.TEXT) {
            event.reply("Provided channel is not a text channel").setEphemeral(true).queue();
            return;
        }

        event.reply("Sent the message to " + channel.getAsMention() + " :)").setEphemeral(true).queue();

        final String message = event.getInteraction().getOption("message").getAsString();
        channel.asTextChannel().sendMessage(message).queue();
    }
}
