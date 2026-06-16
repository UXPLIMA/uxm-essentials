package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Delivers the {@code GIVE} action's item to a viewer. The value is {@code <material>[:amount]} (default amount
 * 1); the material is matched leniently (a Bukkit material name, case-insensitive), and the amount is a positive
 * integer. What does not fit the viewer's inventory drops at their feet rather than vanishing, so a full
 * inventory never silently eats the reward. An unknown material is logged and skipped — the runner treats this as
 * a fail-soft effect, not a gate, so the rest of the chain still runs.
 *
 * <p>Full custom-item delivery (a serialized item payload with NBT) is intentionally out of scope for v1: this
 * gives a plain material stack, which covers the common reward case; a richer payload is a later extension.
 */
@NullMarked
final class NpcGifts {

    private final Logger log;

    NpcGifts(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Give {@code value}'s item to {@code viewer}, dropping any overflow at their location. */
    void give(Player viewer, String value) {
        ItemStack item = parse(value);
        if (item == null) {
            log.warn("event=npc_action_unknown_give value={}", value);
            return;
        }
        var overflow = viewer.getInventory().addItem(item);
        if (overflow.isEmpty()) {
            return;
        }
        Location at = Objects.requireNonNull(viewer.getLocation(), "viewer location");
        for (ItemStack leftover : overflow.values()) {
            viewer.getWorld().dropItemNaturally(at, leftover);
        }
    }

    /** Parse {@code <material>[:amount]} into an item, or {@code null} when the material is unknown. */
    private static @Nullable ItemStack parse(String value) {
        String spec = value.strip();
        int colon = spec.indexOf(':');
        String materialName = colon < 0 ? spec : spec.substring(0, colon);
        Material material = Material.matchMaterial(materialName.strip());
        if (material == null || !material.isItem()) {
            return null;
        }
        int amount = colon < 0 ? 1 : parseAmount(spec.substring(colon + 1));
        return new ItemStack(material, amount);
    }

    /** Parse the amount segment to a positive count, defaulting to 1 on anything non-positive or non-numeric. */
    private static int parseAmount(String raw) {
        try {
            int amount = Integer.parseInt(raw.strip().toLowerCase(Locale.ROOT));
            return amount > 0 ? amount : 1;
        } catch (NumberFormatException notANumber) {
            return 1;
        }
    }
}
