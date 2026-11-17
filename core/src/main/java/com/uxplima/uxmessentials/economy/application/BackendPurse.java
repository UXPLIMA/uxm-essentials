package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.ItemPurse;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ItemPurse} of a currency backend with no offline accounts, such as experience: the backend moves to the
 * player's thread by itself, and a change it could not make (the player is not here, or did not answer) is reported
 * as not done, so the delivery waits. A give lands whole or not at all.
 */
@NullMarked
public final class BackendPurse implements ItemPurse {

    private final CurrencyBackend backend;

    public BackendPurse(CurrencyBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public Optional<Change> give(PlayerRef owner, Money amount) {
        Optional<BigDecimal> before = balance(owner, amount);
        if (before.isEmpty()) {
            return Optional.empty();
        }
        return backend.credit(owner, amount).isOk()
                ? Optional.of(new Change(before.get(), before.get().add(amount.amount())))
                : Optional.empty();
    }

    @Override
    public Optional<Change> setTo(PlayerRef owner, Money target) {
        Optional<BigDecimal> read = balance(owner, target);
        if (read.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal before = read.get();
        BigDecimal diff = target.amount().subtract(before);
        if (diff.signum() == 0) {
            return Optional.of(new Change(before, before));
        }
        Money delta = Money.of(target.currency(), diff.abs());
        boolean done = diff.signum() > 0
                ? backend.credit(owner, delta).isOk()
                : backend.debit(owner, delta).isOk();
        return done ? Optional.of(new Change(before, target.amount())) : Optional.empty();
    }

    /** The balance before a change; empty when it did not answer, which is not zero, so nothing is changed. */
    private Optional<BigDecimal> balance(PlayerRef owner, Money of) {
        try {
            return Optional.of(backend.balance(owner, of.currency()).amount());
        } catch (RuntimeException unknown) {
            return Optional.empty();
        }
    }
}
