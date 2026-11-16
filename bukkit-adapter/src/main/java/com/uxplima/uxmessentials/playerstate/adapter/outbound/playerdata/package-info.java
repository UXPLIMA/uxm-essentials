/**
 * An offline player's {@code players/data/<uuid>.dat}, read and written without the server's internals: the file is
 * parsed only as far as the item slots, so every other tag goes back to disk exactly as it was read. The item codec
 * that turns a slot into an item is the one piece that needs the server, and it is passed in.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;
