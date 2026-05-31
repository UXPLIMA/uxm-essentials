package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.OptionalInt;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code balance}, {@code balance_formatted} and {@code
 * baltop_position} placeholders. It is an adapter over the economy context's resolved {@code
 * EconomyProvider} (balance and baltop read-model) plus the operator-selected {@code AmountFormat} (the
 * v2.1 compact format), all wired during bootstrap; when the economy module is disabled the seam is absent
 * and the placeholders degrade.
 *
 * <p>Every value is read in the configured default currency — the placeholder surface carries no currency
 * argument. The balance is served from the offline read-cache the provider sits in front of, so an offline
 * player's balance still resolves.
 */
public interface EconomyPlaceholders {

    /** {@code who}'s balance in the default currency. */
    Money balance(PlayerRef who);

    /** {@code who}'s balance rendered with the operator-selected amount format (full or compact). */
    String formatted(PlayerRef who);

    /** {@code who}'s 1-based rank on the default-currency leaderboard, or empty when unranked/exempt. */
    OptionalInt baltopPosition(PlayerRef who);
}
