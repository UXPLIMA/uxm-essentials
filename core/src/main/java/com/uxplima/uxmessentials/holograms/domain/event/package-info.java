/**
 * The holograms context's domain events: the sealed {@code HologramEvent} family and its record
 * implementations. Each event records something that already happened ({@code HologramCreated},
 * {@code HologramDeleted}); the adapter bridges them to Bukkit events so other plugins observe hologram
 * changes without importing this package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.holograms.domain.event;
