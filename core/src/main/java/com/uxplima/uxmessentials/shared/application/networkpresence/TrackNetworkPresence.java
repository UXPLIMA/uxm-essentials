package com.uxplima.uxmessentials.shared.application.networkpresence;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresenceBroadcast;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the {@link NetworkRoster} current. Local joins and quits change this backend's players and are announced to
 * the peers; what the peers announce is applied to their rosters, and every player first seen on another backend is
 * taught to the {@link PlayerNameIndex}, so a name typed here resolves to that account even after they log off.
 *
 * <p>Recovery rides the heartbeat: {@link #announce} sends the whole local roster, which replaces whatever a peer
 * held for this backend, so a lost join or quit is repaired within one heartbeat. A backend that starts asks for
 * replies, so it learns the network after one round trip instead of after a heartbeat. A large roster crosses the
 * bus in parts; a peer applies it only once every part of the same announcement has arrived.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>lock</b> for the local side. A local change, the stamp it gets and its hand-off to the broadcast
 * happen in one short critical section on {@code lock}, so the stamps reach the transport in the order the roster
 * changed and a roster announcement never claims a newer stamp than a change it does not include. The hand-off
 * only appends the frame to the transport's bounded queue and asks the scheduler for a flush; asking only enqueues a
 * task and runs no server code on this thread, so the lock is never held across work on the server. Remote
 * announcements need no lock here: the roster applies them atomically, and the part and reply bookkeeping are
 * concurrent maps updated per backend inside {@link ConcurrentHashMap#compute}.
 */
public final class TrackNetworkPresence {

    private final NetworkRoster roster;
    private final NetworkPresenceBroadcast broadcast;
    private final PlayerNameIndex names;
    private final long session;
    private final Object lock = new Object();
    private long sequence;
    private final ConcurrentHashMap<String, Parts> partsByOrigin = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RosterStamp> answeredByOrigin = new ConcurrentHashMap<>();

    /**
     * @param roster the view this keeps current
     * @param broadcast how local changes reach the other backends
     * @param names the name index taught every player first seen on another backend
     * @param clock read once, to tell this run of the backend apart from earlier ones
     */
    public TrackNetworkPresence(
            NetworkRoster roster, NetworkPresenceBroadcast broadcast, PlayerNameIndex names, Clock clock) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.names = Objects.requireNonNull(names, "names");
        this.session = Objects.requireNonNull(clock, "clock").millis();
    }

    /** {@code player} joined this backend. */
    public void joined(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        synchronized (lock) {
            roster.addLocal(player);
            broadcast.joined(nextStamp(), player);
        }
    }

    /** {@code player} left this backend. */
    public void left(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        synchronized (lock) {
            roster.removeLocal(player.uuid());
            broadcast.left(nextStamp(), player);
        }
    }

    /**
     * Announce every player on this backend and forget the backends that went quiet. Runs with every heartbeat, and
     * once with {@code replyRequested} when the bus starts.
     */
    public void announce(boolean replyRequested) {
        roster.expire();
        synchronized (lock) {
            broadcast.roster(nextStamp(), replyRequested, roster.localPlayers());
        }
    }

    /** {@code player} joined the backend {@code origin}. */
    public void remoteJoined(String origin, RosterStamp stamp, PlayerRef player) {
        Objects.requireNonNull(player, "player");
        if (roster.applyJoin(origin, stamp, player)) {
            names.learn(player.uuid(), player.name());
        }
    }

    /** {@code player} left the backend {@code origin}. */
    public void remoteLeft(String origin, RosterStamp stamp, PlayerRef player) {
        Objects.requireNonNull(player, "player");
        roster.applyLeave(origin, stamp, player);
    }

    /**
     * Part {@code part} of {@code parts} of a roster the backend {@code origin} announced. The roster is applied once
     * every part of the same announcement is in; a part of an older announcement than the one being collected is
     * dropped. When the origin asks, ours is sent back once per request, however many parts or copies carry it.
     */
    public void remoteRoster(
            String origin, RosterStamp stamp, boolean replyRequested, int part, int parts, List<PlayerRef> players) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(stamp, "stamp");
        Objects.requireNonNull(players, "players");
        if (parts < 1 || part < 0 || part >= parts) {
            throw new IllegalArgumentException("part " + part + " of " + parts + " is not a roster part");
        }
        List<PlayerRef> complete = parts == 1 ? players : collect(origin, stamp, part, parts, players);
        if (complete != null) {
            for (PlayerRef learned : roster.applyRoster(origin, stamp, complete)) {
                names.learn(learned.uuid(), learned.name());
            }
        }
        // Answered even when the roster itself was stale or incomplete: the starting backend needs our players
        // regardless, and a newer frame from the same start may have overtaken this one.
        if (replyRequested && !stamp.equals(answeredByOrigin.put(origin, stamp))) {
            announce(false);
        }
    }

    /** The backend {@code origin} sent a heartbeat. */
    public void remoteAlive(String origin) {
        roster.heardFrom(Objects.requireNonNull(origin, "origin"));
    }

    /** Add one part; the whole roster once its last part arrives, otherwise {@code null}. */
    private @Nullable List<PlayerRef> collect(
            String origin, RosterStamp stamp, int part, int parts, List<PlayerRef> players) {
        List<List<PlayerRef>> done = new ArrayList<>(1);
        partsByOrigin.compute(origin, (key, pending) -> {
            Parts collecting = pending;
            if (collecting == null || newer(stamp, collecting.stamp())) {
                collecting = new Parts(stamp, parts, Map.of());
            } else if (!stamp.equals(collecting.stamp()) || parts != collecting.parts()) {
                return collecting;
            }
            Map<Integer, List<PlayerRef>> received = new HashMap<>(collecting.received());
            received.put(part, List.copyOf(players));
            if (received.size() < parts) {
                return new Parts(stamp, parts, Map.copyOf(received));
            }
            List<PlayerRef> all = new ArrayList<>();
            for (int i = 0; i < parts; i++) {
                all.addAll(Objects.requireNonNull(received.get(i), "part"));
            }
            done.add(all);
            return null;
        });
        return done.isEmpty() ? null : done.get(0);
    }

    private static boolean newer(RosterStamp candidate, RosterStamp than) {
        return candidate.session() != than.session()
                ? candidate.session() > than.session()
                : candidate.sequence() > than.sequence();
    }

    private RosterStamp nextStamp() {
        sequence++;
        return new RosterStamp(session, sequence);
    }

    /**
     * The parts of one roster announcement collected so far.
     *
     * @param stamp the announcement they belong to
     * @param parts how many parts it has
     * @param received the parts in, by index
     */
    private record Parts(RosterStamp stamp, int parts, Map<Integer, List<PlayerRef>> received) {}
}
