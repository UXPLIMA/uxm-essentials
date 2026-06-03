/**
 * The scoreboard context's application layer: the {@link com.uxplima.uxmessentials.scoreboard.application.ScoreboardModule}
 * feature module (ships disabled, no persistence, render timer on the {@code Scheduler} port), the
 * {@link com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard} use case driving the per-player
 * visibility flip, the platform-neutral command surface, the per-context {@code MessageKey} enum, and the notifier
 * that pairs message resolution with delivery. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.scoreboard.application;

import org.jspecify.annotations.NullMarked;
