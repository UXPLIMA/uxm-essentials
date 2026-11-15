package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /freeze <player>} and {@code /unfreeze <player>}: pin a connected player in place pending review, or
 * release them. Freeze is session-scoped runtime state owned by the {@link Sanctions} adapter (a relog clears
 * it), not a DB-backed sanction, but each toggle is still audit-logged because it is an admin action. The
 * exempt target cannot be frozen.
 *
 * <p>A target connected to another backend is refused with that backend's name before anything is written: this
 * backend cannot pin or release them.
 */
public final class Freeze {

    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final LiveTargetCheck live;

    public Freeze(
            Sanctions sanctions,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            NetworkPresence presence) {
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.live = new LiveTargetCheck(presence, this.notifier);
    }

    /** Freeze {@code target}, or refuse an exempt target. */
    public Result<Unit, ModerationError> freeze(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (live.refusedElsewhere(actor, target)) {
            return Result.err(ModerationError.TARGET_ELSEWHERE);
        }
        if (guard.isExempt(target)) {
            audit.froze(actor, target, true, false);
            notifier.send(actor, ModerationError.TARGET_EXEMPT.messageKey());
            return Result.err(ModerationError.TARGET_EXEMPT);
        }
        sanctions.freeze(target);
        notifier.send(target, ModerationMessageKey.FREEZE_NOTIFY_TARGET);
        audit.froze(actor, target, true, true);
        notifier.send(actor, ModerationMessageKey.FREEZE_APPLIED, Map.of("player", target.name()));
        return Result.ok();
    }

    /** Release {@code target} from a freeze. */
    public Result<Unit, ModerationError> unfreeze(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (live.refusedElsewhere(actor, target)) {
            return Result.err(ModerationError.TARGET_ELSEWHERE);
        }
        sanctions.unfreeze(target);
        notifier.send(target, ModerationMessageKey.UNFREEZE_NOTIFY_TARGET);
        audit.froze(actor, target, false, true);
        notifier.send(actor, ModerationMessageKey.UNFREEZE_APPLIED, Map.of("player", target.name()));
        return Result.ok();
    }
}
