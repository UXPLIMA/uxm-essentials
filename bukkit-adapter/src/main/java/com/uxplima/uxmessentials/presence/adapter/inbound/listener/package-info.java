/**
 * The presence context's Bukkit listeners: {@code PresenceActivityListener} feeds the AFK clock from the sync
 * {@code PlayerMoveEvent} (block-change filtered) and the async {@code AsyncChatEvent} (bridged back to the
 * player's region thread through the {@code Scheduler} port); {@code PresenceLifecycleListener} seeds and drops
 * the {@code PlayerPresence} on join/quit and suppresses the fake join/quit line for a vanished player while
 * reconciling the vanish view. The listeners write the activity stamp on the player's thread and the AFK sweep
 * only reads it, so the async sweep never writes player state.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.adapter.inbound.listener;
