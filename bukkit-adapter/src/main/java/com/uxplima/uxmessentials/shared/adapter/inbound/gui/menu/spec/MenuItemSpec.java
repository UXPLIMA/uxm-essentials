package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One item in a menu spec. It declares the slots it occupies, a priority used when several items contend for the
 * same slot, the raw material/name/lore text (a {@code @key}, an inline literal, or a {@code %placeholder%} —
 * resolved later), its decoration, the view conditions that gate visibility, click handling, whether it
 * re-renders on refresh, an optional list expansion, and its pagination role.
 */
public record MenuItemSpec(
        SlotSet slots,
        int priority,
        String material,
        String name,
        List<String> lore,
        ItemDecor decor,
        List<Ref> view,
        ClickSpec click,
        boolean update,
        Optional<ListSpec> list,
        ItemType type) {

    public MenuItemSpec {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        Objects.requireNonNull(decor, "decor");
        view = List.copyOf(Objects.requireNonNull(view, "view"));
        Objects.requireNonNull(click, "click");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(type, "type");
    }
}
