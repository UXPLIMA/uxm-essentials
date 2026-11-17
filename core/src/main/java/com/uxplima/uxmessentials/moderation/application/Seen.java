package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SeenVisibility;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /seen <player>} (last-seen + first-join) and {@code /seenip <player>} (last-seen by IP, surfacing
 * alts). {@code /seen} renders the first-join and last-activity (or the server they are on when connected) from the
 * DB-backed {@link SeenRecord}; {@code /seenip} renders the last address that record holds and the other accounts
 * that have connected from it. The alt half of {@code /seenip} goes through the shared {@link IpAlts} lookup, so
 * it matches by token against the same rows {@code /alts} and {@code /ipalts} answer from.
 *
 * <p>A vanished player the actor may not see reads as last seen, never as online, wherever they are connected, so
 * {@code /seen} cannot be used to find them.
 *
 * <p>A player connected to another backend is reported as online there: the record's last-seen time only knows
 * their sessions on this backend and would read as if they were gone.
 */
public final class Seen {

    private final ModerationRepository repository;
    private final IpAlts ipAlts;
    private final PlayerLookup players;
    private final Notifier notifier;
    private final boolean censorIp;
    private final Clock clock;
    private final LiveTargetCheck live;
    private final NetworkPresence presence;
    private final SeenVisibility visibility;

    public Seen(
            ModerationRepository repository,
            IpAlts ipAlts,
            PlayerLookup players,
            Notifier notifier,
            boolean censorIp,
            Clock clock,
            NetworkPresence presence,
            SeenVisibility visibility) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ipAlts = Objects.requireNonNull(ipAlts, "ipAlts");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.censorIp = censorIp;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.live = new LiveTargetCheck(presence);
    }

    /**
     * What {@code /seen} learns about a target on the command thread, before it reads storage: where they are connected
     * and the actor's vanish see level, both of which ask the server.
     *
     * @param actor who asked
     * @param target who they asked about
     * @param elsewhere the other backend the target is connected to, if any
     * @param here whether the target is connected to this backend
     * @param hiddenHere whether the target is connected here, vanished, and hidden from the actor
     * @param seeLevel the actor's vanish see level, for the check against the shared vanish rows
     */
    public record Lookup(
            PlayerRef actor,
            PlayerRef target,
            Optional<String> elsewhere,
            boolean here,
            boolean hiddenHere,
            int seeLevel) {

        public Lookup {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(elsewhere, "elsewhere");
        }
    }

    /** Report {@code target}'s first-join and last-seen to {@code actor}, all on one thread. */
    public void seen(PlayerRef actor, PlayerRef target) {
        report(look(actor, target));
    }

    /** The command-thread half of {@code /seen}: what the server knows about {@code target} right now. */
    public Lookup look(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        boolean here = players.isOnline(target.uuid());
        int seeLevel = visibility.seeLevel(actor);
        boolean hiddenHere = here && visibility.isHiddenHere(actor, seeLevel, target);
        return new Lookup(actor, target, live.elsewhere(target), here, hiddenHere, seeLevel);
    }

    /** The off-tick half of {@code /seen}: read the records and tell the actor. */
    public void report(Lookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        PlayerRef actor = lookup.actor();
        PlayerRef target = lookup.target();
        Optional<String> elsewhere = lookup.elsewhere();
        boolean here = lookup.here();
        if (lookup.hiddenHere() || (elsewhere.isPresent() && visibility.isHiddenElsewhere(lookup.seeLevel(), target))) {
            reportLastSeen(actor, target);
            return;
        }
        if (elsewhere.isPresent()) {
            notifier.send(
                    actor,
                    ModerationMessageKey.SEEN_ONLINE_ELSEWHERE,
                    Map.of("player", target.name(), "server", elsewhere.get()));
            return;
        }
        Optional<SeenRecord> record = repository.seen(target);
        if (here) {
            // A player connected before their first record was written is being seen for the first time now.
            String first =
                    record.map(SeenRecord::firstSeen).orElseGet(clock::instant).toString();
            notifier.send(
                    actor,
                    ModerationMessageKey.SEEN_ONLINE_HERE,
                    Map.of("player", target.name(), "first", first, "server", presence.localServer()));
            return;
        }
        reportLastSeen(actor, target, record);
    }

    private void reportLastSeen(PlayerRef actor, PlayerRef target) {
        reportLastSeen(actor, target, repository.seen(target));
    }

    private void reportLastSeen(PlayerRef actor, PlayerRef target, Optional<SeenRecord> record) {
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
        String ago = SanctionDuration.format(Duration.between(record.lastSeen(), clock.instant()));
        return Map.of(
                "player", record.player().name(), "first", record.firstSeen().toString(), "ago", ago);
    }
}
