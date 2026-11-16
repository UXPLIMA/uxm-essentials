package com.uxplima.uxmessentials.ranks.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Money the ranks context took from a player and could not give back on the spot: the refund of a charge whose
 * rankup or prestige was not saved, refused or failed by the economy. The economy context keeps it on its pending
 * queue and pays it when the player next joins, so the charge is returned exactly once instead of only being logged.
 * Like {@link RankEconomy} it is expressed in ranks' own terms, so ranks imports no economy type.
 */
public interface OwedCredits {

    /**
     * Queue {@code amount} of {@code currencyId} for {@code who}, in that same currency ({@code "default"} naming
     * the one {@link RankEconomy} charged in).
     */
    void owe(PlayerRef who, BigDecimal amount, String currencyId);
}
