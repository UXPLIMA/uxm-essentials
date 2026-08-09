package com.uxplima.uxmessentials.warps.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmWarpsQuery;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.api.view.UxmWarp;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * The published server-warp query, over the same repository the commands use.
 *
 * <p>{@code visibleTo} goes through the very use case {@code /warps} runs rather than re-implementing its filter,
 * so a warp gated behind a permission node is hidden from the API exactly when it is hidden from the player. A
 * filter written twice is a filter that eventually disagrees with itself.
 *
 * <p>A name that is not a legal warp name is an absent warp rather than an exception: a consumer passing along
 * something a player typed should get "no such warp", which is also what the command says.
 */
@NullMarked
public final class WarpQueries implements UxmWarpsQuery {

    private final WarpRepository repository;
    private final ListWarps listWarps;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public WarpQueries(WarpRepository repository, ListWarps listWarps, PlayerLookup players, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.listWarps = Objects.requireNonNull(listWarps, "listWarps");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmWarp>> list() {
        return AsyncQueries.supply(
                scheduler,
                () -> repository.all().stream().map(WarpQueries::view).toList());
    }

    @Override
    public CompletableFuture<Optional<UxmWarp>> get(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncQueries.supply(
                scheduler, () -> parse(name).flatMap(repository::find).map(WarpQueries::view));
    }

    @Override
    public CompletableFuture<List<UxmWarp>> visibleTo(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(
                scheduler,
                () -> listWarps.available(ApiValues.subject(players, playerId)).stream()
                        .map(WarpQueries::view)
                        .toList());
    }

    @Override
    public CompletableFuture<Boolean> exists(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncQueries.supply(
                scheduler, () -> parse(name).map(repository::exists).orElse(false));
    }

    @Override
    public CompletableFuture<Double> averageRating(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncQueries.supply(
                scheduler, () -> parse(name).map(repository::averageRating).orElse(0.0));
    }

    /** A name as the domain sees it, or empty when nothing could be called that; shared with the write surface. */
    static Optional<WarpName> parse(String name) {
        try {
            return Optional.of(WarpName.of(name));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }

    /** A warp as the API publishes it; shared with the write surface, which answers with what it just wrote. */
    static UxmWarp view(Warp warp) {
        return new UxmWarp(
                warp.name().value(),
                ApiValues.location(warp.location()),
                warp.owner().uuid(),
                warp.owner().name(),
                warp.createdAt(),
                cost(warp.cost()),
                warp.requiredPermission(),
                warp.visitors(),
                warp.isLocked(),
                warp.password().isPresent(),
                warp.categoryId(),
                warp.iconMaterial());
    }

    private static Optional<UxmMoney> cost(WarpCost cost) {
        return cost.isFree() ? Optional.empty() : Optional.of(new UxmMoney(cost.currencyId(), cost.amount()));
    }
}
