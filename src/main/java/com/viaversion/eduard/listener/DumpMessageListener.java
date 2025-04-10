package com.viaversion.eduard.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.viaversion.eduard.ViaEduardBot;
import com.viaversion.eduard.util.AthenaHelper;
import com.viaversion.eduard.util.EmbedMessageUtil;
import com.viaversion.eduard.util.GitVersionUtil;
import com.viaversion.eduard.util.Version;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DumpMessageListener extends ListenerAdapter {

    private static final Object O = new Object();
    private static final String[] SUBPLATFORMS = {"ViaBackwards", "ViaRewind", "viarewind-common", "ViaLegacy", "ViaAprilFools"};
    private static final List<String> PROXYPLATFORMS = List.of("Velocity", "BungeeCord", "Waterfall");
    private static final String FORMAT = "Plugin: `%s`\nPlugin version: `%s`";
    private static final String PLATFORM_FORMAT = "Platform: `%s`\nPlatform version: `%s`";
    private final Cache<Long, Object> recentlySent = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.SECONDS).build();
    private final ViaEduardBot bot;
    private final AthenaHelper athena = new AthenaHelper();

    public DumpMessageListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.isFromType(ChannelType.TEXT) || event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }

        String line = event.getMessage().getContentRaw();
        final int index = line.indexOf("https://dump.viaversion.com/");
        if (index == -1) {
            return;
        }

        // Rate limit
        final long authorId = event.getAuthor().getIdLong();
        if (recentlySent.getIfPresent(authorId) != null) {
            return;
        }

        recentlySent.put(authorId, O);

        line = line.substring(index);
        int end = line.indexOf('\n');
        if (end == -1) {
            end = line.indexOf(' ');
        }
        line = line.replace(")", "");

        line = line.substring(0, end == -1 ? line.length() : end);
        if (line.length() == 28) {
            return;
        }

        line = !line.startsWith("raw", 28) ? line.substring(0, 28) + "raw/" + line.substring(28) : line;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(line).openConnection();
            sendRequest(event.getMessage(), connection);
        } catch (final IOException e) {
            System.err.println("Error requesting " + line);
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }
    }

    private void sendRequest(final Message message, final HttpURLConnection connection) throws IOException {
        connection.setRequestProperty("User-Agent", "ViaEduard/");
        connection.setRequestProperty("Content-Type", "text/plain");

        final int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            //System.out.println("Could not paste '" + url + "': " + responseCode);
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
        //final JsonPrimitive data = ViaEduardBot.GSON.fromJson(content, JsonObject.class).getAsJsonPrimitive("data");
        final JsonObject object = ViaEduardBot.GSON.fromJson(content, JsonObject.class);
        final JsonObject versionInfo = object.getAsJsonObject("versionInfo");
        final JsonPrimitive implementationVersion = versionInfo.getAsJsonPrimitive("implementationVersion");
        if (implementationVersion == null) {
            EmbedMessageUtil.sendMessage(message.getChannel(),
                "Your ViaVersion is outdated! Please download the latest stable release from https://viaversion.com/", Color.RED);
            message.addReaction(Emoji.fromUnicode("U+2623")).queue(); // Radioactive
            return;
        }

        final JsonObject platformDump = object.getAsJsonObject("platformDump");
        final String platformName = versionInfo.getAsJsonPrimitive("platformName").getAsString();
        final String platformVersion = versionInfo.getAsJsonPrimitive("platformVersion").getAsString();

        // None sub platforms which need manual handling of the implementation version (ViaFabricPlus, ViaProxy, ViaForge)
        if (platformName.equals("ViaFabricPlus") || platformName.equals("ViaProxy") || platformName.equals("ViaForge")) {
            final String implVersion = platformDump.getAsJsonPrimitive("impl_version").getAsString();

            final CompareResult result = compareToRemote(PLATFORM_FORMAT, platformName, new Version(platformVersion), implVersion);
            EmbedMessageUtil.sendMessage(message.getChannel(), result.message, result.color);
            scanCompareResults(message, Collections.singleton(result));
            return;
        }

        // Special handling for older ViaFabricPlus/ViaProxy versions
        if (platformDump.has("version") && platformDump.has("impl_version")) {
            final String version = platformDump.getAsJsonPrimitive("version").getAsString();
            final String implVersion = platformDump.getAsJsonPrimitive("impl_version").getAsString();
            final boolean mod = implVersion.contains("ViaFabricPlus");

            final CompareResult result = compareToRemote(PLATFORM_FORMAT, mod ? "ViaFabricPlus" : "ViaProxy", new Version(version), implVersion);
            EmbedMessageUtil.sendMessage(message.getChannel(), result.message, result.color);
            scanCompareResults(message, Collections.singleton(result));
            return;
        }

        // Check for the evil
        if (platformName.equals("Yatopia")) {
            message.addReaction(Emoji.fromUnicode("U+1F4A5")).queue(); // Collision/explosion
            EmbedMessageUtil.sendMessage(message.getChannel(), "Yatopia is known to break quite often and is not supported by us. " +
                "Consider using Paper/Purpur for the best performance without a loss in stability.", Color.RED);
        } else if (platformName.equals("Mohist")) {
            EmbedMessageUtil.sendMessage(message.getChannel(), "Hybrid servers like Mohist are not supported ", Color.RED);
        } else if (platformName.equals("Leaf")) {
            EmbedMessageUtil.sendMessage(message.getChannel(), "Leaf is not supported, use Paper instead", Color.RED);
        }

        final JsonArray plugins = platformDump.getAsJsonArray("plugins");
        final boolean isSpigot = platformName.equals("CraftBukkit");
        boolean hasProtocolSupport = false;
        if (plugins != null) {
            for (final JsonElement pluginElement : plugins) {
                if (!pluginElement.isJsonObject()) {
                    continue;
                }

                final JsonPrimitive name = pluginElement.getAsJsonObject().getAsJsonPrimitive("name");
                if (name == null) {
                    continue;
                }

                final String pluginName = name.getAsString();
                if (!pluginName.equals("ProtocolSupport")) {
                    continue;
                }

                hasProtocolSupport = true;
                if (isSpigot) {
                    message.addReaction(Emoji.fromUnicode("U+2757")).queue(); // Exclamation mark
                    EmbedMessageUtil.sendMessage(message.getChannel(), "Via and ProtocolSupport only work together on Paper servers or one of its forks.", Color.RED);
                }
                break;
            }
        }

        // Send data for ViaVersion
        final String pluginVersion = versionInfo.getAsJsonPrimitive("pluginVersion").getAsString();
        final Version version = new Version(pluginVersion);
        final Collection<CompareResult> compareResults = new LinkedList<>();
        final CompareResult compareResult = compareToRemote("ViaVersion", version, implementationVersion.getAsString());
        compareResults.add(compareResult);
        // Append platform data
        final String s = compareResult.message + "\n" + String.format(PLATFORM_FORMAT, platformName, platformVersion);
        EmbedMessageUtil.sendMessage(message.getChannel(), s, compareResult.color);

        // Check for existing subplatforms
        final JsonArray subplatformArray = versionInfo.getAsJsonArray("subPlatforms");

        if (!subplatformArray.isEmpty()) {
            for (final JsonElement element : subplatformArray) {
                final String stringElement = element.getAsString();
                for (final String subplatform : SUBPLATFORMS) {
                    if (!stringElement.contains(subplatform)) {
                        continue;
                    }

                    if (hasProtocolSupport && subplatform.equals("ViaBackwards")) {
                        message.addReaction(Emoji.fromUnicode("U+26A1")).queue(); // Lightning
                        EmbedMessageUtil.sendMessage(message.getChannel(), "Do not use ProtocolSupport and ViaBackwards (+ ViaRewind) together, please remove one of them. Note that ProtocolSupport is not actively updated anymore.", Color.RED);
                    }

                    // Found subplatform, check data
                    compareResults.add(sendSubplatformInfo(subplatform, stringElement, message));
                }
            }

            final JsonPrimitive serverProtocolObject = versionInfo.getAsJsonPrimitive("serverProtocol");
            if (serverProtocolObject.isNumber()) {
                final int serverProtocol = serverProtocolObject.getAsInt();
                if (serverProtocol > 107 // serverProtocol > 1.9
                    && compareResults.stream().anyMatch(r -> r.pluginName.equals("ViaRewind"))
                    && compareResults.stream().noneMatch(r -> r.pluginName.equals("ViaBackwards"))) {
                    EmbedMessageUtil.sendMessage(message.getChannel(), "It looks like you are missing the ViaBackwards plugin. Please install it from <#" + bot.getLinksChannelId() + "> if you need older versions to join, or delete the ViaRewind plugin.", Color.RED);
                }
            }
        }

        try {
            if (PROXYPLATFORMS.stream().anyMatch(platformName::equalsIgnoreCase)) {
                int behind = athena.sendProxyRequest(platformName, URLEncoder.encode(platformVersion, StandardCharsets.UTF_8));
                if (behind > 0) {
                    EmbedMessageUtil.sendMessage(message.getChannel(), "**Your proxy (" + platformName + ") is " + behind + " build(s) behind. Please update it to the latest version**", Color.RED);
                }
            }
        } catch (Exception ignored) {
        }

        // Send message to user to update outdated plugins
        scanCompareResults(message, compareResults);
    }

    private void scanCompareResults(final Message message, final Collection<CompareResult> compareResults) {
        // Add Radioactive reaction for heavily outdated plugins/mods
        if (compareResults.stream().anyMatch(result -> result.status == VersionStatus.RADIOACTIVE)) {
            message.addReaction(Emoji.fromUnicode("U+2623")).queue();
        }

        // Send a message to tell the user to update to latest build from CI (#links)
        if (compareResults.stream().anyMatch(result -> result.status == VersionStatus.RADIOACTIVE || result.status == VersionStatus.OUTDATED)) {
            final List<String> pluginsToUpdate = compareResults.stream().filter(result -> result.status != VersionStatus.UPDATED_CI && result.status != VersionStatus.UNKNOWN)
                .map(result -> result.pluginName)
                .collect(Collectors.toList());
            final StringBuilder updateMessage = new StringBuilder();
            if (pluginsToUpdate.size() > 1) {
                updateMessage.append(String.join(", ", pluginsToUpdate.subList(0, pluginsToUpdate.size() - 1)));
                updateMessage.append(" and ").append(pluginsToUpdate.get(pluginsToUpdate.size() - 1));
            } else {
                updateMessage.append(pluginsToUpdate.get(0));
            }
            message.getChannel().sendMessage(message.getAuthor().getAsMention() + " Please update " + updateMessage + " from <#" + bot.getLinksChannelId() + ">, it may fix your issue.\nIf it doesn't, send a new dump to this channel for a human to help you.").queue();
        }
    }

    private CompareResult sendSubplatformInfo(String platform, final String data, final Message message) {
        final Version version = new Version(data.split("git-" + platform + "-")[1].split(":")[0]);
        if (platform.equalsIgnoreCase("viarewind-common")) {
            platform = "ViaRewind";
        }
        final CompareResult result = compareToRemote(platform, version, data);
        EmbedMessageUtil.sendMessage(message.getChannel(), result.message, result.color);
        return result;
    }

    private CompareResult compareToRemote(final String name, final Version version, final String commitData) {
        return compareToRemote(FORMAT, name, version, commitData);
    }

    private CompareResult compareToRemote(final String format, final String name, final Version version, @Nullable final String commitData) {
        final String versionInfo = String.format(format, name, version);
        final Version latestRelease = bot.getLatestRelease(name);
        if (version.equals(latestRelease)) {
            return new CompareResult(name, versionInfo, Color.GREEN, VersionStatus.UPDATED_RELEASE);
        } else if (version.compareTo(latestRelease) < 0) {
            return new CompareResult(name, "**Your " + name + " is outdated!**\n" + versionInfo, Color.RED, VersionStatus.RADIOACTIVE);
        }

        if (commitData == null) {
            return new CompareResult(name, "**Unknown version. Snapshot or fork?**\n" + versionInfo, Color.MAGENTA, VersionStatus.UNKNOWN);
        }

        final String commit = commitData.split(":")[1];
        String trackedBranch = null;
        int distance = -1;
        for (final String branch : bot.getTrackedBranches()) {
            distance = GitVersionUtil.fetchDistanceFromGitHub("ViaVersion/" + name, branch, commit);
            trackedBranch = branch;
            if (distance >= 0) {
                break;
            }
        }

        // U+2757 exclamation mark
        // U+2714 check mark

        if (distance == -2) {
            return new CompareResult(name, "**Unknown commit. Fork?**\n" + versionInfo, Color.MAGENTA, VersionStatus.UNKNOWN);
        } else if (distance == -1) {
            return new CompareResult(name, "**Error fetching commit data**\n" + versionInfo, Color.GRAY, VersionStatus.UNKNOWN);
        } else if (distance == 0) {
            return new CompareResult(name, "You are even with **" + trackedBranch + "**\n" + versionInfo, Color.CYAN, VersionStatus.UPDATED_CI);
        } else {
            return new CompareResult(name, "**You are " + distance + " commit(s) behind " + trackedBranch + "**\n" + versionInfo, Color.ORANGE, VersionStatus.OUTDATED);
        }
    }

    private record CompareResult(String pluginName, String message, Color color, VersionStatus status) {
    }

    private enum VersionStatus {
        UPDATED_CI, UPDATED_RELEASE, OUTDATED, RADIOACTIVE, UNKNOWN;
    }
}
