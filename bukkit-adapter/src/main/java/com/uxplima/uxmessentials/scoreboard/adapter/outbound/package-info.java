/**
 * The scoreboard context's outbound adapters: the PDC-backed visibility store
 * ({@link com.uxplima.uxmessentials.scoreboard.adapter.outbound.PdcScoreboardVisibilityStore}), the per-viewer
 * packet-native sidebar renderer over uxmLib's scoreboard transport
 * ({@link com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer}), and the self-rescheduling
 * render timer on the {@code Scheduler} port
 * ({@link com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderTask}).
 */
@NullMarked
package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import org.jspecify.annotations.NullMarked;
