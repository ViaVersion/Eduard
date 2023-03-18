package eu.kennytv.viaeduard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.kennytv.viaeduard.command.MessageCommand;
import eu.kennytv.viaeduard.command.ScanDumpsCommand;
import eu.kennytv.viaeduard.command.base.CommandHandler;
import eu.kennytv.viaeduard.listener.DumpMessageListener;
import eu.kennytv.viaeduard.listener.ErrorHelper;
import eu.kennytv.viaeduard.listener.FileMessageListener;
import eu.kennytv.viaeduard.listener.HelpMessageListener;
import eu.kennytv.viaeduard.util.Version;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;

public final class ViaEduardBot {

    public static final Gson GSON = new GsonBuilder().create();
    private final Map<String, Version> latestReleases = new HashMap<>();
    private final CommandHandler commandHandler;
    private final JDA jda;
    private final Guild guild;
    private long botChannel;
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
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        final String token = object.getAsJsonPrimitive("token").getAsString();
        final JDABuilder builder = JDABuilder.createDefault(token, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES);
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.ONLINE);
        commandHandler = new CommandHandler();

        builder.addEventListeners(commandHandler);
        builder.addEventListeners(new DumpMessageListener(this));
        builder.addEventListeners(new HelpMessageListener(this));
        builder.addEventListeners(new FileMessageListener(this));
        builder.addEventListeners(new ErrorHelper(this, object.getAsJsonObject("error-helper")));

        try {
            jda = builder.build().awaitReady();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }

        guild = jda.getGuildById(316206679014244363L);

        new MessageCommand(this);
        new ScanDumpsCommand(this);
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

        botChannel = object.getAsJsonPrimitive("bot-channel").getAsLong();
        return object;
    }

    public JDA getJda() {
        return jda;
    }

    public Guild getGuild() {
        return guild;
    }

    public CommandHandler getCommandHandler() {
        return commandHandler;
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

    public long getBotChannel() {
        return botChannel;
    }
}
