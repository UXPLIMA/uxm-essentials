package com.uxplima.uxmessentials.vaults.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;

/** One balance that a withdraw guards and a deposit may refuse, logging every move that took. */
final class VaultPurse implements VaultEconomy {

    private BigDecimal balance;
    private boolean refusesDeposits;
    private final List<String> moves = new ArrayList<>();

    VaultPurse(String balance) {
        this.balance = new BigDecimal(balance);
    }

    /** Make every deposit fail, as a provider at its max-balance clamp does. */
    void refuseDeposits() {
        refusesDeposits = true;
    }

    BigDecimal balance() {
        return balance;
    }

    /** Each applied move as {@code -amount} or {@code +amount}, in order. */
    List<String> moves() {
        return List.copyOf(moves);
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    @Override
    public boolean withdraw(PlayerRef who, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            return false;
        }
        balance = balance.subtract(amount);
        moves.add("-" + amount.stripTrailingZeros().toPlainString());
        return true;
    }

    @Override
    public boolean deposit(PlayerRef who, BigDecimal amount) {
        if (refusesDeposits) {
            return false;
        }
        balance = balance.add(amount);
        moves.add("+" + amount.stripTrailingZeros().toPlainString());
        return true;
    }
}
