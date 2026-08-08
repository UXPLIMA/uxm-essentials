package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Locale;

import org.bukkit.Material;

import com.uxplima.uxmessentials.kits.domain.KitCategory;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves the icon material for a kit category, shared across the category GUIs so the parsing-and-fallback rule
 * lives in one place. A category with no display material, or one naming a material this server does not have, falls
 * back to a book.
 */
@NullMarked
final class KitCategoryIcons {

    private static final Material DEFAULT = Material.BOOK;

    private KitCategoryIcons() {}

    static Material material(KitCategory category) {
        if (category.displayMaterial().isEmpty()) {
            return DEFAULT;
        }
        try {
            Material parsed = Material.valueOf(category.displayMaterial().get().toUpperCase(Locale.ROOT));
            return parsed.isAir() ? DEFAULT : parsed;
        } catch (IllegalArgumentException absent) {
            return DEFAULT;
        }
    }
}
