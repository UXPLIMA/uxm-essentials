package com.uxplima.uxmessentials.trade.application;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;

/**
 * The network's wallets as the trade sees them: a balance per player and currency, every debit guarded at the
 * source. A currency can be told to refuse every move, the way a provider whose storage is down or whose clamp
 * rejects the credit does.
 */
final class TradeWallets implements TradeEconomy {

    private final Map<String, BigDecimal> balances = new HashMap<>();
    private final Set<String> refused = new HashSet<>();

    TradeWallets set(PlayerRef who, String currency, String amount) {
        balances.put(key(who, currency), new BigDecimal(amount));
        return this;
    }

    void refuse(String currency) {
        refused.add(currency);
    }

    BigDecimal balance(PlayerRef who, String currency) {
        return balances.getOrDefault(key(who, currency), BigDecimal.ZERO);
    }

    /** Every unit of {@code currency} held by anyone, so a test can see money was neither made nor lost. */
    BigDecimal total(String currency) {
        return balances.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("|" + currency))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
        return balance(who, currencyId).compareTo(amount) >= 0;
    }

    @Override
    public boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {
        if (refused.contains(currencyId) || !canAfford(from, amount, currencyId)) {
            return false;
        }
        balances.put(key(from, currencyId), balance(from, currencyId).subtract(amount));
        balances.put(key(to, currencyId), balance(to, currencyId).add(amount));
        return true;
    }

    @Override
    public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
        if (refused.contains(currencyId) || !canAfford(who, amount, currencyId)) {
            return false;
        }
        balances.put(key(who, currencyId), balance(who, currencyId).subtract(amount));
        return true;
    }

    @Override
    public void deposit(PlayerRef who, BigDecimal amount, String currencyId) {
        balances.put(key(who, currencyId), balance(who, currencyId).add(amount));
    }

    private static String key(PlayerRef who, String currency) {
        return who.uuid() + "|" + currency;
    }
}
