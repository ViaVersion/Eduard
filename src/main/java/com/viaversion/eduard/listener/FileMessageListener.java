package com.viaversion.eduard.listener;

import com.viaversion.eduard.ViaEduardBot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
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

        final Message message = event.getMessage();
        final boolean hasLogsFile = message.getAttachments().stream().anyMatch(attachment -> {
            final String fileExtension = attachment.getFileExtension();
            return fileExtension != null && (fileExtension.equalsIgnoreCase("txt") || fileExtension.equalsIgnoreCase("log") || fileExtension.equalsIgnoreCase("gz"));
        });
        if (hasLogsFile) {
            if (bot.getNonSupportChannelIds().contains(message.getChannelIdLong())) {
                bot.sendSupportChannelRedirect(message.getChannel(), message.getAuthor());
                return;
            }

            message.getChannel().asTextChannel().sendMessage("Please use https://mclo.gs/ for sending long text, code, or server logs.").queue();
        }
    }
}
