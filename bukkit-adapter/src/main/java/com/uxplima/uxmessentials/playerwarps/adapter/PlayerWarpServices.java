package com.uxplima.uxmessentials.playerwarps.adapter;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpListView;
import com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ListPlayerWarps;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
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
 * @param repository the warp store, held only so the name-argument suggesters can peek an owner's warps
 *     without blocking (a join-warmed cache hit completes the names; a cold miss suggests nothing)
 * @param editorView the per-warp settings editor GUI reused from the warps module (opened by {@code /pwarp edit})
 * @param scheduler the kernel scheduler the commands run their repository reads through off the tick thread,
 *     bridging any Bukkit feedback back to the player's region thread (the homes async-read pattern)
 * @param listView the management-GUI list opened by {@code /pwarp} with no arguments (the framework SP3 panel),
 *     owner-scoped for a player and cross-owner for a holder of {@code uxmessentials.pwarp.gui}
 */
@NullMarked
public record PlayerWarpServices(
        SetPlayerWarp setPlayerWarp,
        DelPlayerWarp delPlayerWarp,
        UsePlayerWarp usePlayerWarp,
        ListPlayerWarps listPlayerWarps,
        SetPlayerWarpVisibility visibility,
        PlayerLookup players,
        PlayerWarpRepository repository,
        @org.jspecify.annotations.Nullable WarpEditorView editorView,
        Scheduler scheduler,
        PlayerWarpListView listView) {

    public PlayerWarpServices {
        Objects.requireNonNull(setPlayerWarp, "setPlayerWarp");
        Objects.requireNonNull(delPlayerWarp, "delPlayerWarp");
        Objects.requireNonNull(usePlayerWarp, "usePlayerWarp");
        Objects.requireNonNull(listPlayerWarps, "listPlayerWarps");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(listView, "listView");
    }

    /**
     * The names of the warps {@code owner} owns if they are already cached, for the name-argument suggesters.
     * Reads only the non-blocking repository peek, so a cold cache (no join-warm yet) yields an empty list and
     * the suggester offers nothing rather than reaching the disk on the tick thread.
     */
    public List<String> ownWarpNames(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return repository.peekOwned(owner).orElseGet(List::of).stream()
                .map(PlayerWarp::name)
                .map(name -> name.value())
                .toList();
    }
}
