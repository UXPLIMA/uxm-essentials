package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Loads the warp a GUI click targets as a uniform {@link EditableWarp}, resolving a server warp through the
 * {@link WarpRepository} and a player warp (one with an owner) through the late-bound {@link PlayerWarpRepository}
 * the editor view holds. The warp editor and its sub-menu handler share one loader so both reach a clicked warp
 * the same way; a warp that no longer exists, or a player warp with no repository bound, resolves to {@code null}
 * so the caller can close the stale menu instead of acting on a missing warp.
 */
@NullMarked
final class EditableWarpLoader {

    private final WarpRepository warpRepository;
    private final WarpEditorView editorView;

    EditableWarpLoader(WarpRepository warpRepository, WarpEditorView editorView) {
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
    }

    /** Load the clicked warp as a uniform {@link EditableWarp}, or {@code null} when it no longer exists. */
    @Nullable EditableWarp load(String name, @Nullable PlayerRef owner) {
        if (owner != null) {
            PlayerWarpRepository repo = editorView.playerWarpRepository();
            if (repo == null) {
                return null;
            }
            return repo.findByName(PlayerWarpName.of(name))
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .map(warp -> EditableWarp.ofPlayer(warp, repo))
                    .orElse(null);
        }
        return warpRepository
                .find(WarpName.of(name))
                .map(warp -> EditableWarp.ofServer(warp, warpRepository))
                .orElse(null);
    }
}
