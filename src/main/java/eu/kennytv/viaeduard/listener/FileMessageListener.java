package eu.kennytv.viaeduard.listener;

import eu.kennytv.viaeduard.ViaEduardBot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class FileMessageListener extends ListenerAdapter {

    private final ViaEduardBot bot;

    public FileMessageListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onGuildMessageReceived(@NotNull final GuildMessageReceivedEvent event) {
        final Member member = event.getMember();
        if (member == null) return; // Webhook

        final long id = event.getAuthor().getIdLong();
        if (id == bot.getJda().getSelfUser().getIdLong()) return;

        final boolean hasLogsFile = event.getMessage().getAttachments().stream().anyMatch(attachment -> {
            final String fileExtension = attachment.getFileExtension();
            return fileExtension != null && (fileExtension.equalsIgnoreCase("txt") || fileExtension.equalsIgnoreCase("log"));
        });
        if (hasLogsFile) {
            event.getMessage().getTextChannel().sendMessage("Please use https://paste.gg/ for sending long text, code, or server logs!").queue();
        }
    }
}
