package com.uxplima.uxmessentials.playerwarps.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmPlayerWarpsQuery;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.api.view.UxmPlayerWarp;
import com.uxplima.uxmessentials.api.view.UxmPlayerWarpAccess;
import com.uxplima.uxmessentials.api.view.UxmPlayerWarpStatus;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpBrowse;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpLimit;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;
import com.uxplima.uxmessentials.playerwarps.domain.WarpSort;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.NullMarked;

/**
 * The published player-warp query, over the same repository, read model and quota the commands use.
 *
 * <p>The public listing goes through the paged read model rather than loading every warp: on a busy server the
 * table holds tens of thousands of rows, and the one thing this query must never be is a full-table scan wearing
 * an innocent name. The page comes back as cards, which carry only what a browser tile shows, so each card is
 * resolved to its full warp afterwards; the page size is capped at a hundred, which bounds that fan-out.
 *
 * <p>Nothing here publishes a password or a whitelist. Whether a password is set is a fact a browser needs;
 * what it is, and who is on the list, is the owner's business.
 */
@NullMarked
public final class PlayerWarpQueries implements UxmPlayerWarpsQuery {

    /** Not a real player. The read model wants a viewer to mark favourites with, and an API caller is nobody. */
    private static final UUID NOBODY = new UUID(0L, 0L);

    private final PlayerWarpRepository repository;
    private final PlayerWarpBrowse browse;
    private final PlayerWarpQuota quota;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public PlayerWarpQueries(
            PlayerWarpRepository repository,
            PlayerWarpBrowse browse,
            PlayerWarpQuota quota,
            PlayerLookup players,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.browse = Objects.requireNonNull(browse, "browse");
        this.quota = Objects.requireNonNull(quota, "quota");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmPlayerWarp>> listPublic(int page, int pageSize) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        if (pageSize < 1 || pageSize > WarpQuery.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be in 1.." + WarpQuery.MAX_PAGE_SIZE + ": " + pageSize);
        }
        return AsyncQueries.supply(
                scheduler,
                () -> browse.page(WarpQuery.publicBrowse(NOBODY, WarpSort.NEWEST, page, pageSize)).items().stream()
                        .map(WarpCard::id)
                        .map(repository::findById)
                        .flatMap(Optional::stream)
                        .map(PlayerWarpQueries::view)
                        .toList());
    }

    @Override
    public CompletableFuture<Optional<UxmPlayerWarp>> get(String name) {
        Objects.requireNonNull(name, "name");
        return AsyncQueries.supply(
                scheduler, () -> parse(name).flatMap(repository::findByName).map(PlayerWarpQueries::view));
    }

    @Override
    public CompletableFuture<List<UxmPlayerWarp>> ownedBy(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return AsyncQueries.supply(
                scheduler,
                () -> repository.ownedBy(ApiValues.subject(players, ownerId)).stream()
                        .map(PlayerWarpQueries::view)
                        .toList());
    }

    @Override
    public CompletableFuture<Integer> count(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return AsyncQueries.supply(scheduler, () -> repository.count(ApiValues.subject(players, ownerId)));
    }

    @Override
    public CompletableFuture<Optional<Integer>> limit(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return AsyncQueries.supply(scheduler, () -> {
            // No world: the per-world form of the node only narrows the answer, and a query has no world in hand.
            PlayerWarpLimit resolved = quota.resolve(ApiValues.subject(players, ownerId), null);
            return resolved.unlimited() ? Optional.<Integer>empty() : Optional.of(resolved.cap());
        });
    }

    private static Optional<PlayerWarpName> parse(String name) {
        try {
            return Optional.of(new PlayerWarpName(name));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }

    private static UxmPlayerWarp view(PlayerWarp warp) {
        return new UxmPlayerWarp(
                warp.id().map(id -> id.value()).orElse(0L),
                warp.name().value(),
                warp.displayName().map(DisplayName::value),
                warp.owner().uuid(),
                warp.ownerName(),
                ApiValues.location(warp.location()),
                warp.serverId(),
                warp.categoryId(),
                warp.description().map(WarpDescription::value),
                warp.icon().map(IconSpec::value),
                access(warp.access()),
                warp.passwordSet(),
                status(warp.status()),
                price(warp.price()),
                warp.ratings().average(),
                warp.ratings().count(),
                warp.visits().count(),
                warp.visits().uniqueVisitors(),
                warp.favouriteCount(),
                warp.sponsorship().map(sponsorship -> sponsorship.activeUntil()),
                warp.rent().map(rent -> rent.paidUntil()),
                warp.createdAt(),
                warp.updatedAt());
    }

    private static Optional<UxmMoney> price(WarpCost price) {
        return price.isFree() ? Optional.empty() : Optional.of(new UxmMoney(price.currencyId(), price.amount()));
    }

    private static UxmPlayerWarpAccess access(WarpAccess access) {
        return switch (access) {
            case PUBLIC -> UxmPlayerWarpAccess.PUBLIC;
            case PASSWORD -> UxmPlayerWarpAccess.PASSWORD;
            case WHITELIST -> UxmPlayerWarpAccess.WHITELIST;
            case PRIVATE -> UxmPlayerWarpAccess.PRIVATE;
        };
    }

    private static UxmPlayerWarpStatus status(WarpStatus status) {
        return switch (status) {
            case ACTIVE -> UxmPlayerWarpStatus.ACTIVE;
            case SUSPENDED -> UxmPlayerWarpStatus.SUSPENDED;
            case ARCHIVED -> UxmPlayerWarpStatus.ARCHIVED;
        };
    }
}
