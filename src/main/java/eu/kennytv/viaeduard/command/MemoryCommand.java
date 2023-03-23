package eu.kennytv.viaeduard.command;

import eu.kennytv.viaeduard.ViaEduardBot;
import eu.kennytv.viaeduard.command.base.Command;
import eu.kennytv.viaeduard.util.EmbedMessageUtil;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public final class MemoryCommand extends Command {

    public MemoryCommand(final ViaEduardBot plugin) {
        super("memory", plugin);
    }

    @Override
    public void action(final String[] args, final MessageReceivedEvent event) {
        final long totalMemory = Runtime.getRuntime().totalMemory();
        final long freeMemory = Runtime.getRuntime().freeMemory();
        EmbedMessageUtil.sendMessage(event.getChannel(),
                "Total: " + toMegabytes(totalMemory) + "\nFree: " + toMegabytes(freeMemory) + "\nUsed: " + (toMegabytes(totalMemory - freeMemory)));
    }

    private static String toMegabytes(final long bytes) {
        return ((int) (bytes / 1024 / 1024)) + "MB";
    }

    @Override
    public String getHelp() {
        return ".memory";
    }
}
