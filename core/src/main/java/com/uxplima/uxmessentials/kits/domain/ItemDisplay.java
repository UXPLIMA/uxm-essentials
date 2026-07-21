package com.uxplima.uxmessentials.kits.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One operator-authored display override for a kit icon: the item {@code material} to show, its MiniMessage
 * {@code name}, and its {@code lore} lines. A {@link KitDefinition} carries several of these, one per icon
 * state (the normal display, plus the no-permission, cooldown, claimed, unaffordable, and requirements
 * states), so the same three-field shape is modelled once here rather than repeated as loose fields on the
 * kit. Each field is optional in practice: an absent {@code material} or {@code name} falls back to the
 * per-state message catalog when the icon is rendered, and an empty {@code lore} means "no override".
 *
 * @param material the icon's item type (a material name), or empty to fall back to the default icon
 * @param name the icon's MiniMessage name, or empty to fall back to the per-state catalog name
 * @param lore the icon's lore lines; empty when the state contributes no override lore
 */
public record ItemDisplay(Optional<String> material, Optional<String> name, List<String> lore) {

    private static final ItemDisplay EMPTY = new ItemDisplay(Optional.empty(), Optional.empty(), List.of());

    public ItemDisplay {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lore, "lore");
        lore = List.copyOf(lore);
    }

    /** The display with no override at all: no material, no name, no lore. */
    public static ItemDisplay empty() {
        return EMPTY;
    }
}
