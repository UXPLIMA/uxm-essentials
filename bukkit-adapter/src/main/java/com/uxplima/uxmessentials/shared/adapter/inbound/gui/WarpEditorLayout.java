package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import org.bukkit.Material;

/**
 * Geometry, slot assignments, and materials for the /warp edit <name> configuration menu.
 */
public record WarpEditorLayout(
        int rows,
        int iconSlot,
        int teleportSlot,
        int lockSlot,
        int passwordSlot,
        int welcomeSlot,
        int soundsSlot,
        int particlesSlot,
        int warmupSlot,
        int cooldownSlot,
        int closeSlot,
        Material teleportMaterial,
        Material lockMaterial,
        Material passwordMaterial,
        Material welcomeMaterial,
        Material soundsMaterial,
        Material particlesMaterial,
        Material warmupMaterial,
        Material cooldownMaterial) {
    public static WarpEditorLayout defaultLayout() {
        return new WarpEditorLayout(
                3,
                4,
                0,
                10,
                11,
                12,
                13,
                14,
                15,
                16,
                22,
                Material.ENDER_PEARL,
                Material.LEVER,
                Material.PAPER,
                Material.WRITABLE_BOOK,
                Material.JUKEBOX,
                Material.BLAZE_POWDER,
                Material.CLOCK,
                Material.COMPASS);
    }
}
