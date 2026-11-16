package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.event.ModerationEvent;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerMuted;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnmuted;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The mute of every player connected here, kept in memory so the checks that must answer on the server thread (a
 * muted player's {@code /msg}, a command on {@code muted-blocked-commands}, a placeholder) never read the database
 * there.
 *
 * <p>A player's mute is read on the async lane when they join and dropped when they leave. A mute or unmute issued on
 * this server is taken from the use case's own event as it lands; one issued on another server arrives as its bus
 * frame and is read again on the async lane. A timed mute ends on its own, since the expiry is checked against the
 * clock at every question. Until the read at join completes, and for a player not connected here, the answer comes
 * from the database as before.
 *
 * <p>Each change is numbered, and a read finishing late never replaces a change numbered after it began, so a mute that
 * lands while a peer's frame is being read again is not undone by that older read.
 */
public final class OnlineMutes implements MutePolicy {

    private final ModerationRepository repository;
    private final Scheduler scheduler;
    private final Clock clock;
    private final AtomicLong changes = new AtomicLong();
    private final ConcurrentHashMap<UUID, Known> known = new ConcurrentHashMap<>();

    public OnlineMutes(ModerationRepository repository, Scheduler scheduler, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@code player} connected here: keep their mute from now on, read on the async lane. */
    public void joined(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        long change = changes.incrementAndGet();
        known.put(player.uuid(), new Known(player, change, Optional.empty()));
        scheduler.async(() -> read(player.uuid(), change));
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
            set(muted.target(), muted.mute());
        } else if (event instanceof PlayerUnmuted unmuted) {
            set(unmuted.target(), MuteState.none());
        }
    }

    /** Another server changed {@code player}'s mute: read it again, if they are connected here. */
    public void changedElsewhere(UUID player) {
        Objects.requireNonNull(player, "player");
        if (known.containsKey(player)) {
            long change = changes.incrementAndGet();
            scheduler.async(() -> read(player, change));
        }
    }

    @Override
    public boolean isMuted(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Known entry = known.get(who.uuid());
        MuteState mute = entry == null || entry.mute().isEmpty()
                ? repository.loadMute(who)
                : entry.mute().get();
        return mute.isActiveAt(clock.instant());
    }

    private void set(PlayerRef player, MuteState mute) {
        long change = changes.incrementAndGet();
        known.computeIfPresent(player.uuid(), (uuid, old) -> new Known(old.player(), change, Optional.of(mute)));
    }

    private void read(UUID player, long change) {
        Known entry = known.get(player);
        if (entry == null) {
            return;
        }
        MuteState mute = repository.loadMute(entry.player());
        known.computeIfPresent(
                player,
                (uuid, old) -> old.change() <= change ? new Known(old.player(), change, Optional.of(mute)) : old);
    }

    /** What is kept for one connected player: their mute, empty until first read, and the change it came from. */
    private record Known(PlayerRef player, long change, Optional<MuteState> mute) {}
}
