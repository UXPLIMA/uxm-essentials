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
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.event.AltDetected;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerIpBanned;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /banip <player|ip> [reason]}: ban an address by stored IP (not only by UUID). When a player is
 * given, their last-seen IP is resolved from the {@link ModerationRepository} (an indexed column, a real DB
 * read) and recorded as the ban's target UUID for a replayable audit line. The ban is upserted, then
 * alt-detection runs: the other UUIDs that have connected from the same IP are gathered and an
 * {@code AltDetected} event is published for the audit trail. The login listener enforces the ban (and the
 * alt set) before player data loads on every reconnect.
 *
 * <p>A duration is optional: blank means a permanent IP ban, a parsed span a timed one. This use case takes
 * an already-resolved {@link Target} from the command adapter (which decides whether the argument was a
 * player name or a literal IP) so the domain never parses an address.
 */
public final class BanIp {

    private final ModerationRepository repository;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final Clock clock;

    public BanIp(
            ModerationRepository repository,
            ModerationNotifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Ban {@code target}'s address, run alt-detection, and report the alts found. */
    public Result banIp(PlayerRef actor, Target target, String rawDuration, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rawDuration, "rawDuration");
        Objects.requireNonNull(reason, "reason");
        Instant now = clock.instant();
        Optional<Duration> span = SanctionDuration.parse(rawDuration).duration();
        IpBan ban = new IpBan(target.ip(), span.map(now::plus), reason, target.uuid(), Issuer.of(actor), now);
        repository.saveIpBan(ban);
        history.banIp(actor, target.uuid(), target.ip(), reason, span.map(now::plus));
        List<UUID> alts = repository.altsByIp(target.ip(), target.uuid().orElse(NO_UUID));
        events.publish(new PlayerIpBanned(ban));
        events.publish(new AltDetected(target.uuid().orElse(NO_UUID), target.ip(), alts, true));
        audit.ipBanned(actor, target.ip(), target.uuid(), span.map(SanctionDuration::format), true, reason);
        audit.altDetected(target.uuid().orElse(NO_UUID), target.ip(), alts, true);
        notify(actor, target, alts);
        return new Result(ban, alts);
    }

    private void notify(PlayerRef actor, Target target, List<UUID> alts) {
        notifier.send(actor, ModerationMessageKey.BANIP_APPLIED, Map.of("ip", target.ip()));
        if (!alts.isEmpty()) {
            notifier.send(
                    actor, ModerationMessageKey.BANIP_ALTS_DETECTED, Map.of("count", Integer.toString(alts.size())));
        }
    }

    private static final UUID NO_UUID = new UUID(0L, 0L);

    /**
     * The resolved subject of a {@code /banip}: the address to ban, and the UUID it resolved from (present
     * for {@code /banip <player>}, empty for {@code /banip <ip>}).
     *
     * @param ip the address to ban
     * @param uuid the resolved player UUID, if the argument was a player name
     */
    public record Target(String ip, Optional<UUID> uuid) {

        public Target {
            Objects.requireNonNull(ip, "ip");
            Objects.requireNonNull(uuid, "uuid");
            if (ip.isBlank()) {
                throw new IllegalArgumentException("banned ip must not be blank");
            }
        }
    }

    /**
     * The outcome of a {@code /banip}: the applied ban and the alt UUIDs the IP lookup surfaced.
     *
     * @param ban the applied IP ban
     * @param alts the other UUIDs sharing the banned IP
     */
    public record Result(IpBan ban, List<UUID> alts) {

        public Result {
            Objects.requireNonNull(ban, "ban");
            alts = List.copyOf(Objects.requireNonNull(alts, "alts"));
        }
    }
}
