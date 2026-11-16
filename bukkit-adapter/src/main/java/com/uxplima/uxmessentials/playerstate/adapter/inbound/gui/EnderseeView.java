package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Opens an online {@code /endersee}: a managed menu that mirrors a target's ender chest (see {@link EnderLayout}).
 * Like {@link InvseeView}, the viewer edits a private copy and the close is saved by the shared {@link MirrorEdits}
 * rules. The ender chest is always editable, unless someone else is already editing it.
 */
@NullMarked
public final class EnderseeView {

    private final MirrorEditing editing;

    public EnderseeView(MirrorEditing editing) {
        this.editing = Objects.requireNonNull(editing, "editing");
    }

    /** Open {@code subject}'s ender chest for {@code viewer}. */
    public void open(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        editing.edits().openOnline(viewer, subject, MirrorKind.ENDER, true);
    }
}
