package eu.kennytv.viaeduard;

import com.google.gson.*;
import eu.kennytv.viaeduard.command.MemoryCommand;
import eu.kennytv.viaeduard.command.MessageCommand;
import eu.kennytv.viaeduard.command.ScanDumpsCommand;
import eu.kennytv.viaeduard.command.base.CommandHandler;
import eu.kennytv.viaeduard.listener.*;
import eu.kennytv.viaeduard.util.SupportMessage;
import eu.kennytv.viaeduard.util.Version;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ViaEduardBot {

    public static final Gson GSON = new GsonBuilder().create();
    private final Map<String, Version> latestReleases = new HashMap<>();
    private final Map<String, CommandHandler> commands = new HashMap<>();
    private final JDA jda;
    private final Guild guild;
    private long botChannelId;
    private long pluginSupportChannelId;
    private long modSupportChannelId;
    private long proxySupportChannelId;
    private long linksChannelId;
    private Set<Long> nonSupportChannelIds;
    private String[] trackedBranches;
    private String privateHelpMessage;
    private String helpMessage;
    private Set<SupportMessage> supportMessages;

    public static void main(final String[] args) {
        new ViaEduardBot();
    }

    private ViaEduardBot() {
        final JsonObject object;
        try {
            object = loadConfig();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        final String token = object.getAsJsonPrimitive("token").getAsString();
        final JDABuilder builder = JDABuilder.createDefault(token, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES);
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.ONLINE);

        builder.addEventListeners(new SlashCommandListener(this))
                .addEventListeners(new DumpMessageListener(this))
                .addEventListeners(new HelpMessageListener(this))
                .addEventListeners(new FileMessageListener(this))
                .addEventListeners(new SupportMessageListener(this))
                .addEventListeners(new ErrorHelper(this, object.getAsJsonObject("error-helper")));

        try {
            jda = builder.build().awaitReady();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        guild = jda.getGuildById(316206679014244363L);

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
    }

    private void registerCommand(final CommandCreateAction action, final CommandHandler command) {
        action.queue();
        commands.put(action.getName(), command);
    }

    private JsonObject loadConfig() throws IOException {
        final String s = Files.readString(new File("config.json").toPath());
        final JsonObject object = GSON.fromJson(s, JsonObject.class);
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

        pluginSupportChannelId = object.getAsJsonPrimitive("plugin-support-channel").getAsLong();
        modSupportChannelId = object.getAsJsonPrimitive("mod-support-channel").getAsLong();
        proxySupportChannelId = object.getAsJsonPrimitive("proxy-support-channel").getAsLong();
        linksChannelId = object.getAsJsonPrimitive("links-channel").getAsLong();
        nonSupportChannelIds = object.getAsJsonArray("not-support-channels").asList()
                .stream().map(JsonElement::getAsLong).collect(java.util.stream.Collectors.toSet());
        botChannelId = object.getAsJsonPrimitive("bot-channel").getAsLong();
        supportMessages = new HashSet<>();
        for (JsonElement element : object.getAsJsonArray("support-messages")) {
            final JsonObject message = element.getAsJsonObject();
            Set<String> commands;
            if (message.has("commands")) {
                commands = message.getAsJsonArray("commands").asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet());
            } else if (message.has("command")) {
                commands = Collections.singleton(message.getAsJsonPrimitive("command").getAsString());
            } else {
                continue; // bad command
            }
            List<SupportMessage.Message> messages = new ArrayList<>();
            if (message.has("messages")) {
                final JsonObject messagesElement = message.getAsJsonObject("messages");
                int i = 0;
                for (Map.Entry<String, JsonElement> entry : messagesElement.entrySet()) {
                    final SupportMessage.Channel channel = SupportMessage.Channel.byName(entry.getKey());
                    if (channel == null) {
                        continue; // bad command
                    }
                    messages.add(new SupportMessage.Message(channel, entry.getValue().getAsString()));
                }
            } else if (message.has("message")) {
                messages.add(new SupportMessage.Message(null, message.getAsJsonPrimitive("message").getAsString()));
            } else {
                continue; // bad command
            }

            supportMessages.add(new SupportMessage(commands, messages));
        }
        return object;
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

    public Set<SupportMessage> getSupportMessages() {
        return supportMessages;
    }
}
