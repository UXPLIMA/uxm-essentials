/**
 * The Bukkit shell that turns a resolved menu spec into the {@code ItemStack}s and inventory the player sees.
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer} resolves one item's
 * material, name, lore, and decoration against the placeholder registry and the locale catalog; the wider
 * renderer assembles those into an open inventory. This is the one place the pure spec model meets Bukkit
 * item types, so the {@code spec/} and {@code eval/} packages stay free of {@code org.bukkit}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;
