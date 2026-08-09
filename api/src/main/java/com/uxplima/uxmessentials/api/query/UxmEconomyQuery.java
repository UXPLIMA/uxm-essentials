package com.uxplima.uxmessentials.api.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmBaltopEntry;
import com.uxplima.uxmessentials.api.view.UxmMoney;

/**
 * What players hold, in whichever currencies the operator configured.
 *
 * <p>Every method that names a currency takes its configured id. Most servers run one, whose id is
 * {@code "default"}; the no-currency forms use it, so a consumer on an ordinary server never has to think about
 * currencies at all. An unknown currency id is an empty answer rather than an exception, because which currencies
 * exist is the operator's choice and not something a consumer can be sure of.
 *
 * <p>This reads balances; it does not move money. Paying, charging and setting a balance are use cases with rules
 * (limits, taxes, audit records) that a read surface has no business bypassing.
 */
public interface UxmEconomyQuery {

    /** The ids of every configured currency, the first of which is the default. Answered from memory. */
    List<String> currencies();

    /** What this player holds in the default currency. A player who has never been seen holds the starting balance. */
    CompletableFuture<UxmMoney> balance(UUID playerId);

    /** What this player holds in one currency, or empty when no currency has that id. */
    CompletableFuture<Optional<UxmMoney>> balance(UUID playerId, String currency);

    /** What this player holds in every configured currency, in the order {@link #currencies()} lists them. */
    CompletableFuture<List<UxmMoney>> balances(UUID playerId);

    /**
     * Whether this player holds at least {@code amount} in the default currency. The same comparison the plugin
     * makes before charging, so a consumer that checks first and then asks uxmEssentials to charge agrees with it.
     *
     * @throws IllegalArgumentException when {@code amount} is negative
     */
    CompletableFuture<Boolean> canAfford(UUID playerId, BigDecimal amount);

    /** The same in one currency; {@code false} for an unknown currency id, since nobody holds what does not exist. */
    CompletableFuture<Boolean> canAfford(UUID playerId, BigDecimal amount, String currency);

    /** The richest players in the default currency, ranked, at most {@code limit} of them. */
    CompletableFuture<List<UxmBaltopEntry>> top(int limit);

    /** The richest players in one currency, ranked, at most {@code limit} of them; empty for an unknown currency. */
    CompletableFuture<List<UxmBaltopEntry>> top(String currency, int limit);
}
