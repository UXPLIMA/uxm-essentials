/**
 * What turns a Bukkit event into a line on a socket.
 *
 * <p>The catalogue of what is carried is written out; the shape of each payload is read off the getters the event
 * class publishes. Both halves are pinned by golden files, so an event added upstream and forgotten here fails a
 * build rather than quietly never arriving, and a new field is a diff somebody agreed to rather than a surprise.
 *
 * <p>The one thing that never happens here is I/O on a tick thread: an event is rendered where it arrives and
 * written by a thread of this package's own.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.rest.bridge;
