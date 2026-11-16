package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /freeze <player>} and {@code /unfreeze <player>}: pin a connected player in place pending review, or
 * release them. Freeze is in-memory runtime state each backend holds, not a DB-backed sanction, but each toggle is
 * still audit-logged because it is an admin action. Logging out must not escape a freeze, so it holds across a relog
 * and ends only with {@code /unfreeze}, a restart or a module reload. The exempt target cannot be frozen.
 *
 * <p>On a network both go through {@link NetworkFreeze}, which announces them: every backend holds the freeze, so a
 * switch of server does not escape it, and an unfreeze anywhere lifts it everywhere. A freeze on a target connected to
 * another backend is refused with that backend's name before anything is written: this backend cannot pin them. An
 * unfreeze always runs here, since lifting a freeze is safe wherever it is asked for.
 */
public final class Freeze {

    private final NetworkFreeze freezes;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final LiveTargetCheck live;

    public Freeze(
            NetworkFreeze freezes,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            NetworkPresence presence) {
        this.freezes = Objects.requireNonNull(freezes, "freezes");
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
        freezes.changeHere(target, true);
        notifier.send(target, ModerationMessageKey.FREEZE_NOTIFY_TARGET);
        audit.froze(actor, target, true, true);
        notifier.send(actor, ModerationMessageKey.FREEZE_APPLIED, Map.of("player", target.name()));
        return Result.ok();
    }

    /** Release {@code target} from a freeze. */
    public Result<Unit, ModerationError> unfreeze(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        freezes.changeHere(target, false);
        notifier.send(target, ModerationMessageKey.UNFREEZE_NOTIFY_TARGET);
        audit.froze(actor, target, false, true);
        notifier.send(actor, ModerationMessageKey.UNFREEZE_APPLIED, Map.of("player", target.name()));
        return Result.ok();
    }
}
