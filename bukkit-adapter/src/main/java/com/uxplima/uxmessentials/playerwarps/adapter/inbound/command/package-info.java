/**
 * The player-warps context's inbound Brigadier commands: {@code /setpwarp}, {@code /delpwarp},
 * {@code /pwarp <name> [owner]} (with the {@code public}/{@code private} visibility subtrees), and
 * {@code /pwarps [player]}. Each command maps its arguments to one player-warps use case and resolves an
 * optional owner through the {@code PlayerLookup} port; all player-facing feedback flows through the use
 * cases' {@code MessageSink}, so the catalog is the only place text lives.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;
