package eu.kennytv.viaeduard.command.base;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class CommandHandler extends ListenerAdapter {

    private final Map<String, Command> commands = new HashMap<>();

    @Override
    public void onMessageReceived(final MessageReceivedEvent event) {
        final String content = event.getMessage().getContentRaw();
        if (content.length() < 2 || content.charAt(0) != '.') return;
        if (event.getAuthor().getId().equals(event.getJDA().getSelfUser().getId())) return;

        final String[] split = content.substring(1).split(" ");
        final String cmd = split[0].toLowerCase();
        final Command command = commands.get(cmd);
        if (command == null) return;

        final String[] args = Arrays.copyOfRange(split, 1, split.length);
        if (event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            System.out.println(event.getMember().getEffectiveName() + " used command: " + content);
            command.action(args, event);
        }
    }

    public void registerCommand(final Command command) {
        commands.put(command.getName(), command);
    }
}
