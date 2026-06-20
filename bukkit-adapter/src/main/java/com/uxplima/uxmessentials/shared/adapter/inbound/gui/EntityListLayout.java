package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised presentation of an {@link EntityListView}: a {@link GuiLayout} (rows, content slots, the
 * previous/next navigation slots and their icon) plus the two fields a managed list adds on top — the
 * background {@code filler} material and an optional {@code create} button (its slot and icon) for a list that
 * lets the viewer add a new entry. Holds layout integers and materials only; titles, button labels, and the
 * create-button name stay in the message catalog as {@code MessageKey} lookups, so a translated catalog and an
 * operator-edited layout never collide.
 *
 * <p>Built once at load time by {@link GuiLayouts#loadEntityList}; a view reads {@link #base} for the paged
 * geometry and the create slot/icon when {@link #createSlot} is present. The {@code create} slot is optional:
 * a read-only list (no creation) simply omits it from the conf and the view shows no create button.
 *
 * @param base the paged geometry: rows, content slots, nav slots, nav and fallback icons
 * @param filler the background filler material drawn into every non-content slot
 * @param createSlot the slot the create button sits in, or empty for a list with no creation
 * @param createIcon the create button material; meaningful only when {@link #createSlot} is present
 */
@NullMarked
public record EntityListLayout(GuiLayout base, Material filler, OptionalInt createSlot, Material createIcon) {

    public EntityListLayout {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(filler, "filler");
        Objects.requireNonNull(createSlot, "createSlot");
        Objects.requireNonNull(createIcon, "createIcon");
        createSlot.ifPresent(slot -> {
            if (slot < 0 || slot >= base.rows() * 9) {
                throw new IllegalArgumentException("createSlot out of range: " + slot);
            }
        });
    }

    /** The row count, from the underlying {@link GuiLayout}. */
    public int rows() {
        return base.rows();
    }

    /** The explicit content slots if the conf set any, else empty so the view uses the default page region. */
    public Optional<List<Integer>> explicitContentSlots() {
        return base.explicitContentSlots();
    }

    /** A code default: a {@link GuiLayout#paginatedDefault} list with a glass filler and no create button. */
    public static EntityListLayout paginatedDefault(Material fallbackIcon) {
        return new EntityListLayout(
                GuiLayout.paginatedDefault(fallbackIcon),
                Material.BLACK_STAINED_GLASS_PANE,
                OptionalInt.empty(),
                Material.LIME_DYE);
    }

    /** A code default with a create button at {@code createSlot}, for a list that allows creation. */
    public static EntityListLayout withCreate(Material fallbackIcon, int createSlot, Material createIcon) {
        return new EntityListLayout(
                GuiLayout.paginatedDefault(fallbackIcon),
                Material.BLACK_STAINED_GLASS_PANE,
                OptionalInt.of(createSlot),
                createIcon);
    }
}
