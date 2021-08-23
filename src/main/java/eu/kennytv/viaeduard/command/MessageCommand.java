package eu.kennytv.viaeduard.command;

import eu.kennytv.viaeduard.ViaEduardBot;
import eu.kennytv.viaeduard.command.base.Command;
import eu.kennytv.viaeduard.util.EmbedMessageUtil;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.List;

public final class MessageCommand extends Command {

    public MessageCommand(final ViaEduardBot plugin) {
        super("message", plugin);
    }

    @Override
    public void action(final String[] args, final MessageReceivedEvent event) {
        if (args.length < 2) {
            help(event.getTextChannel(), event.getMessage());
            return;
        }

        final List<TextChannel> mentionedChannels = event.getMessage().getMentionedChannels();
        if (mentionedChannels.isEmpty() || mentionedChannels.get(0).getType() != ChannelType.TEXT) {
            error(event.getTextChannel(), event.getMessage(), "Invalid channel");
            return;
        }

        final TextChannel channel = mentionedChannels.get(0);
        EmbedMessageUtil.sendMessage(event.getTextChannel(), "Sent the message to " + channel.getAsMention() + " :)");

        final String[] message = Arrays.copyOfRange(args, 1, args.length);
        channel.sendMessage(String.join(" ", message)).queue();
    }

    @Override
    public String getHelp() {
        return ".message <channel mention> <message> - sends a message into the specified text channel";
    }
}
