package eu.kennytv.viaeduard.listener;

import eu.kennytv.viaeduard.ViaEduardBot;
import eu.kennytv.viaeduard.util.SupportMessage;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class SupportMessageListener extends ListenerAdapter {

    private final ViaEduardBot bot;

    public SupportMessageListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.isFromType(ChannelType.TEXT) || event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }
        String line = event.getMessage().getContentRaw();
        if (line.startsWith("?")) {
            line = line.substring(1);
            String[] args = line.split(" ");

            for (SupportMessage message : bot.getSupportMessages()) {
                for (String command : message.getCommands()) {
                    if (args[0].equalsIgnoreCase(command)) {
                        SupportMessage.Channel channel = SupportMessage.Channel.byId(bot, event.getChannel().getIdLong());
                        if (args.length > 1) {
                            // Allow to override channel, e.g. ?dump proxy will show the proxy-support dump message in any channel
                            final SupportMessage.Channel cmdChannel = SupportMessage.Channel.byName(args[1]);
                            if (cmdChannel != null) channel = cmdChannel; // Ignore argument if invalid
                        }
                        // If command isn't executed in one of the support channels, just use the plugin-support message
                        if (channel == null) {
                            event.getChannel().sendMessage(message.getMessages().get(0).getMessage()).queue();
                            return;
                        }
                        for (SupportMessage.Message msg : message.getMessages()) {
                            if (msg.getChannel() == null || channel == msg.getChannel()) {
                                event.getChannel().sendMessage(msg.getMessage()).queue();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
