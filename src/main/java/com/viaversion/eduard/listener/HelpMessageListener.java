package com.viaversion.eduard.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.viaversion.eduard.ViaEduardBot;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class HelpMessageListener extends ListenerAdapter {

    private static final List<String> HELLOS = List.of("hi", "hello", "hey");
    private static final List<String> THANKSES = List.of("thx for", "thanks for", "thank you for");
    private static final Object O = new Object();
    private final Cache<Long, Object> recentlySent = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.HOURS).build();
    private final Cache<Long, Object> recentlySentPrivate = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();
    private final Cache<Long, Object> hellos = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();
    private final ViaEduardBot bot;

    public HelpMessageListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (event.isWebhookMessage()) {
            return;
        }

        if (event.getChannel() instanceof PrivateChannel) {
            final long id = event.getAuthor().getIdLong();
            if (id == bot.getJda().getSelfUser().getIdLong()) {
                return;
            }

            if (recentlySentPrivate.getIfPresent(id) != null) {
                return;
            }

            event.getChannel().sendMessage(bot.getPrivateHelpMessage()).queue();
            recentlySentPrivate.put(id, O);
            return;
        }

        if (!event.isFromType(ChannelType.TEXT)) {
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

        // Hello check
        final Message message = event.getMessage();
        final String lowerCaseMessage = message.getContentRaw().toLowerCase();
        if (lowerCaseMessage.contains("eduard") && HELLOS.stream().anyMatch(lowerCaseMessage::contains)) {
            if (hellos.getIfPresent(id) == null) {
                hellos.put(id, O);
                event.getChannel().sendMessage("Hello!").queue();
            }
            return;
        }

        // Help check
        if (!lowerCaseMessage.contains("help") || THANKSES.stream().anyMatch(lowerCaseMessage::contains)) return;
        if (recentlySent.getIfPresent(id) != null) return;
        if (member.getRoles().stream().anyMatch(role -> role.getIdLong() == bot.getStaffRoleId())) return;

        if (bot.getNonSupportChannelIds().contains(message.getChannelIdLong())) {
            bot.sendSupportChannelRedirect(message.getChannel(), message.getAuthor());
            return;
        }

        event.getChannel().asTextChannel().sendMessage(bot.getHelpMessage()).queue();
        recentlySent.put(id, O);
    }
}
