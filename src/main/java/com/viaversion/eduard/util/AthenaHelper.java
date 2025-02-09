package com.viaversion.eduard.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.eduard.ViaEduardBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AthenaHelper {
    private static final UnicodeEmoji CHECKMARK = Emoji.fromUnicode("U+2705");
    private static final UnicodeEmoji CROSSMARK = Emoji.fromUnicode("U+274C");
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void createOutput(MessageReceivedEvent event, String match, JsonObject athenaData) {
        if (athenaData == null) {
            return;
        }

        JsonArray detections = athenaData.getAsJsonArray("detections");
        if (detections.isEmpty()) {
            return;
        }

        UnicodeEmoji containsVia = athenaData.get("containsVia").getAsBoolean() ? CHECKMARK : CROSSMARK;
        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setColor(0x5858ff)
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

        event.getGuildChannel()
            .sendMessageEmbeds(embedBuilder.build())
            .setMessageReference(event.getMessage()).queue();
    }

    @Nullable
    public JsonObject sendRequest(String data, String type) throws IOException, InterruptedException {
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
