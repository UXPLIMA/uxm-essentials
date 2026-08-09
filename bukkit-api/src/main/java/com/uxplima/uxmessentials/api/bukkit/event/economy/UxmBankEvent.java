package com.uxplima.uxmessentials.api.bukkit.event.economy;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmMoney;
import org.jspecify.annotations.NullMarked;

/** What a bank deposit and a bank withdrawal have in common: which bank, how much, and what it holds now. */
@NullMarked
public abstract class UxmBankEvent extends UxmEconomyEvent {

    private final String bankId;
    private final UxmMoney amount;
    private final UxmMoney bankBalance;

    protected UxmBankEvent(UUID playerId, String playerName, String bankId, UxmMoney amount, UxmMoney bankBalance) {
        super(playerId, playerName);
        this.bankId = Objects.requireNonNull(bankId, "bankId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.bankBalance = Objects.requireNonNull(bankBalance, "bankBalance");
    }

    /** The bank's id. */
    public String getBankId() {
        return bankId;
    }

    /** How much moved. */
    public UxmMoney getAmount() {
        return amount;
    }

    /** What the bank holds now. */
    public UxmMoney getBankBalance() {
        return bankBalance;
    }
}
