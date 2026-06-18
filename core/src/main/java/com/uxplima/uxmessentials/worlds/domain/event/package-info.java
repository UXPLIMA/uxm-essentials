/**
 * The worlds context's domain events: the sealed {@code WorldEvent} family and its record
 * implementations. Each event records something that already happened ({@code WorldCreated},
 * {@code WorldDeleted}); the adapter bridges them to Bukkit events so other plugins observe world
 * changes without importing this package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.domain.event;
