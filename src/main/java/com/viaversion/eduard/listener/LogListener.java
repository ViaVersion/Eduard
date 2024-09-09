package com.viaversion.eduard.listener;

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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class LogListener extends ListenerAdapter {
    private static final List<String> BLACKLIST = List.of("https://ci.viaversion.com", "https://dump.viaversion.com", "https://github.com", "https://modrinth.com", "https://hangar.papermc.io", "https://viaversion.com");
    private static final Pattern URL_PATTERN = Pattern.compile("\\(?\\bhttps://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]"); // https://blog.codinghorror.com/the-problem-with-urls/
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
        JsonObject athenaOutput = null;

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
            HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .uri(URI.create("https://athena.viaversion.workers.dev/v0/analyze/url"))
                .header("Content-Type", "application/json").header("User-Agent", "Eduard")
                .timeout(Duration.ofSeconds(2))
                .build();
            try {
                athenaOutput = sendRequest(request);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        //check for logs in message if no links are found
        if (match == null && data.length() >= 500) {
            HttpRequest request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(data))
                .uri(URI.create("https://athena.viaversion.workers.dev/v0/analyze/raw"))
                .header("Content-Type", "application/json").header("User-Agent", "Eduard")
                .timeout(Duration.ofSeconds(2))
                .build();
            try {
                athenaOutput = sendRequest(request);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (athenaOutput != null) {
            EmbedBuilder embedBuilder = new EmbedBuilder();
            if (match != null) {
                embedBuilder.setTitle("Log Analysis for " + match, match);
            } else {
                embedBuilder.setTitle("Log Analysis");
            }
            embedBuilder.setColor(5789951);
            embedBuilder.setAuthor("Athena", "https://github.com/Jo0001/Athena");
            embedBuilder.setThumbnail("https://repository-images.githubusercontent.com/828279767/81e69723-3bce-4d7e-92c4-a1dbcbf4cbb4");
            embedBuilder.addField("containsVia", athenaOutput.get("containsVia").getAsString(), false);
            athenaOutput.getAsJsonArray("detections").forEach(detection -> {
                JsonObject detectionObject = detection.getAsJsonObject();
                embedBuilder.addField(detectionObject.get("type").getAsString(), detectionObject.get("message").getAsString(), false);
            });
            bot.getGuild().getChannelById(TextChannel.class, bot.getBotChannelId()).sendMessageEmbeds(embedBuilder.build()).setMessageReference(event.getMessage()).queue();
            event.getMessage().addReaction(Emoji.fromUnicode("U+1FAB5")).queue();
        }
    }

    private JsonObject sendRequest(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return ViaEduardBot.GSON.fromJson(response.body(), JsonObject.class);
        }
        return null;
    }
}
