package com.uxplima.uxmessentials.warps.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.warps.application.DelWarp;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpInfo;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed warps use cases the Brigadier commands share, built once per module start by
 * {@code WarpsWiring} from the kernel ports, the jOOQ repository, the teleport-delegating teleporter, and
 * the optional economy seam. Held so every command reads the same use cases; the warps context keeps no
 * other adapter-side runtime state, so there is nothing here to drain on stop beyond dropping this holder.
 *
 * @param useWarp {@code /warp}
 * @param setWarp {@code /setwarp}
 * @param delWarp {@code /delwarp}
 * @param listWarps {@code /warps}
 * @param warpInfo {@code /warpinfo}
 * @param moveWarp {@code /movewarp}
 * @param players name → ref resolution, available for future owner-attribution forms
 */
@NullMarked
public record WarpServices(
        UseWarp useWarp,
        SetWarp setWarp,
        DelWarp delWarp,
        ListWarps listWarps,
        WarpInfo warpInfo,
        MoveWarp moveWarp,
        PlayerLookup players) {

    public WarpServices {
        Objects.requireNonNull(useWarp, "useWarp");
        Objects.requireNonNull(setWarp, "setWarp");
        Objects.requireNonNull(delWarp, "delWarp");
        Objects.requireNonNull(listWarps, "listWarps");
        Objects.requireNonNull(warpInfo, "warpInfo");
        Objects.requireNonNull(moveWarp, "moveWarp");
        Objects.requireNonNull(players, "players");
    }
}
