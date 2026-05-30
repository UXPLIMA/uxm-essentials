/**
 * The homes context's domain events: the sealed {@code HomeEvent} family and its record implementations.
 * Each event records something that already happened ({@code HomeCreated}, {@code HomeDeleted},
 * {@code HomeLimitReached}); the adapter bridges them to Bukkit events so other plugins observe home
 * changes without importing this package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.domain.event;
