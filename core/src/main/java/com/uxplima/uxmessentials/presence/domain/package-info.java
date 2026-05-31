/**
 * Pure domain of the presence bounded context: the immutable {@code PlayerPresence} aggregate (the AFK flag
 * with its optional reason, the last-activity instant, and the vanished flag) with its two transition rules —
 * the AFK auto-transition (idle past a threshold) and the activity-clears-AFK rule — plus the vanish flip, and
 * the sealed {@code PresenceEvent} family ({@code WentAfk} / {@code ReturnedFromAfk} / {@code VanishToggled}).
 * The aggregate is the value a {@code ConcurrentHashMap<UUID, PlayerPresence>} holds, re-stamped by sync
 * activity listeners and read by the async AFK sweep. No Bukkit, Paper, Kyori, or logging type appears in this
 * package — the model is built from value objects and the cross-cutting kernel primitives ({@code PlayerRef}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.domain;
