package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.messaging.application.port.MuteAnswer;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.event.ModerationEvent;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerMuted;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnmuted;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The mute of every player connected here, kept in memory so the mute checks never read the database on the thread
 * that asks: a muted player's {@code /msg}, a command on {@code muted-blocked-commands} and a placeholder all answer on
 * the server thread.
 *
 * <p>A player's mute is read on the async lane when they join and dropped when they leave. A mute or unmute issued on
 * this server is taken from the use case's own event as it lands; one issued on another server arrives as its bus
 * frame, which makes the kept mute stale until it is read again on the async lane. A timed mute ends on its own, since
 * the expiry is checked against the clock at every question. Every connected player's mute is also read again now and
 * then ({@link #refreshAll}), so a change that reached the database with no frame (a peer with no bus carrier, an
 * import) is picked up while they stay.
 *
 * <p>While a player's mute is not known (the read at join has not landed, or a peer changed it and the new one has not
 * been read), {@link #check} answers {@link MuteAnswer#UNKNOWN} and the gate holds the action back. A read normally
 * lands within milliseconds of the join, well before a player can type. A read that fails is tried again on the async
 * lane, a little later each time up to {@link #RETRY_CAP}, until it lands or the player leaves, so nobody stays held
 * once the database answers. A player not connected here is not known either.
 *
 * <p>Failed reads are logged through one {@link ReadFailureLog}, so a database outage leaves a line a minute for each
 * kind of read rather than one per player or per message.
 *
 * <p>Each change is numbered, and a read finishing late never replaces a change numbered after it began, so a mute that
 * lands while a peer's frame is being read again is not undone by that older read.
 */
public final class OnlineMutes implements MutePolicy {

    /** The wait before the second read of a mute that failed; each later one waits that much longer again. */
    static final Duration RETRY_AFTER = Duration.ofSeconds(2);

    /** The longest wait between two reads of a mute that keeps failing. */
    static final Duration RETRY_CAP = Duration.ofSeconds(30);

    private final ModerationRepository repository;
    private final Scheduler scheduler;
    private final Clock clock;
    private final ReadFailureLog failures;
    private final AtomicLong changes = new AtomicLong();
    private final ConcurrentHashMap<UUID, Known> known = new ConcurrentHashMap<>();
    private final Set<UUID> reading = ConcurrentHashMap.newKeySet();

    public OnlineMutes(ModerationRepository repository, Scheduler scheduler, Clock clock, Logger log) {
        this(repository, scheduler, clock, log, System::nanoTime);
    }

    OnlineMutes(ModerationRepository repository, Scheduler scheduler, Clock clock, Logger log, LongSupplier nanoTime) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.failures = new ReadFailureLog(Objects.requireNonNull(log, "log"), nanoTime);
    }

    /** {@code player} connected here: keep their mute from now on, read on the async lane. */
    public void joined(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        known.put(player.uuid(), new Known(player, changes.incrementAndGet(), Optional.empty()));
        wantRead(player.uuid());
    }

    /** {@code player} left: nothing is kept for them. */
    public void left(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        known.remove(player.uuid());
    }

    /** A moderation event of this server: a mute or unmute of a connected player is kept as it landed. */
    public void onEvent(ModerationEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof PlayerMuted muted) {
            set(muted.target().uuid(), Optional.of(muted.mute()));
        } else if (event instanceof PlayerUnmuted unmuted) {
            set(unmuted.target().uuid(), Optional.of(MuteState.none()));
        }
    }

    /** Another server changed {@code player}'s mute: the kept one is stale, so read it again, if they are here. */
    public void changedElsewhere(UUID player) {
        Objects.requireNonNull(player, "player");
        if (known.containsKey(player)) {
            set(player, Optional.empty());
            wantRead(player);
        }
    }

    /** Read every connected player's mute again on the async lane, in one read where the store can. */
    public void refreshAll() {
        List<PlayerRef> players = known.values().stream().map(Known::player).toList();
        if (players.isEmpty()) {
            return;
        }
        long change = changes.incrementAndGet();
        scheduler.async(() -> {
            Map<UUID, MuteState> mutes;
            try {
                mutes = repository.loadMutes(players);
            } catch (RuntimeException failure) {
                failures.failed("online_mute_refresh_failed", "players=" + players.size(), failure);
                return;
            }
            failures.succeeded();
            mutes.forEach((player, mute) -> keep(player, change, mute));
        });
    }

    /**
     * The kept answer, never a database read: {@link MuteAnswer#UNKNOWN} while the player's mute is not known, with a
     * read started on the async lane if none is running.
     */
    @Override
    public MuteAnswer check(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Known entry = known.get(who.uuid());
        if (entry == null) {
            return MuteAnswer.UNKNOWN;
        }
        if (entry.mute().isPresent()) {
            return answer(entry.mute().get());
        }
        wantRead(who.uuid());
        return MuteAnswer.UNKNOWN;
    }

    /**
     * The policy for a check that already runs off the server thread (the chat listener on Paper's chat thread): it
     * reads the database every time, so a mute set on any server counts at once, and answers as {@link #check} does
     * when that read fails. It must never be asked on the server thread.
     */
    public MutePolicy fresh() {
        return who -> {
            Objects.requireNonNull(who, "who");
            MuteState mute;
            try {
                mute = repository.loadMute(who);
            } catch (RuntimeException failure) {
                failures.failed("mute_check_read_failed", "player=" + who.name(), failure);
                return check(who);
            }
            failures.succeeded();
            return answer(mute);
        };
    }

    private MuteAnswer answer(MuteState mute) {
        return mute.isActiveAt(clock.instant()) ? MuteAnswer.MUTED : MuteAnswer.NOT_MUTED;
    }

    private void set(UUID player, Optional<MuteState> mute) {
        long change = changes.incrementAndGet();
        known.computeIfPresent(player, (uuid, old) -> new Known(old.player(), change, mute));
    }

    /** Start reading {@code player}'s mute on the async lane, unless a read of it is already running. */
    private void wantRead(UUID player) {
        if (reading.add(player)) {
            scheduler.async(() -> readUntilKnown(player, 1));
        }
    }

    /** Read until the player's mute is known or they left; a failed read is tried again later, on its own. */
    private void readUntilKnown(UUID player, int firstAttempt) {
        int attempt = firstAttempt;
        while (true) {
            Known entry = known.get(player);
            if (entry == null || entry.mute().isPresent()) {
                reading.remove(player);
                // A change that came between the look and the remove found this read running and left the read to it.
                if (!stillUnknown(player) || !reading.add(player)) {
                    return;
                }
                continue;
            }
            long change = changes.get();
            MuteState mute;
            try {
                mute = repository.loadMute(entry.player());
            } catch (RuntimeException failure) {
                failures.failed(
                        "online_mute_read_failed", "player=" + entry.player().name() + " attempt=" + attempt, failure);
                int next = attempt + 1;
                scheduler.asyncAfter(retryAfter(attempt), () -> readUntilKnown(player, next));
                return;
            }
            failures.succeeded();
            attempt = 1;
            keep(player, change, mute);
        }
    }

    private boolean stillUnknown(UUID player) {
        Known entry = known.get(player);
        return entry != null && entry.mute().isEmpty();
    }

    private static Duration retryAfter(int attempt) {
        Duration wait = RETRY_AFTER.multipliedBy(attempt);
        return wait.compareTo(RETRY_CAP) > 0 ? RETRY_CAP : wait;
    }

    /** Keep {@code mute} unless a change numbered after {@code change} has been kept since. */
    private void keep(UUID player, long change, MuteState mute) {
        known.computeIfPresent(
                player,
                (uuid, old) -> old.change() <= change ? new Known(old.player(), change, Optional.of(mute)) : old);
    }

    /** What is kept for one connected player: their mute, empty while not known, and the change it came from. */
    private record Known(PlayerRef player, long change, Optional<MuteState> mute) {}
}
