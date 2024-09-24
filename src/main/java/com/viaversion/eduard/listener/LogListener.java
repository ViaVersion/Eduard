package com.viaversion.eduard.listener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.eduard.ViaEduardBot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LogListener extends ListenerAdapter {
    private static final List<String> BLACKLIST = List.of("https://ci.viaversion.com", "https://dump.viaversion.com", "https://github.com", "https://modrinth.com", "https://hangar.papermc.io", "https://viaversion.com");
    private static final Pattern URL_PATTERN = Pattern.compile("\\(?\\bhttps://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]"); // https://blog.codinghorror.com/the-problem-with-urls/
    private static final UnicodeEmoji CHECKMARK = Emoji.fromUnicode("U+2705");
    private static final UnicodeEmoji CROSSMARK = Emoji.fromUnicode("U+274C");
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ViaEduardBot bot;

    public LogListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.isFromType(ChannelType.TEXT) || event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }

        long channelId = event.getGuildChannel().getIdLong();
        if (channelId != bot.getPluginSupportChannelId() && channelId != bot.getBotChannelId()) {
            return;
        }

        String data = event.getMessage().getContentRaw();
        Matcher matcher = URL_PATTERN.matcher(data);
        String match = null;

        while (matcher.find()) {
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

            JsonObject body = new JsonObject();
            body.addProperty("url", match);
            try {
                createOutput(event, match, sendRequest(body.toString(), "url"));
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        // Check for logs in message if no links are found
        if (match == null && data.length() >= 500) {
            try {
                createOutput(event, match, sendRequest(data, "raw"));
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void createOutput(MessageReceivedEvent event, String match, JsonObject athenaData) {
        if (athenaData == null) {
            return;
        }
        JsonArray detections = athenaData.getAsJsonArray("detections");
        if (detections.isEmpty()) {
            return;
        }
        UnicodeEmoji containsVia = athenaData.get("containsVia").getAsBoolean() ? CHECKMARK : CROSSMARK;
        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setColor(5789951)
            .setAuthor("Athena", "https://github.com/Jo0001/Athena")
            .setFooter("Contains ViaVersion " + containsVia.getFormatted());

        if (match != null) {
            embedBuilder.setTitle("Log Analysis for " + match, match);
        } else {
            embedBuilder.setTitle("Log Analysis");
        }

        for (JsonElement detection : detections) {
            JsonObject detectionObject = detection.getAsJsonObject();
            String type = detectionObject.get("type").getAsString();
            String message = detectionObject.get("message").getAsString();
            embedBuilder.addField(type, message, false);
        }
        event.getGuildChannel().sendMessageEmbeds(embedBuilder.build()).setMessageReference(event.getMessage()).queue();
    }

    @Nullable
    private JsonObject sendRequest(String data, String type) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(data))
            .uri(URI.create("https://athena.viaversion.workers.dev/v0/analyze/" + type))
            .header("Content-Type", "application/json").header("User-Agent", "Eduard")
            .timeout(Duration.ofSeconds(2))
            .build();
        return sendRequest(request);
    }

    @Nullable
    private JsonObject sendRequest(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return ViaEduardBot.GSON.fromJson(response.body(), JsonObject.class);
        }
        return null;
    }
}
