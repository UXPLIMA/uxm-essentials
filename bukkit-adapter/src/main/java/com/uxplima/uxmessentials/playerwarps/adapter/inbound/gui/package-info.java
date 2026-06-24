/**
 * The player-warps management GUI: a spec-driven list of player-owned warps rendered through the menu engine
 * ({@link com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpListMenu}) and a per-warp property
 * editor ({@link com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpEditorView}), a thin consumer
 * of the shared {@code shared.adapter.inbound.gui} framework. A player sees and edits only their own warps; an
 * operator holding {@code uxmessentials.pwarp.gui} sees and edits every player's. Each editor button reads the
 * warp fresh from the repository on open and writes through the same use cases the {@code /pwarp} subcommands
 * call, so the GUI adds no domain logic and never bypasses ownership.
 */
@NullMarked
package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import org.jspecify.annotations.NullMarked;
