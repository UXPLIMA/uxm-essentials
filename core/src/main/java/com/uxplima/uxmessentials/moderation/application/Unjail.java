package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnjailed;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /unjail <player>}: release a jailed player. This is the only release for a target who never logs
 * back in (an online-only sentence is otherwise frozen while they are offline). A target who is not jailed is
 * refused; a jailed target's row is deleted, an online target is teleported out of the jail via
 * {@link Sanctions}, {@code PlayerUnjailed} is published, and the teleport context's {@code JailGate} stops
 * blocking them on its next read.
 */
public final class Unjail {

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    public Unjail(
            ModerationRepository repository,
            Sanctions sanctions,
            ModerationNotifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Release {@code target} from jail, or refuse when they were not jailed. */
    public Result<Unit, ModerationError> unjail(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (repository.loadJail(target) instanceof JailState.None) {
            audit.unjailed(actor, target, false, Optional.of("not-jailed"));
            notifier.send(actor, ModerationError.NOT_JAILED.messageKey());
            return Result.err(ModerationError.NOT_JAILED);
        }
        Instant now = clock.instant();
        repository.saveJail(target, JailState.none());
        sanctions.releaseFromJail(target);
        events.publish(new PlayerUnjailed(target, now));
        audit.unjailed(actor, target, true, Optional.empty());
        notifier.send(actor, ModerationMessageKey.UNJAIL_APPLIED);
        return Result.ok();
    }
}
