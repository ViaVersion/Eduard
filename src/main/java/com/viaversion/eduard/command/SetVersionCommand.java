package com.viaversion.eduard.command;

import com.viaversion.eduard.ViaEduardBot;
import com.viaversion.eduard.command.base.CommandHandler;
import com.viaversion.eduard.util.Version;
import java.io.IOException;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public final class SetVersionCommand implements CommandHandler {

    private final ViaEduardBot bot;

    public SetVersionCommand(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void action(final SlashCommandInteractionEvent event) {
        final String platform = event.getInteraction().getOption("platform").getAsString();
        if (bot.getLatestRelease(platform) == null) {
            event.reply("Unknown platform").setEphemeral(true).queue();
            return;
        }

        final String version = event.getInteraction().getOption("version").getAsString();
        if (!Version.VIA_RELEASE_PATTERN.matcher(version).matches()) {
            event.reply("Invalid version format. Use the format `x.y.z`").setEphemeral(true).queue();
            return;
        }

        try {
            bot.setLatestRelease(platform, version);
            event.reply("Updated version of " + platform + " to " + version).queue();
        } catch (final IOException e) {
            e.printStackTrace();
            event.reply("Failed to set version. Check logs").setEphemeral(true).queue();
        }
    }
}
