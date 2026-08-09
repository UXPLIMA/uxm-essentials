package com.uxplima.uxmessentials.homes.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published homes query, over the same repository and quota the commands use.
 *
 * <p>Reads only, and off the calling thread: the repository is Caffeine-backed but a cold read still goes to the
 * database, and a consumer asking from an event handler must not pay for that on a tick thread.
 *
 * <p>The limit is resolved rather than read from config, so it is the number {@code /sethome} would actually enforce
 * for that player: their permission nodes, their world, and the configured default, in that order.
 */
@NullMarked
public final class HomeQueries implements UxmHomesQuery {

    private final HomeRepository repository;
    private final HomeQuota quota;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public HomeQueries(HomeRepository repository, HomeQuota quota, PlayerLookup players, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmHome>> list(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(
                scheduler,
                () -> repository.load(subject(playerId)).all().stream()
                        .map(HomeQueries::view)
                        .toList());
    }

    @Override
    public CompletableFuture<Optional<UxmHome>> get(UUID playerId, int slot) {
        Objects.requireNonNull(playerId, "playerId");
        if (slot < 0) {
            throw new IllegalArgumentException("home slot must not be negative: " + slot);
        }
        return AsyncQueries.supply(
                scheduler,
                () -> repository.findSlot(subject(playerId), HomeSlot.of(slot)).map(HomeQueries::view));
    }

    @Override
    public CompletableFuture<Integer> count(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> repository.count(subject(playerId)));
    }

    @Override
    public CompletableFuture<Optional<Integer>> limit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> {
            // No world: the per-world quota only narrows the answer, and a query has no world in hand. This is the
            // player's limit anywhere, which is the one a consumer can act on.
            HomeLimit resolved = quota.resolve(subject(playerId), null);
            return resolved.unlimited() ? Optional.<Integer>empty() : Optional.of(resolved.cap());
        });
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, playerId);
    }

    private static UxmHome view(Home home) {
        return new UxmHome(
                home.owner().uuid(),
                home.slot().index(),
                ApiValues.location(home.location()),
                home.label().map(HomeLabel::value),
                home.icon().map(HomeIcon::materialName),
                home.isPublic(),
                home.createdAt(),
                home.updatedAt());
    }
}
