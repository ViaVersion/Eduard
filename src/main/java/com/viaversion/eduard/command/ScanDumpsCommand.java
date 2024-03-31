package com.viaversion.eduard.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.viaversion.eduard.ViaEduardBot;
import com.viaversion.eduard.command.base.CommandHandler;
import com.viaversion.eduard.util.EmbedMessageUtil;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public final class ScanDumpsCommand implements CommandHandler {

    private static final long[] CHANNELS = {316208160232701955L, 1112835241271373966L, 1112837970844721342L};
    private static final int v1_16_4 = 754;
    private static final int OLD_KEY = -1000001;
    private static final int MODERN_KEY = 1000001;
    private final ViaEduardBot bot;

    public ScanDumpsCommand(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void action(final SlashCommandInteractionEvent event) {
        final MessageChannelUnion channel = event.getChannel();
        final int days = event.getInteraction().getOption("days").getAsInt();
        if (days > 1000) {
            event.reply("No").setEphemeral(true).queue();
            return;
        }

        event.reply("Scanning dumps...").setEphemeral(true).queue();

        final long until = (System.currentTimeMillis() / 1000) - TimeUnit.DAYS.toSeconds(days);
        final Set<Long> processed = new HashSet<>();
        final Map<Integer, Integer> javaVersions = new TreeMap<>();
        final Map<Integer, Integer> mcVersions = new TreeMap<>();
        final AtomicInteger counter = new AtomicInteger(CHANNELS.length);
        for (final long channelId : CHANNELS) {
            final TextChannel textChannel = bot.getGuild().getTextChannelById(channelId);
            textChannel.getIterableHistory().forEachRemainingAsync(action -> {
                process(action, processed, javaVersions, mcVersions);
                if (action.getTimeCreated().toEpochSecond() > until) {
                    return true;
                }

                System.out.println("---------------------------------------------------------");
                System.out.println("DONE WITH " + textChannel.getName());
                if (counter.decrementAndGet() == 0) {
                    System.out.println("Fully done.");
                    output(channel, javaVersions, mcVersions);
                }
                return false;
            });
        }
    }

    private void output(final MessageChannelUnion textChannel, final Map<Integer, Integer> javaVersions, final Map<Integer, Integer> mcVersions) {
        int oldJava = 0;
        int modernJava = 0;
        final StringBuilder javaMessageBuilder = new StringBuilder("**Java versions**");
        for (final Map.Entry<Integer, Integer> entry : javaVersions.entrySet()) {
            javaMessageBuilder.append("\nJava ").append(entry.getKey()).append(": ").append(entry.getValue());
            if (entry.getKey() >= 16) {
                modernJava += entry.getValue();
            } else {
                oldJava += entry.getValue();
            }
        }
        EmbedMessageUtil.sendMessage(textChannel, javaMessageBuilder.toString(), Color.CYAN);

        int oldMc = 0;
        int modernMc = 0;
        final StringBuilder mcMessageBuilder = new StringBuilder("**Minecraft versions**");
        final Integer proxyModern = mcVersions.remove(MODERN_KEY);
        if (proxyModern != null) {
            modernMc += proxyModern;
        }

        final Integer proxyOld = mcVersions.remove(OLD_KEY);
        if (proxyOld != null) {
            oldMc += proxyOld;
        }

        mcMessageBuilder.append("\nProxy modern: ").append(proxyModern);
        mcMessageBuilder.append("\nProxy old: ").append(proxyOld);
        for (final Map.Entry<Integer, Integer> entry : mcVersions.entrySet()) {
            mcMessageBuilder.append("\nProtocol ").append(entry.getKey()).append(": ").append(entry.getValue());
            if (entry.getKey() >= v1_16_4) {
                modernMc += entry.getValue();
            } else {
                oldMc += entry.getValue();
            }
        }
        EmbedMessageUtil.sendMessage(textChannel, mcMessageBuilder.toString(), Color.GREEN);

        final String finalMessage = "**Summary**" +
            "\nJava >=16: " + modernJava +
            "\nJava <16: " + oldJava +
            "\n\nMC >=1.16.4: " + modernMc +
            "\nMC <1.16.4: " + oldMc;
        EmbedMessageUtil.sendMessage(textChannel, finalMessage, Color.ORANGE);
    }

    private void process(final Message message, final Set<Long> processed, final Map<Integer, Integer> javaVersions, final Map<Integer, Integer> mcVersions) {
        String line = message.getContentRaw();
        final int index = line.indexOf("https://dump.viaversion.com/");
        if (index == -1) return;

        final User author = message.getAuthor();
        if (!author.isBot() && !author.isSystem() && !processed.add(author.getIdLong())) {
            return;
        }

        line = line.substring(index);
        int end = line.indexOf('\n');
        if (end == -1) {
            end = line.indexOf(' ');
        }

        line = line.substring(0, end == -1 ? line.length() : end);
        if (line.length() == 28) return;

        line = line.substring(0, 28) + "documents/" + line.substring(28);
        try {
            final HttpURLConnection connection = (HttpURLConnection) new URL(line).openConnection();

            connection.setRequestProperty("User-Agent", "ViaEduard/");
            connection.setRequestProperty("Content-Type", "text/plain");

            final int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                System.out.println("Could not paste '" + line + "': " + responseCode);
                return;
            }

            final StringBuilder contentBuilder = new StringBuilder();
            try (final BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String input;
                while ((input = in.readLine()) != null) {
                    contentBuilder.append(input);
                }
            }

            final String content = contentBuilder.toString();
            final JsonPrimitive data = ViaEduardBot.GSON.fromJson(content, JsonObject.class).getAsJsonPrimitive("data");
            final JsonObject object = ViaEduardBot.GSON.fromJson(data.getAsString(), JsonObject.class);
            final JsonObject versionInfo = object.getAsJsonObject("versionInfo");

            final String javaVersion = versionInfo.getAsJsonPrimitive("javaVersion").getAsString();
            try {
                final String[] split = javaVersion.split("\\.", 3);
                int ver = Integer.parseInt(split[0]);
                if (ver == 1) {
                    ver = Integer.parseInt(split[1]);
                }
                javaVersions.compute(ver, (k, v) -> v == null ? 1 : v + 1);

                System.out.println("Adding Java version " + ver);
            } catch (NumberFormatException ignored) {
            }

            final JsonObject configuration = object.getAsJsonObject("configuration");
            if (configuration.has("bungee-servers")) {
                processServers(configuration.getAsJsonObject("bungee-servers"), mcVersions);
            } else if (configuration.has("velocity-servers")) {
                processServers(configuration.getAsJsonObject("velocity-servers"), mcVersions);
            } else {
                final int ver = parseVersion(versionInfo.getAsJsonPrimitive("serverProtocol").getAsString());
                mcVersions.compute(ver, (k, v) -> v == null ? 1 : v + 1);
                System.out.println("Adding MC version " + ver);
            }
        } catch (final Exception e) {
            System.err.println("Error requesting " + line);
            System.err.println("Full: " + message.getContentRaw());
            e.printStackTrace();
        }
    }

    private int parseVersion(final String versionString) {
        try {
            return Integer.parseInt(versionString);
        } catch (final NumberFormatException ignored) {
        }

        final int open = versionString.indexOf('(');
        if (open == -1) {
            System.err.println("Could not parse version: " + versionString);
            return -1;
        }

        return parseVersion(versionString.substring(open + 1, versionString.length() - 1));
    }

    private void processServers(final JsonObject servers, final Map<Integer, Integer> mcVersions) {
        int old = 0;
        int modern = 0;
        for (Map.Entry<String, JsonElement> entry : servers.entrySet()) {
            if (entry.getKey().equals("default")) {
                continue;
            }

            if (entry.getValue().getAsInt() >= v1_16_4) {
                modern++;
            } else {
                old++;
            }
        }

        if (old != 0 || modern != 0) {
            mcVersions.compute(modern >= old ? MODERN_KEY : OLD_KEY, (k, v) -> v == null ? 1 : v + 1);
        }
    }
}
