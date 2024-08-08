package com.viaversion.eduard.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class GitVersionUtil {
    private static final String COMPARE_URL = "https://api.github.com/repos/%s/compare/%s...%s";
    private static final Gson GSON = new Gson();

    // Yoinked from PaperMC by Techcable <Techcable@outlook.com>
    public static int fetchDistanceFromGitHub(final String repo, final String expectedCommit, final String givenCommit) {
        HttpURLConnection connection = null;
        final String parentCommit;
        try {
            connection = (HttpURLConnection) new URL(String.format(COMPARE_URL, repo, expectedCommit, givenCommit)).openConnection();
            connection.connect();
            if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return -2; // Unknown commit
            }

            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                final JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                final String status = obj.get("status").getAsString();
                final JsonObject mostRecentCommit = obj.getAsJsonObject("base_commit");
                final String commitMessage = mostRecentCommit.getAsJsonObject("commit").getAsJsonPrimitive("message").getAsString();
                if (status.equals("identical")) {
                    return 0;
                }

                if (!commitMessage.startsWith("[ci skip]")) {
                    return status.equals("behind") ? obj.get("behind_by").getAsInt() : -1;
                }

                // Fall through to fetch the parent commit after connection and reader are closed
                final JsonObject parent = mostRecentCommit.getAsJsonArray("parents").get(0).getAsJsonObject();
                parentCommit = parent.getAsJsonPrimitive("sha").getAsString();
            }
        } catch (final Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        // ci-skipped commit, try again with parent commit hash
        return fetchDistanceFromGitHub(repo, parentCommit, givenCommit);
    }
}
