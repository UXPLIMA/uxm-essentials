package com.uxplima.uxmessentials.holograms.adapter.outbound;

import org.jspecify.annotations.NullMarked;

/**
 * The one place the PDC key name for a clickable hologram's Interaction hitbox is defined, so the renderer (which
 * stamps the box with the hologram's name under {@code new NamespacedKey(plugin, PDC_KEY)}) and the click
 * listener (which reads it back) cannot drift on the literal. A {@code NamespacedKey} built from the same plugin
 * and this name compares equal, so each side may construct its own instance.
 */
@NullMarked
public final class HologramClickKey {

    /** The {@code NamespacedKey} name, scoped under the plugin namespace, carrying the clicked hologram's name. */
    public static final String PDC_KEY = "hologram_click";

    private HologramClickKey() {}
}
