package com.uxplima.uxmessentials.api.bukkit.event.economy;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.jspecify.annotations.NullMarked;

/** A loan was paid out. The debtor's wallet has already been credited. */
@NullMarked
public final class UxmLoanDisburseEvent extends UxmEconomyEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String loanId;
    private final UxmMoney principal;

    public UxmLoanDisburseEvent(UUID debtorId, String debtorName, String loanId, UxmMoney principal) {
        super(debtorId, debtorName);
        this.loanId = Objects.requireNonNull(loanId, "loanId");
        this.principal = Objects.requireNonNull(principal, "principal");
    }

    /** The loan's id. */
    public String getLoanId() {
        return loanId;
    }

    /** How much was lent. */
    public UxmMoney getPrincipal() {
        return principal;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
