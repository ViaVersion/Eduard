package eu.kennytv.viaeduard.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.kennytv.viaeduard.common.EduardPlatform;
import eu.kennytv.viaeduard.discord.listener.DumpMessageListener;
import eu.kennytv.viaeduard.discord.listener.HelpMessageListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;

import javax.security.auth.login.LoginException;
import java.io.IOException;

public final class ViaEduardBot extends EduardPlatform {

    public static final Gson GSON = new GsonBuilder().create();
    private JDA jda;
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
            e.printStackTrace();
            return;
        }

        final JDABuilder builder = JDABuilder.createDefault(object.getAsJsonPrimitive("token").getAsString());
        builder.setAutoReconnect(true);
        builder.setStatus(OnlineStatus.ONLINE);

        builder.addEventListeners(new DumpMessageListener(this));
        builder.addEventListeners(new HelpMessageListener(this));

        try {
            jda = builder.build().awaitReady();
        } catch (final LoginException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void loadSettings(final JsonObject object) {
        helpMessage = object.getAsJsonPrimitive("help-message").getAsString();
        privateHelpMessage = object.getAsJsonPrimitive("private-help-message").getAsString();

        final JsonArray trackedBranchesArray = object.getAsJsonArray("tracked-branches");
        trackedBranches = new String[trackedBranchesArray.size()];
        for (int i = 0; i < trackedBranchesArray.size(); i++) {
            trackedBranches[i] = trackedBranchesArray.get(i).getAsString();
        }
    }

    public JDA getJda() {
        return jda;
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
}
