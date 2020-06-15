package eu.kennytv.viaeduard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.kennytv.viaeduard.listener.MessageListener;
import eu.kennytv.viaeduard.util.Version;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class ViaEduardBot {

    public static final Gson GSON = new GsonBuilder().create();
    private JDA jda;

    private Version latestRelease;
    private Version latestVBRelease;
    private String[] trackedBranches;

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

        builder.addEventListeners(new MessageListener(this));

        try {
            jda = builder.build().awaitReady();
        } catch (final LoginException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private JsonObject loadConfig() throws IOException {
        final String s = Files.readString(new File("config.json").toPath());
        final JsonObject object = GSON.fromJson(s, JsonObject.class);
        latestRelease = new Version(object.getAsJsonPrimitive("latest-release").getAsString());
        latestVBRelease = new Version(object.getAsJsonPrimitive("latest-viabackwards-release").getAsString());

        final JsonArray trackedBranchesArray = object.getAsJsonArray("tracked-branches");
        trackedBranches = new String[trackedBranchesArray.size()];
        for (int i = 0; i < trackedBranchesArray.size(); i++) {
            trackedBranches[i] = trackedBranchesArray.get(i).getAsString();
        }
        return object;
    }

    public JDA getJda() {
        return jda;
    }

    public Version getLatestRelease() {
        return latestRelease;
    }

    public Version getLatestVBRelease() {
        return latestVBRelease;
    }

    public String[] getTrackedBranches() {
        return trackedBranches;
    }
}
