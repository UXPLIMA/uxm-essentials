package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Port representing the pending queue for physical transactions destined for offline players.
 */
public interface PendingTransactionRepository {

    /**
     * Queues a credit transaction for an offline player.
     * If there is an existing pending credit for the same player and currency, they can accumulate.
     */
    void queueCredit(UUID playerUuid, String currencyId, BigDecimal amount);

    /**
     * Retrieves all pending transactions for the given player and clears them from the database.
     */
    List<PendingTransaction> getAndClearPending(UUID playerUuid);

    record PendingTransaction(String id, UUID playerUuid, String currencyId, BigDecimal amount) {}
}
