package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.Material;

import org.jspecify.annotations.NullMarked;

/**
 * The externalised geometry of the per-home action menu, loaded once from
 * {@code modules/homes/gui/home-actions.conf}: the row count, the button slots, the material each button uses,
 * and the border filler. Titles and lore stay in code as {@code MessageKey} lookups — this record holds layout
 * integers and materials only, never localised strings.
 *
 * @param rows the menu row count, 1..6
 * @param infoSlot the read-only home-info display slot
 * @param renameSlot the rename-via-anvil button slot
 * @param teleportSlot the teleport button slot
 * @param deleteSlot the delete button slot
 * @param relocateSlot the relocate-here button slot
 * @param changeIconSlot the change-icon button slot
 * @param visibilitySlot the public/private visibility toggle button slot
 * @param invitesSlot the open-invited-players button slot
 * @param backSlot the back-to-grid button slot
 * @param infoMaterial the info display material
 * @param renameMaterial the rename button material
 * @param teleportMaterial the teleport button material
 * @param deleteMaterial the delete button material
 * @param relocateMaterial the relocate button material
 * @param changeIconMaterial the change-icon button material
 * @param visibilityPublicMaterial the visibility button material when the home is public
 * @param visibilityPrivateMaterial the visibility button material when the home is private
 * @param invitesMaterial the invited-players button material
 * @param backMaterial the back button material
 * @param filler the material the unused border cells are filled with
 */
@NullMarked
public record HomeActionsLayout(
        int rows,
        int infoSlot,
        int renameSlot,
        int teleportSlot,
        int deleteSlot,
        int relocateSlot,
        int changeIconSlot,
        int visibilitySlot,
        int invitesSlot,
        int backSlot,
        Material infoMaterial,
        Material renameMaterial,
        Material teleportMaterial,
        Material deleteMaterial,
        Material relocateMaterial,
        Material changeIconMaterial,
        Material visibilityPublicMaterial,
        Material visibilityPrivateMaterial,
        Material invitesMaterial,
        Material backMaterial,
        Material filler) {

    public HomeActionsLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        Objects.requireNonNull(infoMaterial, "infoMaterial");
        Objects.requireNonNull(renameMaterial, "renameMaterial");
        Objects.requireNonNull(teleportMaterial, "teleportMaterial");
        Objects.requireNonNull(deleteMaterial, "deleteMaterial");
        Objects.requireNonNull(relocateMaterial, "relocateMaterial");
        Objects.requireNonNull(changeIconMaterial, "changeIconMaterial");
        Objects.requireNonNull(visibilityPublicMaterial, "visibilityPublicMaterial");
        Objects.requireNonNull(visibilityPrivateMaterial, "visibilityPrivateMaterial");
        Objects.requireNonNull(invitesMaterial, "invitesMaterial");
        Objects.requireNonNull(backMaterial, "backMaterial");
        Objects.requireNonNull(filler, "filler");
    }

    /** The bundled default, used when no conf is present so a missing file still opens the action menu. */
    public static HomeActionsLayout codeDefault() {
        return new HomeActionsLayout(
                3,
                4,
                10,
                11,
                13,
                15,
                16,
                19,
                25,
                22,
                Material.PAPER,
                Material.NAME_TAG,
                Material.ENDER_PEARL,
                Material.BARRIER,
                Material.COMPASS,
                Material.ITEM_FRAME,
                Material.LIME_DYE,
                Material.GRAY_DYE,
                Material.PLAYER_HEAD,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }
}
