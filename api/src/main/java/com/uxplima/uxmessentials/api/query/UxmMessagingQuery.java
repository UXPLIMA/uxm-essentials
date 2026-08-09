package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmIgnore;
import com.uxplima.uxmessentials.api.view.UxmMail;

/**
 * A player's mail, their ignore list, and whether they are taking messages at all.
 *
 * <p>Mail and ignores are in the database and survive a restart, so those wait on a read and answer for a player
 * who is offline. The two switches are session state held against the player, so they answer straight away and
 * only mean anything while the player is online.
 *
 * <p>Reading a mailbox does not mark it read. A consumer that shows somebody their mail and then wants it marked
 * should let the player run {@code /mail read}, which is what the unread count is measured against.
 */
public interface UxmMessagingQuery {

    /** This player's mailbox, newest first. Empty when they have none. */
    CompletableFuture<List<UxmMail>> mailbox(UUID playerId);

    /** How much unread mail this player is holding. Cheaper than reading the whole mailbox for a notice. */
    CompletableFuture<Long> unreadMail(UUID playerId);

    /** Who this player is ignoring, and how much of each one's traffic is suppressed. */
    CompletableFuture<List<UxmIgnore>> ignoreList(UUID playerId);

    /** Whether the owner is ignoring the other player. False when the owner ignores nobody. */
    CompletableFuture<Boolean> ignores(UUID ownerId, UUID otherId);

    /**
     * Whether this player is taking private messages, which is the {@code /msgtoggle} switch. Mail reaches them
     * either way: the switch gates the live channel only.
     */
    boolean acceptsMessages(UUID playerId);

    /** Whether this player has social spy on, so they are shown other players' private messages. */
    boolean isSocialSpying(UUID playerId);
}
