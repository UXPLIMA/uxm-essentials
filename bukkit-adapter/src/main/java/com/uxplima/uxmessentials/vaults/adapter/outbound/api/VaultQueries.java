package com.uxplima.uxmessentials.vaults.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmVaultsQuery;
import com.uxplima.uxmessentials.api.view.UxmVault;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;
import org.jspecify.annotations.NullMarked;

/**
 * The published vault query, over the same repository and quotas the commands use.
 *
 * <p>Summaries rather than whole vaults: the repository can read a player's numbers, names and icons without
 * deserialising a single item stack, and that is all a list or a selector shows. A consumer that wants the
 * contents should open the vault through the plugin, which is where the item policy and the audit record live.
 */
@NullMarked
public final class VaultQueries implements UxmVaultsQuery {

    private final VaultRepository repository;
    private final VaultAmountQuota amounts;
    private final VaultSizeQuota sizes;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public VaultQueries(
            VaultRepository repository,
            VaultAmountQuota amounts,
            VaultSizeQuota sizes,
            PlayerLookup players,
            Scheduler scheduler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.amounts = Objects.requireNonNull(amounts, "amounts");
        this.sizes = Objects.requireNonNull(sizes, "sizes");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<List<UxmVault>> list(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return AsyncQueries.supply(
                scheduler,
                () -> repository.summaries(subject(ownerId)).stream()
                        .map(summary -> view(ownerId, summary))
                        .toList());
    }

    @Override
    public CompletableFuture<Optional<UxmVault>> get(UUID ownerId, int index) {
        Objects.requireNonNull(ownerId, "ownerId");
        if (index < 1) {
            throw new IllegalArgumentException("vault numbers count from one: " + index);
        }
        return AsyncQueries.supply(
                scheduler,
                () -> repository.summaries(subject(ownerId)).stream()
                        .filter(summary -> summary.index() == index)
                        .findFirst()
                        .map(summary -> view(ownerId, summary)));
    }

    @Override
    public CompletableFuture<Integer> count(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return AsyncQueries.supply(scheduler, () -> repository.count(subject(ownerId)));
    }

    @Override
    public CompletableFuture<Optional<Integer>> limit(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return AsyncQueries.supply(scheduler, () -> {
            VaultAmount resolved = amounts.resolve(subject(ownerId));
            return resolved.unlimited() ? Optional.<Integer>empty() : Optional.of(resolved.cap());
        });
    }

    @Override
    public CompletableFuture<Integer> rows(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return AsyncQueries.supply(
                scheduler, () -> sizes.resolve(subject(ownerId)).rows());
    }

    private PlayerRef subject(UUID ownerId) {
        return ApiValues.subject(players, ownerId);
    }

    private static UxmVault view(UUID ownerId, VaultSummary summary) {
        return new UxmVault(
                ownerId,
                summary.index(),
                Optional.ofNullable(summary.displayName()),
                Optional.ofNullable(summary.iconMaterial()));
    }
}
