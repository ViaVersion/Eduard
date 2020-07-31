package eu.kennytv.viaeduard.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.kennytv.viaeduard.common.util.Version;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public abstract class EduardPlatform {

    public static final Gson GSON = new GsonBuilder().create();
    private final Map<String, Version> latestReleases = new HashMap<>();

    protected EduardPlatform() {
    }

    protected JsonObject loadConfig() throws IOException {
        final String s = Files.readString(new File("config.json").toPath());
        final JsonObject object = GSON.fromJson(s, JsonObject.class);
        for (final Map.Entry<String, JsonElement> entry : object.getAsJsonObject("latest-releases").entrySet()) {
            latestReleases.put(entry.getKey(), new Version(entry.getValue().getAsString()));
        }

        loadSettings(object);
        return object;
    }

    protected abstract void loadSettings(JsonObject object);

    @Nullable
    public Version getLatestReleaseOf(final String platform) {
        return latestReleases.get(platform);
    }
}
