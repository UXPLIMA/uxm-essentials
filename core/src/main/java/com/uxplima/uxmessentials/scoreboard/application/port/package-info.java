/**
 * The scoreboard context's outbound ports. The single port,
 * {@link com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore}, abstracts the per-player
 * "display hidden" preference the {@code ToggleScoreboard} use case flips; the bukkit-adapter backs it with PDC so
 * the choice survives relog.
 */
@NullMarked
package com.uxplima.uxmessentials.scoreboard.application.port;

import org.jspecify.annotations.NullMarked;
