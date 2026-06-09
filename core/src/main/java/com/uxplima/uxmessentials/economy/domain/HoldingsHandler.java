package com.uxplima.uxmessentials.economy.domain;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Domain interface for resolving and mutating money holdings.
 * Can represent virtual ledgers, physical inventory items, or ender chests.
 */
public interface HoldingsHandler {

    /** Returns the current balance for the given player and currency. */
    Money getBalance(PlayerRef player, Currency currency);

    /** Adds the money amount to the player's holdings. */
    Result<Unit, TransferError> deposit(PlayerRef player, Money amount);

    /** Deducts the money amount from the player's holdings. */
    Result<Unit, TransferError> withdraw(PlayerRef player, Money amount);
}
