package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Opens {@code /invsee} and {@code /endersee} against an <em>offline</em> target: the stored items are read from the
 * player's data file and shown in the same managed menu the online views use. The close is saved back to the file by
 * the shared {@link MirrorEdits} rules, or to the live container when the target has joined since.
 */
@NullMarked
public final class OfflineContainerView {

    private final MirrorEditing editing;

    public OfflineContainerView(MirrorEditing editing) {
        this.editing = Objects.requireNonNull(editing, "editing");
    }

    /** Open {@code subject}'s stored inventory for {@code viewer}, running {@code shown} once it is showing. */
    public void openInventory(PlayerRef viewer, PlayerRef subject, Runnable shown) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        editing.edits().openOffline(viewer, subject, MirrorKind.INVENTORY, InvseeView.mayModify(viewer), shown);
    }

    /** Open {@code subject}'s stored ender chest for {@code viewer}, running {@code shown} once it is showing. */
    public void openEnderChest(PlayerRef viewer, PlayerRef subject, Runnable shown) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        editing.edits().openOffline(viewer, subject, MirrorKind.ENDER, true, shown);
    }
}
