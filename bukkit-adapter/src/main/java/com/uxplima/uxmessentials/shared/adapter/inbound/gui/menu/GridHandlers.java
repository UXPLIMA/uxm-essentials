package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.Objects;

/**
 * The click callbacks a caller hands {@link Menus#openGrid} — the behaviour half of a grid open, kept separate from
 * the {@link GridSpec} layout half so the same visual canvas can be driven by different editing policy. For now a grid
 * carries a single content-slot handler; the control-bar buttons and the prev/next pagination carry their own handlers
 * on the {@link GridSpec}, so this stays a one-field record that later phases can widen without breaking callers.
 */
public record GridHandlers(GridClickHandler onSlot) {

    public GridHandlers {
        Objects.requireNonNull(onSlot, "onSlot");
    }
}
