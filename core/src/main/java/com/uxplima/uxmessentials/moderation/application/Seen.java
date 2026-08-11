package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /seen <player>} (last-seen + first-join) and {@code /seenip <player>} (last-seen by IP, surfacing
 * alts). {@code /seen} renders the first-join and last-activity (or "online now" when connected) from the
 * DB-backed {@link SeenRecord}; {@code /seenip} renders the last address that record holds and the other accounts
 * that have connected from it. The alt half of {@code /seenip} goes through the shared {@link IpAlts} lookup, so
 * it matches by token against the same rows {@code /alts} and {@code /ipalts} answer from.
 */
public final class Seen {

    private final ModerationRepository repository;
    private final IpAlts ipAlts;
    private final PlayerLookup players;
    private final Notifier notifier;
    private final boolean censorIp;
    private final Clock clock;

    public Seen(
            ModerationRepository repository,
            IpAlts ipAlts,
            PlayerLookup players,
            Notifier notifier,
            boolean censorIp,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ipAlts = Objects.requireNonNull(ipAlts, "ipAlts");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.censorIp = censorIp;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Report {@code target}'s first-join and last-seen to {@code actor}. */
    public void seen(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Optional<SeenRecord> record = repository.seen(target);
        if (record.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.SEEN_NEVER, Map.of("player", target.name()));
            return;
        }
        notifier.send(actor, ModerationMessageKey.SEEN_REPORT, seenPlaceholders(record.get()));
    }

    /** Report {@code target}'s last IP and the alt accounts sharing it to {@code actor}. */
    public void seenIp(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Optional<String> ip = repository.seen(target).flatMap(SeenRecord::lastIp);
        if (ip.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.SEENIP_NO_IP, Map.of("player", target.name()));
            return;
        }
        notifier.send(
                actor, ModerationMessageKey.SEENIP_REPORT, Map.of("player", target.name(), "ip", render(ip.get())));
        reportAlts(actor, ip.get(), target.uuid());
    }

    /** Mask an address when censoring is on (LiteBans-style), keeping only the leading octet/segment. */
    private String render(String ip) {
        if (!censorIp) {
            return ip;
        }
        int firstDot = ip.indexOf('.');
        if (firstDot > 0) {
            // IPv4 (or an IPv4-mapped literal): keep the leading octet, mask the rest in dotted notation.
            return ip.substring(0, firstDot) + ".*.*.*";
        }
        int firstColon = ip.indexOf(':');
        if (firstColon > 0) {
            // IPv6: keep the leading hextet and mask the remainder in colon notation, not dotted.
            return ip.substring(0, firstColon) + ":*";
        }
        return "*.*.*.*";
    }

    private void reportAlts(PlayerRef actor, String ip, UUID self) {
        List<UUID> alts = ipAlts.onAddress(ip, self);
        if (!alts.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.SEENIP_ALTS, Map.of("alts", names(alts)));
        }
    }

    private String names(List<UUID> alts) {
        return alts.stream()
                .map(uuid -> players.findByUuid(uuid).map(PlayerRef::name).orElseGet(uuid::toString))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private Map<String, String> seenPlaceholders(SeenRecord record) {
        boolean online = players.isOnline(record.player().uuid());
        String ago =
                online ? "online now" : SanctionDuration.format(Duration.between(record.lastSeen(), clock.instant()));
        return Map.of(
                "player", record.player().name(), "first", record.firstSeen().toString(), "ago", ago);
    }
}
