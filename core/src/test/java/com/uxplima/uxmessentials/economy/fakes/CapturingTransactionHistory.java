package com.uxplima.uxmessentials.economy.fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;

/** A {@link TransactionHistory} that keeps every recorded row so a test can assert on the transaction trail. */
public final class CapturingTransactionHistory implements TransactionHistory {

    /** One recorded transaction. {@code kind} is {@code CREDIT}, {@code DEBIT}, or {@code TRANSFER}. */
    public record Entry(String kind, String ownerId, Money amount, EconomyReason reason, long at) {}

    private final List<Entry> entries = new ArrayList<>();

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset) {
        return List.of();
    }

    @Override
    public List<HistoryRecord> queryGlobalTransactions(int limit, int offset) {
        return List.of();
    }

    @Override
    public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {
        entries.add(new Entry("TRANSFER", fromId, amount, reason, at));
    }

    @Override
    public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {
        entries.add(new Entry("CREDIT", ownerId, amount, reason, at));
    }

    @Override
    public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {
        entries.add(new Entry("DEBIT", ownerId, amount, reason, at));
    }

    @Override
    public List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset) {
        return List.of();
    }

    @Override
    public void flush() {}
}
