package com.viaversion.eduard.command;

import com.viaversion.eduard.ViaEduardBot;
import com.viaversion.eduard.command.base.CommandHandler;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jspecify.annotations.Nullable;

public final class EventCommand implements CommandHandler {

    private final ViaEduardBot bot;

    public EventCommand(final ViaEduardBot bot) {
        this.bot = bot;
    }

    @Override
    public void action(final SlashCommandInteractionEvent event) {
        event.deferReply().queue(hook -> bot.getGuild().retrieveScheduledEvents().queue(events -> {
            final ScheduledEvent latest = findLatest(events);
            if (latest == null) {
                hook.sendMessage("There is currently no scheduled event.").queue();
            } else {
                hook.sendMessage(latest.getJumpUrl()).queue();
            }
        }));
    }

    private @Nullable ScheduledEvent findLatest(final List<ScheduledEvent> events) {
        return events.stream()
            .filter(e -> e.getStatus() == ScheduledEvent.Status.ACTIVE || e.getStatus() == ScheduledEvent.Status.SCHEDULED)
            .min(Comparator.comparing(ScheduledEvent::getStartTime))
            .orElse(null);
    }
}
