package com.uxplima.uxmessentials.economy.adapter.outbound.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmEconomyActions;
import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmResult;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiActors;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The published economy actions, over the same admin use case {@code /eco} runs.
 *
 * <p>Through {@link EcoAdmin} rather than the provider directly, so an API write is audited, recorded in the
 * transaction history and clamped by the currency's limits exactly as an operator's {@code /eco give} would be.
 * The acting ref names the calling plugin, so the audit line says which one moved the money.
 *
 * <p>The transfer is the one operation that does not go through the admin surface: moving money between two
 * players is a two-sided commit that only {@link EconomyProvider#transfer} performs atomically, and splitting it
 * into a take and a give would leave a window in which the money exists nowhere.
 */
@NullMarked
public final class EconomyActions implements UxmEconomyActions {

    private final EcoAdmin admin;
    private final EconomyProvider provider;
    private final CurrencyRegistry currencies;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final PlayerRef actor;

    public EconomyActions(
            EcoAdmin admin,
            EconomyProvider provider,
            CurrencyRegistry currencies,
            PlayerLookup players,
            Scheduler scheduler,
            String source) {
        this.admin = Objects.requireNonNull(admin, "admin");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.actor = ApiActors.of(Objects.requireNonNull(source, "source"));
    }

    @Override
    public CompletableFuture<UxmResult<UxmMoney>> deposit(UUID playerId, BigDecimal amount) {
        return mutate(playerId, amount, currencies.defaultCurrency().id().value(), admin::give);
    }

    @Override
    public CompletableFuture<UxmResult<UxmMoney>> deposit(UUID playerId, BigDecimal amount, String currency) {
        return mutate(playerId, amount, currency, admin::give);
    }

    @Override
    public CompletableFuture<UxmResult<UxmMoney>> withdraw(UUID playerId, BigDecimal amount) {
        return mutate(playerId, amount, currencies.defaultCurrency().id().value(), admin::take);
    }

    @Override
    public CompletableFuture<UxmResult<UxmMoney>> withdraw(UUID playerId, BigDecimal amount, String currency) {
        return mutate(playerId, amount, currency, admin::take);
    }

    @Override
    public CompletableFuture<UxmResult<UxmMoney>> set(UUID playerId, BigDecimal amount) {
        return mutate(playerId, amount, currencies.defaultCurrency().id().value(), admin::set);
    }

    @Override
    public CompletableFuture<UxmResult<UxmMoney>> set(UUID playerId, BigDecimal amount, String currency) {
        return mutate(playerId, amount, currency, admin::set);
    }

    @Override
    public CompletableFuture<UxmOutcome> transfer(UUID fromId, UUID toId, BigDecimal amount) {
        return transfer(fromId, toId, amount, currencies.defaultCurrency().id().value());
    }

    @Override
    public CompletableFuture<UxmOutcome> transfer(UUID fromId, UUID toId, BigDecimal amount, String currency) {
        Objects.requireNonNull(fromId, "fromId");
        Objects.requireNonNull(toId, "toId");
        Objects.requireNonNull(currency, "currency");
        requireNotNegative(amount);
        return AsyncActions.perform(
                scheduler,
                () -> find(currency)
                        .map(found -> move(fromId, toId, Money.of(found, amount)))
                        .orElseGet(() -> UxmOutcome.failed(unknownCurrency(currency))));
    }

    /**
     * The shape every single-sided verb shares: resolve the currency, run the admin use case, and answer with the
     * balance it left behind. A refusal from the ledger is a failure value; the read afterwards is the same one
     * {@code /balance} would make, so the caller does not have to follow up with a query of their own.
     */
    private CompletableFuture<UxmResult<UxmMoney>> mutate(
            UUID playerId, BigDecimal amount, String currency, AdminWrite write) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(currency, "currency");
        requireNotNegative(amount);
        return AsyncActions.perform(scheduler, () -> {
            Optional<Currency> found = find(currency);
            if (found.isEmpty()) {
                return UxmResult.failed(unknownCurrency(currency));
            }
            Currency target = found.get();
            PlayerRef subject = ApiValues.subject(players, playerId);
            Result<Unit, TransferError> result = write.apply(actor, subject, Money.of(target, amount));
            if (result.isErr()) {
                return UxmResult.failed(failure(result.errorOrThrow()));
            }
            return UxmResult.ok(money(provider.balance(subject, target)));
        });
    }

    private UxmOutcome move(UUID fromId, UUID toId, Money amount) {
        TransferResult result =
                provider.transfer(ApiValues.subject(players, fromId), ApiValues.subject(players, toId), amount);
        return switch (result) {
            case TransferResult.Allow ignored -> UxmOutcome.ok();
            case TransferResult.InsufficientFunds shortfall ->
                UxmOutcome.failed(
                        UxmFailure.INSUFFICIENT_FUNDS,
                        "holds " + shortfall.available().amount() + " of the "
                                + shortfall.required().amount() + " needed");
            case TransferResult.DenyWith denied ->
                UxmOutcome.failed(
                        UxmFailure.REFUSED,
                        "the server refused the transfer: " + denied.reason().key());
        };
    }

    /** Which published code a ledger refusal is. Only two of them are about the money itself. */
    private static UxmFailure failure(TransferError error) {
        return switch (error) {
            case INSUFFICIENT_FUNDS -> UxmFailure.of(UxmFailure.INSUFFICIENT_FUNDS, "the balance does not cover it");
            case PLAYER_OFFLINE -> UxmFailure.of(UxmFailure.PLAYER_OFFLINE, "the player has to be online for this");
            default ->
                UxmFailure.of(
                        UxmFailure.REFUSED,
                        "the economy refused it: "
                                + error.name()
                                        .toLowerCase(java.util.Locale.ROOT)
                                        .replace('_', '-'));
        };
    }

    private static UxmFailure unknownCurrency(String currency) {
        return UxmFailure.of(UxmFailure.NOT_FOUND, "no currency with the id " + currency);
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
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    private static UxmMoney money(Money amount) {
        return new UxmMoney(amount.currency().id().value(), amount.amount());
    }

    /** The signature the three single-sided admin verbs share, so one method can run any of them. */
    @FunctionalInterface
    private interface AdminWrite {

        Result<Unit, TransferError> apply(PlayerRef actor, PlayerRef target, Money amount);
    }
}
