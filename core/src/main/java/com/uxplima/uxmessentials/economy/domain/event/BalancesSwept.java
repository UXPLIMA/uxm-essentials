package com.uxplima.uxmessentials.economy.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A sweep over every account finished: {@code /eco giveall} or {@code /eco resetall}. Raised once per sweep, by the
 * runner that ran its last chunk, rather than once per account, which on a large ledger would be thousands of events
 * for one command.
 *
 * @param sweepId the sweep's id in the journal
 * @param kind whether the amount was added or set
 * @param scope which accounts it reached
 * @param amount the amount added, or the balance every account was set to
 * @param accounts the accounts whose balance changed
 * @param skipped the accounts left as they were because the amount would pass the currency's maximum
 * @param actor who asked for it
 * @param occurredAt when the last chunk committed
 */
public record BalancesSwept(
        String sweepId,
        SweepKind kind,
        SweepScope scope,
        Money amount,
        int accounts,
        int skipped,
        PlayerRef actor,
        Instant occurredAt)
        implements EconomyEvent {

    public BalancesSwept {
        Objects.requireNonNull(sweepId, "sweepId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
