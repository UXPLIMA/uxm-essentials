package com.uxplima.uxmessentials.trade.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The narrow experience seam the trade context owns so a trade can move staked experience points without the domain
 * ever touching a Bukkit player (mirrors {@link TradeEconomy} for money). Experience is a native server resource with
 * no provider behind it, so unlike money this seam is always wired when the module is active; a trade may move
 * experience even on an install with no economy provider.
 *
 * <p>The three operations follow the same escrow shape money uses in the two-phase commit: {@link #available} reads the
 * player's current whole experience points for the window preview and the amount prompt, {@link #withdraw} is the
 * guarded debit that removes points and reports a shortfall by a {@code false} return (nothing removed), and
 * {@link #deposit} is the credit that hands points to the recipient (or returns a refund). Because experience lives on
 * the online player rather than a database, every operation is bound to that player's region/entity thread by the
 * adapter and degrades to a no-op (or a zero read) when the owner is offline, exactly as the native experience currency
 * backend does.
 */
public interface TradeExperience {

    /** The whole experience points {@code who} currently holds, or {@code 0} when they are offline. */
    long available(PlayerRef who);

    /**
     * Guardedly remove {@code points} experience from {@code who}, returning {@code true} when the debit took and
     * {@code false} when they could not cover it or are offline (nothing removed). The point at which staked experience
     * leaves the payer, held until the trade commits (delivered to the other side via {@link #deposit}) or is aborted
     * (deposited back to the payer).
     */
    boolean withdraw(PlayerRef who, long points);

    /**
     * Credit {@code points} experience to {@code who}, the delivery half of a settled trade (the counterpart's staked
     * experience reaching the recipient) or a refund (the payer's own staked experience returning). Best-effort: a
     * credit against an offline owner is dropped, mirroring how an offline recipient's item delivery is skipped.
     */
    void deposit(PlayerRef who, long points);
}
