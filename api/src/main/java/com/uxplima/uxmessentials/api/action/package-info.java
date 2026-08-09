/**
 * What you can ask uxmEssentials to do.
 *
 * <p>One interface per context, reached through {@code api.actions(plugin)} and each answering
 * {@link java.util.Optional#empty()} there when its module is switched off. Reads live next door in
 * {@code com.uxplima.uxmessentials.api.query} and never write; nothing here reads on your behalf.
 *
 * <h2>Attribution</h2>
 * The surface is obtained with your plugin, because every write is attributable: your plugin's name is what the
 * audit log records, so an operator asking who moved the money gets a name rather than "the API".
 *
 * <h2>Acting as the server, not as the player</h2>
 * An action performs the operation. It does not check a permission, wait out a cooldown, or charge for anything:
 * your plugin already decided this should happen. Structural rules still hold, because they are what keeps the
 * data consistent with what the commands show: a name stays unique, a world has to exist, a balance cannot go
 * negative.
 *
 * <h2>Results</h2>
 * Nothing throws because the server said no. An action answers with a {@link
 * com.uxplima.uxmessentials.api.action.UxmOutcome} or a {@link com.uxplima.uxmessentials.api.action.UxmResult},
 * carrying a {@link com.uxplima.uxmessentials.api.action.UxmFailure} whose code you can branch on. Exceptions are
 * reserved for calls that were malformed: a null id, a negative amount.
 *
 * <h2>Threading</h2>
 * Every action returns a {@link java.util.concurrent.CompletableFuture} and is safe to call from any thread. The
 * work lands on whichever thread owns it, a worker for the database and the player's own region thread for
 * anything touching a live player, so you never have to arrange that yourself. The future completes off the tick
 * thread: get back to one before you touch the Bukkit API.
 *
 * <h2>Events still fire</h2>
 * An action runs the same use case the command runs, so the same event is published and the same cancellable
 * {@code Pre} event is offered first. A listener that vetoes turns your action into a {@code cancelled} failure.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.api.action;
