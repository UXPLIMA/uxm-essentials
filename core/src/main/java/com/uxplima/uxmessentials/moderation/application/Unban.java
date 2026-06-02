package com.uxplima.uxmessentials.moderation.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /unban <player>}: lift a player's permanent ban. The ban lives in the per-UUID tempban row (a
 * permanent ban is a far-future {@link TempbanState.Active}), so lifting it is a {@code none()} save on that
 * row — {@code /unbanip} only clears IP rows, never this one. A target who is not currently banned is refused
 * (audit-logged); a banned target's row is cleared and the login enforcement stops barring reconnection.
 */
public final class Unban {

    private final ModerationRepository repository;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;

    public Unban(ModerationRepository repository, ModerationNotifier notifier, ModerationAudit audit) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    /** Lift {@code target}'s ban, or refuse when the target is not banned. */
    public Result<Unit, ModerationError> unban(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (!(repository.loadTempban(target) instanceof TempbanState.Active)) {
            audit.unbanned(actor, target, false);
            notifier.send(actor, ModerationError.NOT_BANNED.messageKey());
            return Result.err(ModerationError.NOT_BANNED);
        }
        repository.saveTempban(target, TempbanState.none());
        audit.unbanned(actor, target, true);
        notifier.send(actor, ModerationMessageKey.BAN_LIFTED, Map.of("player", target.name()));
        return Result.ok();
    }
}
