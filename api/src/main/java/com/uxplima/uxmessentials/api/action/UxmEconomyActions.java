package com.uxplima.uxmessentials.api.action;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmMoney;

/**
 * Moving money.
 *
 * <p>Every operation writes through whichever economy the server runs, so a server using Vault or Treasury for its
 * balances is written to through that plugin and the figures stay the ones {@code /balance} prints. Each answers
 * with the balance it left behind, which saves the read a caller would otherwise make straight afterwards.
 *
 * <p>The no-currency forms use the default currency, which is the only one most servers have. A currency id
 * nobody configured is {@link UxmFailure#NOT_FOUND} rather than an exception: which currencies exist is the
 * operator's choice and not the caller's mistake.
 *
 * <p>Amounts are {@link BigDecimal} and never {@code double}. A negative amount throws, because "deposit minus
 * fifty" is a bug in the caller rather than a withdrawal.
 */
public interface UxmEconomyActions {

    /** Add {@code amount} to this player's balance in the default currency, answering the new balance. */
    CompletableFuture<UxmResult<UxmMoney>> deposit(UUID playerId, BigDecimal amount);

    /** The same in one currency. */
    CompletableFuture<UxmResult<UxmMoney>> deposit(UUID playerId, BigDecimal amount, String currency);

    /**
     * Take {@code amount} out of this player's balance in the default currency, answering the new balance.
     *
     * <p>{@link UxmFailure#INSUFFICIENT_FUNDS} when they do not hold it, and nothing moves. The check is made at
     * the database rather than in memory, so two plugins withdrawing at once cannot both succeed past zero.
     */
    CompletableFuture<UxmResult<UxmMoney>> withdraw(UUID playerId, BigDecimal amount);

    /** The same in one currency. */
    CompletableFuture<UxmResult<UxmMoney>> withdraw(UUID playerId, BigDecimal amount, String currency);

    /** Set this player's balance in the default currency to exactly {@code amount}, answering it back. */
    CompletableFuture<UxmResult<UxmMoney>> set(UUID playerId, BigDecimal amount);

    /** The same in one currency. */
    CompletableFuture<UxmResult<UxmMoney>> set(UUID playerId, BigDecimal amount, String currency);

    /**
     * Move {@code amount} from one player to another in the default currency.
     *
     * <p>Both sides commit together or neither does, so a transfer never half-happens. The operator's rules for
     * player-to-player movement still apply: a currency configured not to move between players, or a minimum
     * payment, comes back as {@link UxmFailure#REFUSED}.
     */
    CompletableFuture<UxmOutcome> transfer(UUID fromId, UUID toId, BigDecimal amount);

    /** The same in one currency. */
    CompletableFuture<UxmOutcome> transfer(UUID fromId, UUID toId, BigDecimal amount, String currency);
}
