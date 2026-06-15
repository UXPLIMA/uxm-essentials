package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption resolution of an item or block hologram's stored content — a {@code Material} name and a
 * BlockData string, both pure strings in the domain — into the live Bukkit types the lib needs. Both are
 * fail-soft: an unknown material name or an unparseable BlockData string yields {@code null} rather than
 * throwing, so the renderer can log and skip one bad hologram without crashing the whole render pass.
 */
@NullMarked
final class HologramModels {

    private HologramModels() {}

    /**
     * Resolve a stored item-material name into a single-stack {@link ItemStack}, or {@code null} for a blank or
     * unknown name. A {@code Material} name is the v1 item content; storing a full serialized custom item (via
     * the shared item codec) is a deliberate future enhancement, so a v1 item hologram shows the vanilla item.
     */
    static @Nullable ItemStack itemOf(@Nullable String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(materialName.strip().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            return null;
        }
        return new ItemStack(material);
    }

    /**
     * Parse a stored BlockData string (e.g. {@code minecraft:oak_log[axis=y]}) into live {@link BlockData}, or
     * {@code null} for a blank or invalid string. {@code Bukkit.createBlockData} throws
     * {@link IllegalArgumentException} on a malformed string; that is caught and turned into the fail-soft null.
     */
    static @Nullable BlockData blockOf(@Nullable String blockData) {
        if (blockData == null || blockData.isBlank()) {
            return null;
        }
        try {
            return Bukkit.createBlockData(blockData.strip());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
