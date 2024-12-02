package com.viaversion.eduard.listener;

import com.google.gson.JsonObject;
import com.viaversion.eduard.ViaEduardBot;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.viaversion.eduard.util.AthenaHelper;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class LogListener extends ListenerAdapter {
    private static final List<String> BLACKLIST = List.of("https://ci.viaversion.com", "https://dump.viaversion.com", "https://github.com", "https://modrinth.com", "https://hangar.papermc.io", "https://viaversion.com");
    private static final Pattern URL_PATTERN = Pattern.compile("\\(?\\bhttps://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]"); // https://blog.codinghorror.com/the-problem-with-urls/
    private static final int MAX_URL_CHECKS = 3;
    private final ViaEduardBot bot;
    private final AthenaHelper athena= new AthenaHelper();

    public LogListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.isFromType(ChannelType.TEXT) || event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }

        long channelId = event.getGuildChannel().getIdLong();
        if (channelId != bot.getPluginSupportChannelId() && channelId != bot.getBotChannelId() && channelId != bot.getBotSpamChannelId()) {
            return;
        }

        String data = event.getMessage().getContentRaw();
        Matcher matcher = URL_PATTERN.matcher(data);
        String match = null;

        int urlChecks = 0;
        while (matcher.find() && urlChecks < MAX_URL_CHECKS) {
            int matchStart = matcher.start();
            int matchEnd = matcher.end();
            match = data.substring(matchStart, matchEnd);

            // Clean up the url
            if (match.startsWith("(") && match.endsWith(")")) {
                match = match.substring(1, match.length() - 2);
            }

            // Filter out sites which are 100% not a paste-site
            if (BLACKLIST.stream().anyMatch(match::startsWith)) {
                continue;
            }

            urlChecks++;

            JsonObject body = new JsonObject();
            body.addProperty("url", match);
            try {
                athena.createOutput(event, match, athena.sendRequest(body.toString(), "url"));
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        // Check for logs in message if no links are found
        if (match == null && data.length() >= 250) {
            try {
                athena.createOutput(event, match, athena.sendRequest(data, "raw"));
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


}
