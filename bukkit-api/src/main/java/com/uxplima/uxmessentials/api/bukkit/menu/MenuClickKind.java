package com.uxplima.uxmessentials.api.bukkit.menu;

import org.jspecify.annotations.NullMarked;

/**
 * The gesture that fired a menu click, as a menu spec names it in a {@code click {}} block.
 *
 * <p>This is deliberately not Bukkit's {@code ClickType}: a menu spec distinguishes only the gestures a player can
 * meaningfully bind, and mapping the wider inventory-click vocabulary onto them is the engine's job rather than a
 * consumer's.
 */
@NullMarked
public enum MenuClickKind {
    LEFT,
    RIGHT,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIDDLE,
    DROP,
    CONTROL_DROP,
    DOUBLE_CLICK
}
