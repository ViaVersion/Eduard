package com.viaversion.eduard.listener;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.viaversion.eduard.ViaEduardBot;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public final class LogListener extends ListenerAdapter {
    String[] blacklist = new String[]{"https://ci.viaversion.com", "https://dump.viaversion.com", "https://github.com", "https://modrinth.com", "https://hangar.papermc.io", "https://viaversion.com"};
    String[] allowedChannels = new String[]{"plugin-support", "bot-test"};
    Pattern urlPattern = Pattern.compile("\\(?\\bhttps://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]");//https://blog.codinghorror.com/the-problem-with-urls/
    private final ViaEduardBot bot;

    public LogListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.isFromType(ChannelType.TEXT) || event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }

        if (Arrays.stream(allowedChannels).noneMatch(event.getGuildChannel().getName()::equals)) {
            return;
        }

        String data = event.getMessage().getContentRaw();
        Matcher matcher = urlPattern.matcher(data);
        StringBuilder output = new StringBuilder();

        while (matcher.find()) {
            int matchStart = matcher.start();
            int matchEnd = matcher.end();
            String match = data.substring(matchStart, matchEnd);

            //cleanup the url
            if (match.startsWith("(") && match.endsWith(")")) {
                match = match.substring(1, match.length() - 2);
            }

            //filter out sites which are 100% not a paste-site
            if (Arrays.stream(blacklist).noneMatch(match::startsWith)) {
                System.out.println(match);
                HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"" + match + "\"}"))
                    .uri(URI.create("https://athena.viaversion.workers.dev/v0/analyze/url"))
                    .header("Content-Type", "application/json").header("User-Agent", "Eduard")
                    .timeout(Duration.ofSeconds(2))
                    .build();
                try {
                    HttpResponse<String> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        final JsonObject object = ViaEduardBot.GSON.fromJson(response.body(), JsonObject.class);
                        JsonArray tags = object.getAsJsonArray("tags");
                        if (!tags.isEmpty()) {
                            output.append(match).append(" ").append(object).append("\n");
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (output.length() > 0) {
            bot.getGuild().getChannelById(TextChannel.class, bot.getBotChannelId()).sendMessage(event.getMessage().getJumpUrl() + output).queue();
            event.getMessage().addReaction(Emoji.fromUnicode("U+1FAB5")).queue();
        }
    }
}
