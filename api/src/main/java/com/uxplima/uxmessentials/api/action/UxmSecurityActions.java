package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The two security acts a plugin has business performing.
 *
 * <p>Both go in the safe direction. Making somebody prove themselves again is what a panel does when an account
 * looks compromised; letting somebody back in early is what staff do when a player locked themselves out. Neither
 * hands out a factor, and neither removes one.
 *
 * <p>There is deliberately no enrol and no reset. Enrolling for somebody else would mean minting a secret they
 * never saw, and clearing an account's factors is a security downgrade that belongs behind an operator's own
 * command, where it is logged as a person having done it.
 */
public interface UxmSecurityActions {

    /**
     * Make this account verify again on its next join, by forgetting every device it is trusted from.
     *
     * <p>An account that holds no factor has nothing to be made to prove, and answers {@code not-found}.
     */
    CompletableFuture<UxmOutcome> forceVerification(UUID playerId);

    /**
     * End a lockout early, so the account may try again now.
     *
     * <p>An account that is not locked out answers {@code already-in-state}. A lockout the operator chose to write
     * to the ban list is a ban too, and clearing the window here does not lift that: it is lifted with the unban
     * that lifts every other one.
     */
    CompletableFuture<UxmOutcome> clearLockout(UUID playerId);
}
