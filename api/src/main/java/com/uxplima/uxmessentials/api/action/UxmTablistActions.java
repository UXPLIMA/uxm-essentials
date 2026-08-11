package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Redrawing a player's tab list.
 *
 * <p>One verb, because there is only one honest one. The header, footer, list names and ordering are all authored in
 * the module's config and repainted on a timer; nothing outside the module owns a row it could set or take away, and
 * anything that cleared the list here would be repainted a tick later, which would make it a lie rather than an
 * action.
 *
 * <p>{@link #refresh} is for when the timer is too slow: your plugin has just changed a rank, a prefix, or a
 * placeholder the tab list reads, and you want the list to say so now.
 */
public interface UxmTablistActions {

    /** Repaint this player's tab list now rather than at the next refresh. */
    CompletableFuture<UxmOutcome> refresh(UUID playerId);
}
