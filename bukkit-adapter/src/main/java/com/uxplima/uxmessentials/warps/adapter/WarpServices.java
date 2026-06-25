package com.uxplima.uxmessentials.warps.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpBrowseMenu;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpManagerMenu;
import com.uxplima.uxmessentials.warps.application.DelWarp;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpInfo;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed warps use cases the Brigadier commands share, built once per module start by
 * {@code WarpsWiring} from the kernel ports, the jOOQ repository, the teleport-delegating teleporter, and
 * the optional economy seam. Held so every command reads the same use cases; the warps context keeps no
 * other adapter-side runtime state, so there is nothing here to drain on stop beyond dropping this holder.
 *
 * @param useWarp {@code /warp <name>}
 * @param setWarp {@code /warp set}
 * @param delWarp {@code /warp del}
 * @param listWarps {@code /warp list}
 * @param warpInfo {@code /warp info}
 * @param moveWarp {@code /warp move}
 * @param warpMenu the read-only browse menu {@code /warp list} opens
 * @param players name → ref resolution, available for future owner-attribution forms
 * @param editorView the warp edit chest GUI
 * @param managerView the admin warp manager GUI {@code /warp editor} (no name) opens, or {@code null} when
 *     disabled
 * @param scheduler the kernel scheduler the rating commands run their uncached database read/write through off
 *     the tick thread, bridging the confirmation back to the player's region thread
 */
@NullMarked
public record WarpServices(
        UseWarp useWarp,
        SetWarp setWarp,
        DelWarp delWarp,
        ListWarps listWarps,
        WarpInfo warpInfo,
        MoveWarp moveWarp,
        WarpBrowseMenu warpMenu,
        PlayerLookup players,
        WarpRepository repository,
        @org.jspecify.annotations.Nullable WarpEditorView editorView,
        @org.jspecify.annotations.Nullable WarpManagerMenu managerView,
        Scheduler scheduler) {

    public WarpServices {
        Objects.requireNonNull(useWarp, "useWarp");
        Objects.requireNonNull(setWarp, "setWarp");
        Objects.requireNonNull(delWarp, "delWarp");
        Objects.requireNonNull(listWarps, "listWarps");
        Objects.requireNonNull(warpInfo, "warpInfo");
        Objects.requireNonNull(moveWarp, "moveWarp");
        Objects.requireNonNull(warpMenu, "warpMenu");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(scheduler, "scheduler");
    }
}
