package com.uxplima.uxmessentials.economy.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import org.jspecify.annotations.NullMarked;

/**
 * Outbound port interface for querying historical transaction records and triggering telemetry flushes.
 */
@NullMarked
public interface TransactionHistory {

    /**
     * Queries transaction records where the specified player is either the sender or receiver.
     *
     * @param playerUuid the player's unique ID
     * @param limit maximum records to return
     * @param offset query offset for pagination
     * @return a list of matching transaction records
     */
    List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset);

    /**
     * Queries all global transaction records in descending order of time.
     *
     * @param limit maximum records to return
     * @param offset query offset for pagination
     * @return a list of matching transaction records
     */
    List<HistoryRecord> queryGlobalTransactions(int limit, int offset);

    /**
     * Records an atomic transfer between two entities.
     */
    void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at);

    /**
     * Records a single-sided credit.
     */
    void recordCredit(String ownerId, Money amount, EconomyReason reason, long at);

    /**
     * Records a single-sided debit.
     */
    void recordDebit(String ownerId, Money amount, EconomyReason reason, long at);

    /**
     * Queries transactions associated with a shared bank ID.
     */
    List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset);

    /**
     * Flushes any in-memory buffered telemetry rows to the database.
     */
    void flush();
}
