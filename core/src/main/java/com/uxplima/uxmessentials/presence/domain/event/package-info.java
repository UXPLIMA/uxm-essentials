/**
 * The presence context's sealed {@code PresenceEvent} family and its concrete records: {@code WentAfk},
 * {@code ReturnedFromAfk}, and {@code VanishToggled}. Each is an immutable {@code record} bridged to a Bukkit
 * event by the adapter so other plugins observe AFK and vanish changes without importing this package. No
 * Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.domain.event;
