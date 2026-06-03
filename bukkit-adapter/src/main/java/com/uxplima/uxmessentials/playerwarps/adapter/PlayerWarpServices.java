package com.uxplima.uxmessentials.playerwarps.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed player-warps use cases the Brigadier commands share, built once per module start by
 * {@code PlayerwarpsWiring} from the kernel ports, the cached jOOQ repository, and the teleport-delegating
 * teleporter. Held so every command reads the same use cases; the player-warps context keeps no other
 * adapter-side runtime state, so there is nothing here to drain on stop beyond dropping this holder.
 *
 * @param setPlayerWarp {@code /setpwarp}
 * @param delPlayerWarp {@code /delpwarp}
 * @param usePlayerWarp {@code /pwarp <name> [owner]}
 * @param listPlayerWarps {@code /pwarps [player]}
 * @param visibility {@code /pwarp public|private <name>}
 * @param players name → ref resolution for the {@code [owner]} / {@code [player]} cross-owner forms
 */
@NullMarked
public record PlayerWarpServices(
        SetPlayerWarp setPlayerWarp,
        DelPlayerWarp delPlayerWarp,
        UsePlayerWarp usePlayerWarp,
        ListPlayerWarps listPlayerWarps,
        SetPlayerWarpVisibility visibility,
        PlayerLookup players) {

    public PlayerWarpServices {
        Objects.requireNonNull(setPlayerWarp, "setPlayerWarp");
        Objects.requireNonNull(delPlayerWarp, "delPlayerWarp");
        Objects.requireNonNull(usePlayerWarp, "usePlayerWarp");
        Objects.requireNonNull(listPlayerWarps, "listPlayerWarps");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(players, "players");
    }
}
