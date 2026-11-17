package com.uxplima.uxmessentials.shared.application.networkpresence;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The in-memory {@link NetworkPresence}: the players on this backend, and for every other backend what it announced.
 * Only {@link TrackNetworkPresence} changes it.
 *
 * <h2>Ordering</h2>
 * Announcements from one backend can arrive out of order and twice, so every entry carries the sequence of the
 * announcement that last set it, and a player who left stays as a tombstone with the sequence of the quit. A join
 * or quit applies when its sequence is newer than both the last applied roster and that player's entry, so the
 * joins of two players are independent of each other. A roster replaces every entry it is newer than and keeps
 * those set after it, so an older roster arriving late cannot undo a newer join or quit. Tombstones the roster is
 * newer than are pruned. A frame from a newer session (the backend restarted) starts the roster over; a frame from
 * an older session is a straggler from before the restart and is ignored.
 *
 * <h2>Liveness</h2>
 * A remote roster counts while its backend has been heard from within the liveness window, the same three
 * heartbeats the cluster-peer roster uses. A backend that dies without sending its quits therefore drops out of
 * every view once its heartbeats stop, instead of leaving its players online forever. Time is read on this backend
 * when a frame arrives, never from the sender, so clock skew between backends cannot hide a live one. A heartbeat
 * extends a roster only while a presence frame for it arrived within the window: a backend that restarted with its
 * clock set back sends frames that read as older than the roster they replace, and its heartbeats alone must not keep
 * that stale roster alive.
 *
 * <h2>Switching backends</h2>
 * While a player switches backends they can sit in two rosters for a moment: the new backend's join and the old
 * one's quit travel separately. The entry this backend saw arrive last wins, so lookups follow the player at once;
 * entries that arrived in the same millisecond are ordered by server name, so every lookup answers alike.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The local players are a {@link ConcurrentHashMap}. Each remote roster is
 * an immutable record swapped whole inside {@link ConcurrentHashMap#compute}, so frames from one backend apply
 * atomically even when the bus delivers them on several threads at once. Reads take no lock.
 */
public final class NetworkRoster implements NetworkPresence {

    private static final Comparator<OnlinePlayer> BY_NAME =
            Comparator.comparing(presence -> presence.player().name().toLowerCase(Locale.ROOT));
    private static final Comparator<OnlinePlayer> BY_SERVER_THEN_NAME =
            Comparator.comparing(OnlinePlayer::server).thenComparing(BY_NAME);

    private final String localServer;
    private final Clock clock;
    private final long livenessMillis;
    private final ConcurrentHashMap<UUID, PlayerRef> local = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Remote> remotes = new ConcurrentHashMap<>();

    /**
     * @param localServer this backend's {@code server-name}
     * @param clock the clock frames are timed with on arrival
     * @param livenessWindow how long a remote roster counts after its backend was last heard from
     */
    public NetworkRoster(String localServer, Clock clock, Duration livenessWindow) {
        this.localServer = Objects.requireNonNull(localServer, "localServer");
        this.clock = Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(livenessWindow, "livenessWindow");
        if (localServer.isBlank()) {
            throw new IllegalArgumentException("localServer must not be blank");
        }
        if (livenessWindow.isNegative() || livenessWindow.isZero()) {
            throw new IllegalArgumentException("livenessWindow must be positive: " + livenessWindow);
        }
        this.livenessMillis = livenessWindow.toMillis();
    }

    @Override
    public String localServer() {
        return localServer;
    }

    @Override
    public boolean isLive(String server) {
        Objects.requireNonNull(server, "server");
        if (server.equals(localServer)) {
            return true;
        }
        Remote remote = remotes.get(server);
        return remote != null && !expired(remote, clock.millis());
    }

    @Override
    public Optional<OnlinePlayer> find(UUID player) {
        Objects.requireNonNull(player, "player");
        PlayerRef here = local.get(player);
        if (here != null) {
            return Optional.of(new OnlinePlayer(here, localServer));
        }
        long now = clock.millis();
        @Nullable Placed best = null;
        for (Map.Entry<String, Remote> entry : remotes.entrySet()) {
            Remote remote = entry.getValue();
            Entry member = remote.entries().get(player);
            if (member == null || !member.present() || expired(remote, now)) {
                continue;
            }
            Placed candidate = new Placed(member, entry.getKey());
            if (best == null || candidate.beats(best)) {
                best = candidate;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.presence());
    }

    @Override
    public Optional<OnlinePlayer> findByName(String name) {
        Objects.requireNonNull(name, "name");
        String wanted = name.strip();
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        List<OnlinePlayer> locals = local.values().stream()
                .map(player -> new OnlinePlayer(player, localServer))
                .toList();
        Optional<OnlinePlayer> here = pickByName(locals, wanted);
        boolean heldHere =
                locals.stream().anyMatch(presence -> presence.player().name().equalsIgnoreCase(wanted));
        // Case variants here that name no single account are not settled by a player elsewhere either.
        return here.isPresent() || heldHere ? here : pickByName(remoteWinners(), wanted);
    }

    @Override
    public List<OnlinePlayer> onServer(String server) {
        Objects.requireNonNull(server, "server");
        List<OnlinePlayer> found = new ArrayList<>();
        for (OnlinePlayer presence : online()) {
            if (presence.server().equals(server)) {
                found.add(presence);
            }
        }
        return List.copyOf(found);
    }

    @Override
    public List<OnlinePlayer> online() {
        List<OnlinePlayer> all = new ArrayList<>(remoteWinners());
        for (PlayerRef player : local.values()) {
            all.add(new OnlinePlayer(player, localServer));
        }
        all.sort(BY_SERVER_THEN_NAME);
        return List.copyOf(all);
    }

    /** {@code player} is connected to this backend. */
    void addLocal(PlayerRef player) {
        local.put(player.uuid(), player);
    }

    /** {@code player} left this backend. */
    void removeLocal(UUID player) {
        local.remove(player);
    }

    /** The players connected to this backend, ordered by name. */
    List<PlayerRef> localPlayers() {
        List<PlayerRef> players = new ArrayList<>(local.values());
        players.sort(Comparator.comparing(player -> player.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(players);
    }

    /**
     * {@code player} joined {@code origin}.
     *
     * @return whether the player is newly online there, so the caller can learn the name once
     */
    boolean applyJoin(String origin, RosterStamp stamp, PlayerRef player) {
        return !apply(
                        origin,
                        stamp,
                        (remote, now) -> delta(remote, stamp, new Entry(player, stamp.sequence(), true, now)))
                .isEmpty();
    }

    /** {@code player} left {@code origin}. */
    void applyLeave(String origin, RosterStamp stamp, PlayerRef player) {
        apply(origin, stamp, (remote, now) -> delta(remote, stamp, new Entry(player, stamp.sequence(), false, now)));
    }

    /**
     * {@code origin} announced every player it has.
     *
     * @return the players newly online there, so the caller can learn their names once
     */
    List<PlayerRef> applyRoster(String origin, RosterStamp stamp, Collection<PlayerRef> players) {
        return apply(origin, stamp, (remote, now) -> roster(remote, stamp, players, now));
    }

    /**
     * {@code origin} is alive. Refreshes a roster that still counts and was confirmed by a presence frame within the
     * liveness window. One that has already expired stays expired, because the players it lists may have left while
     * its frames were not arriving; one that only heartbeats still reach may belong to an earlier run of the origin.
     */
    void heardFrom(String origin) {
        long now = clock.millis();
        remotes.computeIfPresent(
                origin,
                (key, remote) -> expired(remote, now) || now - remote.framedMillis() > livenessMillis
                        ? remote
                        : remote.pingedAt(now));
    }

    /** Whether {@code origin} has a roster here, counting one that expired but was not pruned yet. */
    boolean hasRoster(String origin) {
        return remotes.containsKey(origin);
    }

    /** Drop the rosters of backends that have not been heard from within the liveness window. */
    void expire() {
        long now = clock.millis();
        for (String origin : remotes.keySet()) {
            remotes.computeIfPresent(origin, (key, remote) -> expired(remote, now) ? null : remote);
        }
    }

    /**
     * Apply one announcement from {@code origin} to its roster.
     *
     * @param change builds the next roster from the current one, or returns {@code null} when the announcement is
     *     older than what the roster already reflects
     * @return the players the change made newly online; empty when it added none or was ignored
     */
    private List<PlayerRef> apply(String origin, RosterStamp stamp, Change change) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(stamp, "stamp");
        if (origin.equals(localServer)) {
            // Two backends sharing a name is an operator mistake reported elsewhere; it must not overwrite our own.
            return List.of();
        }
        List<PlayerRef> added = new ArrayList<>();
        long now = clock.millis();
        remotes.compute(origin, (key, current) -> {
            Remote base;
            if (current == null || expired(current, now) || stamp.session() > current.session()) {
                base = Remote.fresh(stamp.session(), now);
            } else if (stamp.session() < current.session()) {
                // A straggler from before the origin restarted: neither its players nor its liveness apply.
                return current;
            } else {
                base = current;
            }
            Remote next = change.next(base, now);
            if (next == null) {
                return base.framedAt(now);
            }
            added.clear();
            for (Entry entry : next.entries().values()) {
                Entry before = base.entries().get(entry.player().uuid());
                if (entry.present() && (before == null || !before.present())) {
                    added.add(entry.player());
                }
            }
            return next.framedAt(now);
        });
        return List.copyOf(added);
    }

    /** A join or quit applied to {@code remote}, or {@code null} when it is not newer than what it would replace. */
    private static @Nullable Remote delta(Remote remote, RosterStamp stamp, Entry entry) {
        Entry current = remote.entries().get(entry.player().uuid());
        if (stamp.sequence() <= remote.rosterSequence()
                || (current != null && stamp.sequence() <= current.sequence())) {
            return null;
        }
        Map<UUID, Entry> entries = new HashMap<>(remote.entries());
        entries.put(entry.player().uuid(), entry);
        return remote.with(remote.rosterSequence(), entries);
    }

    /** A whole roster applied to {@code remote}, or {@code null} when an equal or newer one was already applied. */
    private static @Nullable Remote roster(Remote remote, RosterStamp stamp, Collection<PlayerRef> players, long now) {
        if (stamp.sequence() <= remote.rosterSequence()) {
            return null;
        }
        Map<UUID, Entry> entries = new HashMap<>();
        // Joins and quits sent after this roster still hold; everything older, tombstones included, is replaced.
        for (Entry entry : remote.entries().values()) {
            if (entry.sequence() > stamp.sequence()) {
                entries.put(entry.player().uuid(), entry);
            }
        }
        for (PlayerRef player : players) {
            if (entries.containsKey(player.uuid())) {
                continue;
            }
            Entry kept = remote.entries().get(player.uuid());
            long since = kept != null && kept.present() ? kept.sinceMillis() : now;
            entries.put(player.uuid(), new Entry(player, stamp.sequence(), true, since));
        }
        return remote.with(stamp.sequence(), entries);
    }

    /** The winning remote entry of each account, across rosters that still count. */
    private List<OnlinePlayer> remoteWinners() {
        long now = clock.millis();
        Map<UUID, Placed> best = new HashMap<>();
        for (Map.Entry<String, Remote> entry : remotes.entrySet()) {
            Remote remote = entry.getValue();
            if (expired(remote, now)) {
                continue;
            }
            for (Entry member : remote.entries().values()) {
                UUID id = member.player().uuid();
                if (!member.present() || local.containsKey(id)) {
                    continue;
                }
                Placed candidate = new Placed(member, entry.getKey());
                best.merge(id, candidate, (current, offered) -> offered.beats(current) ? offered : current);
            }
        }
        List<OnlinePlayer> winners = new ArrayList<>(best.size());
        for (Placed placed : best.values()) {
            winners.add(placed.presence());
        }
        return winners;
    }

    private boolean expired(Remote remote, long now) {
        return now - remote.heardMillis() > livenessMillis;
    }

    private static Optional<OnlinePlayer> pickByName(List<OnlinePlayer> candidates, String wanted) {
        OnlinePlayer caseless = null;
        boolean several = false;
        for (OnlinePlayer presence : candidates) {
            String name = presence.player().name();
            if (name.equals(wanted)) {
                return Optional.of(presence);
            }
            if (name.equalsIgnoreCase(wanted)) {
                several |= caseless != null && !caseless.player().equals(presence.player());
                caseless = presence;
            }
        }
        // Two offline-mode accounts online under case variants of the name: neither is the one typed, so no guess.
        return several ? Optional.empty() : Optional.ofNullable(caseless);
    }

    /** Builds the next roster of one backend from its current one. */
    @FunctionalInterface
    private interface Change {

        /** The next roster, or {@code null} when the announcement is older than what {@code base} reflects. */
        @Nullable Remote next(Remote base, long now);
    }

    /**
     * One player in a remote roster.
     *
     * @param player the player
     * @param sequence the announcement that last set this entry
     * @param present {@code false} for a tombstone: the player left
     * @param sinceMillis when this backend saw the player arrive there, on this backend's clock
     */
    private record Entry(PlayerRef player, long sequence, boolean present, long sinceMillis) {}

    /**
     * A roster entry together with the backend whose roster holds it.
     *
     * @param entry the entry
     * @param server the backend
     */
    private record Placed(Entry entry, String server) {

        /** Whether this entry wins the account over {@code other}: the later arrival, then the lower server name. */
        boolean beats(Placed other) {
            if (entry.sinceMillis() != other.entry.sinceMillis()) {
                return entry.sinceMillis() > other.entry.sinceMillis();
            }
            return server.compareTo(other.server) < 0;
        }

        OnlinePlayer presence() {
            return new OnlinePlayer(entry.player(), server);
        }
    }

    /**
     * What one remote backend announced.
     *
     * @param session the run of that backend the roster belongs to
     * @param rosterSequence the newest whole roster applied; {@link Long#MIN_VALUE} before the first
     * @param heardMillis when that backend was last heard from, on this backend's clock
     * @param framedMillis when a presence frame of this run last arrived, on this backend's clock
     * @param entries its players and tombstones by account
     */
    private record Remote(
            long session, long rosterSequence, long heardMillis, long framedMillis, Map<UUID, Entry> entries) {

        static Remote fresh(long session, long now) {
            return new Remote(session, Long.MIN_VALUE, now, now, Map.of());
        }

        Remote with(long nextRosterSequence, Map<UUID, Entry> nextEntries) {
            return new Remote(session, nextRosterSequence, heardMillis, framedMillis, Map.copyOf(nextEntries));
        }

        /** A presence frame of this run arrived. */
        Remote framedAt(long now) {
            return new Remote(
                    session, rosterSequence, Math.max(heardMillis, now), Math.max(framedMillis, now), entries);
        }

        /** Only a heartbeat arrived. */
        Remote pingedAt(long now) {
            return new Remote(session, rosterSequence, Math.max(heardMillis, now), framedMillis, entries);
        }
    }
}
