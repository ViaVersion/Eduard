package eu.kennytv.viaeduard.util;

import eu.kennytv.viaeduard.ViaEduardBot;

import java.util.List;
import java.util.Set;

public class SupportMessage {

    private final Set<String> commands;
    private final List<Message> messages;

    public SupportMessage(final Set<String> commands, final List<Message> messages) {
        this.commands = commands;
        this.messages = messages;
    }

    public Set<String> getCommands() {
        return commands;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public static class Message {

        private final Channel channel;
        private final String message;

        public Message(final Channel channel, final String message) {
            this.channel = channel;
            this.message = message;
        }

        public Channel getChannel() {
            return channel;
        }

        public String getMessage() {
            return message;
        }
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
            for (Channel channel : values()) {
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
