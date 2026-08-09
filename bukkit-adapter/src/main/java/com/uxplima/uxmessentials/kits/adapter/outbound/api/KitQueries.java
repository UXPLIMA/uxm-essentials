package com.uxplima.uxmessentials.kits.adapter.outbound.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmKitsQuery;
import com.uxplima.uxmessentials.api.view.UxmKit;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published kit query, over the catalogue the commands read and the same gates they apply.
 *
 * <p>The catalogue is loaded from configuration on enable and held in memory, so the two catalogue methods answer
 * on the calling thread. Anything that names a player does not: their cooldown stamps and one-time marks are
 * stored, and reading them is a stored read like any other.
 *
 * <p>Whether a player may claim goes through {@code KitAccess}, the very object the command consults, rather than
 * re-checking the node and the stamp here. This one has no side effect: it reserves no stock and charges nothing.
 */
@NullMarked
public final class KitQueries implements UxmKitsQuery {

    private final KitRepository repository;
    private final KitAccess access;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public KitQueries(KitRepository repository, KitAccess access, PlayerLookup players, Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public List<UxmKit> list() {
        return repository.all().stream().map(KitQueries::view).toList();
    }

    @Override
    public Optional<UxmKit> get(String kitId) {
        Objects.requireNonNull(kitId, "kitId");
        return parse(kitId).flatMap(repository::find).map(KitQueries::view);
    }

    @Override
    public CompletableFuture<Optional<Duration>> cooldownRemaining(UUID playerId, String kitId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        return AsyncQueries.supply(
                scheduler,
                () -> definition(kitId)
                        .flatMap(kit -> access.remaining(subject(playerId), kit).asError()));
    }

    @Override
    public CompletableFuture<Boolean> canClaim(UUID playerId, String kitId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(kitId, "kitId");
        return AsyncQueries.supply(
                scheduler,
                () -> definition(kitId)
                        .filter(kit -> claimable(subject(playerId), kit))
                        .isPresent());
    }

    @Override
    public CompletableFuture<List<UxmKit>> claimableBy(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> {
            PlayerRef who = subject(playerId);
            return repository.all().stream()
                    .filter(kit -> claimable(who, kit))
                    .map(KitQueries::view)
                    .toList();
        });
    }

    /**
     * Every gate the command applies, in the order it applies them, minus the two that would change something.
     * Stock and price are checked rather than reserved and charged, which is the difference between asking and
     * taking.
     */
    private boolean claimable(PlayerRef who, KitDefinition kit) {
        return access.admissible(who, kit).isOk() && !access.isOutOfStock(kit) && access.canAfford(who, kit);
    }

    private Optional<KitDefinition> definition(String kitId) {
        return parse(kitId).flatMap(repository::find);
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, playerId);
    }

    private static Optional<KitId> parse(String kitId) {
        try {
            return Optional.of(KitId.of(kitId));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }

    private static UxmKit view(KitDefinition kit) {
        return new UxmKit(
                kit.id().value(),
                kit.display().name().orElseGet(() -> kit.id().value()),
                kit.cooldown(),
                kit.oneTime(),
                kit.requiresPermission(),
                kit.requiresPermission() ? Optional.of(kit.permissionNode()) : Optional.empty(),
                cost(kit.cost()),
                kit.categoryId(),
                kit.items().size(),
                kit.firstJoin(),
                kit.stockLimit() > 0 ? Optional.of(kit.stockLimit()) : Optional.empty());
    }

    private static Optional<UxmMoney> cost(KitCost cost) {
        return cost.isFree() ? Optional.empty() : Optional.of(new UxmMoney(cost.currencyId(), cost.amount()));
    }
}
