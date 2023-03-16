package eu.kennytv.viaeduard.command.base;

import eu.kennytv.viaeduard.ViaEduardBot;
import eu.kennytv.viaeduard.util.EmbedMessageUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public abstract class Command {

    protected final ViaEduardBot plugin;
    private final String name;

    protected Command(final String name, final ViaEduardBot plugin) {
        this.name = name;
        this.plugin = plugin;

        plugin.getCommandHandler().registerCommand(this);
    }

    public abstract void action(String[] args, MessageReceivedEvent event);

    public abstract String getHelp();

    public String getName() {
        return name;
    }

    protected void help(final TextChannel textChannel, final Message message) {
        EmbedMessageUtil.sendHelpMessage(textChannel, message, getHelp());
    }

    protected void error(final TextChannel textChannel, final Message message, final String error) {
        EmbedMessageUtil.sendHelpMessage(textChannel, message, error);
    }
}
