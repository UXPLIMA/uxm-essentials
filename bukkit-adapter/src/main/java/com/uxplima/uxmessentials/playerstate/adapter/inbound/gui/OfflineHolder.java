package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflineInventory;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Tags an offline {@code /invsee} or {@code /endersee} menu so {@link OfflineContainerListener} recognises it and
 * {@link OfflineContainerView} can reconcile it on close. Mirrors {@link InvseeHolder}, but for a target read from
 * disk: it carries the subject it was loaded for, whether the viewer may edit, and which kind of container it is
 * (so the listener knows the editable-slot shape and the view knows which storage block to write back).
 */
@NullMarked
final class OfflineHolder implements InventoryHolder {

    /** Which stored container the menu mirrors — the full inventory (54-slot layout) or the ender chest (27). */
    enum Kind {
        INVENTORY,
        ENDER
    }

    private final PlayerRef target;
    private final boolean editable;
    private final Kind kind;
    private @Nullable Inventory inventory;

    OfflineHolder(PlayerRef target, boolean editable, Kind kind) {
        this.target = Objects.requireNonNull(target, "target");
        this.editable = editable;
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    /** The offline player whose stored container this menu mirrors and writes back to on close. */
    PlayerRef target() {
        return target;
    }

    /** Whether the viewer may edit; a view-only menu cancels every click and writes nothing back. */
    boolean editable() {
        return editable;
    }

    Kind kind() {
        return kind;
    }

    /** Whether {@code slot} in the top inventory is one the viewer may edit (not a filler pane). */
    boolean isEditableSlot(int slot) {
        return kind == Kind.INVENTORY ? InvseeLayout.isEditable(slot) : slot >= 0 && slot < OfflineInventory.ENDER_SIZE;
    }

    /** Store the built menu so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("offline menu inventory not attached yet");
        }
        return built;
    }
}
