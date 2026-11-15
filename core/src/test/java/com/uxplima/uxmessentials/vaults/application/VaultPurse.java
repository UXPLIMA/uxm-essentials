package com.uxplima.uxmessentials.vaults.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;

/**
 * One balance per currency: fees come out of the currency the purse currently charges in, a refund lands in the
 * currency it names, a withdraw guards the balance and a deposit may refuse. Every move that took is logged.
 */
final class VaultPurse implements VaultEconomy {

    private final Map<String, BigDecimal> balances = new HashMap<>();
    private String charging = "coins";
    private boolean refusesDeposits;
    private final List<String> moves = new ArrayList<>();

    VaultPurse(String balance) {
        balances.put(charging, new BigDecimal(balance));
    }

    /** Make every deposit fail, as a provider at its max-balance clamp does. */
    void refuseDeposits() {
        refusesDeposits = true;
    }

    /** Charge later fees in {@code currency}, as a server whose default currency changed does. */
    void chargeIn(String currency) {
        charging = currency;
    }

    /** The balance in the currency fees are charged in. */
    BigDecimal balance() {
        return balance(charging);
    }

    BigDecimal balance(String currency) {
        return balances.getOrDefault(currency, BigDecimal.ZERO);
    }

    /** Each applied move as {@code -amount} for a fee or {@code +amount currency} for a refund, in order. */
    List<String> moves() {
        return List.copyOf(moves);
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount) {
        return balance().compareTo(amount) >= 0;
    }

    @Override
    public boolean withdraw(PlayerRef who, BigDecimal amount) {
        if (balance().compareTo(amount) < 0) {
            return false;
        }
        balances.put(charging, balance().subtract(amount));
        moves.add("-" + plain(amount));
        return true;
    }

    @Override
    public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
        if (refusesDeposits) {
            return false;
        }
        balances.put(currencyId, balance(currencyId).add(amount));
        moves.add("+" + plain(amount) + " " + currencyId);
        return true;
    }

    @Override
    public String currencyId() {
        return charging;
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
