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
     * Takes the player's pending transactions off the queue and returns them. Each row is handed to exactly one
     * take: two takes running at once never both return the same row, and a credit queued while a take runs is
     * either returned by it or left for the next one, never cleared unseen.
     */
    List<PendingTransaction> getAndClearPending(UUID playerUuid);

    record PendingTransaction(String id, UUID playerUuid, String currencyId, BigDecimal amount) {}
}
