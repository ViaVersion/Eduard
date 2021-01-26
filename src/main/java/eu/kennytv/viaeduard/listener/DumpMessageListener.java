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
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

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
    public void onGuildMessageReceived(@NotNull final GuildMessageReceivedEvent event) {
        String line = event.getMessage().getContentRaw();
        final int index = line.indexOf("https://dump.viaversion.com/");
        if (index == -1) return;

        // Rate limit
        final long authorId = event.getAuthor().getIdLong();
        if (recentlySent.getIfPresent(authorId) != null) return;

        recentlySent.put(authorId, O);

        line = line.substring(index);
        int end = line.indexOf('\n');
        if (end == -1) {
            end = line.indexOf(' ');
        }

        line = line.substring(0, end == -1 ? line.length() : end);
        if (line.length() == 28) return;

        line = line.substring(0, 28) + "documents/" + line.substring(28);
        try {
            sendRequest(event.getMessage(), line);
        } catch (final IOException e) {
            System.err.println("Error requesting " + line);
            e.printStackTrace();
        }
    }

    private void sendRequest(final Message message, final String url) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

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
            EmbedMessageUtil.sendMessage(message.getTextChannel(),
                    "Your ViaVersion is outdated! Please download the latest stable release from https://viaversion.com/", Color.RED);
            message.addReaction("U+2623").queue(); // Radioactive
            return;
        }

        boolean radioactive = false;

        // Check for the evil
        final JsonObject platformDump = object.getAsJsonObject("platformDump");
        final JsonArray plugins = platformDump.getAsJsonArray("plugins");
        final String platformName = versionInfo.getAsJsonPrimitive("platformName").getAsString();
        final boolean isSpigot = platformName.equals("CraftBukkit");
        boolean hasProtocolSupport = false;
        for (final JsonElement pluginElement : plugins) {
            if (!pluginElement.isJsonObject()) continue;

            final JsonPrimitive name = pluginElement.getAsJsonObject().getAsJsonPrimitive("name");
            if (name == null) continue;

            final String pluginName = name.getAsString();
            if (pluginName.equals("ProtocolSupport")) {
                hasProtocolSupport = true;
                if (isSpigot) {
                    message.addReaction("U+2757").queue(); // Exclamation mark
                    EmbedMessageUtil.sendMessage(message.getTextChannel(), "Via and ProtocolSupport only work together on Paper servers or one of its forks.", Color.RED);
                }
            }
        }

        // Send data for ViaVersion
        final String pluginVersion = versionInfo.getAsJsonPrimitive("pluginVersion").getAsString();
        final Version version = new Version(pluginVersion);
        final ImmutablePair<String, Color> pair = compareToRemote("ViaVersion", version, implementationVersion.getAsString());
        // Append platform data
        final String s = pair.left + String.format(PLATFORM_FORMAT, platformName,
                versionInfo.getAsJsonPrimitive("platformVersion").getAsString());
        EmbedMessageUtil.sendMessage(message.getTextChannel(), s, pair.right);
        if (pair.right == Color.RED) {
            message.addReaction("U+2623").queue(); // Radioactive
            radioactive = true;
        }

        // Check for ViaBackwards/ViaRewind
        final JsonArray subplatformArray = versionInfo.getAsJsonArray("subPlatforms");
        if (subplatformArray.size() == 0) return;

        for (final JsonElement element : subplatformArray) {
            final String stringElement = element.getAsString();
            for (final String subplatform : SUBPLATFORMS) {
                if (stringElement.contains(subplatform)) {
                    if (hasProtocolSupport && subplatform.equals("ViaBackwards")) {
                        message.addReaction("U+26A1").queue(); // Lightning
                        EmbedMessageUtil.sendMessage(message.getTextChannel(), "Do not use ProtocolSupport and ViaBackwards together, please remove one of them.", Color.RED);
                    }

                    // Found subplatform, check data
                    radioactive |= sendSubplatformInfo(subplatform, stringElement, message, radioactive);
                }
            }
        }
    }

    /**
     * @return true if the subplatform is heavily outdated
     */
    private boolean sendSubplatformInfo(final String platform, final String data, final Message message, final boolean isRadioactive) {
        final Version version = new Version(data.split("git-" + platform + "-")[1].split(":")[0]);
        final ImmutablePair<String, Color> pair = compareToRemote(platform, version, data);
        EmbedMessageUtil.sendMessage(message.getTextChannel(), pair.left, pair.right);
        if (!isRadioactive && pair.right == Color.RED) {
            // Add Radioactive if not already done
            message.addReaction("U+2623").queue();
            return true;
        }
        return false;
    }

    private ImmutablePair<String, Color> compareToRemote(final String pluginName, final Version version, final String commitData) {
        final String versionInfo = String.format(FORMAT, pluginName, version);
        final Version latestRelease = bot.getLatestRelease(pluginName);
        if (version.equals(latestRelease)) {
            return new ImmutablePair<>(versionInfo, Color.GREEN);
        } else if (version.compareTo(latestRelease) == -1) {
            return new ImmutablePair<>("**Your " + pluginName + " is outdated!**\n" + versionInfo, Color.RED);
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
            return new ImmutablePair<>("**Error fetching commit data**\n" + versionInfo, Color.GRAY);
        } else if (distance == 0) {
            return new ImmutablePair<>("You are even with **" + trackedBranch + "**\n" + versionInfo, Color.CYAN);
        } else {
            return new ImmutablePair<>("**You are " + distance + " commit(s) behind " + trackedBranch + "**\n" + versionInfo, Color.ORANGE);
        }
    }
}
