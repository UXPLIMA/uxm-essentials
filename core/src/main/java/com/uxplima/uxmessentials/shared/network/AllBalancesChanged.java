package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * Every account's balance in one currency may have changed on the origin backend: an {@code /eco giveall} or
 * {@code /eco resetall} swept the whole ledger. Peers drop every cached wallet and read the rows again on the next
 * look. One frame stands for the whole sweep, because a {@link BalanceChanged} per account would put thousands of
 * frames on the bus for a single command. Like every frame it carries no figure: the database already holds them.
 *
 * @param originServer the backend that ran the sweep
 * @param currencyId the currency the sweep changed
 */
public record AllBalancesChanged(String originServer, String currencyId) implements NetworkMessage {

    public AllBalancesChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(currencyId, "currencyId");
    }

    @Override
    public MessageType type() {
        return MessageType.ALL_BALANCES_CHANGED;
    }
}
