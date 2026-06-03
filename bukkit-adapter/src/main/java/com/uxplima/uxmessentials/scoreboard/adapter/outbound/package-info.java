/**
 * The scoreboard context's outbound adapters: the PDC-backed visibility store
 * ({@link com.uxplima.uxmessentials.scoreboard.adapter.outbound.PdcScoreboardVisibilityStore}), the per-viewer
 * renderer over uxmLib's {@code SidebarManager} and {@code Tablist}
 * ({@link com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer}), and the self-rescheduling
 * render timer on the {@code Scheduler} port
 * ({@link com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderTask}).
 */
@NullMarked
package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import org.jspecify.annotations.NullMarked;
