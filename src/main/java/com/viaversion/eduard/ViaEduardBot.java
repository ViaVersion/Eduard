package com.viaversion.eduard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.eduard.command.MemoryCommand;
import com.viaversion.eduard.command.MessageCommand;
import com.viaversion.eduard.command.ReloadMessagesCommand;
import com.viaversion.eduard.command.ScanDumpsCommand;
import com.viaversion.eduard.command.SetVersionCommand;
import com.viaversion.eduard.command.base.CommandHandler;
import com.viaversion.eduard.listener.DumpMessageListener;
import com.viaversion.eduard.listener.ErrorHelper;
import com.viaversion.eduard.listener.FileMessageListener;
import com.viaversion.eduard.listener.HelpMessageListener;
import com.viaversion.eduard.listener.LogListener;
import com.viaversion.eduard.listener.SlashCommandListener;
import com.viaversion.eduard.listener.SupportMessageListener;
import com.viaversion.eduard.util.SupportMessage;
import com.viaversion.eduard.util.Version;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ViaEduardBot {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Version> latestReleases = new HashMap<>();
    private final Map<String, CommandHandler> commands = new HashMap<>();
    private final List<SupportMessage> supportMessages = new ArrayList<>();
    private final JDA jda;
    private final Guild guild;
    private long serverId;
    private long botChannelId;
    private long pluginSupportChannelId;
    private long modSupportChannelId;
    private long proxySupportChannelId;
    private long linksChannelId;
    private long staffRoleId;
    private Set<Long> nonSupportChannelIds;
    private String messageUrl;
    private String[] trackedBranches;
    private String privateHelpMessage;
    private String helpMessage;

    public static void main(final String[] args) {
        new ViaEduardBot();
    }

    private ViaEduardBot() {
        final JsonObject object;
        try {
            object = loadConfig();
            loadMessages();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        final String token = object.getAsJsonPrimitive("token").getAsString();
        final JDABuilder builder = JDABuilder.createDefault(token, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES);
        builder.disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.ONLINE);

        builder.addEventListeners(new SlashCommandListener(this))
            .addEventListeners(new DumpMessageListener(this))
            .addEventListeners(new HelpMessageListener(this))
            .addEventListeners(new FileMessageListener(this))
            .addEventListeners(new SupportMessageListener(this))
            .addEventListeners(new ErrorHelper(this, object.getAsJsonObject("error-helper")))
            .addEventListeners(new LogListener(this));

        try {
            jda = builder.build().awaitReady();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        guild = jda.getGuildById(serverId);

        registerCommand(guild.upsertCommand("message", "Send a message into a channel")
            .addOption(OptionType.CHANNEL, "channel", "Channel to send the message in", true)
            .addOption(OptionType.STRING, "message", "Message to send", true)
            .setDefaultPermissions(DefaultMemberPermissions.DISABLED), new MessageCommand());
        registerCommand(guild.upsertCommand("scandumps", "Analyze sent dumps")
            .addOption(OptionType.INTEGER, "days", "Days to go back", true)
            .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
            .setGuildOnly(true), new ScanDumpsCommand(this));
        registerCommand(guild.upsertCommand("memory", "Display used and remaining memory of this bot instance")
            .setDefaultPermissions(DefaultMemberPermissions.DISABLED), new MemoryCommand());
        registerCommand(guild.upsertCommand("setversion", "Set the release version for a platform")
            .addOption(OptionType.STRING, "platform", "Via software name", true)
            .addOption(OptionType.STRING, "version", "Release version to set", true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
            .setGuildOnly(true), new SetVersionCommand(this));
        registerCommand(guild.upsertCommand("reloadmessages", "Reload the support messages")
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
            .setGuildOnly(true), new ReloadMessagesCommand(this));
        //registerCommand(guild.upsertCommand("sethelpmessage", "Set the contents of a help message")
        //        .addOption(OptionType.NUMBER, "message", "Message ID to get the contents of", true)
        //        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE))
        //        .setGuildOnly(true), new SetHelpMessageCommand(this));
    }

    private void registerCommand(final CommandCreateAction action, final CommandHandler command) {
        action.queue();
        commands.put(action.getName(), command);
    }

    private JsonObject loadConfig() throws IOException {
        final JsonObject object = loadFile("config.json");
        for (final Map.Entry<String, JsonElement> entry : object.getAsJsonObject("latest-releases").entrySet()) {
            latestReleases.put(entry.getKey(), new Version(entry.getValue().getAsString()));
        }

        helpMessage = object.getAsJsonPrimitive("help-message").getAsString();
        privateHelpMessage = object.getAsJsonPrimitive("private-help-message").getAsString();

        final JsonArray trackedBranchesArray = object.getAsJsonArray("tracked-branches");
        trackedBranches = new String[trackedBranchesArray.size()];
        for (int i = 0; i < trackedBranchesArray.size(); i++) {
            trackedBranches[i] = trackedBranchesArray.get(i).getAsString();
        }

        serverId = object.getAsJsonPrimitive("server-id").getAsLong();
        pluginSupportChannelId = object.getAsJsonPrimitive("plugin-support-channel").getAsLong();
        modSupportChannelId = object.getAsJsonPrimitive("mod-support-channel").getAsLong();
        proxySupportChannelId = object.getAsJsonPrimitive("proxy-support-channel").getAsLong();
        linksChannelId = object.getAsJsonPrimitive("links-channel").getAsLong();
        nonSupportChannelIds = object.getAsJsonArray("not-support-channels").asList()
            .stream().map(JsonElement::getAsLong).collect(java.util.stream.Collectors.toSet());
        staffRoleId = object.getAsJsonPrimitive("staff-role").getAsLong();
        botChannelId = object.getAsJsonPrimitive("bot-channel").getAsLong();
        messageUrl = object.getAsJsonPrimitive("message-url").getAsString();
        return object;
    }

    public void loadMessages() throws IOException {
        final JsonObject object = loadFile(URI.create(messageUrl));
        final JsonArray array = object.getAsJsonArray("messages");
        for (final JsonElement element : array) {
            final JsonObject message = element.getAsJsonObject();
            final Set<String> commands;
            if (message.has("commands")) {
                commands = message.getAsJsonArray("commands").asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet());
            } else if (message.has("command")) {
                commands = Collections.singleton(message.getAsJsonPrimitive("command").getAsString());
            } else {
                throw new IllegalStateException("Invalid support message, missing command");
            }

            final List<SupportMessage.Message> messages = new ArrayList<>();
            if (message.has("messages")) {
                final JsonObject messagesElement = message.getAsJsonObject("messages");
                for (final Map.Entry<String, JsonElement> entry : messagesElement.entrySet()) {
                    final SupportMessage.Channel channel = SupportMessage.Channel.byName(entry.getKey());
                    if (channel == null) {
                        throw new IllegalStateException("Invalid support message, unknown channel: " + entry.getKey());
                    }
                    messages.add(new SupportMessage.Message(channel, entry.getValue().getAsString()));
                }
            } else if (message.has("message")) {
                messages.add(new SupportMessage.Message(null, message.getAsJsonPrimitive("message").getAsString()));
            } else {
                throw new IllegalStateException("Invalid support message, missing content");
            }

            supportMessages.add(new SupportMessage(commands, messages));
        }
    }

    private JsonObject loadFile(final URI uri) throws IOException {
        return GSON.fromJson(IOUtils.toString(uri, StandardCharsets.UTF_8), JsonObject.class);
    }

    private JsonObject loadFile(final String name) throws IOException {
        return GSON.fromJson(Files.newBufferedReader(Path.of(name)), JsonObject.class);
    }

    public void sendSupportChannelRedirect(final MessageChannel channel, final User user) {
        channel.sendMessage("Please use one of the support channels for help " + user.getAsMention()).queue();
    }

    public @Nullable CommandHandler getCommand(final String command) {
        return commands.get(command);
    }

    public JDA getJda() {
        return jda;
    }

    public Guild getGuild() {
        return guild;
    }

    public Version getLatestRelease(final String platform) {
        return latestReleases.get(platform);
    }

    public String[] getTrackedBranches() {
        return trackedBranches;
    }

    public String getHelpMessage() {
        return helpMessage;
    }

    public String getPrivateHelpMessage() {
        return privateHelpMessage;
    }

    public long getStaffRoleId() {
        return staffRoleId;
    }

    public long getBotChannelId() {
        return botChannelId;
    }

    public long getPluginSupportChannelId() {
        return pluginSupportChannelId;
    }

    public long getModSupportChannelId() {
        return modSupportChannelId;
    }

    public long getProxySupportChannelId() {
        return proxySupportChannelId;
    }

    public long getLinksChannelId() {
        return linksChannelId;
    }

    public Set<Long> getNonSupportChannelIds() {
        return nonSupportChannelIds;
    }

    public List<SupportMessage> getSupportMessages() {
        return supportMessages;
    }

    public void setLatestRelease(final String platform, final String version) throws IOException {
        latestReleases.put(platform, new Version(version));

        final JsonObject object = loadFile("config.json");
        object.getAsJsonObject("latest-releases").addProperty(platform, version);
        Files.writeString(Path.of("config.json"), GSON.toJson(object));
    }
}
