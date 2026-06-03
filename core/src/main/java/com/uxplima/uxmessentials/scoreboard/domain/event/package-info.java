/**
 * The scoreboard context's sealed domain-event family. Each concrete event is a {@code record} (value equality
 * backs the in-process bus and equality-based test consumers); the adapter bridges them to Bukkit events so other
 * plugins observe a player toggling their display without importing this package.
 */
@NullMarked
package com.uxplima.uxmessentials.scoreboard.domain.event;

import org.jspecify.annotations.NullMarked;
