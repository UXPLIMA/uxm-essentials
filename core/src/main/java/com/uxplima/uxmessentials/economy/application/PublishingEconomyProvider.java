package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import com.uxplima.uxmessentials.economy.domain.event.WalletRejected;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;

/**
 * The provider every caller sees, raising the economy's events for each change of money it passes on.
 *
 * <p>The wallet aggregate raises these events, and it was never on the path money takes: the ledger moves money in
 * one guarded statement, and a foreign backend moves it in another plugin. So the events the developer API publishes
 * for a credit, a debit and a refusal never fired, and a plugin keeping its own ledger from them heard nothing. This
 * sits in front of whichever provider is in play, so a change made by a command, by the API and by a plugin through
 * Vault each raise one.
 *
 * <p>The balance an event carries is read back after the change, because the ledger's guarded statement does not
 * return one. A transfer carries the two transactions the provider already minted, so it reads nothing.
 */
@NullMarked
public final class PublishingEconomyProvider implements EconomyProvider {

    private final EconomyProvider inner;
    private final DomainEventPublisher events;
    private final Clock clock;

    public PublishingEconomyProvider(EconomyProvider inner, DomainEventPublisher events, Clock clock) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The provider this one publishes for, which decides where the money actually lives. */
    public EconomyProvider inner() {
        return inner;
    }

    @Override
    public boolean hasAccount(PlayerRef owner, Currency currency) {
        return inner.hasAccount(owner, currency);
    }

    @Override
    public void ensureAccount(PlayerRef owner, Currency currency) {
        inner.ensureAccount(owner, currency);
    }

    @Override
    public Money balance(PlayerRef owner, Currency currency) {
        return inner.balance(owner, currency);
    }

    @Override
    public Result<Unit, TransferError> credit(PlayerRef owner, Money amount) {
        Result<Unit, TransferError> result = inner.credit(owner, amount);
        Instant now = clock.instant();
        Money resulting = inner.balance(owner, amount.currency());
        if (result.isOk()) {
            events.publish(new WalletCredited(
                    owner, amount, resulting, Transaction.credit(owner, amount, resulting, now), now));
        } else if (result.errorOrThrow() == TransferError.BALANCE_MAX_EXCEEDED) {
            events.publish(new WalletRejected(owner, amount, resulting, EconomyError.BALANCE_MAX_EXCEEDED, now));
        }
        return result;
    }

    @Override
    public Result<Unit, TransferError> debit(PlayerRef owner, Money amount) {
        Result<Unit, TransferError> result = inner.debit(owner, amount);
        Instant now = clock.instant();
        Money resulting = inner.balance(owner, amount.currency());
        if (result.isOk()) {
            events.publish(
                    new WalletDebited(owner, amount, resulting, Transaction.debit(owner, amount, resulting, now), now));
        } else if (result.errorOrThrow() == TransferError.INSUFFICIENT_FUNDS) {
            events.publish(new WalletRejected(owner, amount, resulting, EconomyError.INSUFFICIENT_FUNDS, now));
        }
        return result;
    }

    @Override
    public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
        TransferResult result = inner.transfer(from, to, amount);
        Instant now = clock.instant();
        switch (result) {
            case TransferResult.Allow(Transaction debit, Transaction credit) -> {
                events.publish(new WalletDebited(from, debit.amount(), debit.resulting(), debit, now));
                events.publish(new WalletCredited(to, credit.amount(), credit.resulting(), credit, now));
            }
            case TransferResult.InsufficientFunds(Money required, Money available) ->
                events.publish(new WalletRejected(from, required, available, EconomyError.INSUFFICIENT_FUNDS, now));
            case TransferResult.DenyWith denied -> {
                // Refused before any money was looked at: a self payment, a closed currency. Nothing changed.
            }
        }
        return result;
    }

    @Override
    public List<BaltopRow> top(Currency currency, int limit) {
        return inner.top(currency, limit);
    }

    @Override
    public Set<Currency> currencies() {
        return inner.currencies();
    }
}
