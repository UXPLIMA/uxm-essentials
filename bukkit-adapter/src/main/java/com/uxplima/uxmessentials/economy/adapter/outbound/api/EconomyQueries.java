package com.uxplima.uxmessentials.economy.adapter.outbound.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.view.UxmBaltopEntry;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The published economy query, over the same provider the commands use.
 *
 * <p>Through the provider rather than the ledger, so a server running Vault or Treasury against a third-party
 * economy answers with that plugin's figures instead of the balances our own tables happen to hold.
 *
 * <p>The leaderboard reads the same periodically refreshed snapshot {@code /baltop} prints, exempt players
 * already filtered out. Recomputing it per call would put a whole-table sort behind a method any consumer can call
 * in a loop, which is the shape of an accidental denial of service.
 */
@NullMarked
public final class EconomyQueries implements UxmEconomyQuery {

    private final EconomyProvider provider;
    private final CurrencyRegistry currencies;
    private final BaltopSnapshots leaderboard;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public EconomyQueries(
            EconomyProvider provider,
            CurrencyRegistry currencies,
            BaltopSnapshots leaderboard,
            PlayerLookup players,
            Scheduler scheduler) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.leaderboard = Objects.requireNonNull(leaderboard, "leaderboard");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public List<String> currencies() {
        List<String> ids = new ArrayList<>();
        ids.add(currencies.defaultCurrency().id().value());
        currencies.ids().stream()
                .map(CurrencyId::value)
                .filter(id -> !id.equals(ids.getFirst()))
                .sorted()
                .forEach(ids::add);
        return List.copyOf(ids);
    }

    @Override
    public CompletableFuture<UxmMoney> balance(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> money(read(playerId, currencies.defaultCurrency())));
    }

    @Override
    public CompletableFuture<Optional<UxmMoney>> balance(UUID playerId, String currency) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(currency, "currency");
        return AsyncQueries.supply(scheduler, () -> find(currency).map(found -> money(read(playerId, found))));
    }

    @Override
    public CompletableFuture<List<UxmMoney>> balances(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(
                scheduler,
                () -> currencies().stream()
                        .map(this::find)
                        .flatMap(Optional::stream)
                        .map(currency -> money(read(playerId, currency)))
                        .toList());
    }

    @Override
    public CompletableFuture<Boolean> canAfford(UUID playerId, BigDecimal amount) {
        Objects.requireNonNull(playerId, "playerId");
        requireNotNegative(amount);
        return AsyncQueries.supply(scheduler, () -> holds(playerId, currencies.defaultCurrency(), amount));
    }

    @Override
    public CompletableFuture<Boolean> canAfford(UUID playerId, BigDecimal amount, String currency) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(currency, "currency");
        requireNotNegative(amount);
        return AsyncQueries.supply(
                scheduler,
                () -> find(currency)
                        .map(found -> holds(playerId, found, amount))
                        .orElse(false));
    }

    @Override
    public CompletableFuture<List<UxmBaltopEntry>> top(int limit) {
        return top(currencies.defaultCurrency(), limit);
    }

    @Override
    public CompletableFuture<List<UxmBaltopEntry>> top(String currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        requirePositive(limit);
        Optional<Currency> found = find(currency);
        if (found.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return top(found.get(), limit);
    }

    private CompletableFuture<List<UxmBaltopEntry>> top(Currency currency, int limit) {
        requirePositive(limit);
        // The snapshot is already in memory and lock-free to read, so this answers without a hop.
        List<BaltopRow> rows = leaderboard.top(currency, limit);
        List<UxmBaltopEntry> ranked = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            BaltopRow row = rows.get(index);
            ranked.add(new UxmBaltopEntry(
                    index + 1, row.owner().uuid(), row.owner().name(), money(row.balance())));
        }
        return CompletableFuture.completedFuture(List.copyOf(ranked));
    }

    private Money read(UUID playerId, Currency currency) {
        return provider.balance(ApiValues.subject(players, playerId), currency);
    }

    private boolean holds(UUID playerId, Currency currency, BigDecimal amount) {
        return read(playerId, currency).amount().compareTo(amount) >= 0;
    }

    private Optional<Currency> find(String currency) {
        try {
            return currencies.find(CurrencyId.of(currency));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }

    private static void requireNotNegative(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
    }

    private static void requirePositive(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least one: " + limit);
        }
    }

    private static UxmMoney money(Money amount) {
        return new UxmMoney(amount.currency().id().value(), amount.amount());
    }
}
