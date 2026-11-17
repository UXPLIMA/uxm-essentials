package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Port representing the pending queue for physical transactions destined for offline players.
 */
public interface PendingTransactionRepository {

    /**
     * Queues a credit transaction for an offline player.
     * If there is an existing pending credit for the same player and currency, they can accumulate. The player's
     * name travels with the credit because the queue may be the first record the economy keeps of them.
     */
    void queueCredit(PlayerRef player, String currencyId, BigDecimal amount);

    /**
     * Takes the player's pending transactions off the queue and returns them. Each row is handed to exactly one
     * take: two takes running at once never both return the same row, and a credit queued while a take runs is
     * either returned by it or left for the next one, never cleared unseen.
     */
    default List<PendingTransaction> getAndClearPending(UUID playerUuid) {
        return getAndClearPending(playerUuid, Set.of());
    }

    /**
     * {@link #getAndClearPending(UUID)}, leaving the rows of the currencies in {@code leave} (lower case) on the queue
     * untouched.
     */
    List<PendingTransaction> getAndClearPending(UUID playerUuid, Set<String> leave);

    record PendingTransaction(String id, UUID playerUuid, String currencyId, BigDecimal amount) {}
}
