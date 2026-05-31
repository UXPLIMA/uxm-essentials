package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnmuted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /unmute <player>}: lift a player's mute. A target who is not muted is refused (audit-logged with the
 * reason); a muted target's mute row is deleted, {@code PlayerUnmuted} is published, and the messaging
 * context's {@code MutePolicy} stops gagging them on its next read.
 */
public final class Unmute {

    private final ModerationRepository repository;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    public Unmute(
            ModerationRepository repository,
            ModerationNotifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Lift {@code target}'s mute, or refuse when they were not muted. */
    public Result<Unit, ModerationError> unmute(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (repository.loadMute(target) instanceof MuteState.None) {
            audit.unmuted(actor, target, false, Optional.of("not-muted"));
            notifier.send(actor, ModerationError.NOT_MUTED.messageKey());
            return Result.err(ModerationError.NOT_MUTED);
        }
        Instant now = clock.instant();
        repository.saveMute(target, MuteState.none());
        events.publish(new PlayerUnmuted(target, now));
        audit.unmuted(actor, target, true, Optional.empty());
        notifier.send(actor, ModerationMessageKey.UNMUTE_APPLIED);
        return Result.ok();
    }
}
