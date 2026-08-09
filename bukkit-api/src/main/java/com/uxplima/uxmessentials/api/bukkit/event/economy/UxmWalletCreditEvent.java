package com.uxplima.uxmessentials.api.bukkit.event.economy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.jspecify.annotations.NullMarked;

/** Money was paid into a wallet. The balance is already the new one. */
@NullMarked
public final class UxmWalletCreditEvent extends UxmEconomyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmMoney amount;
    private final UxmMoney balance;
    private final UUID transactionId;
    private final Instant occurredAt;

    public UxmWalletCreditEvent(
            UUID ownerId, String ownerName, UxmMoney amount, UxmMoney balance, UUID transactionId, Instant occurredAt) {
        super(ownerId, ownerName);
        this.amount = Objects.requireNonNull(amount, "amount");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** How much was paid in. */
    public UxmMoney getAmount() {
        return amount;
    }

    /** What the wallet holds now. */
    public UxmMoney getBalance() {
        return balance;
    }

    /** The id of the recorded transaction, for matching against your own ledger. */
    public UUID getTransactionId() {
        return transactionId;
    }

    /** When it happened. */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
