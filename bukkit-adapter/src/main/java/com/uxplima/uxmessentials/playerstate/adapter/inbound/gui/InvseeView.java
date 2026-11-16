package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.LivePlayers;
import com.uxplima.uxmessentials.shared.application.permission.PlayerstatePermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Opens an online {@code /invsee}: a managed menu that mirrors a target's full inventory (main slots, armour and
 * offhand, see {@link InvseeLayout}). The viewer edits a private copy, never the target's live inventory, so no click
 * reads or writes a foreign container from the viewer's thread; the close is saved by the shared {@link MirrorEdits}
 * rules, which write only the slots the viewer changed and take back what could not be saved.
 *
 * <p>The viewer may edit when they hold {@code uxmessentials.invsee.modify} and nobody else is editing the same
 * inventory, online or offline; otherwise the menu opens view only.
 */
@NullMarked
public final class InvseeView {

    private final MirrorEditing editing;

    public InvseeView(MirrorEditing editing) {
        this.editing = Objects.requireNonNull(editing, "editing");
    }

    /** Open {@code subject}'s inventory for {@code viewer}. Called on the viewer's thread. */
    public void open(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        editing.edits().openOnline(viewer, subject, MirrorKind.INVENTORY, mayModify(viewer));
    }

    /** Whether {@code viewer} holds the node that makes an inventory window editable. */
    static boolean mayModify(PlayerRef viewer) {
        return LivePlayers.of(viewer)
                .map(player -> player.hasPermission(PlayerstatePermissions.INVSEE_MODIFY))
                .orElse(false);
    }
}
