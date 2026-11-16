package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository.PendingTransaction;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Pays a joining player the credits queued for them while they were away (a physical currency cannot reach an
 * offline inventory, so its credits wait in the pending queue).
 *
 * <p>Runs on the async lane: the queue is a database table, and the provider marshals a physical credit onto the
 * player's own thread by itself. The rows leave the queue in one atomic take, so a credit that does not land is put
 * back for the next join rather than dropped, and each queued amount is paid at most once.
 */
@NullMarked
public final class DeliverPendingCredits {

    private final EconomyProvider economy;
    private final PendingTransactionRepository pending;
    private final EconomyNotifier notifier;
    private final Logger log;

    public DeliverPendingCredits(
            EconomyProvider economy, PendingTransactionRepository pending, EconomyNotifier notifier, Logger log) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Take {@code player}'s queued credits and pay each currency's total; off the tick. */
    public void deliver(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        List<PendingTransaction> queued;
        try {
            queued = pending.getAndClearPending(player.uuid());
        } catch (RuntimeException failure) {
            // The take is atomic, so the rows are still queued and the next join tries again.
            log.error("event=pending_credit_read_failed player=" + player.uuid(), failure);
            return;
        }
        // One credit and one notice per currency, however many rows were queued for it.
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (PendingTransaction row : queued) {
            totals.merge(row.currencyId(), row.amount(), BigDecimal::add);
        }
        totals.forEach((currencyId, amount) -> apply(player, currencyId, amount));
    }

    /**
     * Pay one currency's total, or put it back on the queue: an unknown currency (the config changed since it was
     * queued), a credit the provider refuses (a full inventory, a player who left) and a credit that throws all
     * leave the money waiting for the next join. The player is told only once the credit has landed.
     */
    private void apply(PlayerRef player, String currencyId, BigDecimal amount) {
        Optional<Currency> currency = economy.currencies().stream()
                .filter(known -> known.id().value().equalsIgnoreCase(currencyId))
                .findFirst();
        if (currency.isEmpty()) {
            requeue(player, currencyId, amount);
            return;
        }
        Money money = Money.of(currency.get(), amount);
        boolean landed;
        try {
            // The queued credit's cause (trade, admin give, banknote) is lost on the queue, so the replay is not
            // counted.
            landed = UncountedMoney.call(() -> economy.credit(player, money)).isOk();
        } catch (RuntimeException failure) {
            log.error("event=pending_credit_failed player=" + player.uuid() + " currency=" + currencyId, failure);
            landed = false;
        }
        if (!landed) {
            requeue(player, currencyId, amount);
            return;
        }
        try {
            notifier.send(
                    player, EconomyMessageKey.PHYSICAL_PENDING_RECEIVED, Map.of("amount", notifier.amount(money)));
        } catch (RuntimeException failure) {
            // The money has landed; a notice that cannot be sent must not put it back on the queue.
            log.error("event=pending_credit_notice_failed player=" + player.uuid(), failure);
        }
    }

    private void requeue(PlayerRef player, String currencyId, BigDecimal amount) {
        try {
            pending.queueCredit(player.uuid(), currencyId, amount);
        } catch (RuntimeException failure) {
            log.error(
                    "event=pending_credit_requeue_failed player=" + player.uuid() + " currency=" + currencyId
                            + " amount=" + amount + "; the player is owed that amount",
                    failure);
        }
    }
}
