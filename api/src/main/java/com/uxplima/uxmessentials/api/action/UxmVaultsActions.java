package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Opening a player's vault for them, removing one, and changing how one is labelled.
 *
 * <p>Vault numbers count from one, which is what the owner types and what {@code UxmVaultsQuery} answers with.
 *
 * <p>There is nothing here to put items in a vault with, or to take them out. An item stack is a Bukkit value with
 * no published form, and the window is where the item policy, the size quota and the save-on-close live: a write
 * that went around it would be a second, weaker way into the same storage. {@link #open} hands the player the real
 * window instead, which is the same one {@code /vault} opens.
 *
 * <p>These run the owner's own path, not a staff override: the amount quota applies, a configured fee is charged
 * to the owner and a configured refund is paid back to them, and they are told what happened in their own
 * language exactly as if they had typed the command. That is deliberate. A vault handed out around the quota
 * would be one the plugin's own selector refuses to draw.
 */
public interface UxmVaultsActions {

    /**
     * Open this player's own vault, allocating it first when they have never opened it and are within their quota.
     *
     * <p>{@link UxmFailure#PLAYER_OFFLINE} when they are not here to see it, {@link UxmFailure#REFUSED} when the
     * number is past their quota, {@link UxmFailure#INSUFFICIENT_FUNDS} when they cannot pay a configured open
     * fee. The player must be online: a window has to be shown to somebody.
     */
    CompletableFuture<UxmOutcome> open(UUID ownerId, int index);

    /**
     * Remove a vault, freeing the owner's quota slot. The items in it go with it.
     *
     * <p>{@link UxmFailure#NOT_FOUND} when they have no vault under that number. A configured delete refund is
     * paid to the owner, since this is their own delete rather than a staff override.
     */
    CompletableFuture<UxmOutcome> delete(UUID ownerId, int index);

    /** Give a vault the name its owner sees in the selector. {@link UxmFailure#NOT_FOUND} when there is no vault. */
    CompletableFuture<UxmOutcome> rename(UUID ownerId, int index, String name);

    /** Drop a vault's name, so the selector falls back to its number again. */
    CompletableFuture<UxmOutcome> clearName(UUID ownerId, int index);

    /**
     * Set the item a vault is drawn with in the selector, named as a Bukkit material ({@code ENDER_CHEST}).
     *
     * <p>{@link UxmFailure#REFUSED} when no such material exists, {@link UxmFailure#NOT_FOUND} when the player has
     * no vault under that number.
     */
    CompletableFuture<UxmOutcome> setIcon(UUID ownerId, int index, String material);

    /** Drop a vault's icon, so the selector draws it with the default again. */
    CompletableFuture<UxmOutcome> clearIcon(UUID ownerId, int index);
}
