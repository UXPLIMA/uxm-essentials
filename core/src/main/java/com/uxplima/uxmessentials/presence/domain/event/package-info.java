/**
 * The presence context's sealed {@code PresenceEvent} family and its concrete records: {@code WentAfk} and
 * {@code ReturnedFromAfk}. Each is an immutable {@code record} bridged to a Bukkit event by the adapter so other
 * plugins observe AFK changes without importing this package. No Bukkit, Paper, Kyori, or logging type appears here.
 * Vanish moved to its own {@code vanish} context.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.domain.event;
