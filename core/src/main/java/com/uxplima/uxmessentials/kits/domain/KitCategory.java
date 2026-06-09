package com.uxplima.uxmessentials.kits.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A category to group multiple kits together in the GUI.
 * Supports nesting via parentCategoryId to achieve unlimited sub-category depth.
 */
public record KitCategory(
        String id,
        String displayName,
        Optional<String> displayMaterial,
        List<String> displayLore,
        int slot,
        Optional<String> parentCategoryId) {

    public KitCategory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(displayMaterial, "displayMaterial");
        Objects.requireNonNull(displayLore, "displayLore");
        Objects.requireNonNull(parentCategoryId, "parentCategoryId");
        displayLore = List.copyOf(displayLore);
    }

    /** A copy with the display name swapped, every other setting preserved. */
    public KitCategory withDisplayName(String value) {
        Objects.requireNonNull(value, "value");
        return new KitCategory(id, value, displayMaterial, displayLore, slot, parentCategoryId);
    }

    /** A copy with the display material swapped, every other setting preserved. */
    public KitCategory withDisplayMaterial(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return new KitCategory(id, displayName, value, displayLore, slot, parentCategoryId);
    }

    /** A copy with the display lore swapped, every other setting preserved. */
    public KitCategory withDisplayLore(List<String> value) {
        Objects.requireNonNull(value, "value");
        return new KitCategory(id, displayName, displayMaterial, value, slot, parentCategoryId);
    }

    /** A copy with the sorting slot swapped, every other setting preserved. */
    public KitCategory withSlot(int value) {
        return new KitCategory(id, displayName, displayMaterial, displayLore, value, parentCategoryId);
    }

    /** A copy with the parent category swapped, every other setting preserved. */
    public KitCategory withParentCategoryId(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return new KitCategory(id, displayName, displayMaterial, displayLore, slot, value);
    }
}
