package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One open managed mirror window: the edit it shows and where its final contents go. It is the subject the menu
 * carries, so the content provider reads the window's state from here rather than having to know which view opened
 * it.
 *
 * <p>Identity is the object itself, not its fields: two staff may have the same target open at once and each window
 * is written back on its own.
 */
@NullMarked
final class MirrorHolder {

    private final MirrorEdits.Edit<ItemStack> edit;
    private final Consumer<@Nullable ItemStack[]> writeBack;

    MirrorHolder(MirrorEdits.Edit<ItemStack> edit, Consumer<@Nullable ItemStack[]> writeBack) {
        this.edit = Objects.requireNonNull(edit, "edit");
        this.writeBack = Objects.requireNonNull(writeBack, "writeBack");
    }

    /** The staff member the window is open for. */
    PlayerRef viewer() {
        return edit.viewer();
    }

    /** The player whose container this window mirrors. */
    PlayerRef target() {
        return edit.target();
    }

    /** The edit this window shows. */
    MirrorEdits.Edit<ItemStack> edit() {
        return edit;
    }

    /**
     * Whether the viewer may move items in the window now. A view-only window refuses every movement in its region,
     * and so does one whose edit is no longer open.
     */
    boolean editable() {
        return edit.accepting();
    }

    /** Which container is mirrored. */
    MirrorKind kind() {
        return edit.kind();
    }

    /** The items the window was seeded with, as a fresh array each call so no caller can mutate them. */
    @Nullable ItemStack[] snapshot() {
        return edit.base().toArray(new ItemStack[0]);
    }

    /** Hand this window's final contents over; called when the window closes. */
    void writeBack(@Nullable ItemStack[] contents) {
        writeBack.accept(contents);
    }
}
