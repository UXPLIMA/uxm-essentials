/**
 * The warps context's domain events: the sealed {@code WarpEvent} family and its record implementations.
 * Each event records something that already happened ({@code WarpCreated}, {@code WarpDeleted}); the
 * adapter bridges them to Bukkit events so other plugins observe warp changes without importing this
 * package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.domain.event;
