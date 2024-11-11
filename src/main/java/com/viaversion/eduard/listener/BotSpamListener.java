package com.viaversion.eduard.listener;

import com.viaversion.eduard.ViaEduardBot;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class BotSpamListener extends ListenerAdapter {
    private final ViaEduardBot bot;

    public BotSpamListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.isFromType(ChannelType.TEXT) || event.getGuildChannel().getIdLong() != bot.getBotSpamChannelId()) {
            return;
        }

        if (event.getMember().getRoles().stream().anyMatch(role -> role.getIdLong() == bot.getStaffRoleId())) {
            return;
        }

        event.getMessage().delete().queueAfter(5, TimeUnit.MINUTES);
    }
}
