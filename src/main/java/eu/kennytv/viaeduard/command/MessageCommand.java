package eu.kennytv.viaeduard.command;

import eu.kennytv.viaeduard.ViaEduardBot;
import eu.kennytv.viaeduard.command.base.Command;
import eu.kennytv.viaeduard.util.EmbedMessageUtil;
import java.util.Arrays;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public final class MessageCommand extends Command {

    public MessageCommand(final ViaEduardBot plugin) {
        super("message", plugin);
    }

    @Override
    public void action(final String[] args, final MessageReceivedEvent event) {
        if (args.length < 2) {
            help(event.getChannel().asTextChannel(), event.getMessage());
            return;
        }

        final List<TextChannel> mentionedChannels = event.getMessage().getMentions().getChannels(TextChannel.class);
        if (mentionedChannels.isEmpty() || mentionedChannels.get(0).getType() != ChannelType.TEXT) {
            error(event.getChannel().asTextChannel(), event.getMessage(), "Invalid channel");
            return;
        }

        final TextChannel channel = mentionedChannels.get(0);
        EmbedMessageUtil.sendMessage(event.getChannel().asTextChannel(), "Sent the message to " + channel.getAsMention() + " :)");

        final String[] message = Arrays.copyOfRange(args, 1, args.length);
        channel.sendMessage(String.join(" ", message)).queue();
    }

    @Override
    public String getHelp() {
        return ".message <channel mention> <message> - sends a message into the specified text channel";
    }
}
