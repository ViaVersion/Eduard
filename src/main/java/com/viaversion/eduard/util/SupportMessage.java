package com.viaversion.eduard.util;

import com.google.common.base.Preconditions;
import com.viaversion.eduard.ViaEduardBot;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

public record SupportMessage(Set<String> commands, List<Message> messages) {

    public SupportMessage {
        Preconditions.checkArgument(!commands.isEmpty(), "commands must not be empty");
        Preconditions.checkArgument(!messages.isEmpty(), "messages must not be empty");
    }

    public void send(final MessageChannelUnion messageChannel, final Channel channel) {
        for (final SupportMessage.Message msg : messages) {
            if (msg.channel() == null || channel == msg.channel()) {
                messageChannel.sendMessage(msg.message()).queue();
                return;
            }
        }
    }

    public record Message(Channel channel, String message) {
    }

    public enum Channel {
        PLUGIN_SUPPORT("plugin"),
        MOD_SUPPORT("mod"),
        PROXY_SUPPORT("proxy");

        private final String name;

        Channel(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static Channel byName(final String name) {
            for (final Channel channel : values()) {
                if (channel.getName().equals(name)) {
                    return channel;
                }
            }
            return null;
        }

        public static Channel byId(final ViaEduardBot bot, final long id) {
            if (id == bot.getPluginSupportChannelId()) {
                return PLUGIN_SUPPORT;
            } else if (id == bot.getModSupportChannelId()) {
                return MOD_SUPPORT;
            } else if (id == bot.getProxySupportChannelId()) {
                return PROXY_SUPPORT;
            } else {
                return null;
            }
        }
    }

}
