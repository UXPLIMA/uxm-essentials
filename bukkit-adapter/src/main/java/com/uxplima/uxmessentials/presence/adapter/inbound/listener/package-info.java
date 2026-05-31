/**
 * The presence context's Bukkit listeners: {@code PresenceActivityListener} feeds the AFK clock from the sync
 * {@code PlayerMoveEvent} (block-change filtered) and the async {@code AsyncChatEvent} (bridged back to the
 * player's region thread through the {@code Scheduler} port); {@code PresenceLifecycleListener} seeds and drops
 * the {@code PlayerPresence} on join/quit and suppresses the fake join/quit line for a vanished player while
 * reconciling the vanish view. The §6.4 sync-listener / async-reader split: the listeners write the activity
 * stamp, the AFK sweep only reads.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.adapter.inbound.listener;
