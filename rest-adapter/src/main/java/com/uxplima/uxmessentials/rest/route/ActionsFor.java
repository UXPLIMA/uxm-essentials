package com.uxplima.uxmessentials.rest.route;

import com.uxplima.uxmessentials.api.action.UxmActions;

/**
 * Where a write route gets its write surface, already attributed.
 *
 * <p>An indirection worth having twice over. The add-on is a door rather than an actor, so a write over HTTP is
 * attributed to the token behind it and the audit trail says {@code uxmEssentials-rest/panel} rather than naming
 * the jar; and the route tables then need nothing from Bukkit, so they can be tested without a server.
 */
@FunctionalInterface
public interface ActionsFor {

    /** The write surface for a request that authenticated as {@code caller}. */
    UxmActions actingFor(String caller);
}
