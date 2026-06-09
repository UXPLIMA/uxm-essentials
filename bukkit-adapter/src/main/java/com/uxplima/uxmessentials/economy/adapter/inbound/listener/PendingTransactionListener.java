package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.math.BigDecimal;
import java.util.*;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository.PendingTransaction;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Listens for players joining the server to apply any queued credits that occurred while they were offline.
 */
@NullMarked
public final class PendingTransactionListener implements Listener {

    private final EconomyProvider economy;
    private final PendingTransactionRepository pendingRepo;
    private final EconomyNotifier notifier;
    private final Scheduler scheduler;

    public PendingTransactionListener(
            EconomyProvider economy,
            PendingTransactionRepository pendingRepo,
            EconomyNotifier notifier,
            Scheduler scheduler) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.pendingRepo = Objects.requireNonNull(pendingRepo, "pendingRepo");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerRef ref = BukkitRefs.toRef(event.getPlayer());

        // Run delayed by 500ms using Folia-aware Scheduler to ensure player is fully initialized and can receive items
        // safely
        scheduler.asyncAfter(java.time.Duration.ofMillis(500), () -> {
            scheduler.onEntity(ref, () -> {
                List<PendingTransaction> pending = pendingRepo.getAndClearPending(ref.uuid());
                if (pending.isEmpty()) {
                    return;
                }

                // Aggregate by currency to avoid multiple messages/credits if there are multiple pending credits
                Map<String, BigDecimal> totals = new LinkedHashMap<>();
                for (PendingTransaction tx : pending) {
                    totals.put(
                            tx.currencyId(),
                            totals.getOrDefault(tx.currencyId(), BigDecimal.ZERO)
                                    .add(tx.amount()));
                }

                for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
                    String currencyId = entry.getKey();
                    BigDecimal amount = entry.getValue();

                    Optional<Currency> matchedCurrency = economy.currencies().stream()
                            .filter(c -> c.id().value().equalsIgnoreCase(currencyId))
                            .findFirst();

                    if (matchedCurrency.isPresent()) {
                        Currency currency = matchedCurrency.get();
                        Money money = Money.of(currency, amount);

                        // Directly credit player (decorated economy provider will route to inventory)
                        economy.credit(ref, money);

                        // Notify player
                        notifier.send(
                                ref,
                                EconomyMessageKey.PHYSICAL_PENDING_RECEIVED,
                                Map.of("amount", notifier.amount(money)));
                    }
                }
            });
        });
    }
}
