package eu.kennytv.viaeduard.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import eu.kennytv.viaeduard.ViaEduardBot;
import eu.kennytv.viaeduard.util.EmbedMessageUtil;
import eu.kennytv.viaeduard.util.GitVersionUtil;
import eu.kennytv.viaeduard.util.Version;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
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

public final class DumpMessageListener extends ListenerAdapter {

    private static final Object O = new Object();
    private static final String[] SUBPLATFORMS = {"ViaBackwards", "ViaRewind"};
    private static final String FORMAT = "Plugin: `%s`\nPlugin version: `%s`";
    private static final String PLATFORM_FORMAT = "\nPlatform: `%s`\nPlatform version: `%s`";
    private final Cache<Long, Object> recentlySent = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.SECONDS).build();
    private final ViaEduardBot bot;

    public DumpMessageListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(@NotNull final MessageReceivedEvent event) {
        if (!event.isFromGuild() || !event.isFromType(ChannelType.TEXT) || event.isWebhookMessage()) {
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

        line = line.substring(0, end == -1 ? line.length() : end);
        if (line.length() == 28) {
            return;
        }

        line = line.substring(0, 28) + "documents/" + line.substring(28);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(line).openConnection();
            sendRequest(event.getMessage(), line, connection);
        } catch (final IOException e) {
            System.err.println("Error requesting " + line);
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }
    }

    private void sendRequest(final Message message, final String url, final HttpURLConnection connection) throws IOException {
        connection.setRequestProperty("User-Agent", "ViaEduard/");
        connection.setRequestProperty("Content-Type", "text/plain");

        final int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            System.out.println("Could not paste '" + url + "': " + responseCode);
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
        final JsonPrimitive implementationVersion = versionInfo.getAsJsonPrimitive("implementationVersion");
        if (implementationVersion == null) {
            EmbedMessageUtil.sendMessage(message.getChannel(),
                    "Your ViaVersion is outdated! Please download the latest stable release from https://viaversion.com/", Color.RED);
            message.addReaction(Emoji.fromUnicode("U+2623")).queue(); // Radioactive
            return;
        }

        // Check for the evil
        final JsonObject platformDump = object.getAsJsonObject("platformDump");
        final JsonArray plugins = platformDump.getAsJsonArray("plugins");
        final String platformName = versionInfo.getAsJsonPrimitive("platformName").getAsString();
        final boolean isSpigot = platformName.equals("CraftBukkit");
        if (platformName.equals("Yatopia")) {
            message.addReaction(Emoji.fromUnicode("U+1F4A5")).queue(); // Collision/explosion
            EmbedMessageUtil.sendMessage(message.getChannel(), "Yatopia is known to break quite often and is not supported by us. " +
                    "Consider using Paper/Purpur for the best performance without a loss in stability.", Color.RED);
        }

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
                    EmbedMessageUtil.sendMessage(message.getChannel(), "Via and ProtocolSupport only work together on Paper servers or one of it's forks!", Color.RED);
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
        final String s = compareResult.message + String.format(PLATFORM_FORMAT, platformName,
                versionInfo.getAsJsonPrimitive("platformVersion").getAsString());
        EmbedMessageUtil.sendMessage(message.getChannel(), s, compareResult.color);

        // Check for ViaBackwards/ViaRewind
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
                        EmbedMessageUtil.sendMessage(message.getChannel(), "Do not use ProtocolSupport and ViaBackwards (+ ViaRewind) together, Please remove one of them.", Color.RED);
                    }

                    // Found subplatform, check data
                    compareResults.add(sendSubplatformInfo(subplatform, stringElement, message));
                }
            }

            final int serverProtocol = versionInfo.getAsJsonPrimitive("serverProtocol").getAsInt();
            if (serverProtocol > 107 // serverProtocol > 1.9
                    && compareResults.stream().anyMatch(r -> r.pluginName.equals("ViaRewind"))
                    && compareResults.stream().noneMatch(r -> r.pluginName.equals("ViaBackwards"))) {
                EmbedMessageUtil.sendMessage(message.getChannel(), "It looks like you are missing the ViaBackwards plugin. Please install it from <#698284788074938388> if you need older versions to join, or delete the ViaRewind plugin.", Color.RED);
            }
        }

        // Add Radioactive reaction for heavily outdated plugins
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
                updateMessage.append("plugins ").append(String.join(", ", pluginsToUpdate.subList(0, pluginsToUpdate.size() - 1)));
                updateMessage.append(" and ").append(pluginsToUpdate.get(pluginsToUpdate.size() - 1));
            } else {
                updateMessage.append("plugin ").append(pluginsToUpdate.get(0));
            }
            message.getChannel().sendMessage(message.getAuthor().getAsMention() + " Please update " + updateMessage + " from <#698284788074938388>, it may fix your issue.\nIf it doesn't, send a new dump to this channel for a human to help you.").queue();
        }
    }

    private CompareResult sendSubplatformInfo(final String platform, final String data, final Message message) {
        final Version version = new Version(data.split("git-" + platform + "-")[1].split(":")[0]);
        final CompareResult result = compareToRemote(platform, version, data);
        EmbedMessageUtil.sendMessage(message.getChannel(), result.message, result.color);
        return result;
    }

    private CompareResult compareToRemote(final String pluginName, final Version version, final String commitData) {
        final String versionInfo = String.format(FORMAT, pluginName, version);
        final Version latestRelease = bot.getLatestRelease(pluginName);
        if (version.equals(latestRelease)) {
            return new CompareResult(pluginName, versionInfo, Color.GREEN, VersionStatus.UPDATED_RELEASE);
        } else if (version.compareTo(latestRelease) < 0) {
            return new CompareResult(pluginName, "**Your " + pluginName + " is outdated!**\n" + versionInfo, Color.RED, VersionStatus.RADIOACTIVE);
        }

        final String commit = commitData.split(":")[1];
        String trackedBranch = null;
        int distance = -1;
        for (final String branch : bot.getTrackedBranches()) {
            distance = GitVersionUtil.fetchDistanceFromGitHub("ViaVersion/" + pluginName, branch, commit);
            trackedBranch = branch;
            if (distance >= 0) {
                break;
            }
        }

        // U+2757 exclamation mark
        // U+2714 check mark

        // Commit does not exist / http error
        if (distance == -1) {
            return new CompareResult(pluginName, "**Error fetching commit data**\n" + versionInfo, Color.GRAY, VersionStatus.UNKNOWN);
        } else if (distance == 0) {
            return new CompareResult(pluginName, "You are even with **" + trackedBranch + "**\n" + versionInfo, Color.CYAN, VersionStatus.UPDATED_CI);
        } else {
            return new CompareResult(pluginName, "**You are " + distance + " commit(s) behind " + trackedBranch + "**\n" + versionInfo, Color.ORANGE, VersionStatus.OUTDATED);
        }
    }

    private static class CompareResult {
        private final String pluginName;
        private final String message;
        private final Color color;
        private final VersionStatus status;

        public CompareResult(final String pluginName, final String message, final Color color, final VersionStatus status) {
            this.pluginName = pluginName;
            this.message = message;
            this.color = color;
            this.status = status;
        }
    }

    private enum VersionStatus {
        UPDATED_CI, UPDATED_RELEASE, OUTDATED, RADIOACTIVE, UNKNOWN;
    }
}
