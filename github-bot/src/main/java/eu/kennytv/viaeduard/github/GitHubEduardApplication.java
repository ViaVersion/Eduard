package eu.kennytv.viaeduard.github;

import com.google.gson.JsonObject;
import eu.kennytv.viaeduard.common.EduardPlatform;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class GitHubEduardApplication extends EduardPlatform {

    public GitHubEduardApplication() {
        final JsonObject object;
        try {
            object = loadConfig();
        } catch (final IOException e) {
            e.printStackTrace();
            return;
        }


    }

    @Override
    public void loadSettings(final JsonObject object) {
    }
}
