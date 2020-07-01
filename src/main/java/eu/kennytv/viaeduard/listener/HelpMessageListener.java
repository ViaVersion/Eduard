package eu.kennytv.viaeduard.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import eu.kennytv.viaeduard.ViaEduardBot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class HelpMessageListener extends ListenerAdapter {

    private static final String[] HELLOS = {"hi", "hello", "hey"};
    private static final Object O = new Object();
    private final Cache<Long, Object> recentlySent = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.HOURS).build();
    private final Cache<Long, Object> recentlySentPrivate = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();
    private final Cache<Long, Object> hellos = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();
    private final ViaEduardBot bot;

    public HelpMessageListener(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void onGuildMessageReceived(@NotNull final GuildMessageReceivedEvent event) {
        final Member member = event.getMember();
        if (member == null) return; // Webhook

        final long id = event.getAuthor().getIdLong();
        if (id == bot.getJda().getSelfUser().getIdLong()) return;

        // Hello check
        final Message message = event.getMessage();
        final String lowerCaseMessage = message.getContentRaw().toLowerCase();
        if (lowerCaseMessage.contains("eduard") && Arrays.stream(HELLOS).anyMatch(lowerCaseMessage::contains)) {
            if (hellos.getIfPresent(id) == null) {
                hellos.put(id, O);
                event.getChannel().sendMessage("Hello!").queue();
            }
            return;
        }

        // Help check
        if (!lowerCaseMessage.contains("help")) return;
        if (recentlySent.getIfPresent(id) != null) return;
        if (member.hasPermission(Permission.VOICE_MOVE_OTHERS)) return;

        message.getTextChannel().sendMessage(bot.getHelpMessage()).queue();
        recentlySent.put(id, O);
    }

    @Override
    public void onPrivateMessageReceived(@NotNull final PrivateMessageReceivedEvent event) {
        final long id = event.getAuthor().getIdLong();
        if (id == bot.getJda().getSelfUser().getIdLong()) return;
        if (recentlySentPrivate.getIfPresent(id) != null) return;

        event.getChannel().sendMessage(bot.getPrivateHelpMessage()).queue();
        recentlySentPrivate.put(id, O);
    }
}
