package com.uxplima.uxmessentials.api.bukkit.menu;

import java.util.Map;

import org.bukkit.entity.Player;

import org.jspecify.annotations.NullMarked;

/**
 * What an action handler receives when a player clicks a menu item bound to it: the open menu, the live player, the
 * gesture, and whatever the spec passed with the action id.
 *
 * <p>A click handler runs on the viewer's own region thread, so it may act on {@link #player()} inline. Work that
 * touches another entity or another region must be scheduled onto that region rather than run here.
 */
@NullMarked
public interface MenuClick {

    /** The open menu the click happened in. */
    MenuView view();

    /** The player who clicked; live, and safe to act on from this handler. */
    Player player();

    /** Which gesture fired the action, so a handler can branch on left against shift-right. */
    MenuClickKind kind();

    /**
     * The arguments of the action reference that fired. A spec writing {@code ["my-award:100"]} arrives here as
     * {@code {value: "100"}}.
     */
    Map<String, String> args();

    /**
     * The single positional argument of an {@code id:value} action reference, or an empty string when the spec
     * named the action without one. Shorthand for {@code args().getOrDefault("value", "")}.
     */
    String arg();
}
