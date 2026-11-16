package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.event.AltDetected;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerIpBanned;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /banip <player|ip> [reason]}: ban an address by stored IP (not only by UUID). When a player is
 * given, their last-seen IP is resolved from the {@link ModerationRepository} (an indexed column, a real DB
 * read) and recorded as the ban's target UUID for a replayable audit line. The ban is stored, then
 * alt-detection runs: the other UUIDs that have connected from the same IP are gathered and an
 * {@code AltDetected} event is published for the audit trail. Everyone connected from the address is kicked
 * at once, here and, through {@link SanctionSync}, on every peer; the login listener refuses the address on
 * every reconnect.
 *
 * <p>An IP ban already in force on the address that would outlast this one is never cut short: the request is refused
 * with {@link ModerationError#LONGER_BAN_RUNNING} and the actor is told which ban runs, so shortening an IP ban takes
 * an explicit {@code /unbanip} first. A ban that ends later, or a second permanent one, replaces it. The write is
 * compare and set, so an IP ban landing on another server at the same moment is never overwritten blindly.
 *
 * <p>A duration is optional: blank means a permanent IP ban, a parsed span a timed one. This use case takes
 * an already-resolved {@link Target} from the command adapter (which decides whether the argument was a
 * player name or a literal IP) so the domain never parses an address.
 */
public final class BanIp {

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final SanctionSync sync;
    private final IpAlts ipAlts;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final Clock clock;

    public BanIp(
            ModerationRepository repository,
            Sanctions sanctions,
            ModerationGuard guard,
            SanctionSync sync,
            IpAlts ipAlts,
            Notifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.ipAlts = Objects.requireNonNull(ipAlts, "ipAlts");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Ban {@code target}'s address, run alt-detection, and report the alts found; or refuse when an IP ban already in
     * force on the address would outlast this one.
     */
    public com.uxplima.uxmessentials.shared.domain.Result<Result, ModerationError> banIp(
            PlayerRef actor, Target target, String rawDuration, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rawDuration, "rawDuration");
        Objects.requireNonNull(reason, "reason");
        Instant now = clock.instant();
        Optional<Duration> span = SanctionDuration.parse(rawDuration).duration();
        IpBan ban = new IpBan(target.ip(), span.map(now::plus), reason, target.uuid(), Issuer.of(actor), now);
        Predicate<IpBan> longer = GuardedWrite.outlasting(ban.until(), IpBan::until);
        if (GuardedWrite.store(GuardedWrite.ipBan(repository, ban), now, longer)
                instanceof GuardedWrite.Kept<IpBan> kept) {
            audit.ipBanned(
                    actor,
                    target.ip(),
                    target.uuid(),
                    span.map(SanctionDuration::format),
                    false,
                    SanctionStrength.HELD);
            GuardedWrite.tellIpBanKept(notifier, actor, kept.running(), now);
            return com.uxplima.uxmessentials.shared.domain.Result.err(ModerationError.LONGER_BAN_RUNNING);
        }
        history.banIp(actor, target.uuid(), target.ip(), reason, span.map(now::plus));
        IpBanKick.kickEveryoneOn(ban, Optional.of(actor.uuid()), guard, sanctions, notifier);
        List<UUID> alts = ipAlts.onAddress(target.ip(), target.uuid().orElse(NO_UUID));
        events.publish(new PlayerIpBanned(ban));
        events.publish(new AltDetected(target.uuid().orElse(NO_UUID), target.ip(), alts, true));
        audit.ipBanned(actor, target.ip(), target.uuid(), span.map(SanctionDuration::format), true, reason);
        audit.altDetected(target.uuid().orElse(NO_UUID), target.ip(), alts, true);
        notify(actor, target, alts);
        // The local kick above cleared this backend; peers kick the players they have on the address.
        sync.ipBanChanged(target.ip());
        return com.uxplima.uxmessentials.shared.domain.Result.ok(new Result(ban, alts));
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
