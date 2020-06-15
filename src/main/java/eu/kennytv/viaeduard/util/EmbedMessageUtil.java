package eu.kennytv.viaeduard.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public final class EmbedMessageUtil {

    private static final EmbedBuilder EMBED_BUILDER = new EmbedBuilder();

    public static MessageEmbed getMessage(final String message, final Color color) {
        return EMBED_BUILDER.setDescription(message).setColor(color).build();
    }

    public static MessageEmbed getMessage(final String message) {
        return getMessage(message, Color.GRAY);
    }

    public static void sendMessage(final TextChannel channel, final String message, final Color color) {
        channel.sendMessage(getMessage(message, color)).queue();
    }

    public static void sendMessage(final TextChannel channel, final String message) {
        sendMessage(channel, message, Color.GRAY);
    }
}
