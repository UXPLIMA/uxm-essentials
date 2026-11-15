package com.uxplima.uxmessentials.ranks.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the ranks context owns so a rank's rankup cost can be charged <em>without</em> a hard
 * dependency on the economy context. This is the entire economy surface ranks needs: a balance check, a
 * guarded withdrawal and the deposit that refunds a withdrawal whose rankup was not saved. Expressed in ranks' own
 * terms; the economy context supplies an adapter that bridges this
 * to its {@code EconomyProvider}/{@code Wallet}, and the ranks context never imports an economy type (mirrors the
 * kits {@code KitEconomy} seam).
 *
 * <p>Soft coupling: this port is injected as an {@link java.util.Optional} into the rankup use case. When no
 * provider is present, a rank's cost is simply ignored and rankup is free; when present, the cost is charged
 * through this seam <em>after</em> the requirements pass and <em>before</em> the rank pointer advances, so a
 * player who cannot pay never advances. A {@link #withdraw} is a guarded single-sided debit at the source, so a
 * {@code true} return means the money left the account exactly once. There is no separate balance read to race
 * against, so a rankup charge can never double-charge.
 */
public interface RankEconomy {

    /** True when {@code who} can afford {@code amount} in the specified currency (a read, no money moves). */
    boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Withdraw {@code amount} of {@code currencyId} from {@code who}'s balance, returning {@code true} on a
     * successful debit and {@code false} when the balance was insufficient.
     */
    boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Give {@code amount} of {@code currencyId} back to {@code who}: the refund of a charge whose rankup could not be
     * saved. Returns {@code false} when the credit was refused, so the caller can report money it still owes.
     */
    boolean deposit(PlayerRef who, BigDecimal amount, String currencyId);
}
