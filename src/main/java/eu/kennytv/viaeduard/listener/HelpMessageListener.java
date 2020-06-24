package eu.kennytv.viaeduard.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import eu.kennytv.viaeduard.ViaEduardBot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class HelpMessageListener extends ListenerAdapter {

    private static final Object O = new Object();
    private final Cache<Long, Object> recentlySent = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.HOURS).build();
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
        if (recentlySent.getIfPresent(id) != null) return;
        // Exempt people with move perm
        if (member.hasPermission(Permission.VOICE_MOVE_OTHERS)) return;

        final Message message = event.getMessage();
        if (!message.getContentRaw().toLowerCase().contains("help")) return;

        message.getTextChannel().sendMessage(bot.getHelpMessage()).queue();
        recentlySent.put(id, O);
    }
}
