package com.uxplima.uxmessentials.api.bukkit.menu;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;

/**
 * A source of menu-item icons, so a menu spec can write {@code material = "myplugin:sword"} and get your item.
 *
 * <p>Claim your own prefix and return the base stack for a spec you recognise; return {@link Optional#empty()} for
 * everything else so the next provider, and finally the plain material lookup, gets its turn. Never match a bare
 * material name: an item declaring {@code DIAMOND} must keep rendering as a diamond.
 *
 * <p>Providers registered through {@link MenuApi#registerIconProvider} are consulted after every built-in one, so
 * they add prefixes and can never shadow the engine's own. The method runs on the viewer's region thread during a
 * render, so it must not block, and it must never throw: return empty instead.
 */
@FunctionalInterface
@NullMarked
public interface MenuIconProvider {

    /** The base icon for {@code materialSpec}, or empty when this provider does not own that spec. */
    Optional<ItemStack> icon(String materialSpec, MenuView view);
}
