package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * {@code /kick <player> [reason]}: eject one connected player. The exempt target is refused (audit-logged);
 * any other online target is kicked with the rendered reason. A kick mutates no DB state. It is a live
 * disconnect, but it is still audit-logged because it is an admin action.
 *
 * <p>Unless the kick is silent ({@code /kick -s}), the staff broadcast announces it to everyone holding the
 * moderation broadcast node; a silent kick suppresses only that announcement, leaving the actor confirmation
 * and the target's kick screen intact. A successful kick is recorded in the append-only sanction history so it
 * surfaces in {@code /history} and {@code /staffhistory}: a kick carries no expiry.
 *
 * <p>A target connected to another backend is refused with that backend's name before anything is written: this
 * backend cannot disconnect them, and a kick recorded and announced without happening would mislead staff. A target
 * who is on no server at all is refused as offline for the same reason.
 */
public final class Kick {

    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final SanctionHistoryRecorder history;
    private final SanctionBroadcast broadcast;
    private final NetworkPresence presence;
    private final LiveTargetCheck live;

    public Kick(
            Sanctions sanctions,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            SanctionHistoryRecorder history,
            SanctionBroadcast broadcast,
            NetworkPresence presence) {
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.history = Objects.requireNonNull(history, "history");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.live = new LiveTargetCheck(presence, this.notifier);
    }

    /** Kick {@code target} with an optional reason, or refuse an exempt, offline or elsewhere target. */
    public Result<Unit, ModerationError> kick(
            PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {
        return kick(actor, actor, target, reason, silent);
    }

    /**
     * {@link #kick(PlayerRef, PlayerRef, Optional, boolean)} issued under {@code actor} while what the use case says
     * about it goes to {@code told}: the warn escalation kicks under the server rule and tells the staff member whose
     * warning set it off.
     */
    Result<Unit, ModerationError> kick(
            PlayerRef actor, PlayerRef told, PlayerRef target, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(told, "told");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (live.refusedElsewhere(told, target)) {
            return Result.err(ModerationError.TARGET_ELSEWHERE);
        }
        if (!presence.isOnline(target.uuid())) {
            notifier.send(told, ModerationMessageKey.KICK_OFFLINE, Map.of("player", target.name()));
            return Result.err(ModerationError.TARGET_OFFLINE);
        }
        if (guard.isExempt(target)) {
            audit.kicked(actor, target, false, reason);
            notifier.send(told, ModerationError.TARGET_EXEMPT.messageKey());
            return Result.err(ModerationError.TARGET_EXEMPT);
        }
        MessageKey key = ModerationMessageKey.KICK_KICKED;
        sanctions.kick(target, key, notifier.render(target, key, Map.of("reason", reason.orElse(""))));
        history.kick(actor, target, reason);
        audit.kicked(actor, target, true, reason);
        notifier.send(told, ModerationMessageKey.KICK_APPLIED, Map.of("player", target.name()));
        if (!silent) {
            broadcast.announce(
                    ModerationMessageKey.MOD_BROADCAST_KICK,
                    Map.of("actor", actor.name(), "target", target.name(), "reason", reason.orElse("")));
        }
        return Result.ok();
    }
}
