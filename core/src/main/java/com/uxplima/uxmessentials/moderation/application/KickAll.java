package com.uxplima.uxmessentials.moderation.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * {@code /kickall [reason]}: eject every connected player except the actor and any exempt holder. Emits one
 * {@code event=player_kickall} audit line with the affected count, never one line per kicked player, so a
 * mass disconnect is one greppable record. Returns the number of players kicked.
 *
 * <p>Each kicked player gets their own {@link com.uxplima.uxmessentials.moderation.domain.SanctionAction#KICK}
 * history row, the same row {@code /kick} writes, so {@code /history <player>} shows the kick. A history row
 * always names one target, and a summary row with no target would be invisible to every per-player review.
 *
 * <p>The kicks, the audit line and the staff member's answer come first and never wait on the database: a kickall is
 * an emergency tool, and a slow or failing history write must not leave players online or the command unanswered.
 * The rows are written afterwards as one batch on the async lane, and a failure there is logged.
 */
public final class KickAll {

    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final SanctionHistoryRecorder history;
    private final Scheduler scheduler;
    private final Logger log;

    public KickAll(
            Sanctions sanctions,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            SanctionHistoryRecorder history,
            Scheduler scheduler,
            Logger log) {
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.history = Objects.requireNonNull(history, "history");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Kick every online player but {@code actor} and the exempt; return the number kicked. */
    public int kickAll(PlayerRef actor, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        MessageKey key = ModerationMessageKey.KICK_KICKED;
        List<PlayerRef> kicked = new ArrayList<>();
        for (PlayerRef target : sanctions.onlinePlayers()) {
            if (target.equals(actor) || guard.isExempt(target)) {
                continue;
            }
            sanctions.kick(target, key, notifier.render(target, key, Map.of("reason", reason.orElse(""))));
            kicked.add(target);
        }
        audit.kickedAll(actor, kicked.size(), reason);
        notifier.send(actor, ModerationMessageKey.KICKALL_APPLIED, Map.of("count", Integer.toString(kicked.size())));
        if (!kicked.isEmpty()) {
            List<PlayerRef> rows = List.copyOf(kicked);
            scheduler.async(() -> record(actor, rows, reason));
        }
        return kicked.size();
    }

    private void record(PlayerRef actor, List<PlayerRef> kicked, Optional<String> reason) {
        try {
            history.kicks(actor, kicked, reason);
        } catch (RuntimeException failure) {
            log.error("event=kickall_history_failed actor=" + actor.name() + " kicked=" + kicked.size(), failure);
        }
    }
}
