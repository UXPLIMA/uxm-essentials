package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.Objects;

/**
 * A list-backed item: the engine asks the {@code source} reference for a collection of entries and stamps the
 * {@code template} item once per entry, expanding a single spec block into a paginated grid.
 */
public record ListSpec(Ref source, MenuItemSpec template) {

    public ListSpec {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(template, "template");
    }
}
