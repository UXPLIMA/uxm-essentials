package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised geometry of the invited-players menu, loaded once from
 * {@code modules/homes/gui/invites-menu.conf}: the row count, the content cells each invited player is paged
 * through, the navigation slots (previous, add, back, next), the materials those buttons use, and the border
 * filler. Titles, lore, and player names stay in code as {@code MessageKey} lookups — this record holds layout
 * integers and materials only, never localised strings.
 *
 * @param rows the menu row count, 1..6
 * @param contentSlots the cells invited players are paged through
 * @param entryMaterial the material an invited-player entry is rendered with
 * @param navMaterial the previous/next button material
 * @param addMaterial the invite-a-player button material
 * @param backMaterial the back-to-action-menu button material
 * @param filler the material the unused border cells are filled with
 * @param prevSlot the previous-page button slot
 * @param addSlot the invite-a-player button slot
 * @param backSlot the back button slot
 * @param nextSlot the next-page button slot
 */
@NullMarked
public record InvitesMenuLayout(
        int rows,
        List<Integer> contentSlots,
        Material entryMaterial,
        Material navMaterial,
        Material addMaterial,
        Material backMaterial,
        Material filler,
        int prevSlot,
        int addSlot,
        int backSlot,
        int nextSlot) {

    public InvitesMenuLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        Objects.requireNonNull(contentSlots, "contentSlots");
        if (contentSlots.isEmpty()) {
            throw new IllegalArgumentException("contentSlots must not be empty");
        }
        contentSlots = List.copyOf(contentSlots);
        Objects.requireNonNull(entryMaterial, "entryMaterial");
        Objects.requireNonNull(navMaterial, "navMaterial");
        Objects.requireNonNull(addMaterial, "addMaterial");
        Objects.requireNonNull(backMaterial, "backMaterial");
        Objects.requireNonNull(filler, "filler");
    }

    /** The bundled default, used when no conf is present so a missing file still opens the menu. */
    public static InvitesMenuLayout codeDefault() {
        return new InvitesMenuLayout(
                6,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
                Material.PLAYER_HEAD,
                Material.ARROW,
                Material.LIME_DYE,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE,
                48,
                49,
                45,
                50);
    }
}
