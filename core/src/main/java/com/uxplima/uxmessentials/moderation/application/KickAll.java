package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
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
 */
public final class KickAll {

    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final SanctionHistoryRecorder history;

    public KickAll(
            Sanctions sanctions,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            SanctionHistoryRecorder history) {
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.history = Objects.requireNonNull(history, "history");
    }

    /** Kick every online player but {@code actor} and the exempt; return the number kicked. */
    public int kickAll(PlayerRef actor, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        MessageKey key = ModerationMessageKey.KICK_KICKED;
        int affected = 0;
        for (PlayerRef target : sanctions.onlinePlayers()) {
            if (target.equals(actor) || guard.isExempt(target)) {
                continue;
            }
            sanctions.kick(target, key, notifier.render(target, key, Map.of("reason", reason.orElse(""))));
            history.kick(actor, target, reason);
            affected++;
        }
        audit.kickedAll(actor, affected, reason);
        notifier.send(actor, ModerationMessageKey.KICKALL_APPLIED, Map.of("count", Integer.toString(affected)));
        return affected;
    }
}
