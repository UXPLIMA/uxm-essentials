package com.uxplima.uxmessentials.warps.application.port;

import java.math.BigDecimal;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow economy seam the warps context owns so a per-warp cost can be charged <em>without</em> a hard
 * dependency on the economy context. This is the entire economy surface warps needs, a balance check and a
 * withdrawal. Expressed in warps' own terms; the economy context supplies an adapter
 * that bridges this to its {@code EconomyProvider}/{@code Wallet}, and the warps context never imports an
 * economy type.
 *
 * <p>Soft coupling: this port is injected as an {@link java.util.Optional} into {@code UseWarp}. When no
 * provider is present, a warp's recorded cost is simply ignored at use time (the warp is still created and
 * stored with its cost, ready for when economy is wired). When a provider is present, {@code UseWarp}
 * charges through it before delegating the teleport. A {@code withdraw} that the player cannot afford fails
 * the use, and a charged warp whose teleport is then refused for another reason is the caller's concern to
 * order correctly: the charge happens only once the access gates have passed.
 */
public interface WarpEconomy {

    /** True when {@code who} can afford {@code amount} in the specified currency. */
    boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Withdraw {@code amount} of {@code currencyId} from {@code who}'s balance. Answers the id of the currency the
     * money left in, with {@code "default"} resolved to this server's currency, so a refund later, on this server or
     * another, gives it back in that same currency; empty when the balance was insufficient.
     */
    Optional<String> debit(PlayerRef who, BigDecimal amount, String currencyId);

    /**
     * Give {@code amount} of {@code currencyId}, the id {@link #debit} answered, back to {@code who}, for a warp use
     * that was charged and never moved the player. Returns {@code true} once the credit has landed, and {@code false}
     * when this server knows no such currency: the money is never credited in another one.
     */
    boolean refund(PlayerRef who, BigDecimal amount, String currencyId);
}
