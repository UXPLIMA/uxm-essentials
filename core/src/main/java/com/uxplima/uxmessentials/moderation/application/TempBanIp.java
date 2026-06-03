package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.event.AltDetected;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerIpBanned;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /tempbanip <player|ip> <duration> [reason]}: ban an address until a wall-clock expiry. This is the
 * timed sibling of {@code /banip}: the duration is mandatory (a permanent IP ban is {@code /banip}), so a
 * blank or malformed span is refused as {@link ModerationError#BAD_DURATION} with nothing written. On a valid
 * span the {@link IpBan} is upserted with its {@code until} instant, alt-detection runs, and the events and
 * audit line mirror {@code /banip}; the existing login listener already honours the {@code until} expiry, so
 * the address is admitted again once the span passes.
 *
 * <p>Like {@code /banip} this takes an already-resolved {@link BanIp.Target} from the command adapter (which
 * decides whether the argument was a player name or a literal IP), so the domain never parses an address.
 */
public final class TempBanIp {

    private static final UUID NO_UUID = new UUID(0L, 0L);

    private final ModerationRepository repository;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    public TempBanIp(
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

    /** Ban {@code target}'s address until {@code rawDuration} elapses, refusing a permanent/blank span. */
    public Result<BanIp.Result, ModerationError> tempBanIp(
            PlayerRef actor, BanIp.Target target, String rawDuration, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rawDuration, "rawDuration");
        Objects.requireNonNull(reason, "reason");
        Optional<Duration> span = SanctionDuration.parse(rawDuration).duration();
        if (span.isEmpty()) {
            // A tempbanip must be timed: a permanent or malformed input is BAD_DURATION (use /banip for permanent).
            notifier.send(actor, ModerationError.BAD_DURATION.messageKey());
            return Result.err(ModerationError.BAD_DURATION);
        }
        return apply(actor, target, span.get(), reason);
    }

    private Result<BanIp.Result, ModerationError> apply(
            PlayerRef actor, BanIp.Target target, Duration span, Optional<String> reason) {
        Instant now = clock.instant();
        IpBan ban = new IpBan(target.ip(), Optional.of(now.plus(span)), reason, target.uuid(), Issuer.of(actor), now);
        repository.saveIpBan(ban);
        List<UUID> alts = repository.altsByIp(target.ip(), target.uuid().orElse(NO_UUID));
        events.publish(new PlayerIpBanned(ban));
        events.publish(new AltDetected(target.uuid().orElse(NO_UUID), target.ip(), alts, true));
        audit.ipBanned(actor, target.ip(), target.uuid(), Optional.of(SanctionDuration.format(span)), true, reason);
        audit.altDetected(target.uuid().orElse(NO_UUID), target.ip(), alts, true);
        notify(actor, target, span, alts);
        return Result.ok(new BanIp.Result(ban, alts));
    }

    private void notify(PlayerRef actor, BanIp.Target target, Duration span, List<UUID> alts) {
        notifier.send(
                actor,
                ModerationMessageKey.TEMPBANIP_APPLIED,
                Map.of("ip", target.ip(), "duration", SanctionDuration.format(span)));
        if (!alts.isEmpty()) {
            notifier.send(
                    actor, ModerationMessageKey.BANIP_ALTS_DETECTED, Map.of("count", Integer.toString(alts.size())));
        }
    }
}
