package com.uxplima.uxmessentials.persistence.trade;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.trade.application.port.TradeEscrowStore;
import org.jspecify.annotations.NullMarked;

/**
 * The trade context's repository factory: it constructs the jOOQ-backed cross-server escrow store over the shared
 * {@link Persistence} handle without ever exposing a jOOQ type to the caller (jOOQ is an {@code implementation}
 * dependency of this module only), mirroring {@code PlayerWarpRepositories}. The bukkit-adapter wiring calls this and
 * receives the {@link TradeEscrowStore} port.
 */
@NullMarked
public final class TradeRepositories {

    private TradeRepositories() {}

    /** The cross-server trade escrow store over the shared network database. */
    public static TradeEscrowStore escrowStore(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqTradeEscrowStore(persistence.dsl());
    }
}
