/**
 * The Bukkit-facing half of the uxmEssentials developer API: the {@code UxmEssentialsApi} front door, the event
 * classes other plugins listen to, and the menu registration surface. Everything here is a published
 * compatibility promise, so it names only JDK types, Bukkit types and the views in {@code
 * com.uxplima.uxmessentials.api.view}; an architecture fence enforces that nothing internal leaks in.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.api.bukkit;
