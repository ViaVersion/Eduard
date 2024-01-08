package eu.kennytv.viaeduard.listener;

import eu.kennytv.viaeduard.ViaEduardBot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class FileMessageListener extends ListenerAdapter {

    private final ViaEduardBot bot;

    public FileMessageListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.isFromType(ChannelType.TEXT) || event.isWebhookMessage()) {
            return;
        }

        final Member member = event.getMember();
        if (member == null) {
            return;
        }

        final long id = event.getAuthor().getIdLong();
        if (id == bot.getJda().getSelfUser().getIdLong()) {
            return;
        }

        final boolean hasLogsFile = event.getMessage().getAttachments().stream().anyMatch(attachment -> {
            final String fileExtension = attachment.getFileExtension();
            return fileExtension != null && (fileExtension.equalsIgnoreCase("txt") || fileExtension.equalsIgnoreCase("log") || fileExtension.equalsIgnoreCase("gz"));
        });
        if (hasLogsFile) {
            event.getMessage().getChannel().asTextChannel().sendMessage("Please use https://mclo.gs/ for sending long text, code, or server logs!").queue();
        }
    }
}
