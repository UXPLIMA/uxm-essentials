package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import org.jspecify.annotations.NullMarked;

/**
 * The last step of the rent lifecycle, and the only one that cannot be undone: a warp archived for unpaid rent that
 * nobody has restored or touched for {@link RentConfig#hardDeleteAfter} is deleted for good, freeing its name. Off
 * unless the operator sets a delay, so an archived warp stays recoverable by default.
 *
 * <p>Only warps {@link SettleRent} retired qualify: they carry a rent archive deadline, which a warp the owner
 * archived while paid up never has. The delay counts from both that deadline and the warp's last change, so a warp
 * an admin restored and that was archived again gets the full delay once more. Each candidate is checked again here
 * before the delete, so a store that returns too much can still never delete a warp that does not qualify. An exempt
 * warp is left alone, as the rest of the rent sweep leaves it.
 *
 * <p>The delete publishes {@link PlayerWarpDeleted}, the same as the admin delete, so cross-server caches and the
 * side tables are cleaned up the same way. No veto is asked: the operator chose the delay, as an operator confirms
 * the admin delete.
 */
@NullMarked
public final class PurgeArchivedRentWarps {

    private final PlayerWarpRepository repository;
    private final DomainEventPublisher events;
    private final RentConfig config;
    private final Clock clock;

    public PurgeArchivedRentWarps(
            PlayerWarpRepository repository, DomainEventPublisher events, RentConfig config, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.events = Objects.requireNonNull(events, "events");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Up to {@code limit} warps whose hard delete is due now; empty when hard delete is off. */
    public List<PlayerWarp> due(int limit) {
        if (!config.hardDeletes()) {
            return List.of();
        }
        return repository.archivedForRent(cutoff(), limit);
    }

    /** Delete {@code warp} for good when its hard delete is due; true when it was deleted. */
    public boolean purge(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        if (!config.hardDeletes() || config.isExempt(warp) || !qualifies(warp, cutoff())) {
            return false;
        }
        Optional<PlayerWarpId> id = warp.id();
        if (id.isEmpty()) {
            return false;
        }
        repository.deleteById(id.get());
        events.publish(new PlayerWarpDeleted(warp.owner(), warp.name()));
        return true;
    }

    private Instant cutoff() {
        return clock.instant().minus(config.hardDeleteAfter());
    }

    private static boolean qualifies(PlayerWarp warp, Instant cutoff) {
        if (warp.status() != WarpStatus.ARCHIVED || warp.updatedAt().isAfter(cutoff)) {
            return false;
        }
        return warp.rent()
                .flatMap(RentState::archiveAfter)
                .map(deadline -> !deadline.isAfter(cutoff))
                .orElse(false);
    }
}
