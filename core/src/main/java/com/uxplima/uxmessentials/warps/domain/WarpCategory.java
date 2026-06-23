package com.uxplima.uxmessentials.warps.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A category to group multiple warps together in the browse GUI. Supports nesting via
 * {@code parentCategoryId} so an operator can build sub-categories of unlimited depth, mirroring the kit
 * category model. A warp references a category by its {@code id}; a warp with no category id is rendered at
 * the top level of the browse menu.
 */
public record WarpCategory(
        String id,
        String displayName,
        Optional<String> displayMaterial,
        List<String> displayLore,
        int slot,
        Optional<String> parentCategoryId) {

    public WarpCategory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(displayMaterial, "displayMaterial");
        Objects.requireNonNull(displayLore, "displayLore");
        Objects.requireNonNull(parentCategoryId, "parentCategoryId");
        displayLore = List.copyOf(displayLore);
    }

    /** A copy with the display name swapped, every other setting preserved. */
    public WarpCategory withDisplayName(String value) {
        Objects.requireNonNull(value, "value");
        return new WarpCategory(id, value, displayMaterial, displayLore, slot, parentCategoryId);
    }

    /** A copy with the display material swapped, every other setting preserved. */
    public WarpCategory withDisplayMaterial(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return new WarpCategory(id, displayName, value, displayLore, slot, parentCategoryId);
    }

    /** A copy with the display lore swapped, every other setting preserved. */
    public WarpCategory withDisplayLore(List<String> value) {
        Objects.requireNonNull(value, "value");
        return new WarpCategory(id, displayName, displayMaterial, value, slot, parentCategoryId);
    }

    /** A copy with the sorting slot swapped, every other setting preserved. */
    public WarpCategory withSlot(int value) {
        return new WarpCategory(id, displayName, displayMaterial, displayLore, value, parentCategoryId);
    }

    /** A copy with the parent category swapped, every other setting preserved. */
    public WarpCategory withParentCategoryId(Optional<String> value) {
        Objects.requireNonNull(value, "value");
        return new WarpCategory(id, displayName, displayMaterial, displayLore, slot, value);
    }
}
