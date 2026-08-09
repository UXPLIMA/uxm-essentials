/**
 * What you can ask uxmEssentials about its current state.
 *
 * <p>One interface per context, each obtained from the front door and each answering {@link java.util.Optional#empty()}
 * there when its module is switched off. Nine modules ship switched off, so treat an absent context as ordinary
 * rather than exceptional: it is the difference between "this server has homes disabled" and "this player has no
 * homes", which no amount of empty lists can tell you apart.
 *
 * <h2>Threading</h2>
 * Anything that reaches the database returns a {@link java.util.concurrent.CompletableFuture}. A synchronous getter
 * would be called from an event handler, on the tick thread, and would stall the server on a query; there is no
 * version of this API that lets you do that by accident.
 *
 * <p>Those futures complete on a uxmEssentials worker thread, <em>not</em> a tick thread. Get back to one before you
 * touch the Bukkit API:
 *
 * <pre>{@code
 * homes.list(playerId).thenAccept(list ->
 *         player.getScheduler().run(plugin, task -> render(list), null));
 * }</pre>
 *
 * <p>Reads that are served from memory (is this player AFK, vanished, in staff mode) return their value directly and
 * say so on the method. Those are safe to call from anywhere.
 *
 * <h2>Reading only</h2>
 * Nothing here changes anything. The values you get back are records: your own copy, safe to hold, and holding one
 * does not keep the server's data alive or up to date.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.api.query;
