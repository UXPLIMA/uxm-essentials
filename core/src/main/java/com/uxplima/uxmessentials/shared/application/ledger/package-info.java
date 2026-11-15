/**
 * The network form of a player's one-time kit claims and cooldowns: read from the shared database when the player
 * logs in, held in memory while they are online, written through off the tick. Used only while {@code network.enabled}
 * is on; a single server keeps these in the player's PDC.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.application.ledger;
