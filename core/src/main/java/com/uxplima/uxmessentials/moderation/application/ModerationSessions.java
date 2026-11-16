package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * What moderation stores when a player joins or leaves: when and from where they were last seen, and the jail
 * countdown's edges (the jail is applied again on join; the online time of the visit is burnt off an online-only
 * sentence on quit).
 *
 * <p>The caller reads the player's address on the player's own thread and passes it in; the database work then runs on
 * the {@link StorageLane}, in order with every other piece of work about that player. The visit's start is stamped
 * here, in memory, the moment they join, so the time a quit burns off is the time they were actually connected,
 * however late the join's database work lands. A quit that comes before the join's work has run is queued behind it,
 * so the sentence is read, shortened once and saved once.
 */
public final class ModerationSessions {

    private final ModerationRepository repository;
    private final JailCountdown countdown;
    private final StorageLane lane;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Instant> started = new ConcurrentHashMap<>();

    public ModerationSessions(ModerationRepository repository, JailCountdown countdown, StorageLane lane, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.countdown = Objects.requireNonNull(countdown, "countdown");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@code player} joined from {@code address}: stamp the visit now, store the rest on the lane. */
    public void joined(PlayerRef player, Optional<String> address) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(address, "address");
        Instant now = clock.instant();
        started.put(player.uuid(), now);
        lane.background(player, () -> {
            repository.recordSeen(player, address, now);
            countdown.onJoin(player);
        });
    }

    /** {@code player} left from {@code address}: burn the visit off an online-only sentence and record the time. */
    public void left(PlayerRef player, Optional<String> address) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(address, "address");
        Instant now = clock.instant();
        Instant start = started.remove(player.uuid());
        Optional<Duration> visit = start == null ? Optional.empty() : Optional.of(Duration.between(start, now));
        lane.background(player, () -> {
            visit.ifPresent(online -> countdown.onTick(player, online));
            repository.recordSeen(player, address, now);
        });
    }
}
