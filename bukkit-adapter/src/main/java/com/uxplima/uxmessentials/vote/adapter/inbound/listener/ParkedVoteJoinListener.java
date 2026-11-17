package com.uxplima.uxmessentials.vote.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.BindParkedVotes;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import org.jspecify.annotations.NullMarked;

/**
 * Credits a joining player with the votes a vote site sent for their name before any account answered to it
 * ({@link BindParkedVotes}). A Bedrock player also claims the votes cast for their gamertag without the bridge's
 * prefix. The database work runs on the async lane; the player is online by then, so the votes are rewarded as any
 * vote of an online player is.
 */
@NullMarked
public final class ParkedVoteJoinListener implements Listener {

    private final BindParkedVotes bind;
    private final BedrockDetector bedrock;
    private final Scheduler scheduler;
    private final Logger log;

    public ParkedVoteJoinListener(BindParkedVotes bind, BedrockDetector bedrock, Scheduler scheduler, Logger log) {
        this.bind = Objects.requireNonNull(bind, "bind");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef joiner = BukkitRefs.toRef(event.getPlayer());
        String prefix = bedrock.isBedrock(joiner.uuid()) ? bedrock.namePrefix() : "";
        scheduler.async(() -> {
            int credited = bind.bind(joiner, prefix);
            if (credited > 0) {
                log.info("event=vote_parked_credited player={} votes={}", joiner.uuid(), credited);
            }
        });
    }
}
