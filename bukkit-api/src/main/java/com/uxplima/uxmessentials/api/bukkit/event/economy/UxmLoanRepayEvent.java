package com.uxplima.uxmessentials.api.bukkit.event.economy;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.jspecify.annotations.NullMarked;

/** A payment was made against a loan. A remaining balance of zero means it is now settled. */
@NullMarked
public final class UxmLoanRepayEvent extends UxmEconomyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String loanId;
    private final UxmMoney paid;
    private final UxmMoney remaining;

    public UxmLoanRepayEvent(UUID debtorId, String debtorName, String loanId, UxmMoney paid, UxmMoney remaining) {
        super(debtorId, debtorName);
        this.loanId = Objects.requireNonNull(loanId, "loanId");
        this.paid = Objects.requireNonNull(paid, "paid");
        this.remaining = Objects.requireNonNull(remaining, "remaining");
    }

    /** The loan's id. */
    public String getLoanId() {
        return loanId;
    }

    /** How much was paid this time. */
    public UxmMoney getPaid() {
        return paid;
    }

    /** How much is still owed. Zero means the loan is settled. */
    public UxmMoney getRemaining() {
        return remaining;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
