package eu.kennytv.viaeduard.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

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
        channel.sendMessageEmbeds(getMessage(message, color)).queue();
    }

    public static void sendMessage(final TextChannel channel, final String message) {
        sendMessage(channel, message, Color.GRAY);
    }

    public static void sendHelpMessage(final TextChannel channel, final Message message, final String help) {
        sendTempMessage(channel, message, help, Color.YELLOW);
    }

    public static void sendErrorMessage(final TextChannel channel, final Message message, final String error) {
        sendTempMessage(channel, message, error, Color.RED);
    }

    private static void sendTempMessage(final TextChannel channel, final Message message, final String error, final Color color) {
        final Message msg = channel.sendMessageEmbeds(EmbedMessageUtil.getMessage(error, color)).complete();
        message.delete().queueAfter(5, TimeUnit.SECONDS);
        msg.delete().queueAfter(5, TimeUnit.SECONDS);
    }
}
