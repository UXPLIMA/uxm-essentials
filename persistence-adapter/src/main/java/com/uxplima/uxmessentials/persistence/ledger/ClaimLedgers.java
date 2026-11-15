package com.uxplima.uxmessentials.persistence.ledger;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.application.port.DailyRewardLedger;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the shared claim ledgers, so the bukkit-adapter wires them from the {@link Persistence} handle without
 * naming a jOOQ type.
 */
@NullMarked
public final class ClaimLedgers {

    private ClaimLedgers() {}

    /** The one-time kit claims and cooldowns over the shared persistence DSL. */
    public static PlayerClaimLedger claims(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPlayerClaimLedger(persistence.dsl());
    }

    /** The daily reward stamps over the shared persistence DSL. */
    public static DailyRewardLedger dailyRewards(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqDailyRewardLedger(persistence.dsl());
    }
}
