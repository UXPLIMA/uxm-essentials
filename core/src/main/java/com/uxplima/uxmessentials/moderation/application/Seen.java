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
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /seen <player>} (last-seen + first-join) and {@code /seenip <player>} (last-seen by IP, surfacing
 * alts). Both are read-only lookups against the DB-backed {@link SeenRecord}: {@code /seen} renders the
 * first-join and last-activity (or "online now" when connected); {@code /seenip} renders the last IP and the
 * other UUIDs that have connected from it (the alt set), the same lookup the login alt-detection uses.
 */
public final class Seen {

    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final ModerationNotifier notifier;
    private final Clock clock;

    public Seen(ModerationRepository repository, PlayerLookup players, ModerationNotifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
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
        notifier.send(actor, ModerationMessageKey.SEENIP_REPORT, Map.of("player", target.name(), "ip", ip.get()));
        reportAlts(actor, ip.get(), target.uuid());
    }

    private void reportAlts(PlayerRef actor, String ip, UUID self) {
        List<UUID> alts = repository.altsByIp(ip, self);
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
